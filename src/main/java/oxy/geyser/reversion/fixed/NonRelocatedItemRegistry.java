package oxy.geyser.reversion.fixed;

import com.github.blackjack200.ouranos.converter.ItemTypeDictionary;
import com.github.blackjack200.ouranos.data.ItemTypeInfo;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemVersion;
import org.cloudburstmc.protocol.common.DefinitionRegistry;

public class NonRelocatedItemRegistry implements DefinitionRegistry<ItemDefinition> {
    private final int protocol;

    public NonRelocatedItemRegistry(int protocol) {
        this.protocol = protocol;
    }

    public ItemDefinition getDefinition(int runtimeId) {
        try {
            ItemTypeDictionary.InnerEntry dict = ItemTypeDictionary.getInstance(this.protocol);
            String strId = dict.fromIntId(runtimeId);
            ItemTypeInfo x = dict.getEntries().get(strId);
            return new ItemDefinition() {
                @Override
                public boolean isComponentBased() {
                    return x.component_based();
                }

                @Override
                public String getIdentifier() {
                    return strId;
                }

                @Override
                public int getRuntimeId() {
                    return x.runtime_id();
                }

                @Override
                public ItemVersion getVersion() {
                    return x.getVersion() == null ? ItemVersion.LEGACY : ItemVersion.from(x.getVersion().ordinal());
                }

                @Override
                public NbtMap getComponentData() {
                    return x.getComponentNbt();
                }
            };
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
