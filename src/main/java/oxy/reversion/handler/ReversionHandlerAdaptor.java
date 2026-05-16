package oxy.reversion.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import lombok.RequiredArgsConstructor;
import org.cloudburstmc.protocol.bedrock.netty.BedrockPacketWrapper;
import org.cloudburstmc.protocol.bedrock.netty.codec.packet.BedrockPacketCodec;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemComponentPacket;
import oxy.reversion.user.ReversionUser;
import oxy.reversion.util.protocol.PacketTranslatorUtil;
import oxy.reversion.util.registry.RegistryUtil;

import java.util.List;

@RequiredArgsConstructor
public class ReversionHandlerAdaptor extends MessageToMessageCodec<BedrockPacketWrapper, BedrockPacketWrapper> {
    private final ReversionUser user;
    private final BedrockPacketCodec codec;

    public static final String NAME = "reversion-packet-translator";

    @Override
    protected void encode(ChannelHandlerContext ctx, BedrockPacketWrapper msg, List<Object> out) {
        if (user.getSession().isClosed()) {
            return;
        }

        if (msg.getPacket() instanceof ItemComponentPacket packet) {
            RegistryUtil.onItemComponent(user, packet);
        }

        final BedrockPacket packet = PacketTranslatorUtil.translateClientbound(this.user, msg.getPacket());
        if (packet == null) {
            return;
        }

        msg.setPacketBuffer(null);

        ByteBuf buf = ctx.alloc().buffer(128);
        try {
            msg.setPacketId(this.codec.getPacketId(packet));
            this.codec.encodeHeader(buf, msg);
            this.codec.getCodec().tryEncode(this.user.getCloudburstClientCodecHelper(), buf, packet);

            msg.setPacketBuffer(buf.retain());
            out.add(msg.retain());
        } catch (Exception ignored) {
        } finally {
            buf.release();
        }
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, BedrockPacketWrapper msg, List<Object> out) {
        if (user.getSession().isClosed()) {
            return;
        }

        final BedrockPacket packet = PacketTranslatorUtil.translateServerbound(this.user, msg.getPacket());
        if (packet == null) {
            return;
        }

        msg.setPacket(packet);
        out.add(msg.retain());
    }
}
