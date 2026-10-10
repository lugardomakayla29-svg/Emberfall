package com.solme.emberfall.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solme.emberfall.entity.BroodtideBody;

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
 * Draws a {@link BroodtideBody} with the owner's sickly slime skin (assets/emberfall/textures/entity/sickly_slime.png, from the supplied
 * SicklySlimes pack). Same reason as the Pink Slime renderer: the vanilla {@code SlimeOuterLayer} reads the static green texture for its
 * outline and translucent pass, so this carries its own outer layer using the sickly texture. The model is scaled by the slime's size
 * ({@link BroodtideBody#BODY_SIZE}), so the body is drawn that many times larger.
 */
public class BroodtideRenderer extends MobRenderer<Slime, SlimeRenderState, SlimeModel> {
    /**
     * How far above the body the upper slime sits, in the pose stack's units (a block before the size scale). Read from the vanilla SlimeModel bytecode: the
     * outer shell is an 8 pixel box, so one slime is 8 / 16 = 0.5 tall; sunk 12% into the one below (ChainGlue.OVERLAP) so no sliver of sky shows between the two.
     * The size scale already on the stack multiplies this, so it stays one slime-height at any size.
     */
    private static final float HEAD_LIFT = 0.5F * (1.0F - (float) com.solme.emberfall.entity.ChainGlue.OVERLAP);

    public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("emberfall", "textures/entity/sickly_slime.png");

    public BroodtideRenderer(EntityRendererProvider.Context context) {
        super(context, new SlimeModel(context.bakeLayer(ModelLayers.SLIME)), 0.25F);
        this.addLayer(new SicklyOuterLayer(this, context.getModelSet()));
        this.addLayer(new UpperHeadLayer(this, context.getModelSet()));
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
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F));   // the lower slime faces away from the player (owner head design)
        float s = 0.999F;
        poseStack.scale(s, s, s);
        poseStack.translate(0.0F, 0.001F, 0.0F);
        float size = state.size;
        float squish = state.squish / (size * 0.5F + 1.0F);
        float stretch = 1.0F / (squish + 1.0F);
        poseStack.scale(stretch * size, 1.0F / stretch * size, stretch * size);
    }

    /** The translucent outer shell, identical to the vanilla layer except that it uses the pink texture. */
    private static final class SicklyOuterLayer extends RenderLayer<SlimeRenderState, SlimeModel> {
        private final SlimeModel model;

        SicklyOuterLayer(RenderLayerParent<SlimeRenderState, SlimeModel> parent, EntityModelSet modelSet) {
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

    /**
     * The UPPER slime of the head column (owner, 2026-10-10: two giant slimes stacked, the lower facing away, the upper facing the player). Drawn as a
     * second copy of the slime model one body height above the real body and turned back half a revolution, so the face looks at the player. It costs
     * no entity: it is a second draw of the same model, with its own translucent shell. The model is drawn at the same scale as the body, and the body's
     * scale() already ran, so the lift is HEAD_LIFT in that scaled space.
     * UNSEEN: nobody has looked at this on a real client.
     */
    private static final class UpperHeadLayer extends RenderLayer<SlimeRenderState, SlimeModel> {
        private final SlimeModel inner;
        private final SlimeModel outer;

        UpperHeadLayer(RenderLayerParent<SlimeRenderState, SlimeModel> parent, EntityModelSet modelSet) {
            super(parent);
            this.inner = new SlimeModel(modelSet.bakeLayer(ModelLayers.SLIME));
            this.outer = new SlimeModel(modelSet.bakeLayer(ModelLayers.SLIME_OUTER));
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, SlimeRenderState state,
                           float yRot, float xRot) {
            if (state.isInvisible) {
                return;
            }
            poseStack.pushPose();
            poseStack.translate(0.0F, -HEAD_LIFT, 0.0F);   // the model's y axis points down in entity space, so up is negative
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F));   // undo the body's half turn: this one faces the player
            int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
            collector.order(0).submitModel(this.inner, state, poseStack, RenderTypes.entityCutoutNoCull(TEXTURE), light, overlay,
                    -1, null, state.outlineColor, null);
            collector.order(1).submitModel(this.outer, state, poseStack, RenderTypes.entityTranslucent(TEXTURE), light, overlay,
                    -1, null, state.outlineColor, null);
            poseStack.popPose();
        }
    }
}
