import com.solme.emberfall.rift.RiftShape;
import com.solme.emberfall.rift.RiftShape.Shape;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure checks for the Rift silhouette. Several checks re-derive the property with their OWN code (their own flood fill, their own
 * rotation, their own gap measure) instead of calling the Shape helper that the generator also uses, so one wrong helper cannot
 * agree with itself and hide. The mutation proof (each deliberate break must turn exactly the named check red) is in the PR.
 */
public class RiftShapeCheck {
    static int fails = 0;
    static int total = 0;
    static final int SEEDS = 5000;

    static void check(String l, boolean ok, String e) {
        total++;
        System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e);
        if (!ok) {
            fails++;
        }
    }

    public static void main(String[] a) {
        // ---- bounds and emptiness ------------------------------------------------------------------------------------------
        int outside = 0, empty = 0, wrongBox = 0, noBody = 0;
        for (long s = 0; s < SEEDS; s++) {
            Shape sh = RiftShape.generate(s);
            if (sh.width != RiftShape.BOX_W || sh.height != RiftShape.BOX_H) {
                wrongBox++;
            }
            List<int[]> cells = sh.cells();
            if (cells.isEmpty()) {
                empty++;
            }
            if (sh.bodyCount() == 0) {
                noBody++;
            }
            for (int[] c : cells) {
                if (c[0] < 0 || c[1] < 0 || c[0] >= RiftShape.BOX_W || c[1] >= RiftShape.BOX_H) {
                    outside++;
                }
            }
        }
        check("box: every seed is built in the declared box", wrongBox == 0, "wrong box " + wrongBox + " of " + SEEDS);
        check("bounds: no cell is ever outside the box", outside == 0, "outside " + outside);
        check("never empty: every seed has cells", empty == 0, "empty " + empty);
        check("never empty: every seed has a body, not only satellites", noBody == 0, "no body " + noBody);

        // ---- the body is ONE piece (own flood fill, not Shape.bodyPieces) -------------------------------------------------
        int split = 0;
        for (long s = 0; s < SEEDS; s++) {
            if (piecesOfBody(RiftShape.generate(s)) != 1) {
                split++;
            }
        }
        check("connected: the body is exactly one 4-connected piece for every seed (own flood fill)", split == 0, "split " + split + " of " + SEEDS);
        // The independent count must agree with the Shape helper, so a wrong helper is caught here too.
        int disagree = 0;
        for (long s = 0; s < 500; s++) {
            Shape sh = RiftShape.generate(s);
            if (piecesOfBody(sh) != sh.bodyPieces()) {
                disagree++;
            }
        }
        check("connected: Shape.bodyPieces agrees with the independent count", disagree == 0, "disagree " + disagree);
        // The fill must be able to report a split: two blocks with a gap are two pieces. This guards the flood fill itself.
        check("connected: the independent count really sees a split (two separate blocks = 2)", piecesOfGrid(new String[] {"##..##", "##..##"}) == 2, "");
        check("connected: a diagonal touch is NOT connected (4-neighbour rule)", piecesOfGrid(new String[] {"#.", ".#"}) == 2, "");

        // ---- the Shape helpers on HAND-MADE shapes with known answers (generated shapes never exercise a diagonal) -------------
        check("hand: a plus sign is one piece", RiftShape.fromRows(".#.", "###", ".#.").bodyPieces() == 1, "");
        check("hand: two blocks with a gap are two pieces", RiftShape.fromRows("##.##", "##.##").bodyPieces() == 2, "");
        check("hand: a diagonal touch is two pieces, not one", RiftShape.fromRows("#.", ".#").bodyPieces() == 2, "");
        check("hand: an empty grid has no pieces", RiftShape.fromRows("...", "...").bodyPieces() == 0, "");
        check("hand: a satellite is not part of the body count", RiftShape.fromRows("#o").bodyPieces() == 1 && RiftShape.fromRows("#o").satelliteCount() == 1, "");
        check("hand: a satellite touching the body at a corner is reported as touching", RiftShape.fromRows("#.", ".o").touchesBody(1, 0) && !RiftShape.fromRows("#..", "..o").touchesBody(2, 0), "");
        check("hand: a 3x3 block has exactly 8 rim cells (the centre is interior)", RiftShape.fromRows("###", "###", "###").rim().size() == 8, "");
        check("hand: a 1 cell wide line is all rim", RiftShape.fromRows("#", "#", "#").rim().size() == 3, "");
        check("hand: rotating swaps the box and moves a corner cell", RiftShape.fromRows("#..", "...").rotated().isBody(1, 0) && RiftShape.fromRows("#..", "...").rotated().width == 2, "");
        check("hand: render and fromRows round-trip", RiftShape.generate(11).render().equals(RiftShape.fromRows(RiftShape.generate(11).render().split("\n")).render()), "");
        boolean threw = false;
        try { RiftShape.fromRows("##", "#"); } catch (IllegalArgumentException e) { threw = true; }
        check("hand: uneven rows are refused", threw, "");
        threw = false;
        try { RiftShape.fromRows("#x"); } catch (IllegalArgumentException e) { threw = true; }
        check("hand: an unknown cell character is refused", threw, "");

        // ---- satellites: detached but near (own measures) -----------------------------------------------------------------
        int touching = 0, tooFar = 0, wrongCount = 0, overlap = 0;
        for (long s = 0; s < SEEDS; s++) {
            Shape sh = RiftShape.generate(s);
            int n = 0;
            for (int x = 0; x < sh.width; x++) {
                for (int y = 0; y < sh.height; y++) {
                    if (sh.isSatellite(x, y)) {
                        n++;
                        if (sh.isBody(x, y)) {
                            overlap++;
                        }
                        int gap = chebyshevToBody(sh, x, y);
                        if (gap <= 1) {
                            touching++;
                        }
                        if (gap > RiftShape.SATELLITE_MAX_GAP) {
                            tooFar++;
                        }
                    }
                }
            }
            if (n < RiftShape.SATELLITES_MIN || n > RiftShape.SATELLITES_MAX) {
                wrongCount++;
            }
        }
        check("satellites: none touches the body, not even at a corner", touching == 0, "touching " + touching);
        check("satellites: none floats farther than the allowed gap", tooFar == 0, "too far " + tooFar);
        check("satellites: every seed has between the minimum and maximum", wrongCount == 0, "wrong count " + wrongCount);
        check("satellites: no cell is both body and satellite", overlap == 0, "overlap " + overlap);

        // ---- it looks like the picture, as far as numbers can say ---------------------------------------------------------
        int narrow = 0, short_ = 0, notTaller = 0, symmetric = 0;
        for (long s = 0; s < SEEDS; s++) {
            Shape sh = RiftShape.generate(s);
            if (sh.bodySpanX() < RiftShape.MIN_SPAN_X) {
                narrow++;
            }
            if (sh.bodySpanY() < RiftShape.COLUMN_MIN_H) {
                short_++;
            }
            if (sh.bodySpanY() <= columnWidth(sh)) {
                notTaller++;
            }
            if (isMirrorSymmetric(sh)) {
                symmetric++;
            }
        }
        check("picture: the body is at least the minimum width (wings, not a bare column)", narrow == 0, "narrow " + narrow);
        check("picture: the body is at least the column's minimum height", short_ == 0, "short " + short_);
        check("picture: the body is taller than its centre column is wide", notTaller == 0, "not taller " + notTaller);
        check("picture: a tear is jagged, not left-right mirror-symmetric (under 2% of seeds)", symmetric * 50 < SEEDS, "symmetric " + symmetric + " of " + SEEDS);

        // ---- determinism and variety --------------------------------------------------------------------------------------
        int unstable = 0;
        for (long s = 0; s < 500; s++) {
            if (!RiftShape.generate(s).render().equals(RiftShape.generate(s).render())) {
                unstable++;
            }
        }
        check("determinism: the same seed gives the same tear every time", unstable == 0, "unstable " + unstable);
        Set<String> shapes = new HashSet<>();
        for (long s = 0; s < 1000; s++) {
            shapes.add(RiftShape.generate(s).render());
        }
        check("variety: 1000 seeds give at least 900 different tears", shapes.size() >= 900, shapes.size() + " distinct of 1000");
        check("variety: two neighbouring seeds differ", !RiftShape.generate(1).render().equals(RiftShape.generate(2).render()), "");
        check("variety: negative and huge seeds work and differ", !RiftShape.generate(-1).render().equals(RiftShape.generate(Long.MAX_VALUE).render()) && RiftShape.generate(Long.MIN_VALUE).bodyCount() > 0, "");

        // ---- orientation: horizontal is the SAME cells turned (own rotation) ----------------------------------------------
        int wrongSize = 0, lost = 0, bodyMoved = 0, satMoved = 0;
        for (long s = 0; s < 2000; s++) {
            Shape v = RiftShape.generate(s, false);
            Shape h = RiftShape.generate(s, true);
            if (h.width != v.height || h.height != v.width) {
                wrongSize++;
            }
            if (h.bodyCount() != v.bodyCount() || h.satelliteCount() != v.satelliteCount()) {
                lost++;
            }
            for (int x = 0; x < v.width; x++) {
                for (int y = 0; y < v.height; y++) {
                    if (v.isBody(x, y) != h.isBody(y, x)) {
                        bodyMoved++;
                    }
                    if (v.isSatellite(x, y) != h.isSatellite(y, x)) {
                        satMoved++;
                    }
                }
            }
        }
        check("orientation: the flat tear has the box turned (width and height swap)", wrongSize == 0, "wrong size " + wrongSize);
        check("orientation: the flat tear keeps every body and satellite cell count", lost == 0, "lost " + lost);
        check("orientation: every body cell (x,y) is body at (y,x) in the flat tear", bodyMoved == 0, "moved " + bodyMoved);
        check("orientation: every satellite cell (x,y) is satellite at (y,x) in the flat tear", satMoved == 0, "moved " + satMoved);
        int notFlat = 0, notTall = 0;
        for (long s = 0; s < 2000; s++) {
            Shape v = RiftShape.generate(s, false);
            Shape h = RiftShape.generate(s, true);
            if (v.bodySpanY() <= v.bodySpanX()) {
                notTall++;
            }
            if (h.bodySpanX() <= h.bodySpanY()) {
                notFlat++;
            }
        }
        check("orientation: the upright tear's body is taller than wide for every seed", notTall == 0, "not tall " + notTall);
        check("orientation: the flat tear's body is wider than tall for every seed", notFlat == 0, "not flat " + notFlat);
        check("orientation: turning a tear twice gives back the original", RiftShape.generate(7).render().equals(RiftShape.generate(7).rotated().rotated().render()), "");
        int flatSplit = 0;
        for (long s = 0; s < 500; s++) {
            if (piecesOfBody(RiftShape.generate(s, true)) != 1) {
                flatSplit++;
            }
        }
        check("orientation: the flat tear's body is still one piece", flatSplit == 0, "split " + flatSplit);

        // ---- the rim (where the bright line is drawn) ---------------------------------------------------------------------
        int rimBad = 0, rimInterior = 0, rimMissed = 0;
        for (long s = 0; s < 1000; s++) {
            Shape sh = RiftShape.generate(s);
            Set<String> rim = new HashSet<>();
            for (int[] c : sh.rim()) {
                rim.add(c[0] + "," + c[1]);
                if (!sh.isCell(c[0], c[1])) {
                    rimBad++;
                }
            }
            for (int x = 0; x < sh.width; x++) {
                for (int y = 0; y < sh.height; y++) {
                    if (!sh.isCell(x, y)) {
                        continue;
                    }
                    boolean open = !sh.isCell(x - 1, y) || !sh.isCell(x + 1, y) || !sh.isCell(x, y - 1) || !sh.isCell(x, y + 1);
                    if (open && !rim.contains(x + "," + y)) {
                        rimMissed++;
                    }
                    if (!open && rim.contains(x + "," + y)) {
                        rimInterior++;
                    }
                }
            }
        }
        check("rim: every rim cell is a real cell", rimBad == 0, "bad " + rimBad);
        check("rim: no fully surrounded interior cell is on the rim", rimInterior == 0, "interior " + rimInterior);
        check("rim: every cell with an open side is on the rim", rimMissed == 0, "missed " + rimMissed);
        Shape one = RiftShape.generate(3);
        check("rim: a tear has an interior (fewer rim cells than cells), so the fill has somewhere to be", one.rim().size() < one.cells().size(), one.rim().size() + " of " + one.cells().size());

        System.out.println(fails == 0 ? "ALL PASS (" + total + " checks)" : "FAILED " + fails + " of " + total);
        if (fails != 0) {
            System.exit(1);
        }
    }

    // ---- independent helpers (do not call the Shape helpers they are checking) -----------------------------------------------

    static int piecesOfBody(Shape sh) {
        String[] rows = new String[sh.height];
        for (int y = 0; y < sh.height; y++) {
            StringBuilder b = new StringBuilder();
            for (int x = 0; x < sh.width; x++) {
                b.append(sh.isBody(x, y) ? '#' : '.');
            }
            rows[y] = b.toString();
        }
        return piecesOfGrid(rows);
    }

    /** Count 4-connected '#' pieces in a grid of equal-length rows. */
    static int piecesOfGrid(String[] rows) {
        int h = rows.length, w = rows[0].length();
        boolean[][] seen = new boolean[h][w];
        int pieces = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (rows[y].charAt(x) == '#' && !seen[y][x]) {
                    pieces++;
                    List<int[]> q = new ArrayList<>();
                    q.add(new int[] {x, y});
                    seen[y][x] = true;
                    for (int i = 0; i < q.size(); i++) {
                        int cx = q.get(i)[0], cy = q.get(i)[1];
                        int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                        for (int[] k : d) {
                            int nx = cx + k[0], ny = cy + k[1];
                            if (nx >= 0 && ny >= 0 && nx < w && ny < h && rows[ny].charAt(nx) == '#' && !seen[ny][nx]) {
                                seen[ny][nx] = true;
                                q.add(new int[] {nx, ny});
                            }
                        }
                    }
                }
            }
        }
        return pieces;
    }

    static int chebyshevToBody(Shape sh, int x, int y) {
        int best = Integer.MAX_VALUE;
        for (int bx = 0; bx < sh.width; bx++) {
            for (int by = 0; by < sh.height; by++) {
                if (sh.isBody(bx, by)) {
                    best = Math.min(best, Math.max(Math.abs(bx - x), Math.abs(by - y)));
                }
            }
        }
        return best;
    }

    /** Width of the widest run of body cells in the tallest column: the centre column's width, found from the cells alone. */
    static int columnWidth(Shape sh) {
        int bestRows = -1;
        for (int y = 0; y < sh.height; y++) {
            int n = 0;
            for (int x = 0; x < sh.width; x++) {
                if (sh.isBody(x, y)) {
                    n++;
                }
            }
            // the narrowest occupied row is the column alone
            if (n > 0 && (bestRows < 0 || n < bestRows)) {
                bestRows = n;
            }
        }
        return bestRows < 0 ? 0 : bestRows;
    }

    static boolean isMirrorSymmetric(Shape sh) {
        for (int x = 0; x < sh.width; x++) {
            for (int y = 0; y < sh.height; y++) {
                if (sh.isBody(x, y) != sh.isBody(sh.width - 1 - x, y)) {
                    return false;
                }
            }
        }
        return true;
    }
}
