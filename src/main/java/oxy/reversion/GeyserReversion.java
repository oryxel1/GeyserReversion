package oxy.reversion;

import net.lenni0451.classtransform.TransformerManager;
import net.lenni0451.reflect.Agents;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.v575.Bedrock_v575;
import org.cloudburstmc.protocol.bedrock.codec.v944.Bedrock_v944;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPreInitializeEvent;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.api.extension.ExtensionLogger;
import oxy.reversion.util.ClassLoaderPriorityUtil;
import oxy.reversion.util.ServerUtil;
import oxy.reversion.config.Config;
import oxy.reversion.config.ConfigLoader;
import oxy.reversion.util.auth.PendingBedrockAuthentication;
import oxy.reversion.util.protocol.CodecUtil;
import oxy.reversion.util.transformers.BaseBedrockCodecHelperTransformer;
import oxy.reversion.util.transformers.GeyserExtensionClassProvider;

public class GeyserReversion implements Extension {
    private static ExtensionLogger LOGGER;
    public static ExtensionLogger extensionlogger() {
        return LOGGER;
    }

    private static boolean supportV575AndBelow = true;
    public static boolean doWeSupportBelow575() {
        return supportV575AndBelow;
    }

    private static Config CONFIG;
    public static Config config() {
        return CONFIG;
    }

    public static BedrockCodec TARGET_CODEC = CodecUtil.rebuildCodec(Bedrock_v944.CODEC);

    public static PendingBedrockAuthentication AUTH = new PendingBedrockAuthentication();

    @Subscribe
    public void onGeyserPreInitializeEvent(GeyserPreInitializeEvent event) {
        LOGGER = logger();
        // Mainly for testing/debugging.
        ClassLoaderPriorityUtil.loadOverridingJars(this);

        try {
            TransformerManager transformerManager = new TransformerManager(new GeyserExtensionClassProvider());
            transformerManager.addTransformer(BaseBedrockCodecHelperTransformer.class.getName());
            transformerManager.hookInstrumentation(Agents.getInstrumentation());
        } catch (Exception e) {
            supportV575AndBelow = false;
            throw new RuntimeException("Failed to hook into codec helper, any version below " + Bedrock_v575.CODEC.getMinecraftVersion() + " will not be supported!");
        }
    }

    @Subscribe
    public void onGeyserPostInitializeEvent(GeyserPostInitializeEvent event) {
        CONFIG = ConfigLoader.load(this, GeyserReversion.class, Config.class);
        try {
            // Shutdown Geyser and restart it to use our custom packet handler.
            ServerUtil.restartWithInitializer();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}