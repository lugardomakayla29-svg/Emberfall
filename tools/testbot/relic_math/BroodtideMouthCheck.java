import com.solme.emberfall.boss.BroodtideMouth;

/** Pure checks for the particle mouth. PASS/FAIL lines and a verdict; exit 1 on failure. */
public class BroodtideMouthCheck {
    static int fails = 0, total = 0;
    static void check(String n, boolean ok, String note) { total++; System.out.println((ok ? "PASS " : "FAIL ") + n + (note.isEmpty() ? "" : " " + note)); if (!ok) fails++; }

    public static void main(String[] a) {
        boolean count = true, finite = true, bounded = true;
        for (int sh = 0; sh < BroodtideMouth.SHAPES; sh++) {
            for (double ph = 0; ph <= 1.0; ph += 0.125) {
                double[][] p = BroodtideMouth.points(sh, ph);
                if (p.length != BroodtideMouth.POINTS) count = false;
                for (double[] q : p) {
                    if (!Double.isFinite(q[0]) || !Double.isFinite(q[1])) finite = false;
                    if (Math.abs(q[0]) > 1.3 || Math.abs(q[1]) > 1.3) bounded = false;
                }
            }
        }
        check("M1 every shape at every phase has exactly POINTS points (a constant particle cost)", count, "");
        check("M2 every coordinate is a finite number", finite, "");
        check("M3 every point stays inside 1.3 mouth radii (the shape cannot fly off the face)", bounded, "");
        boolean cycle = true;
        for (int sh = 0; sh < BroodtideMouth.SHAPES; sh++) if (BroodtideMouth.shapeAt((long) sh * BroodtideMouth.HOLD_TICKS) != sh) cycle = false;
        check("M4 the shapes take turns in order, HOLD_TICKS each", cycle, "");
        check("M5 after the last shape it starts over with the first", BroodtideMouth.shapeAt((long) BroodtideMouth.SHAPES * BroodtideMouth.HOLD_TICKS) == 0, "");
        check("M6 a shape holds for its whole HOLD_TICKS, not one tick less", BroodtideMouth.shapeAt(BroodtideMouth.HOLD_TICKS - 1) == 0 && BroodtideMouth.shapeAt(BroodtideMouth.HOLD_TICKS) == 1, "");
        check("M7 a negative tick shows the first shape and never throws", BroodtideMouth.shapeAt(-5) == 0, "");
        check("M8 it redraws every REDRAW_TICKS ticks and not in between", BroodtideMouth.redrawsAt(0) && BroodtideMouth.redrawsAt(BroodtideMouth.REDRAW_TICKS) && !BroodtideMouth.redrawsAt(1) && !BroodtideMouth.redrawsAt(BroodtideMouth.REDRAW_TICKS - 1) && !BroodtideMouth.redrawsAt(-4), "");
        boolean distinct = true;
        for (int x = 0; x < BroodtideMouth.SHAPES; x++) for (int y = x + 1; y < BroodtideMouth.SHAPES; y++) {
            double[][] A = BroodtideMouth.points(x, 0), B = BroodtideMouth.points(y, 0); double diff = 0;
            for (int i = 0; i < A.length; i++) diff += Math.abs(A[i][0] - B[i][0]) + Math.abs(A[i][1] - B[i][1]);
            if (diff < 1.0) distinct = false;
        }
        check("M9 no two shapes are the same picture (each looks different)", distinct, "");
        boolean moves = true;
        for (int sh = 0; sh < BroodtideMouth.SHAPES; sh++) {
            double[][] A = BroodtideMouth.points(sh, 0.0), B = BroodtideMouth.points(sh, 0.25); double diff = 0;
            for (int i = 0; i < A.length; i++) diff += Math.abs(A[i][0] - B[i][0]) + Math.abs(A[i][1] - B[i][1]);
            if (diff < 0.05) moves = false;
        }
        check("M10 every shape animates: a quarter turn of the phase changes its points", moves, "");
        double[][] arrow = BroodtideMouth.points(BroodtideMouth.ARROW, 0.0); double lowest = 9, lowX = 9;
        for (double[] q : arrow) if (q[1] < lowest) { lowest = q[1]; lowX = Math.abs(q[0]); }
        check("M11 the arrow points DOWN: its lowest point is on the centre line", lowX < 0.1 && lowest < -0.5, "lowest y " + lowest + " at |x| " + lowX);
        double[][] c = BroodtideMouth.points(BroodtideMouth.CIRCLE, 0.0); boolean ring = true;
        for (double[] q : c) if (Math.abs(Math.hypot(q[0], q[1]) - 0.9) > 1e-9) ring = false;
        check("M12 the circle is a true ring of radius 0.9", ring, "");
        double[][] g = BroodtideMouth.points(BroodtideMouth.GRIN, 0.0); double minX = 9, maxX = -9;
        for (double[] q : g) { minX = Math.min(minX, q[0]); maxX = Math.max(maxX, q[0]); }
        check("M13 the grin is wide (spans the face) and symmetric", maxX - minX > 1.8 && Math.abs(maxX + minX) < 1e-9, "span " + (maxX - minX));
        check("M14 an unknown shape number gives the circle", java.util.Arrays.deepEquals(BroodtideMouth.points(99, 0.3), BroodtideMouth.points(BroodtideMouth.CIRCLE, 0.3)), "");
        System.out.println(fails == 0 ? "ALL PASS (" + total + " checks)" : "FAILED " + fails + " of " + total);
        System.exit(fails == 0 ? 0 : 1);
    }
}
