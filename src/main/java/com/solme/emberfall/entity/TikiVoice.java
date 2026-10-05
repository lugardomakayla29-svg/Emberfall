package com.solme.emberfall.entity;

import com.solme.emberfall.combat.Fx;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Tiki's two ranged moves, kept out of {@link TikiMagma} so that class stays about the rig.
 *
 * <ul>
 *   <li><b>Shriek</b>: the idol screams. A cone in front of it is outlined for a short wind-up, then everyone
 *       inside is hurt and shoved back a little. Veteran fodder, Elite and Corrupted all have it.</li>
 *   <li><b>Laser</b>: a straight beam from the idol's masks. Outlined as a lane during the wind-up, then it
 *       fires along the locked line. Only Elite and Corrupted have it, and the higher tier recharges faster.</li>
 * </ul>
 *
 * <p>Cost: no entities at all. The warning is drawn on a 4 tick cadence during the wind-up only (never during the
 * hit), and both moves resolve with a geometry test against players, so a Tiki adds a handful of packets per
 * second only while it is winding up. The direction is locked when the wind-up starts, so both are dodgeable.
 * Only {@link Player}s are ever selected, so neither move can hurt another expedition mob.
 */
final class TikiVoice {
    enum Move { NONE, SHRIEK, LASER }

    // ---- tuning ----
    static final double SHRIEK_RANGE = 8.0;
    static final double SHRIEK_HALF_ANGLE_DEG = 32.0;
    static final int SHRIEK_WINDUP_TICKS = 18;
    static final double SHRIEK_KNOCKBACK = 0.55;
    static final double LASER_RANGE = 20.0;
    static final double LASER_HALF_WIDTH = 0.8;
    static final int LASER_WINDUP_TICKS = 22;
    static final double LASER_DAMAGE_MULT = 1.4;   // of the Tiki's own attack damage
    static final double SHRIEK_DAMAGE_MULT = 0.9;
    static final double TRIGGER_RANGE = 12.0;      // only starts a move when the player is this close

    private Move move = Move.NONE;
    private long resolveAtTick = 0L;
    private long shriekReadyAt = 0L;
    private long laserReadyAt = 0L;
    private Vec3 lockedDir = new Vec3(1.0, 0.0, 0.0);
    private Vec3 lockedOrigin = Vec3.ZERO;

    /** Cooldown in ticks for a move at a tier. Higher tier = shorter, which is what makes the laser "faster". */
    static int shriekCooldown(TikiMagma.EliteTier tier, boolean veteran) {
        return switch (tier) {
            case CORRUPTED -> 100;   // 5.0s
            case ELITE -> 130;       // 6.5s
            case NONE -> veteran ? 160 : Integer.MAX_VALUE;   // fodder without veteran has no shriek
        };
    }

    static int laserCooldown(TikiMagma.EliteTier tier) {
        return switch (tier) {
            case CORRUPTED -> 100;   // 5.0s
            case ELITE -> 160;       // 8.0s
            case NONE -> Integer.MAX_VALUE;
        };
    }

    boolean busy() {
        return move != Move.NONE;
    }

    Move current() {
        return move;
    }

    /** Called every server tick while the Tiki is alive. */
    void tick(TikiMagma tiki, ServerLevel level, LivingEntity target, TikiMagma.EliteTier tier, boolean veteran, double attackDamage, boolean aloft) {
        long now = level.getGameTime();
        if (move != Move.NONE) {
            if (target == null || !target.isAlive()) {
                move = Move.NONE;   // target gone mid wind-up: cancel quietly
                return;
            }
            if ((now & 3L) == 0L) {
                drawWarning(level);
            }
            if (now >= resolveAtTick) {
                if (move == Move.SHRIEK) {
                    resolveShriek(tiki, level, attackDamage);
                } else {
                    resolveLaser(tiki, level, attackDamage);
                }
                move = Move.NONE;
            }
            return;
        }
        if (aloft || !(target instanceof Player) || !target.isAlive() || tiki.distanceToSqr(target) > TRIGGER_RANGE * TRIGGER_RANGE) {
            return;   // aloft: the glide finishes and lands first, then the voice moves resume
        }
        // The laser is the rarer, stronger move: try it first when it is ready.
        if (now >= laserReadyAt && laserCooldown(tier) != Integer.MAX_VALUE
                && tiki.distanceToSqr(target) > 4.0 * 4.0) {
            begin(tiki, level, target, Move.LASER, LASER_WINDUP_TICKS);
            laserReadyAt = now + LASER_WINDUP_TICKS + laserCooldown(tier);
            return;
        }
        if (now >= shriekReadyAt && shriekCooldown(tier, veteran) != Integer.MAX_VALUE
                && tiki.distanceToSqr(target) <= SHRIEK_RANGE * SHRIEK_RANGE) {
            begin(tiki, level, target, Move.SHRIEK, SHRIEK_WINDUP_TICKS);
            shriekReadyAt = now + SHRIEK_WINDUP_TICKS + shriekCooldown(tier, veteran);
        }
    }

