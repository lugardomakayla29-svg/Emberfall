import com.solme.emberfall.relic.*;
import java.util.Random;

public class Check {
    static int fails = 0;
    static void check(String label, boolean ok, String extra) { System.out.println((ok ? "PASS " : "FAIL ") + label + "  " + extra); if (!ok) fails++; }
    public static void main(String[] a) {
        // price curve
        check("price(0)=30", RelicMath.chestPrice(0) == 30, "" + RelicMath.chestPrice(0));
        check("price(1)=38", RelicMath.chestPrice(1) == 38, "" + RelicMath.chestPrice(1));
        check("price strictly rises until the int ceiling at opening 82", rising(), "p81=" + RelicMath.chestPrice(81));
        check("price holds at the ceiling after that", RelicMath.chestPrice(82) == Integer.MAX_VALUE && RelicMath.chestPrice(200) == Integer.MAX_VALUE, "");
        check("price never overflows", RelicMath.chestPrice(100000) == Integer.MAX_VALUE, "" + RelicMath.chestPrice(100000));
        check("negative opened is base", RelicMath.chestPrice(-5) == 30, "");
        // weights
        for (double luck : new double[]{0, 10, 50, 100, 500, -20}) {
            double[] w = RelicMath.weights(luck);
            double s = w[0] + w[1] + w[2] + w[3];
            check("weights sum 100 at luck " + luck, Math.abs(s - 100) < 1e-9, String.format("%.3f %.3f %.3f %.3f", w[0], w[1], w[2], w[3]));
            check("no tier is zero at luck " + luck, w[0] > 0 && w[1] > 0 && w[2] > 0 && w[3] > 0, "");
        }
        double[] w0 = RelicMath.weights(0), w100 = RelicMath.weights(100);
        check("luck 0 is the base table", w0[0] == 60 && w0[1] == 28 && w0[2] == 10 && w0[3] == 2, "");
        check("luck raises every upper tier", w100[1] > w0[1] && w100[2] > w0[2] && w100[3] > w0[3], "");
        check("luck lowers common but keeps the floor", w100[0] < w0[0] && w100[0] >= 60 * 0.15 - 1e-9, "common@100=" + w100[0]);
        // rolled distribution matches the weights
        Random rnd = new Random(42);
        for (double luck : new double[]{0, 50, 100}) {
            int[] n = new int[4]; int N = 400000;
            for (int i = 0; i < N; i++) n[RelicMath.rollRarity(luck, rnd::nextDouble).ordinal()]++;
            double[] w = RelicMath.weights(luck);
            boolean ok = true; StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) { double got = 100.0 * n[i] / N; sb.append(String.format("%.2f/%.2f ", got, w[i])); if (Math.abs(got - w[i]) > 0.4) ok = false; }
            check("rolled share matches weights at luck " + luck, ok, sb.toString());
        }
        check("edge roll 0.0 is COMMON", RelicMath.rollRarity(0, () -> 0.0) == RelicRarity.COMMON, "");
        check("edge roll 0.9999999 is LEGENDARY", RelicMath.rollRarity(0, () -> 0.9999999) == RelicRarity.LEGENDARY, "");
        // key
        check("key 0 stacks = 0", RelicMath.keyChance(0) == 0.0, "");
        check("key 1 stack = 10%", Math.abs(RelicMath.keyChance(1) - 0.10) < 1e-12, "");
        check("key 5 stacks = 50% cap", Math.abs(RelicMath.keyChance(5) - 0.50) < 1e-12, "");
        check("key 10 stacks still 50% cap", Math.abs(RelicMath.keyChance(10) - 0.50) < 1e-12, "");
        check("higher() ladder", RelicRarity.COMMON.higher() == RelicRarity.UNCOMMON && RelicRarity.LEGENDARY.higher() == RelicRarity.LEGENDARY, "");
        // economy read-out for the design doc
        StringBuilder sb = new StringBuilder("price after N opens: ");
        for (int n : new int[]{0, 1, 2, 3, 5, 8, 10, 15, 20, 30}) sb.append(n).append("=").append(RelicMath.chestPrice(n)).append(" ");
        System.out.println(sb);
        System.out.println(fails == 0 ? "RESULT: ALL PASS" : "RESULT: " + fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
    static boolean rising() { int p = 0; for (int i = 0; i < 82; i++) { int c = RelicMath.chestPrice(i); if (c <= p && i > 0) return false; p = c; } return true; }
}
