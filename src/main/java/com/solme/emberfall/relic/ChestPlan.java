package com.solme.emberfall.relic;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Where a run's chests go, as a PURE function of the candidate spots and a random, so it is provable without a server.
 *
 * Spots are shuffled, then taken greedily while keeping a minimum gap between any two chests (plain random picks clump three
 * chests in one corner). If the gap cannot be kept for the requested count, the gap is relaxed step by step rather than
 * placing fewer chests than the design wants, and never below {@link #MIN_GAP}.
 */
public final class ChestPlan {
    private ChestPlan() {}

    /** Chests a run starts with: enough to spend gold on, few enough that finding one still matters. */
    public static final int PAID_CHESTS = 14;
    /** Gold chests are rare: one or two per run. */
    public static final int GOLD_CHESTS = 2;
    /** Preferred and smallest allowed distance between any two chests. */
    public static final double PREFERRED_GAP = 22.0;
    public static final double MIN_GAP = 8.0;

    public record Spot(int x, int y, int z, ChestOpening.Kind kind) {}

    /** Picks {@code paid + gold} spots. Gold chests take the FIRST picks so they get the best-spread positions. */
    public static List<Spot> plan(List<int[]> candidates, int paid, int gold, Random random) {
        List<int[]> pool = new ArrayList<>(candidates);
        java.util.Collections.shuffle(pool, random);
        int want = Math.max(0, paid) + Math.max(0, gold);
        List<int[]> chosen = new ArrayList<>();
        for (double gap = PREFERRED_GAP; gap >= MIN_GAP - 1e-9 && chosen.size() < want; gap -= 2.0) {
            for (int[] p : pool) {
                if (chosen.size() >= want) {
                    break;
                }
                if (chosen.contains(p)) {
                    continue;
                }
                boolean ok = true;
                for (int[] c : chosen) {
                    if (Math.hypot(p[0] - c[0], p[2] - c[2]) < gap) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    chosen.add(p);
                }
            }
        }
        List<Spot> out = new ArrayList<>();
        for (int i = 0; i < chosen.size(); i++) {
            int[] p = chosen.get(i);
            out.add(new Spot(p[0], p[1], p[2], i < gold ? ChestOpening.Kind.GOLD : ChestOpening.Kind.PAID));
        }
        return out;
    }
}
