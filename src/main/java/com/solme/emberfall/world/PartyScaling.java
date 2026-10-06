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

    /** Placeholder: extra live hostiles per additional player. */
    static final int HOSTILE_CAP_PER_EXTRA = 10;
    /**
     * The cap stops growing at this party size. The server cost of even 40 hostiles with several players has not been
     * measured, so growing the cap up to 10 players (130 entities) is not allowed until the MSPT measurement exists
     * (plan section 4). The value at this size, 80, is itself a placeholder.
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
     *                          interval under it, and a solo interval already at or below it is returned unchanged, for any {@code n}
     *                          and for any input including 0 or less (the director never passes one; this just defines it).
     * @param n                 party size
     */
    public static int spawnIntervalTicks(int soloIntervalTicks, int floorTicks, int n) {
        if (soloIntervalTicks <= floorTicks) {
            return soloIntervalTicks; // nothing to speed up; also what makes n = 1 return the input for every value at or below the floor
        }
        int scaled = (int) Math.round(soloIntervalTicks / (1.0 + SPAWN_RATE_PER_EXTRA * (clamp(n) - 1)));
        return Math.max(floorTicks, scaled);
    }

    /**
     * The most hostiles alive at once: {@code soloCap} for one player, growing until {@link #HOSTILE_CAP_GROWS_UNTIL} players,
     * then frozen.
     *
     * @param soloCap the director's own cap for one player ({@code WaveDirector.HOSTILE_CAP}, 40 today). Passed in rather than copied
     *                here, so this class cannot drift from the director (Koda, issue #13).
     * @param n       party size
     */
    public static int hostileCap(int soloCap, int n) {
        int grown = Math.min(clamp(n), HOSTILE_CAP_GROWS_UNTIL);
        return soloCap + HOSTILE_CAP_PER_EXTRA * (grown - 1);
    }
}
