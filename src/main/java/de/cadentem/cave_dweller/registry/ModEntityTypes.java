package de.cadentem.cave_dweller.registry;

import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class ModEntityTypes {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("betterimprovements", "cave_dweller");
    private static final ResourceKey<EntityType<?>> KEY = ResourceKey.create(Registries.ENTITY_TYPE, ID);

    public static final EntityType<CaveDwellerEntity> CAVE_DWELLER =
            EntityType.Builder.of(CaveDwellerEntity::new, MobCategory.MONSTER).sized(0.4F, 3.0F).build(KEY);

    public static void register() {
        Registry.register(BuiltInRegistries.ENTITY_TYPE, KEY, CAVE_DWELLER);
    }
}
