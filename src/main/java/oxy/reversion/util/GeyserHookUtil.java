package oxy.reversion.util;

import io.netty.channel.Channel;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.netty.codec.packet.BedrockPacketCodec;
import org.geysermc.geyser.session.GeyserSession;
import oxy.reversion.handler.ReversionHandlerAdaptor;
import oxy.reversion.handler.SpoofedUpstreamHandler;
import oxy.reversion.user.ReversionUser;

import java.lang.reflect.Field;

public class GeyserHookUtil {
    public static void hook(final ReversionUser user) {
        try {
            BedrockServerSession session = user.getSession().getUpstream().getSession();
            final Channel channel = session.getPeer().getChannel();
            channel.pipeline().addAfter(BedrockPacketCodec.NAME, ReversionHandlerAdaptor.NAME,
                    new ReversionHandlerAdaptor(user, (BedrockPacketCodec) channel.pipeline().get(BedrockPacketCodec.NAME)));

            injectCloudburstUpstream(user.getSession(), session);
        } catch (Exception ignored) {
            user.getSession().disconnect("Failed to hook into bedrock channel pipeline!");
        }
    }

    private static void injectCloudburstUpstream(final GeyserSession session, final BedrockServerSession serverSession) throws Exception {
        final Field upstream = GeyserSession.class.getDeclaredField("upstream");
        upstream.setAccessible(true);

        upstream.set(session, new SpoofedUpstreamHandler(session.getUpstream(), serverSession));
    }

}
