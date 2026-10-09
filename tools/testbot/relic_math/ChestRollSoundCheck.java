import com.solme.emberfall.relic.ChestReveal;
import com.solme.emberfall.relic.ChestRollSound;
import com.solme.emberfall.relic.ChestRollSound.Cues;

/** P4: the chest roll sound calls, counted. Pure arithmetic, no server and no client. Nothing was heard. */
public class ChestRollSoundCheck {
    static int fails = 0, passes = 0;
    static void check(String l, boolean ok, String e) { System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e); if (ok) passes++; else fails++; }

    /** Independent count: how many times a reel's decoy index changes between two animation ticks, straight off symbolAt. */
    static int stepsOf(int start, int stop) {
        int n = 0;
        for (int t = start + 1; t < stop; t++) if (ChestReveal.symbolAt(t - start, stop - start) != ChestReveal.symbolAt(t - 1 - start, stop - start)) n++;
        return n;
    }

    public static void main(String[] a) {
        int tierSteps = stepsOf(0, ChestReveal.TIER_STOP_TICK);
        int itemStart = ChestReveal.TIER_STOP_TICK + ChestReveal.GAP_TICKS;
        int itemSteps = stepsOf(itemStart, ChestReveal.ITEM_STOP_TICK);
        check("S0 the independent count finds the reel steps (tier 15, item 23, read off symbolAt)", tierSteps == 15 && itemSteps == 23, "(tier " + tierSteps + ", item " + itemSteps + ")");

        // A full watch, one client tick at a time: one cue per step, each landing once.
        Cues all = ChestRollSound.total(ChestReveal.TOTAL_TICKS + 80);
        check("S1 a full watch plays exactly one tick per reel step (tier + item)", all.ticks() == tierSteps + itemSteps, "(" + all.ticks() + " vs " + (tierSteps + itemSteps) + ")");
        check("S2 the tier landing and the item landing each happen", all.tierLands() && all.itemLands(), "");
        check("S3 total sound calls over a full watch = steps + 2 landings", all.calls() == tierSteps + itemSteps + 2, "(" + all.calls() + ")");

        // Per-boundary exactness: every landing is on one boundary only.
        int tierLandCount = 0, itemLandCount = 0, bothAtOnce = 0, maxCallsOneTick = 0;
        for (int s = 0; s < ChestReveal.TOTAL_TICKS + 80; s++) {
            Cues c = ChestRollSound.cues(s, s + 1);
            if (c.tierLands()) tierLandCount++;
            if (c.itemLands()) itemLandCount++;
            if (c.tierLands() && c.itemLands()) bothAtOnce++;
            maxCallsOneTick = Math.max(maxCallsOneTick, c.calls());
        }
        check("S4 the tier landing fires on exactly one boundary, and the item landing on exactly one", tierLandCount == 1 && itemLandCount == 1, "(" + tierLandCount + ", " + itemLandCount + ")");
        check("S5 the tier lands on the tick the reel stops (boundary " + (ChestReveal.TIER_STOP_TICK - 1) + "->" + ChestReveal.TIER_STOP_TICK + ")", ChestRollSound.cues(ChestReveal.TIER_STOP_TICK - 1, ChestReveal.TIER_STOP_TICK).tierLands(), "");
        check("S6 the item lands on the tick the reel stops (boundary " + (ChestReveal.ITEM_STOP_TICK - 1) + "->" + ChestReveal.ITEM_STOP_TICK + ")", ChestRollSound.cues(ChestReveal.ITEM_STOP_TICK - 1, ChestReveal.ITEM_STOP_TICK).itemLands(), "");
        check("S7 the two landings never share a tick, so they are two distinct sounds", bothAtOnce == 0, "");
        check("S8 no single client tick asks for more than 2 sound calls (never a burst)", maxCallsOneTick <= 2, "(max " + maxCallsOneTick + ")");

        // No sound after the item landed, and none before the spin starts.
        boolean quietAfter = true;
        for (int s = ChestReveal.ITEM_STOP_TICK; s < ChestReveal.TOTAL_TICKS + 80; s++) if (ChestRollSound.cues(s, s + 1).calls() != 0) quietAfter = false;
        check("S9 silent from the item landing to the end of the hold and the auto close", quietAfter, "");
        check("S10 no tick sound while the item reel is still hidden in the gap (ticks 40..49 add only the tier landing)", ChestRollSound.cues(ChestReveal.TIER_STOP_TICK, ChestReveal.TIER_STOP_TICK + 1).calls() == 0 && ChestRollSound.cues(ChestReveal.TIER_STOP_TICK + 3, ChestReveal.TIER_STOP_TICK + 4).calls() == 0, "");

        // Skips. A click jumps the screen tick forward by many ticks.
        Cues skip = ChestRollSound.cues(45, ChestReveal.ITEM_STOP_TICK);
        check("S11 a skip from tick 45 to the end plays NO tick sounds for the steps it jumped over", skip.ticks() == 0, "(" + skip.ticks() + ")");
        check("S12 that skip plays the item landing once (the answer), and not the tier landing it had already passed", skip.itemLands() && !skip.tierLands(), "");
        Cues early = ChestRollSound.cues(35, ChestReveal.ITEM_STOP_TICK);
        check("S13 a skip from before the tier landed plays both landings, once each, and no ticks", early.tierLands() && early.itemLands() && early.ticks() == 0 && early.calls() == 2, "(" + early.calls() + ")");
        // A jump that STARTS inside a spinning reel is where a missing skip rule would show: the symbol differs between the two ends.
        Cues midItem = ChestRollSound.cues(80, ChestReveal.ITEM_STOP_TICK);
        check("S13b a skip that starts in the middle of the item reel (80 -> end) plays no ticks, only the item landing", midItem.ticks() == 0 && midItem.itemLands() && !midItem.tierLands(), "(" + midItem.calls() + ")");
        Cues midTier = ChestRollSound.cues(20, 30);
        check("S13c a 10-tick jump inside the tier reel plays no ticks (it is a skip, not a step)", midTier.ticks() == 0, "(" + midTier.ticks() + ")");
        check("S13d the SAME two symbols one tick apart would have counted, so the rule is what silences it", ChestReveal.symbolAt(20, 40) != ChestReveal.symbolAt(30, 40), "");
        check("S14 a screen that opens already finished (no spin to show) plays nothing", ChestRollSound.cues(ChestReveal.TOTAL_TICKS, ChestReveal.TOTAL_TICKS + 1).calls() == 0, "");
        check("S15 a jump from 0 straight to TOTAL plays 2 landings and no ticks", ChestRollSound.cues(0, ChestReveal.TOTAL_TICKS).calls() == 2, "");

        // Safety.
        check("S16 no time passing asks for nothing", ChestRollSound.cues(20, 20).calls() == 0, "");
        check("S17 time going backwards asks for nothing", ChestRollSound.cues(30, 10).calls() == 0, "");
        check("S18 negative ticks do not throw and ask for nothing", ChestRollSound.cues(-5, -1).calls() == 0, "");

        // Slowing down: steps get further apart toward the end of the tier reel.
        int firstGap = -1, lastGap = -1, prev = -1;
        for (int s = 0; s < ChestReveal.TIER_STOP_TICK; s++) if (ChestRollSound.cues(s, s + 1).ticks() > 0) { if (prev >= 0) { if (firstGap < 0) firstGap = s - prev; lastGap = s - prev; } prev = s; }
        check("S19 the tick sounds slow down with the reel (gap " + firstGap + " ticks at first, " + lastGap + " at the end)", lastGap > firstGap, "");

        System.out.println("TABLE tier reel steps=" + tierSteps + " item reel steps=" + itemSteps + " landings=2 total calls=" + (tierSteps + itemSteps + 2));
        System.out.println(fails == 0 ? "ALL PASS (" + passes + " checks)" : "SOME FAIL (" + fails + ")");
        System.exit(fails == 0 ? 0 : 1);
    }
}
