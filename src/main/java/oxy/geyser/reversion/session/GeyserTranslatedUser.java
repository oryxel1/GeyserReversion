package oxy.geyser.reversion.session;

import lombok.Getter;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemComponentPacket;
import org.cloudburstmc.protocol.common.SimpleDefinitionRegistry;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;
import oxy.geyser.reversion.GeyserReversion;
import oxy.geyser.reversion.util.PacketUtil;
import oxy.toviabedrock.ToViaBedrock;
import oxy.toviabedrock.base.registry.BlockDefinitionRegistryMapper;
import oxy.toviabedrock.session.UserSession;
import oxy.toviabedrock.shaded.protocol.bedrock.codec.BedrockCodec;
import oxy.toviabedrock.shaded.protocol.bedrock.codec.BedrockCodecHelper;
import oxy.toviabedrock.shaded.protocol.bedrock.data.definitions.ItemDefinition;

@Getter
public class GeyserTranslatedUser extends UserSession {
    private final GeyserSession session;
    private final BedrockCodec codec;
    private final org.cloudburstmc.protocol.bedrock.codec.BedrockCodec cloudburstCodec;
    private final org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper cloudburstHelper;
    private final BedrockCodecHelper helper;
    private final BedrockCodecHelper latestHelper;
    private final org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper cloudburstLatestHelper;

    public GeyserTranslatedUser(int protocolVersion, int serverVersion, GeyserSession session) {
        super(protocolVersion, serverVersion);
        this.session = session;
        this.codec = ToViaBedrock.getCodec(protocolVersion);
        this.cloudburstCodec = GeyserReversion.OXY_CODEC_MAPPER.get(protocolVersion);
        this.helper = this.codec.createHelper();
        this.latestHelper = GeyserReversion.OLDEST_GEYSER_OXY_CODEC.createHelper();
        this.cloudburstLatestHelper = GeyserReversion.OLDEST_GEYSER_CODEC.createHelper();
        this.cloudburstHelper = this.cloudburstCodec.createHelper();

        this.helper.setBlockDefinitions(new BlockDefinitionRegistryMapper(this));
        this.latestHelper.setBlockDefinitions(this.helper.getBlockDefinitions());

        this.cloudburstHelper.setBlockDefinitions(new org.cloudburstmc.protocol.common.DefinitionRegistry<>() {
            @Override
            public org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition getDefinition(int runtimeId) {
                return () -> runtimeId;
            }

            @Override
            public boolean isRegistered(org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition definition) {
                return true;
            }
        });

        this.cloudburstLatestHelper.setBlockDefinitions(this.cloudburstHelper.getBlockDefinitions());
    }

    public void setItemDefinitions(ItemComponentPacket packet) {
        SimpleDefinitionRegistry.Builder<org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition> builder = SimpleDefinitionRegistry.<org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition>builder()
                .add(new SimpleItemDefinition("minecraft:empty", 0, false));

        for (org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition definition : packet.getItems()) {
            builder.add(new SimpleItemDefinition(definition.getIdentifier(), definition.getRuntimeId(), definition.isComponentBased()));
        }

        SimpleDefinitionRegistry<org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition> itemDefinitions = builder.build();
        this.cloudburstHelper.setItemDefinitions(itemDefinitions);
        this.cloudburstLatestHelper.setItemDefinitions(itemDefinitions);

        oxy.toviabedrock.shaded.protocol.common.SimpleDefinitionRegistry.Builder<ItemDefinition> builder1 = oxy.toviabedrock.shaded.protocol.common.SimpleDefinitionRegistry.<ItemDefinition>builder()
                .add(new oxy.toviabedrock.shaded.protocol.bedrock.data.definitions.SimpleItemDefinition(
                        "minecraft:empty", 0, false));

        for (org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition definition : packet.getItems()) {
            builder1.add(new oxy.toviabedrock.shaded.protocol.bedrock.data.definitions.SimpleItemDefinition(definition.getIdentifier(), definition.getRuntimeId(), definition.isComponentBased()));
        }

        oxy.toviabedrock.shaded.protocol.common.SimpleDefinitionRegistry<ItemDefinition> itemDefinitions1 = builder1.build();

        this.helper.setItemDefinitions(itemDefinitions1);
        this.latestHelper.setItemDefinitions(itemDefinitions1);
    }

    @Override
    public void sendUpstreamPacket(oxy.toviabedrock.shaded.protocol.bedrock.packet.BedrockPacket bedrockPacket, boolean immediately) {
        final BedrockPacket packet = PacketUtil.toCloudburstOld(this, bedrockPacket);
        if (packet == null) {
            return;
        }

        if (immediately) {
            session.sendUpstreamPacketImmediately(packet);
        } else {
            session.sendUpstreamPacket(packet);
        }
    }

    @Override
    public void sendDownstreamPacket(oxy.toviabedrock.shaded.protocol.bedrock.packet.BedrockPacket bedrockPacket, boolean immediately) {
        final BedrockPacket packet = PacketUtil.toCloudburstOld(this, bedrockPacket);
        if (packet == null) {
            return;
        }

        Registries.BEDROCK_PACKET_TRANSLATORS.translate(packet.getClass(), packet, session, false);
    }
}
