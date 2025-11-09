package oxy.geyser.reversion.transformer;

import net.lenni0451.classtransform.annotations.CInline;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CWrapCatch;
import org.cloudburstmc.protocol.bedrock.codec.BaseBedrockCodecHelper;

@CTransformer(BaseBedrockCodecHelper.class)
public class BaseBedrockCodecHelperTransformer {
    @CInline
    @CWrapCatch(value = "setCameraPresetDefinitions")
    public void transform(final Exception exception) {
        // Don't throw the exception, Geyser try to do this but older versions doesn't support this!
    }
}
