package de.cadentem.cave_dweller.client;

import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.EntityType;

/**
 * GeckoLib 5 renderer. Model/animation/texture are found by registry id (cave_dweller:cave_dweller), the old eyes
 * layer is replaced by the built-in glowmask (textures/entity/cave_dweller_glowmask.png).
 */
public class CaveDwellerRenderer<R extends EntityRenderState & GeoRenderState> extends GeoEntityRenderer<CaveDwellerEntity, R> {
    public CaveDwellerRenderer(EntityRendererProvider.Context context, EntityType<CaveDwellerEntity> entityType) {
        super(context, entityType);
        // TODO: re-enable glowing eyes once AutoGlowingGeoLayer's package is known:
        // withRenderLayer(new AutoGlowingGeoLayer<>(this));
        withScale(1.3F);
    }
}
