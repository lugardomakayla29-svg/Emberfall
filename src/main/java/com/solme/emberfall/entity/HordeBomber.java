package com.solme.emberfall.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;

/**
 * Fifth horde filler: a fragile, fast runner that sprints at a player and detonates. Built on the vanilla
 * {@link Creeper} so the fuse flash, swell sound and blast are the engine's own; nothing is simulated by hand.
 *
 * <ul>
 *   <li>Blocks: {@code EmberfallExplosionMixin} already cancels block interaction and fire for every explosion in
 *       the expedition map, so a Bomber hurts players and nothing else. It never needs its own block rule.</li>
 *   <li>Blast size and fuse are the vanilla {@code ExplosionRadius} and {@code Fuse} values (defaults 3 and 30). A
 *       Bomber uses radius 2 and fuse 20 (quicker, smaller); a Veteran uses radius 3.</li>
 *   <li>Cost: no goals beyond the vanilla creeper set, no entity spawned besides itself, no per-tick scan.</li>
 *   <li>Veteran: 2.5x health and a wider blast. The name is coloured with {@link MobNames}.</li>
 * </ul>
 */
public class HordeBomber extends Creeper {
    private static final double BASE_HEALTH = 10.0;
    private static final double BASE_SPEED = 0.33;          // vanilla creeper is 0.25
    private static final int BASE_FUSE_TICKS = 20;
    private static final int BASE_RADIUS = 2;
    private static final int VETERAN_RADIUS = 3;
    private static final double VETERAN_HEALTH_MULT = 2.5;

    private boolean veteran = false;

    public HordeBomber(EntityType<? extends Creeper> type, Level level) {
        super(type, level);
    }

    /** Attributes: creeper defaults with the Bomber's own health and speed. */
    public static AttributeSupplier.Builder createBomberAttributes() {
        return Creeper.createAttributes()
                .add(Attributes.MAX_HEALTH, BASE_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, BASE_SPEED);
    }

    /** Sets the vanilla fuse and radius fields through the creeper's own NBT reader, so no reflection is needed. */
    private void applyBlast(int fuse, int radius) {
        CompoundTag tag = new CompoundTag();
        tag.putShort("Fuse", (short) fuse);
        tag.putByte("ExplosionRadius", (byte) radius);
        this.readAdditionalSaveData(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, this.registryAccess(), tag));
    }

    /** Called once right after construction, before the entity is added to the world. */
    public void prepare() {
        applyBlast(BASE_FUSE_TICKS, BASE_RADIUS);
        MobNames.apply(this, "Horde Bomber", MobNames.Tier.LAVA);
    }

    /** Veteran tier: tougher and a wider blast. Call right after {@link #prepare()} and before spawning. */
    public void becomeVeteran() {
        this.veteran = true;
        AttributeInstance health = this.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(health.getBaseValue() * VETERAN_HEALTH_MULT);
            this.setHealth((float) health.getValue());
        }
        applyBlast(BASE_FUSE_TICKS, VETERAN_RADIUS);
        MobNames.apply(this, "Veteran Horde Bomber", MobNames.Tier.VETERAN);
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY() + 0.8, this.getZ(),
                    12, 0.3, 0.4, 0.3, 0.02);
            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 0.8F, 1.1F);
        }
    }

    public boolean isVeteran() {
        return veteran;
    }
}
