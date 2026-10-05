package com.solme.emberfall.entity;

import com.mojang.math.Transformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Boil-Ridden Marksman - Elite-track Skeleton (2026-09-28 elite pass), a mix
 * of a sniper (Charged Volley), a plague-thrower (Plague Glob) and a skirmisher
 * (Blink Step). Wears the "Plague Knight" minecraft-heads.com head (#112014) in
 * the ordinary head slot (Skeleton renders on a humanoid model, so no extra
 * entity is needed for that) plus a decorative off-hand bow carried by a single
 * ItemDisplay rider (the only extra entity this mob costs).
 *
 * Abilities, all on the vanilla ranged AI's own target selection:
 * <ul>
 *   <li>Charged Volley: telegraphed 1.5s wind-up, then a 5-arrow fan of real
 *       arrows. Real arrows because vanilla already handles arc/gravity/block
 *       collision for them and this fires at most once per cooldown.</li>
 *   <li>Plague Glob: a virtual bolt ({@link TrackedProjectiles}, no entity in
 *       flight) that leaves a real {@link AreaEffectCloud} poison puddle on
 *       impact - one real entity only while the hazard is actually active.</li>
 *   <li>Blink Step: when a player closes in, {@code LivingEntity#randomTeleport}
 *       (does its own ground-finding, collision and liquid check, and reverts
 *       on a bad spot) relocates it away from the target.</li>
 * </ul>
 * Arrow damage is an explicit field, not ATTACK_DAMAGE, for the same reason
 * documented in {@link HordeSkeleton}: vanilla bow arrows never read it.
 */
public class BoilRiddenMarksman extends Skeleton {
    private static final double BASE_HEALTH = 90.0;
    private static final double BASE_ARROW_DAMAGE = 6.0;
    private static final float SCALE = 1.2F;

    private static final long VOLLEY_COOLDOWN_TICKS = 260L;  // 13s
    private static final int VOLLEY_WINDUP_TICKS = 30;
    private static final double VOLLEY_RANGE = 24.0;
    private static final double VOLLEY_FAN_ANGLE_DEG = 12.0;
    private static final int VOLLEY_ARROWS = 5;

    private static final long GLOB_COOLDOWN_TICKS = 220L;    // 11s
    private static final double GLOB_RANGE = 18.0;
    private static final double GLOB_SPEED = 0.8;
    private static final double GLOB_HIT_RADIUS = 1.2;
    private static final int GLOB_MAX_TICKS = 45;
    private static final float CLOUD_RADIUS = 2.5F;
    private static final int CLOUD_DURATION_TICKS = 120;     // 6s
    private static final int CLOUD_POISON_TICKS = 60;

    private static final long BLINK_COOLDOWN_TICKS = 100L;   // 5s
    private static final double BLINK_TRIGGER_RANGE = 4.0;
    private static final double BLINK_DISTANCE = 9.0;
    private static final int BLINK_ATTEMPTS = 6;

    private double statMultiplier = 1.0;
    private double arrowDamage = BASE_ARROW_DAMAGE;

    private long nextVolleyAtTick = 0L;
    private long nextGlobAtTick = 0L;
    private long nextBlinkAtTick = 0L;
    private boolean winding = false;
    private long windupEndsAtTick = 0L;

    private Display.ItemDisplay offhandBowDisplay;

    public BoilRiddenMarksman(EntityType<? extends Skeleton> type, Level level) {
        super(type, level);
    }

    public static BoilRiddenMarksman spawn(ServerLevel level, BlockPos pos, double statMultiplier) {
        BoilRiddenMarksman m = new BoilRiddenMarksman(ModEntities.BOIL_RIDDEN_MARKSMAN, level);
        m.statMultiplier = statMultiplier;
        m.arrowDamage = BASE_ARROW_DAMAGE * statMultiplier;
        m.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        m.setYRot(level.getRandom().nextFloat() * 360.0F);

        AttributeInstance scale = m.getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.setBaseValue(SCALE);
        }
        AttributeInstance health = m.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            double hp = BASE_HEALTH * statMultiplier;
            health.setBaseValue(hp);
            m.setHealth((float) hp);
        }

        MobNames.apply(m, "Boil-Ridden Marksman", MobNames.Tier.VENOM);
        m.setPersistenceRequired();

        m.setItemSlot(EquipmentSlot.HEAD, EliteHeads.plagueKnightHead());
        m.setDropChance(EquipmentSlot.HEAD, 0.0F);
        m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        m.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        m.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
        m.setDropChance(EquipmentSlot.CHEST, 0.0F);
        m.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
        m.setDropChance(EquipmentSlot.LEGS, 0.0F);

        level.addFreshEntity(m);
        m.attachOffhandBow(level);
        level.sendParticles(ParticleTypes.SNEEZE, m.getX(), m.getY() + 1.5, m.getZ(), 20, 0.5, 0.8, 0.5, 0.02);
        level.playSound(null, m.getX(), m.getY(), m.getZ(),
                SoundEvents.SKELETON_AMBIENT, SoundSource.HOSTILE, 1.5F, 0.5F);
        return m;
    }

    /** Decorative second bow, one ItemDisplay rider. Purely cosmetic; cleaned up in {@link #remove}. */
    private void attachOffhandBow(ServerLevel level) {
        Display.ItemDisplay d = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
        d.setPos(this.getX(), this.getY(), this.getZ());
        d.setItemStack(new ItemStack(Items.BOW));
        d.setTransformation(new Transformation(
                new Vector3f(-0.55F, 0.9F, 0.0F),
                new Quaternionf().rotateZ((float) Math.toRadians(-20.0)),
                new Vector3f(0.9F, 0.9F, 0.9F), new Quaternionf()));
        d.setNoGravity(true);
        d.setInvulnerable(true);
        level.addFreshEntity(d);
        d.startRiding(this, true, false);
        this.offhandBowDisplay = d;
    }

    // No sun-burn override needed: Mob#burnUndead returns early whenever the head slot holds a
    // non-empty item, and this mob always wears the (non-damageable) Plague Knight head.

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
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!this.isAlive()) {
            return;
        }
        long now = level.getGameTime();
        LivingEntity target = this.getTarget();

        if (this.tickCount % 8 == 0) {
            level.sendParticles(ParticleTypes.ITEM_SLIME, this.getX(), this.getY() + 1.4, this.getZ(),
                    1, 0.3, 0.4, 0.3, 0.0);
        }

        if (winding) {
            tickWindup(level, now, target);
            return;
        }
        if (target == null || !target.isAlive()) {
            return;
        }
        double distSq = this.distanceToSqr(target);

        if (now >= nextBlinkAtTick && distSq <= BLINK_TRIGGER_RANGE * BLINK_TRIGGER_RANGE) {
            if (blinkAwayFrom(level, target)) {
                nextBlinkAtTick = now + BLINK_COOLDOWN_TICKS;
            } else {
                nextBlinkAtTick = now + 20L; // no safe spot found; retry soon rather than every tick
            }
            return;
        }
        if (now >= nextVolleyAtTick && distSq <= VOLLEY_RANGE * VOLLEY_RANGE && this.hasLineOfSight(target)) {
            winding = true;
            windupEndsAtTick = now + VOLLEY_WINDUP_TICKS;
            level.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.CROSSBOW_LOADING_START.value(), SoundSource.HOSTILE, 1.4F, 0.6F);
            return;
        }
        if (now >= nextGlobAtTick && distSq <= GLOB_RANGE * GLOB_RANGE && this.hasLineOfSight(target)) {
            nextGlobAtTick = now + GLOB_COOLDOWN_TICKS;
            throwGlob(level, target);
        }
    }

    private void tickWindup(ServerLevel level, long now, LivingEntity target) {
        if (target == null || !target.isAlive()) {
            winding = false;
            nextVolleyAtTick = now + 60L;
            return;
        }
        this.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (now % 3 == 0) {
            level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getEyeY(), this.getZ(), 4, 0.3, 0.3, 0.3, 0.05);
        }
        if (now >= windupEndsAtTick) {
            winding = false;
            nextVolleyAtTick = now + VOLLEY_COOLDOWN_TICKS;
            fireVolley(level, target);
        }
    }

    private void fireVolley(ServerLevel level, LivingEntity target) {
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE, 1.6F, 0.6F);
        ItemStack weapon = this.getItemInHand(ProjectileUtil.getWeaponHoldingHand(this, Items.BOW));
        ItemStack projectileStack = this.getProjectile(weapon);
        double baseDx = target.getX() - this.getX();
        double baseDy = target.getY(0.3333333333333333) - this.getEyeY();
        double baseDz = target.getZ() - this.getZ();
        int half = VOLLEY_ARROWS / 2;
        for (int i = -half; i <= half; i++) {
            double rad = Math.toRadians(i * VOLLEY_FAN_ANGLE_DEG);
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);
            double dx = baseDx * cos - baseDz * sin;
            double dz = baseDx * sin + baseDz * cos;
            double horizontalDist = Math.sqrt(dx * dx + dz * dz);
            AbstractArrow arrow = ProjectileUtil.getMobArrow(this, projectileStack, 1.0F, weapon);
            arrow.setBaseDamage(arrowDamage * 1.25);
            Projectile.spawnProjectileUsingShoot(arrow, level, projectileStack,
                    dx, baseDy + horizontalDist * 0.2, dz, 1.8F, 6.0F);
        }
    }

    private void throwGlob(ServerLevel level, LivingEntity target) {
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.3F, 0.7F);
        Vec3 origin = this.getEyePosition();
        Vec3 velocity = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0).subtract(origin)
                .normalize().scale(GLOB_SPEED);
        TrackedProjectiles.launchAtTarget(level, origin, velocity, this, target,
                GLOB_HIT_RADIUS, GLOB_MAX_TICKS, ParticleTypes.ITEM_SLIME,
                impact -> spawnPoisonCloud(level, target.position()));
    }

    private void spawnPoisonCloud(ServerLevel level, Vec3 at) {
        AreaEffectCloud cloud = new AreaEffectCloud(level, at.x, at.y, at.z);
        cloud.setOwner(this);
        cloud.setRadius(CLOUD_RADIUS);
        cloud.setDuration(CLOUD_DURATION_TICKS);
        cloud.setRadiusPerTick(-CLOUD_RADIUS / CLOUD_DURATION_TICKS);
        cloud.setCustomParticle(ParticleTypes.ITEM_SLIME);
        cloud.addEffect(new MobEffectInstance(MobEffects.POISON, CLOUD_POISON_TICKS, 0));
        level.addFreshEntity(cloud);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.5F, 0.4F);
    }

    /** @return true if a safe spot was found and the teleport succeeded. */
    private boolean blinkAwayFrom(ServerLevel level, LivingEntity target) {
        Vec3 away = this.position().subtract(target.position());
        Vec3 dir = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : new Vec3(away.x, 0, away.z).normalize();
        Vec3 from = this.position();
        for (int i = 0; i < BLINK_ATTEMPTS; i++) {
            double jitter = Math.toRadians((level.getRandom().nextDouble() - 0.5) * 100.0);
            double cos = Math.cos(jitter);
            double sin = Math.sin(jitter);
            double dx = dir.x * cos - dir.z * sin;
            double dz = dir.x * sin + dir.z * cos;
            double dist = BLINK_DISTANCE * (0.7 + level.getRandom().nextDouble() * 0.3);
            if (this.randomTeleport(from.x + dx * dist, from.y, from.z + dz * dist, false)) {
                level.sendParticles(ParticleTypes.PORTAL, from.x, from.y + 1.0, from.z, 25, 0.3, 0.6, 0.3, 0.3);
                level.sendParticles(ParticleTypes.PORTAL, this.getX(), this.getY() + 1.0, this.getZ(), 25, 0.3, 0.6, 0.3, 0.3);
                level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 1.4F);
                return true;
            }
        }
        return false;
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (offhandBowDisplay != null && !offhandBowDisplay.isRemoved()) {
            offhandBowDisplay.discard();
        }
        offhandBowDisplay = null;
        super.remove(reason);
    }
}
