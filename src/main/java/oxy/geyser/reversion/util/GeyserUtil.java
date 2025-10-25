package oxy.geyser.reversion.util;

import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.UpstreamSession;
import oxy.geyser.reversion.handler.TranslatorPacketHandler;
import oxy.geyser.reversion.handler.TranslatorSendListener;

import java.lang.reflect.Field;

public class GeyserUtil {
    public static void hook(final GeyserSession session) {
        try {
            injectCloudburstUpstream(session, findCloudburstSession(session));
        } catch (Exception ignored) {
            session.disconnect("Failed to hook into cloudburst session!");
        }
    }

    private static void injectCloudburstUpstream(final GeyserSession session, final BedrockServerSession downstream) throws Exception {
        final Field upstream = GeyserSession.class.getDeclaredField("upstream");
        upstream.setAccessible(true);

        final TranslatorPacketHandler handler = (TranslatorPacketHandler) downstream.getPacketHandler();
        if (handler.getUser() == null) {
            return;
        }
        upstream.set(session, new TranslatorSendListener(handler.getUser(), downstream, (UpstreamSession) upstream.get(session)));
    }

    private static BedrockServerSession findCloudburstSession(final GeyserSession connection) throws Exception {
        final Field upstream = GeyserSession.class.getDeclaredField("upstream");
        upstream.setAccessible(true);
        final Object session = upstream.get(connection);
        final Field field = UpstreamSession.class.getDeclaredField("session");
        field.setAccessible(true);
        return (BedrockServerSession) field.get(session);
    }
}
