package com.solme.emberfall.frost;

/**
 * The Frostbloom colony's rules as PURE numbers (no Minecraft types), so each is provable without a server. The colony replaces Tiki
 * Magma: where the Tiki was hot, tall and levitating, the Frostbloom is cold, low and BURROWS, and strikes from below.
 *
 * Health and damage deliberately match the Tiki they replace (fodder 14 hp / 3 dmg, elite 120 hp / 7 dmg), so the wave tables do not
 * shift: only the behaviour is new.
 *
 * A burrowing mob is only fair if its warning IS its damage window. So the surface warning is the ONLY timing constant; the radius a
 * player must leave and the moment the burst lands are both derived from it here, and cannot be edited apart.
 */
public final class FrostbloomRules {
    private FrostbloomRules() {}

    public enum Tier { BUD, BLOOM, RIMEHEART }

    /** Ticks the ground heaves and shows a ring BEFORE the mob surfaces. The burst lands exactly when this ends. */
    public static final int SURFACE_WARNING_TICKS = 24;
    /** Blocks around the surfacing point that the burst hits. The warning ring is drawn at exactly this radius. */
    public static final double BURST_RADIUS = 2.5;
    /** Blocks per tick a player can cover while sprinting, used to prove the warning is long enough to leave the ring. */
    public static final double SPRINT_BLOCKS_PER_TICK = 0.28;

    public static double maxHealth(Tier t) {
        return switch (t) {
            case BUD -> 14.0;
            case BLOOM -> 120.0;
            case RIMEHEART -> 220.0;
        };
    }

    public static double attackDamage(Tier t) {
        return switch (t) {
            case BUD -> 3.0;
            case BLOOM -> 7.0;
            case RIMEHEART -> 10.0;
        };
    }

    /** Crystals shown on the body: more per tier, and the Rimeheart adds a dark core on top of its seven. */
    public static int crystals(Tier t) {
        return switch (t) {
            case BUD -> 3;
            case BLOOM -> 5;
            case RIMEHEART -> 7;
        };
    }

    /** Ticks a mob stays surfaced and attackable before it burrows again. Shorter for the weak, longer for the heavy. */
    public static int surfacedTicks(Tier t) {
        return switch (t) {
            case BUD -> 30;
            case BLOOM -> 50;
            case RIMEHEART -> 70;
        };
    }

    /** Ticks a mob stays burrowed (moving unseen) between surfacings. */
    public static int burrowedTicks(Tier t) {
        return switch (t) {
            case BUD -> 60;
            case BLOOM -> 80;
            case RIMEHEART -> 100;
        };
    }

    /** The tick, counted from the START of the warning, at which the burst lands. Derived: it is the end of the warning. */
    public static int burstTick() {
        return SURFACE_WARNING_TICKS;
    }

    /** Whether a player at {@code distance} blocks from the surfacing point is hit when the burst lands. */
    public static boolean burstHits(double distance) {
        return distance <= BURST_RADIUS;
    }

    /** The farthest a player standing exactly on the surfacing point can get from it during the warning, running flat out. */
    public static double reachableDistance() {
        return SURFACE_WARNING_TICKS * SPRINT_BLOCKS_PER_TICK;
    }

    /** True when a sprinting player who starts inside the ring can always leave it before the burst. This is what makes it fair. */
    public static boolean escapable() {
        return reachableDistance() > BURST_RADIUS;
    }

    /** Damage the burst deals at a distance: full at the centre, half at the edge, none outside. Never negative. */
    public static double burstDamage(Tier t, double distance) {
        if (!burstHits(distance)) {
            return 0.0;
        }
        double edge = Math.max(0.0, Math.min(1.0, distance / BURST_RADIUS));
        return attackDamage(t) * (1.0 - 0.5 * edge);
    }

    /** Ticks in one full burrow-surface cycle, warning included. */
    public static int cycleTicks(Tier t) {
        return burrowedTicks(t) + SURFACE_WARNING_TICKS + surfacedTicks(t);
    }

    /** The share of a cycle the mob can be hurt (it is surfaced). A mob that is never hittable would be unfair; one always hittable is no mechanic. */
    public static double vulnerableShare(Tier t) {
        return (double) surfacedTicks(t) / cycleTicks(t);
    }

    /** Rimeheart only: the crystals shatter outward once this much health has been lost in a single window, then regrow. */
    public static double shatterThreshold(Tier t) {
        return t == Tier.RIMEHEART ? maxHealth(t) * 0.20 : Double.POSITIVE_INFINITY;
    }

    /** Whether {@code damageTaken} within one surfaced window triggers the Rimeheart's shatter. */
    public static boolean shatters(Tier t, double damageTaken) {
        return damageTaken >= shatterThreshold(t);
    }

    /** Elite and up freeze the ground in a circle each cycle: slowness only, no damage, so standing in it is the danger. */
    public static boolean freezesGround(Tier t) {
        return t != Tier.BUD;
    }
}
