import com.solme.emberfall.rift.RiftPlacement;
import com.solme.emberfall.rift.RiftPlacement.Pos;

/**
 * Pure check of RiftPlacement (no Minecraft jar). The property that matters: the grid maps onto the world without mirroring,
 * rotating or shifting, for all four facings. Every expectation below is worked out by hand from the rule in the class
 * comment, not by calling the method under test.
 */
public class RiftPlacementCheck {
    static int pass = 0;
    static int fail = 0;

    static void check(String name, boolean ok, String extra) {
        if (ok) {
            pass++;
            System.out.println("PASS " + name);
        } else {
            fail++;
            System.out.println("FAIL " + name + " " + extra);
        }
    }

    static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-9;
    }

    static boolean at(Pos p, double x, double y, double z) {
        return near(p.x(), x) && near(p.y(), y) && near(p.z(), z);
    }

    public static void main(String[] args) {
        // Box 15 wide, 13 tall, anchor at (100, 64, 200). The middle cell is (7, 6): across 7.5-7.5=0, up 6.5-6.5=0.
        Pos mid = RiftPlacement.cell(100, 64, 200, 0, 15, 13, 7, 6);
        check("M1 the middle cell sits exactly on the anchor", at(mid, 100, 64, 200), mid.toString());

        // Facing 0: across runs along +X. Cell 8 is one block to the right of the middle: x 101.
        Pos r0 = RiftPlacement.cell(100, 64, 200, 0, 15, 13, 8, 6);
        check("F0a facing 0: one cell right is +1 X", at(r0, 101, 64, 200), r0.toString());
        // Cell y one higher is one block up.
        Pos u0 = RiftPlacement.cell(100, 64, 200, 0, 15, 13, 7, 7);
        check("F0b facing 0: one cell up is +1 Y", at(u0, 100, 65, 200), u0.toString());

        // Facing 1: across runs along +Z, plane at the anchor's X.
        Pos r1 = RiftPlacement.cell(100, 64, 200, 1, 15, 13, 8, 6);
        check("F1 facing 1: one cell right is +1 Z", at(r1, 100, 64, 201), r1.toString());
        // Facing 2: across runs along -X (the plane turned half way round).
        Pos r2 = RiftPlacement.cell(100, 64, 200, 2, 15, 13, 8, 6);
        check("F2 facing 2: one cell right is -1 X", at(r2, 99, 64, 200), r2.toString());
        // Facing 3: across runs along -Z.
        Pos r3 = RiftPlacement.cell(100, 64, 200, 3, 15, 13, 8, 6);
        check("F3 facing 3: one cell right is -1 Z", at(r3, 100, 64, 199), r3.toString());

        // Y never depends on the facing (the tear always stands upright).
        boolean upright = true;
        for (int f = 0; f < 4; f++) {
            upright &= near(RiftPlacement.cell(0, 70, 0, f, 15, 13, 3, 11).y(), 70 + 11 + 0.5 - 6.5);
        }
        check("U1 the tear stays upright for every facing", upright, "y changed with facing");

        // Corners of the 15 x 13 box land exactly 7 and 6 blocks from the anchor.
        Pos lo = RiftPlacement.cell(0, 0, 0, 0, 15, 13, 0, 0);
        Pos hi = RiftPlacement.cell(0, 0, 0, 0, 15, 13, 14, 12);
        check("B1 the box spans -7 to +7 across and -6 to +6 up", at(lo, -7, -6, 0) && at(hi, 7, 6, 0), lo + " " + hi);

        // An even width centres between two cells, never on a cell edge off by one.
        Pos even = RiftPlacement.cell(0, 0, 0, 0, 14, 12, 7, 6);
        check("E1 even box: cell width/2 sits half a block right of the anchor", at(even, 0.5, 0.5, 0), even.toString());

        // Facings wrap: 4 is 0, -1 is 3, 5 is 1.
        check("W1 facing 4 equals facing 0", at(RiftPlacement.cell(0, 0, 0, 4, 15, 13, 8, 6), 1, 0, 0), "");
        check("W2 facing -1 equals facing 3", at(RiftPlacement.cell(0, 0, 0, -1, 15, 13, 8, 6), 0, 0, -1), "");
        // -2 is the case that separates a real wrap from Java's raw remainder: -2 % 4 is -2, which falls into the default branch
        // (facing 3) by accident, while the correct answer is facing 2. -1 alone cannot tell them apart (-1 % 4 is -1, also default).
        check("W4 facing -2 equals facing 2", at(RiftPlacement.cell(0, 0, 0, -2, 15, 13, 8, 6), -1, 0, 0), "");
        check("W5 facing -3 equals facing 1", at(RiftPlacement.cell(0, 0, 0, -3, 15, 13, 8, 6), 0, 0, 1), "");
        check("W3 facing 5 equals facing 1", at(RiftPlacement.cell(0, 0, 0, 5, 15, 13, 8, 6), 0, 0, 1), "");

        // The four facings are four distinct, 90 degree turns of the same offset (never two the same).
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int f = 0; f < 4; f++) {
            Pos p = RiftPlacement.cell(0, 0, 0, f, 15, 13, 10, 6);
            seen.add(p.x() + "," + p.z());
        }
        check("D1 the four facings give four distinct positions", seen.size() == 4, seen.toString());

        // Normals: each is a unit vector, perpendicular to that facing's across axis.
        boolean normals = true;
        for (int f = 0; f < 4; f++) {
            double[] n = RiftPlacement.normal(f);
            double len = Math.sqrt(n[0] * n[0] + n[1] * n[1]);
            Pos a = RiftPlacement.cell(0, 0, 0, f, 15, 13, 8, 6);
            double dot = n[0] * a.x() + n[1] * a.z();
            normals &= near(len, 1.0) && near(dot, 0.0);
        }
        check("N1 every normal is a unit vector and perpendicular to the plane", normals, "");
        double[] n0 = RiftPlacement.normal(0);
        check("N2 facing 0 faces +Z", near(n0[0], 0) && near(n0[1], 1), n0[0] + "," + n0[1]);

        // within(): a point exactly on the radius counts, one just past does not.
        check("R1 within includes the boundary", RiftPlacement.within(0, 0, 0, 3, 4, 0, 5), "");
        check("R2 within excludes just past the boundary", !RiftPlacement.within(0, 0, 0, 3, 4, 0.01, 5), "");
        check("R3 within measures height too", !RiftPlacement.within(0, 0, 0, 0, 40, 0, 32), "");

        System.out.println(fail == 0 ? "ALL PASS (" + pass + " checks)" : "FAILED " + fail + " of " + (pass + fail));
        if (fail > 0) {
            System.exit(1);
        }
    }
}
