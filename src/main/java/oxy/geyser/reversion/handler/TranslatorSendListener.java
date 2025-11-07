package oxy.geyser.reversion.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.NonNull;

import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.geyser.session.UpstreamSession;
import oxy.geyser.reversion.GeyserReversion;
import oxy.geyser.reversion.session.GeyserTranslatedUser;

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
            final BedrockPacket translated = this.translate(packet);
            if (translated != null) {
                super.sendPacket(translated);
            }
            return;
        }

        super.sendPacket(packet);
    }

    @Override
    public void sendPacketImmediately(@NonNull BedrockPacket packet) {
        if (this.user != null) {
            final BedrockPacket translated = this.translate(packet);
            if (translated != null) {
                super.sendPacketImmediately(translated);
            }
            return;
        }

        super.sendPacketImmediately(packet);
    }

    private BedrockPacket translate(BedrockPacket packet) {
        final ByteBuf input = Unpooled.buffer(), output = Unpooled.buffer();
        try {
            this.user.encodeServer(packet, input);

            final int oldId = this.user.getCloudburstServerCodec().getPacketDefinition(packet.getClass()).getId();
            if (!this.user.translateClientbound(input, output, oldId)) {
                return null;
            }

            return this.user.decodeClient(output, this.user.getCloudburstClientCodec().getPacketDefinition(packet.getClass()).getId());
        } catch (Exception ignored) {
        } finally {
            input.release();
            output.release();
        }

        return null;
    }

    @Override
    public int getProtocolVersion() {
        return this.user == null ? super.getProtocolVersion() : GeyserReversion.OLDEST_GEYSER_CODEC.getProtocolVersion();
    }
}