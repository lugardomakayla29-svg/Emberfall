import com.solme.emberfall.hub.*;
import com.solme.emberfall.world.PartyScaling;
public class GatePartyCheck {
    static int fails = 0, ran = 0;
    static void check(String l, boolean ok, String e) { ran++; System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e); if (!ok) fails++; }
    public static void main(String[] a) {
        check("gate party cap equals PartyScaling.MAX_PARTY", GateRules.MAX_PARTY == PartyScaling.MAX_PARTY, "" + GateRules.MAX_PARTY);
        check("a second player may join 1 s into the countdown", GateRules.canJoin(1, 20), "");
        check("a join at tick 0 is allowed", GateRules.canJoin(1, 0), "");
        check("a join on the last tick (59) is allowed", GateRules.canJoin(1, 59), "");
        check("a join at tick 60 (clock done) is refused", !GateRules.canJoin(1, 60), "");
        check("a join long after is refused", !GateRules.canJoin(1, 5000), "");
        check("the 10th member may join, the 11th may not", GateRules.canJoin(9, 20) && !GateRules.canJoin(10, 20), "");
        check("no one can join an empty departure (0 members)", !GateRules.canJoin(0, 20), "");
        check("a negative clock is refused, never an exception", !GateRules.canJoin(1, -1), "");
        check("group departs exactly when the first clock ends", !GateRules.groupDeparts(59) && GateRules.groupDeparts(60), "");
        boolean mono = true; for (int m = 1; m < 10; m++) if (!GateRules.canJoin(m, 10)) mono = false;
        check("every size from 1 to 9 can take one more", mono, "");
        check("huge member counts never throw or allow", !GateRules.canJoin(Integer.MAX_VALUE, 10) && !GateRules.canJoin(Integer.MIN_VALUE, 10), "");
        check("standing on the gate cell is NOT a safe own spot", !GateRules.ownSpotIsSafe(0, 0), "");
        check("0.5 blocks from the gate is not safe", !GateRules.ownSpotIsSafe(0.5, 0.5), "");
        check("1.19 blocks is not safe, 1.2 is", !GateRules.ownSpotIsSafe(1.19, 0) && GateRules.ownSpotIsSafe(1.2, 0), "");
        check("3 blocks away is safe in any direction", GateRules.ownSpotIsSafe(3, 0) && GateRules.ownSpotIsSafe(0, -3) && GateRules.ownSpotIsSafe(-2, 2), "");
        System.out.println(fails == 0 && ran == 16 ? "RESULT: ALL PASSED (" + ran + " checks)" : "RESULT: " + fails + " FAILED (ran " + ran + " of 16)");
        System.exit(fails == 0 && ran == 16 ? 0 : 1);
    }
}
