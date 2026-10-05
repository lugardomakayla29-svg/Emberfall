package com.solme.emberfall.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Second horde-filler enemy (design doc 2.4 - "expand horde/elite variety"
 * per the user's 2026-09-27 direction, using SlopPack's {@code EliteMobListener}
 * as reference, not a straight port): a ranged Skeleton-based filler, giving
 * the horde wave pool its first non-melee threat outside the 5 Corrupted
 * elites. Sibling to {@link HordeZombie} - same "cheap by default, one extra
 * reactive ability at Veteran tier" philosophy, not a Section 6 composite rig.
 *
 * ENGINE FACT that shaped this class's design (confirmed via decompiled
 * {@code AbstractSkeleton#performRangedAttack} / {@code ProjectileUtil#getMobArrow}
 * / {@code AbstractArrow#setBaseDamageFromMob}, not guessed): a vanilla
 * Skeleton's shot-arrow damage comes from {@code setBaseDamageFromMob(power)},
 * a formula based purely on the bow-draw power float and world difficulty -
 * it never reads the shooter's {@code Attributes.ATTACK_DAMAGE} at all.
 * {@link HordeZombie#becomeVeteran}'s generic "1.6x ATTACK_DAMAGE" Veteran
 * buff would therefore be a silent no-op here. This class instead overrides
 * {@link #performRangedAttack} entirely (same override point BlightfeatherMarksman
 * already uses, though that one no-ops it in favor of a separate ability layer)
 * and calls {@code AbstractArrow#setBaseDamage} explicitly with our own tracked
 * {@code arrowDamage} field, so the Veteran multiplier has something real to
 * scale. Everything else about the shot (arrow selection, spawn velocity/arc,
 * sound) mirrors {@code AbstractSkeleton}'s own vanilla implementation exactly -
 * only the final damage value is substituted.
 *
 * Veteran tier's extra ability, Volley (ported/adapted from SlopPack's
 * elite-Skeleton "arrow volley", tier-1 version: a 3-arrow fan, not the
 * 5-arrow tier-2 spread - filler-tier stays cheaper than the elite version):
 * a 3-arrow horizontal fan (-14/0/+14 degrees) on its own cooldown, gated by
 * a target + range check in {@link #customServerAiStep}, independent of the
 * single-target auto-shots {@code performRangedAttack} keeps producing on
 * vanilla's own {@code RangedBowAttackGoal} cadence.
 *
 * Verified live (2026-09-27) via temporary debug logging against a real
 * spawned instance (using the new {@code /emberfall spawnveteran horde_skeleton}
 * testing hook): confirmed Volley fires exactly on its 9s cooldown once a
 * target is in range, with {@code arrowDamage} correctly scaled to 6.4
 * (4.0 base x 1.6 Veteran multiplier). One real testing gotcha hit along
 * the way, not a code bug: a Creative-mode test player is never a valid
 * vanilla AI target ({@code NearestAttackableTargetGoal} excludes
 * Creative/Spectator players), so {@code getTarget()} stayed null against
 * a Creative bot regardless of proximity - had to switch the test bot to
 * Survival (+ Resistance/Regeneration to survive being shot) to get a
 * real target. Debug logging removed after verification.
 */
public class HordeSkeleton extends Skeleton {
    private static final double VETERAN_HEALTH_MULT = 2.5;
    private static final double VETERAN_ARROW_DAMAGE_MULT = 1.6; // see class javadoc - applied to our own explicit damage field, not the (irrelevant) ATTACK_DAMAGE attribute
    private static final double BASE_ARROW_DAMAGE = 4.0;
    private static final int VOLLEY_COOLDOWN_TICKS = 180; // 9s, matches SlopPack's tier-1 skeleton-elite cadence
    private static final double VOLLEY_RANGE = 20.0;
    private static final double VOLLEY_RANGE_SQ = VOLLEY_RANGE * VOLLEY_RANGE;
    private static final double VOLLEY_FAN_ANGLE_DEG = 14.0;

    /**
     * Base-mob signature move (every horde skeleton, not only veterans): a backstep. A player inside 4 blocks makes it
     * hop backward about 5 blocks, then it answers with a second arrow 4 ticks after its next shot. Launch speed and
     * flight follow the same drag rule as the zombie lunge (total flight = 8.65 x launch speed).
     */
    private static final int BACKSTEP_COOLDOWN_TICKS = 140;   // 7 s
    private static final double BACKSTEP_TRIGGER_RANGE = 4.0;
    private static final double BACKSTEP_DISTANCE = 5.0;
    private static final double BACKSTEP_FLIGHT_FACTOR = 8.65;
    private static final double BACKSTEP_VERTICAL = 0.36;
    private static final int BACKSTEP_FLIGHT_TICKS = 12;
    private static final int ECHO_SHOT_DELAY_TICKS = 4;
    private static final boolean TEST_MODE = Boolean.getBoolean("emberfall.testMode");

    private boolean veteran = false;
    private double arrowDamage = BASE_ARROW_DAMAGE;
    private long nextVolleyAtTick = 0L;
    private long nextBackstepAtTick = 0L;
    private long steerLockUntilTick = -1L;   // while now < this, the AI must not steer: its walk input would cancel the hop
    private long echoShotAtTick = -1L;       // tick at which the scheduled second arrow fires, or -1
    private boolean echoArmed = false;

    public HordeSkeleton(EntityType<? extends Skeleton> type, Level level) {
        super(type, level);
    }

    /** Called right after construction, before {@code addFreshEntity} - see {@link HordeZombie#becomeVeteran}'s javadoc on why. */
    public void becomeVeteran() {
        this.veteran = true;

        AttributeInstance health = this.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(health.getBaseValue() * VETERAN_HEALTH_MULT);
            this.setHealth((float) health.getValue());
        }
        this.arrowDamage = BASE_ARROW_DAMAGE * VETERAN_ARROW_DAMAGE_MULT;

        MobNames.apply(this, "Veteran Horde Skeleton", MobNames.Tier.VETERAN);

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

    /** Sets up base equipment - called once right after construction, same spot every horde-filler spawner uses. */
    public void equipBow() {
        ItemStack bow = new ItemStack(Items.BOW);
        this.setItemSlot(EquipmentSlot.MAINHAND, bow);
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    /**
     * Vanilla's own single-shot logic ({@code AbstractSkeleton#performRangedAttack}),
     * reproduced exactly except the final arrow's damage is set explicitly from
     * {@link #arrowDamage} instead of the vanilla power-based formula - see class javadoc.
     */
    @Override
    public void performRangedAttack(LivingEntity target, float distanceFactor) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        ItemStack weapon = this.getItemInHand(ProjectileUtil.getWeaponHoldingHand(this, Items.BOW));
        ItemStack projectileStack = this.getProjectile(weapon);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, projectileStack, distanceFactor, weapon);
        arrow.setBaseDamage(arrowDamage);

        double dx = target.getX() - this.getX();
        double dy = target.getY(0.3333333333333333) - arrow.getY();
        double dz = target.getZ() - this.getZ();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        Projectile.spawnProjectileUsingShoot(arrow, serverLevel, projectileStack,
                dx, dy + horizontalDist * 0.2, dz, 1.6F, 14 - serverLevel.getDifficulty().getId() * 4);

        this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
        armEchoIfReady(serverLevel);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!this.isAlive()) {
            return;
        }
        tickBackstep(level);
        if (!veteran) {
            return;
        }
        long now = this.level().getGameTime();
        if (now < nextVolleyAtTick) {
            return;
        }
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive() || this.position().distanceToSqr(target.position()) > VOLLEY_RANGE_SQ) {
            return;
        }
        nextVolleyAtTick = now + VOLLEY_COOLDOWN_TICKS;
        fireVolley(level, target);
    }

    /** Veteran-only extra ability - see class javadoc. */
    private void fireVolley(ServerLevel level, LivingEntity target) {
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE, 1.3F, 0.7F);
        level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getEyeY(), this.getZ(),
                12, 0.2, 0.2, 0.2, 0.05);
        ItemStack weapon = this.getItemInHand(ProjectileUtil.getWeaponHoldingHand(this, Items.BOW));
        ItemStack projectileStack = this.getProjectile(weapon);

        double baseDx = target.getX() - this.getX();
        double baseDy = target.getY(0.3333333333333333) - this.getEyeY();
        double baseDz = target.getZ() - this.getZ();

        double[] fanAnglesDeg = { -VOLLEY_FAN_ANGLE_DEG, 0.0, VOLLEY_FAN_ANGLE_DEG };
        for (double angleDeg : fanAnglesDeg) {
            double angleRad = Math.toRadians(angleDeg);
            double cos = Math.cos(angleRad);
            double sin = Math.sin(angleRad);
            // Rotate only the horizontal (X/Z) component around Y - the vertical arc lob stays as vanilla computes it per-arrow below.
            double rotatedDx = baseDx * cos - baseDz * sin;
            double rotatedDz = baseDx * sin + baseDz * cos;
            double horizontalDist = Math.sqrt(rotatedDx * rotatedDx + rotatedDz * rotatedDz);

            AbstractArrow arrow = ProjectileUtil.getMobArrow(this, projectileStack, 1.0F, weapon);
            arrow.setBaseDamage(arrowDamage);
            Projectile.spawnProjectileUsingShoot(arrow, level, projectileStack,
                    rotatedDx, baseDy + horizontalDist * 0.2, rotatedDz, 1.6F, 14 - level.getDifficulty().getId() * 4);
        }
    }

    /** Base-mob signature move, see the constants above. One cheap check every 5 ticks while a target exists. */
    private void tickBackstep(ServerLevel level) {
        long now = level.getGameTime();
        if (echoShotAtTick >= 0L && now >= echoShotAtTick) {
            echoShotAtTick = -1L;
            LivingEntity target = this.getTarget();
            if (target != null && target.isAlive()) {
                fireEchoArrow(level, target);
            }
        }
        if (now < steerLockUntilTick) {
            this.getNavigation().stop();
            this.setZza(0.0F);
            this.setXxa(0.0F);
            return;
        }
        if (now < nextBackstepAtTick || now % 5L != 0L || !this.onGround()) {
            return;
        }
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        if (this.position().distanceToSqr(target.position()) > BACKSTEP_TRIGGER_RANGE * BACKSTEP_TRIGGER_RANGE) {
            return;
        }
        Vec3 away = new Vec3(this.getX() - target.getX(), 0.0, this.getZ() - target.getZ());
        if (away.lengthSqr() < 1.0E-4) {
            return;
        }
        Vec3 dir = away.normalize();
        // Do not hop into a wall: need open floor-level space for most of the distance.
        Vec3 probe = this.position().add(dir.scale(BACKSTEP_DISTANCE * 0.6));
        if (!level.noCollision(this, this.getBoundingBox().move(probe.subtract(this.position())))) {
            return;
        }
        nextBackstepAtTick = now + BACKSTEP_COOLDOWN_TICKS;
        steerLockUntilTick = now + BACKSTEP_FLIGHT_TICKS;
        echoArmed = true;
        if (TEST_MODE) { com.solme.emberfall.EmberfallMod.LOGGER.info("SKELETON_TEST backstep tick={}", now); }
        double speed = BACKSTEP_DISTANCE / BACKSTEP_FLIGHT_FACTOR;
        this.setOnGround(false);
        this.setPos(this.getX(), this.getY() + 0.02, this.getZ());
        this.setDeltaMovement(dir.x * speed, BACKSTEP_VERTICAL, dir.z * speed);
        this.hurtMarked = true;
        level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 0.1, this.getZ(), 6, 0.2, 0.02, 0.2, 0.01);
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.SKELETON_STEP, SoundSource.HOSTILE, 1.0F, 1.6F);
    }

    /** Called from performRangedAttack: the first arrow after a backstep is followed by a second. */
    private void armEchoIfReady(ServerLevel level) {
        if (echoArmed) {
            echoArmed = false;
            echoShotAtTick = level.getGameTime() + ECHO_SHOT_DELAY_TICKS;
            if (TEST_MODE) { com.solme.emberfall.EmberfallMod.LOGGER.info("SKELETON_TEST shot-armed tick={}", level.getGameTime()); }
        }
    }

    private void fireEchoArrow(ServerLevel level, LivingEntity target) {
        ItemStack weapon = this.getItemInHand(ProjectileUtil.getWeaponHoldingHand(this, Items.BOW));
        ItemStack projectileStack = this.getProjectile(weapon);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, projectileStack, 1.0F, weapon);
        arrow.setBaseDamage(arrowDamage);
        double dx = target.getX() - this.getX();
        double dy = target.getY(0.3333333333333333) - arrow.getY();
        double dz = target.getZ() - this.getZ();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        Projectile.spawnProjectileUsingShoot(arrow, level, projectileStack,
                dx, dy + horizontalDist * 0.2, dz, 1.6F, 14 - level.getDifficulty().getId() * 4);
        this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
        if (TEST_MODE) { com.solme.emberfall.EmberfallMod.LOGGER.info("SKELETON_TEST echo tick={}", level.getGameTime()); }
    }
}
