package com.solme.emberfall.relic;

import java.util.Collection;

/**
 * Wither Crown's price: the run grows more dangerous. PURE so it can be proven without a server.
 *
 * A party's relic threat is NOT the sum of every member's crowns, because then a 10 player party with one crown each would add 40 threat
 * (double the cap) and the crown would be unusable in a group. It is the HIGHEST single player's bonus plus a quarter of the rest, so one
 * greedy player raises the danger for everybody a little, and more crowns still matter, but it stays bounded.
 */
public final class RelicThreat {
    private RelicThreat() {}

    /** The share of every other player's threat that still counts. */
    public static final double OTHERS_SHARE = 0.25;
    /** Hard ceiling on what relics alone may add to the threat scalar (the director's own cap is 20). */
    public static final double MAX_RELIC_THREAT = 12.0;

    /** Threat the relics of one party add to the run. Empty, zero or negative input gives 0. */
    public static double partyThreat(Collection<Double> perPlayer) {
        double max = 0.0, sum = 0.0;
        for (Double d : perPlayer) {
            if (d == null || !(d > 0.0)) {
                continue;
            }
            max = Math.max(max, d);
            sum += d;
        }
        return Math.min(MAX_RELIC_THREAT, max + OTHERS_SHARE * (sum - max));
    }
}
