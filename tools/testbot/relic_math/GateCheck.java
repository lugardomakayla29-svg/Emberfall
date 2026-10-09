import com.solme.emberfall.rift.GateRules;
public class GateCheck {
    static int fails = 0;
    static void check(String l, boolean ok, String e) { System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e); if (!ok) fails++; }
    public static void main(String[] a) {
        check("lockout: never ended (-1) is free, 0 ticks locked, 199 locked, 200 free", !GateRules.lockedOut(-1) && GateRules.lockedOut(0) && GateRules.lockedOut(199) && !GateRules.lockedOut(200), "");
        check("lockout lasts 10 s", GateRules.LOCKOUT_TICKS == 200, "");
        check("countdown: 3 at 0 ticks, 3 at 19, 2 at 20, 1 at 40, 0 at 60", GateRules.secondsLeft(0) == 3 && GateRules.secondsLeft(19) == 3 && GateRules.secondsLeft(20) == 2 && GateRules.secondsLeft(40) == 1 && GateRules.secondsLeft(60) == 0, "");
        check("countdown done flips exactly at 60 ticks", !GateRules.countdownDone(59) && GateRules.countdownDone(60), "");
        check("secondsLeft never negative, even far past the end or for negative input", GateRules.secondsLeft(100000) == 0 && GateRules.secondsLeft(-5) == 3, "");
        check("moving 2.5 blocks keeps the countdown, 2.6 cancels it", !GateRules.moved(2.5, 0) && GateRules.moved(2.6, 0) && !GateRules.moved(1.7, 1.7) && GateRules.moved(1.9, 1.9), "");
        System.out.println(fails == 0 ? "ALL PASS" : "FAILED " + fails);
    }
}
