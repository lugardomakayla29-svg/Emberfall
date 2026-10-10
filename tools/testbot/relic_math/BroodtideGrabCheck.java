import com.solme.emberfall.boss.BroodtideGrab;
import com.solme.emberfall.boss.BroodtideGrab.Phase;
import com.solme.emberfall.boss.TideClock;

/**
 * Pure checks for the Broodtide's phases and Grab (docs/PLAN_broodtide.md section 8 test 5). They prove the rules that protect a player: the single
 * impulse never goes into the body, never points down, never carries past the stop radius; only the two allowed effects exist; a phase never goes
 * backwards; a grab never lands in Flood. They do NOT prove the entity applies them (the live test does).
 */
public class BroodtideGrabCheck {
    static int fails = 0, total = 0;

    static void check(String l, boolean ok, String e) {
        total++;
        System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e);
        if (!ok) fails++;
    }

    public static void main(String[] a) {
        // ---- phases ----
        check("G1 full health is phase ONE", BroodtideGrab.phaseFor(1.0) == Phase.ONE, "");
        check("G2 just above 2/3 is still ONE", BroodtideGrab.phaseFor(0.67) == Phase.ONE, "");
        check("G3 exactly 2/3 is already TWO (boundary belongs to the harder phase)", BroodtideGrab.phaseFor(2.0 / 3.0) == Phase.TWO, "");
        check("G4 half health is TWO", BroodtideGrab.phaseFor(0.5) == Phase.TWO, "");
        check("G5 exactly 1/3 is already THREE", BroodtideGrab.phaseFor(1.0 / 3.0) == Phase.THREE, "");
        check("G6 a nearly dead boss is THREE", BroodtideGrab.phaseFor(0.01) == Phase.THREE, "");
        check("G7 out-of-range fractions clamp (1.5 is ONE, -1 is THREE)", BroodtideGrab.phaseFor(1.5) == Phase.ONE && BroodtideGrab.phaseFor(-1) == Phase.THREE, "");
        check("G8 a phase never goes backwards when the boss heals", BroodtideGrab.latch(Phase.THREE, 0.9) == Phase.THREE && BroodtideGrab.latch(Phase.TWO, 1.0) == Phase.TWO, "");
        check("G9 the latch still advances", BroodtideGrab.latch(Phase.ONE, 0.5) == Phase.TWO && BroodtideGrab.latch(Phase.TWO, 0.2) == Phase.THREE, "");

        // ---- timing ----
        check("G10 the wind-up meets the 0.9 s telegraph floor (18 ticks)", BroodtideGrab.WINDUP_TICKS >= 18, "windup=" + BroodtideGrab.WINDUP_TICKS);
        check("G11 cooldowns shrink as phases advance", BroodtideGrab.cooldownTicks(Phase.ONE) > BroodtideGrab.cooldownTicks(Phase.TWO)
                && BroodtideGrab.cooldownTicks(Phase.TWO) > BroodtideGrab.cooldownTicks(Phase.THREE), "");
        check("G12 phase three may grab two players, the others one", BroodtideGrab.maxTargets(Phase.THREE) == 2 && BroodtideGrab.maxTargets(Phase.ONE) == 1, "");
        check("G13 a grab may start early in Ebb with no cooldown history", BroodtideGrab.mayStart(10, -1, Phase.ONE, false), "");
        check("G14 never in Flood", !BroodtideGrab.mayStart(TideClock.EBB_TICKS + 5, -1, Phase.ONE, false), "");
        check("G15 never when a grab is already running", !BroodtideGrab.mayStart(10, -1, Phase.ONE, true), "");
        check("G16 not before the cooldown has elapsed", !BroodtideGrab.mayStart(100, 0, Phase.ONE, false) && BroodtideGrab.mayStart(200, 0, Phase.ONE, false), "");
        boolean landsInFlood = false;
        for (long t = 0; t < TideClock.CYCLE_TICKS * 3L; t++) {
            if (BroodtideGrab.mayStart(t, -1, Phase.ONE, false) && TideClock.stateAt(t + BroodtideGrab.WINDUP_TICKS) != TideClock.State.EBB) landsInFlood = true;
        }
        check("G17 over three full tides no allowed start ever LANDS in Flood", !landsInFlood, "");

        // ---- reach ----
        check("G18 a player in reach and outside the stop radius can be grabbed", BroodtideGrab.inReach(10.0), "");
        check("G19 beyond reach cannot", !BroodtideGrab.inReach(BroodtideGrab.REACH + 0.1), "");
        check("G20 inside the stop radius cannot (nothing to pull)", !BroodtideGrab.inReach(BroodtideGrab.STOP_RADIUS), "");

        // ---- the one impulse: scan a grid of player positions around a body at the origin ----
        boolean anyDown = false, anyIntoBody = false, anyPastStop = false, anyInsideMoved = false, anyWrongDir = false, anyOverSpeed = false;
        double worstLand = 1e9; int samples = 0;
        for (double px = -20; px <= 20; px += 0.5) {
            for (double pz = -20; pz <= 20; pz += 0.5) {
                double dist = Math.hypot(px, pz);
                double[] v = BroodtideGrab.impulse(0, 0, px, pz);
                samples++;
                if (v[1] < 0) anyDown = true;
                double sp = Math.hypot(v[0], v[2]);
                if (sp > BroodtideGrab.IMPULSE_SPEED + 1e-9) anyOverSpeed = true;
                if (dist <= BroodtideGrab.STOP_RADIUS && sp > 1e-12) anyInsideMoved = true;
                if (sp > 1e-12) {
                    if (v[0] * px + v[2] * pz >= 0) anyWrongDir = true;                 // must point toward the body: opposite to the player's offset
                    double land = dist - BroodtideGrab.travel(sp);
                    worstLand = Math.min(worstLand, land);
                    if (land < BroodtideGrab.STOP_RADIUS - 1e-6) anyPastStop = true;
                    if (land < 0) anyIntoBody = true;
                }
            }
        }
        check("G21 over " + samples + " positions the impulse never points down", !anyDown, "");
        check("G22 it never exceeds the speed cap", !anyOverSpeed, "");
        check("G23 a player already inside the stop radius is not moved at all", !anyInsideMoved, "");
        check("G24 every impulse points toward the body", !anyWrongDir, "");
        check("G25 nobody is carried past the stop radius (worst landing " + String.format("%.2f", worstLand) + ")", !anyPastStop, "");
        check("G26 nobody is ever carried into the body centre", !anyIntoBody, "");
        double[] same = BroodtideGrab.impulse(3, 3, 3, 3);
        check("G27 a player exactly on the body gets a zero impulse, not NaN", same[0] == 0 && same[2] == 0 && !Double.isNaN(same[0]), "");

        // ---- effects ----
        check("G28 exactly two effects", BroodtideGrab.EFFECTS.length == 2, "n=" + BroodtideGrab.EFFECTS.length);
        check("G29 they are Slowness and Mining Fatigue, nothing else", BroodtideGrab.EFFECTS[0].equals("minecraft:slowness") && BroodtideGrab.EFFECTS[1].equals("minecraft:mining_fatigue"), "");
        boolean harmful = false;
        for (String e : BroodtideGrab.EFFECTS) if (e.contains("poison") || e.contains("wither") || e.contains("blind") || e.contains("nausea") || e.contains("weak")) harmful = true;
        check("G30 no damage, blind, nausea or weakness effect hides in the list", !harmful, "");

        // ---- ending ----
        check("G31 a grab is not over mid-hold", !BroodtideGrab.ended(0, 50, false), "");
        check("G32 it ends when the hold has elapsed", BroodtideGrab.ended(0, BroodtideGrab.WINDUP_TICKS + BroodtideGrab.HOLD_TICKS, false), "");
        check("G33 a cancel ends it at once (boss died, run ended)", BroodtideGrab.ended(0, 1, true), "");

        System.out.println();
        System.out.println(fails == 0 ? "ALL PASS (" + total + " checks)" : "SOME FAIL (" + fails + " of " + total + ")");
        if (fails > 0) System.exit(1);
    }
}
