package oxy.reversion.util.Initializer;

import io.netty.channel.Channel;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.util.concurrent.DefaultThreadFactory;
import lombok.Getter;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.netty.codec.packet.BedrockPacketCodec;
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockServerInitializer;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.network.GeyserBedrockPeer;
import org.geysermc.geyser.network.InvalidPacketHandler;
import org.geysermc.geyser.session.GeyserSession;
import oxy.reversion.handler.ReversionUpstreamHandler;

import java.net.InetSocketAddress;

public class CustomServerInitializer extends BedrockServerInitializer {
    private final GeyserImpl geyser;
    private final boolean rakCookiesEnabled;
    // There is a constructor that doesn't require inputting threads, but older Netty versions don't have it
    @Getter
    private final DefaultEventLoopGroup eventLoopGroup = new DefaultEventLoopGroup(0, new DefaultThreadFactory("Geyser player thread"));

    public CustomServerInitializer(GeyserImpl geyser, boolean rakCookiesEnabled) {
        this.geyser = geyser;
        this.rakCookiesEnabled = rakCookiesEnabled;
    }

    @Override
    protected void preInitChannel(Channel channel) throws Exception {
        if (!rakCookiesEnabled) {
            channel.setOption(RakChannelOption.RAK_PROTOCOL_VERSION, 11);
        }
        super.preInitChannel(channel);
    }

    @Override
    public void initSession(@NonNull BedrockServerSession bedrockServerSession) {
        try {
            if (this.geyser.getGeyserServer().getProxiedAddresses() != null) {
                InetSocketAddress address = this.geyser.getGeyserServer().getProxiedAddresses().get((InetSocketAddress) bedrockServerSession.getSocketAddress());
                if (address != null) {
                    ((GeyserBedrockPeer) bedrockServerSession.getPeer()).setProxiedAddress(address);
                }
            }

            bedrockServerSession.setLogging(this.geyser.config().debugMode());
            GeyserSession session = new GeyserSession(this.geyser, bedrockServerSession, this.eventLoopGroup.next());

            if (!bedrockServerSession.isSubClient()) {
                Channel channel = bedrockServerSession.getPeer().getChannel();
                channel.pipeline().addAfter(BedrockPacketCodec.NAME, InvalidPacketHandler.NAME, new InvalidPacketHandler(session));
            }

            bedrockServerSession.setPacketHandler(new ReversionUpstreamHandler(this.geyser, session));
        } catch (Throwable e) {
            // Error must be caught or it will be swallowed
            this.geyser.getLogger().error("Error occurred while initializing player!", e);
            bedrockServerSession.disconnect(e.getMessage());
        }
    }

    @Override
    protected BedrockPeer createPeer(Channel channel) {
        return new GeyserBedrockPeer(channel, this::createSession);
    }
}