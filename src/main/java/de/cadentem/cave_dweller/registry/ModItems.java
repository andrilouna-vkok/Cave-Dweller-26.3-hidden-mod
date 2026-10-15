package de.cadentem.cave_dweller.registry;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

public class ModItems {
    private static final ResourceKey<Item> KEY = ResourceKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath("cave_dweller", "cave_dweller_spawn_egg"));

    public static final Item CAVE_DWELLER_SPAWN_EGG =
            new SpawnEggItem(new Item.Properties().setId(KEY).spawnEgg(ModEntityTypes.CAVE_DWELLER));

    public static void register() {
        Registry.register(BuiltInRegistries.ITEM, KEY, CAVE_DWELLER_SPAWN_EGG);
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.SPAWN_EGGS).register(content -> content.accept(CAVE_DWELLER_SPAWN_EGG));
    }
}