    private void begin(TikiMagma tiki, ServerLevel level, LivingEntity target, Move next, int windup) {
        move = next;
        resolveAtTick = level.getGameTime() + windup;
        lockedOrigin = tiki.position().add(0.0, tiki.getBbHeight() * 0.6, 0.0);
        Vec3 to = target.getEyePosition().subtract(lockedOrigin);
        Vec3 flat = new Vec3(to.x, next == Move.LASER ? to.y : 0.0, to.z);
        lockedDir = flat.lengthSqr() > 1.0E-4 ? flat.normalize() : new Vec3(1.0, 0.0, 0.0);
        Fx.sound(level, tiki.position(), next == Move.SHRIEK ? SoundEvents.GHAST_WARN : SoundEvents.BEACON_POWER_SELECT,
                1.1F, next == Move.SHRIEK ? 0.7F : 1.6F);
        drawWarning(level);
    }

    private void drawWarning(ServerLevel level) {
        if (move == Move.SHRIEK) {
            Fx.telegraphCone(level, new Vec3(lockedOrigin.x, groundY(), lockedOrigin.z),
                    lockedDir, SHRIEK_RANGE, SHRIEK_HALF_ANGLE_DEG, Fx.WARN_ORANGE);
        } else if (move == Move.LASER) {
            Fx.telegraphLine(level, lockedOrigin, lockedOrigin.add(lockedDir.scale(LASER_RANGE)), LASER_HALF_WIDTH, Fx.WARN_RED);
        }
    }

    private double groundY() {
        return lockedOrigin.y - 0.9;   // the cone outline sits near the feet, not at mask height
    }

    private void resolveShriek(TikiMagma tiki, ServerLevel level, double attackDamage) {
        Vec3 o = lockedOrigin;
        double cosLimit = Math.cos(Math.toRadians(SHRIEK_HALF_ANGLE_DEG));
        Fx.sound(level, o, SoundEvents.WARDEN_SONIC_BOOM, 0.9F, 1.5F);
        for (int i = 1; i <= 6; i++) {
            double d = SHRIEK_RANGE * i / 6.0;
            Vec3 p = o.add(lockedDir.scale(d));
            level.sendParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        AABB box = new AABB(o, o).inflate(SHRIEK_RANGE);
        for (Player p : level.getEntitiesOfClass(Player.class, box, pl -> pl.isAlive() && !pl.isSpectator() && !pl.isCreative())) {
            Vec3 rel = p.position().add(0.0, p.getBbHeight() * 0.5, 0.0).subtract(o);
            Vec3 flatRel = new Vec3(rel.x, 0.0, rel.z);
            double dist = flatRel.length();
            if (dist > SHRIEK_RANGE || dist < 1.0E-4) {
                continue;
            }
            Vec3 flatDir = new Vec3(lockedDir.x, 0.0, lockedDir.z).normalize();
            if (flatRel.normalize().dot(flatDir) < cosLimit) {
                continue;
            }
            p.hurtServer(level, tiki.damageSources().mobAttack(tiki), (float) (attackDamage * SHRIEK_DAMAGE_MULT));
            Vec3 push = flatRel.normalize().scale(SHRIEK_KNOCKBACK);
            p.setDeltaMovement(p.getDeltaMovement().add(push.x, 0.18, push.z));
            p.hurtMarked = true;
        }
    }

    private void resolveLaser(TikiMagma tiki, ServerLevel level, double attackDamage) {
        Vec3 o = lockedOrigin;
        Vec3 end = o.add(lockedDir.scale(LASER_RANGE));
        Fx.sound(level, o, SoundEvents.GUARDIAN_ATTACK, 1.2F, 1.4F);
        DustParticleOptions beam = new DustParticleOptions(Fx.WARN_RED, 1.1F);
        for (int i = 0; i <= 24; i++) {
            Vec3 p = o.add(lockedDir.scale(LASER_RANGE * i / 24.0));
            level.sendParticles(beam, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        AABB box = new AABB(o, end).inflate(LASER_HALF_WIDTH + 0.5);
        for (Player p : level.getEntitiesOfClass(Player.class, box, pl -> pl.isAlive() && !pl.isSpectator() && !pl.isCreative())) {
            Vec3 c = p.position().add(0.0, p.getBbHeight() * 0.5, 0.0);
            double t = Math.max(0.0, Math.min(LASER_RANGE, c.subtract(o).dot(lockedDir)));
            Vec3 closest = o.add(lockedDir.scale(t));
            if (closest.distanceTo(c) <= LASER_HALF_WIDTH + p.getBbWidth() * 0.5) {
                p.hurtServer(level, tiki.damageSources().mobAttack(tiki), (float) (attackDamage * LASER_DAMAGE_MULT));
                level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 8, 0.2, 0.3, 0.2, 0.03);
            }
        }
    }
}
