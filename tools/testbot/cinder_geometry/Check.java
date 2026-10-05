import java.lang.reflect.Method;
import java.util.Random;
public class Check {
    public static void main(String[] a) throws Exception {
        Class<?> c = Lanes.class;
        Method strikes = c.getDeclaredMethod("cinderStrikes", double.class, double.class, int.class, int.class, int.class);
        Method pick = c.getDeclaredMethod("pickOpenLanes", Random.class, int.class, int.class);
        strikes.setAccessible(true); pick.setAccessible(true);
        var depth = c.getDeclaredField("CINDER_ROW_DEPTH"); depth.setAccessible(true);
        var width = c.getDeclaredField("CINDER_LANE_WIDTH"); width.setAccessible(true);
        double D = depth.getDouble(null), W = width.getDouble(null);
        int LANES = 7, ROWS = 4, fails = 0;
        System.out.println("depth " + D + " lane width " + W);
        // 1. standing at the centre of the open lane is never struck; the centre of any other lane always is (middle of the depth)
        for (int open = 0; open < LANES; open++) {
            for (int lane = 0; lane < LANES; lane++) {
                double across = (lane - LANES / 2) * W;
                boolean s = (boolean) strikes.invoke(null, D / 2, across, 0, open, LANES);
                if (s == (lane == open)) { fails++; System.out.println("FAIL open=" + open + " lane=" + lane + " struck=" + s); }
            }
        }
        // 2. anywhere in the open lane (sweep across its full width and the full depth) is safe; just outside it is struck
        for (int open = 0; open < LANES; open++) {
            double lo = (open - LANES / 2) * W - W / 2, hi = lo + W;
            for (double x = lo + 0.01; x < hi; x += 0.25) for (double al = 0.0; al < D; al += 1.0) {
                if ((boolean) strikes.invoke(null, al, x, 0, open, LANES)) { fails++; System.out.println("FAIL inside open lane struck " + open + " " + x + " " + al); }
            }
        }
        // 3. outside the strip (behind, beyond the far end, past the side walls) is never struck
        if ((boolean) strikes.invoke(null, -0.01, 0.0, 0, 6, LANES)) { fails++; System.out.println("FAIL behind struck"); }
        if ((boolean) strikes.invoke(null, D, 0.0, 0, 6, LANES)) { fails++; System.out.println("FAIL past far end struck"); }
        if ((boolean) strikes.invoke(null, D / 2, LANES * W / 2 + 0.01, 0, 0, LANES)) { fails++; System.out.println("FAIL beyond side struck"); }
        // 4. the generated plan: 100000 plans, first lane within one of the middle, every later step exactly one lane, always in range
        Random rng = new Random(12345); int[] firstCount = new int[LANES]; int badStep = 0, badRange = 0, badFirst = 0;
        for (int n = 0; n < 100000; n++) {
            int[] plan = (int[]) pick.invoke(null, rng, ROWS, LANES);
            firstCount[plan[0]]++;
            if (Math.abs(plan[0] - LANES / 2) > 1) badFirst++;
            for (int r = 0; r < ROWS; r++) { if (plan[r] < 0 || plan[r] >= LANES) badRange++; if (r > 0 && Math.abs(plan[r] - plan[r - 1]) != 1) badStep++; }
        }
        System.out.println("first lane histogram " + java.util.Arrays.toString(firstCount) + " badFirst " + badFirst + " badStep " + badStep + " badRange " + badRange);
        fails += badFirst + badStep + badRange;
        System.out.println(fails == 0 ? "CINDER GEOMETRY ALL PASS" : "CINDER GEOMETRY FAILED " + fails);
    }
}
