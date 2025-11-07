package oxy.geyser.reversion.handler;

import com.github.blackjack200.ouranos.ProtocolInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.Getter;
import org.cloudburstmc.protocol.bedrock.codec.compat.BedrockCompat;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.cloudburstmc.protocol.bedrock.packet.RequestNetworkSettingsPacket;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.network.GameProtocol;
import org.geysermc.geyser.network.UpstreamPacketHandler;
import org.geysermc.geyser.session.GeyserSession;
import oxy.geyser.reversion.DuplicatedProtocolInfo;
import oxy.geyser.reversion.GeyserReversion;
import oxy.geyser.reversion.session.GeyserTranslatedUser;
import oxy.geyser.reversion.util.GeyserUtil;

import java.lang.reflect.Field;

public final class TranslatorPacketHandler extends UpstreamPacketHandler {
    @Getter
    private GeyserTranslatedUser user;

    public TranslatorPacketHandler(GeyserImpl geyser, GeyserSession session) {
        super(geyser, session);
    }

    private int clientProtocol = -1;
    @Override
    public PacketSignal handle(RequestNetworkSettingsPacket packet) {
        if (checkCodec(packet.getProtocolVersion())) {
            return PacketSignal.HANDLED;
        }

        this.clientProtocol = packet.getProtocolVersion();

        final boolean needTranslation = GameProtocol.getBedrockCodec(this.clientProtocol) == null;
        if (needTranslation) {
            packet.setProtocolVersion(GeyserReversion.OLDEST_GEYSER_CODEC.getProtocolVersion());
        }

        super.handle(packet);

        if (needTranslation) {
            session.getUpstream().getSession().setCodec(DuplicatedProtocolInfo.getPacketCodec(this.clientProtocol));
        }

        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(LoginPacket packet) {
        if (this.clientProtocol == -1) { // Older versions don't send RequestNetworkSettingsPacket, handle it ourselves!
            this.clientProtocol = packet.getProtocolVersion();

            // Ughhhh, reflection... ugly.
            try {
                final Field field = UpstreamPacketHandler.class.getDeclaredField("networkSettingsRequested");
                field.setAccessible(true);
                field.set(this, true);
            } catch (Exception exception) {
                session.disconnect("Some expection occured when you trying to join!");
                throw new RuntimeException(exception);
            }
        }

        final int pv = packet.getProtocolVersion();
        if (checkCodec(pv)) {
            return PacketSignal.HANDLED;
        }

        if (GameProtocol.getBedrockCodec(pv) == null) {
            this.user = new GeyserTranslatedUser(pv, GeyserReversion.OLDEST_GEYSER_CODEC.getProtocolVersion(), this.session);
            packet.setProtocolVersion(GeyserReversion.OLDEST_GEYSER_CODEC.getProtocolVersion());
            session.getUpstream().getSession().setCodec(DuplicatedProtocolInfo.getPacketCodec(this.clientProtocol));
            GeyserUtil.hook(session);
        }

        return super.handle(packet);
    }

    @Override
    public PacketSignal handlePacket(BedrockPacket packet) {
        if (this.user == null) {
            super.handlePacket(packet);
            return PacketSignal.HANDLED;
        }

        final ByteBuf input = Unpooled.buffer(), output = Unpooled.buffer();
        try {
            this.user.encodeClient(packet, input);

            final int oldId = this.user.getCloudburstClientCodec().getPacketDefinition(packet.getClass()).getId();
            if (!this.user.translateServerbound(input, output, oldId)) {
                return PacketSignal.HANDLED;
            }

            super.handlePacket(this.user.decodeServer(output, this.user.getCloudburstServerCodec().getPacketDefinition(packet.getClass()).getId()));
        } catch (Exception ignored) {
            ignored.printStackTrace();
        } finally {
            input.release();
            output.release();
        }
        return PacketSignal.HANDLED;
    }

    private boolean checkCodec(int protocolVersion) {
        if (ProtocolInfo.getPacketCodec(protocolVersion) == null && GameProtocol.getBedrockCodec(protocolVersion) == null) {
            session.getUpstream().getSession().setCodec(BedrockCompat.disconnectCompat(protocolVersion));
            session.disconnect("Eh your version is old as hell, please update (PV: " + protocolVersion + ").");
            return true;
        }

        return false;
    }
}
