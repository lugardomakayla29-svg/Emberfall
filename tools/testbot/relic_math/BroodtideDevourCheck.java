import com.solme.emberfall.boss.BroodtideDevour;
import com.solme.emberfall.boss.BroodtideGrab.Phase;
import com.solme.emberfall.boss.TideClock;

/**
 * The Devour's rules (docs/PLAN_broodtide.md section 3), checked against the PLAN, not against the constants' values: the cap is 6, an eat starts only in Ebb and its
 * wind-up must finish inside that Ebb, a swallowed mob is spat in Flood and never the instant it is eaten and never kept forever.
 */
public class BroodtideDevourCheck {
    static int fails = 0, total = 0;

    static void check(String l, boolean ok, String e) {
        total++;
        System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e);
        if (!ok) {
            fails++;
        }
    }

    public static void main(String[] a) {
        final int CYCLE = TideClock.CYCLE_TICKS;
        final int W = BroodtideDevour.WINDUP_TICKS;

        check("D1 the plan's cap is 6 Brood-Kin", BroodtideDevour.BROOD_KIN_CAP == 6, "cap=" + BroodtideDevour.BROOD_KIN_CAP);
        check("D2 the wind-up is at least 0.9 s (18 ticks), the plan's telegraph floor", W >= 18, "windup=" + W);

        // ---- mayStartEat ----
        // find an Ebb tick with plenty of Ebb left, and the last tick where the wind-up still fits
        long early = -1;
        for (long t = 0; t < CYCLE; t++) {
            if (TideClock.stateAt(t) == TideClock.State.EBB && TideClock.ticksLeft(t) > W + 5) {
                early = t;
                break;
            }
        }
        check("D3 an eat may start early in Ebb with a free slot and no cooldown", early >= 0 && BroodtideDevour.mayStartEat(early, -1, Phase.ONE, false, 0), "tick=" + early);

        boolean floodRefused = true;
        for (long t = 0; t < 2L * CYCLE; t++) {
            if (TideClock.stateAt(t) == TideClock.State.FLOOD && BroodtideDevour.mayStartEat(t, -1, Phase.THREE, false, 0)) {
                floodRefused = false;
            }
        }
        check("D4 an eat never starts in Flood, in any phase, over two full cycles", floodRefused, "");

        boolean fits = true;
        long worst = -1;
        for (long t = 0; t < 3L * CYCLE; t++) {
            if (BroodtideDevour.mayStartEat(t, -1, Phase.THREE, false, 0) && TideClock.ticksLeft(t) <= W) {
                fits = false;
                worst = t;
            }
        }
        check("D5 whenever an eat may start, its wind-up finishes inside the same Ebb (never lands in Flood)", fits, "worst=" + worst);

        // the boundary itself: exactly W ticks left must refuse, W+1 left must allow (if such a tick exists and the cooldown is clear)
        long atW = -1, atW1 = -1;
        for (long t = 0; t < CYCLE; t++) {
            if (TideClock.stateAt(t) == TideClock.State.EBB) {
                if (TideClock.ticksLeft(t) == W) atW = t;
                if (TideClock.ticksLeft(t) == W + 1) atW1 = t;
            }
        }
        check("D6 boundary: with exactly the wind-up left it refuses, with one tick more it allows",
                atW >= 0 && atW1 >= 0 && !BroodtideDevour.mayStartEat(atW, -1, Phase.ONE, false, 0) && BroodtideDevour.mayStartEat(atW1, -1, Phase.ONE, false, 0), "atW=" + atW + " atW+1=" + atW1);

        check("D7 the cap blocks an eat: 5 alive allows, 6 alive refuses",
                BroodtideDevour.mayStartEat(early, -1, Phase.ONE, false, 5) && !BroodtideDevour.mayStartEat(early, -1, Phase.ONE, false, 6) && !BroodtideDevour.mayStartEat(early, -1, Phase.ONE, false, 99), "");
        check("D8 an eat already running blocks another", !BroodtideDevour.mayStartEat(early, -1, Phase.ONE, true, 0), "");

        // cooldown: just before refuses, exactly at allows, per phase
        boolean cd = true;
        StringBuilder note = new StringBuilder();
        for (Phase p : Phase.values()) {
            int c = BroodtideDevour.cooldownTicks(p);
            long start = early;
            // look for a later Ebb tick exactly c after 'start' and c-1 after; skip phases where it lands in Flood
            long t1 = start + c - 1, t2 = start + c;
            if (TideClock.stateAt(t2) == TideClock.State.EBB && TideClock.ticksLeft(t2) > W) {
                boolean ok = !BroodtideDevour.mayStartEat(t1, start, p, false, 0) && BroodtideDevour.mayStartEat(t2, start, p, false, 0);
                cd &= ok;
                note.append(p).append("=").append(ok ? "ok " : "BAD ");
            } else {
                note.append(p).append("=skipped(Flood) ");
            }
        }
        check("D9 the cooldown refuses one tick early and allows exactly on time (phases that land in Ebb)", cd, note.toString());
        check("D10 the eat comes faster as the fight goes on", BroodtideDevour.cooldownTicks(Phase.ONE) > BroodtideDevour.cooldownTicks(Phase.TWO) && BroodtideDevour.cooldownTicks(Phase.TWO) > BroodtideDevour.cooldownTicks(Phase.THREE), "");

        // ---- isEdible ----
        double R = BroodtideDevour.REACH;
        check("D11 a living horde mob inside reach is edible", BroodtideDevour.isEdible(true, true, false, false, R - 0.1), "");
        check("D12 reach is inclusive at exactly REACH, refused just beyond", BroodtideDevour.isEdible(true, true, false, false, R) && !BroodtideDevour.isEdible(true, true, false, false, R + 0.01), "reach=" + R);
        check("D13 a dead mob, a non-horde mob, a Brood-Kin and an already swallowed mob are all refused",
                !BroodtideDevour.isEdible(false, true, false, false, 3) && !BroodtideDevour.isEdible(true, false, false, false, 3)
                        && !BroodtideDevour.isEdible(true, true, true, false, 3) && !BroodtideDevour.isEdible(true, true, false, true, 3), "");
        check("D14 a negative distance (a bad input) is refused", !BroodtideDevour.isEdible(true, true, false, false, -1.0), "");

        // ---- pullFraction ----
        check("D15 the pull starts at 0 and ends at exactly 1", BroodtideDevour.pullFraction(0) == 0.0 && BroodtideDevour.pullFraction(1) == 1.0, "");
        boolean mono = true, inRange = true;
        double prev = -1;
        for (int i = -5; i <= 105; i++) {
            double f = BroodtideDevour.pullFraction(i / 100.0);
            if (f < prev - 1e-12) mono = false;
            if (f < 0 || f > 1) inRange = false;
            prev = f;
        }
        check("D16 the pull only ever moves toward the body and stays in 0..1 (even for t outside 0..1)", mono && inRange, "");
        check("D17 the pull eases out: it covers more than half the way by the half-time", BroodtideDevour.pullFraction(0.5) > 0.5, "f(0.5)=" + BroodtideDevour.pullFraction(0.5));

        // ---- shouldSpit ----
        long sw = 100;
        check("D18 a mob is never spat the tick it is eaten", !BroodtideDevour.shouldSpit(sw, sw), "");
        // earliest Flood tick at least MIN_HIDDEN_TICKS after swallowing must spit; any tick before MIN must not
        boolean tooSoon = false, spitInFlood = false;
        for (long t = sw; t < sw + BroodtideDevour.MIN_HIDDEN_TICKS; t++) {
            if (BroodtideDevour.shouldSpit(t, sw)) tooSoon = true;
        }
        for (long t = sw + BroodtideDevour.MIN_HIDDEN_TICKS; t < sw + BroodtideDevour.MAX_HIDDEN_TICKS; t++) {
            if (TideClock.stateAt(t) == TideClock.State.FLOOD) {
                spitInFlood = BroodtideDevour.shouldSpit(t, sw);
                break;
            }
        }
        check("D19 never spat before the minimum hidden time", !tooSoon, "min=" + BroodtideDevour.MIN_HIDDEN_TICKS);
        check("D20 spat in the first Flood tick once it has been hidden long enough", spitInFlood, "");
        check("D21 spat no later than the maximum hidden time, even if no Flood came", BroodtideDevour.shouldSpit(sw + BroodtideDevour.MAX_HIDDEN_TICKS, sw), "");
        check("D22 a swallow time in the future never spits", !BroodtideDevour.shouldSpit(50, 100), "");

        // The three holes the first mutation run found. Ticks are chosen ON PURPOSE in Flood and in Ebb, so the Flood gate cannot hide the minimum-time rule.
        long floodStart = -1;
        for (long t = 0; t < CYCLE; t++) {
            if (TideClock.stateAt(t) == TideClock.State.FLOOD && TideClock.ticksInto(t) == 0) {
                floodStart = t;
                break;
            }
        }
        // swallowed 5 ticks before the Flood began: in Flood but hidden less than the minimum -> must NOT spit yet; once the minimum has passed (and still Flood) it must
        long justBefore = floodStart - 5;
        boolean tooSoonInFlood = BroodtideDevour.shouldSpit(floodStart, justBefore);
        long minPassed = justBefore + BroodtideDevour.MIN_HIDDEN_TICKS;
        boolean afterMinInFlood = TideClock.stateAt(minPassed) == TideClock.State.FLOOD && BroodtideDevour.shouldSpit(minPassed, justBefore);
        check("D30 in Flood but hidden less than the minimum: not spat; the tick the minimum is reached (still Flood): spat",
                floodStart >= 0 && !tooSoonInFlood && !BroodtideDevour.shouldSpit(minPassed - 1, justBefore) && afterMinInFlood, "floodStart=" + floodStart);

        // in Ebb, hidden well past the minimum but short of the maximum: must NOT spit (it waits for the Flood)
        long ebbSwallow = 0;                                    // tick 0 is the first Ebb tick
        long ebbLater = BroodtideDevour.MIN_HIDDEN_TICKS + 20;  // still Ebb: Ebb is far longer than 50 ticks
        check("D31 in Ebb, hidden past the minimum but under the maximum: NOT spat (it waits for the Flood)",
                TideClock.stateAt(ebbLater) == TideClock.State.EBB && !BroodtideDevour.shouldSpit(ebbLater, ebbSwallow), "");

        // a swallow time in the future, queried while in Flood: must still refuse (a negative hidden time is never 'hidden long enough')
        check("D32 a swallow time in the future is refused even when queried during Flood",
                !BroodtideDevour.shouldSpit(floodStart, floodStart + 10) && !BroodtideDevour.shouldSpit(floodStart + 3, floodStart + 500), "");

        // the safety bound really bounds: for every swallow tick in a cycle, some tick within MAX hidden spits
        boolean bounded = true;
        for (long s = 0; s < CYCLE; s += 7) {
            boolean found = false;
            for (long t = s; t <= s + BroodtideDevour.MAX_HIDDEN_TICKS; t++) {
                if (BroodtideDevour.shouldSpit(t, s)) {
                    found = true;
                    break;
                }
            }
            if (!found) bounded = false;
        }
        check("D23 for every swallow time, the mob is spat within the maximum hidden time", bounded, "");

        // ---- kinHealth, floodHeal, refund ----
        check("D24 a Brood-Kin has more health than the eaten mob, exactly 1.5x", Math.abs(BroodtideDevour.kinHealth(20) - 30.0) < 1e-9 && BroodtideDevour.kinHealth(20) > 20, "");
        check("D25 kin health is never below the mob's own (even for 0 or tiny health)", BroodtideDevour.kinHealth(0) == 0.0 && BroodtideDevour.kinHealth(1) >= 1.0, "");
        check("D26 the flood heal scales with kin alive and is 0 with none", BroodtideDevour.floodHeal(0, 1000) == 0.0 && BroodtideDevour.floodHeal(2, 1000) > BroodtideDevour.floodHeal(1, 1000) && BroodtideDevour.floodHeal(1, 1000) > 0, "");
        check("D27 the flood heal is capped at the 6-kin cap and ignores a negative count",
                BroodtideDevour.floodHeal(60, 1000) == BroodtideDevour.floodHeal(6, 1000) && BroodtideDevour.floodHeal(-3, 1000) == 0.0, "");
        check("D28 the biggest possible heal in one Flood is small: under 5% of the boss's health", BroodtideDevour.floodHeal(6, 1000) < 50.0, "max=" + BroodtideDevour.floodHeal(6, 1000));
        check("D29 an early kill refunds, a late kill does not, a negative time never does",
                BroodtideDevour.refundsKill(0) && BroodtideDevour.refundsKill(BroodtideDevour.REFUND_WINDOW_TICKS) && !BroodtideDevour.refundsKill(BroodtideDevour.REFUND_WINDOW_TICKS + 1) && !BroodtideDevour.refundsKill(-1), "");

        // ---- the v1 allow-list ----
        check("D33 the Devour eats exactly the two proven types: horde_zombie and horde_spitter",
                BroodtideDevour.EDIBLE_TYPES.size() == 2 && BroodtideDevour.isEdibleType("emberfall:horde_zombie") && BroodtideDevour.isEdibleType("emberfall:horde_spitter"), BroodtideDevour.EDIBLE_TYPES.toString());
        check("D34 nothing unproven is edible: other horde mobs, bosses, the Testificate, the Pink Slime, a vanilla zombie, null and empty",
                !BroodtideDevour.isEdibleType("emberfall:horde_skeleton") && !BroodtideDevour.isEdibleType("emberfall:horde_witch") && !BroodtideDevour.isEdibleType("emberfall:horde_bomber")
                        && !BroodtideDevour.isEdibleType("emberfall:broodtide") && !BroodtideDevour.isEdibleType("emberfall:testificate") && !BroodtideDevour.isEdibleType("emberfall:pink_slime")
                        && !BroodtideDevour.isEdibleType("minecraft:zombie") && !BroodtideDevour.isEdibleType(null) && !BroodtideDevour.isEdibleType(""), "");
        check("D35 the match is exact: no prefix, suffix or case tricks",
                !BroodtideDevour.isEdibleType("emberfall:horde_zombie_elite") && !BroodtideDevour.isEdibleType("horde_zombie") && !BroodtideDevour.isEdibleType("EMBERFALL:HORDE_ZOMBIE")
                        && !BroodtideDevour.isEdibleType(" emberfall:horde_zombie"), "");

        System.out.println();
        System.out.println(fails == 0 ? "ALL PASS (" + total + " checks)" : "FAILED " + fails + " of " + total);
        System.exit(fails == 0 ? 0 : 1);
    }
}
