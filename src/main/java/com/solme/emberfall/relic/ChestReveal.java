package com.solme.emberfall.relic;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The slot-machine reveal of a chest as PURE DATA (GAME_PLAN row 2.6: "tier first, then item"). The server has already decided the result in
 * {@link ChestOpening#open}: it rolls the TIER first, then picks a relic WITHIN that tier. This class only decides how that known answer is SHOWN:
 * two reels that spin and stop, the tier reel first, then the item reel. A client screen draws {@link #frame}; it contains no rules of its own.
 *
 * The properties that matter, all checked in RevealCheck: both reels end on the TRUE tier and the TRUE item and never on a decoy; the item reel is
 * hidden until the tier reel has stopped; a reel never shows a symbol outside its pool; the true answer is not shown early (no spoiler); and the whole
 * thing is finite and fully determined by (answer, pools, seed), so a packet needs to carry nothing but those.
 *
 * Timing and symbol counts are PROPOSALS: nothing here was seen or heard, and how a reveal FEELS cannot be judged headless.
 */
public final class ChestReveal {
    private ChestReveal() {}

    /** The tier reel starts spinning at tick 0 and has fully stopped by this tick. 2 seconds. PROPOSAL. */
    public static final int TIER_STOP_TICK = 40;
    /** A pause between the tier locking and the item reel starting, so the tier is read first. 0.5 seconds. PROPOSAL. */
    public static final int GAP_TICKS = 10;
    /** The item reel starts at TIER_STOP_TICK + GAP_TICKS and has fully stopped this many ticks later. 3 seconds. PROPOSAL. */
    public static final int ITEM_SPIN_TICKS = 60;
    /** The item reel's stop tick. */
    public static final int ITEM_STOP_TICK = TIER_STOP_TICK + GAP_TICKS + ITEM_SPIN_TICKS;
    /** The whole reveal, then the screen may be dismissed. A short hold after the item lands so it can be read. PROPOSAL: 1.5 seconds. */
    public static final int HOLD_TICKS = 30;
    public static final int TOTAL_TICKS = ITEM_STOP_TICK + HOLD_TICKS;
    /** A symbol changes every this many ticks while a reel is still fast; the last ticks slow down (see {@link #symbolAt}). */
    public static final int FAST_STEP = 2;

    /** What one reel shows at one moment. */
    public enum State {
        /** The reel is not on screen yet (the item reel before the tier has locked). It shows no symbol at all. */
        HIDDEN,
        /** The reel is spinning: the symbol is a decoy, never the answer. */
        SPINNING,
        /** The reel has stopped on the true answer. */
        STOPPED
    }

    /** One reel's state and the symbol it shows (null when hidden). */
    public record Reel(State state, String symbol) {}

    /** Both reels at one tick. */
    public record Frame(Reel tier, Reel item) {}

    /**
     * The reveal for a known answer. {@code tierPool} is the list of tier labels the tier reel may scroll past (it must contain {@code trueTier});
     * {@code itemPool} is the list of item names the item reel may scroll past (it must contain {@code trueItem}). {@code seed} makes the decoy order.
     */
    public static final class Reveal {
        private final String trueTier;
        private final String trueItem;
        private final List<String> tierDecoys;
        private final List<String> itemDecoys;

        Reveal(String trueTier, String trueItem, List<String> tierDecoys, List<String> itemDecoys) {
            this.trueTier = trueTier;
            this.trueItem = trueItem;
            this.tierDecoys = tierDecoys;
            this.itemDecoys = itemDecoys;
        }

        public String trueTier() {
            return trueTier;
        }

        public String trueItem() {
            return trueItem;
        }

        /** The decoys the tier reel cycles through, in order. Never contains the true tier. */
        public List<String> tierDecoys() {
            return tierDecoys;
        }

        /** The decoys the item reel cycles through, in order. Never contains the true item. */
        public List<String> itemDecoys() {
            return itemDecoys;
        }

        /** What both reels show at {@code tick}. Ticks below 0 act as 0; ticks past the end act as the last tick. */
        public Frame frame(int tick) {
            int t = Math.max(0, Math.min(tick, TOTAL_TICKS));
            return new Frame(reel(t, 0, TIER_STOP_TICK, trueTier, tierDecoys), reel(t, TIER_STOP_TICK + GAP_TICKS, ITEM_STOP_TICK, trueItem, itemDecoys));
        }

        private static Reel reel(int t, int start, int stop, String truth, List<String> decoys) {
            if (t < start) {
                return new Reel(State.HIDDEN, null);
            }
            if (t >= stop) {
                return new Reel(State.STOPPED, truth);
            }
            if (decoys.isEmpty()) {
                // Nothing to scroll past (a pool of one): it spins on a blank rather than showing the answer early.
                return new Reel(State.SPINNING, "");
            }
            return new Reel(State.SPINNING, decoys.get(symbolAt(t - start, stop - start) % decoys.size()));
        }
    }

    /**
     * Which decoy index a reel shows {@code elapsed} ticks into a spin of {@code length} ticks. Fast and steady for the first two thirds, then it
     * slows: the step grows, so the last symbols hang on screen longer, like a real reel running out of speed. Never decreases.
     */
    public static int symbolAt(int elapsed, int length) {
        if (elapsed <= 0) {
            return 0;
        }
        int fastEnd = length * 2 / 3;
        if (elapsed <= fastEnd) {
            return elapsed / FAST_STEP;
        }
        int base = fastEnd / FAST_STEP;
        int slow = elapsed - fastEnd;
        int step = FAST_STEP * 3;
        return base + slow / step;
    }

    /**
     * Builds a reveal. Throws {@link IllegalArgumentException} if the answer is not in its pool, or a pool is empty, because a reveal that cannot
     * land on the truth must fail loudly rather than quietly land on something else. The decoys are the pool minus the answer, shuffled by the seed.
     */
    public static Reveal build(String trueTier, String trueItem, List<String> tierPool, List<String> itemPool, long seed) {
        if (trueTier == null || trueItem == null || tierPool == null || itemPool == null) {
            throw new IllegalArgumentException("null argument");
        }
        if (!tierPool.contains(trueTier)) {
            throw new IllegalArgumentException("tier '" + trueTier + "' is not in the tier pool");
        }
        if (!itemPool.contains(trueItem)) {
            throw new IllegalArgumentException("item '" + trueItem + "' is not in the item pool");
        }
        Random r = new Random(seed);
        return new Reveal(trueTier, trueItem, decoys(trueTier, tierPool, r), decoys(trueItem, itemPool, r));
    }

    /** The pool with every copy of the answer removed, de-duplicated, then shuffled. Order of equal pools and seeds is the same every time. */
    static List<String> decoys(String truth, List<String> pool, Random r) {
        List<String> out = new ArrayList<>();
        for (String s : pool) {
            if (!s.equals(truth) && !out.contains(s)) {
                out.add(s);
            }
        }
        for (int i = out.size() - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            String tmp = out.get(i);
            out.set(i, out.get(j));
            out.set(j, tmp);
        }
        return out;
    }
}
