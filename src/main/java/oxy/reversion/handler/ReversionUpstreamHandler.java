package oxy.reversion.handler;

import com.github.blackjack200.ouranos.ProtocolInfo;
import com.github.blackjack200.ouranos.shaded.protocol.bedrock.codec.v589.Bedrock_v589;
import net.raphimc.minecraftauth.bedrock.model.MinecraftMultiplayerToken;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.compat.BedrockCompat;
import org.cloudburstmc.protocol.bedrock.codec.v544.Bedrock_v544;
import org.cloudburstmc.protocol.bedrock.codec.v575.Bedrock_v575;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.event.bedrock.SessionInitializeEvent;
import org.geysermc.geyser.network.GameProtocol;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.auth.AuthData;
import oxy.reversion.GeyserReversion;
import oxy.reversion.user.ReversionUser;
import oxy.reversion.util.GeyserHookUtil;
import oxy.reversion.util.auth.IncompleteLoginUtil;
import oxy.reversion.util.auth.PendingBedrockAuthentication;
import oxy.reversion.util.protocol.ProtocolSet;
import oxy.reversion.util.duplicate.UpstreamPacketHandler;

import java.util.List;

public class ReversionUpstreamHandler extends UpstreamPacketHandler {
    private ReversionUser user;

    public ReversionUpstreamHandler(GeyserImpl geyser, GeyserSession session) {
        super(geyser, session);
    }

