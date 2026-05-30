package oxy.reversion.util.auth;

import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePacksInfoPacket;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.event.bedrock.SessionInitializeEvent;
import org.geysermc.geyser.event.type.SessionLoadResourcePacksEventImpl;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.text.GeyserLocale;

import java.util.UUID;

public class IncompleteLoginUtil {
    public static SessionLoadResourcePacksEventImpl halfLogin(LoginPacket packet, GeyserSession session, boolean alreadyLoggedIn) {
        final GeyserImpl geyser = GeyserImpl.getInstance();
        if (geyser.isShuttingDown() || geyser.isReloading()) {
            // Don't allow new players in if we're no longer operating
            session.disconnect(GeyserLocale.getLocaleStringLog("geyser.core.shutdown.kick.message"));
            return null;
        }

        if (alreadyLoggedIn) {
            session.disconnect("Received duplicate login packet!");
            session.forciblyCloseUpstream();
            return null;
        }

        ClientDataUtil.setClientData(session, packet);

        if (session.isClosed()) {
            // Can happen if Xbox validation fails
            session.forciblyCloseUpstream();
            return null;
        }

        if (geyser.getSessionManager().isXuidAlreadyPending(session.xuid()) || geyser.getSessionManager().sessionByXuid(session.xuid()) != null) {
            session.disconnect(GeyserLocale.getLocaleStringLog("geyser.auth.already_loggedin", session.bedrockUsername()));
            return null;
        }

        // Set the block translation based off of version
        session.setBlockMappings(BlockRegistries.BLOCKS.forVersion(packet.getProtocolVersion()));
        session.setItemMappings(Registries.ITEMS.forVersion(packet.getProtocolVersion()));

        PlayStatusPacket playStatus = new PlayStatusPacket();
        playStatus.setStatus(PlayStatusPacket.Status.LOGIN_SUCCESS);
        session.sendUpstreamPacket(playStatus);

        SessionLoadResourcePacksEventImpl resourcePackLoadEvent = new SessionLoadResourcePacksEventImpl(session);
        geyser.eventBus().fireEventElseKick(resourcePackLoadEvent, session);
        if (session.isClosed()) {
            // Can happen if an error occurs in the resource pack event; that'll disconnect the player
            return null;
        }
        session.integratedPackActive(resourcePackLoadEvent.isIntegratedPackActive());

        ResourcePacksInfoPacket resourcePacksInfo = new ResourcePacksInfoPacket();
        resourcePacksInfo.getResourcePackInfos().addAll(resourcePackLoadEvent.infoPacketEntries());
        resourcePacksInfo.setVibrantVisualsForceDisabled(!session.isAllowVibrantVisuals());

        resourcePacksInfo.setForcedToAccept(GeyserImpl.getInstance().config().gameplay().forceResourcePacks() ||
                resourcePackLoadEvent.isIntegratedPackActive());
        resourcePacksInfo.setWorldTemplateId(UUID.randomUUID());
        resourcePacksInfo.setWorldTemplateVersion("*");

        session.sendUpstreamPacket(resourcePacksInfo);

        GeyserLocale.loadGeyserLocale(session.locale());
        return resourcePackLoadEvent;
    }
}
