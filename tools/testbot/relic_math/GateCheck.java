import com.solme.emberfall.hub.*;
public class GateCheck {
    static int fails = 0;
    static void check(String l, boolean ok, String e) { System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e); if (!ok) fails++; }
    public static void main(String[] a) {
        var gate = HubLayout.departureSpot();
        check("the gate stays at (0,-1)", gate.dx() == 0 && gate.dz() == -1, "");
        boolean notOnGate = true, clearOfBusts = true, insideFootprint = true;
        for (var r : GateRules.returnSpots()) {
            if (r.dx() == gate.dx() && r.dz() == gate.dz()) notOnGate = false;
            for (var b : HubLayout.bustSpots()) if (Math.hypot(r.dx() - b.dx(), r.dz() - b.dz()) < 1.4) clearOfBusts = false;
            if (Math.abs(r.dx()) > 4 || Math.abs(r.dz()) > 4) insideFootprint = false;
        }
        check("no return spot is the gate cell", notOnGate, "");
        check("every return spot is at least 1.4 blocks from every bust", clearOfBusts, "");
        check("every return spot is inside the 9x9 footprint", insideFootprint, "");
        boolean beside = true; for (var r : GateRules.returnSpots()) if (Math.hypot(r.dx() - gate.dx(), r.dz() - gate.dz()) > 1.5) beside = false;
        check("every return spot is beside the gate (within 1.5 blocks)", beside, "");
        var keeper = HubLayout.shopKeeperSpot(); boolean notKeeper = true; for (var r : GateRules.returnSpots()) if (r.dx() == keeper.dx() && r.dz() == keeper.dz()) notKeeper = false;
        check("no return spot is the shop keeper's cell", notKeeper, "");
        check("the two spots differ (two players do not stack)", GateRules.returnSpot(0) != GateRules.returnSpot(1), "");
        check("the spot index wraps and never throws for any int", GateRules.returnSpot(-7) != null && GateRules.returnSpot(Integer.MIN_VALUE) != null && GateRules.returnSpot(Integer.MAX_VALUE) != null, "");
        check("lockout: never ended (-1) is free, 0 ticks locked, 199 locked, 200 free", !GateRules.lockedOut(-1) && GateRules.lockedOut(0) && GateRules.lockedOut(199) && !GateRules.lockedOut(200), "");
        check("lockout lasts 10 s", GateRules.LOCKOUT_TICKS == 200, "");
        check("countdown: 3 at 0 ticks, 3 at 19, 2 at 20, 1 at 40, 0 at 60", GateRules.secondsLeft(0) == 3 && GateRules.secondsLeft(19) == 3 && GateRules.secondsLeft(20) == 2 && GateRules.secondsLeft(40) == 1 && GateRules.secondsLeft(60) == 0, "");
        check("countdown done flips exactly at 60 ticks", !GateRules.countdownDone(59) && GateRules.countdownDone(60), "");
        check("secondsLeft never negative, even far past the end or for negative input", GateRules.secondsLeft(100000) == 0 && GateRules.secondsLeft(-5) == 3, "");
        check("moving 2.5 blocks keeps the countdown, 2.6 cancels it", !GateRules.moved(2.5, 0) && GateRules.moved(2.6, 0) && !GateRules.moved(1.7, 1.7) && GateRules.moved(1.9, 1.9), "");
        System.out.println(fails == 0 ? "ALL PASS" : "FAILED " + fails);
    }
}
