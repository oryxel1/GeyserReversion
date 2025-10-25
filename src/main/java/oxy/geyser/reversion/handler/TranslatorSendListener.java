package oxy.geyser.reversion.handler;

import lombok.NonNull;

import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.geyser.session.UpstreamSession;
import oxy.geyser.reversion.GeyserReversion;
import oxy.geyser.reversion.session.GeyserTranslatedUser;
import oxy.geyser.reversion.util.PacketUtil;

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
        if (this.user != null) {
            oxy.toviabedrock.shaded.protocol.bedrock.packet.BedrockPacket oxyPacket = PacketUtil.toOxy(this.user, packet);
            if (oxyPacket != null) {
                this.user.getWorldReader().onClientbound(oxyPacket);
                oxyPacket = this.user.translateClientbound(oxyPacket);

                final BedrockPacket translated = PacketUtil.toCloudburstOld(this.user, oxyPacket);
                if (translated != null) {
                    super.sendPacket(translated);
                }
            }
        } else {
            super.sendPacket(packet);
        }
    }

    @Override
    public void sendPacketImmediately(@NonNull BedrockPacket packet) {
        if (this.user != null) {
            oxy.toviabedrock.shaded.protocol.bedrock.packet.BedrockPacket oxyPacket = PacketUtil.toOxy(this.user, packet);
            if (oxyPacket != null) {
                this.user.getWorldReader().onClientbound(oxyPacket);
                oxyPacket = this.user.translateClientbound(oxyPacket);

                final BedrockPacket translated = PacketUtil.toCloudburstOld(this.user, oxyPacket);
                if (translated != null) {
                    super.sendPacketImmediately(translated);
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