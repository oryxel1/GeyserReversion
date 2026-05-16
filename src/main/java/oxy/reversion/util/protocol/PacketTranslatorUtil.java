package oxy.reversion.util.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import oxy.reversion.GeyserReversion;
import oxy.reversion.user.ReversionUser;

public class PacketTranslatorUtil {
    public static BedrockPacket translateServerbound(ReversionUser user, BedrockPacket packet) {
        final ByteBuf input = Unpooled.buffer(), output = Unpooled.buffer();
        try {
            user.encodeClient(packet, input);

            final int oldId = user.getCloudburstClientCodec().getPacketDefinition(packet.getClass()).getId();
            final Integer newId = user.translateServerbound(input, output, oldId);
            if (newId == null) {
                return null;
            }

            return user.decodeServer(output, newId);
        } catch (Exception exception) {
            if (GeyserReversion.config().debugMode()) {
                GeyserReversion.extensionlogger().severe("Failed to translate " + packet.getPacketType() + " (serverbound)!", exception);
            }
        } finally {
            input.release();
            output.release();
        }
        return null;
    }

    public static BedrockPacket translateClientbound(ReversionUser user, BedrockPacket packet) {
        final ByteBuf input = Unpooled.buffer(), output = Unpooled.buffer();
        try {
            user.encodeServer(packet, input);

            final int oldId = user.getCloudburstServerCodec().getPacketDefinition(packet.getClass()).getId();
            final Integer newId = user.translateClientbound(input, output, oldId);
            if (newId == null) {
                return null;
            }

            return user.decodeClient(output, newId);
        } catch (Exception exception) {
            if (GeyserReversion.config().debugMode()) {
                GeyserReversion.extensionlogger().severe("Failed to translate " + packet.getPacketType() + " (clientbound)!", exception);
            }
        } finally {
            input.release();
            output.release();
        }

        return null;
    }
}
