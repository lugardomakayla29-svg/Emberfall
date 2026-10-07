import com.solme.emberfall.rift.RiftRules;
import com.solme.emberfall.hub.GateRules;
public class RiftRulesCheck {
    static int fails = 0;
    static void check(String l, boolean ok, String e) { System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e); if (!ok) fails++; }
    public static void main(String[] a) {
        // The party/gate numbers must BE the gate's, not copies that can drift.
        check("countdown, lockout and reach are the gate's own constants", RiftRules.COUNTDOWN_SECONDS == GateRules.COUNTDOWN_SECONDS && RiftRules.LOCKOUT_TICKS == GateRules.LOCKOUT_TICKS && RiftRules.REACH == GateRules.REACH, "");
        check("a party is at most 10", RiftRules.MAX_PARTY == 10, "");
        // Idle expiry: boundary on BOTH axes.
        check("idle: not expired one tick before 10 minutes, expired at 10 minutes, when empty", !RiftRules.idleExpired(RiftRules.IDLE_TICKS - 1, 0) && RiftRules.idleExpired(RiftRules.IDLE_TICKS, 0), "");
        check("idle: never expires while anyone is waiting, however long", !RiftRules.idleExpired(Long.MAX_VALUE, 1) && !RiftRules.idleExpired(RiftRules.IDLE_TICKS * 50L, 3), "");
        check("idle: a negative waiting count behaves as empty, not as a crash", RiftRules.idleExpired(RiftRules.IDLE_TICKS, -2), "");
        check("idle: 10 minutes is exactly 12000 ticks", RiftRules.IDLE_TICKS == 12000, "");
        // Close delay.
        check("close: not due one tick early, due at the delay", !RiftRules.closeDue(RiftRules.CLOSE_DELAY_TICKS - 1) && RiftRules.closeDue(RiftRules.CLOSE_DELAY_TICKS), "");
        check("close: the delay is 5 seconds (100 ticks)", RiftRules.CLOSE_DELAY_TICKS == 100, "");
        check("close: a run that has NOT ended (negative) never closes the Rift", !RiftRules.closeDue(-1) && !RiftRules.closeDue(Long.MIN_VALUE), "");
        // Party cap: boundary 9 yes, 10 no.
        check("join: a party of 9 takes one more, a party of 10 does not", RiftRules.canJoin(9) && !RiftRules.canJoin(10), "");
        check("join: an empty party takes a player, a negative size is refused", RiftRules.canJoin(0) && !RiftRules.canJoin(-1), "");
        // Enter: each condition alone must be able to refuse.
        long none = -1;
        check("enter: allowed when near, never run, room", RiftRules.canEnter(1.0, none, 1), "");
        check("enter: reach is inclusive at 4.0 and refuses just past it", RiftRules.canEnter(4.0, none, 1) && !RiftRules.canEnter(4.01, none, 1), "");
        check("enter: refused at 199 ticks after a run, allowed at 200", !RiftRules.canEnter(1.0, 199, 1) && RiftRules.canEnter(1.0, 200, 1), "");
        check("enter: refused when the party is full even if near and rested", !RiftRules.canEnter(1.0, none, 10), "");
        // Refusal text: right reason, right order, null when allowed.
        check("refusal: null when allowed", RiftRules.refusal(1.0, none, 1) == null, "");
        check("refusal: names distance first, then lockout, then full", "too far from the Rift".equals(RiftRules.refusal(9, 0, 10)) && "the Rift is still settling".equals(RiftRules.refusal(1, 0, 10)) && "the party is full".equals(RiftRules.refusal(1, none, 10)), "");
        check("refusal agrees with canEnter for every combination", agree(), "");
        // Shard.
        check("shard: 0 and negative cannot open, 1 and 64 can", !RiftRules.canOpen(0) && !RiftRules.canOpen(-1) && RiftRules.canOpen(1) && RiftRules.canOpen(64), "");
        // The mutation proof (5 deliberately wrong rules, each must turn this check red) is in the PR; it edits RiftRules, so it cannot live here.
        System.out.println(fails == 0 ? "ALL PASS" : "FAILED " + fails);
        if (fails != 0) System.exit(1);
    }
    static boolean agree() {
        double[] d = {0, 3.9, 4.0, 4.1, 50}; long[] s = {-1, 0, 100, 199, 200, 5000}; int[] p = {0, 1, 9, 10, 11};
        for (double x : d) for (long y : s) for (int z : p) if ((RiftRules.refusal(x, y, z) == null) != RiftRules.canEnter(x, y, z)) return false;
        return true;
    }
}
