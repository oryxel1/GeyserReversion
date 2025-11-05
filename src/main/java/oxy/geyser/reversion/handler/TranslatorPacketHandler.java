package oxy.geyser.reversion.handler;

import com.github.blackjack200.ouranos.ProtocolInfo;
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
import oxy.geyser.reversion.util.PacketUtil;

import java.lang.reflect.Field;

public class TranslatorPacketHandler extends UpstreamPacketHandler {
    @Getter
    private GeyserTranslatedUser user;

    public TranslatorPacketHandler(GeyserImpl geyser, GeyserSession session) {
        super(geyser, session);
    }

    private int cachedProtocolVersion;
    @Override
    public PacketSignal handlePacket(BedrockPacket packet) {
        if (packet instanceof RequestNetworkSettingsPacket networkSettingsPacket) {
            if (kickIfNotSupported(networkSettingsPacket.getProtocolVersion())) {
                return PacketSignal.HANDLED;
            }
            this.cachedProtocolVersion = networkSettingsPacket.getProtocolVersion();

            if (GameProtocol.getBedrockCodec(this.cachedProtocolVersion) == null) {
                networkSettingsPacket.setProtocolVersion(GeyserReversion.OLDEST_GEYSER_CODEC.getProtocolVersion());
            }
        } else if (packet instanceof LoginPacket loginPacket) {
            if (this.cachedProtocolVersion == 0) { // Older versions don't send this.
                this.cachedProtocolVersion = loginPacket.getProtocolVersion();

                try {
                    final Field field = UpstreamPacketHandler.class.getDeclaredField("networkSettingsRequested");
                    field.setAccessible(true);
                    field.set(this, true);
                } catch (Exception e) {
                    session.disconnect("Some expection occured when you trying to join!");
                    e.printStackTrace();
                }
                session.getUpstream().getSession().setCodec(DuplicatedProtocolInfo.getPacketCodec(this.cachedProtocolVersion));
            }

            final int pv = loginPacket.getProtocolVersion();
            if (kickIfNotSupported(pv)) {
                return PacketSignal.HANDLED;
            }

            if (GameProtocol.getBedrockCodec(pv) == null) {
                this.user = new GeyserTranslatedUser(pv, GeyserReversion.OLDEST_GEYSER_CODEC.getProtocolVersion(), this.session);
                loginPacket.setProtocolVersion(GeyserReversion.OLDEST_GEYSER_CODEC.getProtocolVersion());
            }
        }

        if (this.user != null && !(packet instanceof LoginPacket)) {
            com.github.blackjack200.ouranos.shaded.protocol.bedrock.packet.BedrockPacket oxyPacket = PacketUtil.toOxy(this.user, packet);
            if (oxyPacket != null) {
                oxyPacket = this.user.translateServerbound(oxyPacket);

                if (oxyPacket != null) {
                    final BedrockPacket translated = PacketUtil.toCloudburstMCLatest(this.user, oxyPacket);
                    if (translated != null) {
                        super.handlePacket(translated);
                    }
                }
            }
        } else {
            super.handlePacket(packet);
        }

        // We trick Geyser into using a different codec, it's wrong now, so we correct it :)
        if (packet instanceof RequestNetworkSettingsPacket) {
            if (GameProtocol.getBedrockCodec(this.cachedProtocolVersion) == null) {
                session.getUpstream().getSession().setCodec(DuplicatedProtocolInfo.getPacketCodec(this.cachedProtocolVersion));
            }
        }

        return PacketSignal.HANDLED;
    }

    private boolean kickIfNotSupported(int protocolVersion) {
        if (ProtocolInfo.getPacketCodec(protocolVersion) == null && GameProtocol.getBedrockCodec(protocolVersion) == null) {
            session.getUpstream().getSession().setCodec(BedrockCompat.disconnectCompat(protocolVersion));
            session.disconnect("Eh your version is old as hell, please update (PV: " + protocolVersion + ").");
            return true;
        }

        return false;
    }
}
