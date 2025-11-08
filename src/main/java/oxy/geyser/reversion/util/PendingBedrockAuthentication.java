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
import lombok.Setter;
import lombok.SneakyThrows;
import net.lenni0451.commons.httpclient.HttpClient;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.step.bedrock.session.StepFullBedrockSession;
import net.raphimc.minecraftauth.step.msa.StepMsaDeviceCode;
import net.raphimc.minecraftauth.util.MicrosoftConstants;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.session.PendingMicrosoftAuthentication;
import org.geysermc.geyser.util.MinecraftAuthLogger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Pending Microsoft authentication task cache.
 * It permits user to exit the server while they authorize Geyser to access their Microsoft account.
 */
public class PendingBedrockAuthentication {
    public static final HttpClient AUTH_CLIENT = PendingMicrosoftAuthentication.AUTH_CLIENT;
    public static final BiFunction<Boolean, Integer, StepFullBedrockSession> AUTH_FLOW = (offlineAccess, timeoutSec) -> MinecraftAuth.builder()
            .withClientId(MicrosoftConstants.BEDROCK_ANDROID_TITLE_ID).withScope(MicrosoftConstants.SCOPE_TITLE_AUTH)
            .deviceCode()
            .withDeviceToken("Android")
            .sisuTitleAuthentication(MicrosoftConstants.BEDROCK_XSTS_RELYING_PARTY)
            .buildMinecraftBedrockChainStep(true, false);

    public static class AuthenticationTask {
        private static final Executor DELAYED_BY_ONE_SECOND = CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS);

        private final int timeoutSec;
        @Getter
        private CompletableFuture<StepChainResult> authentication;

        public AuthenticationTask(int timeoutSec) {
            this.timeoutSec = timeoutSec;
        }

        public void resetRunningFlow() {
            if (authentication == null) {
                return;
            }

            // Interrupt the current flow
            this.authentication.cancel(true);
        }

        public CompletableFuture<StepChainResult> performLoginAttempt(Consumer<StepMsaDeviceCode.MsaDeviceCode> deviceCodeConsumer) {
            return authentication = CompletableFuture.supplyAsync(() -> {
                try {
                    StepFullBedrockSession step = AUTH_FLOW.apply(false, timeoutSec);
                    return new StepChainResult(step, step.getFromInput(MinecraftAuthLogger.INSTANCE, AUTH_CLIENT, new StepMsaDeviceCode.MsaDeviceCodeCallback(deviceCodeConsumer)));
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }, DELAYED_BY_ONE_SECOND);
        }
    }

    public record StepChainResult(StepFullBedrockSession step, StepFullBedrockSession.FullBedrockSession session) {
    }
}
