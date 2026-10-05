package com.solme.emberfall.combat;

import com.solme.emberfall.item.WeaponGrowth;

/**
 * The Spectral Sickles' growth maths. {@link OrbitWeaponSystem} owns the tick loop and calls these.
 *
 * BLADES, fractional: 2.0 at level 1 plus 0.3 per level, so 4.7 at level 10. The whole part always orbits; the fraction is a blade that
 * exists for that share of every revolution (a blade that is "on" for 70% of the lap at 4.7), decided by the blade's angle, never by a
 * random roll, so it does not flicker from tick to tick.
 * ORBIT: radius 2.2 to 3.6 and blade contact radius 0.9 to 1.3, so the thin ring becomes a wide band.
 * SPIN: 8 to 14 degrees a tick. Spin raises coverage, not damage, because a target can only be cut once per hit cooldown.
 *
 * ULTIMATE, Reaper's Rite (see {@link ReapersRite} and {@link ReapersRiteSystem}): the meter fills from the sickles' own hits and kills. When
 * full, the sickles GATHER every foe in reach into one pile by dragging them fast, then both sickles sit there as a spinning full disc and
 * SLICE everything in it for 5 s (10 s at level 10), then BREAK in one last big hit. The ordinary ring stands down while it runs.
 */
public final class SickleSystem {
    public static final double BLADES_BASE = 2.0;
    public static final double BLADES_PER_LEVEL = 0.3;
    public static final double RADIUS_BASE = 2.2;
    public static final double RADIUS_PER_LEVEL = 1.4 / 9.0;
    public static final double HIT_BASE = 0.9;
    public static final double HIT_PER_LEVEL = 0.4 / 9.0;
    public static final double SPIN_BASE = 8.0;
    public static final double SPIN_PER_LEVEL = 6.0 / 9.0;
    public static final int NORMAL_COOLDOWN = 8;
    /** Each level above 1 shortens the per-foe cooldown by this share, so more blades mean faster cutting on ONE foe (floor {@link #MIN_COOLDOWN}). */
    public static final double COOLDOWN_SPEEDUP_PER_LEVEL = 0.11;
    public static final int MIN_COOLDOWN = 3;
    /** Hard ceiling on blades a player can show at once, so particle and test cost stay bounded whatever happens. */
    public static final int MAX_BLADES = 12;

    private SickleSystem() {}

    /** Ticks between two cuts on the same foe at this level: 8 at level 1, 4 at level 10. */
    public static int cooldownAt(int level) {
        int l = Math.max(1, Math.min(level, WeaponGrowth.MAX_LEVEL));
        return Math.max(MIN_COOLDOWN, (int) Math.round(NORMAL_COOLDOWN / (1.0 + COOLDOWN_SPEEDUP_PER_LEVEL * (l - 1))));
    }

    public static double bladesAt(int level) {
        return WeaponGrowth.scale(BLADES_BASE, BLADES_PER_LEVEL, level);
    }

    public static double orbitRadius(int level) {
        return WeaponGrowth.scale(RADIUS_BASE, RADIUS_PER_LEVEL, level);
    }

    public static double hitRadius(int level) {
        return WeaponGrowth.scale(HIT_BASE, HIT_PER_LEVEL, level);
    }

    public static double spinDegrees(int level) {
        return WeaponGrowth.scale(SPIN_BASE, SPIN_PER_LEVEL, level);
    }

    /** Blade count for this level, never above {@link #MAX_BLADES}. */
    public static double effectiveBlades(int level) {
        return Math.min(MAX_BLADES, bladesAt(level));
    }

    /**
     * Whether blade number {@code index} (0 based) of a ring of {@code blades} blades is on the ring at this moment. Whole blades are always
     * on. The one fractional blade (the last index) is on for {@code fraction} of each lap, judged by the ring's own phase, so it is
     * steady: on for a stretch of the lap, then off, not a flicker.
     */
    public static boolean bladeOn(int index, double blades, double lapPhase01) {
        int whole = (int) Math.floor(blades);
        if (index < whole) {
            return true;
        }
        double fraction = blades - whole;
        return index == whole && fraction > 0.0 && lapPhase01 < fraction;
    }

    /** How many blade slots to walk for a ring of {@code blades} blades (the whole part plus one for the fractional blade). */
    public static int slots(double blades) {
        int whole = (int) Math.floor(blades);
        return (blades - whole) > 0.0 ? whole + 1 : whole;
    }

    /** Trailing points behind each blade (the comet tail that makes the ring read as a sweep, not dots). */
    public static final int TRAIL_POINTS = 2;
    /** Hard ceiling on particles the ordinary ring may draw in one tick, whatever the level. */
    public static final int RING_PARTICLE_BUDGET = 40;

    /** Evenly spaced points on the rim between the blades: 6 at level 1 up to 19 at level 10, so the circle closes up as the weapon grows. */
    public static int rimPoints(int level) {
        return (int) Math.round(WeaponGrowth.scale(6.0, 1.5, level));
    }

    /** Points of the inner fill (two rings inside the blade circle): 4 at level 1 up to 13 at level 10, so the disc reads as filled. */
    public static int fillPoints(int level) {
        return (int) Math.round(WeaponGrowth.scale(4.0, 1.0, level));
    }

    /** Particles the whole ordinary ring draws in a tick of the given parity, for {@code slots} blade slots: blades and trails every tick, rim and fill every other. */
    public static int ringParticles(int level, int slots, boolean fullTick) {
        int n = slots + slots * TRAIL_POINTS;
        if (fullTick) {
            n += rimPoints(level) + fillPoints(level);
        }
        return Math.min(RING_PARTICLE_BUDGET, n);
    }
}
