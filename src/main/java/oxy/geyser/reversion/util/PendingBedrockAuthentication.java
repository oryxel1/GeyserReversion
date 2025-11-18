/*
 * Copyright (c) 2019-2021 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package oxy.geyser.reversion.util;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.Getter;
import lombok.SneakyThrows;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode;
import net.raphimc.minecraftauth.msa.service.impl.DeviceCodeMsaAuthService;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.network.GameProtocol;
import org.geysermc.geyser.session.PendingMicrosoftAuthentication;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Pending Microsoft authentication task cache.
 * It permits user to exit the server while they authorize Geyser to access their Microsoft account.
 */
public class PendingBedrockAuthentication {
    private final LoadingCache<String, AuthenticationTask> authentications;

    public PendingBedrockAuthentication() {
        this.authentications = CacheBuilder.newBuilder()
            .build(new CacheLoader<>() {
                    @Override
                    public AuthenticationTask load(@NonNull String userKey) {
                        return new AuthenticationTask(userKey, GeyserImpl.getInstance().getConfig().getPendingAuthenticationTimeout());
                    }
        });
    }

    @SneakyThrows(ExecutionException.class)
    public AuthenticationTask getOrCreateTask(@NonNull String userKey) {
        return authentications.get(userKey);
    }

    public class AuthenticationTask {
        private static final Executor DELAYED_BY_ONE_SECOND = CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS);

        private final int timeoutSec;
        @Getter
        private CompletableFuture<BedrockAuthManager> authentication;
        private final String userKey;

        public AuthenticationTask(String userKey, int timeoutSec) {
            this.userKey = userKey;
            this.timeoutSec = timeoutSec;
        }

        public void cleanup() {
            GeyserLogger logger = GeyserImpl.getInstance().getLogger();
            if (logger.isDebug()) {
                logger.debug("Cleaning up authentication task for " + userKey);
            }
            authentications.invalidate(userKey);
        }

        public void resetRunningFlow() {
            if (this.authentication == null) {
                return;
            }

            // Interrupt the current flow
            this.authentication.cancel(true);
        }

        public CompletableFuture<BedrockAuthManager> performLoginAttempt(Consumer<MsaDeviceCode> deviceCodeConsumer) {
            return this.authentication = CompletableFuture.supplyAsync(() -> {
                try {
                    BedrockAuthManager auth = BedrockAuthManager.create(PendingMicrosoftAuthentication.AUTH_CLIENT, GameProtocol.DEFAULT_BEDROCK_VERSION).login(DeviceCodeMsaAuthService::new, deviceCodeConsumer);
                    auth.getMinecraftCertificateChain().refresh();
                    auth.getMinecraftMultiplayerToken().refresh();
                    return auth;
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }, DELAYED_BY_ONE_SECOND).whenComplete((r, ex) -> {
                // avoid memory leak, in case player doesn't connect again
                CompletableFuture.delayedExecutor(this.timeoutSec, TimeUnit.SECONDS).execute(this::cleanup);
            });
        }
    }
}
