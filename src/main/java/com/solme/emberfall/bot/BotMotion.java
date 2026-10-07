package com.solme.emberfall.bot;

/**
 * The human-looking part of an EmberTester's movement, free of engine types so it can be proven on its own.
 *
 * <p>{@link BotWalk} says where the next placement is. This class shapes HOW the body gets there, from what real players do in a
 * fight (Minecraft wiki PvP tutorial: strafing, circle strafing, S-tapping, jump resets, hit-and-run): a turn-rate limit instead
 * of snapping to a new facing every tick (the owner saw the bot jitter), a speed that eases in and out, a sideways strafe that
 * flips direction at random intervals, a jump that follows the real arc, and a brief back-step after a hit.
 *
 * <p>Every method is a function of its inputs: randomness comes in as a {@code double u in [0,1)} so a test can replay it.
 */
public final class BotMotion {
    private BotMotion() {}

    /** Vanilla jump: initial upward speed 0.42 blocks/tick, gravity 0.08, drag 0.98 (peak about 1.25 blocks, 12 ticks in the air). */
    public static final double JUMP_SPEED = 0.42;
    public static final double GRAVITY = 0.08;
    public static final double DRAG = 0.98;

    /** Shortest signed turn from {@code from} to {@code to}, in degrees, in (-180, 180]. */
    public static double angleDelta(double from, double to) {
        double d = (to - from) % 360.0;
        if (d > 180.0) {
            d -= 360.0;
        } else if (d <= -180.0) {
            d += 360.0;
        }
        return d;
    }

    /**
     * Turns {@code current} toward {@code target} by at most {@code maxTurn} degrees, taking the short way round. This replaces the
     * old "face the step direction every tick", which flipped the facing by large angles on every re-plan.
     */
    public static double turnToward(double current, double target, double maxTurn) {
        double d = angleDelta(current, target);
        if (Math.abs(d) <= maxTurn) {
            return target;
        }
        return current + Math.signum(d) * maxTurn;
    }

    /**
     * The turn rate for this much remaining angle: fast when far off, gentle when close (an ease-out), between {@code minTurn}
     * and {@code maxTurn} degrees per tick. A human does not turn at one constant rate.
     */
    public static double turnRate(double remainingDegrees, double minTurn, double maxTurn) {
        double f = Math.min(1.0, Math.abs(remainingDegrees) / 90.0);
        return minTurn + (maxTurn - minTurn) * f;
    }

    /** Eases a speed toward a target by at most {@code accel} per tick, so the bot does not start and stop instantly. */
    public static double easeSpeed(double current, double target, double accel) {
        double d = target - current;
        return Math.abs(d) <= accel ? target : current + Math.signum(d) * accel;
    }

    /**
     * Height above the take-off point {@code tick} ticks into a jump (0 at take-off). Follows the vanilla integration
     * (velocity 0.42, minus gravity, times drag) so the arc looks like a real jump. Returns 0 once it lands.
     */
    public static double jumpHeight(int tick) {
        double y = 0.0;
        double v = JUMP_SPEED;
        for (int t = 0; t < tick; t++) {
            y += v;
            v = (v - GRAVITY) * DRAG;
            if (y <= 0.0 && t > 0) {
                return 0.0;
            }
        }
        return Math.max(0.0, y);
    }

    /** How many ticks a full jump lasts (until it comes back down to the take-off height). */
    public static int jumpTicks() {
        int t = 1;
        while (jumpHeight(t) > 0.0 && t < 100) {
            t++;
        }
        return t;
    }

    /**
     * Strafe state: which way (+1 right, -1 left, 0 none) and ticks left in this direction. Real players hold a direction for a
     * short, uneven time and then flip; they do not oscillate on a fixed beat. {@code u1} picks the duration, {@code u2} the side.
     */
    public record Strafe(int dir, int ticksLeft) {}

    /**
     * Steps the strafe one tick. While ticks remain it keeps its direction; when they run out it either rests or picks a new
     * side. {@code share} is the fraction of time spent strafing (BotPersonality.strafeShare), {@code flipBias} favours reversing.
     */
    public static Strafe stepStrafe(Strafe s, double share, double u1, double u2) {
        if (s.ticksLeft() > 1) {
            return new Strafe(s.dir(), s.ticksLeft() - 1);
        }
        int ticks = 8 + (int) (u1 * 22.0); // 8..29 ticks, 0.4..1.5 s
        if (u2 >= share) {
            return new Strafe(0, ticks); // a rest
        }
        int side = u2 < share / 2.0 ? 1 : -1;
        if (s.dir() != 0 && u1 < 0.5) {
            side = -s.dir(); // half the time a strafe reverses the last one, the way a real player juke-strafes
        }
        return new Strafe(side, ticks);
    }

    /**
     * Sideways offset in blocks for this tick: the strafe direction times the per-tick strafe distance, eased in over the first
     * three ticks so it does not jerk. {@code ticksIntoStrafe} counts up from 0.
     */
    public static double strafeStep(Strafe s, int ticksIntoStrafe, double perTick) {
        if (s.dir() == 0) {
            return 0.0;
        }
        double ease = Math.min(1.0, (ticksIntoStrafe + 1) / 3.0);
        return s.dir() * perTick * ease;
    }

    /**
     * S-tap: after a hit, ease back a little to re-open the gap, then close again (what the PvP tutorials call S-tapping). Returns
     * the backward fraction (0..1) of normal speed for this many ticks after the hit: strongest right after it, gone after
     * {@code window} ticks.
     */
    public static double sTap(int ticksSinceHit, int window) {
        if (ticksSinceHit < 0 || ticksSinceHit >= window) {
            return 0.0;
        }
        return 1.0 - (double) ticksSinceHit / window;
    }

    /**
     * Decision lag: a human does not react in the same tick something happens. Returns true once {@code ticksSinceChange}
     * has reached the bot's reaction time, so a changed situation is acted on only after that delay.
     */
    public static boolean hasReacted(int ticksSinceChange, int reactionTicks) {
        return ticksSinceChange >= reactionTicks;
    }
}
