import com.solme.emberfall.bot.BotPersonality;
import com.solme.emberfall.bot.BotPersonality.Kind;
import com.solme.emberfall.bot.BotPlan;
import com.solme.emberfall.bot.BotSteer;
import com.solme.emberfall.bot.BotSteer.Mate;
import com.solme.emberfall.bot.BotSteer.Push;
import java.util.*;

/** Pure proof of BotPersonality and BotSteer. Each check names the fact; the CLUMP ones reproduce the live failure in numbers. */
public class BotMindCheck {
    static int fails = 0;
    static void check(String l, boolean ok, String e) { System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e); if (!ok) fails++; }

    public static void main(String[] args) {
        // ---- personality ----
        BotPersonality a = BotPersonality.fromSeed(12345L), b = BotPersonality.fromSeed(12345L);
        check("the same seed deals the same bot", a.kind() == b.kind() && a.boldness() == b.boldness() && a.reactionTicks() == b.reactionTicks(), a.kind() + "");
        Set<Kind> kinds = new HashSet<>();
        for (long s = 0; s < 400; s++) kinds.add(BotPersonality.fromSeed(s).kind());
        check("400 seeds deal all four kinds", kinds.size() == 4, kinds + "");
        int[] count = new int[4];
        for (long s = 0; s < 4000; s++) count[BotPersonality.fromSeed(s).kind().ordinal()]++;
        int min = Arrays.stream(count).min().getAsInt(), max = Arrays.stream(count).max().getAsInt();
        check("the kinds are dealt roughly evenly (each 20..30% of 4000)", min > 800 && max < 1200, Arrays.toString(count));

        double rushB = 0, cowB = 0, rushFlee = 0, cowFlee = 0, rushStand = 0, cowStand = 0, rushStrafe = 0, guardLeash = 0, cowLeash = 0;
        int n = 200;
        for (int i = 0; i < n; i++) {
            Random r = new Random(i);
            BotPersonality ru = BotPersonality.of(Kind.RUSHER, r), co = BotPersonality.of(Kind.COWARD, r), gu = BotPersonality.of(Kind.GUARDIAN, r);
            rushB += ru.boldness(); cowB += co.boldness(); rushFlee += ru.fleeBelow(); cowFlee += co.fleeBelow();
            rushStand += ru.standOff(6.0); cowStand += co.standOff(6.0); rushStrafe += ru.strafeShare(); guardLeash += gu.allyLeash(); cowLeash += co.allyLeash();
        }
        check("a rusher is bolder than a coward", rushB / n > cowB / n + 0.5, String.format("%.2f vs %.2f", rushB / n, cowB / n));
        check("a rusher flees at a much lower health than a coward", rushFlee / n < 0.2 && cowFlee / n > 0.35, String.format("%.2f vs %.2f", rushFlee / n, cowFlee / n));
        check("a rusher stands closer to a foe than a coward (same weapon)", rushStand / n + 2.0 < cowStand / n, String.format("%.1f vs %.1f blocks", rushStand / n, cowStand / n));
        check("a guardian's ally leash stays within 4..18 blocks", guardLeash / n >= 4.0 && guardLeash / n <= 18.0, String.format("%.1f", guardLeash / n));
        check("no stand-off is ever below 1.5 blocks", stands(1.0) >= 1.5 && stands(0.0) >= 1.5, stands(1.0) + " " + stands(0.0));
        BotPersonality hurt = BotPersonality.of(Kind.COWARD, new Random(1));
        check("CONTROL: a coward at full health does not flee, at 10% it does", !hurt.shouldFlee(1.0) && hurt.shouldFlee(0.10), "flee below " + hurt.fleeBelow());
        BotPersonality rush = BotPersonality.of(Kind.RUSHER, new Random(1));
        check("CONTROL: a rusher at 30% health still fights", !rush.shouldFlee(0.30), "flee below " + rush.fleeBelow());
        check("every reaction delay is 4..12 ticks", reactionsOk(), "");

        // ---- steering: the live failure, in numbers ----
        // four allies standing at the SAME spot: old behaviour kept them there. Separation must push them apart, and must never be NaN.
        List<Mate> stack = List.of(new Mate(0, 0), new Mate(0, 0), new Mate(0, 0));
        Push onTop = BotSteer.separation(0, 0, stack, 3.0, 2.0, 0.7);
        check("allies exactly on top still give a real push (not zero, not NaN)", Double.isFinite(onTop.dx()) && Double.isFinite(onTop.dz()) && Math.hypot(onTop.dx(), onTop.dz()) > 0.5, onTop + "");
        Push far = BotSteer.separation(0, 0, List.of(new Mate(10, 0)), 3.0, 2.0, 0.0);
        check("CONTROL: an ally beyond the radius gives no push", far.dx() == 0 && far.dz() == 0, far + "");
        Push left = BotSteer.separation(0, 0, List.of(new Mate(-1, 0)), 3.0, 2.0, 0.0);
        check("an ally on the left pushes right, and not up or down", left.dx() > 0 && Math.abs(left.dz()) < 1e-9, left + "");
        check("the push never exceeds the cap", Math.hypot(onTop.dx(), onTop.dz()) <= 2.0 + 1e-9, "" + Math.hypot(onTop.dx(), onTop.dz()));
        Push near = BotSteer.separation(0, 0, List.of(new Mate(-0.5, 0)), 3.0, 2.5, 0.0), edge = BotSteer.separation(0, 0, List.of(new Mate(-2.5, 0)), 3.0, 2.5, 0.0);
        check("a closer ally pushes harder than a distant one", near.dx() > edge.dx(), near.dx() + " vs " + edge.dx());

        // slots: four bots around one foe stand at four different points, all at the stand-off distance
        double[][] pts = new double[4][];
        for (int i = 0; i < 4; i++) pts[i] = BotSteer.slotPoint(50, 50, 6.0, BotSteer.slotBearing(i, 4, 0.2));
        double minPair = 1e9;
        for (int i = 0; i < 4; i++) for (int j = i + 1; j < 4; j++) minPair = Math.min(minPair, Math.hypot(pts[i][0] - pts[j][0], pts[i][1] - pts[j][1]));
        check("CLUMP: four bots around one foe stand at least 6 blocks apart (live baseline was 0.00)", minPair >= 6.0, String.format("min pair %.2f", minPair));
        boolean ring = true;
        for (double[] p : pts) ring &= Math.abs(Math.hypot(p[0] - 50, p[1] - 50) - 6.0) < 1e-9;
        check("each slot is exactly the stand-off from the foe", ring, "");
        check("slot bearings of different party sizes still cover the circle", Math.abs(BotSteer.slotBearing(3, 4, 0) - 3 * Math.PI / 2) < 1e-9, "");
        check("a negative or oversize index wraps instead of crashing", Double.isFinite(BotSteer.slotBearing(-1, 4, 0)) && Double.isFinite(BotSteer.slotBearing(9, 4, 0)), "");

        // iterate the old rule and the new rule for 4 bots that start at the same spot and see the spread
        double oldSpread = simulate(false), newSpread = simulate(true);
        check("CLUMP end-to-end: with the old rule four bots end on one spot", oldSpread < 0.5, String.format("%.2f blocks", oldSpread));
        check("CLUMP end-to-end: with separation + slots they end at least 4 blocks apart", newSpread >= 4.0, String.format("%.2f blocks", newSpread));

        // foe choice spreads a party
        List<BotPlan.Foe> foes = List.of(new BotPlan.Foe(10, 0, 0), new BotPlan.Foe(12, 0, 0), new BotPlan.Foe(14, 0, 0));
        int[] claims = new int[3];
        Set<Integer> chosen = new HashSet<>();
        for (int bot = 0; bot < 3; bot++) { int p = BotSteer.pickFoe(0, 0, foes, claims, 40, 0.5, 6.0); claims[p]++; chosen.add(p); }
        check("three bots with a claim cost take three different foes", chosen.size() == 3, chosen + "");
        int[] noClaims = new int[3]; Set<Integer> same = new HashSet<>();
        for (int bot = 0; bot < 3; bot++) same.add(BotSteer.pickFoe(0, 0, foes, noClaims, 40, 0.5, 0.0));
        check("CONTROL: with no claim cost all three take the nearest (the old behaviour)", same.equals(Set.of(0)), same + "");
        check("a foe beyond sight is ignored; none in sight gives -1", BotSteer.pickFoe(0, 0, List.of(new BotPlan.Foe(100, 0, 0)), null, 40, 0.5, 6.0) == -1, "");
        List<BotPlan.Foe> crowd = List.of(new BotPlan.Foe(10, 0, 0), new BotPlan.Foe(11, 0, 6));
        check("a bold bot prefers the crowded foe, a timid one the lonely one", BotSteer.pickFoe(0, 0, crowd, null, 40, 1.0, 0) == 1 && BotSteer.pickFoe(0, 0, crowd, null, 40, 0.0, 0) == 0, "");

        System.out.println(fails == 0 ? "ALL PASS" : "FAILED " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }

    static double stands(double boldness) {
        // build a personality near the bounds through the public path: rusher (bold) and coward (timid) bound the range
        double lo = 1e9;
        for (int i = 0; i < 100; i++) { Random r = new Random(i); lo = Math.min(lo, BotPersonality.of(Kind.RUSHER, r).standOff(boldness)); lo = Math.min(lo, BotPersonality.of(Kind.COWARD, r).standOff(boldness)); }
        return lo;
    }

    static boolean reactionsOk() {
        for (long s = 0; s < 2000; s++) { int t = BotPersonality.fromSeed(s).reactionTicks(); if (t < 4 || t > 12) return false; }
        return true;
    }

    /** Four bots start on the same spot with the same foe 20 blocks away. Returns the smallest pair distance after 200 steps. */
    static double simulate(boolean steer) {
        double[][] p = new double[4][2];
        for (int step = 0; step < 200; step++) {
            for (int i = 0; i < 4; i++) {
                double gx, gz;
                if (steer) {
                    double[] s = BotSteer.slotPoint(20, 0, 6.0, BotSteer.slotBearing(i, 4, 0.2));
                    gx = s[0]; gz = s[1];
                } else {
                    double dx = p[i][0] - 20, dz = p[i][1] - 0, d = Math.hypot(dx, dz);
                    gx = d < 1e-9 ? 20 - 6 : 20 + dx / d * 6; gz = d < 1e-9 ? 0 : 0 + dz / d * 6;
                }
                List<Mate> mates = new ArrayList<>();
                for (int j = 0; j < 4; j++) if (j != i) mates.add(new Mate(p[j][0], p[j][1]));
                Push push = steer ? BotSteer.separation(p[i][0], p[i][1], mates, 3.0, 1.5, i * 1.3) : new Push(0, 0);
                double tx = gx + push.dx() - p[i][0], tz = gz + push.dz() - p[i][1], d = Math.hypot(tx, tz);
                double stepLen = Math.min(0.4, d);
                if (d > 1e-9) { p[i][0] += tx / d * stepLen; p[i][1] += tz / d * stepLen; }
            }
        }
        double m = 1e9;
        for (int i = 0; i < 4; i++) for (int j = i + 1; j < 4; j++) m = Math.min(m, Math.hypot(p[i][0] - p[j][0], p[i][1] - p[j][1]));
        return m;
    }
}
