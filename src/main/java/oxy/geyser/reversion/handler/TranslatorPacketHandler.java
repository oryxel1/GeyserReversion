package oxy.geyser.reversion.handler;

import com.github.blackjack200.ouranos.ProtocolInfo;
import com.github.blackjack200.ouranos.shaded.protocol.bedrock.codec.v575.Bedrock_v575;
import com.github.blackjack200.ouranos.shaded.protocol.bedrock.codec.v589.Bedrock_v589;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.Getter;
import net.raphimc.minecraftauth.step.bedrock.StepMCChain;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.compat.BedrockCompat;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.event.bedrock.SessionInitializeEvent;
import org.geysermc.geyser.event.type.SessionLoadResourcePacksEventImpl;
import org.geysermc.geyser.network.GameProtocol;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.auth.AuthData;
import org.geysermc.geyser.text.GeyserLocale;
import org.geysermc.geyser.util.LoginEncryptionUtils;
import oxy.geyser.reversion.DuplicatedProtocolInfo;
import oxy.geyser.reversion.GeyserReversion;
import oxy.geyser.reversion.handler.duplicated.UpstreamPacketHandler;
import oxy.geyser.reversion.session.GeyserTranslatedUser;
import oxy.geyser.reversion.util.ClientDataUtil;
import oxy.geyser.reversion.util.GeyserUtil;
import oxy.geyser.reversion.util.PendingBedrockAuthentication;

import java.util.List;
import java.util.UUID;

public final class TranslatorPacketHandler extends UpstreamPacketHandler {
    @Getter
    private GeyserTranslatedUser user;

    public TranslatorPacketHandler(GeyserImpl geyser, GeyserSession session) {
        super(geyser, session);
    }

