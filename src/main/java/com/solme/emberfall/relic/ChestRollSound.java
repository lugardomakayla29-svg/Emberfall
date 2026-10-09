package com.solme.emberfall.relic;

/**
 * WHEN the chest roll makes a sound, as pure arithmetic, so a test can count the calls without Minecraft. The screen asks once per client tick
 * {@link #cues} with the screen tick it was at and the one it is at now, and plays what comes back. Which vanilla sound each cue is lives in the
 * client screen, not here.
 *
 * Rules:
 * - one {@link Cue#TICK} for each time a reel CHANGES the symbol it shows (a reel step), tier reel and item reel alike;
 * - one {@link Cue#TIER_LANDS} on the tick the tier reel stops, one {@link Cue#ITEM_LANDS} on the tick the item reel stops;
 * - a skip (a click jumps the screen tick forward by many ticks) plays NO ticks for the steps it jumped over, only the landing cues that
 *   the jump crossed, so a skip is quiet except for the answer;
 * - a screen that opens already finished (no spin to show) plays nothing;
 * - nothing is played twice: a cue belongs to exactly one tick boundary.
 *
 * Tested headless. Nothing here was heard; how it sounds is the owner's call.
 */
public final class ChestRollSound {
    private ChestRollSound() {}

    public enum Cue { TICK, TIER_LANDS, ITEM_LANDS }

    /** The three-cue result of one client tick: how many reel steps, and whether each reel landed on this boundary. */
    public record Cues(int ticks, boolean tierLands, boolean itemLands) {
        public static final Cues NONE = new Cues(0, false, false);
        /** Total sound calls this result asks for. */
        public int calls() { return ticks + (tierLands ? 1 : 0) + (itemLands ? 1 : 0); }
    }

    private static final int ITEM_START = ChestReveal.TIER_STOP_TICK + ChestReveal.GAP_TICKS;

    /** Which decoy a reel shows at this animation tick, or -1 while hidden/stopped (those show no moving symbol). */
    static int shown(int animTick, int start, int stop) {
        int t = ChestRevealClock.animationTick(animTick);
        if (t < start || t >= stop) {
            return -1;
        }
        return ChestReveal.symbolAt(t - start, stop - start);
    }

    /**
     * The cues for the move from screen tick {@code from} to screen tick {@code to}. {@code to <= from} (no time passed, or time went backwards)
     * asks for nothing. A step of exactly one tick is the normal case; anything larger is a skip.
     */
    public static Cues cues(int from, int to) {
        int a = ChestRevealClock.animationTick(from);
        int b = ChestRevealClock.animationTick(to);
        if (to <= from || b <= a) {
            return Cues.NONE;
        }
        boolean skip = (to - from) > 1;
        boolean tierLands = a < ChestReveal.TIER_STOP_TICK && b >= ChestReveal.TIER_STOP_TICK;
        boolean itemLands = a < ChestReveal.ITEM_STOP_TICK && b >= ChestReveal.ITEM_STOP_TICK;
        int ticks = 0;
        if (!skip) {
            int tierBefore = shown(a, 0, ChestReveal.TIER_STOP_TICK), tierNow = shown(b, 0, ChestReveal.TIER_STOP_TICK);
            if (tierNow >= 0 && tierBefore >= 0 && tierNow != tierBefore) {
                ticks++;
            }
            int itemBefore = shown(a, ITEM_START, ChestReveal.ITEM_STOP_TICK), itemNow = shown(b, ITEM_START, ChestReveal.ITEM_STOP_TICK);
            if (itemNow >= 0 && itemBefore >= 0 && itemNow != itemBefore) {
                ticks++;
            }
        }
        return new Cues(ticks, tierLands, itemLands);
    }

    /** Every cue over a whole watch of {@code lastTick} client ticks with no skip, for tests and for the table in the PR. */
    public static Cues total(int lastTick) {
        int t = 0;
        boolean tier = false, item = false;
        for (int s = 0; s < lastTick; s++) {
            Cues c = cues(s, s + 1);
            t += c.ticks();
            tier |= c.tierLands();
            item |= c.itemLands();
        }
        return new Cues(t, tier, item);
    }
}
