package com.solme.emberfall.world;

/**
 * How a run's numbers change with the number of players in it (issue #13, plan {@code docs/PLAN_party_scaling.md} section 4).
 * Pure numbers and no game types, so the maths can be checked without a server, like {@code RunModifiers}.
 *
 * <p><b>Status: the maths only.</b> Nothing calls this yet. The wiring into {@code WaveDirector} and the boss {@code applyCurse}
 * is plan steps 3 and 4, each its own PR. For one player every method returns today's value, so wiring it in cannot change a
 * solo run.
 *
 * <p><b>The coefficients are PLACEHOLDERS from the plan, not tuned.</b> They make the structure concrete; they must be tuned
 * against measured targets (plan section 5) before they mean anything. The party size {@code n} is fixed when the run starts
 * (option b: join during the countdown, no late join), so every quantity here is constant for the whole run.
 *
 * <p>Any {@code n} outside 1..{@link #MAX_PARTY} is clamped, never an exception and never a negative multiplier.
 */
public final class PartyScaling {
    private PartyScaling() {
    }

    /** The owner's target (issue #6: "10+"). One constant. Live tests and measurements use 5; sizes 6 to 10 are covered by the pure check only. */
    public static final int MAX_PARTY = 10;

    /** Placeholder: extra mob health per additional player. */
    static final double MOB_HEALTH_PER_EXTRA = 0.5;
    /** Placeholder: extra boss health per additional player (goes through the same door as the Boss Curse and multiplies with it). */
    static final double BOSS_HEALTH_PER_EXTRA = 0.75;
    /** Placeholder: the spawn interval is divided by {@code 1 + this * (n - 1)}, so more spawn events per minute. */
    static final double SPAWN_RATE_PER_EXTRA = 0.6;

    /** {@code WaveDirector.HOSTILE_CAP} today. */
    static final int SOLO_HOSTILE_CAP = 40;
    /** Placeholder: extra live hostiles per additional player. */
    static final int HOSTILE_CAP_PER_EXTRA = 10;
    /**
     * The cap stops growing at this party size. Measured (PR #43, headless, ONE bot, one sample per cap): 40 hostiles 8.8 ms,
     * 60 hostiles 20.7 ms, 80 hostiles 13.7 ms of a 50 ms tick, so 80 fits with one player. Not measured: 80 hostiles with five
     * players (per-player work) and any client render cost. Growing past 80 toward 130 entities stays off until it is. The value
     * at this size, 80, is still a placeholder.
     */
    static final int HOSTILE_CAP_GROWS_UNTIL = 5;

    /** Clamp to 1..MAX_PARTY. */
    static int clamp(int n) {
        return Math.max(1, Math.min(MAX_PARTY, n));
    }

    /** Multiplies the existing {@code statMultiplier} of every mob. 1.0 for one player. */
    public static double mobHealthMultiplier(int n) {
        return 1.0 + MOB_HEALTH_PER_EXTRA * (clamp(n) - 1);
    }

    /** Multiplies a boss's health, together with (not instead of) the Boss Curse multiplier. 1.0 for one player. */
    public static double bossHealthMultiplier(int n) {
        return 1.0 + BOSS_HEALTH_PER_EXTRA * (clamp(n) - 1);
    }

    /**
     * The spawn interval for a party, from the interval the director would use for one player.
     *
     * @param soloIntervalTicks the interval for one player at the current threat (today's {@code currentSpawnIntervalTicks()})
     * @param floorTicks        the director's own minimum ({@code MIN_SPAWN_INTERVAL_TICKS}, 15 today). A party never pushes the
     *                          interval under it, and a solo interval already below it is returned unchanged.
     * @param n                 party size
     */
    public static int spawnIntervalTicks(int soloIntervalTicks, int floorTicks, int n) {
        int size = clamp(n);
        if (size == 1) {
            return soloIntervalTicks;
        }
        int scaled = (int) Math.round(soloIntervalTicks / (1.0 + SPAWN_RATE_PER_EXTRA * (size - 1)));
        return Math.max(Math.min(soloIntervalTicks, floorTicks), Math.max(1, scaled));
    }

    /** The most hostiles alive at once. 40 for one player (today), growing until {@link #HOSTILE_CAP_GROWS_UNTIL} players, then frozen. */
    public static int hostileCap(int n) {
        int grown = Math.min(clamp(n), HOSTILE_CAP_GROWS_UNTIL);
        return SOLO_HOSTILE_CAP + HOSTILE_CAP_PER_EXTRA * (grown - 1);
    }
}
