package oxy.geyser.reversion.util;

import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.data.EncodingSettings;

public class CodecUtil {
    public static BedrockCodec rebuildCodec(BedrockCodec codec) {
        BedrockCodecHelper helper = codec.createHelper();
        helper.setEncodingSettings(EncodingSettings.builder().maxListSize(Integer.MAX_VALUE).maxByteArraySize(Integer.MAX_VALUE).maxNetworkNBTSize(Integer.MAX_VALUE).maxItemNBTSize(Integer.MAX_VALUE).maxStringLength(Integer.MAX_VALUE).build());
        return codec.toBuilder().helper(() -> helper).build();
    }
}