    @Override
    public PacketSignal handle(RequestNetworkSettingsPacket packet) {
        session.getUpstream().getSession().setCodec(ProtocolSet.getPacketCodec(packet.getProtocolVersion()));
        PacketCompressionAlgorithm algorithm = PacketCompressionAlgorithm.ZLIB;

        NetworkSettingsPacket responsePacket = new NetworkSettingsPacket();
        responsePacket.setCompressionAlgorithm(algorithm);
        responsePacket.setCompressionThreshold(512);
        session.sendUpstreamPacketImmediately(responsePacket);
        session.getUpstream().getSession().getPeer().setCompression(compressionStrategy);

        networkSettingsRequested = true;
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(LoginPacket packet) {
        if (!networkSettingsRequested && packet.getProtocolVersion() < Bedrock_v544.CODEC.getProtocolVersion()) {
            networkSettingsRequested = true;
        }

        final int protocolVersion = packet.getProtocolVersion();
        if (!isSupported(protocolVersion)) {
            return PacketSignal.HANDLED;
        }

        // This version is natively supported by Geyser.
        if (GameProtocol.getBedrockCodec(protocolVersion) != null) {
            super.handle(packet);
            return PacketSignal.HANDLED;
        }

        // Spoof this so Geyser allow the player to join...  and set the codec to what the player actually use.
        packet.setProtocolVersion(GeyserReversion.TARGET_CODEC.getProtocolVersion());
        session.getUpstream().getSession().setCodec(ProtocolSet.getPacketCodec(protocolVersion));

        user = new ReversionUser(protocolVersion, GeyserReversion.TARGET_CODEC.getProtocolVersion(), this.session);
        GeyserHookUtil.hook(user);

        // Older version have old auth system, which no longer works, we will have to handle it ourselves.
        if (protocolVersion < Bedrock_v589.CODEC.getProtocolVersion() && session.getGeyser().config().advanced().bedrock().validateBedrockLogin()) {
            this.resourcePackLoadEvent = IncompleteLoginUtil.halfLogin(packet, session, receivedLoginPacket);
            receivedLoginPacket = true;
            return PacketSignal.HANDLED;
        }

        super.handle(packet);
        user.setAuthenticated(true);

        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(ResourcePackClientResponsePacket packet) {
        if (session.getUpstream().isClosed() || session.isClosed()) {
            return PacketSignal.HANDLED;
        }

        if (user.getProtocolId() >= Bedrock_v589.CODEC.getProtocolVersion()
                || packet.getStatus() != ResourcePackClientResponsePacket.Status.COMPLETED
                || !session.getGeyser().config().advanced().bedrock().validateBedrockLogin()
        ) {
            return super.handle(packet);
        }

        // We need to spawn them into a void world so they can log in to their account since auth is broken.
        if (this.finishedResourcePackSending) {
            session.disconnect("Illegal duplicate resource pack response packet received!");
            return PacketSignal.HANDLED;
        }

        this.finishedResourcePackSending = true;
        session.connect();
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(SetLocalPlayerAsInitializedPacket packet) {
        if (this.user == null || this.user.isAuthenticated()) {
            return super.handle(packet);
        }
        final PendingBedrockAuthentication.AuthenticationTask task =
                GeyserReversion.AUTH.getOrCreateTask(
                        session.getAuthData().xuid() + "-" + session.getAuthData().uuid() + "-" + session.getAuthData().name());

        if (task.getAuthentication() != null && task.getAuthentication().isDone()) {
            onMicrosoftLoginComplete(task);
        } else {
            task.resetRunningFlow();
            task.performLoginAttempt(false, code -> {
                if (!this.session.isClosed()) {
                    session.sendMessage("You're on version older than 1.20 which have broken authentication!");
                    session.sendMessage("Please login into your Bedrock account to play on this website: " + code.getVerificationUri());
                    session.sendMessage("Your code is: " + code.getUserCode());
                }
            }).handle((r, e) -> onMicrosoftLoginComplete(task));
        }

        return super.handle(packet);
    }

    private boolean onMicrosoftLoginComplete(PendingBedrockAuthentication.AuthenticationTask task) {
        if (session.isClosed()) {
            return false;
        }

        task.cleanup(); // player is online -> remove pending authentication immediately
        return task.getAuthentication().handle((result, ex) -> {
            if (ex != null) {
                if (GeyserReversion.config().debugMode()) {
                    ex.printStackTrace();
                }
                session.disconnect(ex.toString());
                return false;
            }

            MinecraftMultiplayerToken token = result.getMinecraftMultiplayerToken().getCached();
            this.session.setAuthData(new AuthData(token.getDisplayName(),
                    token.getUuid(), token.getXuid(), this.session.getAuthData().issuedAt(), result.getPlayFabToken().getCached().getPlayFabId()));
            geyser.getSessionManager().addPendingSession(this.session);
            geyser.eventBus().fire(new SessionInitializeEvent(this.session));

            session.sendMessage("Authenticating in the server as " + token.getDisplayName());

            this.user.setAuthenticated(true);
            this.session.authenticate(session.getAuthData().name());
            return true;
        }).getNow(false);
    }

    private boolean isSupported(int protocolVersion) {
        int minProtocolVer = GeyserReversion.config().minProtocolId();
        if (!GeyserReversion.doWeSupportBelow575()) {
            minProtocolVer = Math.max(Bedrock_v575.CODEC.getProtocolVersion(), minProtocolVer);
        }

        if (minProtocolVer != -1) {
            BedrockCodec codec = ProtocolSet.getPacketCodec(minProtocolVer);
            if (codec != null && protocolVersion < minProtocolVer) {
                session.getUpstream().getSession().setCodec(BedrockCompat.disconnectCompat(protocolVersion));
                session.disconnect(GeyserReversion.config().minProtocolKick().replace("%version%", codec.getMinecraftVersion()));
                return false;
            }
        }

        List<Integer> blockProtocols = GeyserReversion.config().blockProtocols();
        if (blockProtocols != null && blockProtocols.contains(protocolVersion)) {
            session.getUpstream().getSession().setCodec(BedrockCompat.disconnectCompat(protocolVersion));
            session.disconnect(GeyserReversion.config().blockedProtocolKick());
            return false;
        }

        if (ProtocolInfo.getPacketCodec(protocolVersion) == null && GameProtocol.getBedrockCodec(protocolVersion) == null) {
            session.getUpstream().getSession().setCodec(BedrockCompat.disconnectCompat(protocolVersion));
            session.disconnect(GeyserReversion.config().versionNotSupportedKick());
            return false;
        }

        return true;
    }
}
