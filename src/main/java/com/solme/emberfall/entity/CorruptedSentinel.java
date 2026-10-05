package com.solme.emberfall.entity;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Ported and refined from SlopPack's CorruptedSentinel (a Zombie-based,
 * 3x-scale tank boss: Fortify defensive buff, Ground Stomp leap-slam AoE,
 * Earth-Shattering Roar cone knockback, plus an anti-stall "stuck" leap
 * failsafe). Per the user's "nothing should be a plain reskin" ask, see
 * KEPT vs NEW/REFINED below.
 *
 * KEPT (ported, tuned for Emberfall's statMultiplier scaling instead of
 * SlopPack's discrete dungeon tier + realm-flavor systems):
 * - Fortify: reactive defensive buff below 50% HP (Resistance + Slowness).
 * - Ground Stomp: telegraphed leap, AI disabled mid-air, then a landing
 *   slam with distance-falloff AoE damage/knockback and a ground-ripple
 *   visual (nearby floor blocks briefly kick up as decorative displays).
 * - Earth-Shattering Roar: telegraphed cone-shaped knockback + damage +
 *   Slowness/Weakness, the Sentinel's answer to being kited at range.
 * - Stuck/escape failsafe: if pathing stalls (hasn't moved in ~4 checks),
 *   forces a Ground Stomp leap in place to unstick itself, same as
 *   SlopPack's version - a real anti-stall safety net, not cosmetic.
 *
 * NEW / REFINED (replacing SlopPack's dungeon-tier/RealmMobFlavor branch,
 * which this project has no equivalent system for):
 * - Fortify now gets a short telegraph (wind-up sound + brief delay)
 *   instead of firing instantly - SlopPack's version was a free instant
 *   panic button with zero counterplay window; every other Emberfall
 *   elite ability telegraphs, so this brings Fortify in line.
 * - The old "isAwakened tier" and "RAGNAROK_RIFT realm" bonus branches on
 *   Ground Stomp (which needed systems Emberfall doesn't have) are
 *   replaced with one clean, always-available scaling hook: at high
 *   statMultiplier the landing slam also drops a {@link ScorchedGround}
 *   fire patch, so the "harder fight = extra hazard" idea survives
 *   without SlopPack's tier/realm plumbing.
 *
 * Base entity is vanilla Zombie (real pathfinding + melee AI, already
 * hostile-by-default so no custom targeting goal is needed - unlike
 * Witch-based Umbral Magus) - only stats, equipment, scale, and this
 * bolt-on ability layer are custom.
 */
public class CorruptedSentinel extends Zombie {
    private static final double BASE_HEALTH = 150.0;
    private static final double BASE_ATTACK_DAMAGE = 8.0;
    private static final float SCALE = 3.0F;
    /** Mirrors UmbralMagus's AWAKENED_STAT_THRESHOLD pattern for a scaling-based bonus hazard. */
    private static final double AWAKENED_STAT_THRESHOLD = 1.3;

    private static final int STOMP_TELEGRAPH_TICKS = 10;
    private static final int ROAR_TELEGRAPH_TICKS = 24;
    private static final int FORTIFY_TELEGRAPH_TICKS = 8;
    private static final int LEAP_TIMEOUT_TICKS = 40;

    private static final long FORTIFY_COOLDOWN_TICKS = 500L;  // 25s
    private static final long STOMP_COOLDOWN_TICKS = 240L;    // 12s
    private static final long ROAR_COOLDOWN_TICKS = 500L;     // 25s
    private static final double STOMP_RANGE = 10.0;
    /** Damage radius of the Ground Stomp crash. The telegraph ring is drawn from this same value. */
    private static final double CRASH_RADIUS = 8.0;
    /** Reach and half-angle (degrees) of the Earth-Shattering Roar. The telegraph cone uses the same values. */
    private static final double ROAR_RANGE = 8.0;
    private static final double ROAR_HALF_ANGLE_DEG = 45.0;
    /** cos(45 degrees): the dot-product cutoff that matches ROAR_HALF_ANGLE_DEG. */
    private static final double ROAR_COS = Math.cos(Math.toRadians(ROAR_HALF_ANGLE_DEG));
    private static final int STUCK_CHECKS_BEFORE_ESCAPE = 4;
    private static final double STUCK_MOVE_THRESHOLD_SQ = 0.16;
    private static final long ESCAPE_COOLDOWN_TICKS = 120L;   // 6s

    private double statMultiplier = 1.0;

    private enum AbilityState { IDLE, TELEGRAPH_FORTIFY, TELEGRAPH_STOMP, LEAPING, TELEGRAPH_ROAR }

    private AbilityState state = AbilityState.IDLE;
    private long telegraphEndsAtTick = 0L;
    private int leapTicks = 0;
    private boolean leapLeftGround = false;

    private long nextFortifyAtTick = 0L;
    private long nextStompAtTick = 0L;
    private long nextRoarAtTick = 0L;
    private long nextEscapeAtTick = 0L;

    private Vec3 lastStuckCheckPos;
    private int stuckTicks = 0;

    public CorruptedSentinel(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    public static CorruptedSentinel spawn(ServerLevel level, BlockPos pos, double statMultiplier) {
        CorruptedSentinel sentinel = new CorruptedSentinel(ModEntities.CORRUPTED_SENTINEL, level);
        sentinel.statMultiplier = statMultiplier;
        sentinel.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        sentinel.setYRot(level.getRandom().nextFloat() * 360.0F);

        AttributeInstance scale = sentinel.getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.setBaseValue(SCALE);
        }
        AttributeInstance health = sentinel.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            double hp = BASE_HEALTH * statMultiplier;
            health.setBaseValue(hp);
            sentinel.setHealth((float) hp);
        }
        AttributeInstance dmg = sentinel.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null) {
            dmg.setBaseValue(BASE_ATTACK_DAMAGE * statMultiplier);
        }
        AttributeInstance knockbackResist = sentinel.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knockbackResist != null) {
            knockbackResist.setBaseValue(0.85);
        }
        AttributeInstance speed = sentinel.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(speed.getBaseValue() * 0.7);
        }

        MobNames.apply(sentinel, "Corrupted Sentinel", MobNames.Tier.CORRUPTED);
        sentinel.setPersistenceRequired();

        sentinel.setItemSlot(EquipmentSlot.HEAD, EliteHeads.moltenGolemHead());
        sentinel.setDropChance(EquipmentSlot.HEAD, 0.0F);
        sentinel.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_AXE));
        sentinel.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        sentinel.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        sentinel.setDropChance(EquipmentSlot.CHEST, 0.0F);
        sentinel.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        sentinel.setDropChance(EquipmentSlot.LEGS, 0.0F);
        sentinel.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        sentinel.setDropChance(EquipmentSlot.FEET, 0.0F);

        level.addFreshEntity(sentinel);
        level.sendParticles(ParticleTypes.LAVA, sentinel.getX(), sentinel.getY() + 1.5, sentinel.getZ(),
                10, 0.6, 1.0, 0.6, 0.0);
        level.playSound(null, sentinel.getX(), sentinel.getY(), sentinel.getZ(),
                SoundEvents.RAVAGER_AMBIENT, SoundSource.HOSTILE, 1.5F, 0.6F);
        return sentinel;
    }

    @Override
    public boolean isSunSensitive() {
        return false;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!this.isAlive()) {
            return;
        }
        long now = level.getGameTime();

        spawnAmbientParticles(level);

        if (state == AbilityState.LEAPING) {
            tickLeap(level);
            return;
        }
        if (state != AbilityState.IDLE) {
            if (now >= telegraphEndsAtTick) {
                resolveTelegraph(level);
            } else if (now % 4 == 0) {
                drawTelegraph(level, now);
            }
            return;
        }

        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }

        // Only treat "hasn't moved" as a pathing stall when it's actually
        // trying to close distance - a Sentinel already at melee range is
        // SUPPOSED to stand still and swing (that's not stuck, that's
        // fighting). Without this guard the check false-positives during
        // ordinary melee combat and hijacks the whole ability rotation
        // into a leap-spam loop (confirmed live: Fortify/Roar/telegraphed
        // Stomp stopped firing entirely once a target stayed adjacent).
        boolean pastMeleeRange = this.distanceTo(target) > 4.0;
        if (pastMeleeRange) {
            if (checkStuckAndEscape(level, now)) {
                return;
            }
        } else {
            lastStuckCheckPos = null;
            stuckTicks = 0;
        }

        double healthFrac = this.getHealth() / this.getAttribute(Attributes.MAX_HEALTH).getValue();
        if (healthFrac < 0.5 && now >= nextFortifyAtTick) {
            nextFortifyAtTick = now + FORTIFY_COOLDOWN_TICKS;
            beginTelegraph(level, AbilityState.TELEGRAPH_FORTIFY, FORTIFY_TELEGRAPH_TICKS);
            return;
        }

        double distSq = this.distanceToSqr(target);
        if (distSq <= STOMP_RANGE * STOMP_RANGE && now >= nextStompAtTick) {
            nextStompAtTick = now + STOMP_COOLDOWN_TICKS;
            beginTelegraph(level, AbilityState.TELEGRAPH_STOMP, STOMP_TELEGRAPH_TICKS);
            return;
        }
        if (now >= nextRoarAtTick) {
            nextRoarAtTick = now + ROAR_COOLDOWN_TICKS;
            beginTelegraph(level, AbilityState.TELEGRAPH_ROAR, ROAR_TELEGRAPH_TICKS);
        }
    }

    private boolean checkStuckAndEscape(ServerLevel level, long now) {
        Vec3 pos = this.position();
        Vec3 last = lastStuckCheckPos;
        lastStuckCheckPos = pos;
        if (last == null) {
            stuckTicks = 0;
            return false;
        }
        if (last.distanceToSqr(pos) >= STUCK_MOVE_THRESHOLD_SQ) {
            stuckTicks = 0;
            return false;
        }
        stuckTicks++;
        if (stuckTicks < STUCK_CHECKS_BEFORE_ESCAPE) {
            return false;
        }
        stuckTicks = 0;
        if (now < nextEscapeAtTick) {
            return false;
        }
        nextEscapeAtTick = now + ESCAPE_COOLDOWN_TICKS;
        nextStompAtTick = now + STOMP_COOLDOWN_TICKS;
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.4F, 0.5F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
                this.getX(), this.getY() + 0.1, this.getZ(), 25, 1.0, 0.3, 1.0, 0.0);
        beginLeap(level);
        return true;
    }

    private void spawnAmbientParticles(ServerLevel level) {
        Vec3 pos = this.position().add(0.0, 1.0, 0.0);
        level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 2, 1.5, 2.0, 1.5, 0.01);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y, pos.z, 2, 1.2, 1.5, 1.2, 0.01);
    }

    /**
     * Shows the real danger area while the Sentinel winds up. The stomp is a ring at the crash radius that
     * fills toward impact; the roar is a cone at the roar's true reach and angle. Both read the same
     * constants the damage code uses, so what the player sees is what will hit them.
     */
    private void drawTelegraph(ServerLevel level, long now) {
        long total = state == AbilityState.TELEGRAPH_ROAR ? ROAR_TELEGRAPH_TICKS : STOMP_TELEGRAPH_TICKS;
        double progress = 1.0 - Math.max(0L, telegraphEndsAtTick - now) / (double) total;
        switch (state) {
            case TELEGRAPH_STOMP -> com.solme.emberfall.combat.Fx.telegraphFill(
                    level, this.position(), CRASH_RADIUS, progress, com.solme.emberfall.combat.Fx.WARN_ORANGE);
            case TELEGRAPH_ROAR -> com.solme.emberfall.combat.Fx.telegraphCone(
                    level, this.position(), this.getLookAngle(), ROAR_RANGE, ROAR_HALF_ANGLE_DEG,
                    com.solme.emberfall.combat.Fx.WARN_RED);
            default -> {}
        }
    }

    private void beginTelegraph(ServerLevel level, AbilityState next, int durationTicks) {
        state = next;
        telegraphEndsAtTick = level.getGameTime() + durationTicks;
        EmberfallMod.LOGGER.info("Corrupted Sentinel[id={}] telegraphing {}", this.getId(), next);
        Vec3 loc = this.position().add(0.0, 1.6, 0.0);
        switch (next) {
            case TELEGRAPH_FORTIFY -> {
                level.playSound(null, loc.x, loc.y, loc.z, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.2F, 0.8F);
                level.sendParticles(ParticleTypes.PORTAL, loc.x, loc.y, loc.z, 12, 0.6, 0.6, 0.6, 0.1);
            }
            case TELEGRAPH_STOMP -> {
                level.playSound(null, loc.x, loc.y, loc.z, SoundEvents.GRAVEL_BREAK, SoundSource.HOSTILE, 1.5F, 0.5F);
                level.playSound(null, loc.x, loc.y, loc.z, SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 1.5F, 0.5F);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
                        this.getX(), this.getY() + 0.1, this.getZ(), 30, 2.0, 0.1, 2.0, 0.0);
            }
            case TELEGRAPH_ROAR -> {
                level.playSound(null, loc.x, loc.y, loc.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 1.5F, 0.5F);
            }
            default -> {}
        }
    }

    private void resolveTelegraph(ServerLevel level) {
        AbilityState finishing = state;
        state = AbilityState.IDLE;
        switch (finishing) {
            case TELEGRAPH_FORTIFY -> executeFortify(level);
            case TELEGRAPH_STOMP -> beginLeap(level);
            case TELEGRAPH_ROAR -> executeRoar(level);
            default -> {}
        }
    }

    // ---- Fortify ----

    private void executeFortify(ServerLevel level) {
        EmberfallMod.LOGGER.info("Corrupted Sentinel[id={}] used Fortify", this.getId());
        this.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100, 3, false, true, true));
        this.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 4, false, true, true));
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.5F, 0.5F);
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.2F, 0.5F);
        Vec3 loc = this.position().add(0.0, 2.0, 0.0);
        for (int i = 0; i < 16; i++) {
            double angle = (Math.PI * 2 / 16) * i;
            double dx = Math.cos(angle) * 1.8;
            double dz = Math.sin(angle) * 1.8;
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                    loc.x + dx, loc.y - 1.0, loc.z + dz, 3, 0.1, 0.1, 0.1, 0.0);
        }
    }

    // ---- Ground Stomp: leap -> land -> crash ----

    private void beginLeap(ServerLevel level) {
        // Deliberately does NOT use setNoAi(true): on Fabric that also
        // gates LivingEntity#serverAiStep, which is what drives this very
        // ability system (customServerAiStep) - unlike SlopPack's Bukkit
        // version where the ability loop was a separate scheduler task
        // untouched by Mob#setAI(false). Leaving AI on means the goal
        // selector's move control may add minor horizontal steering
        // during the ~0.5-1s airborne window - a cosmetic nitpick, not a
        // functional problem, and far better than freezing mid-leap.
        state = AbilityState.LEAPING;
        leapTicks = 0;
        leapLeftGround = false;
        this.setDeltaMovement(0.0, 1.15, 0.0);
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.3F, 0.7F);
        level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY(), this.getZ(), 20, 0.6, 0.1, 0.6, 0.02);
    }

    private void tickLeap(ServerLevel level) {
        leapTicks++;
        if (!leapLeftGround && !this.onGround()) {
            leapLeftGround = true;
        }
        if ((leapLeftGround && this.onGround()) || leapTicks >= LEAP_TIMEOUT_TICKS) {
            state = AbilityState.IDLE;
            executeCrash(level);
        }
    }

    private void executeCrash(ServerLevel level) {
        EmberfallMod.LOGGER.info("Corrupted Sentinel[id={}] Ground Stomp crash", this.getId());
        Vec3 loc = this.position();
        level.playSound(null, loc.x, loc.y, loc.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.6F, 0.6F);
        level.playSound(null, loc.x, loc.y, loc.z, SoundEvents.WITHER_BREAK_BLOCK, SoundSource.HOSTILE, 1.2F, 0.5F);
        level.sendParticles(ParticleTypes.EXPLOSION, loc.x, loc.y, loc.z, 8, 3.0, 0.5, 3.0, 0.0);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                loc.x, loc.y, loc.z, 60, 3.5, 0.3, 3.5, 0.0);

        double range = CRASH_RADIUS;
        double maxDamage = 12.0 * statMultiplier;
        AABB box = AABB.ofSize(loc, range * 2, 4.0, range * 2);
        List<Player> hit = level.getEntitiesOfClass(Player.class, box, Player::isAlive);
        DamageSource source = this.damageSources().mobAttack(this);
        for (Player player : hit) {
            double dist = player.position().distanceTo(loc);
            if (dist > range) {
                continue;
            }
            double falloff = 1.0 - dist / range;
            double dmg = Math.max(2.0, maxDamage * falloff);
            player.hurtServer(level, source, (float) dmg);
            Vec3 push = player.position().subtract(loc);
            push = new Vec3(push.x, 0.0, push.z);
            if (push.lengthSqr() > 0.001) {
                push = push.normalize();
            }
            player.setDeltaMovement(push.x * 1.2 * falloff, 0.45 * falloff, push.z * 1.2 * falloff);
        }

        triggerGroundRipple(level, loc);

        if (statMultiplier >= AWAKENED_STAT_THRESHOLD) {
            ScorchedGround.spawn(level, BlockPos.containing(loc), 100, 3.5);
        }
    }

    private void triggerGroundRipple(ServerLevel level, Vec3 center) {
        int radius = 4;
        int cx = (int) Math.floor(center.x);
        int cz = (int) Math.floor(center.z);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > radius + 0.4) {
                    continue;
                }
                BlockPos surface = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                        new BlockPos(cx + dx, (int) center.y, cz + dz)).below();
                BlockState blockState = level.getBlockState(surface);
                if (blockState.isAir() || !blockState.isSolidRender()) {
                    continue;
                }
                Vec3 fx = new Vec3(surface.getX() + 0.5, surface.getY() + 1.05, surface.getZ() + 0.5);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, blockState),
                        fx.x, fx.y, fx.z, 3, 0.25, 0.05, 0.25, 0.0);
            }
        }
    }

    // ---- Earth-Shattering Roar ----

    private void executeRoar(ServerLevel level) {
        EmberfallMod.LOGGER.info("Corrupted Sentinel[id={}] used Earth-Shattering Roar", this.getId());
        Vec3 loc = this.getEyePosition();
        Vec3 dir = this.getLookAngle().normalize();
        level.playSound(null, loc.x, loc.y, loc.z, SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 1.8F, 0.6F);
        level.playSound(null, loc.x, loc.y, loc.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.5F, 0.5F);
        Vec3 conePos = loc.add(dir.scale(2.0));
        level.sendParticles(ParticleTypes.SONIC_BOOM, conePos.x, conePos.y, conePos.z, 3, 1.0, 1.0, 1.0, 0.1);

        double range = ROAR_RANGE;
        double maxDamage = 16.0 * statMultiplier;
        AABB box = AABB.ofSize(this.position(), range * 2, range * 2, range * 2);
        List<Player> hit = level.getEntitiesOfClass(Player.class, box, Player::isAlive);
        DamageSource source = this.damageSources().mobAttack(this);
        for (Player player : hit) {
            Vec3 toPlayer = player.position().subtract(this.position());
            double dist = toPlayer.length();
            if (dist > range || dist < 0.001) {
                continue;
            }
            Vec3 toPlayerNorm = toPlayer.scale(1.0 / dist);
            double dot = dir.dot(toPlayerNorm);
            if (dot < ROAR_COS) {
                continue;
            }
            double falloff = 1.0 - dist / range;
            double dmg = Math.max(4.0, maxDamage * falloff);
            player.hurtServer(level, source, (float) dmg);
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 1, false, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 1, false, true, true));
            player.setDeltaMovement(dir.x * 1.5, 0.4, dir.z * 1.5);
        }
    }

}
