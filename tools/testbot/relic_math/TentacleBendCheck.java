import com.solme.emberfall.boss.BroodtideArmPlan;
import com.solme.emberfall.entity.TentacleMath;
import net.minecraft.world.phys.Vec3;

/**
 * TentacleMath.limitBend and the Broodtide curl, run on the REAL solver (Vec3 is a test-only stand-in). Angles and lengths are re-measured here with this file's
 * own formulas, not read back from the function under test.
 */
public class TentacleBendCheck {
    static int fails = 0;
    static void check(String name, boolean ok, String note) {
        System.out.println((ok ? "PASS " : "FAIL ") + name + (note.isEmpty() ? "" : "  " + note));
        if (!ok) fails++;
    }

    static double turn(Vec3[] j, int i) {                     // the turn at joint i, in degrees, from the three positions
        Vec3 u = j[i].subtract(j[i - 1]), v = j[i + 1].subtract(j[i]);
        double c = u.dot(v) / (u.length() * v.length());
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, c))));
    }
    static double worst(Vec3[] j) { double w = 0; for (int i = 1; i < j.length - 1; i++) w = Math.max(w, turn(j, i)); return w; }
    static double maxBoneError(Vec3[] j, double[] bones) { double e = 0; for (int i = 0; i < bones.length; i++) e = Math.max(e, Math.abs(j[i + 1].distanceTo(j[i]) - bones[i])); return e; }

    /** The arm folding from straight out to the curl pose, solved the way BroodtideArms does it each tick. */
    static Vec3[] fold(int arm, int arms, double spin, double limit, int ticks) {
        double[] bones = BroodtideArmPlan.BONES; int n = bones.length + 1;
        Vec3 root = new Vec3(BroodtideArmPlan.rootDx(arm, arms, spin), BroodtideArmPlan.ROOT_HEIGHT, BroodtideArmPlan.rootDz(arm, arms, spin));
        double[] c = BroodtideArmPlan.curlTarget(arm, arms, spin); Vec3 target = new Vec3(c[0], c[1], c[2]);
        Vec3[] j = new Vec3[n]; TentacleMath.lay(j, root, new Vec3(Math.cos(0.3), 0.1, Math.sin(0.3)), bones);
        for (int t = 0; t < ticks; t++) {
            TentacleMath.solve(j, root, target, bones, 2);
            if (limit < 900) TentacleMath.limitBend(j, root, bones, limit);
        }
        return j;
    }

    /** Same fold, but the arm starts laid straight out along its own root bearing: the pose it really has when the Tide closes. */
    static Vec3[] foldFromOutward(int arm, int arms, double spin, double limit, int ticks) {
        double[] bones = BroodtideArmPlan.BONES; int n = bones.length + 1;
        double rx = BroodtideArmPlan.rootDx(arm, arms, spin), rz = BroodtideArmPlan.rootDz(arm, arms, spin);
        Vec3 root = new Vec3(rx, BroodtideArmPlan.ROOT_HEIGHT, rz);
        double[] c = BroodtideArmPlan.curlTarget(arm, arms, spin); Vec3 target = new Vec3(c[0], c[1], c[2]);
        double bearing = Math.atan2(rz, rx);
        Vec3[] j = new Vec3[n]; TentacleMath.lay(j, root, new Vec3(Math.cos(bearing), 0.1, Math.sin(bearing)), bones);
        for (int t = 0; t < ticks; t++) {
            TentacleMath.solve(j, root, target, bones, 2);
            TentacleMath.limitBend(j, root, bones, limit);
        }
        return j;
    }

    public static void main(String[] args) {
        double[] bones = BroodtideArmPlan.BONES;
        double lim = BroodtideArmPlan.MAX_BEND_DEG;
        check("T0 the limit is a sane number", lim > 5 && lim < 90, "limit " + lim);

        // Without the limiter the solver folds with a hard elbow: proves the limiter has something to do (the problem is real).
        double rawWorst = 0;
        for (int arm = 0; arm < 5; arm++) rawWorst = Math.max(rawWorst, worst(fold(arm, 5, 0.0, 999, 200)));
        check("T1 WITHOUT the limiter the curl folds with a hard elbow (over twice the limit)", rawWorst > 2 * lim, "worst raw " + rawWorst);

        for (int arm = 0; arm < 5; arm++) {
            for (double spin : new double[] {0.0, 1.3, 4.1}) {
                Vec3[] j = fold(arm, 5, spin, lim, 200);
                double w = worst(j);
                check("T2 arm " + arm + " spin " + spin + ": no joint turns more than the limit", w <= lim + 1e-6, "worst " + w);
                check("T3 arm " + arm + " spin " + spin + ": every bone keeps its length", maxBoneError(j, bones) < 1e-6, "err " + maxBoneError(j, bones));
                Vec3 root = new Vec3(BroodtideArmPlan.rootDx(arm, 5, spin), BroodtideArmPlan.ROOT_HEIGHT, BroodtideArmPlan.rootDz(arm, 5, spin));
                check("T4 arm " + arm + " spin " + spin + ": the root stays put", j[0].distanceTo(root) < 1e-9, "");
                double[] c = BroodtideArmPlan.curlTarget(arm, 5, spin);
                double miss = j[j.length - 1].distanceTo(new Vec3(c[0], c[1], c[2]));
                check("T5 arm " + arm + " spin " + spin + ": from an arbitrary start pose the tip settles within 1.1 blocks of the curl target (measured worst 0.98: the limiter can leave a valid horn a little short, permanently)", miss < 1.1, "miss " + miss);
                Vec3[] out = foldFromOutward(arm, 5, spin, lim, 200);
                double[] cc = BroodtideArmPlan.curlTarget(arm, 5, spin);
                double missOut = out[out.length - 1].distanceTo(new Vec3(cc[0], cc[1], cc[2]));
                check("T5b arm " + arm + " spin " + spin + ": an arm that starts OUT (how a real Ebb to Flood curl begins) reaches the curl target exactly", missOut < 0.05, "miss " + missOut);
            }
        }

        // A chain that is already gentle is left alone.
        Vec3 root = new Vec3(0, 0, 0); Vec3[] straight = new Vec3[bones.length + 1];
        TentacleMath.lay(straight, root, new Vec3(1, 0, 0), bones);
        Vec3[] copy = straight.clone();
        TentacleMath.limitBend(straight, root, bones, lim);
        double moved = 0; for (int i = 0; i < copy.length; i++) moved = Math.max(moved, copy[i].distanceTo(straight[i]));
        check("T6 a straight chain is not moved by the limiter", moved < 1e-9, "moved " + moved);

        // A right-angle chain is bent no more than the limit allowed per joint, and still ends in the right direction family (turns the same way).
        Vec3[] sharp = new Vec3[bones.length + 1];
        sharp[0] = root; Vec3 p = root;
        for (int i = 0; i < bones.length; i++) { Vec3 d = i < 3 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0); p = p.add(d.scale(bones[i])); sharp[i + 1] = p; }
        TentacleMath.limitBend(sharp, root, bones, 20.0);
        check("T7 a 90 degree corner is spread to at most the limit (20) per joint", worst(sharp) <= 20.0 + 1e-6, "worst " + worst(sharp));
        check("T8 a tighter limit never produces a sharper joint than a looser one", worst(fold(0, 5, 0.0, 20, 200)) <= worst(fold(0, 5, 0.0, 35, 200)) + 1e-6, "");

        // Degenerate input must not produce NaN.
        Vec3[] zero = new Vec3[bones.length + 1]; for (int i = 0; i < zero.length; i++) zero[i] = root;
        TentacleMath.limitBend(zero, root, bones, lim);
        boolean finite = true; for (Vec3 v : zero) finite &= Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
        check("T9 a chain collapsed onto its root gives finite positions, no NaN", finite, "");

        System.out.println(fails == 0 ? "ALL PASS" : "FAILED " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }
}
