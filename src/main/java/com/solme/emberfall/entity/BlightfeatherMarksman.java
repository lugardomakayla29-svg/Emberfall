package com.solme.emberfall.entity;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ported and refined from SlopPack's BlightfeatherMarksman (a Stray-based
 * Corrupted archetype: FAN/HOMING mode-switching sniper with Fan Volley,
 * Homing Volley, Spin Barrage, an emergency Smoke Bomb escape and a rare
 * Laser bonus shot). Base entity swapped Stray -> vanilla Skeleton (same
 * ranged-kiting AI family, no gameplay difference here since the vanilla
 * bow-shoot is disabled either way in favor of this ability layer).
 *
 * KEPT (verified against the SlopPack source before porting, per the
 * user's "recheck for bugs first" instruction - see class-level notes
 * below for the one real bug found and fixed):
 * - FAN/HOMING mode switching with a randomized window, nudged by target
 *   distance and low-HP urgency, each switch telegraphed distinctly.
 * - Fan Volley (3-arrow spread) and Homing Volley (single steering shot
 *   that detonates in an AoE burst) - both KEPT feature-for-feature.
 * - Spin Barrage: a self-spin that fires arrows in an expanding ring,
 *   usable regardless of current mode.
 * - Smoke Bomb: emergency escape below 45% HP - invisibility + speed,
 *   a lingering AoE zone, and a blink to behind the target.
 * - Laser: rare Guardian-beam bonus shot, FAN-mode only.
 *
 * FIXED (real bug, found by inspection, not guessed): SlopPack's
 * executeLaser swept the beam in 0.5-block steps with a 0.9-block hit
 * radius at each step and damaged every {@code Player} found with no
 * per-cast dedupe - a stationary target sitting in the beam could be hit
 * by several overlapping steps in the same cast, taking multiples of the
 * intended 9 damage. This version tracks already-hit targets in a
 * per-cast set so each target takes the laser's damage exactly once.
 *
 * REFINED (not a straight port): Fan/Homing Volley bolts are now fully
 * virtual (see {@link TrackedProjectiles}) instead of real vanilla Arrow
 * entities driven by a second manual proximity check running alongside
 * vanilla's own arrow-vs-entity collision - that combination in SlopPack
 * was a latent race (both the manual check and real collision could fire
 * in the same tick, double-hitting the target). Spin Barrage's arrows
 * have no such secondary check in the original, so those stay as real
 * Arrow entities here - no bug there, no reason to change it.
 */
public class BlightfeatherMarksman extends Skeleton {
    private static final double BASE_HEALTH = 80.0;
    /** Larger than fodder, smaller than the Sentinel (3.0). A humanoid above 1.0 no longer fits a 2 high door; the arena is open ground. */
    private static final float ELITE_SCALE = 1.25F;

    private static final int FAN_VOLLEY_COOLDOWN_TICKS = 90;     // 4.5s
    private static final int HOMING_VOLLEY_COOLDOWN_TICKS = 90;  // 4.5s
    private static final int SPIN_BARRAGE_COOLDOWN_TICKS = 420;  // 21s
    /** Laser reach and half-width. The beam damage and its warning lane both read these. */
    private static final double LASER_RANGE = 22.0;
    private static final double LASER_HALF_WIDTH = 0.9;
    private static final int LASER_TELEGRAPH_TICKS = 18;
    private static final int LASER_COOLDOWN_TICKS = 400;         // 20s
    private static final int SMOKE_BOMB_COOLDOWN_TICKS = 640;    // 32s
    private static final double SMOKE_BOMB_HP_FRACTION = 0.45;
    private static final double ABILITY_RANGE = 26.0;

    private double statMultiplier = 1.0;

    private enum Mode { FAN, HOMING }

    private enum AbilityState { IDLE, TELEGRAPH_MODE_SWITCH, TELEGRAPH_FAN, TELEGRAPH_HOMING, TELEGRAPH_SPIN, TELEGRAPH_LASER, TELEGRAPH_SMOKE }

    private Mode mode = null;
    private long modeSwitchAtTick = 0L;
    private AbilityState state = AbilityState.IDLE;
    private long telegraphEndsAtTick = 0L;
    private Mode pendingMode = null;

    private long nextFanVolleyAtTick = 0L;
    private long nextHomingVolleyAtTick = 0L;
    private long nextSpinBarrageAtTick = 0L;
    private long nextLaserAtTick = 0L;
    private long nextSmokeBombAtTick = 0L;

