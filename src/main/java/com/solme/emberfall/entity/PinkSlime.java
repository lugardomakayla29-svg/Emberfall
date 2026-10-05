package com.solme.emberfall.entity;

import com.solme.emberfall.EmberfallMod;
import com.solme.emberfall.combat.Fx;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The Umbral Magus's cauldron payoff: a pink slime that climbs out of the pot, grows to size {@value #FULL_SIZE}
 * (a 2.6 block body: vanilla slime hitbox is 0.52 x size) and then fights with four moves, all driven from
 * {@link #customServerAiStep} with no helper entities:
 * <ul>
 *   <li><b>Spit</b>: a pink ball ({@link TrackedProjectiles#launchSweep}, zero entities) that slows and splats.</li>
 *   <li><b>Trail</b>: it leaves thin pink puddles that dry up ({@link PinkPools#trail}).</li>
 *   <li><b>Leap and slam</b>: a telegraphed, very high jump that lands on the player's position and slams.</li>
 *   <li><b>Burst</b>: on death it bursts into a large puddle instead of splitting.</li>
 * </ul>
 *
 * <p>Engine facts this class depends on (all from the 1.21.11 bytecode, see the project notes):
 * {@code Slime.setSize} clamps to 1..127 and OVERWRITES max health, speed and damage before healing to full, so
 * every size change goes through {@link #applySize}, which re-applies this class's stats and keeps the health
 * fraction; and {@code Slime.remove} splits a dying slime of size above 1 into 2 to 4 copies of its own type, which
 * would copy this AI onto every child, so {@link #remove} drops the size to 1 first.</p>
 */
public class PinkSlime extends Slime {
    /** The break particle every Pink Slime effect uses: an item particle textured with the slime's own pink. */
    private static net.minecraft.core.particles.ParticleOptions pinkGlob;

    static net.minecraft.core.particles.ParticleOptions pinkGlob() {
        if (pinkGlob == null) {   // built on first use: the item is registered after this class loads
            pinkGlob = new net.minecraft.core.particles.ItemParticleOption(ParticleTypes.ITEM,
                    new net.minecraft.world.item.ItemStack(com.solme.emberfall.item.ModItems.PINK_SLIME_GLOB));
        }
        return pinkGlob;
    }

    /** Vanilla's landing squish burst asks this for its particle; without the override it is the green slimeball. */
    @Override
    protected net.minecraft.core.particles.ParticleOptions getParticleType() {
        return pinkGlob();
    }

    public static final int FULL_SIZE = 5;
    public static final int START_SIZE = 1;

    private static final double MAX_HEALTH = 140.0;
    private static final double MOVEMENT_SPEED = 0.26;
    private static final double ATTACK_DAMAGE = 5.0;
    private static final int GROW_TICKS = 40;
    private static final int LIFESPAN_TICKS = 1200;          // 60 s, then it bursts on its own

    private static final int SPIT_COOLDOWN = 70;
    private static final int SPIT_WINDUP = 14;
    private static final double SPIT_SPEED = 0.6;
    private static final float SPIT_DAMAGE = 4.0F;
    private static final double SPIT_HIT_RADIUS = 0.8;
    private static final int SPIT_LIFETIME = 60;

    private static final int LEAP_COOLDOWN = 160;
    private static final int LEAP_WINDUP = 24;
    private static final double LEAP_VY = 1.2;               // 8.2 block peak, 29 ticks airborne (computed, not guessed)
    private static final int LEAP_AIRTIME = 29;
    private static final double LEAP_MIN = 4.0;
    /** Pressed in melee this long, it leaps anyway (over the player) so it never gets stuck biting. */
    private static final int CLOSE_LEAP_AFTER = 40;
    private static final double CLOSE_LEAP_RANGE = 3.2;
    private static final double LEAP_MAX = 14.0;
    private static final double SLAM_RADIUS = 4.2;
    private static final float SLAM_DAMAGE = 9.0F;

    private static final int TRAIL_EVERY = 6;
    private static final boolean TEST_MODE = Boolean.getBoolean("emberfall.testMode");
    private static final DustParticleOptions PINK_DUST = new DustParticleOptions(PinkPools.PINK, 1.5F);

    private int ageTicks = 0;
    private int currentSize = START_SIZE;
    private double statMultiplier = 1.0;

    private long nextSpitAt = 0L;
    private int spitWindup = -1;
    private LivingEntity spitTarget;

    private long nextLeapAt = 0L;
    private int leapWindup = -1;
    private Vec3 leapAim;
    private int leapAir = -1;                                 // ticks since launch, -1 when not leaping
    private boolean dead = false;
    private int closeTicks = 0;                               // consecutive ticks spent inside melee range

    public PinkSlime(EntityType<? extends Slime> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED)
                .add(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    /** Spawns a pink slime at the top of the cauldron, size 1, and starts it growing. */
    public static PinkSlime spawn(ServerLevel level, Vec3 pos, LivingEntity target) {
        return spawn(level, pos, target, 1.0);
    }

    public static PinkSlime spawn(ServerLevel level, Vec3 pos, LivingEntity target, double statMultiplier) {
        PinkSlime slime = new PinkSlime(ModEntities.PINK_SLIME, level);
        slime.statMultiplier = statMultiplier;
        slime.applySize(START_SIZE, 1.0);
        slime.setPos(pos.x, pos.y, pos.z);
        MobNames.apply(slime, "Pink Slime", MobNames.Tier.SLIME);
        slime.setNoGravity(true);                              // floats up out of the pot, then settles
        if (target != null) {
            slime.setTarget(target);
        }
        level.addFreshEntity(slime);
        level.sendParticles(PINK_DUST, pos.x, pos.y + 0.4, pos.z, 30, 0.4, 0.3, 0.4, 0.0);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.SLIME_JUMP, SoundSource.HOSTILE, 1.4F, 0.5F);
        return slime;
    }

    /**
     * Sets the slime size and puts this class's stats back. {@code Slime.setSize} overwrites max health (= size),
     * speed (= 0.2 + 0.1 x size) and damage (= size) and heals to full, which would reset HP on every growth step,
     * so the health FRACTION is captured first and restored after.
     */
    private void applySize(int size, double healthFraction) {
        super.setSize(size, false);
        this.currentSize = size;
        double grown = (double) size / FULL_SIZE;              // stats scale up with the body
        setBase(Attributes.MAX_HEALTH, MAX_HEALTH * statMultiplier * Math.max(0.2, grown));
        setBase(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED);
        setBase(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE * statMultiplier);
        this.setHealth((float) (this.getMaxHealth() * Math.max(0.0, Math.min(1.0, healthFraction))));
    }

    private void setBase(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, double value) {
        AttributeInstance inst = this.getAttribute(attr);
        if (inst != null) {
            inst.setBaseValue(value);
        }
    }

    /** The vanilla contact damage reads getAttackDamage(), which setSize does not touch after our re-apply. */
    @Override
    protected float getAttackDamage() {
        return (float) (ATTACK_DAMAGE * statMultiplier);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        ageTicks++;

        if (ageTicks <= GROW_TICKS) {
            tickGrowth(level);
            return;                                            // no attacks while it is still climbing out
        }
        if (ageTicks >= LIFESPAN_TICKS && !this.isDeadOrDying()) {
            this.kill(level);
            return;
        }

        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        tickTrail(level);
        closeTicks = this.distanceTo(target) <= CLOSE_LEAP_RANGE ? closeTicks + 1 : 0;
        if (spitWindup < 0) {
            tickLeap(level, target);                           // a leap never starts while a spit is winding up
        }
        if (leapWindup < 0 && leapAir < 0) {
            tickSpit(level, target);                           // a spit never starts (or resumes) during a leap
        }
    }

    // ---- growth -------------------------------------------------------------------------------------------

    private void tickGrowth(ServerLevel level) {
        double t = ageTicks / (double) GROW_TICKS;
        int wanted = START_SIZE + (int) Math.floor(t * (FULL_SIZE - START_SIZE));
        wanted = Math.min(FULL_SIZE, wanted);
        if (ageTicks == GROW_TICKS) {
            wanted = FULL_SIZE;
        }
        if (wanted != currentSize) {
            double frac = this.getMaxHealth() > 0 ? this.getHealth() / this.getMaxHealth() : 1.0;
            applySize(wanted, frac);
            level.sendParticles(PINK_DUST, this.getX(), this.getY() + 0.3, this.getZ(), 14, 0.5, 0.2, 0.5, 0.0);
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.0F,
                    0.6F + 0.1F * wanted);
            if (TEST_MODE) {
                EmberfallMod.LOGGER.info("PINK_TEST grow size={} hp={}/{}", wanted, this.getHealth(), this.getMaxHealth());
            }
        }
        if (ageTicks % 3 == 0) {
            level.sendParticles(pinkGlob(), this.getX(), this.getY() + 0.2, this.getZ(), 4, 0.3, 0.1, 0.3, 0.02);
        }
        if (ageTicks == GROW_TICKS) {
            this.setNoGravity(false);
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SLIME_JUMP, SoundSource.HOSTILE, 1.6F, 0.4F);
            if (TEST_MODE) {
                EmberfallMod.LOGGER.info("PINK_TEST grown hp={}/{}", this.getHealth(), this.getMaxHealth());
            }
        }
    }

    // ---- trail --------------------------------------------------------------------------------------------

    private void tickTrail(ServerLevel level) {
        if (ageTicks % TRAIL_EVERY == 0 && this.onGround() && this.getDeltaMovement().horizontalDistanceSqr() > 0.0004) {
            PinkPools.trail(level, this.position(), this);
        }
    }

    // ---- spit ---------------------------------------------------------------------------------------------

    private void tickSpit(ServerLevel level, LivingEntity target) {
        long now = level.getGameTime();
        if (spitWindup >= 0) {
            spitWindup++;
            if (spitWindup % 4 == 0) {
                level.sendParticles(PINK_DUST, this.getX(), this.getY() + this.getBbHeight() * 0.7, this.getZ(),
                        6, 0.4, 0.3, 0.4, 0.0);
            }
            if (spitWindup >= SPIT_WINDUP) {
                spitWindup = -1;
                fireSpit(level, spitTarget != null && spitTarget.isAlive() ? spitTarget : target);
            }
            return;
        }
        if (now < nextSpitAt || !this.hasLineOfSight(target)) {
            return;
        }
        double dist = this.distanceTo(target);
        if (dist < 5.0 || dist > 24.0) {
            return;
        }
        nextSpitAt = now + SPIT_COOLDOWN;
        spitWindup = 0;
        spitTarget = target;
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SLIME_ATTACK, SoundSource.HOSTILE, 1.3F, 0.6F);
        if (TEST_MODE) {
            EmberfallMod.LOGGER.info("PINK_TEST spit telegraph tick={}", now);
        }
    }

    private void fireSpit(ServerLevel level, LivingEntity target) {
        Vec3 from = this.position().add(0.0, this.getBbHeight() * 0.6, 0.0);
        Vec3 aim = target.getEyePosition().subtract(from);
        if (aim.lengthSqr() < 0.01) {
            return;
        }
        Vec3 velocity = aim.normalize().scale(SPIT_SPEED);
        PinkSlime self = this;
        TrackedProjectiles.launchSweep(level, from, velocity, this, SPIT_LIFETIME, pinkGlob(),
                pos -> {
                    AABB box = new AABB(pos, pos).inflate(SPIT_HIT_RADIUS);
                    List<Player> players = level.getEntitiesOfClass(Player.class, box, p -> p.isAlive() && !p.isSpectator());
                    return players.isEmpty() ? null : players.get(0);
                },
                (pos, victim) -> {
                    victim.hurtServer(level, level.damageSources().mobAttack(self), (float) (SPIT_DAMAGE * statMultiplier));
                    victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, PinkPools.SLOW_TICKS, 1, false, true));
                    PinkPools.splat(level, pos, self);
                    splash(level, pos);
                    if (TEST_MODE) {
                        EmberfallMod.LOGGER.info("PINK_TEST spit hit");
                    }
                },
                pos -> {
                    PinkPools.splat(level, pos, self);
                    splash(level, pos);
                    if (TEST_MODE) {
                        EmberfallMod.LOGGER.info("PINK_TEST spit landed");
                    }
                },
                0.0);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SLIME_ATTACK, SoundSource.HOSTILE, 1.4F, 1.1F);
        if (TEST_MODE) {
            EmberfallMod.LOGGER.info("PINK_TEST spit tick={}", level.getGameTime());
        }
    }

    private void splash(ServerLevel level, Vec3 at) {
        level.sendParticles(PINK_DUST, at.x, at.y + 0.2, at.z, 16, 0.4, 0.2, 0.4, 0.0);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.0F, 0.9F);
    }

    // ---- leap and slam ------------------------------------------------------------------------------------

    private void tickLeap(ServerLevel level, LivingEntity target) {
        long now = level.getGameTime();
        if (leapAir >= 0) {                                    // airborne: watch for the landing
            leapAir++;
            if (leapAir > 3 && this.onGround()) {
                slam(level);
                leapAir = -1;
            } else if (leapAir > 80) {
                leapAir = -1;                                  // safety net: never stay "leaping" forever
            }
            return;
        }
        if (leapWindup >= 0) {
            leapWindup++;
            double progress = leapWindup / (double) LEAP_WINDUP;
            if (leapWindup % 3 == 0 && leapAim != null) {
                Fx.telegraphFill(level, leapAim, SLAM_RADIUS, progress, Fx.WARN_RED);
            }
            this.getNavigation().stop();
            if (leapWindup >= LEAP_WINDUP) {
                launch(level);
            }
            return;
        }
        if (now < nextLeapAt || !this.onGround()) {
            return;
        }
        double dist = this.distanceTo(target);
        boolean pressed = closeTicks >= CLOSE_LEAP_AFTER;
        if (!pressed && (dist < LEAP_MIN || dist > LEAP_MAX)) {
            return;
        }
        nextLeapAt = now + LEAP_COOLDOWN;
        closeTicks = 0;
        leapWindup = 0;
        leapAim = target.position();
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.6F, 0.4F);
        if (TEST_MODE) {
            EmberfallMod.LOGGER.info("PINK_TEST leap telegraph dist={}", String.format("%.1f", dist));
        }
    }

    /**
     * Solves the horizontal launch speed from the distance, like the zombie lunge: with air drag 0.91 per tick the
     * horizontal travel over n ticks is h x (1 - 0.91^n) / 0.09, so h = distance x 0.09 / (1 - 0.91^n).
     */
    private void launch(ServerLevel level) {
        leapWindup = -1;
        Vec3 flat = new Vec3(leapAim.x - this.getX(), 0.0, leapAim.z - this.getZ());
        double dist = flat.length();
        double h = dist > 0.5 ? dist * 0.09 / (1.0 - Math.pow(0.91, LEAP_AIRTIME)) : 0.0;
        h = Math.min(h, 1.1);
        Vec3 dir = dist > 0.001 ? flat.normalize() : Vec3.ZERO;
        this.setDeltaMovement(dir.x * h, LEAP_VY, dir.z * h);
        this.hurtMarked = true;                                // sync the velocity to clients
        leapAir = 0;
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SLIME_JUMP, SoundSource.HOSTILE, 1.8F, 0.5F);
        level.sendParticles(PINK_DUST, this.getX(), this.getY() + 0.2, this.getZ(), 24, 0.6, 0.1, 0.6, 0.0);
        if (TEST_MODE) {
            EmberfallMod.LOGGER.info("PINK_TEST leap launch h={}", String.format("%.2f", h));
        }
    }

    private void slam(ServerLevel level) {
        Vec3 at = this.position();
        Fx.slam(level, at, SLAM_RADIUS, PinkPools.PINK);
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 2, 0.6, 0.1, 0.6, 0.0);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 2.0F, 0.4F);
        AABB box = new AABB(at.x - SLAM_RADIUS, at.y - 1.0, at.z - SLAM_RADIUS, at.x + SLAM_RADIUS, at.y + 2.5, at.z + SLAM_RADIUS);
        int hits = 0;
        for (Player player : level.getEntitiesOfClass(Player.class, box, p -> p.isAlive() && !p.isSpectator())) {
            if (player.position().distanceTo(at) <= SLAM_RADIUS) {
                player.hurtServer(level, level.damageSources().mobAttack(this), (float) (SLAM_DAMAGE * statMultiplier));
                Vec3 push = player.position().subtract(at);
                Vec3 n = push.lengthSqr() > 0.001 ? push.normalize().scale(0.9) : Vec3.ZERO;
                player.setDeltaMovement(n.x, 0.45, n.z);
                player.hurtMarked = true;
                hits++;
            }
        }
        PinkPools.splat(level, at, this);
        if (TEST_MODE) {
            EmberfallMod.LOGGER.info("PINK_TEST slam hits={} at={},{},{}", hits, String.format("%.1f", at.x),
                    String.format("%.1f", at.y), String.format("%.1f", at.z));
        }
    }

    // ---- death --------------------------------------------------------------------------------------------

    /** The spec: it bursts into a large puddle; no children, no split. */
    private void burstInto(ServerLevel level) {
        if (dead) {
            return;
        }
        dead = true;
        Vec3 at = this.position();
        PinkPools.burst(level, at, null);
        level.sendParticles(PINK_DUST, at.x, at.y + 1.0, at.z, 80, 1.6, 0.9, 1.6, 0.0);
        level.sendParticles(pinkGlob(), at.x, at.y + 1.0, at.z, 50, 1.4, 0.8, 1.4, 0.1);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_DEATH, SoundSource.HOSTILE, 2.0F, 0.4F);
        if (TEST_MODE) {
            EmberfallMod.LOGGER.info("PINK_TEST burst size={}", currentSize);
        }
    }

    /**
     * Fires synchronously for EVERY removal path (combat death, /kill, discard, unload), unlike a tick check. The
     * burst only happens on a real death, and the size is dropped to 1 before the parent runs: {@code Slime.remove}
     * splits only when getSize() is above 1 (bytecode), so this is what keeps a size-5 slime from spawning 2 to 4
     * copies of itself.
     */
    @Override
    public void remove(Entity.RemovalReason reason) {
        if (this.isDeadOrDying() && this.level() instanceof ServerLevel serverLevel) {
            burstInto(serverLevel);
        }
        if (!this.level().isClientSide() && currentSize > 1) {
            super.setSize(1, false);                           // vanilla split needs size > 1: this disables it
            currentSize = 1;
        }
        super.remove(reason);
    }
}
