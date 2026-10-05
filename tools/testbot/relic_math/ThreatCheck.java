import com.solme.emberfall.relic.*;
import java.util.*;
public class ThreatCheck {
    static int fails = 0;
    static void check(String l, boolean ok, String e) { System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e); if (!ok) fails++; }
    static boolean eq(double a, double b) { return Math.abs(a - b) < 1e-9; }
    public static void main(String[] x) {
        check("nobody: 0", eq(RelicThreat.partyThreat(List.of()), 0), "");
        check("no crowns: 0", eq(RelicThreat.partyThreat(List.of(0.0, 0.0, 0.0)), 0), "");
        check("one player with a crown: +4", eq(RelicThreat.partyThreat(List.of(4.0)), 4.0), "");
        check("solo among 9 crownless: still +4", eq(RelicThreat.partyThreat(List.of(4.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)), 4.0), "");
        check("two crowns in a party: 4 + 0.25*4 = 5", eq(RelicThreat.partyThreat(List.of(4.0, 4.0)), 5.0), "" + RelicThreat.partyThreat(List.of(4.0, 4.0)));
        check("ten crowns: 4 + 0.25*36 = 13, capped at 12", eq(RelicThreat.partyThreat(Collections.nCopies(10, 4.0)), 12.0), "" + RelicThreat.partyThreat(Collections.nCopies(10, 4.0)));
        check("order does not matter", eq(RelicThreat.partyThreat(List.of(8.0, 4.0, 0.0)), RelicThreat.partyThreat(List.of(0.0, 4.0, 8.0))), "");
        check("a bigger stack counts fully, smaller ones a quarter: 8 + 0.25*4 = 9", eq(RelicThreat.partyThreat(List.of(8.0, 4.0)), 9.0), "");
        check("negative and null entries are ignored", eq(RelicThreat.partyThreat(Arrays.asList(4.0, -3.0, null)), 4.0), "");
        check("NaN is ignored", eq(RelicThreat.partyThreat(List.of(Double.NaN, 4.0)), 4.0), "");
        boolean mono = true; double prev = 0; for (int n = 0; n <= 12; n++) { double v = RelicThreat.partyThreat(Collections.nCopies(n, 4.0)); if (v < prev - 1e-9) mono = false; prev = v; }
        check("more crowns never lower the threat (monotonic 0..12)", mono, "");
        System.out.println(fails == 0 ? "RESULT: ALL PASS" : "RESULT: " + fails + " FAILED"); System.exit(fails == 0 ? 0 : 1);
    }
}
