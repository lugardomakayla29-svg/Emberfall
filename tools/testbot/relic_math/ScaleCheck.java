import com.solme.emberfall.relic.*;
import java.util.Random;
public class ScaleCheck {
    static int fails = 0;
    static void check(String l, boolean ok, String e) { System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e); if (!ok) fails++; }
    public static void main(String[] a) {
        Random r = new Random(11);
        check("x1.0 is exact", RelicMath.scaleWhole(7, 1.0, r::nextDouble) == 7, "");
        check("zero amount pays nothing", RelicMath.scaleWhole(0, 2.5, r::nextDouble) == 0, "");
        check("negative multiplier pays nothing", RelicMath.scaleWhole(5, -1, r::nextDouble) == 0, "");
        check("x2.0 is exactly double, no roll needed", RelicMath.scaleWhole(3, 2.0, () -> 0.9999) == 6, "");
        check("whole part is never lost (10 x1.15 >= 11)", RelicMath.scaleWhole(10, 1.15, () -> 0.9999) >= 11, "");
        check("1 x1.15: fraction .15 hit at roll 0.10", RelicMath.scaleWhole(1, 1.15, () -> 0.10) == 2, "");
        check("1 x1.15: fraction .15 missed at roll 0.20", RelicMath.scaleWhole(1, 1.15, () -> 0.20) == 1, "");
        for (double[] c : new double[][]{{1, 1.15}, {1, 2.5}, {3, 1.45}, {10, 1.30}, {25, 1.15}}) {
            long sum = 0; int N = 600000; for (int i = 0; i < N; i++) sum += RelicMath.scaleWhole((int) c[0], c[1], r::nextDouble);
            double mean = (double) sum / N, want = c[0] * c[1];
            check("mean of " + (int) c[0] + " x" + c[1] + " is " + want, Math.abs(mean - want) < 0.01, String.format("%.4f", mean));
        }
        boolean never = true; for (int i = 0; i < 100000; i++) { int v = RelicMath.scaleWhole(4, 1.3, r::nextDouble); if (v != 5 && v != 6) never = false; }
        check("4 x1.3 only ever pays 5 or 6", never, "");
        System.out.println(fails == 0 ? "RESULT: ALL PASS" : "RESULT: " + fails + " FAILED"); System.exit(fails == 0 ? 0 : 1);
    }
}
