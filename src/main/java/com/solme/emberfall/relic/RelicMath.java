package com.solme.emberfall.relic;

import java.util.function.DoubleSupplier;

/**
 * Pure relic economy maths: no Minecraft types, so a standalone check can prove every number.
 *
 * Luck: each point moves a share of the COMMON weight up the ladder. The shift is capped so COMMON keeps at least
 * {@link #COMMON_FLOOR} of its weight and no tier ever reaches zero ("a legendary is possible at any luck").
 * Chest price: {@code BASE_PRICE * GROWTH^opened}, rounded. These two constants are the design's own placeholders
 * (not Megabonk's numbers) and are the only knobs a balance pass needs to turn.
 */
public final class RelicMath {
    private RelicMath() {}

    public static final int BASE_PRICE = 30;
    public static final double GROWTH = 1.25;
    /** Luck is clamped to this range before use. */
    public static final double MAX_LUCK = 100.0;
    /** Share of COMMON weight that can never be moved away, so the ladder never collapses. */
    public static final double COMMON_FLOOR = 0.15;
    /** Key relic: chance per stack and the cap. */
    public static final double KEY_PER_STACK = 0.10;
    public static final double KEY_CAP = 0.50;

    /**
     * Price of the next paid chest after {@code opened} paid openings. Never below the base, never overflows.
     * Measured: passes 2 million at opening 50 and reaches the int ceiling at opening 82, where it holds. No run gets
     * near that (opening 30 already costs 24,234 Gold), so the ceiling is a safety net, not a design limit.
     */
    public static int chestPrice(int opened) {
        if (opened <= 0) {
            return BASE_PRICE;
        }
        double price = BASE_PRICE * Math.pow(GROWTH, opened);
        return price >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.round(price);
    }

    /** Tier weights (COMMON, UNCOMMON, RARE, LEGENDARY) at the given luck. They always sum to 100. */
    public static double[] weights(double luck) {
        double l = Math.max(0.0, Math.min(MAX_LUCK, luck)) / MAX_LUCK; // 0..1
        double common = RelicRarity.COMMON.baseWeight();
        double moved = common * (1.0 - COMMON_FLOOR) * l;
        // The moved weight is handed up the ladder in proportion 1 : 2 : 4 so the top tiers grow fastest in relative terms.
        double w1 = moved * 1.0 / 7.0;
        double w2 = moved * 2.0 / 7.0;
        double w3 = moved * 4.0 / 7.0;
        return new double[] {
                common - moved,
                RelicRarity.UNCOMMON.baseWeight() + w1,
                RelicRarity.RARE.baseWeight() + w2,
                RelicRarity.LEGENDARY.baseWeight() + w3
        };
    }

    /** Rolls a tier. {@code roll} returns a uniform value in [0,1). */
    public static RelicRarity rollRarity(double luck, DoubleSupplier roll) {
        double[] w = weights(luck);
        double total = w[0] + w[1] + w[2] + w[3];
        double r = roll.getAsDouble() * total;
        for (int i = 0; i < 4; i++) {
            r -= w[i];
            if (r < 0.0) {
                return RelicRarity.values()[i];
            }
        }
        return RelicRarity.LEGENDARY;
    }

    /** Chance a paid chest opens free with this many Key stacks. */
    public static double keyChance(int stacks) {
        return Math.min(KEY_CAP, Math.max(0, stacks) * KEY_PER_STACK);
    }

    /**
     * Scales a whole-number reward by a multiplier WITHOUT losing the fraction. The whole part is always paid; the
     * fractional part is paid as one extra with that probability. The average over many rolls is exactly
     * {@code amount * multiplier}, so +15% Gold still means something when a kill drops a single coin
     * (plain rounding would turn 1 x 1.15 into 1 every time). {@code roll} is uniform in [0,1).
     */
    public static int scaleWhole(int amount, double multiplier, DoubleSupplier roll) {
        if (amount <= 0 || multiplier <= 0.0) {
            return 0;
        }
        double exact = amount * multiplier;
        int whole = (int) Math.floor(exact);
        double frac = exact - whole;
        return frac > 0.0 && roll.getAsDouble() < frac ? whole + 1 : whole;
    }
}
