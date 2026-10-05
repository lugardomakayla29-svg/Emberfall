package com.solme.emberfall.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solme.emberfall.entity.PinkSlime;

import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.slime.SlimeModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.Slime;

/**
 * Draws a {@link PinkSlime} with the rosie pink skin. The vanilla {@code SlimeRenderer} cannot simply be given a new
 * texture: its {@code SlimeOuterLayer} reads the static {@code SlimeRenderer.SLIME_LOCATION} for both its outline and
 * its translucent pass (bytecode), so the body would be pink inside a vanilla GREEN shell. This renderer therefore
 * carries its own outer layer that uses the same pink texture. Body, shadow and scaling are copied from the vanilla
 * class, which scales the model by the slime's size, so size 5 is drawn 5x.
 */
public class PinkSlimeRenderer extends MobRenderer<Slime, SlimeRenderState, SlimeModel> {
    public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("emberfall", "textures/entity/pink_slime.png");

    public PinkSlimeRenderer(EntityRendererProvider.Context context) {
        super(context, new SlimeModel(context.bakeLayer(ModelLayers.SLIME)), 0.25F);
        this.addLayer(new PinkOuterLayer(this, context.getModelSet()));
    }

    @Override
    public Identifier getTextureLocation(SlimeRenderState state) {
        return TEXTURE;
    }

    @Override
    public SlimeRenderState createRenderState() {
        return new SlimeRenderState();
    }

    @Override
    public void extractRenderState(Slime slime, SlimeRenderState state, float partialTick) {
        super.extractRenderState(slime, state, partialTick);
        state.squish = net.minecraft.util.Mth.lerp(partialTick, slime.oSquish, slime.squish);
        state.size = slime.getSize();
    }

    @Override
    protected float getShadowRadius(SlimeRenderState state) {
        return state.size * 0.25F;
    }

    @Override
    protected void scale(SlimeRenderState state, PoseStack poseStack) {
        float s = 0.999F;
        poseStack.scale(s, s, s);
        poseStack.translate(0.0F, 0.001F, 0.0F);
        float size = state.size;
        float squish = state.squish / (size * 0.5F + 1.0F);
        float stretch = 1.0F / (squish + 1.0F);
        poseStack.scale(stretch * size, 1.0F / stretch * size, stretch * size);
    }

    /** The translucent outer shell, identical to the vanilla layer except that it uses the pink texture. */
    private static final class PinkOuterLayer extends RenderLayer<SlimeRenderState, SlimeModel> {
        private final SlimeModel model;

        PinkOuterLayer(RenderLayerParent<SlimeRenderState, SlimeModel> parent, EntityModelSet modelSet) {
            super(parent);
            this.model = new SlimeModel(modelSet.bakeLayer(ModelLayers.SLIME_OUTER));
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, SlimeRenderState state,
                           float yRot, float xRot) {
            boolean outlineOnly = state.appearsGlowing() && state.isInvisible;
            if (state.isInvisible && !outlineOnly) {
                return;
            }
            int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
            if (outlineOnly) {
                collector.order(1).submitModel(this.model, state, poseStack, RenderTypes.outline(TEXTURE), light, overlay,
                        -1, null, state.outlineColor, null);
            } else {
                collector.order(1).submitModel(this.model, state, poseStack, RenderTypes.entityTranslucent(TEXTURE), light,
                        overlay, -1, null, state.outlineColor, null);
            }
        }
    }
}
