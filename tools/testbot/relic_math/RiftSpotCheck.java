import com.solme.emberfall.rift.RiftPlacement;
import com.solme.emberfall.rift.RiftSpot;
import java.util.List;

/** RiftSpotCheck: open-air rule, facing toward a player, nearest-Rift distance. Pure, no game jar. Run from tools/testbot/relic_math. */
public class RiftSpotCheck {
    static int fails = 0, total = 0;
    static void check(String name, boolean ok, String extra) { total++; System.out.println((ok ? "PASS " : "FAIL ") + name + (extra.isEmpty() ? "" : "  " + extra)); if (!ok) fails++; }

    public static void main(String[] a) {
        // O: open air. The boundary is exact: 70% of 10 is 7.
        // Sample size 25 is RiftManager's 5 by 5 grid. 70% of 25 is 17.5, so 18 open is the smallest that passes.
        check("O1 exactly the smallest passing count is open (18 of 25)", RiftSpot.hasOpenAir(18, 25), "");
        check("O2 one cell short is not open (17 of 25)", !RiftSpot.hasOpenAir(17, 25), "");
        check("O3 fully open", RiftSpot.hasOpenAir(25, 25), "");
        check("O4 fully buried", !RiftSpot.hasOpenAir(0, 25), "");
        check("O5 a sample of 0 cells is never open", !RiftSpot.hasOpenAir(0, 0), "");
        check("O6 more open than sampled is refused (bad input)", !RiftSpot.hasOpenAir(26, 25), "");
        check("O7 negative open is refused (bad input)", !RiftSpot.hasOpenAir(-1, 25), "");
        check("O8 rounds UP at another size: 70% of 16 = 11.2 needs 12", !RiftSpot.hasOpenAir(11, 16) && RiftSpot.hasOpenAir(12, 16), "");
        // M: the loaded-cell floor. With the old rule (1,1) passed: one loaded cell, and it was open.
        check("M1 one loaded cell, and it is open, is refused", !RiftSpot.hasOpenAir(1, 1), "");
        check("M2 15 of 15 sampled, all open, is open (the floor itself)", RiftSpot.hasOpenAir(15, 15), "");
        check("M3 14 of 14 sampled, all open, is refused (one under the floor)", !RiftSpot.hasOpenAir(14, 14), "");
        check("M4 the floor is 15 (a quarter of 25 would be 6, so a change to it must show)", RiftSpot.MIN_SAMPLED == 15, "got " + RiftSpot.MIN_SAMPLED);
        boolean floorHolds = true;
        for (int total = 0; total < RiftSpot.MIN_SAMPLED; total++) { for (int open = 0; open <= total; open++) { if (RiftSpot.hasOpenAir(open, total)) floorHolds = false; } }
        check("M5 no combination under the floor is ever open (every open count for 0..14 sampled)", floorHolds, "");

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
