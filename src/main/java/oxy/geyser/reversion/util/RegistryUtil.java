package oxy.geyser.reversion.util;

import com.github.blackjack200.ouranos.shaded.protocol.bedrock.data.inventory.ItemVersion;
import com.github.blackjack200.ouranos.utils.ItemTypeDictionaryRegistry;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.packet.ItemComponentPacket;
import org.cloudburstmc.protocol.common.SimpleDefinitionRegistry;
import oxy.geyser.reversion.session.GeyserTranslatedUser;

public class RegistryUtil {
    public static void onItemComponent(final GeyserTranslatedUser user, final ItemComponentPacket packet) {
        {
            SimpleDefinitionRegistry.Builder<ItemDefinition> builder = SimpleDefinitionRegistry.<ItemDefinition>builder()
                    .add(new SimpleItemDefinition("minecraft:empty", 0, false));

            for (final ItemDefinition entry : packet.getItems()) {
                builder.add(new SimpleItemDefinition(entry.getIdentifier(), entry.getRuntimeId(), entry.getVersion(), entry.isComponentBased(), entry.getComponentData()));
            }

            SimpleDefinitionRegistry<ItemDefinition> itemDefinitions = builder.build();
            user.getCloudburstServerCodecHelper().setItemDefinitions(itemDefinitions);
            user.getCloudburstClientCodecHelper().setItemDefinitions(new OtherItemTypeDictionaryRegistry(itemDefinitions, user.getProtocolId()));

            user.getSession().getUpstream().getCodecHelper().setItemDefinitions(user.getCloudburstClientCodecHelper().getItemDefinitions());
        }

        {
            com.github.blackjack200.ouranos.shaded.protocol.common.SimpleDefinitionRegistry.Builder<com.github.blackjack200.ouranos.shaded.protocol.bedrock.data.definitions.ItemDefinition> builder = com.github.blackjack200.ouranos.shaded.protocol.common.SimpleDefinitionRegistry.<com.github.blackjack200.ouranos.shaded.protocol.bedrock.data.definitions.ItemDefinition>builder()
                    .add(new com.github.blackjack200.ouranos.shaded.protocol.bedrock.data.definitions.SimpleItemDefinition("minecraft:empty", 0, false));

            for (final ItemDefinition entry : packet.getItems()) {
                builder.add(new com.github.blackjack200.ouranos.shaded.protocol.bedrock.data.definitions.SimpleItemDefinition(entry.getIdentifier(), entry.getRuntimeId(), ItemVersion.from(entry.getVersion().ordinal()), entry.isComponentBased(), entry.getComponentData()));
            }

            com.github.blackjack200.ouranos.shaded.protocol.common.SimpleDefinitionRegistry<com.github.blackjack200.ouranos.shaded.protocol.bedrock.data.definitions.ItemDefinition> itemDefinitions = builder.build();
            user.getServerCodecHelper().setItemDefinitions(itemDefinitions);
            user.getClientCodecHelper().setItemDefinitions(new ItemTypeDictionaryRegistry(itemDefinitions, user.getProtocolId()));
        }
    }
}
