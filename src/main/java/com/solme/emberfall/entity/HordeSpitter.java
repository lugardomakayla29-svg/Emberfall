package com.solme.emberfall.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Fifth new horde filler: a ranged acid spitter that holds its distance and leaves puddles.
 *
 * <p>Cycle: with a target 6 to 16 blocks away in line of sight it stops and bubbles for {@value #TELEGRAPH_TICKS}
 * ticks (the warning), then spits one glob aimed at where the target IS at that moment, so a player who keeps moving
 * dodges it. The glob is a virtual bolt ({@link TrackedProjectiles#launchSweep}): no entity, stops on walls and the
 * floor. On a hit it damages and slows; wherever it ends (hit, wall, floor or timeout) it leaves an
 * {@link AcidPools} puddle that burns anyone standing in it.</p>
 *
 * <p>Cost per spitter: zero entities for its attack, one bolt in a shared list while a glob is in flight, and at most
 * one puddle in a server-wide capped list. The melee goals a zombie normally has are removed; it never walks up and
 * slaps, it keeps its distance like the witch.</p>
 */
public class HordeSpitter extends Zombie {
    private static final double BASE_HEALTH = 16.0;
    private static final double BASE_SPEED = 0.23;
    private static final double VETERAN_HEALTH_MULT = 2.5;
    private static final float BASE_SPIT_DAMAGE = 3.0F;
    private static final float VETERAN_SPIT_DAMAGE = 5.0F;

    public static final double MIN_RANGE = 6.0;           // closer than this it backs away
    public static final double MAX_RANGE = 16.0;          // further than this it walks in
    private static final int TELEGRAPH_TICKS = 12;
    private static final int COOLDOWN_TICKS = 50;
    private static final int VETERAN_COOLDOWN_TICKS = 34;
    private static final double SPIT_SPEED = 0.9;         // blocks per tick
    private static final int SPIT_LIFETIME = 30;          // 27 blocks of reach, beyond MAX_RANGE
    private static final double SPIT_HIT_RADIUS = 0.6;

    private static final boolean TEST_MODE = Boolean.getBoolean("emberfall.testMode");

    private boolean veteran = false;
    private long nextSpitAtTick = 0L;
    private long spitAtTick = -1L;     // >= 0 while telegraphing; the glob leaves on this tick

    public HordeSpitter(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    /** Door breaking stays off, like every horde zombie. */
    @Override
    public void setCanBreakDoors(boolean canBreakDoors) {
        // intentionally ignored
    }

    public static AttributeSupplier.Builder createSpitterAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, BASE_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, BASE_SPEED);
    }

    /**
     * super.registerGoals() still runs (Zombie keeps a private breakDoorGoal that only it assigns); then the melee
     * goals come out and the keep-distance goal goes in, so it never closes to slap range on its own.
     */
    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.removeAllGoals(g -> g instanceof net.minecraft.world.entity.ai.goal.ZombieAttackGoal
                || g instanceof net.minecraft.world.entity.ai.goal.SpearUseGoal);
        this.goalSelector.addGoal(2, new KeepRangeGoal(this));
    }

    public void prepare() {
        this.setHealth(this.getMaxHealth());
        MobNames.apply(this, "Horde Spitter", MobNames.Tier.ELITE);
    }

    public void becomeVeteran() {
        this.veteran = true;
        AttributeInstance health = this.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(health.getBaseValue() * VETERAN_HEALTH_MULT);
            this.setHealth((float) health.getValue());
        }
        MobNames.apply(this, "Veteran Horde Spitter", MobNames.Tier.VETERAN);
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
        LivingEntity target = this.getTarget();

        if (spitAtTick >= 0L) {
            this.getNavigation().stop();
            if (target != null) {
                this.getLookControl().setLookAt(target, 60.0F, 60.0F);
            }
            if ((now & 3L) == 0L) {
                level.sendParticles(ParticleTypes.ITEM_SLIME, this.getX(), this.getEyeY() - 0.2, this.getZ(),
                        4, 0.2, 0.1, 0.2, 0.02);
            }
            if (now >= spitAtTick) {
                spitAtTick = -1L;
                if (target != null && target.isAlive()) {
                    spit(level, target);
                }
            }
            return;
        }
        if (now < nextSpitAtTick || now % 5L != 0L) {
            return;
        }
        if (target == null || !target.isAlive() || !this.hasLineOfSight(target)) {
            return;
        }
        double distSq = this.distanceToSqr(target);
        if (distSq < MIN_RANGE * MIN_RANGE || distSq > MAX_RANGE * MAX_RANGE) {
            return;
        }
        spitAtTick = now + TELEGRAPH_TICKS;
        nextSpitAtTick = now + (veteran ? VETERAN_COOLDOWN_TICKS : COOLDOWN_TICKS);
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.LLAMA_SPIT, SoundSource.HOSTILE, 0.8F, 0.7F);
        if (TEST_MODE) {
            com.solme.emberfall.EmberfallMod.LOGGER.info("SPITTER_TEST telegraph tick={} dist={}", now,
                    String.format("%.1f", Math.sqrt(distSq)));
        }
    }

    private void spit(ServerLevel level, LivingEntity target) {
        Vec3 from = new Vec3(this.getX(), this.getEyeY() - 0.15, this.getZ());
        Vec3 aim = new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ());
        Vec3 dir = aim.subtract(from);
        if (dir.lengthSqr() < 1.0E-4) {
            return;
        }
        Vec3 velocity = dir.normalize().scale(SPIT_SPEED);
        float damage = veteran ? VETERAN_SPIT_DAMAGE : BASE_SPIT_DAMAGE;
        HordeSpitter self = this;
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.LLAMA_SPIT, SoundSource.HOSTILE, 1.0F, 1.1F);
        if (TEST_MODE) {
            com.solme.emberfall.EmberfallMod.LOGGER.info("SPITTER_TEST spit tick={}", level.getGameTime());
        }
        TrackedProjectiles.launchSweep(level, from, velocity, this, SPIT_LIFETIME, ParticleTypes.SPIT,
                pos -> playerAt(level, pos),
                (pos, victim) -> {
                    victim.hurtServer(level, level.damageSources().mobAttack(self), damage);
                    victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, AcidPools.SLOW_TICKS, 0, false, true));
                    AcidPools.spawn(level, pos, self);
                    if (TEST_MODE) {
                        com.solme.emberfall.EmberfallMod.LOGGER.info("SPITTER_TEST spit hit");
                    }
                },
                pos -> {
                    AcidPools.spawn(level, pos, self);
                    if (TEST_MODE) {
                        com.solme.emberfall.EmberfallMod.LOGGER.info("SPITTER_TEST spit landed");
                    }
                },
                0.0);   // no climbing: a glob splashes against a step instead of hopping over it
    }

    /** The glob only ever hurts players: the horde is on one team and acid must not touch it. */
    private static LivingEntity playerAt(ServerLevel level, Vec3 pos) {
        AABB box = AABB.ofSize(pos, SPIT_HIT_RADIUS * 2, SPIT_HIT_RADIUS * 2, SPIT_HIT_RADIUS * 2);
        for (Player player : level.getEntitiesOfClass(Player.class, box)) {
            if (player.isAlive() && !player.isSpectator()) {
                return player;
            }
        }
        return null;
    }

    /**
     * Holds a firing band: closes in beyond {@link #MAX_RANGE}, backs away inside {@link #MIN_RANGE}, stands and
     * faces the target in between. It must also close in, or a kiting ranged mob drifts out of range and idles
     * (measured on the witch).
     */
    private static final class KeepRangeGoal extends net.minecraft.world.entity.ai.goal.Goal {
        private final HordeSpitter spitter;
        private int repathIn = 0;

        KeepRangeGoal(HordeSpitter spitter) {
            this.spitter = spitter;
            this.setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = spitter.getTarget();
            return t != null && t.isAlive() && spitter.spitAtTick < 0L;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            LivingEntity t = spitter.getTarget();
            if (t == null) {
                return;
            }
            spitter.getLookControl().setLookAt(t, 30.0F, 30.0F);
            double d2 = spitter.distanceToSqr(t);
            if (d2 > MAX_RANGE * MAX_RANGE) {
                if (--repathIn <= 0) {
                    repathIn = 10;
                    spitter.getNavigation().moveTo(t, 1.0);
                }
            } else if (d2 < MIN_RANGE * MIN_RANGE) {
                if (--repathIn <= 0) {
                    repathIn = 10;
                    Vec3 away = spitter.position().subtract(t.position());
                    Vec3 dir = new Vec3(away.x, 0.0, away.z);
                    dir = dir.lengthSqr() > 1.0E-4 ? dir.normalize() : new Vec3(1.0, 0.0, 0.0);
                    Vec3 to = spitter.position().add(dir.scale(6.0));
                    spitter.getNavigation().moveTo(to.x, to.y, to.z, 1.1);
                }
            } else {
                spitter.getNavigation().stop();
            }
        }

        @Override
        public void stop() {
            spitter.getNavigation().stop();
        }
    }
}
