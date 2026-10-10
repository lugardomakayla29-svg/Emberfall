package com.solme.emberfall.client;

import com.solme.emberfall.entity.HordeZombie;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * Draws a {@link HordeZombie} like the vanilla zombie, except a Brood-Kin (a zombie the Broodtide ate and spat out) wears the owner's sickly skin.
 * The skin is a texture under the emberfall namespace, NOT a replacement of the vanilla zombie path, so every other zombie in the game is untouched.
 * The flag comes through synced entity data because entity tags never reach a client.
 */
public class HordeZombieRenderer extends ZombieRenderer {
    private static final Identifier KIN_TEXTURE = Identifier.fromNamespaceAndPath("emberfall", "textures/entity/brood_kin_zombie.png");

    /** The vanilla render state plus the one thing this renderer needs to know. */
    public static final class State extends ZombieRenderState {
        public boolean broodKin;
    }

    public HordeZombieRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ZombieRenderState createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(Zombie zombie, ZombieRenderState state, float partialTick) {
        super.extractRenderState(zombie, state, partialTick);
        if (state instanceof State s) {
            s.broodKin = zombie instanceof HordeZombie horde && horde.isBroodKin();
        }
    }

    @Override
    public Identifier getTextureLocation(ZombieRenderState state) {
        return state instanceof State s && s.broodKin ? KIN_TEXTURE : super.getTextureLocation(state);
    }
}