    private int clientProtocol = -1;
    @Override
    public PacketSignal handle(RequestNetworkSettingsPacket packet) {
        if (checkCodec(packet.getProtocolVersion())) {
            return PacketSignal.HANDLED;
        }

        this.clientProtocol = packet.getProtocolVersion();

        final boolean needTranslation = GameProtocol.getBedrockCodec(this.clientProtocol) == null;
        if (needTranslation) {
            packet.setProtocolVersion(GeyserReversion.OLDEST_GEYSER_CODEC.getProtocolVersion());
        }

        super.handle(packet);

        if (needTranslation) {
            session.getUpstream().getSession().setCodec(DuplicatedProtocolInfo.getPacketCodec(this.clientProtocol));
        }

        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(LoginPacket packet) {
        if (this.clientProtocol == -1) { // Older versions don't send RequestNetworkSettingsPacket, handle it ourselves!
            this.clientProtocol = packet.getProtocolVersion();
        }

        final int pv = packet.getProtocolVersion();
        if (checkCodec(pv)) {
            return PacketSignal.HANDLED;
        }

        if (GameProtocol.getBedrockCodec(pv) == null) {
            this.user = new GeyserTranslatedUser(pv, GeyserReversion.OLDEST_GEYSER_CODEC.getProtocolVersion(), this.session);
            packet.setProtocolVersion(GeyserReversion.OLDEST_GEYSER_CODEC.getProtocolVersion());
            session.getUpstream().getSession().setCodec(DuplicatedProtocolInfo.getPacketCodec(this.clientProtocol));
            GeyserUtil.hook(session);
        }

        // The player is using the version before authentication change, damn it. Let's handle this ourselves...
        if (this.clientProtocol < Bedrock_v589.CODEC.getProtocolVersion()) {
            if (geyser.isShuttingDown() || geyser.isReloading()) {
                // Don't allow new players in if we're no longer operating
                session.disconnect(GeyserLocale.getLocaleStringLog("geyser.core.shutdown.kick.message"));
                return PacketSignal.HANDLED;
            }

            if (geyser.getSessionManager().reachedMaxConnectionsPerAddress(session)) {
                session.disconnect("Too many connections are originating from this location!");
                return PacketSignal.HANDLED;
            }

            // Set the block translation based off of version
            session.setBlockMappings(BlockRegistries.BLOCKS.forVersion(packet.getProtocolVersion()));
            session.setItemMappings(Registries.ITEMS.forVersion(packet.getProtocolVersion()));

            // Call this just to set the client data lol...
            ClientDataUtil.setClientData(session, packet);

            if (session.isClosed()) {
                return PacketSignal.HANDLED;
            }

            PlayStatusPacket playStatus = new PlayStatusPacket();
            playStatus.setStatus(PlayStatusPacket.Status.LOGIN_SUCCESS);
            session.sendUpstreamPacket(playStatus);

            this.resourcePackLoadEvent = new SessionLoadResourcePacksEventImpl(session);
            this.geyser.eventBus().fireEventElseKick(this.resourcePackLoadEvent, session);
            if (session.isClosed()) {
                // Can happen if an error occurs in the resource pack event; that'll disconnect the player
                return PacketSignal.HANDLED;
            }

            // Let's just send player stuff, don't spawn them in yet....
            ResourcePacksInfoPacket resourcePacksInfo = new ResourcePacksInfoPacket();
            resourcePacksInfo.getResourcePackInfos().addAll(this.resourcePackLoadEvent.infoPacketEntries());
            resourcePacksInfo.setVibrantVisualsForceDisabled(!session.isAllowVibrantVisuals());

            resourcePacksInfo.setForcedToAccept(GeyserImpl.getInstance().getConfig().isForceResourcePacks());
            resourcePacksInfo.setWorldTemplateId(UUID.randomUUID());
            resourcePacksInfo.setWorldTemplateVersion("*");
            session.sendUpstreamPacket(resourcePacksInfo);

            GeyserLocale.loadGeyserLocale(session.locale());
            return PacketSignal.HANDLED;
        }

        super.handle(packet);
        if (!session.getUpstream().isClosed() && this.user != null) {
            this.user.setAuthenticated(true);
        }

        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(ResourcePackClientResponsePacket packet) {
        if (session.getUpstream().isClosed() || session.isClosed()) {
            return PacketSignal.HANDLED;
        }

        if (this.clientProtocol >= Bedrock_v589.CODEC.getProtocolVersion() || packet.getStatus() != ResourcePackClientResponsePacket.Status.COMPLETED) {
            return super.handle(packet); // New authentication supported, allow them to login!
        }

        if (this.finishedResourcePackSending) {
            session.disconnect("Illegal duplicate resource pack response packet received!");
            return PacketSignal.HANDLED;
        }

        this.finishedResourcePackSending = true;
        session.connect(); // We have to spawn player in the void world!
        this.authenticate();

        return PacketSignal.HANDLED;
    }


    private void authenticate() {
        // This just looks cool - idk
        // Yes it does! (oxy)
        SetTimePacket packet = new SetTimePacket();
        packet.setTime(16000);
        session.sendUpstreamPacket(packet);

        final PendingBedrockAuthentication.AuthenticationTask task = GeyserReversion.AUTH.getOrCreateTask(session.getAuthData().xuid());

        if (task.getAuthentication() != null && task.getAuthentication().isDone()) {
            onMicrosoftLoginComplete(task);
        } else {
            task.resetRunningFlow();
            task.performLoginAttempt(code -> LoginEncryptionUtils.buildAndShowMicrosoftCodeWindow(this.session, code)).handle((r, e) -> onMicrosoftLoginComplete(task));
        }
    }

    public boolean onMicrosoftLoginComplete(PendingBedrockAuthentication.AuthenticationTask task) {
        task.cleanup();
        return task.getAuthentication().handle((result, ex) -> {
            this.session.closeForm();
            this.session.sendUpstreamPacket(new ClientboundCloseFormPacket()); // Send again this just in case...

            StepMCChain.MCChain mcChain = result.session().getMcChain();

            this.session.setAuthData(new AuthData(mcChain.getDisplayName(), mcChain.getId(), mcChain.getXuid(), this.session.getAuthData().issuedAt()));
            geyser.getSessionManager().addPendingSession(this.session);
            geyser.eventBus().fire(new SessionInitializeEvent(this.session));

            this.user.setAuthenticated(true);
            this.session.authenticate(session.getAuthData().name());
            return true;
        }).getNow(false);
    }

    @Override
    public PacketSignal handlePacket(BedrockPacket packet) {
        if (this.user == null) {
            super.handlePacket(packet);
            return PacketSignal.HANDLED;
        }

        final ByteBuf input = Unpooled.buffer(), output = Unpooled.buffer();
        try {
            this.user.encodeClient(packet, input);

            final int oldId = this.user.getCloudburstClientCodec().getPacketDefinition(packet.getClass()).getId();
            final Integer newId = this.user.translateServerbound(input, output, oldId);
            if (newId == null) {
                return PacketSignal.HANDLED;
            }

            super.handlePacket(this.user.decodeServer(output, newId));
        } catch (Exception exception) {
            if (GeyserReversion.CONFIG.debugMode()) {
                GeyserReversion.LOGGER.severe("Failed to translate " + packet.getPacketType() + " (serverbound)!", exception);
            }
        } finally {
            input.release();
            output.release();
        }
        return PacketSignal.HANDLED;
    }

    private boolean checkCodec(int protocolVersion) {
        int minProtocolVer = GeyserReversion.CONFIG.minProtocolId();
        if (GeyserReversion.INJECTION_FAILED) {
            minProtocolVer = Math.max(Bedrock_v575.CODEC.getProtocolVersion(), minProtocolVer);
        }

        if (minProtocolVer != -1) {
            BedrockCodec codec = DuplicatedProtocolInfo.getPacketCodec(minProtocolVer);
            if (codec != null && protocolVersion < minProtocolVer) {
                session.getUpstream().getSession().setCodec(BedrockCompat.disconnectCompat(protocolVersion));
                session.disconnect(GeyserReversion.CONFIG.minProtocolKick().replace("%version%", codec.getMinecraftVersion()));
                return true;
            }
        }

        List<Integer> blockProtocols = GeyserReversion.CONFIG.blockProtocols();
        if (blockProtocols != null && blockProtocols.contains(protocolVersion)) {
            session.getUpstream().getSession().setCodec(BedrockCompat.disconnectCompat(protocolVersion));
            session.disconnect(GeyserReversion.CONFIG.blockedProtocolKick());
            return true;
        }

        if (ProtocolInfo.getPacketCodec(protocolVersion) == null && GameProtocol.getBedrockCodec(protocolVersion) == null) {
            session.getUpstream().getSession().setCodec(BedrockCompat.disconnectCompat(protocolVersion));
            session.disconnect(GeyserReversion.CONFIG.versionNotSupportedKick());
            return true;
        }

        return false;
    }
}
