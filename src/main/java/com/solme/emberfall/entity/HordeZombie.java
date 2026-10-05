package com.solme.emberfall.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * The cheapest possible horde-filler enemy (design doc 2.4 / 6.2): a
 * near-default Zombie, reused by the hundred. Deliberately does NOT add
 * extra goal selectors, AI, or per-tick logic beyond vanilla Zombie's own
 * move-toward-player + melee-on-contact behavior - elites/bosses (composite
 * entities, Section 6) are where the mechanical complexity budget goes.
 * Exists as its own EntityType purely so the Wave Director can spawn and
 * count "our" hostiles distinctly from anything else that might exist in
 * the expedition dimension.
 *
 * persistenceRequired defaults to false already (Mob.DEFAULT_PERSISTENCE_REQUIRED),
 * which is what we want - no need to touch it.
 *
 * Veteran tier (ported from a prior project's simple "Elite" mob-affix
 * idea, adapted): NOT a Section 6 composite rig - those stay reserved for
 * actual elites/bosses per 2.4/6.2. This is a much cheaper single-entity
 * upgrade rolled rarely by the Wave Director at spawn time (see
 * {@link com.solme.emberfall.wave.WaveDirector}): straight stat multipliers
 * plus one reactive ability (a brief enrage speed burst on being hit, on a
 * cooldown so it can't be chain-triggered every tick), so a run's horde
 * pool gets occasional harder single targets without paying for a whole
 * passenger-stack rig on every one of them.
 *
 * Verified live (2026-09-26): stat multipliers checked via /attribute get
 * against a spawned instance (20 -> 50 max health, 3.0 -> 4.8 attack
 * damage, both exact). Enrage-on-hit + its cooldown gate were verified via
 * temporary debug logging against real hurtServer calls (including rapid
 * repeat suffocation-damage calls, which incidentally proved the per-instance
 * cooldown correctly rejects re-trigger attempts within the same window) -
 * addEffect confirmed returning true and applying Speed on the first
 * post-cooldown hit. Debug logging has been removed after verification.
 */
public class HordeZombie extends Zombie {

    /**
     * Emberfall zombies never break doors. Vanilla switches this on at spawn (Hard difficulty) and BreakDoorGoal then
     * calls removeBlock, which would delete a door from the designed map. Making the setter a no-op means the
     * flag stays false and the goal is never added.
     */
    @Override
    public void setCanBreakDoors(boolean canBreakDoors) {
        // intentionally ignored
    }

    private static final double VETERAN_HEALTH_MULT = 2.5;
    private static final double VETERAN_DAMAGE_MULT = 1.6;
    private static final int ENRAGE_COOLDOWN_TICKS = 160; // 8s, matches the source mechanic's cooldown
    private static final int ENRAGE_DURATION_TICKS = 60; // 3s of Speed II

    /**
     * Base-mob signature move: a telegraphed lunge. Every horde zombie (not only veterans) crouches for 0.5 s, then
     * leaps at a target 4 to 7 blocks away. The cost is one distance check every 5 ticks while a target exists.
     */
    private static final int LUNGE_COOLDOWN_TICKS = 120;   // 6 s
    private static final int LUNGE_TELEGRAPH_TICKS = 10;   // 0.5 s crouch
    private static final double LUNGE_MIN_RANGE = 4.0;
    private static final double LUNGE_MAX_RANGE = 7.0;
    private static final double LUNGE_HORIZONTAL = 0.9;
    private static final double LUNGE_VERTICAL = 0.42;
    private static final double LUNGE_STOP_SHORT = 1.5;    // land this far in front of the target, inside melee reach
    private static final double LUNGE_FLIGHT_FACTOR = 8.65; // total horizontal flight / launch speed, measured with the drag rule
    private static final double LUNGE_MIN_SPEED = 0.3;
    private static final int LUNGE_FLIGHT_TICKS = 14;      // simulated flight time of the impulse (12 to 14 ticks)

    private boolean veteran = false;
    private long nextEnrageAtTick = 0L;
    private static final boolean TEST_MODE = Boolean.getBoolean("emberfall.testMode");
    private long nextLungeAtTick = 0L;
    private long lungeAtTick = -1L;   // >= 0 while crouching; the leap fires on that tick
    private long leapingUntilTick = -1L;   // while now < this, the AI must not steer: its walking input would cancel the leap

    public HordeZombie(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    /**
     * Upgrades this zombie in place to the Veteran tier. Must be called
     * right after spawn, before {@code addFreshEntity} - stat base values
     * are only safe to rewrite before the entity starts taking real damage.
     */
    public void becomeVeteran() {
        this.veteran = true;

        AttributeInstance health = this.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(health.getBaseValue() * VETERAN_HEALTH_MULT);
            this.setHealth((float) health.getValue());
        }
        AttributeInstance damage = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(damage.getBaseValue() * VETERAN_DAMAGE_MULT);
        }

        MobNames.apply(this, "Veteran Horde Zombie", MobNames.Tier.VETERAN);

        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY() + 1.0, this.getZ(),
                    20, 0.4, 0.6, 0.4, 0.02);
        }
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 1.0F, 0.7F);
    }

    public boolean isVeteran() {
        return veteran;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!this.isAlive()) {
            return;
        }
        long now = level.getGameTime();
        if (now < leapingUntilTick) {
            this.getNavigation().stop();
            this.setZza(0.0F);
            this.setXxa(0.0F);
            return;
        }
        if (lungeAtTick >= 0L) {
            if (now >= lungeAtTick) {
                lungeAtTick = -1L;
                if (TEST_MODE) {
                    com.solme.emberfall.EmberfallMod.LOGGER.info("ZOMBIE_TEST launch tick={}", now);
                }
                leap(level);
            }
            return;
        }
        if (now < nextLungeAtTick || now % 5L != 0L) {
            return;
        }
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive() || !this.onGround()) {
            return;
        }
        double distSq = this.position().distanceToSqr(target.position());
        if (distSq < LUNGE_MIN_RANGE * LUNGE_MIN_RANGE || distSq > LUNGE_MAX_RANGE * LUNGE_MAX_RANGE || !this.hasLineOfSight(target)) {
            return;
        }
        lungeAtTick = now + LUNGE_TELEGRAPH_TICKS;
        nextLungeAtTick = now + LUNGE_COOLDOWN_TICKS;
        if (TEST_MODE) {
            com.solme.emberfall.EmberfallMod.LOGGER.info("ZOMBIE_TEST crouch tick={} dist={}", now, String.format("%.1f", Math.sqrt(distSq)));
        }
        this.getNavigation().stop();
        level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 0.3, this.getZ(), 8, 0.3, 0.05, 0.3, 0.02);
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ZOMBIE_AMBIENT, SoundSource.HOSTILE, 1.0F, 0.6F);
    }

    private void leap(ServerLevel level) {
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive() || !this.onGround()) {
            return;
        }
        Vec3 flat = new Vec3(target.getX() - this.getX(), 0.0, target.getZ() - this.getZ());
        if (flat.lengthSqr() < 1.0E-4) {
            return;
        }
        Vec3 dir = flat.normalize();
        this.setOnGround(false);
        this.setPos(this.getX(), this.getY() + 0.02, this.getZ());
        // Air drag makes the total flight about 8.65 x the launch speed (measured: 0.819 launch -> 7.09 blocks). Aim to stop
        // LUNGE_STOP_SHORT blocks in front of the target, so a close target is not overflown and a far one is reached.
        double wanted = Math.max(0.0, Math.sqrt(flat.lengthSqr()) - LUNGE_STOP_SHORT);
        double speed = Math.min(LUNGE_HORIZONTAL, Math.max(LUNGE_MIN_SPEED, wanted / LUNGE_FLIGHT_FACTOR));
        this.setDeltaMovement(dir.x * speed, LUNGE_VERTICAL, dir.z * speed);
        this.hurtMarked = true;
        leapingUntilTick = level.getGameTime() + LUNGE_FLIGHT_TICKS;
        level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 0.1, this.getZ(), 6, 0.2, 0.02, 0.2, 0.01);
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 0.8F, 1.2F);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && veteran && this.isAlive()) {
            long now = this.level().getGameTime();
            if (now >= nextEnrageAtTick) {
                nextEnrageAtTick = now + ENRAGE_COOLDOWN_TICKS;
                this.addEffect(new MobEffectInstance(MobEffects.SPEED, ENRAGE_DURATION_TICKS, 1, false, true, true));
                level.sendParticles(ParticleTypes.ANGRY_VILLAGER, this.getX(), this.getY() + 2.0, this.getZ(),
                        4, 0.2, 0.1, 0.2, 0.0);
                level.playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.5F, 1.7F);
            }
        }
        return hurt;
    }
}
