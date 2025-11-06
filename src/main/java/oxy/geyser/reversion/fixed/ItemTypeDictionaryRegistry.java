package oxy.geyser.reversion.fixed;

import com.github.blackjack200.ouranos.converter.ItemTypeDictionary;
import com.github.blackjack200.ouranos.data.ItemTypeInfo;
import com.github.blackjack200.ouranos.shaded.protocol.bedrock.data.definitions.ItemDefinition;
import com.github.blackjack200.ouranos.shaded.protocol.common.DefinitionRegistry;
import com.github.blackjack200.ouranos.utils.SimpleVersionedItemDefinition;

public class ItemTypeDictionaryRegistry implements DefinitionRegistry<ItemDefinition> {
    private final int protocol;

    public ItemTypeDictionaryRegistry(int protocol) {
        this.protocol = protocol;
    }

    public ItemDefinition getDefinition(int runtimeId) {
        try {
            ItemTypeDictionary.InnerEntry dict = ItemTypeDictionary.getInstance(this.protocol);
            String strId = dict.fromIntId(runtimeId);
            ItemTypeInfo x = dict.getEntries().get(strId);
            return new SimpleVersionedItemDefinition(strId, x.runtime_id(), x.getVersion(), x.component_based(), x.getComponentNbt());
        } catch (Exception ignored) {
            return new ItemDefinition() {
                @Override
                public boolean isComponentBased() {
                    return false;
                }

                @Override
                public String getIdentifier() {
                    return "minecraft:unknown";
                }

                @Override
                public int getRuntimeId() {
                    return runtimeId;
                }
            };
        }
    }

    public boolean isRegistered(ItemDefinition definition) {
        return ItemTypeDictionary.getInstance(this.protocol).fromStringId(definition.getIdentifier()) != null;
    }
}
