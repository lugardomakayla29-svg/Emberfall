package com.solme.emberfall.entity;

import com.mojang.math.Transformation;
import com.solme.emberfall.EmberfallMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One of the Ember Guardian's lit pylons. Displays have no hitbox, so the thing a player actually hits is this real
 * Mob: a fully invisible Silverfish with a scaled lit-magma display riding it (the same technique the retired HydraHead
 * used). The auto-weapon targets the nearest emberfall Mob, so a pylon standing where players fight gets attacked
 * without any weapon change.
 *
 * Unlike a Hydra head, a pylon is NOT a composite part: it has its own small HP pool and must actually die, because
 * breaking pylons is the objective that opens the boss. The owning {@link EmberGuardian} counts living pylons and is
 * invulnerable while any is alive (a visible beam is drawn from each lit pylon to the boss as the cause).
 */
public class CinderPylon extends Silverfish {
    static final float DISPLAY_SCALE = 1.6F;
    static final double PYLON_HP = 40.0;

    private Display.ItemDisplay skin;

    public CinderPylon(EntityType<? extends Silverfish> type, Level level) {
        super(type, level);
        this.setInvisible(true);
        this.setSilent(true);
    }

    public static AttributeSupplier.Builder createPylonAttributes() {
        return Silverfish.createAttributes()
                .add(Attributes.MAX_HEALTH, PYLON_HP)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    /** Spawns the lit-magma skin as a passenger. Call once, right after this pylon has been added to the level. */
    public void attachSkin(ServerLevel level) {
        if (skin != null) {
            return;
        }
        Display.ItemDisplay d = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
        d.setPos(this.getX(), this.getY(), this.getZ());
        d.setItemStack(new ItemStack(Items.MAGMA_BLOCK));
        d.setBillboardConstraints(Display.BillboardConstraints.FIXED);
        d.setTransformation(new Transformation(
                new Vector3f(0, 0, 0), new Quaternionf(),
                new Vector3f(DISPLAY_SCALE, DISPLAY_SCALE, DISPLAY_SCALE), new Quaternionf()));
        d.setNoGravity(true);
        d.setInvulnerable(true);
        d.addTag("emberfall_run"); // run teardown sweeps any stray skin
        level.addFreshEntity(d);
        d.startRiding(this, true, false);
        this.skin = d;
    }

    @Override
    public Vec3 getPassengerRidingPosition(Entity passenger) {
        return this.position().add(0.0, 0.55, 0.0);
    }

    @Override
    protected void registerGoals() {
        // Deliberately empty: a pylon never moves or attacks. The Ember Guardian owns every behaviour.
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.getEntity() == this) {
            return false;
        }
        return super.hurtServer(level, source, amount);
    }

    /** The skin only exists as this pylon's passenger; discard it on every removal path or it would float forever. */
    @Override
    public void remove(RemovalReason reason) {
        if (skin != null && skin.isAlive()) {
            skin.discard();
        }
        skin = null;
        super.remove(reason);
    }
}
