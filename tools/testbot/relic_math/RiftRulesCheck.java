import com.solme.emberfall.rift.RiftRules;
import com.solme.emberfall.rift.GateRules;
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
        // ---- Step 2: natural event, spacing, particle budget ------------------------------------------------------------------
        check("natural: a roll is not due one tick early and is due at one minute", !RiftRules.naturalRollDue(RiftRules.NATURAL_CHECK_TICKS - 1) && RiftRules.naturalRollDue(RiftRules.NATURAL_CHECK_TICKS), "");
        check("natural: the roll interval is exactly 1200 ticks (one minute)", RiftRules.NATURAL_CHECK_TICKS == 1200, "");
        check("natural: a negative time since the last roll is not due, including one as large as the interval", !RiftRules.naturalRollDue(-1) && !RiftRules.naturalRollDue(-RiftRules.NATURAL_CHECK_TICKS) && !RiftRules.naturalRollDue(-RiftRules.NATURAL_CHECK_TICKS * 5L) && !RiftRules.naturalRollDue(Long.MIN_VALUE + 1), "");
        check("natural: exactly one face of the die wins, out of NATURAL_ODDS", winners() == 1, "winners " + winners());
        check("natural: the odds are 1 in 30", RiftRules.NATURAL_ODDS == 30, "");
        check("natural: cooldown holds at 1 tick before 20 minutes and ends at 20 minutes", RiftRules.inNaturalCooldown(RiftRules.NATURAL_COOLDOWN_TICKS - 1) && !RiftRules.inNaturalCooldown(RiftRules.NATURAL_COOLDOWN_TICKS), "");
        check("natural: cooldown starts at once after a Rift (0 ticks) and a player with none is not cooling", RiftRules.inNaturalCooldown(0) && !RiftRules.inNaturalCooldown(-1), "");
        check("natural: the cooldown is 24000 ticks (20 minutes) and longer than the roll interval", RiftRules.NATURAL_COOLDOWN_TICKS == 24000 && RiftRules.NATURAL_COOLDOWN_TICKS > RiftRules.NATURAL_CHECK_TICKS, "");
        check("spacing: 47.99 refused, 48 allowed, no Rift at all allowed", !RiftRules.spacingOk(47.99) && RiftRules.spacingOk(48.0) && RiftRules.spacingOk(Double.POSITIVE_INFINITY), "");
        check("spacing: a Rift on the same spot (0) is refused", !RiftRules.spacingOk(0.0), "");
        check("distance: 15.99 too near, 16 and 32 fine, 32.01 too far", !RiftRules.naturalDistanceOk(15.99) && RiftRules.naturalDistanceOk(16.0) && RiftRules.naturalDistanceOk(32.0) && !RiftRules.naturalDistanceOk(32.01), "");
        check("distance: the farthest natural Rift is inside the render range, so the player can see it open", RiftRules.NATURAL_MAX_DISTANCE <= RiftRules.RENDER_RANGE && RiftRules.inRenderRange(RiftRules.NATURAL_MAX_DISTANCE), "");
        check("placement: allowed on open air, spaced, outside any run", RiftRules.placementOk(false, 100, true), "");
        check("placement: inside an active run refuses alone", !RiftRules.placementOk(true, 100, true), "");
        check("placement: too close to another Rift refuses alone", !RiftRules.placementOk(false, 10, true), "");
        check("placement: no open air refuses alone", !RiftRules.placementOk(false, 100, false), "");
        check("placement refusal: null when allowed and a name for each reason, in order", RiftRules.placementRefusal(false, 100, true) == null && "inside an active run".equals(RiftRules.placementRefusal(true, 1, false)) && "too close to another Rift".equals(RiftRules.placementRefusal(false, 1, false)) && "no open air".equals(RiftRules.placementRefusal(false, 100, false)), "");
        check("placement refusal agrees with placementOk for every combination", placementAgrees(), "");
        check("render: inclusive at 32, out at 32.01, negative distance refused", RiftRules.inRenderRange(32.0) && !RiftRules.inRenderRange(32.01) && RiftRules.inRenderRange(0) && !RiftRules.inRenderRange(-1), "");
        check("budget: nobody in range spawns nothing, however much is wanted", RiftRules.particlesThisTick(1000, 0) == 0 && RiftRules.particlesThisTick(1000, -3) == 0, "");
        check("budget: never above the per-tick cap, even for a huge request or Integer.MAX_VALUE", RiftRules.particlesThisTick(1000, 1) == RiftRules.BUDGET_PER_TICK && RiftRules.particlesThisTick(Integer.MAX_VALUE, 50) == RiftRules.BUDGET_PER_TICK, "");
        check("budget: a request under the cap passes unchanged, and exactly the cap passes", RiftRules.particlesThisTick(7, 1) == 7 && RiftRules.particlesThisTick(RiftRules.BUDGET_PER_TICK, 1) == RiftRules.BUDGET_PER_TICK, "");
        check("budget: zero and negative requests give zero", RiftRules.particlesThisTick(0, 4) == 0 && RiftRules.particlesThisTick(-5, 4) == 0, "");
        check("budget: more players in range do NOT raise the cost (still capped)", RiftRules.particlesThisTick(1000, 1) == RiftRules.particlesThisTick(1000, 10), "");
        check("budget: the cap is 160 and the idle hum is 40, well inside it", RiftRules.BUDGET_PER_TICK == 160 && RiftRules.IDLE_PER_TICK == 40 && RiftRules.IDLE_PER_TICK < RiftRules.BUDGET_PER_TICK, "");
        check("idle: costs nothing with nobody near and the hum with someone near", RiftRules.idleParticles(0) == 0 && RiftRules.idleParticles(1) == 40, "");
        check("budget: no input in a wide sweep ever exceeds the cap or goes negative", budgetSweepOk(), "");
        // The mutation proof (5 deliberately wrong rules, each must turn this check red) is in the PR; it edits RiftRules, so it cannot live here.
        System.out.println(fails == 0 ? "ALL PASS" : "FAILED " + fails);
        if (fails != 0) System.exit(1);
    }
    static int winners() {
        int n = 0;
        for (int r = 0; r < RiftRules.NATURAL_ODDS; r++) if (RiftRules.naturalRollWins(r)) n++;
        return n;
    }
    static boolean placementAgrees() {
        double[] d = {0, 47.99, 48, 100, Double.POSITIVE_INFINITY};
        for (boolean run : new boolean[] {false, true}) for (double x : d) for (boolean air : new boolean[] {false, true})
            if ((RiftRules.placementRefusal(run, x, air) == null) != RiftRules.placementOk(run, x, air)) return false;
        return true;
    }
    static boolean budgetSweepOk() {
        int[] wanted = {Integer.MIN_VALUE, -1, 0, 1, 39, 40, 159, 160, 161, 5000, Integer.MAX_VALUE};
        int[] players = {Integer.MIN_VALUE, -1, 0, 1, 2, 100, Integer.MAX_VALUE};
        for (int w : wanted) for (int p : players) {
            int n = RiftRules.particlesThisTick(w, p);
            if (n < 0 || n > RiftRules.BUDGET_PER_TICK) return false;
            if ((p <= 0 || w <= 0) && n != 0) return false;
        }
        return true;
    }
    static boolean agree() {
        double[] d = {0, 3.9, 4.0, 4.1, 50}; long[] s = {-1, 0, 100, 199, 200, 5000}; int[] p = {0, 1, 9, 10, 11};
        for (double x : d) for (long y : s) for (int z : p) if ((RiftRules.refusal(x, y, z) == null) != RiftRules.canEnter(x, y, z)) return false;
        return true;
    }
}
