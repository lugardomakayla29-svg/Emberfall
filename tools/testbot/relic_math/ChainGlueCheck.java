import com.solme.emberfall.entity.ChainGlue;

/** Pure checks for the "no gaps" chain rule. Prints FAIL lines and a verdict; exit 1 on any failure. */
public class ChainGlueCheck {
    static int fails = 0, total = 0;
    static void check(String name, boolean ok, String note) { total++; System.out.println((ok ? "PASS " : "FAIL ") + name + (note.isEmpty() ? "" : " " + note)); if (!ok) fails++; }

    static float[] devourer() { float[] s = new float[20]; s[0] = 2.1F; for (int i = 0; i < 19; i++) s[1 + i] = 1.7F - 0.8F * i / 18; return s; }

    public static void main(String[] a) {
        float[] s = devourer();
        double[] sp = ChainGlue.spacings(s);
        check("G1 a chain of 20 parts has 19 links", sp.length == 19, "len=" + sp.length);
        boolean noGap = true, bounded = true; double worstOverlap = 0;
        for (int i = 0; i < sp.length; i++) {
            if (ChainGlue.gap(s[i], s[i + 1], sp[i]) > 1e-9) noGap = false;
            double touch = (s[i] + s[i + 1]) * 0.5;
            double ov = (touch - sp[i]) / touch; worstOverlap = Math.max(worstOverlap, ov);
            if (ov < 0.05 || ov > 0.20) bounded = false;
        }
        check("G2 every link of the real Devourer chain has NO gap", noGap, "");
        check("G3 every link overlaps by 5% to 20% (glued, not tangled)", bounded, "worst=" + worstOverlap);
        double oldWorst = 0; for (int i = 0; i < s.length - 1; i++) oldWorst = Math.max(oldWorst, ChainGlue.gap(s[i], s[i + 1], 1.5));
        check("G4 the OLD flat 1.5 spacing left a visible gap on the real Devourer chain (worst link > 0.5 blocks): this is the bug the owner saw", oldWorst > 0.5, "worst old gap=" + oldWorst);
        check("G5 two equal cubes: spacing is their size times (1 - overlap)", Math.abs(ChainGlue.linkSpacing(1.0, 1.0) - (1.0 - ChainGlue.OVERLAP)) < 1e-12, "");
        check("G6 spacing is symmetric in its two sizes", ChainGlue.linkSpacing(2.1, 0.9) == ChainGlue.linkSpacing(0.9, 2.1), "");
        check("G7 a bigger neighbour always means a longer link (monotone)", ChainGlue.linkSpacing(2.0, 1.0) > ChainGlue.linkSpacing(1.5, 1.0), "");
        check("G8 a gap is never negative", ChainGlue.gap(1.0, 1.0, 0.0) == 0.0, "");
        check("G9 a real gap is measured: cubes 1 and 1 apart by 1.5 leave 0.5", Math.abs(ChainGlue.gap(1.0, 1.0, 1.5) - 0.5) < 1e-12, "");
        double[] ring = ChainGlue.spacings(new float[]{1f, 2f, 3f});
        check("G10 arc length before part 0 is 0, before part 2 is both links", ChainGlue.arcBefore(ring, 0) == 0.0 && Math.abs(ChainGlue.arcBefore(ring, 2) - (ring[0] + ring[1])) < 1e-12, "");
        check("G11 arcBefore never reads past the last link", Math.abs(ChainGlue.arcBefore(ring, 99) - (ring[0] + ring[1])) < 1e-12, "");
        check("G12 an empty or one-part chain has no links", ChainGlue.spacings(new float[0]).length == 0 && ChainGlue.spacings(new float[]{1f}).length == 0, "");
        float[] arm = {1.6F, 1.35F, 1.1F, 0.9F, 0.7F, 0.55F, 0.42F, 0.3F};
        double[] as = ChainGlue.spacings(arm); boolean armOk = true;
        for (int i = 0; i < as.length; i++) if (ChainGlue.gap(arm[i], arm[i + 1], as[i]) > 1e-9) armOk = false;
        check("G13 a big-to-small tapering arm has no gap at any link", armOk, "");
        // The glued Devourer must keep the old body length (28.5 blocks) so the Coil gap is unchanged: sizes grow by 1.28 (DevourerBrain.GLUE_GROWTH).
        float[] grown = new float[20]; float g = 1.28F; grown[0] = 2.1F * g; for (int i = 0; i < 19; i++) grown[1 + i] = (1.7F - 0.8F * i / 18) * g;
        double len = 0; for (double d : ChainGlue.spacings(grown)) len += d;
        check("G14 the glued, grown Devourer is within 1% of the old 28.5 block length (the Coil gap is kept)", Math.abs(len - 28.5) / 28.5 < 0.01, "len=" + len);
        double lenNoGrow = 0; for (double d : ChainGlue.spacings(s)) lenNoGrow += d;
        check("G15 without the growth it would be 22.3 blocks, 6 blocks short (proves the growth is needed)", lenNoGrow < 23.0 && lenNoGrow > 21.5, "len=" + lenNoGrow);
        boolean grownGlued = true; double[] gs = ChainGlue.spacings(grown);
        for (int i = 0; i < gs.length; i++) if (ChainGlue.gap(grown[i], grown[i + 1], gs[i]) > 1e-9) grownGlued = false;
        check("G16 the grown chain is still glued at every link", grownGlued, "");
        System.out.println(fails == 0 ? "ALL PASS (" + total + " checks)" : "FAILED " + fails + " of " + total);
        System.exit(fails == 0 ? 0 : 1);
    }
}
