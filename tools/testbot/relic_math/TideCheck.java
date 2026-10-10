import com.solme.emberfall.boss.TideClock;
import com.solme.emberfall.boss.TideClock.State;

/**
 * Pure checks for the Broodtide's Tide (TideClock, plan section 2 and test 4). They prove the rule: Ebb then Flood on a fixed cycle, the exact
 * tick of every change, armour exactly 0.35 in Flood and 1.0 in Ebb and never 0, and no drift however long the fight runs. They do NOT prove the
 * boss reads this clock, or how the Tide looks or sounds: that is the entity's and the owner's to judge.
 */
public class TideCheck {
    static int fails = 0;
    static int total = 0;

    static void check(String l, boolean ok, String e) {
        total++;
        System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e);
        if (!ok) {
            fails++;
        }
    }

    public static void main(String[] a) {
        final int E = TideClock.EBB_TICKS, F = TideClock.FLOOD_TICKS, C = TideClock.CYCLE_TICKS;

        check("T1 the numbers are the plan's: Ebb 14 s, Flood 9 s, cycle 23 s", E == 280 && F == 180 && C == 460, "ebb " + E + " flood " + F + " cycle " + C);
        check("T2 the fight starts in Ebb", TideClock.stateAt(0) == State.EBB, "tick 0 " + TideClock.stateAt(0));
        check("T3 the last Ebb tick is Ebb and the first Flood tick is Flood (no off-by-one)",
                TideClock.stateAt(E - 1) == State.EBB && TideClock.stateAt(E) == State.FLOOD, "tick " + (E - 1) + " " + TideClock.stateAt(E - 1) + ", tick " + E + " " + TideClock.stateAt(E));
        check("T4 the last Flood tick is Flood and the next tick is Ebb again",
                TideClock.stateAt(C - 1) == State.FLOOD && TideClock.stateAt(C) == State.EBB, "tick " + (C - 1) + " " + TideClock.stateAt(C - 1) + ", tick " + C + " " + TideClock.stateAt(C));

        // exact counts over one cycle
        int ebb = 0, flood = 0;
        for (int t = 0; t < C; t++) {
            if (TideClock.stateAt(t) == State.EBB) ebb++; else flood++;
        }
        check("T5 over one cycle exactly 280 Ebb ticks and 180 Flood ticks", ebb == E && flood == F, "ebb " + ebb + " flood " + flood);

        // armour
        boolean armourOk = true, neverZero = true;
        double bad = -1;
        for (int t = 0; t < 3 * C; t++) {
            double ar = TideClock.armourAt(t);
            State s = TideClock.stateAt(t);
            if (s == State.FLOOD && ar != 0.35) { armourOk = false; bad = ar; }
            if (s == State.EBB && ar != 1.0) { armourOk = false; bad = ar; }
            if (ar <= 0.0) neverZero = false;
        }
        check("T6 armour is exactly 0.35 in Flood and exactly 1.0 in Ebb, on every tick of 3 cycles", armourOk, "bad value " + bad);
        check("T7 the body is never immune: the multiplier is above 0 on every tick", neverZero, "");
        check("T8 a hit of 10 does 3.5 in Flood and 10 in Ebb", Math.abs(10 * TideClock.armourAt(E) - 3.5) < 1e-9 && Math.abs(10 * TideClock.armourAt(0) - 10.0) < 1e-9, "flood " + 10 * TideClock.armourAt(E) + " ebb " + 10 * TideClock.armourAt(0));

        // changes
        boolean changeOk = true;
        int changes = 0;
        for (int t = 0; t < 10 * C; t++) {
            boolean ch = TideClock.changesAt(t);
            boolean real = t > 0 && TideClock.stateAt(t) != TideClock.stateAt(t - 1);
            if (ch != real) changeOk = false;
            if (ch) changes++;
        }
        check("T9 changesAt is true exactly when the state differs from the tick before, never on tick 0", changeOk && !TideClock.changesAt(0), "changes in 10 cycles " + changes);
        check("T10 ten full cycles hold exactly 19 changes (the one at tick 10*C starts cycle 11 and is outside the window)", changes == 19, "changes " + changes);

        boolean changeTickOk = true;
        for (int n = 1; n <= 40; n++) {
            long t = TideClock.changeTick(n);
            if (!TideClock.changesAt(t)) changeTickOk = false;
            if (n > 1 && TideClock.changeTick(n) <= TideClock.changeTick(n - 1)) changeTickOk = false;
            State expect = (n % 2 == 1) ? State.FLOOD : State.EBB;
            if (TideClock.stateAt(t) != expect) changeTickOk = false;
        }
        check("T11 changeTick(n) is a real change, strictly increasing, and alternates Flood, Ebb, Flood ...", changeTickOk, "");

        // ticksLeft
        boolean leftOk = true;
        for (int t = 0; t < 2 * C; t++) {
            int left = TideClock.ticksLeft(t);
            State s = TideClock.stateAt(t);
            State after = TideClock.stateAt(t + left);
            State lastIn = TideClock.stateAt(t + left - 1);
            if (left < 1 || s != lastIn || s == after) leftOk = false;
        }
        check("T12 ticksLeft is how long the current state lasts: the last tick is still this state and the next one is not", leftOk, "");

        // no drift
        long far = 100_000L * C + 137;
        check("T13 no drift: after 100000 cycles the state is the same as the same point in the first cycle", TideClock.stateAt(far) == TideClock.stateAt(137) && TideClock.armourAt(far) == TideClock.armourAt(137), "tick " + far);
        long farFlood = 100_000L * C + E;
        check("T14 the first Flood tick after 100000 cycles is still exactly where it should be", TideClock.stateAt(farFlood) == State.FLOOD && TideClock.stateAt(farFlood - 1) == State.EBB, "tick " + farFlood);
        check("T15 a negative tick is treated as the fight's start (Ebb, armour 1.0), never a crash or Flood", TideClock.stateAt(-5) == State.EBB && TideClock.armourAt(-5) == 1.0 && !TideClock.changesAt(-5), "");

        // T16-T19: the 2^31 tick probe. 2^31 ticks is ~3.4 years of fight, unreachable in play, but the (int) casts in ticksInto/ticksLeft and the int n of changeTick are
        // exactly where a boundary bug would hide, so prove the arithmetic across the int overflow point against a reference written here with plain long modulo.
        long B = 1L << 31;
        boolean sweepOk = true; String sweepNote = "";
        for (long t = B - 3L * C; t <= B + 3L * C && sweepOk; t++) {
            long into = t % C;
            State want = into < E ? State.EBB : State.FLOOD;
            int wantInto = (int) (into < E ? into : into - E);
            int wantLeft = (int) (into < E ? E - into : C - into);
            boolean wantChange = t > 0 && ((t % C) == 0 || (t % C) == E);
            if (TideClock.stateAt(t) != want || TideClock.ticksInto(t) != wantInto || TideClock.ticksLeft(t) != wantLeft || TideClock.changesAt(t) != wantChange
                || TideClock.ticksInto(t) + TideClock.ticksLeft(t) != (want == State.EBB ? E : C - E)) { sweepOk = false; sweepNote = "first bad tick " + t; }
        }
        check("T16 every tick within three cycles either side of 2^31 matches an independent long-modulo reference (state, ticksInto, ticksLeft, changesAt)", sweepOk, sweepNote);
        check("T17 the ticks straddling 2^31 are positive longs, never wrapped negative (so a wrap to Ebb at tick 0 cannot happen)", B > 0 && (B - 1) > 0 && (B + 1) > B && TideClock.stateAt(B) == (((B % C) < E) ? State.EBB : State.FLOOD), "2^31 = " + B);
        long big = Long.MAX_VALUE - 5;
        long bigInto = big % C;
        check("T18 a tick near Long.MAX_VALUE still gives a valid state, an in-range ticksInto and ticksLeft >= 1", TideClock.stateAt(big) == ((bigInto < E) ? State.EBB : State.FLOOD) && TideClock.ticksInto(big) >= 0 && TideClock.ticksLeft(big) >= 1, "tick " + big);
        boolean wideChangeOk = true;
        for (int n = 1; n <= 200_000 && wideChangeOk; n += 997) { long ct = TideClock.changeTick(n); if (ct <= 0 || !TideClock.changesAt(ct) || TideClock.changesAt(ct - 1)) wideChangeOk = false; }
        long cLast = TideClock.changeTick(Integer.MAX_VALUE);
        check("T19 changeTick stays a real change over a wide n range, and at n = Integer.MAX_VALUE it is a positive long that is a real change (no int overflow)", wideChangeOk && cLast > 0 && TideClock.changesAt(cLast) && !TideClock.changesAt(cLast - 1), "changeTick(MAX)=" + cLast);

        // T20: the VALUE of changeTick at huge n, from the closed form written here (a masked or truncated n is still "a real change", so T19 alone cannot see it).
        boolean valueOk = true; String valueNote = "";
        int[] ns = { 1, 2, 3, 4, 999_999, 1_000_000, 1_073_741_823, 1_073_741_824, 1_073_741_825, 2_000_000_001, Integer.MAX_VALUE - 1, Integer.MAX_VALUE };
        for (int n : ns) {
            long want = ((long) (n - 1) / 2) * C + ((n % 2) == 1 ? E : C);
            if (TideClock.changeTick(n) != want) { valueOk = false; valueNote = "n=" + n + " got " + TideClock.changeTick(n) + " want " + want; break; }
        }
        check("T20 changeTick(n) equals the closed form ((n-1)/2)*cycle + (odd ? ebb : cycle) for n up to Integer.MAX_VALUE (no mask, no truncation)", valueOk, valueNote);

        System.out.println();
        System.out.println(fails == 0 ? "ALL PASS (" + total + " checks)" : "SOME FAIL (" + fails + " of " + total + ")");
        if (fails > 0) System.exit(1);
    }
}
