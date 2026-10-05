package com.solme.emberfall.hub;

/**
 * The Expedition Gate's rules as PURE numbers, so each is provable without a server.
 *
 * The gate replaces the old departure plate. Three rules together end the plate loop: (1) a run starts only on a deliberate
 * right click, never on stepping; (2) a returning player is put BESIDE the gate, never on it; (3) for a short time after a
 * run ends, starting another is refused, so even a stray click cannot restart at once.
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

    /** The two floor cells beside the gate at (0,-1): (-1,-1) and (1,-1), each clear of every bust and the gate. */
    public static HubLayout.Spot[] returnSpots() {
        return new HubLayout.Spot[] {
                new HubLayout.Spot(-1, -1, 0.0F),
                new HubLayout.Spot(1, -1, 0.0F)
        };
    }

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

    /** The return spot a player lands on: alternates by a stable index so two players do not stack. */
    public static HubLayout.Spot returnSpot(int playerIndex) {
        HubLayout.Spot[] s = returnSpots();
        return s[Math.floorMod(playerIndex, s.length)];
    }
}
