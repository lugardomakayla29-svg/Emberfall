package com.solme.emberfall.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Sixth horde filler: a slow, heavy brute that commits to a straight-line rush.
 *
 * <p>Cycle: it must have a target 5 to 12 blocks away in line of sight, then it stops and snorts for
 * {@value #TELEGRAPH_TICKS} ticks (the player's warning), locks the direction, and runs that line for up to
 * {@value #RUSH_MAX_TICKS} ticks or until it hits something. A wall ends the rush and stuns it for
 * {@value #STUN_TICKS} ticks, which is the punish window. The rush line is locked, so a player who steps aside
 * is missed; a player in the way takes a hit and is thrown.</p>
 *
 * <p>Cost: nothing is spawned, no raycast, no area scan. The engine's own {@code horizontalCollision} flag ends the
 * rush, and the hit check is one bounding-box test against the single known target.</p>
 */
public class HordeCharger extends Zombie {
    private static final double BASE_HEALTH = 60.0;
    private static final double BASE_SPEED = 0.20;          // zombie is 0.23
    private static final double BASE_DAMAGE = 5.0;
    private static final double KNOCKBACK_RESISTANCE = 0.6;
    private static final double VETERAN_HEALTH_MULT = 2.5;
    private static final double VETERAN_DAMAGE_MULT = 1.6;

    private static final double RUSH_MIN_RANGE = 5.0;
    private static final double RUSH_MAX_RANGE = 12.0;
    private static final int TELEGRAPH_TICKS = 20;          // 1 s stop and snort
    private static final int RUSH_MAX_TICKS = 30;           // 1.5 s
    private static final double RUSH_SPEED_MODIFIER = 3.0;  // multiplies the movement speed attribute
    private static final int STUN_TICKS = 40;               // 2 s
    private static final int COOLDOWN_TICKS = 160;          // 8 s
    private static final int VETERAN_COOLDOWN_TICKS = 100;  // 5 s
    private static final double HIT_KNOCKBACK = 1.1;
    private static final double RUSH_LINE_AHEAD = 24.0;     // how far along the locked line the wanted position sits

    private static final boolean TEST_MODE = Boolean.getBoolean("emberfall.testMode");

    private boolean veteran = false;
    private long nextRushAtTick = 0L;
    private long telegraphUntilTick = -1L;
    private long rushUntilTick = -1L;
    private long stunUntilTick = -1L;
    private Vec3 rushDir = Vec3.ZERO;
    private boolean rushHitDone = false;

    public HordeCharger(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    /** Door breaking stays off, like every horde zombie (see {@link HordeZombie#setCanBreakDoors}). */
    @Override
    public void setCanBreakDoors(boolean canBreakDoors) {
        // intentionally ignored
    }

    public static AttributeSupplier.Builder createChargerAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, BASE_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, BASE_SPEED)
                .add(Attributes.ATTACK_DAMAGE, BASE_DAMAGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE);
    }

    /** Called once right after construction, before the entity is added to the world. */
    public void prepare() {
        this.setHealth(this.getMaxHealth());
        MobNames.apply(this, "Horde Charger", MobNames.Tier.ELITE);
    }

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
        MobNames.apply(this, "Veteran Horde Charger", MobNames.Tier.VETERAN);
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

        if (now < stunUntilTick) {
            holdStill();
            if ((now & 3L) == 0L) {
                level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 2.0, this.getZ(), 2, 0.3, 0.1, 0.3, 0.0);
            }
            return;
        }
        if (now < telegraphUntilTick) {
            holdStill();
            if ((now & 3L) == 0L) {
                level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.1, this.getZ(), 4, 0.3, 0.05, 0.3, 0.02);
            }
            return;
        }
        if (telegraphUntilTick >= 0L && now >= telegraphUntilTick && rushUntilTick < 0L) {
            beginRush(level, now);
            return;
        }
        if (now < rushUntilTick) {
            continueRush(level, now);
            return;
        }
        if (rushUntilTick >= 0L && now >= rushUntilTick) {
            rushUntilTick = -1L;          // rush ran its length without a wall: back to normal hunting
            telegraphUntilTick = -1L;
        }
        if (now < nextRushAtTick || now % 5L != 0L) {
            return;
        }
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive() || !this.onGround()) {
            return;
        }
        double distSq = this.position().distanceToSqr(target.position());
        if (distSq < RUSH_MIN_RANGE * RUSH_MIN_RANGE || distSq > RUSH_MAX_RANGE * RUSH_MAX_RANGE || !this.hasLineOfSight(target)) {
            return;
        }
        telegraphUntilTick = now + TELEGRAPH_TICKS;
        if (TEST_MODE) { com.solme.emberfall.EmberfallMod.LOGGER.info("CHARGER_TEST windup tick={}", now); }
        nextRushAtTick = now + (veteran ? VETERAN_COOLDOWN_TICKS : COOLDOWN_TICKS);
        rushUntilTick = -1L;
        this.getNavigation().stop();
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.0F, 1.4F);
    }

    private void holdStill() {
        this.getNavigation().stop();
        this.setZza(0.0F);
        this.setXxa(0.0F);
    }

    private void beginRush(ServerLevel level, long now) {
        LivingEntity target = this.getTarget();
        Vec3 flat = target == null ? Vec3.ZERO
                : new Vec3(target.getX() - this.getX(), 0.0, target.getZ() - this.getZ());
        if (flat.lengthSqr() < 1.0E-4) {
            telegraphUntilTick = -1L;     // target vanished or is on top of us: skip the rush
            return;
        }
        rushDir = flat.normalize();       // locked now: a player who sidesteps is missed
        rushUntilTick = now + RUSH_MAX_TICKS;
        rushHitDone = false;
        if (TEST_MODE) { com.solme.emberfall.EmberfallMod.LOGGER.info("CHARGER_TEST rush tick={} dir=({},{})", now, String.format("%.2f", rushDir.x), String.format("%.2f", rushDir.z)); }
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 1.0F, 0.9F);
    }

    private void continueRush(ServerLevel level, long now) {
        // re-issue the goal every tick: MoveControl reverts to WAIT after one tick (learned at the witch)
        Vec3 ahead = this.position().add(rushDir.scale(RUSH_LINE_AHEAD));
        this.getMoveControl().setWantedPosition(ahead.x, this.getY(), ahead.z, RUSH_SPEED_MODIFIER);
        if ((now & 1L) == 0L) {
            level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 0.1, this.getZ(), 2, 0.3, 0.02, 0.3, 0.01);
        }

        LivingEntity target = this.getTarget();
        if (!rushHitDone && target != null && target.isAlive()
                && this.getBoundingBox().inflate(0.3, 0.0, 0.3).intersects(target.getBoundingBox())) {
            rushHitDone = true;
            float dmg = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
            boolean landed = target.hurtServer(level, this.damageSources().mobAttack(this), dmg);
            if (TEST_MODE) { com.solme.emberfall.EmberfallMod.LOGGER.info("CHARGER_TEST hit tick={} dmg={} landed={}", now, dmg, landed); }
            if (landed) {
                target.push(rushDir.x * HIT_KNOCKBACK, 0.35, rushDir.z * HIT_KNOCKBACK);
                target.hurtMarked = true;
                level.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 1.0F, 0.8F);
            }
        }
        // the engine sets horizontalCollision when a wall stops the body: that ends the rush and stuns it
        if (this.horizontalCollision && now > rushUntilTick - RUSH_MAX_TICKS + 4) {
            stunUntilTick = now + STUN_TICKS;
            if (TEST_MODE) { com.solme.emberfall.EmberfallMod.LOGGER.info("CHARGER_TEST stun tick={}", now); }
            rushUntilTick = -1L;
            telegraphUntilTick = -1L;
            holdStill();
            level.sendParticles(ParticleTypes.EXPLOSION, this.getX() + rushDir.x, this.getY() + 1.0, this.getZ() + rushDir.z,
                    1, 0.0, 0.0, 0.0, 0.0);
            level.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.8F, 0.7F);
        }
    }
}
