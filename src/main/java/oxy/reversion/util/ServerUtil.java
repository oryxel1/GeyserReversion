package oxy.reversion.util;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoopGroup;
import io.netty.util.NettyRuntime;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.internal.SystemPropertyUtil;
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory;
import org.cloudburstmc.netty.channel.raknet.config.DefaultRakServerThrottle;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;
import org.cloudburstmc.netty.channel.raknet.config.RakServerCookieMode;
import org.cloudburstmc.netty.handler.codec.raknet.server.RakServerOfflineHandler;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.configuration.GeyserConfig;
import org.geysermc.geyser.network.netty.Bootstraps;
import org.geysermc.geyser.network.netty.GeyserServer;
import org.geysermc.geyser.network.netty.handler.RakConnectionRequestHandler;
import org.geysermc.geyser.network.netty.handler.RakPingHandler;
import org.geysermc.mcprotocollib.network.helper.TransportHelper;
import oxy.reversion.util.initializer.CustomServerInitializer;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;

import static org.cloudburstmc.netty.channel.raknet.RakConstants.DEFAULT_GLOBAL_PACKET_LIMIT;
import static org.cloudburstmc.netty.channel.raknet.RakConstants.DEFAULT_PACKET_LIMIT;

public class ServerUtil {
    private static final TransportHelper.TransportType TRANSPORT = TransportHelper.TRANSPORT_TYPE;

    public static void restartWithInitializer() throws Exception {
        final GeyserImpl geyser = GeyserImpl.getInstance();
        geyser.getGeyserServer().shutdown();

        Integer bedrockThreadCount = Integer.getInteger("Geyser.BedrockNetworkThreads");
        if (bedrockThreadCount == null) {
            // Copy the code from Netty's default thread count fallback
            bedrockThreadCount = Math.max(1, SystemPropertyUtil.getInt("io.netty.eventLoopThreads", NettyRuntime.availableProcessors() * 2));
        }

        final EventLoopGroup group = TRANSPORT.eventLoopGroupFactory().apply(Bootstraps.isReusePortAvailable() ?  Integer.getInteger("Geyser.ListenCount", 1) : 1, new DefaultThreadFactory("GeyserServer", true));
        final EventLoopGroup childGroup = TRANSPORT.eventLoopGroupFactory().apply(bedrockThreadCount, new DefaultThreadFactory("GeyserServerChild", true));


        int rakPacketLimit = positivePropOrDefault("Geyser.RakPacketLimit", DEFAULT_PACKET_LIMIT);
        int rakGlobalPacketLimit = positivePropOrDefault("Geyser.RakGlobalPacketLimit", DEFAULT_GLOBAL_PACKET_LIMIT);
        boolean rakSendCookie = Boolean.parseBoolean(System.getProperty("Geyser.RakSendCookie", "true"));

        int maxConnectionsPerAddress =  positivePropOrDefault("Geyser.MaxConnectionsPerAddress", 10);

        boolean rakRateLimitingDisabled = Boolean.parseBoolean(System.getProperty(
                "Geyser.RakRateLimitingDisabled",
                Boolean.toString(geyser.config().advanced().bedrock().useWaterdogpeForwarding())
        ));

        CustomServerInitializer serverInitializer = new CustomServerInitializer(geyser, rakSendCookie);

        ServerBootstrap bootstrap = new ServerBootstrap()
                .channelFactory(RakChannelFactory.server(TRANSPORT.datagramChannelClass()))
                .group(group, childGroup)
                .option(RakChannelOption.RAK_HANDLE_PING, true)
                .option(RakChannelOption.RAK_MAX_MTU, geyser.config().advanced().bedrock().mtu())
                .option(RakChannelOption.RAK_PACKET_LIMIT, rakRateLimitingDisabled ? 0 : rakPacketLimit)
                .option(RakChannelOption.RAK_GLOBAL_PACKET_LIMIT, rakGlobalPacketLimit)
                .option(RakChannelOption.RAK_PROXY_PROTOCOL, geyser.config().advanced().bedrock().useHaproxyProtocol())
                .option(RakChannelOption.RAK_THROTTLE, rakRateLimitingDisabled ? null : new DefaultRakServerThrottle(maxConnectionsPerAddress, 4_000, 3))
                .option(RakChannelOption.RAK_SERVER_COOKIE_MODE, rakSendCookie ? RakServerCookieMode.ACTIVE : RakServerCookieMode.INVALID)
                .childHandler(serverInitializer);

        Bootstraps.setupBootstrap(bootstrap, TransportHelper.TRANSPORT_TYPE);

        final Field field = GeyserServer.class.getDeclaredField("bootstrapFutures");
        field.setAccessible(true);

        final GeyserConfig config = geyser.config();
        final ChannelFuture[] futures = (ChannelFuture[]) field.get(geyser.getGeyserServer());
        for (int i = 0; i < futures.length; i++) {
            ChannelFuture future = bootstrap.bind(new InetSocketAddress(config.bedrock().address(), config.bedrock().port()));
            modifyHandlers(future);
            futures[i] = future;
        }

        Bootstraps.allOf(futures).join();

        final Field groupField = GeyserServer.class.getDeclaredField("group");
        groupField.setAccessible(true);
        groupField.set(geyser.getGeyserServer(), group);

        final Field childGroupField = GeyserServer.class.getDeclaredField("childGroup");
        childGroupField.setAccessible(true);
        childGroupField.set(geyser.getGeyserServer(), childGroup);

        final Field playerGroupField = GeyserServer.class.getDeclaredField("playerGroup");
        playerGroupField.setAccessible(true);
        playerGroupField.set(geyser.getGeyserServer(), serverInitializer.getEventLoopGroup());
    }

    private static void modifyHandlers(ChannelFuture future) {
        future.addListener((ChannelFutureListener) f -> {
            if (!f.isSuccess()) {
                GeyserImpl.getInstance().getLogger().warning("Not modifying handlers due to exception: " + f.cause());
                return;
            }

            Channel channel = f.channel();
            // Add our ping handler
            channel.pipeline()
                    .addFirst(RakConnectionRequestHandler.NAME, new RakConnectionRequestHandler(GeyserImpl.getInstance().getGeyserServer()))
                    .addAfter(RakServerOfflineHandler.NAME, RakPingHandler.NAME, new RakPingHandler(GeyserImpl.getInstance().getGeyserServer()));
        });
    }

    private static int positivePropOrDefault(String property, int defaultValue) {
        String value = System.getProperty(property);
        try {
            int parsed = value != null ? Integer.parseInt(value) : defaultValue;

            if (parsed < 1) {
                GeyserImpl.getInstance().getLogger().warning(
                        "Non-postive integer value for " + property + ": " + value + ". Using default value: " + defaultValue
                );
                return defaultValue;
            }

            return parsed;
        } catch (NumberFormatException e) {
            GeyserImpl.getInstance().getLogger().warning(
                    "Invalid integer value for " + property + ": " + value + ". Using default value: " + defaultValue
            );
            return defaultValue;
        }
    }
}
