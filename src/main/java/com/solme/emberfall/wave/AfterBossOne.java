package com.solme.emberfall.wave;

/**
 * What changes for the rest of the run once the FIRST boss (the Ember Guardian) is dead, as PURE numbers (no Minecraft types)
 * so every rule is provable without a server.
 *
 * Killing the first boss moves the run to tier 2 ({@link WaveDirector#tier()}). Tier 2 already made veterans far more likely
 * (4% to 30%), brought elites twice as often and added a stat bonus. What it did NOT do was make the ordinary horde arrive any
 * faster, and the player was told nothing loud about it. This class adds the missing numbers:
 * <ul>
 *   <li>the horde spawn interval after boss 1 is {@link #INTERVAL_FACTOR} times the interval before it (more mobs per minute);</li>
 *   <li>the warning line the player reads once, and the sound they hear once.</li>
 * </ul>
 * The mob CAP is not touched here or anywhere in this change: more spawns per minute, never more mobs alive at once.
 */
public final class AfterBossOne {
    private AfterBossOne() {}

    /** After boss 1 the horde arrives at 70% of the old interval, about 1.4 times as many spawns per minute. */
    public static final double INTERVAL_FACTOR = 0.7;

    /** The one chat line the player sees when the first boss falls. Sent exactly once per run, from {@link WaveDirector#onHydraDefeated}. */
    public static final String WARNING =
            "§4§lWARNING: §cThe horde grows restless. §7More of them now, and the strong ones come more often.";

    /** The log line written each time the warning and sound fire, so a test (and V4's audit) can prove they happened. */
    public static final String LOG_TAG = "AFTERBOSS1";

    /** True from the first boss's death on (tier 2 or later). Tier 1 is the run before any boss has fallen. */
    public static boolean active(int tier) {
        return tier >= 2;
    }

    /**
     * The horde spawn interval, in ticks, for a SOLO player at this moment.
     *
     * @param soloIntervalTicks the interval the director would use for one player at the current threat
     * @param floorTicks        the director's own minimum (a faster horde never goes under it)
     * @param tier              the run's tier: 1 before the first boss dies, 2 after
     * @return the input unchanged before boss 1; after it, {@code round(input * 0.7)}, never under the floor and never above
     *         the input (an interval that is already at or under the floor is returned as it was)
     */
    public static int spawnIntervalTicks(int soloIntervalTicks, int floorTicks, int tier) {
        if (!active(tier)) {
            return soloIntervalTicks;
        }
        int scaled = (int) Math.round(soloIntervalTicks * INTERVAL_FACTOR);
        return Math.max(Math.min(soloIntervalTicks, floorTicks), Math.min(soloIntervalTicks, scaled));
    }
}
