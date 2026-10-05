import com.solme.emberfall.world.StaticMap;
import java.util.*;
public class ChestSpotCheck {
    static int fails = 0;
    static void check(String l, boolean ok, String e) { System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e); if (!ok) fails++; }
    public static void main(String[] a) {
        StaticMap m = StaticMap.get();
        List<int[]> c = m.chestCandidates(3, 15, 80, 12);
        System.out.println("     candidates: " + c.size());
        check("plenty of spots for 10+ chests", c.size() >= 200, "" + c.size());
        boolean flat = true, ring = true, ground = true, clear = true;
        List<Object[]> sh = m.shrineSpots();
        for (int[] p : c) {
            double d = Math.hypot(p[0], p[2]);
            if (d < 15 - 1e-9 || d > 80 + 1e-9) ring = false;
            if (m.height(p[0], p[2]) != 0) ground = false;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (m.height(p[0] + dx, p[2] + dz) != 0) flat = false;
            for (Object[] s : sh) if (Math.hypot(p[0] - (Integer) s[1], p[2] - (Integer) s[2]) < 12 - 1e-9) clear = false;
            if (Math.hypot(p[0] - m.entry()[0], p[2] - m.entry()[1]) < 12 - 1e-9) clear = false;
            if (Math.hypot(p[0] - m.boss()[0], p[2] - m.boss()[1]) < 12 - 1e-9) clear = false;
        }
        check("every spot is between 15 and 80 blocks from the centre", ring, "");
        check("every spot is on height-0 ground", ground, "");
        check("every spot has a flat 3x3 around it", flat, "");
        check("every spot is 12+ from each shrine, the entry and the boss", clear, "shrines " + sh.size());
        check("deterministic: a second call gives the identical list", Arrays.deepEquals(c.toArray(), m.chestCandidates(3, 15, 80, 12).toArray()), "");
        // spread: the spots cover the whole annulus, not one corner (count per quadrant)
        int[] q = new int[4]; for (int[] p : c) q[(p[0] >= 0 ? 0 : 1) + (p[2] >= 0 ? 0 : 2)]++;
        check("all four quadrants have spots (no one-sided map)", q[0] > 20 && q[1] > 20 && q[2] > 20 && q[3] > 20, Arrays.toString(q));
        // a tight filter returns fewer, never more
        check("stricter clearance never adds spots", m.chestCandidates(3, 15, 80, 25).size() <= c.size(), m.chestCandidates(3, 15, 80, 25).size() + " <= " + c.size());
        // props: no spot may have a tree, boulder or house block on it or beside it
        boolean propFree = true; for (int[] p : c) if (m.propNear(p[0], p[2])) propFree = false;
        check("no spot has a prop block on it or in its 3x3", propFree, "");
        check("the 1057 spots before the fix dropped to the prop-free set", c.size() < 1057 && c.size() > 900, "" + c.size());
        System.out.println("     COUNT " + c.size());
        System.out.println(fails == 0 ? "RESULT: ALL PASS" : "RESULT: " + fails + " FAILED"); System.exit(fails == 0 ? 0 : 1);
    }
}
