package com.solme.emberfall.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The Tiki's levitation: it lifts off the ground, glides toward the player, then drops and lands with the existing
 * {@link TikiMagma} landing shove, so the ability has a payoff and reuses tested code. Stronger with tier: higher,
 * faster, longer, and a shorter recharge. Fodder glides too, low and slow, so no Tiki hops about aimlessly.
 *
 * <p>Cost: no entities and no scheduled work. While aloft the mob's own gravity is switched off and its velocity is
 * steered toward a hover point above the player, a few arithmetic operations per tick; it ends by itself after a fixed
 * number of ticks (or at once if the target is gone), and {@link #cancel} restores gravity on every removal path.
 * The Tiki never leaves the play area in effect because it only steers toward a player who is inside it.
 */
final class TikiLevitate {
    // ---- tuning per tier: hover height above the player's feet, glide speed in blocks per tick, duration, recharge ----
    private record Spec(double height, double speed, int durationTicks, int cooldownTicks) { }

    private static final Spec FODDER = new Spec(0.9, 0.12, 40, 100);      // 2.0 s aloft, every 5 s: low and slow, still closes the gap
    private static final Spec VETERAN = new Spec(1.6, 0.16, 40, 200);     // 2.0 s aloft, every 10 s
    private static final Spec ELITE = new Spec(2.6, 0.22, 60, 160);       // 3.0 s aloft, every 8 s
    private static final Spec CORRUPTED = new Spec(3.6, 0.28, 80, 120);   // 4.0 s aloft, every 6 s
    static final double TRIGGER_MIN = 5.0;    // only lifts off when the player is at least this far away (to close the gap)
    private static final double RISE_RATE = 0.12;   // blocks per tick of vertical climb toward the hover height

    private int remaining = 0;
    private long readyAt = 0L;
    private double groundY = 0.0;

    static Spec specFor(TikiMagma.EliteTier tier, boolean veteran) {
        return switch (tier) {
            case CORRUPTED -> CORRUPTED;
            case ELITE -> ELITE;
            case NONE -> veteran ? VETERAN : FODDER;
        };
    }

    boolean aloft() {
        return remaining > 0;
    }

    private long glideEndedAt = Long.MIN_VALUE / 2;

    /** True when the Tiki came down from a glide within the last 3 seconds (used by the sandbox test to tell a drop from a hop). */
    boolean droppedFromGlide(long now) {
        return now - glideEndedAt < 60L;
    }


    /** Called every server tick while the Tiki is alive; {@code busy} (a voice move winding up) only blocks a NEW lift-off. */
    void tick(TikiMagma tiki, ServerLevel level, LivingEntity target, TikiMagma.EliteTier tier, boolean veteran, boolean busy) {
        Spec spec = specFor(tier, veteran);
        if (spec == null) {
            return;
        }
        long now = level.getGameTime();
        if (remaining > 0) {
            if (!(target instanceof Player) || !target.isAlive()) {
                cancel(tiki);        // the target is gone: come down
                return;
            }
            remaining--;
            steer(tiki, target, spec);
            if ((now & 3L) == 0L) {
                level.sendParticles(ParticleTypes.FLAME, tiki.getX(), tiki.getY() + 0.1, tiki.getZ(), 2, 0.25, 0.05, 0.25, 0.01);
            }
            if (remaining == 0) {
                cancel(tiki);
            }
            return;
        }
        if (busy || now < readyAt || !(target instanceof Player) || !target.isAlive() || !tiki.onGround()) {
            return;
        }
        double d2 = tiki.distanceToSqr(target);
        if (d2 < TRIGGER_MIN * TRIGGER_MIN) {
            return;
        }
        remaining = spec.durationTicks();
        readyAt = now + spec.durationTicks() + spec.cooldownTicks();
        groundY = tiki.getY();
        tiki.setNoGravity(true);
        level.playSound(null, tiki.getX(), tiki.getY(), tiki.getZ(), SoundEvents.BLAZE_SHOOT, net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 0.6F);
    }

    private void steer(TikiMagma tiki, LivingEntity target, Spec spec) {
        double hoverY = Math.max(groundY, target.getY()) + spec.height();
        double dy = Math.max(-RISE_RATE, Math.min(RISE_RATE, hoverY - tiki.getY()));
        Vec3 toward = new Vec3(target.getX() - tiki.getX(), 0.0, target.getZ() - tiki.getZ());
        double len = toward.length();
        Vec3 horizontal = len > spec.speed() ? toward.scale(spec.speed() / len) : Vec3.ZERO;   // stop above the player, do not jitter
        tiki.setDeltaMovement(horizontal.x, dy, horizontal.z);
        tiki.hurtMarked = true;   // forces a velocity sync so clients see the glide
    }

    /** Ends the glide and gives gravity back, so the Tiki falls and lands (which triggers its landing shove). */
    void cancel(TikiMagma tiki) {
        if (remaining > 0 || tiki.isNoGravity()) {
            tiki.setNoGravity(false);
            if (tiki.level() instanceof ServerLevel sl) {
                glideEndedAt = sl.getGameTime();
            }
        }
        remaining = 0;
    }
}
