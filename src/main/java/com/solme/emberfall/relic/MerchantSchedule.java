package com.solme.emberfall.relic;

/**
 * When merchants come and go, as PURE numbers so the timing is provable without a server.
 *
 * One merchant at a time. The first arrives {@link #FIRST_AT} seconds into the run; after one leaves, the next arrives
 * {@link #GAP} seconds later. A merchant waits {@link #STAY} seconds for a buyer, then leaves disappointed.
 * No merchant arrives in the last {@link #QUIET_END} seconds of a run that has a known end, and never after the final
 * swarm starts (the caller passes {@code swarm = true}).
 */
public final class MerchantSchedule {
    private MerchantSchedule() {}

    public static final int FIRST_AT = 120;
    public static final int GAP = 180;
    public static final int STAY = 60;
    public static final int QUIET_END = 30;
    /** A merchant never appears nearer than this to a player, so arriving on top of someone is impossible. */
    public static final double MIN_PLAYER_DISTANCE = 10.0;
    /** ... nor nearer than this to an unopened chest, so the two never share a spot. */
    public static final double MIN_CHEST_DISTANCE = 6.0;

    /**
     * Whether a merchant should arrive now.
     *
     * @param elapsed      seconds since the run began
     * @param leftAt       the elapsed second the previous merchant left, or -1 if none has yet
     * @param present      whether a merchant is currently standing
     * @param runEndsAt    the elapsed second the run must end by, or -1 for an open-ended run
     * @param swarm        true once the final swarm has begun
     */
    public static boolean shouldArrive(long elapsed, long leftAt, boolean present, long runEndsAt, boolean swarm) {
        if (present || swarm) {
            return false;
        }
        if (runEndsAt >= 0 && elapsed >= runEndsAt - QUIET_END) {
            return false;
        }
        return leftAt < 0 ? elapsed >= FIRST_AT : elapsed >= leftAt + GAP;
    }

    /** Seconds a standing merchant has left, never negative. */
    public static int secondsLeft(long elapsed, long arrivedAt) {
        return (int) Math.max(0, STAY - (elapsed - arrivedAt));
    }

    /** Whether a standing merchant has waited long enough to leave unhappy. */
    public static boolean expired(long elapsed, long arrivedAt) {
        return elapsed - arrivedAt >= STAY;
    }
}
