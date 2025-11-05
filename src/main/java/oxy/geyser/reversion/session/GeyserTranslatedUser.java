package oxy.geyser.reversion.session;

import com.github.blackjack200.ouranos.ProtocolInfo;
import com.github.blackjack200.ouranos.session.OuranosSession;
import com.github.blackjack200.ouranos.shaded.protocol.bedrock.data.definitions.BlockDefinition;
import com.github.blackjack200.ouranos.shaded.protocol.common.DefinitionRegistry;
import lombok.Getter;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemComponentPacket;
import org.cloudburstmc.protocol.common.SimpleDefinitionRegistry;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;
import oxy.geyser.reversion.DuplicatedProtocolInfo;
import oxy.geyser.reversion.GeyserReversion;
import oxy.geyser.reversion.util.PacketUtil;
import com.github.blackjack200.ouranos.shaded.protocol.bedrock.codec.BedrockCodec;
import com.github.blackjack200.ouranos.shaded.protocol.bedrock.codec.BedrockCodecHelper;
import com.github.blackjack200.ouranos.shaded.protocol.bedrock.data.definitions.ItemDefinition;
import com.github.blackjack200.ouranos.shaded.protocol.bedrock.data.inventory.ItemVersion;

@Getter
public class GeyserTranslatedUser extends OuranosSession {
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
        this.codec = ProtocolInfo.getPacketCodec(protocolVersion);
        this.cloudburstCodec = DuplicatedProtocolInfo.getPacketCodec(protocolVersion);
        this.helper = this.codec.createHelper();
        this.latestHelper = GeyserReversion.OLDEST_GEYSER_OXY_CODEC.createHelper();
        this.cloudburstLatestHelper = GeyserReversion.OLDEST_GEYSER_CODEC.createHelper();
        this.cloudburstHelper = this.cloudburstCodec.createHelper();

        this.helper.setBlockDefinitions(new DefinitionRegistry<>() {
            @Override
            public BlockDefinition getDefinition(int runtimeId) {
                return () -> runtimeId;
            }

            @Override
            public boolean isRegistered(BlockDefinition definition) {
                return true;
            }
        });
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
            builder.add(new SimpleItemDefinition(definition.getIdentifier(), definition.getRuntimeId(), definition.getVersion(), definition.isComponentBased(), definition.getComponentData()));
        }

        SimpleDefinitionRegistry<org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition> itemDefinitions = builder.build();
        this.cloudburstHelper.setItemDefinitions(itemDefinitions);
        this.cloudburstLatestHelper.setItemDefinitions(itemDefinitions);
//        System.out.println(itemDefinitions);

        com.github.blackjack200.ouranos.shaded.protocol.common.SimpleDefinitionRegistry.Builder<ItemDefinition> builder1 = com.github.blackjack200.ouranos.shaded.protocol.common.SimpleDefinitionRegistry.<ItemDefinition>builder()
                .add(new com.github.blackjack200.ouranos.shaded.protocol.bedrock.data.definitions.SimpleItemDefinition("minecraft:empty", 0, false));

        for (org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition definition : packet.getItems()) {
            builder1.add(new com.github.blackjack200.ouranos.shaded.protocol.bedrock.data.definitions.SimpleItemDefinition(definition.getIdentifier(), definition.getRuntimeId(), ItemVersion.from(definition.getVersion().ordinal()), definition.isComponentBased(), definition.getComponentData()));
        }

        com.github.blackjack200.ouranos.shaded.protocol.common.SimpleDefinitionRegistry<ItemDefinition> itemDefinitions1 = builder1.build();

        this.helper.setItemDefinitions(itemDefinitions1);
        this.latestHelper.setItemDefinitions(itemDefinitions1);
    }

    @Override
    public void sendUpstreamPacket(com.github.blackjack200.ouranos.shaded.protocol.bedrock.packet.BedrockPacket bedrockPacket) {
        final BedrockPacket packet = PacketUtil.toCloudburstOld(this, bedrockPacket);
        if (packet == null) {
            return;
        }

        session.sendUpstreamPacket(packet);
    }

    @Override
    public void sendDownstreamPacket(com.github.blackjack200.ouranos.shaded.protocol.bedrock.packet.BedrockPacket bedrockPacket) {
        final BedrockPacket packet = PacketUtil.toCloudburstOld(this, bedrockPacket);
        if (packet == null) {
            return;
        }

        Registries.BEDROCK_PACKET_TRANSLATORS.translate(packet.getClass(), packet, session, false);
    }
}
