package oxy.reversion.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import lombok.RequiredArgsConstructor;

import java.util.List;

public final class Config {
    @JsonProperty("debug-mode")
    private boolean debugMode;
    @JsonProperty("blocked-protocols")
    private List<Integer> blockProtocols;
    @JsonProperty("min-protocol-id")
    private int minProtocolId;
    @JsonProperty("min-protocol-kick")
    private String minProtocolKick;
    @JsonProperty("version-not-supported-kick")
    private String versionNotSupportedKick;
    @JsonProperty("block-protocol-kick")
    private String blockedProtocolKick;
    @JsonProperty("timeout-seconds")
    @JsonSetter(nulls = Nulls.SKIP)
    private int timeoutSeconds = 120;

    public boolean debugMode() {
        return debugMode;
    }

    public List<Integer> blockProtocols() {
        return blockProtocols;
    }

    public int minProtocolId() {
        return minProtocolId;
    }

    public String minProtocolKick() {
        return minProtocolKick;
    }

    public String versionNotSupportedKick() {
        return versionNotSupportedKick;
    }

    public String blockedProtocolKick() {
        return blockedProtocolKick;
    }

    public int timeoutSeconds() {
        return timeoutSeconds;
    }
}