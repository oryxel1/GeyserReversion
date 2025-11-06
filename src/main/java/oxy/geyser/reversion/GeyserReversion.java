package oxy.geyser.reversion;

import com.github.blackjack200.ouranos.ProtocolInfo;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.util.NettyRuntime;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.internal.SystemPropertyUtil;
import lombok.SneakyThrows;
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;
import org.cloudburstmc.netty.handler.codec.raknet.server.RakServerOfflineHandler;
import org.cloudburstmc.netty.handler.codec.raknet.server.RakServerRateLimiter;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.v818.Bedrock_v818;
import org.cloudburstmc.protocol.bedrock.data.EncodingSettings;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.event.bedrock.SessionLoginEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.configuration.GeyserConfiguration;
import org.geysermc.geyser.network.netty.Bootstraps;
import org.geysermc.geyser.network.netty.GeyserServer;
import org.geysermc.geyser.network.netty.handler.RakConnectionRequestHandler;
import org.geysermc.geyser.network.netty.handler.RakGeyserRateLimiter;
import org.geysermc.geyser.network.netty.handler.RakPingHandler;
import org.geysermc.geyser.network.netty.proxy.ProxyServerHandler;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.mcprotocollib.network.helper.TransportHelper;
import oxy.geyser.reversion.handler.init.TranslatorServerInitializer;
import oxy.geyser.reversion.util.GeyserUtil;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;

import static org.cloudburstmc.netty.channel.raknet.RakConstants.DEFAULT_GLOBAL_PACKET_LIMIT;
import static org.cloudburstmc.netty.channel.raknet.RakConstants.DEFAULT_PACKET_LIMIT;

public class GeyserReversion implements Extension {
    public static BedrockCodec OLDEST_GEYSER_CODEC = fixCodec(Bedrock_v818.CODEC);
    public static com.github.blackjack200.ouranos.shaded.protocol.bedrock.codec.BedrockCodec OLDEST_GEYSER_OXY_CODEC;
    static {
        com.github.blackjack200.ouranos.shaded.protocol.bedrock.codec.BedrockCodecHelper helper = com.github.blackjack200.ouranos.shaded.protocol.bedrock.codec.v818.Bedrock_v818.CODEC.createHelper();
        helper.setEncodingSettings(com.github.blackjack200.ouranos.shaded.protocol.bedrock.data.EncodingSettings.builder().maxListSize(Integer.MAX_VALUE).maxByteArraySize(Integer.MAX_VALUE).maxNetworkNBTSize(Integer.MAX_VALUE).maxItemNBTSize(Integer.MAX_VALUE).maxStringLength(Integer.MAX_VALUE).build());
        OLDEST_GEYSER_OXY_CODEC = com.github.blackjack200.ouranos.shaded.protocol.bedrock.codec.v818.Bedrock_v818.CODEC.toBuilder().helper(() -> helper).build();
    }

    private static BedrockCodec fixCodec(BedrockCodec codec) {
        BedrockCodecHelper helper = codec.createHelper();
        helper.setEncodingSettings(EncodingSettings.builder().maxListSize(Integer.MAX_VALUE).maxByteArraySize(Integer.MAX_VALUE).maxNetworkNBTSize(Integer.MAX_VALUE).maxItemNBTSize(Integer.MAX_VALUE).maxStringLength(Integer.MAX_VALUE).build());
        return codec.toBuilder().helper(() -> helper).build();
    }

    private static final TransportHelper.TransportType TRANSPORT = TransportHelper.TRANSPORT_TYPE;

