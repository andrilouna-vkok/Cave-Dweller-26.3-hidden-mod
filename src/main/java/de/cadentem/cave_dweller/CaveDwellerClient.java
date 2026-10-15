package de.cadentem.cave_dweller;

import de.cadentem.cave_dweller.client.CaveDwellerRenderer;
import de.cadentem.cave_dweller.registry.ModEntityTypes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class CaveDwellerClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntityTypes.CAVE_DWELLER, context -> new CaveDwellerRenderer<>(context, ModEntityTypes.CAVE_DWELLER));
    }
}