    public BlightfeatherMarksman(EntityType<? extends Skeleton> type, Level level) {
        super(type, level);
    }

    /** Vanilla bow-shoot AI is disabled - all ranged damage comes from this class's ability layer instead. */
    @Override
    public void performRangedAttack(LivingEntity target, float distanceFactor) {
        // Intentionally no-op. SlopPack achieved the same result by cancelling
        // EntityShootBowEvent for tagged mobs; here we just don't let the
        // vanilla RangedBowAttackGoal fire a real arrow at all. The goal still
        // drives the kiting/retreat behavior we want to keep, it just never
        // gets to actually shoot.
    }

    public static BlightfeatherMarksman spawn(ServerLevel level, BlockPos pos, double statMultiplier) {
        BlightfeatherMarksman marksman = new BlightfeatherMarksman(ModEntities.BLIGHTFEATHER_MARKSMAN, level);
        marksman.statMultiplier = statMultiplier;
        marksman.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        marksman.setYRot(level.getRandom().nextFloat() * 360.0F);
        AttributeInstance eliteScale = marksman.getAttribute(Attributes.SCALE);
        if (eliteScale != null) {
            eliteScale.setBaseValue(ELITE_SCALE);
        }

        AttributeInstance health = marksman.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            double hp = BASE_HEALTH * statMultiplier;
            health.setBaseValue(hp);
            marksman.setHealth((float) hp);
        }

        MobNames.apply(marksman, "Blightfeather Marksman", MobNames.Tier.VENOM);
        marksman.setPersistenceRequired();

        ItemStack bow = new ItemStack(Items.BOW);
        bow.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Golden Shortbow"));
        bow.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        marksman.setItemSlot(EquipmentSlot.MAINHAND, bow);
        marksman.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        marksman.setItemSlot(EquipmentSlot.HEAD, EliteHeads.skeletonSniperHead());
        marksman.setDropChance(EquipmentSlot.HEAD, 0.0F);

