package oxy.geyser.reversion.session;

import com.github.blackjack200.ouranos.session.SpecialOuranosSession;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.Getter;
import lombok.Setter;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.common.DefinitionRegistry;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;
import oxy.geyser.reversion.DuplicatedProtocolInfo;
import oxy.geyser.reversion.util.PendingBedrockAuthentication;

@Getter @Setter
public class GeyserTranslatedUser extends SpecialOuranosSession {
    private final GeyserSession session;

    private final BedrockCodec cloudburstClientCodec;
    private final BedrockCodecHelper cloudburstClientCodecHelper;

    private final BedrockCodec cloudburstServerCodec;
    private final BedrockCodecHelper cloudburstServerCodecHelper;

    private boolean authenticated;

    public GeyserTranslatedUser(int protocolVersion, int serverVersion, GeyserSession session) {
        super(protocolVersion, serverVersion);
        this.session = session;

        this.cloudburstClientCodec = DuplicatedProtocolInfo.getPacketCodec(protocolVersion);
        this.cloudburstServerCodec = DuplicatedProtocolInfo.getPacketCodec(serverVersion);

        this.cloudburstClientCodecHelper = this.cloudburstClientCodec.createHelper();
        this.cloudburstServerCodecHelper = this.cloudburstServerCodec.createHelper();

        this.cloudburstClientCodecHelper.setBlockDefinitions(new DefinitionRegistry<>() {
            @Override
            public BlockDefinition getDefinition(int runtimeId) {
                return () -> runtimeId;
            }

            @Override
            public boolean isRegistered(BlockDefinition definition) {
                return true;
            }
        });
        this.cloudburstServerCodecHelper.setBlockDefinitions(this.cloudburstClientCodecHelper.getBlockDefinitions());
    }

    @Override
    public void sendUpstreamPacket(com.github.blackjack200.ouranos.shaded.protocol.bedrock.packet.BedrockPacket bedrockPacket) {
        final ByteBuf input = Unpooled.buffer();

        BedrockPacket packet = null;
        try {

            this.encodeClient(bedrockPacket, input);
            packet = this.decodeClient(input, this.getClientCodec().getPacketDefinition(bedrockPacket.getClass()).getId());
        }  catch (Exception ignored) {
        } finally {
            input.release();
        }

        if (packet == null) {
            return;
        }

        session.getUpstream().getSession().sendPacket(packet);
    }

    @Override
    public void sendDownstreamPacket(com.github.blackjack200.ouranos.shaded.protocol.bedrock.packet.BedrockPacket bedrockPacket) {
        final ByteBuf input = Unpooled.buffer();

        BedrockPacket packet = null;
        try {

            this.encodeServer(bedrockPacket, input);
            packet = this.decodeServer(input, this.getClientCodec().getPacketDefinition(bedrockPacket.getClass()).getId());
        }  catch (Exception ignored) {
        } finally {
            input.release();
        }

        if (packet == null) {
            return;
        }

        Registries.BEDROCK_PACKET_TRANSLATORS.translate(packet.getClass(), packet, session, false);
    }

    public final void encodeClient(BedrockPacket packet, ByteBuf output) {
        this.cloudburstClientCodec.tryEncode(this.cloudburstClientCodecHelper, output, packet);
    }

    public final void encodeServer(BedrockPacket packet, ByteBuf output) {
        this.cloudburstServerCodec.tryEncode(this.cloudburstServerCodecHelper, output, packet);
    }

    public final BedrockPacket decodeClient(ByteBuf input, int id) {
        return this.cloudburstClientCodec.tryDecode(this.cloudburstClientCodecHelper, input, id);
    }

    public final BedrockPacket decodeServer(ByteBuf input, int id) {
        return this.cloudburstServerCodec.tryDecode(this.cloudburstServerCodecHelper, input, id);
    }
}
