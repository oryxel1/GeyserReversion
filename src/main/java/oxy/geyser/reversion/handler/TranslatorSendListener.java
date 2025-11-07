package oxy.geyser.reversion.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.NonNull;

import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemComponentPacket;
import org.geysermc.geyser.session.UpstreamSession;
import oxy.geyser.reversion.GeyserReversion;
import oxy.geyser.reversion.session.GeyserTranslatedUser;
import oxy.geyser.reversion.util.RegistryUtil;

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
        this.oldSession.disconnect(reason);
    }

    @Override
    public void sendPacket(@NonNull BedrockPacket packet) {
        if (packet instanceof ItemComponentPacket) {
            RegistryUtil.onItemComponent(this.user, (ItemComponentPacket) packet);
        }

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
            final Integer newId = this.user.translateClientbound(input, output, oldId);
            if (newId == null) {
                return null;
            }

            return this.user.decodeClient(output, newId);
        } catch (Exception ignored) {
            ignored.printStackTrace();
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