        level.addFreshEntity(marksman);
        level.sendParticles(ParticleTypes.WITCH, marksman.getX(), marksman.getY() + 1.0, marksman.getZ(),
                20, 0.4, 0.6, 0.4, 0.02);
        level.playSound(null, marksman.getX(), marksman.getY(), marksman.getZ(),
                SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE, 1.0F, 0.6F);
        return marksman;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!this.isAlive()) {
            return;
        }
        long now = this.level().getGameTime();
        spawnAmbientParticles(level);

        if (state != AbilityState.IDLE) {
            if (now >= telegraphEndsAtTick) {
                resolveTelegraph(level);
            } else {
                if (now % 4 == 0) {
                    drawTelegraph(level, now);
                }
                return; // frozen mid-telegraph
            }
        }

        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        double distance = this.position().distanceTo(target.position());
        if (distance > ABILITY_RANGE) {
            return;
        }
        double healthPct = this.getHealth() / this.getMaxHealth();

        if (healthPct < SMOKE_BOMB_HP_FRACTION && now >= nextSmokeBombAtTick) {
            nextSmokeBombAtTick = now + SMOKE_BOMB_COOLDOWN_TICKS;
            beginTelegraph(AbilityState.TELEGRAPH_SMOKE, 6, target);
            return;
        }
        if (now >= nextSpinBarrageAtTick) {
            nextSpinBarrageAtTick = now + SPIN_BARRAGE_COOLDOWN_TICKS;
            beginTelegraph(AbilityState.TELEGRAPH_SPIN, 8, target);
            return;
        }

        if (mode == null || now >= modeSwitchAtTick) {
            Mode newMode = mode == null
                    ? (this.getRandom().nextBoolean() ? Mode.FAN : Mode.HOMING)
                    : (mode == Mode.FAN ? Mode.HOMING : Mode.FAN);
            long window = 200L + this.getRandom().nextInt(160); // 10-18s
            if (newMode == Mode.HOMING && distance > 15.0) {
                window = (long) (window * 1.3);
            } else if (newMode == Mode.FAN && distance < 8.0) {
                window = (long) (window * 1.3);
            }
            if (healthPct < 0.4) {
                window = (long) (window * 0.6);
            }
            pendingMode = newMode;
            modeSwitchAtTick = now + window;
            beginTelegraph(AbilityState.TELEGRAPH_MODE_SWITCH, 16, target);
            return;
        }

        if (mode == Mode.FAN) {
            if (now >= nextFanVolleyAtTick) {
                boolean laserReady = now >= nextLaserAtTick;
                if (laserReady && this.getRandom().nextDouble() < 0.25) {
                    nextLaserAtTick = now + LASER_COOLDOWN_TICKS;
                    nextFanVolleyAtTick = now + FAN_VOLLEY_COOLDOWN_TICKS;
                    beginTelegraph(AbilityState.TELEGRAPH_LASER, LASER_TELEGRAPH_TICKS, target);
                } else {
                    nextFanVolleyAtTick = now + FAN_VOLLEY_COOLDOWN_TICKS;
                    beginTelegraph(AbilityState.TELEGRAPH_FAN, 8, target);
                }
            }
        } else {
            if (now >= nextHomingVolleyAtTick) {
                nextHomingVolleyAtTick = now + HOMING_VOLLEY_COOLDOWN_TICKS;
                beginTelegraph(AbilityState.TELEGRAPH_HOMING, 8, target);
            }
        }
    }

    /**
     * Shows what is about to happen. The Laser is a hitscan beam, so it gets a lane from the eye toward the
     * target at the beam's true range and width, tracking the target during the wind-up exactly as the shot
     * will (it is aimed at fire time). The Fan and Homing volleys are tracked projectiles with no lane to
     * dodge, so they mark the target instead ("you are being shot at").
     */
    private void drawTelegraph(ServerLevel level, long now) {
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        switch (state) {
            case TELEGRAPH_LASER -> {
                Vec3 eye = this.getEyePosition();
                Vec3 to = target.getEyePosition().subtract(eye);
                if (to.lengthSqr() > 1.0E-4) {
                    Vec3 end = eye.add(to.normalize().scale(LASER_RANGE));
                    com.solme.emberfall.combat.Fx.telegraphLine(level, this.position(), end,
                            LASER_HALF_WIDTH, com.solme.emberfall.combat.Fx.WARN_RED);
                }
            }
            case TELEGRAPH_FAN, TELEGRAPH_HOMING -> com.solme.emberfall.combat.Fx.telegraphTarget(
                    level, target, com.solme.emberfall.combat.Fx.WARN_ORANGE);
            default -> {}
        }
    }

    private void beginTelegraph(AbilityState next, int durationTicks, LivingEntity target) {
        state = next;
        telegraphEndsAtTick = this.level().getGameTime() + durationTicks;
        ServerLevel level = (ServerLevel) this.level();
        switch (next) {
            case TELEGRAPH_MODE_SWITCH -> {
                boolean homing = pendingMode == Mode.HOMING;
                level.playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 1.2F, homing ? 0.6F : 0.8F);
                level.sendParticles(homing ? ParticleTypes.PORTAL : ParticleTypes.CRIT,
                        this.getX(), this.getY() + 1.0, this.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
            }
            case TELEGRAPH_FAN -> level.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.STRAY_AMBIENT, SoundSource.HOSTILE, 1.0F, 1.5F);
            case TELEGRAPH_HOMING -> level.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 0.8F, 1.5F);
            case TELEGRAPH_SPIN -> {
                level.playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.HOSTILE, 1.2F, 0.6F);
                this.getNavigation().stop();
            }
            case TELEGRAPH_LASER -> level.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.GUARDIAN_AMBIENT, SoundSource.HOSTILE, 1.4F, 0.6F);
            case TELEGRAPH_SMOKE -> level.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.PHANTOM_FLAP, SoundSource.HOSTILE, 1.0F, 0.6F);
            default -> {}
        }
    }

    private void resolveTelegraph(ServerLevel level) {
        AbilityState resolving = state;
        state = AbilityState.IDLE;
        LivingEntity target = this.getTarget();
        switch (resolving) {
            case TELEGRAPH_MODE_SWITCH -> {
                mode = pendingMode;
                pendingMode = null;
                EmberfallMod.LOGGER.info("Blightfeather Marksman[id={}] switched to {} mode", this.getId(), mode);
            }
            case TELEGRAPH_FAN -> { if (target != null) { EmberfallMod.LOGGER.info("Blightfeather Marksman fired Fan Volley"); executeFanVolley(level, target); } }
            case TELEGRAPH_HOMING -> { if (target != null) { EmberfallMod.LOGGER.info("Blightfeather Marksman fired Homing Volley"); executeHomingVolley(level, target); } }
            case TELEGRAPH_SPIN -> { EmberfallMod.LOGGER.info("Blightfeather Marksman used Spin Barrage"); executeSpinBarrage(level); }
            case TELEGRAPH_LASER -> { if (target != null) { EmberfallMod.LOGGER.info("Blightfeather Marksman fired Laser"); executeLaser(level, target); } }
            case TELEGRAPH_SMOKE -> { EmberfallMod.LOGGER.info("Blightfeather Marksman used Smoke Bomb at {}% HP", (int) (100 * this.getHealth() / this.getMaxHealth())); executeSmokeBomb(level, target); }
            default -> {}
        }
    }

    private void spawnAmbientParticles(ServerLevel level) {
        level.sendParticles(ParticleTypes.WITCH, this.getX(), this.getY() + 1.0, this.getZ(),
                1, 0.4, 0.6, 0.4, 0.01);
    }

    private void executeFanVolley(ServerLevel level, LivingEntity target) {
        Vec3 eye = this.getEyePosition();
        Vec3 toTarget = target.getEyePosition().subtract(eye);
        double horiz = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
        float pitch = (float) Math.toDegrees(Math.atan2(-toTarget.y, horiz));
        level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE, 1.0F, 0.7F);

        for (int n : new int[]{-1, 0, 1}) {
            float shotYaw = yaw + n * 9.0F;
            Vec3 dir = directionFromYawPitch(shotYaw, pitch).scale(1.8);
            TrackedProjectiles.launchAtTarget(level, eye, dir, this, target, 1.2, 50,
                    ParticleTypes.HAPPY_VILLAGER, impactPos -> {
                        if (!target.isAlive()) {
                            return;
                        }
                        boolean glowing = target.hasEffect(MobEffects.GLOWING);
                        double dmg = 3.5 * (glowing ? 1.2 : 1.0) * statMultiplier;
                        DamageSource source = this.damageSources().mobAttack(this);
                        target.hurtServer(level, source, (float) dmg);
                        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 160, 0));
                        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, impactPos.x, impactPos.y, impactPos.z,
                                10, 0.3, 0.3, 0.3, 0.0);
                        level.playSound(null, impactPos.x, impactPos.y, impactPos.z,
                                SoundEvents.ARROW_HIT_PLAYER, SoundSource.HOSTILE, 1.0F, 1.0F);
                    });
        }
    }

    private void executeHomingVolley(ServerLevel level, LivingEntity target) {
        Vec3 eye = this.getEyePosition();
        Vec3 dir = target.getEyePosition().subtract(eye).normalize().scale(1.2);
        level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE, 1.0F, 1.2F);
        level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 0.8F, 1.5F);

        TrackedProjectiles.launchHoming(level, eye, dir, this, target, 0.18, 1.2, 60,
                new DustParticleOptions(0xFFD700, 1.0F),
                impactPos -> detonateHomingBurst(level, impactPos),
                () -> detonateHomingBurst(level, this.position().add(0, 1, 0)));
    }

    private void detonateHomingBurst(ServerLevel level, Vec3 loc) {
        level.playSound(null, loc.x, loc.y, loc.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.1F, 1.6F);
        level.sendParticles(new DustParticleOptions(0xFFD700, 1.3F), loc.x, loc.y, loc.z, 40, 1.4, 1.0, 1.4, 0.0);
        level.sendParticles(ParticleTypes.EXPLOSION, loc.x, loc.y, loc.z, 4, 0.8, 0.5, 0.8, 0.02);

        double radius = 3.2;
        AABB box = AABB.ofSize(loc, radius * 2, radius * 2, radius * 2);
        List<Player> hit = level.getEntitiesOfClass(Player.class, box,
                p -> p.isAlive() && !p.isSpectator() && p.position().distanceTo(loc) <= radius);
        DamageSource source = this.damageSources().mobAttack(this);
        for (Player player : hit) {
            boolean glowing = player.hasEffect(MobEffects.GLOWING);
            double dist = player.position().distanceTo(loc);
            double falloff = 1.0 - dist / radius;
            double dmg = Math.max(2.5, 6.5 * falloff) * (glowing ? 1.25 : 1.0) * statMultiplier;
            player.hurtServer(level, source, (float) dmg);
            player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 160, 0));
            Vec3 push = player.position().subtract(loc);
            if (push.lengthSqr() > 0.001) {
                Vec3 n = push.normalize().scale(0.7 * falloff);
                player.setDeltaMovement(player.getDeltaMovement().add(n.x, 0.25, n.z));
            }
        }
    }

    private void executeSpinBarrage(ServerLevel level) {
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.WITCH_CELEBRATE, SoundSource.HOSTILE, 0.8F, 0.4F);
        SpinBarrageRunner.start(this, level, statMultiplier);
    }

    private void executeLaser(ServerLevel level, LivingEntity target) {
        Vec3 eye = this.getEyePosition();
        Vec3 dir = target.isAlive() ? target.getEyePosition().subtract(eye).normalize() : this.getLookAngle();
        level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.GUARDIAN_ATTACK, SoundSource.HOSTILE, 1.6F, 0.7F);

        // FIX vs SlopPack: dedupe hits per cast so overlapping beam-step radii
        // can't damage the same target more than once.
        Set<Player> alreadyHit = new HashSet<>();
        double maxDist = LASER_RANGE;
        DamageSource source = this.damageSources().mobAttack(this);
        for (double d = 0.0; d <= maxDist; d += 0.5) {
            Vec3 point = eye.add(dir.scale(d));
            level.sendParticles(new DustParticleOptions(0xB428C8, 1.4F), point.x, point.y, point.z, 3, 0.06, 0.06, 0.06, 0.0);
            AABB box = AABB.ofSize(point, LASER_HALF_WIDTH * 2, LASER_HALF_WIDTH * 2, LASER_HALF_WIDTH * 2);
            for (Player player : level.getEntitiesOfClass(Player.class, box, Player::isAlive)) {
                if (alreadyHit.add(player)) {
                    player.hurtServer(level, source, (float) (9.0 * statMultiplier));
                    player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0));
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.ARROW_HIT_PLAYER, SoundSource.HOSTILE, 1.0F, 0.9F);
                }
            }
        }
    }

    private void executeSmokeBomb(ServerLevel level, LivingEntity target) {
        Vec3 origin = this.position();
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.3F, 0.7F);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, origin.x, origin.y + 1.0, origin.z, 80, 0.8, 1.0, 0.8, 0.05);

        SmokeCloud.spawn(level, BlockPos.containing(origin), 100, 2.75,
                (float) (2.5 * statMultiplier));

        this.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 70, 0));
        this.addEffect(new MobEffectInstance(MobEffects.SPEED, 70, 1));
        this.setTarget(null);

        if (target != null && target.isAlive()) {
            Vec3 back = target.getLookAngle().normalize().scale(-6.0);
            Vec3 behindTarget = target.position().add(back);
            this.teleportTo(behindTarget.x, behindTarget.y, behindTarget.z);
            level.sendParticles(ParticleTypes.SMOKE, behindTarget.x, behindTarget.y, behindTarget.z, 15, 0.3, 0.5, 0.3, 0.02);
        }
    }

    private static Vec3 directionFromYawPitch(float yaw, float pitch) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(pitchRad);
        double z = Math.cos(yawRad) * Math.cos(pitchRad);
        return new Vec3(x, y, z);
    }

    /** Spin Barrage's own real-Arrow shots (no manual dedupe check needed - see class javadoc). */
    static void spawnSpinArrow(LivingEntity self, ServerLevel level, Vec3 loc, Vec3 dir) {
        // Vanilla's AbstractArrow constructor throws "Invalid weapon firing an
        // arrow" on a ServerLevel if the weapon stack is empty - pass the
        // marksman's actual bow rather than ItemStack.EMPTY.
        ItemStack weapon = self.getMainHandItem();
        // The pickup stack must also be a real item: an arrow that lands in the ground keeps this stack, and
        // a chunk holding one fails to save ("0 minecraft:air ... Item must not be minecraft:air") if it is
        // empty. Pickup is DISALLOWED below, so it can never actually be collected.
        Arrow arrow = new Arrow(level, self, new ItemStack(Items.ARROW), weapon.isEmpty() ? new ItemStack(Items.BOW) : weapon);
        arrow.setPos(loc.x, loc.y, loc.z);
        arrow.shoot(dir.x, dir.y, dir.z, 1.6F, 0.0F);
        arrow.setBaseDamage(2.5);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        level.addFreshEntity(arrow);
    }
}
