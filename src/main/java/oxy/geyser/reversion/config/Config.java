package oxy.geyser.reversion.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record Config(@JsonProperty("debug-mode") boolean debugMode,
                     @JsonProperty("blocked-protocols") List<Integer> blockProtocols,
                     @JsonProperty("min-protocol-id") int minProtocolId,
                     @JsonProperty("min-protocol-kick") String minProtocolKick,
                     @JsonProperty("version-not-supported-kick") String versionNotSupportedKick,
                     @JsonProperty("block-protocol-kick") String blockedProtocolKick
) {
}