package com.solme.emberfall.rift;

/**
 * The shared entry rules of a Rift (countdown, lockout, party window) as PURE numbers, so each is provable without a server.
 *
 * Two rules end the old plate loop: a run starts only on a deliberate click, never on stepping; and for a short time after a run
 * ends, starting another is refused, so even a stray click cannot restart at once. {@link RiftRules} and {@link RiftGate} reuse
 * these and never copy them.
 */
public final class GateRules {
    private GateRules() {}

    /** Seconds of countdown between confirming and the run starting; moving away cancels it. */
    public static final int COUNTDOWN_SECONDS = 3;
    /** Ticks after a run ends during which a new start is refused. */
    public static final int LOCKOUT_TICKS = 200;
    /** How far (blocks) a player may wander from where they confirmed before the countdown cancels. */
    public static final double CANCEL_DISTANCE = 2.5;
    /** Max distance (blocks) a player may click the gate from. */
    public static final double REACH = 4.0;

    /** True while a start must be refused: a run ended {@code sinceEndTicks} ago (negative = never ended). */
    public static boolean lockedOut(long sinceEndTicks) {
        return sinceEndTicks >= 0 && sinceEndTicks < LOCKOUT_TICKS;
    }

    /** Whole seconds left in a countdown that began {@code elapsedTicks} ago, 0 when done. */
    public static int secondsLeft(long elapsedTicks) {
        return (int) Math.max(0, COUNTDOWN_SECONDS - (Math.max(0, elapsedTicks) / 20));
    }

    public static boolean countdownDone(long elapsedTicks) {
        return elapsedTicks >= COUNTDOWN_SECONDS * 20L;
    }

    /** True when the player moved far enough from where they confirmed to cancel the countdown. */
    public static boolean moved(double dx, double dz) {
        return dx * dx + dz * dz > CANCEL_DISTANCE * CANCEL_DISTANCE;
    }

    /** Largest party one gate departure can carry; matches {@code PartyScaling.MAX_PARTY}. */
    public static final int MAX_PARTY = 10;

    /**
     * Whether a player may join the departure already counting down. {@code members} is how many are already in it,
     * {@code sinceFirstTicks} how long ago the first one clicked. Joining is allowed only while the clock is still running, so
     * nobody is pulled into a run that is about to start, and only below the party cap.
     */
    public static boolean canJoin(int members, long sinceFirstTicks) {
        return members >= 1 && members < MAX_PARTY && sinceFirstTicks >= 0 && !countdownDone(sinceFirstTicks);
    }

    /**
     * A member who joined late must still hold still for a fair moment, but the departure does not wait for them: it leaves when
     * the FIRST click's countdown ends. This is the whole group's start rule: true once the first click's clock is done.
     */
    public static boolean groupDeparts(long sinceFirstTicks) {
        return countdownDone(sinceFirstTicks);
    }
}
