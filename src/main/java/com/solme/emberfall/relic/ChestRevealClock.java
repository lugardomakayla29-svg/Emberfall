package com.solme.emberfall.relic;

/**
 * The screen's timing decisions, pure so they can be tested without Minecraft. The screen counts client ticks since it opened; this says which animation
 * tick that is, whether the spin is over, and whether a click may close. The relic was already granted on the server before any of this runs, so nothing
 * here can lose a prize: the clock only decides how long the show lasts.
 */
public final class ChestRevealClock {
    private ChestRevealClock() {}

    /** Ticks the player must wait before a click or key may skip to the end. Short enough not to feel stuck, long enough to see a tier land. */
    public static final int MIN_WATCH_TICKS = ChestReveal.TIER_STOP_TICK;

    /** After the animation ends the screen closes itself this many ticks later, so a player who walks away is never held on it. */
    public static final int AUTO_CLOSE_AFTER_END = 60;

    /** The animation tick for this many screen ticks: never negative, never past the end. */
    public static int animationTick(int screenTicks) {
        return Math.max(0, Math.min(screenTicks, ChestReveal.TOTAL_TICKS));
    }

    /** True once the reels have both stopped and the hold is over. */
    public static boolean finished(int screenTicks) {
        return screenTicks >= ChestReveal.TOTAL_TICKS;
    }

    /** True when the screen should close by itself. */
    public static boolean autoClose(int screenTicks) {
        return screenTicks >= ChestReveal.TOTAL_TICKS + AUTO_CLOSE_AFTER_END;
    }

    /**
     * What a click or key does: {@code SKIP} jumps to the end if it is early enough to be a deliberate skip, {@code CLOSE} closes once the spin is finished,
     * {@code NONE} ignores it (a click in the first moments is almost certainly the one that opened the chest).
     */
    public enum Press { NONE, SKIP, CLOSE }

    public static Press onPress(int screenTicks) {
        if (finished(screenTicks)) {
            return Press.CLOSE;
        }
        return screenTicks >= MIN_WATCH_TICKS ? Press.SKIP : Press.NONE;
    }

    /** The screen tick a skip jumps to: the first tick where both reels are stopped. */
    public static int skipTarget() {
        return ChestReveal.ITEM_STOP_TICK;
    }
}
