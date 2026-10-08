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
