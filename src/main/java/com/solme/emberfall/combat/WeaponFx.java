package com.solme.emberfall.combat;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * "What just happened" feedback for the player's own melee weapons, drawn only when a swing really damaged
 * something, so the particle doubles as a hit confirmation. Each moveset gets its own shape, so the Broadsword,
 * the daggers and the chain are told apart at a glance.
 *
 * Budget rules (a player attack can land many times a second, so this must stay cheap): every shape is a small
 * FIXED number of particles, none is forced past the client's particle limiter, and nothing is drawn on a miss.
 * All of it is plain vanilla particles, so no resource pack is needed.
 */
public final class WeaponFx {
    private static final DustParticleOptions STEEL = new DustParticleOptions(0xD8E4F0, 1.0F);
    private static final DustParticleOptions EMPOWER = new DustParticleOptions(0xFFC24A, 1.3F);
    private static final DustParticleOptions BLOOD = new DustParticleOptions(0xC01818, 1.0F);

    private WeaponFx() {}

    /** Horizontal unit vector from the player toward the target (falls back to the player's facing). */
    private static Vec3 forward(ServerPlayer player, LivingEntity target) {
        Vec3 d = new Vec3(target.getX() - player.getX(), 0, target.getZ() - player.getZ());
        if (d.lengthSqr() < 1.0E-4) {
            d = Vec3.directionFromRotation(0, player.getYRot());
            d = new Vec3(d.x, 0, d.z);
        }
        return d.normalize();
    }

    /**
     * A crescent in front of the player, {@code arc} radians wide, at {@code radius} blocks, from one side to the other.
     * {@code points} particles, evenly spaced, at waist height.
     */
    static void arc(ServerLevel level, ServerPlayer player, Vec3 forward, double radius, double arc, int points,
                    double startFraction, double endFraction, ParticleOptions particle) {
        double baseAngle = Math.atan2(forward.z, forward.x);
        double y = player.getY() + player.getBbHeight() * 0.55;
        for (int i = 0; i < points; i++) {
            double t = points == 1 ? 0.5 : (double) i / (points - 1);
            double f = startFraction + (endFraction - startFraction) * t;
            double a = baseAngle + (f - 0.5) * arc;
            level.sendParticles(particle, player.getX() + Math.cos(a) * radius, y, player.getZ() + Math.sin(a) * radius,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** Broadsword: one wide sweeping arc. The empowered Steady Hand swing is longer, golden and louder. */
    public static void broadswordSwing(ServerLevel level, ServerPlayer player, LivingEntity target, double reach, boolean empowered) {
        Vec3 f = forward(player, target);
        double radius = Math.max(1.5, Math.min(reach, 3.5));
        arc(level, player, f, radius, Math.toRadians(empowered ? 200 : 150), empowered ? 14 : 9, 0.0, 1.0,
                empowered ? EMPOWER : STEEL);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX() + f.x * radius * 0.7, player.getY() + player.getBbHeight() * 0.6,
                player.getZ() + f.z * radius * 0.7, 1, 0.0, 0.0, 0.0, 0.0);
        if (empowered) {
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    8, 0.25, 0.3, 0.25, 0.3);
            Fx.sound(level, target.position(), SoundEvents.PLAYER_ATTACK_STRONG, 0.8F, 0.9F);
        }
    }

    /** Twin Daggers: two crossing slashes, one from each side. The empowered hit adds a red burst on the target. */
    public static void daggerSlash(ServerLevel level, ServerPlayer player, LivingEntity target, double reach, boolean empowered) {
        Vec3 f = forward(player, target);
        double radius = Math.max(1.2, Math.min(reach, 2.6));
        arc(level, player, f, radius, Math.toRadians(90), 5, 0.0, 1.0, STEEL);
        arc(level, player, f, radius, Math.toRadians(90), 5, 1.0, 0.0, STEEL);
        if (empowered) {
            level.sendParticles(BLOOD, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    10, 0.3, 0.3, 0.3, 0.0);
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    6, 0.25, 0.3, 0.25, 0.3);
        }
    }

    /** War Halberd: a forward chop arc (the cleave ring is drawn separately by the cleave code). */
    public static void halberdChop(ServerLevel level, ServerPlayer player, LivingEntity target, double reach) {
        Vec3 f = forward(player, target);
        double radius = Math.max(1.8, Math.min(reach, 4.0));
        arc(level, player, f, radius, Math.toRadians(110), 6, 0.0, 1.0, ParticleTypes.CRIT);
    }

    /**
     * Spectral Sickles: a blade just cut this mob. A short soul-fire slash through the mob's body plus a crit burst,
     * so the hit reads clearly against the constant orbiting-blade particles. Fixed size: 1 sweep, 6 flame, 6 crit.
     */
    public static void sickleCut(ServerLevel level, LivingEntity target) {
        double x = target.getX(), y = target.getY() + target.getBbHeight() * 0.55, z = target.getZ();
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 6, 0.3, 0.3, 0.3, 0.04);
        level.sendParticles(ParticleTypes.CRIT, x, y, z, 6, 0.25, 0.3, 0.25, 0.3);
    }

    /** Gravechain: a chain of soul-fire from the player to the target, drawn on the pull. Fixed 8 points. */
    public static void chainLine(ServerLevel level, ServerPlayer player, LivingEntity target) {
        Vec3 from = new Vec3(player.getX(), player.getY() + player.getBbHeight() * 0.6, player.getZ());
        Vec3 to = new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ());
        int points = 8;
        for (int i = 1; i <= points; i++) {
            Vec3 p = from.lerp(to, (double) i / points);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.0);
        }
        level.sendParticles(ParticleTypes.SOUL, to.x, to.y, to.z, 5, 0.2, 0.3, 0.2, 0.02);
    }
}