    // Fucking hell.
    @SneakyThrows
    @Subscribe
    public void onGeyserPostInitializeEvent(GeyserPostInitializeEvent event) {
        final GeyserImpl geyser = GeyserImpl.getInstance();
        geyser.getGeyserServer().shutdown();

        Integer bedrockThreadCount = Integer.getInteger("Geyser.BedrockNetworkThreads");
        if (bedrockThreadCount == null) {
            // Copy the code from Netty's default thread count fallback
            bedrockThreadCount = Math.max(1, SystemPropertyUtil.getInt("io.netty.eventLoopThreads", NettyRuntime.availableProcessors() * 2));
        }

        final EventLoopGroup group = TRANSPORT.eventLoopGroupFactory().apply(Bootstraps.isReusePortAvailable() ?  Integer.getInteger("Geyser.ListenCount", 1) : 1, new DefaultThreadFactory("GeyserServer", true));
        final EventLoopGroup childGroup = TRANSPORT.eventLoopGroupFactory().apply(bedrockThreadCount, new DefaultThreadFactory("GeyserServerChild", true));

        TranslatorServerInitializer serverInitializer = new TranslatorServerInitializer(geyser);

        int rakPacketLimit = positivePropOrDefault("Geyser.RakPacketLimit", DEFAULT_PACKET_LIMIT);
        int rakGlobalPacketLimit = positivePropOrDefault("Geyser.RakGlobalPacketLimit", DEFAULT_GLOBAL_PACKET_LIMIT);
        boolean rakSendCookie = Boolean.parseBoolean(System.getProperty("Geyser.RakSendCookie", "true"));

        final ServerBootstrap bootstrap = new ServerBootstrap()
                .channelFactory(RakChannelFactory.server(TRANSPORT.datagramChannelClass()))
                .group(group, childGroup)
                .option(RakChannelOption.RAK_HANDLE_PING, true)
                .option(RakChannelOption.RAK_MAX_MTU, geyser.getConfig().getMtu())
                .option(RakChannelOption.RAK_PACKET_LIMIT, rakPacketLimit)
                .option(RakChannelOption.RAK_GLOBAL_PACKET_LIMIT, rakGlobalPacketLimit)
                .option(RakChannelOption.RAK_SEND_COOKIE, rakSendCookie)
                .childHandler(serverInitializer);

        Bootstraps.setupBootstrap(bootstrap);

        final Field field = GeyserServer.class.getDeclaredField("bootstrapFutures");
        field.setAccessible(true);

        final GeyserConfiguration config = geyser.getConfig();
        final ChannelFuture[] futures = (ChannelFuture[]) field.get(geyser.getGeyserServer());
        for (int i = 0; i < futures.length; i++) {
            ChannelFuture future = bootstrap.bind(new InetSocketAddress(config.getBedrock().address(), config.getBedrock().port()));
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

    private void modifyHandlers(ChannelFuture future) {
        Channel channel = future.channel();
        // Add our ping handler
        channel.pipeline()
                .addFirst(RakConnectionRequestHandler.NAME, new RakConnectionRequestHandler(GeyserImpl.getInstance().getGeyserServer()))
                .addAfter(RakServerOfflineHandler.NAME, RakPingHandler.NAME, new RakPingHandler(GeyserImpl.getInstance().getGeyserServer()));

        // Add proxy handler
        boolean isProxyProtocol = GeyserImpl.getInstance().getConfig().getBedrock().isEnableProxyProtocol();
        if (isProxyProtocol) {
            channel.pipeline().addFirst("proxy-protocol-decoder", new ProxyServerHandler());
        }

        boolean isWhitelistedProxyProtocol = isProxyProtocol && !GeyserImpl.getInstance().getConfig().getBedrock().getProxyProtocolWhitelistedIPs().isEmpty();
        if (Boolean.parseBoolean(System.getProperty("Geyser.RakRateLimitingDisabled", "false")) || isWhitelistedProxyProtocol) {
            // We would already block any non-whitelisted IP addresses in onConnectionRequest so we can remove the rate limiter
            channel.pipeline().remove(RakServerRateLimiter.NAME);
        } else {
            // Use our own rate limiter to allow multiple players from the same IP
            channel.pipeline().replace(RakServerRateLimiter.NAME, RakGeyserRateLimiter.NAME, new RakGeyserRateLimiter(channel));
        }
    }

    private int positivePropOrDefault(String property, int defaultValue) {
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
