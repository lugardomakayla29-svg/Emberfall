package com.solme.emberfall.bot;

import java.util.List;

/**
 * Steering that keeps a party of EmberTesters from collapsing into one blob, free of engine types so it can be proven on its own.
 *
 * <p>Before this, every bot picked the same nearest foe and the same stand-off point, with no idea of the others, so a party sat
 * on the same coordinates (measured live: the closest pair was 0.00 blocks apart in 13 of 14 samples). Two ideas from Reynolds'
 * steering behaviours fix that: <b>separation</b> (push away from allies that are too close) and a <b>personal slot</b> (each
 * bot approaches a foe from its own bearing instead of from the common direction).
 */
public final class BotSteer {
    private BotSteer() {}

    /** A neighbour: where it stands. */
    public record Mate(double x, double z) {}

    /** A sideways-and-forward push in blocks, to be added to a goal. */
    public record Push(double dx, double dz) {}

    /**
     * Separation: the sum of pushes away from every ally closer than {@code radius}, each stronger the closer it is, capped at
     * {@code maxPush} blocks. An ally exactly on top of the bot (distance 0) pushes along {@code fallbackBearing}, so the push
     * never has an undefined direction.
     */
    public static Push separation(double x, double z, List<Mate> mates, double radius, double maxPush, double fallbackBearing) {
        double px = 0.0;
        double pz = 0.0;
        for (Mate m : mates) {
            double dx = x - m.x();
            double dz = z - m.z();
            double d = Math.hypot(dx, dz);
            if (d >= radius) {
                continue;
            }
            double strength = (radius - d) / radius; // 1 on top, 0 at the edge
            if (d < 1.0e-6) {
                px += Math.cos(fallbackBearing) * strength;
                pz += Math.sin(fallbackBearing) * strength;
            } else {
                px += dx / d * strength;
                pz += dz / d * strength;
            }
        }
        double len = Math.hypot(px, pz);
        if (len < 1.0e-9) {
            return new Push(0.0, 0.0);
        }
        double mag = Math.min(maxPush, len * radius);
        return new Push(px / len * mag, pz / len * mag);
    }

    /**
     * The point a bot should stand at around a foe: {@code standOff} blocks from the foe, on the bot's own bearing
     * {@code slotBearing} (radians) instead of on the line to the bot. Four bots with four bearings stand at four different points.
     */
    public static double[] slotPoint(double foeX, double foeZ, double standOff, double slotBearing) {
        return new double[] {foeX + Math.cos(slotBearing) * standOff, foeZ + Math.sin(slotBearing) * standOff};
    }

    /**
     * A stable bearing for a bot: its index in the sorted party spread evenly round the circle, plus a small seeded wobble, so two
     * parties of the same size still look different. {@code count} is the party size.
     */
    public static double slotBearing(int index, int count, double wobbleRadians) {
        int n = Math.max(1, count);
        return (Math.PI * 2.0) * (Math.floorMod(index, n)) / n + wobbleRadians;
    }

    /**
     * Foe choice with a crowding cost: each ally already heading for a foe adds {@code claimPenalty} blocks to its score, so a
     * party spreads over several foes instead of all picking the nearest. A bold bot likes a foe with company (a bigger fight), a
     * timid one avoids it. Lower score wins. Returns -1 when nothing is in sight.
     */
    public static int pickFoe(double x, double z, List<BotPlan.Foe> foes, int[] claims, double sight, double boldness, double claimPenalty) {
        int best = -1;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < foes.size(); i++) {
            BotPlan.Foe f = foes.get(i);
            double d = Math.hypot(f.x() - x, f.z() - z);
            if (d > sight) {
                continue;
            }
            double crowd = (0.5 - boldness) * 2.0 * f.neighbours();
            double claimed = (claims != null && i < claims.length ? claims[i] : 0) * claimPenalty;
            double score = d + crowd + claimed;
            if (score < bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }
}
