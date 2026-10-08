import com.solme.emberfall.rift.RiftPlacement;
import com.solme.emberfall.rift.RiftSpot;
import java.util.List;

/** RiftSpotCheck: open-air rule, facing toward a player, nearest-Rift distance. Pure, no game jar. Run from tools/testbot/relic_math. */
public class RiftSpotCheck {
    static int fails = 0, total = 0;
    static void check(String name, boolean ok, String extra) { total++; System.out.println((ok ? "PASS " : "FAIL ") + name + (extra.isEmpty() ? "" : "  " + extra)); if (!ok) fails++; }

    public static void main(String[] a) {
        // O: open air. The boundary is exact: 70% of 10 is 7.
        check("O1 exactly 70% open is open", RiftSpot.hasOpenAir(7, 10), "");
        check("O2 one cell short is not open", !RiftSpot.hasOpenAir(6, 10), "");
        check("O3 fully open", RiftSpot.hasOpenAir(10, 10), "");
        check("O4 fully buried", !RiftSpot.hasOpenAir(0, 10), "");
        check("O5 a sample of 0 cells is never open", !RiftSpot.hasOpenAir(0, 0), "");
        check("O6 more open than sampled is refused (bad input)", !RiftSpot.hasOpenAir(11, 10), "");
        check("O7 negative open is refused (bad input)", !RiftSpot.hasOpenAir(-1, 10), "");
        check("O8 rounds UP: 70% of 9 = 6.3 needs 7", !RiftSpot.hasOpenAir(6, 9) && RiftSpot.hasOpenAir(7, 9), "");

        // F: facing. The normal of the answer must point AT the player, checked through RiftPlacement.normal (index 0 = x, 1 = z).
        double[][] players = {{5, 0}, {-5, 0}, {0, 5}, {0, -5}, {7, 3}, {-7, 3}, {3, 7}, {3, -7}, {-3, -7}, {-7, -3}};
        boolean allToward = true;
        StringBuilder bad = new StringBuilder();
        for (double[] p : players) {
            double[] n = RiftPlacement.normal(RiftSpot.facingToward(p[0], p[1]));
            double dot = n[0] * p[0] + n[1] * p[1];
            if (dot <= 0) { allToward = false; bad.append("(" + p[0] + "," + p[1] + ") "); }
        }
        check("F1 the facing's normal points toward the player for 10 directions", allToward, bad.toString());
        check("F2 the dominant axis wins", RiftSpot.facingToward(7, 3) == 3 && RiftSpot.facingToward(3, 7) == 0 && RiftSpot.facingToward(-7, 3) == 1 && RiftSpot.facingToward(3, -7) == 2, "");
        check("F3 same column faces +Z (0), defined not random", RiftSpot.facingToward(0, 0) == 0, "");
        check("F4 an exact diagonal picks z (dz > 0 gives 0, dz < 0 gives 2)", RiftSpot.facingToward(4, 4) == 0 && RiftSpot.facingToward(4, -4) == 2, "");
        boolean range = true;
        for (int i = 0; i < 360; i += 5) { int f = RiftSpot.facingToward(Math.cos(Math.toRadians(i)) * 10, Math.sin(Math.toRadians(i)) * 10); if (f < 0 || f >= RiftPlacement.FACINGS) range = false; }
        check("F5 always one of the 4 facings over 72 angles", range, "");

        // N: nearest.
        List<RiftSpot.At> none = List.of();
        check("N1 no Rifts: infinite", RiftSpot.nearest(none, 0, 0, 0) == Double.POSITIVE_INFINITY, "");
        List<RiftSpot.At> two = List.of(new RiftSpot.At(30, 0, 0), new RiftSpot.At(0, 0, 40));
        check("N2 picks the nearest of two", Math.abs(RiftSpot.nearest(two, 0, 0, 0) - 30.0) < 1e-9, "");
        List<RiftSpot.At> late = List.of(new RiftSpot.At(90, 0, 0), new RiftSpot.At(50, 0, 0), new RiftSpot.At(12, 0, 0), new RiftSpot.At(70, 0, 0));
        check("N5 the nearest can be anywhere in the list (third of four)", Math.abs(RiftSpot.nearest(late, 0, 0, 0) - 12.0) < 1e-9, "");
        List<RiftSpot.At> lastOne = List.of(new RiftSpot.At(90, 0, 0), new RiftSpot.At(50, 0, 0), new RiftSpot.At(8, 0, 0));
        check("N6 the nearest can be the LAST one", Math.abs(RiftSpot.nearest(lastOne, 0, 0, 0) - 8.0) < 1e-9, "");
        check("N3 measures 3D, not flat", Math.abs(RiftSpot.nearest(List.of(new RiftSpot.At(0, 3, 4)), 0, 0, 0) - 5.0) < 1e-9, "");
        check("N4 a Rift at the same point is distance 0", RiftSpot.nearest(List.of(new RiftSpot.At(1, 2, 3)), 1, 2, 3) == 0.0, "");

        System.out.println(fails == 0 ? "ALL PASS (" + total + " checks)" : "FAILED " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }
}
