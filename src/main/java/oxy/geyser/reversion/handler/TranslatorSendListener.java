package oxy.geyser.reversion.handler;

import com.github.blackjack200.ouranos.converter.BlockStateDictionary;
import com.github.blackjack200.ouranos.data.LegacyBlockIdToStringIdMap;
import com.github.blackjack200.ouranos.data.bedrock.GlobalBlockDataHandlers;
import com.github.blackjack200.ouranos.shaded.protocol.bedrock.packet.StartGamePacket;
import lombok.NonNull;

import org.cloudburstmc.nbt.NbtList;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.codec.v361.Bedrock_v361;
import org.cloudburstmc.protocol.bedrock.codec.v408.Bedrock_v408;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemComponentPacket;
import org.geysermc.geyser.session.UpstreamSession;
import oxy.geyser.reversion.GeyserReversion;
import oxy.geyser.reversion.session.GeyserTranslatedUser;
import oxy.geyser.reversion.util.PacketUtil;

import java.util.Map;
import java.util.Objects;

public final class TranslatorSendListener extends UpstreamSession {
    private final GeyserTranslatedUser user;
    private final UpstreamSession oldSession;

    public TranslatorSendListener(GeyserTranslatedUser user, BedrockServerSession session, UpstreamSession oldSession) {
        super(session);
        this.oldSession = oldSession;
        this.user = user;
    }

    @Override
    public void disconnect(String reason) {
        oldSession.disconnect(reason);
    }

    @Override
    public void sendPacket(@NonNull BedrockPacket packet) {
        if (packet instanceof ItemComponentPacket componentPacket) {
            if (this.user != null) {
                this.user.setItemDefinitions(componentPacket);
            }
        }

        if (this.user != null) {
            com.github.blackjack200.ouranos.shaded.protocol.bedrock.packet.BedrockPacket oxyPacket = PacketUtil.toOxyNew(this.user, packet);
            if (oxyPacket instanceof StartGamePacket startGamePacket) {
                int downstreamProtocolId = this.user.getProtocolId();
                if (downstreamProtocolId <= Bedrock_v408.CODEC.getProtocolVersion()) {
                    if (downstreamProtocolId > Bedrock_v361.CODEC.getProtocolVersion()) {
                        var states = BlockStateDictionary.getInstance(downstreamProtocolId).getKnownStates().stream().map((e) -> {
                            var legacyId = (short) (Objects.requireNonNullElse(LegacyBlockIdToStringIdMap.getInstance().fromString(downstreamProtocolId, e.name()), 255) & 0xfffffff);
                            return NbtMap.builder().putCompound("block", e.rawState()).putShort("id", legacyId).build();
                        }).toList();
                        startGamePacket.setBlockPalette(new NbtList<>(NbtType.COMPOUND, states));
                    } else {
                        startGamePacket.setBlockPalette(new NbtList<>(NbtType.COMPOUND, BlockStateDictionary.getInstance(downstreamProtocolId).getKnownStates().stream().map((e) -> {
                            var blk = GlobalBlockDataHandlers.getUpgrader().fromLatestStateHash(e.latestStateHash());
                            var legacyId = (short) (Objects.requireNonNullElse(LegacyBlockIdToStringIdMap.getInstance().fromString(downstreamProtocolId, e.name()), 255) & 0xfffffff);
                            return NbtMap.builder().putCompound("block", NbtMap.fromMap(
                                    Map.of(
                                            "name", blk.id(),
                                            "meta", (short) blk.meta(),
                                            "id", legacyId
                                    ))).build();
                        }).toList()));
                    }
                }
//                startGamePacket.setBlockPalette(new NbtList<>(NbtType.COMPOUND));
            }
            if (oxyPacket != null) {
                oxyPacket = this.user.translateClientbound(oxyPacket);

                if (oxyPacket != null) {
                    final BedrockPacket translated = PacketUtil.toCloudburstOld(this.user, oxyPacket);
                    if (translated != null) {
                        super.sendPacket(translated);
                    }
                }
            }
        } else {
            super.sendPacket(packet);
        }
    }

    @Override
    public void sendPacketImmediately(@NonNull BedrockPacket packet) {
        if (this.user != null) {
            com.github.blackjack200.ouranos.shaded.protocol.bedrock.packet.BedrockPacket oxyPacket = PacketUtil.toOxyNew(this.user, packet);
            if (oxyPacket != null) {
                oxyPacket = this.user.translateClientbound(oxyPacket);

                if (oxyPacket != null) {
                    final BedrockPacket translated = PacketUtil.toCloudburstOld(this.user, oxyPacket);
                    if (translated != null) {
                        super.sendPacketImmediately(translated);
                    }
                }
            }
        } else {
            super.sendPacketImmediately(packet);
        }
    }

    @Override
    public int getProtocolVersion() {
        return this.user == null ? super.getProtocolVersion() : GeyserReversion.OLDEST_GEYSER_CODEC.getProtocolVersion();
    }
}