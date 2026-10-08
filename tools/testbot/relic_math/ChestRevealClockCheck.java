import com.solme.emberfall.relic.ChestReveal;
import com.solme.emberfall.relic.ChestRevealClock;
import com.solme.emberfall.relic.ChestRevealClock.Press;

/**
 * Pure checks for the reveal screen's timing. Numbers are written by hand from the design (tier stops at 40, item stops at 110, total 140, auto close 60
 * later), not read back from the constants, so a changed constant makes a check fail on purpose and has to be looked at.
 */
public class ChestRevealClockCheck {
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
        check("the design numbers: tier stops 40, item stops 110, total 140", ChestReveal.TIER_STOP_TICK == 40 && ChestReveal.ITEM_STOP_TICK == 110 && ChestReveal.TOTAL_TICKS == 140, "");
        check("animation tick never negative", ChestRevealClock.animationTick(-5) == 0, "");
        check("animation tick follows the screen tick", ChestRevealClock.animationTick(77) == 77, "");
        check("animation tick stops at the end", ChestRevealClock.animationTick(9999) == 140, "");
        check("not finished at 139", !ChestRevealClock.finished(139), "");
        check("finished at 140", ChestRevealClock.finished(140), "");
        check("no auto close at 199", !ChestRevealClock.autoClose(199), "");
        check("auto close at 200", ChestRevealClock.autoClose(200), "");
        check("a press at tick 0 is ignored (the click that opened the chest)", ChestRevealClock.onPress(0) == Press.NONE, "");
        check("a press at tick 39 is still ignored", ChestRevealClock.onPress(39) == Press.NONE, "");
        check("a press at tick 40 skips", ChestRevealClock.onPress(40) == Press.SKIP, "");
        check("a press at tick 139 skips", ChestRevealClock.onPress(139) == Press.SKIP, "");
        check("a press at tick 140 closes", ChestRevealClock.onPress(140) == Press.CLOSE, "");
        check("a press long after still closes", ChestRevealClock.onPress(100000) == Press.CLOSE, "");
        check("a negative tick is ignored, not a crash", ChestRevealClock.onPress(-3) == Press.NONE, "");
        check("skip lands where both reels have stopped", ChestRevealClock.skipTarget() == 110, "");
        // After a skip the screen is at 110: both reels stopped, not yet finished, so a second press waits for the hold and does not close early.
        check("after a skip a press is a SKIP again, not an instant close", ChestRevealClock.onPress(ChestRevealClock.skipTarget()) == Press.SKIP, "");
        check("the reveal at the skip target shows both reels stopped", bothStopped(ChestRevealClock.skipTarget()), "");
        check("one tick before the skip target the item reel is still spinning", !bothStopped(ChestRevealClock.skipTarget() - 1), "");
        // --- Added with the Task 4 list (Koda 13:55 CT). Expected values are written by hand from the design: tier stop 40, item stop 110, total 140, auto close at 200.
        // Case 1: the integer extremes. A screen tick counter that wrapped, or a bad caller, must not crash the screen or close it by accident.
        check("extremes: Integer.MIN_VALUE acts as tick 0 (animation 0, not finished, no auto close, press ignored)",
                ChestRevealClock.animationTick(Integer.MIN_VALUE) == 0 && !ChestRevealClock.finished(Integer.MIN_VALUE)
                        && !ChestRevealClock.autoClose(Integer.MIN_VALUE) && ChestRevealClock.onPress(Integer.MIN_VALUE) == Press.NONE, "");
        check("extremes: -1 acts as tick 0 (animation 0, not finished, no auto close, press ignored)",
                ChestRevealClock.animationTick(-1) == 0 && !ChestRevealClock.finished(-1) && !ChestRevealClock.autoClose(-1)
                        && ChestRevealClock.onPress(-1) == Press.NONE, "");
        check("extremes: Integer.MAX_VALUE acts as the end (animation 140, finished, auto close, press closes)",
                ChestRevealClock.animationTick(Integer.MAX_VALUE) == 140 && ChestRevealClock.finished(Integer.MAX_VALUE)
                        && ChestRevealClock.autoClose(Integer.MAX_VALUE) && ChestRevealClock.onPress(Integer.MAX_VALUE) == Press.CLOSE, "");
        // Case 2: monotone. Once the reveal is finished (or the screen auto-closes) no later tick may undo it.
        boolean finishedStays = true;
        boolean autoCloseStays = true;
        boolean sawFinished = false;
        boolean sawAutoClose = false;
        for (int t = 0; t <= 1000; t++) {
            if (sawFinished && !ChestRevealClock.finished(t)) {
                finishedStays = false;
            }
            if (sawAutoClose && !ChestRevealClock.autoClose(t)) {
                autoCloseStays = false;
            }
            sawFinished |= ChestRevealClock.finished(t);
            sawAutoClose |= ChestRevealClock.autoClose(t);
        }
        check("monotone: once finished is true it stays true for every tick up to 1000", finishedStays && sawFinished, "");
        check("monotone: once autoClose is true it stays true for every tick up to 1000", autoCloseStays && sawAutoClose, "");
        // Case 3: the screen never closes itself while a reel is still moving, and at the last tick before it closes both reels are stopped.
        boolean neverClosesEarly = true;
        for (int t = -5; t <= 400; t++) {
            if (ChestRevealClock.autoClose(t) && !ChestRevealClock.finished(t)) {
                neverClosesEarly = false;
            }
        }
        check("auto close never happens before finished (ticks -5 to 400)", neverClosesEarly, "");
        check("at the last tick before auto close (199) both reels are stopped", bothStopped(ChestRevealClock.animationTick(199)), "");
        // Case 5: every boundary of onPress in one table: NONE below 40, SKIP from 40 to 139, CLOSE from 140.
        int[] ticks = {0, 39, 40, 41, 109, 110, 139, 140, 141, 199, 200};
        Press[] want = {Press.NONE, Press.NONE, Press.SKIP, Press.SKIP, Press.SKIP, Press.SKIP, Press.SKIP, Press.CLOSE, Press.CLOSE, Press.CLOSE, Press.CLOSE};
        boolean table = true;
        StringBuilder bad = new StringBuilder();
        for (int i = 0; i < ticks.length; i++) {
            if (ChestRevealClock.onPress(ticks[i]) != want[i]) {
                table = false;
                bad.append(ticks[i]).append(' ');
            }
        }
        check("onPress boundary table: 0,39 NONE; 40,41,109,110,139 SKIP; 140,141,199,200 CLOSE", table, bad.toString());
        System.out.println(fails == 0 ? "ALL PASS (" + total + ")" : "FAILED " + fails + " of " + total);
        if (fails != 0) {
            System.exit(1);
        }
    }

    static boolean bothStopped(int tick) {
        ChestReveal.Reveal v = ChestReveal.build("Rare", "Zed", java.util.List.of("Common", "Rare", "Legendary"), java.util.List.of("Zed", "Ann", "Bob", "Cy"), 3L);
        ChestReveal.Frame f = v.frame(tick);
        return f.tier().state() == ChestReveal.State.STOPPED && f.item().state() == ChestReveal.State.STOPPED;
    }
}
