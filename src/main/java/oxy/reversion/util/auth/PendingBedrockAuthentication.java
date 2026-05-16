package oxy.reversion.util.auth;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import net.lenni0451.commons.httpclient.HttpClient;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.minecraftauth.java.JavaAuthManager;
import net.raphimc.minecraftauth.msa.data.MsaConstants;
import net.raphimc.minecraftauth.msa.model.MsaApplicationConfig;
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode;
import net.raphimc.minecraftauth.msa.model.MsaToken;
import net.raphimc.minecraftauth.msa.service.impl.DeviceCodeMsaAuthService;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.GeyserLogger;
import oxy.reversion.GeyserReversion;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class PendingBedrockAuthentication {
    public static final HttpClient AUTH_CLIENT = MinecraftAuth.createHttpClient();
    private final LoadingCache<String, AuthenticationTask> authentications;

    public PendingBedrockAuthentication() {
        this.authentications = CacheBuilder.newBuilder()
                .build(new CacheLoader<>() {
                    @Override
                    public AuthenticationTask load(@NonNull String userKey) {
                        return new AuthenticationTask(userKey, GeyserReversion.config().timeoutSeconds());
                    }
                });
    }

    public AuthenticationTask getTask(@NonNull String userKey) {
        return authentications.getIfPresent(userKey);
    }

    @SneakyThrows(ExecutionException.class)
    public AuthenticationTask getOrCreateTask(@NonNull String userKey) {
        return authentications.get(userKey);
    }

    public class AuthenticationTask {
        private static final Executor DELAYED_BY_ONE_SECOND = CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS);

        private final String userKey;
        private final int timeoutSec;
        @Getter
        private CompletableFuture<BedrockAuthManager> authentication;

        private AuthenticationTask(String userKey, int timeoutSec) {
            this.userKey = userKey;
            this.timeoutSec = timeoutSec;
        }

        public void resetRunningFlow() {
            if (authentication == null) {
                return;
            }

            // Interrupt the current flow
            this.authentication.cancel(true);
        }

        public void cleanup() {
            GeyserLogger logger = GeyserImpl.getInstance().getLogger();
            if (logger.isDebug()) {
                logger.debug("Cleaning up authentication task for " + userKey);
            }
            authentications.invalidate(userKey);
        }

        public CompletableFuture<BedrockAuthManager> performLoginAttempt(boolean offlineAccess, Consumer<MsaDeviceCode> deviceCodeConsumer) {
            DeviceCodeMsaAuthService authService = new DeviceCodeMsaAuthService(AUTH_CLIENT, new MsaApplicationConfig(MsaConstants.BEDROCK_ANDROID_TITLE_ID, MsaConstants.SCOPE_TITLE_AUTH), deviceCodeConsumer, timeoutSec * 1000);
            return authentication = CompletableFuture.supplyAsync(() -> {
                try {
                    MsaToken msaToken = authService.acquireToken();
                    BedrockAuthManager authManager = BedrockAuthManager.create(AUTH_CLIENT, GeyserReversion.TARGET_CODEC.getMinecraftVersion()).login(msaToken);
                    authManager.getMinecraftMultiplayerToken().refresh();
                    return authManager;
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }, DELAYED_BY_ONE_SECOND).whenComplete((r, ex) -> {
                // avoid memory leak, in case player doesn't connect again
                CompletableFuture.delayedExecutor(timeoutSec, TimeUnit.SECONDS).execute(this::cleanup);
            });
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "{userKey='" + userKey + "'}";
        }
    }
}