package com.solme.emberfall.bot;

import java.util.List;

/**
 * Where an EmberTester wants to be. Pure maths on plain numbers, so every rule is provable without a server.
 *
 * <p>The game's auto-attack does the fighting; the bot only has to stand where its weapons reach. So the whole decision
 * is "which foe, and how close".
 */
public final class BotPlan {
    private BotPlan() {}

    /** A foe the bot can see: where it is, and how many other foes stand within 6 blocks of it. */
    public record Foe(double x, double z, int neighbours) {}

    /** A chosen goal: a point to walk to, or none. */
    public record Goal(double x, double z, boolean hasFoe) {}

    /** Walking distance kept to the chosen foe: 0.8 of the shortest weapon reach, never under 1.5 blocks (no hugging). */
    public static double standOff(double shortestReach) {
        return Math.max(1.5, shortestReach * 0.8);
    }

    /**
     * Picks the foe to walk at: the lowest {@code distance - 1.5 * neighbours} (a crowd is worth a longer walk, which is what
     * splash and area weapons want), among foes within {@code sight} blocks. Returns the index, or -1 when none is in sight.
     */
    public static int pickFoe(double x, double z, List<Foe> foes, double sight) {
        int best = -1;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < foes.size(); i++) {
            Foe f = foes.get(i);
            double d = Math.hypot(f.x() - x, f.z() - z);
            if (d > sight) {
                continue;
            }
            double score = d - 1.5 * f.neighbours();
            if (score < bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    /**
     * The point to walk to for a foe: on the line from the foe toward the bot, {@code standOff} blocks from the foe. If the
     * bot is already inside that distance it stays where it is (no step into the crowd).
     */
    public static Goal goalFor(double x, double z, Foe foe, double standOff) {
        double dx = x - foe.x();
        double dz = z - foe.z();
        double d = Math.hypot(dx, dz);
        if (d <= standOff) {
            return new Goal(x, z, true);
        }
        double f = standOff / d;
        return new Goal(foe.x() + dx * f, foe.z() + dz * f, true);
    }

    /** With no foe in sight: walk toward the run's centre until within {@code arrive} blocks of it, then hold. */
    public static Goal wander(double x, double z, double centreX, double centreZ, double arrive) {
        double d = Math.hypot(centreX - x, centreZ - z);
        return d <= arrive ? new Goal(x, z, false) : new Goal(centreX, centreZ, false);
    }

    /** True when the bot should ask for a new route: every {@code every} ticks, or the goal moved more than {@code slack}. */
    public static boolean needsReplan(long ticksSincePlan, long every, double goalMovedBlocks, double slack) {
        return ticksSincePlan >= every || goalMovedBlocks > slack;
    }

    /**
     * Whether a bot should stop fighting and walk to a standing merchant: only when it could actually pay for the cheapest
     * thing on offer and still keep its reserve. A broke bot ignores the merchant, so it never queues at a stall for nothing.
     * {@code cheapestPrice} below zero means the stall has not been seen yet, so the bot goes once to look when it has at least
     * {@code lookGold} gold.
     */
    public static boolean shouldVisitMerchant(boolean merchantStanding, long gold, long cheapestPrice, long reserve, long lookGold) {
        if (!merchantStanding) {
            return false;
        }
        if (cheapestPrice < 0) {
            return gold >= lookGold;
        }
        return gold - reserve >= cheapestPrice;
    }

    /**
     * Whether a bot should walk to the Challenge Shrine: it is free, pays gold, XP and Silver, and failing costs nothing, so a
     * tester always takes it once. It is skipped while the bot is already in a shrine fight (the shrine is "busy"), when it was
     * already used this run, or when the bot is too hurt to take on guardians. The Curse and the Statue of Greed are never
     * visited: they make the bot's own run harder, which a tester must not do on its own.
     */
    public static boolean shouldVisitShrine(boolean challengeUnused, boolean challengeBusy, double healthFraction, double minHealth) {
        return challengeUnused && !challengeBusy && healthFraction >= minHealth;
    }
}
