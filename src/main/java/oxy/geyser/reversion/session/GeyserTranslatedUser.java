package oxy.geyser.reversion.session;

import lombok.Getter;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
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
import oxy.toviabedrock.shaded.protocol.bedrock.data.definitions.SimpleItemDefinition;
import oxy.toviabedrock.shaded.protocol.common.SimpleDefinitionRegistry;

@Getter
public class GeyserTranslatedUser extends UserSession {
    private final GeyserSession session;
    private final BedrockCodec codec;
    private final org.cloudburstmc.protocol.bedrock.codec.BedrockCodec cloudburstCodec;
    private final org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper cloudburstHelper;
    private final BedrockCodecHelper helper;

    public GeyserTranslatedUser(int protocolVersion, int serverVersion, GeyserSession session) {
        super(protocolVersion, serverVersion);
        this.session = session;
        this.codec = ToViaBedrock.getCodec(protocolVersion);
        this.cloudburstCodec = GeyserReversion.OXY_CODEC_MAPPER.get(protocolVersion);
        this.helper = this.codec.createHelper();
        this.cloudburstHelper = this.cloudburstCodec.createHelper();

        this.helper.setBlockDefinitions(new BlockDefinitionRegistryMapper(this));
        this.helper.setItemDefinitions(SimpleDefinitionRegistry.<ItemDefinition>builder()
                .add(new SimpleItemDefinition("minecraft:empty", 0, false))
                .build());
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
