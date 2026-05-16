package oxy.reversion.handler;

import lombok.NonNull;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.geyser.session.UpstreamSession;
import oxy.reversion.GeyserReversion;

public class SpoofedUpstreamHandler extends UpstreamSession {
    private final UpstreamSession prevSession;

    public SpoofedUpstreamHandler(UpstreamSession prevSession, BedrockServerSession session) {
        super(session);

        this.prevSession = prevSession;
    }

    @Override
    public void disconnect(String reason) {
        this.prevSession.disconnect(reason);
    }

    @Override
    public void sendPacket(@NonNull BedrockPacket packet) {
        this.prevSession.sendPacket(packet);
    }

    @Override
    public void sendPacketImmediately(@NonNull BedrockPacket packet) {
        this.prevSession.sendPacketImmediately(packet);
    }

    @Override
    public int getProtocolVersion() {
        return GeyserReversion.TARGET_CODEC.getProtocolVersion();
    }
}
