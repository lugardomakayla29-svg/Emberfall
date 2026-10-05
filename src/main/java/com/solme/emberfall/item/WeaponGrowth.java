package com.solme.emberfall.item;

import java.util.random.RandomGenerator;

/**
 * The shared growth rules every weapon uses: a run-scoped weapon LEVEL, a fractional-count roll, and an ULTIMATE meter.
 * Pure arithmetic with no Minecraft types, so the rules can be tested exhaustively on their own (see GrowthMath in the
 * test sources) before any weapon depends on them.
 *
 * LEVEL: a weapon starts at level 1 and rises with the kills that weapon makes. The kills needed for the next level
 * grow slowly, so the first few levels come quickly (a new weapon feels like it is waking up) and the last ones are earned.
 * MAX_LEVEL caps it so a long run cannot push a radius or a count past what the arena and the packet budget can carry.
 *
 * FRACTIONAL COUNT: a weapon stat like "projectiles" or "extra chains" is a real number per level. 1.5 means one for
 * certain and a 50% chance of a second. {@link #roll} turns that number into a whole count for THIS shot. Over many shots
 * the average equals the number exactly, which is what makes a +0.25 per level step feel smooth instead of a cliff.
 *
 * ULTIMATE METER: filled by the weapon's own play (a hit adds a little, a kill adds more, an elite more again). When it
 * reaches {@link #METER_MAX} the ultimate fires on its own and the meter empties to the overflow, so a big kill at 95%
 * is not wasted. There is no key combo and nothing to press.
 */
public final class WeaponGrowth {
    public static final int MAX_LEVEL = 10;
    /** The ultimate meter is a whole number out of this, so it ships as one short and compares exactly. */
    public static final int METER_MAX = 1000;

    private WeaponGrowth() {}

    /** Kills a weapon needs, counted from level 1, to BE at {@code level}. Level 1 needs none. */
    public static int killsForLevel(int level) {
        int l = Math.max(1, Math.min(level, MAX_LEVEL));
        // 0, 4, 10, 18, 28, 40, 54, 70, 88, 108: the gap grows by 2 each level (4, 6, 8, ...).
        int total = 0;
        for (int i = 2; i <= l; i++) {
            total += 2 * i;
        }
        return total;
    }

    /** The level a weapon is at after {@code kills} kills, between 1 and {@link #MAX_LEVEL}. */
    public static int levelForKills(int kills) {
        int level = 1;
        while (level < MAX_LEVEL && kills >= killsForLevel(level + 1)) {
            level++;
        }
        return level;
    }

    /**
     * Turns a per-shot average into a whole count for this shot: the whole part always, the fraction as a chance of one more.
     * {@code roll(1.5)} is 1 half the time and 2 half the time; {@code roll(3.0)} is always 3; {@code roll(0.25)} is 1 a
     * quarter of the time. Negative or NaN input gives 0.
     */
    public static int roll(double average, RandomGenerator random) {
        if (!(average > 0.0)) {
            return 0;
        }
        int whole = (int) Math.floor(average);
        double fraction = average - whole;
        return whole + (fraction > 0.0 && random.nextDouble() < fraction ? 1 : 0);
    }

    /** The value a stat has at {@code level}: {@code base} at level 1, plus {@code perLevel} for every level above it. */
    public static double scale(double base, double perLevel, int level) {
        return base + perLevel * (Math.max(1, Math.min(level, MAX_LEVEL)) - 1);
    }

    /**
     * Adds {@code gain} to a meter and reports what is left after any ultimate. Returns the new meter value; the caller
     * checks {@link #ready} BEFORE calling {@link #afterFire}. A gain never pushes past twice the maximum, so a single
     * absurd hit cannot bank a second ultimate.
     */
    public static int fill(int meter, int gain) {
        long next = (long) Math.max(0, meter) + Math.max(0, gain);
        return (int) Math.min(next, 2L * METER_MAX - 1);
    }

    public static boolean ready(int meter) {
        return meter >= METER_MAX;
    }

    /** The meter after an ultimate fires: the overflow is kept, so no fill is wasted. */
    public static int afterFire(int meter) {
        return Math.max(0, meter - METER_MAX);
    }
}
