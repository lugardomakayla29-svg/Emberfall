package com.solme.emberfall.entity;

import com.solme.emberfall.entity.TrackedProjectiles;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Third horde-filler enemy (design doc 2.4 - "expand horde/elite variety",
 * SlopPack's {@code EliteMobListener} used as reference/inspiration, not a
 * straight port - see {@link HordeSkeleton}'s javadoc for the same note).
 * Vanilla Spider is already fully melee-capable out of the box (its own
 * {@code registerGoals} wires up a working {@code MeleeAttackGoal}, unlike
 * Skeleton which needed a full ranged-attack override - see
 * {@link HordeSkeleton}), so this class needs no base-tier changes at all:
 * a plain Spider's climbing/erratic movement is already a genuinely
 * different silhouette and threat pattern from {@link HordeZombie} (melee
 * grind) and {@link HordeSkeleton} (kiting ranged), which is the actual
 * point of adding it. Same "cheap by default, one extra reactive ability at
 * Veteran tier" shape as its two siblings.
 *
 * Veteran tier's extra ability, Web Shot (ported/adapted from SlopPack's
 * elite-Spider "web shot", tier-1 version: single Slowness+Mining Fatigue
 * application, not SlopPack's tier-2 add-on Silk Snare poison patch - filler
 * tier stays simpler than the elite version): fires a ranged debuff bolt at
 * the current target on a cooldown. Uses {@link TrackedProjectiles}'
 * virtual-bolt tracker (same "no extra entity" performance rule as
 * {@link com.solme.emberfall.combat.OrbitWeaponSystem} /
 * {@link com.solme.emberfall.combat.TotemWeaponSystem}) rather than a real
 * thrown-item entity - a Spider has no vanilla ranged-projectile pathway to
 * reuse the way Skeleton's bow gave {@link HordeSkeleton} one, so this was
 * always going to need a bespoke shot; virtual keeps it free of any real
 * entity cost regardless of how many Veteran spiders are on the field.
 *
 * Verified live (2026-09-27) via temporary debug logging against a real
 * spawned instance ({@code /emberfall spawnveteran horde_spider}): Web
 * Shot fires on its 10s cooldown and lands, confirmed by the target
 * actually gaining Slowness III + Mining Fatigue II (checked via
 * {@code /effect clear} reporting real effects removed, not "no effects
 * to remove"). Same Creative-mode-target testing gotcha as
 * {@link HordeSkeleton} applied here too. Debug logging removed after
 * verification.
 */
public class HordeSpider extends Spider {
    private static final double VETERAN_HEALTH_MULT = 2.5;
    private static final double VETERAN_DAMAGE_MULT = 1.6; // Spider's bite IS governed by Attributes.ATTACK_DAMAGE (vanilla Mob.doHurtTarget) - safe to reuse HordeZombie's generic multiplier here, unlike HordeSkeleton's ranged shot.
    private static final int WEB_SHOT_COOLDOWN_TICKS = 200; // 10s, matches SlopPack's tier-1 spider-elite cadence
    private static final double WEB_SHOT_RANGE = 14.0;
    private static final double WEB_SHOT_RANGE_SQ = WEB_SHOT_RANGE * WEB_SHOT_RANGE;
    private static final double WEB_SHOT_SPEED = 0.9; // blocks/tick
    private static final double WEB_SHOT_HIT_RADIUS = 0.9;
    private static final int WEB_SHOT_MAX_TICKS = 40; // 2s flight time cap
    private static final int WEB_SLOWNESS_DURATION_TICKS = 80; // 4s
    private static final int WEB_SLOWNESS_AMPLIFIER = 2; // Slowness III
    private static final int WEB_MINING_FATIGUE_AMPLIFIER = 1; // Mining Fatigue II

    /**
     * Base-mob signature move (every horde spider, not only veterans): a skitter-dodge. When hit, it hops sideways about
     * 3 blocks (perpendicular to the attacker, random side), so it is harder to focus down. Vanilla already gives every
     * spider a leap at its target (LeapAtTargetGoal), so this is a different verb on purpose. Same drag rule as the
     * zombie lunge: total flight = 8.65 x launch speed.
     */
    private static final int DODGE_COOLDOWN_TICKS = 80;   // 4 s
    private static final double DODGE_DISTANCE = 3.0;
    private static final double DODGE_FLIGHT_FACTOR = 8.65;
    private static final double DODGE_VERTICAL = 0.30;
    private static final int DODGE_FLIGHT_TICKS = 10;
    private static final boolean TEST_MODE = Boolean.getBoolean("emberfall.testMode");

    private boolean veteran = false;
    private long nextWebShotAtTick = 0L;
    private long nextDodgeAtTick = 0L;
    private long steerLockUntilTick = -1L;   // while now < this, the AI must not steer: its walk input would cancel the hop

    public HordeSpider(EntityType<? extends Spider> type, Level level) {
        super(type, level);
    }

    /** See {@link SpiderGoals}: vanilla's brightness gate left this spider without a target in the arena. */
    @Override
    protected void registerGoals() {
        SpiderGoals.install(this, this.goalSelector, this.targetSelector);
    }

    /** Called right after construction, before {@code addFreshEntity} - see {@link HordeZombie#becomeVeteran}'s javadoc on why. */
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

        MobNames.apply(this, "Veteran Horde Spider", MobNames.Tier.VENOM);

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
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && this.isAlive() && this.onGround()) {
            long now = level.getGameTime();
            if (now >= nextDodgeAtTick) {
                skitter(level, source, now);
            }
        }
        return hurt;
    }

    /** Sideways hop away from whatever hit us; skipped when there is no attacker position or no room. */
    private void skitter(ServerLevel level, DamageSource source, long now) {
        net.minecraft.world.entity.Entity attacker = source.getEntity();
        if (attacker == null) {
            return;
        }
        Vec3 fromAttacker = new Vec3(this.getX() - attacker.getX(), 0.0, this.getZ() - attacker.getZ());
        if (fromAttacker.lengthSqr() < 1.0E-4) {
            return;
        }
        Vec3 away = fromAttacker.normalize();
        double side = this.getRandom().nextBoolean() ? 1.0 : -1.0;
        Vec3 dir = new Vec3(-away.z * side, 0.0, away.x * side);
        Vec3 probe = dir.scale(DODGE_DISTANCE * 0.6);
        if (!level.noCollision(this, this.getBoundingBox().move(probe))) {
            dir = dir.scale(-1.0);   // blocked on that side: try the other
            if (!level.noCollision(this, this.getBoundingBox().move(dir.scale(DODGE_DISTANCE * 0.6)))) {
                return;
            }
        }
        nextDodgeAtTick = now + DODGE_COOLDOWN_TICKS;
        steerLockUntilTick = now + DODGE_FLIGHT_TICKS;
        double speed = DODGE_DISTANCE / DODGE_FLIGHT_FACTOR;
        this.setOnGround(false);
        this.setPos(this.getX(), this.getY() + 0.02, this.getZ());
        this.setDeltaMovement(dir.x * speed, DODGE_VERTICAL, dir.z * speed);
        this.hurtMarked = true;
        if (TEST_MODE) { com.solme.emberfall.EmberfallMod.LOGGER.info("SPIDER_TEST dodge tick={}", now); }
        level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 0.1, this.getZ(), 5, 0.2, 0.02, 0.2, 0.01);
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.SPIDER_STEP, SoundSource.HOSTILE, 1.0F, 1.5F);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (this.isAlive() && level.getGameTime() < steerLockUntilTick) {
            this.getNavigation().stop();
            this.setZza(0.0F);
            this.setXxa(0.0F);
        }
        if (!veteran || !this.isAlive()) {
            return;
        }
        long now = this.level().getGameTime();
        if (now < nextWebShotAtTick) {
            return;
        }
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive() || this.position().distanceToSqr(target.position()) > WEB_SHOT_RANGE_SQ) {
            return;
        }
        nextWebShotAtTick = now + WEB_SHOT_COOLDOWN_TICKS;
        fireWebShot(level, target);
    }

    /** Veteran-only extra ability - see class javadoc. */
    private void fireWebShot(ServerLevel level, LivingEntity target) {
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.SPIDER_HURT, SoundSource.HOSTILE, 1.2F, 0.5F);
        Vec3 origin = this.getEyePosition();
        Vec3 toTarget = target.getEyePosition().subtract(origin);
        Vec3 velocity = toTarget.normalize().scale(WEB_SHOT_SPEED);

        TrackedProjectiles.launchAtTarget(level, origin, velocity, this, target,
                WEB_SHOT_HIT_RADIUS, WEB_SHOT_MAX_TICKS, ParticleTypes.ITEM_SNOWBALL,
                (impactPos) -> onWebShotHit(level, target, impactPos));
    }

    private void onWebShotHit(ServerLevel level, LivingEntity target, Vec3 impactPos) {
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
                WEB_SLOWNESS_DURATION_TICKS, WEB_SLOWNESS_AMPLIFIER, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE,
                WEB_SLOWNESS_DURATION_TICKS, WEB_MINING_FATIGUE_AMPLIFIER, false, true, true));
        level.sendParticles(ParticleTypes.ITEM_SNOWBALL, target.getX(), target.getY() + 1.0, target.getZ(),
                20, 0.4, 0.5, 0.4, 0.02);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.BEEHIVE_ENTER, SoundSource.HOSTILE, 1.0F, 0.6F);
    }
}
