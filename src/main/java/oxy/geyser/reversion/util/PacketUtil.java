package oxy.geyser.reversion.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import oxy.geyser.reversion.GeyserReversion;
import oxy.geyser.reversion.session.GeyserTranslatedUser;

public class PacketUtil {
    public static com.github.blackjack200.ouranos.shaded.protocol.bedrock.packet.BedrockPacket toOxy(final GeyserTranslatedUser user, final BedrockPacket packet) {
        if (user.getCloudburstCodec().getPacketDefinition(packet.getClass()) == null) {
            return null;
        }

        final ByteBuf decoded = Unpooled.buffer();
        try {
            user.getCloudburstCodec().tryEncode(user.getCloudburstHelper(), decoded, packet);

            return user.getCodec().tryDecode(user.getHelper(), decoded, user.getCloudburstCodec().getPacketDefinition(packet.getClass()).getId());
        } catch (Exception ignored) {
            ignored.printStackTrace();
            return null;
        } finally {
            decoded.release();
        }
    }

    public static com.github.blackjack200.ouranos.shaded.protocol.bedrock.packet.BedrockPacket toOxyNew(final GeyserTranslatedUser user, final BedrockPacket packet) {
        if (GeyserReversion.OLDEST_GEYSER_CODEC.getPacketDefinition(packet.getClass()) == null) {
            return null;
        }

        final ByteBuf decoded = Unpooled.buffer();
        try {
            GeyserReversion.OLDEST_GEYSER_CODEC.tryEncode(user.getCloudburstLatestHelper(), decoded, packet);

            return GeyserReversion.OLDEST_GEYSER_OXY_CODEC.tryDecode(user.getLatestHelper(), decoded, GeyserReversion.OLDEST_GEYSER_CODEC.getPacketDefinition(packet.getClass()).getId());
        } catch (Exception ignored) {
            ignored.printStackTrace();
            return null;
        } finally {
            decoded.release();
        }
    }

    public static BedrockPacket toCloudburstMCLatest(final GeyserTranslatedUser user, com.github.blackjack200.ouranos.shaded.protocol.bedrock.packet.BedrockPacket packet) {
        if (GeyserReversion.OLDEST_GEYSER_OXY_CODEC.getPacketDefinition(packet.getClass()) == null) {
            return null;
        }

        final ByteBuf decoded = Unpooled.buffer();
        try {
            GeyserReversion.OLDEST_GEYSER_OXY_CODEC.tryEncode(user.getLatestHelper(), decoded, packet);

            return GeyserReversion.OLDEST_GEYSER_CODEC.tryDecode(user.getCloudburstLatestHelper(), decoded, GeyserReversion.OLDEST_GEYSER_OXY_CODEC.getPacketDefinition(packet.getClass()).getId());
        } catch (Exception ignored) {
            ignored.printStackTrace();
            return null;
        } finally {
            decoded.release();
        }
    }

    public static BedrockPacket toCloudburstOld(final GeyserTranslatedUser user, com.github.blackjack200.ouranos.shaded.protocol.bedrock.packet.BedrockPacket packet) {
        if (user.getCodec().getPacketDefinition(packet.getClass()) == null) {
            return null;
        }

        final ByteBuf decoded = Unpooled.buffer();
        try {
            user.getCodec().tryEncode(user.getHelper(), decoded, packet);

            return user.getCloudburstCodec().tryDecode(user.getCloudburstHelper(), decoded, user.getCodec().getPacketDefinition(packet.getClass()).getId());
        } catch (Exception ignored) {
            ignored.printStackTrace();
            return null;
        } finally {
            decoded.release();
        }
    }
}
