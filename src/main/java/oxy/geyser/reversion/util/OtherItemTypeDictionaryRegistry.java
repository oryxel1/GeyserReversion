//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package oxy.geyser.reversion.util;

import com.github.blackjack200.ouranos.converter.ItemTypeDictionary;
import com.github.blackjack200.ouranos.data.ItemTypeInfo;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemVersion;
import org.cloudburstmc.protocol.common.DefinitionRegistry;

public class OtherItemTypeDictionaryRegistry implements DefinitionRegistry<ItemDefinition> {
    private final DefinitionRegistry<ItemDefinition> registry;
    private final int protocol;

    public OtherItemTypeDictionaryRegistry(DefinitionRegistry<ItemDefinition> registry, int protocol) {
        this.protocol = protocol;
        this.registry = registry;
    }

    public ItemDefinition getDefinition(int runtimeId) {
        ItemTypeDictionary.InnerEntry entry = ItemTypeDictionary.getInstance(this.protocol);
        String itemId = entry.fromIntId(runtimeId);
        ItemTypeInfo itemInfo = entry.getEntries().get(itemId);
        return itemInfo == null ? this.registry.getDefinition(runtimeId) :
                new SimpleItemDefinition(itemId, itemInfo.runtime_id(),
                        itemInfo.getVersion() == null ? ItemVersion.NONE : ItemVersion.from(itemInfo.getVersion().ordinal()),
                        itemInfo.component_based(), itemInfo.getComponentNbt());
    }

    public boolean isRegistered(ItemDefinition definition) {
        return this.registry.isRegistered(definition) || ItemTypeDictionary.getInstance(this.protocol).fromStringId(definition.getIdentifier()) != null;
    }
}
