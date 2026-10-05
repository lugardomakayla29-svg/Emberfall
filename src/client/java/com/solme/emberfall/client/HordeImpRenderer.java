package com.solme.emberfall.client;

import com.solme.emberfall.entity.HordeImp;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.vex.VexModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.VexRenderState;
import net.minecraft.resources.Identifier;

/**
 * Draws a {@link HordeImp} with the vanilla Vex model and texture. The vanilla {@code VexRenderer} cannot be reused:
 * it is {@code MobRenderer<Vex, ...>} and its bridge method casts the entity to {@code Vex}, which an imp is not
 * (it extends Monster, since Vex.tick() turns on noPhysics). The imp holds nothing, so no item layer is added.
 */
public class HordeImpRenderer extends MobRenderer<HordeImp, VexRenderState, VexModel> {
    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/illager/vex.png");

    public HordeImpRenderer(EntityRendererProvider.Context context) {
        super(context, new VexModel(context.bakeLayer(ModelLayers.VEX)), 0.3F);
    }

    @Override
    public Identifier getTextureLocation(VexRenderState state) {
        return TEXTURE;
    }

    @Override
    public VexRenderState createRenderState() {
        return new VexRenderState();
    }
}
