package com.solme.emberfall.rift;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The silhouette of a Rift as PURE DATA (no Minecraft types, no entities): a set of unit cells on a 2D grid, built from a seed.
 * The Rift is drawn with particles along and inside these cells, so everything about its look that can be proven (it is one
 * connected tear, it fits its box, it is never empty, a different seed gives a different tear, horizontal is the same tear
 * turned flat) is proven here without a server.
 *
 * Shape, read from the owner's picture: a tall CENTRE column, wide WINGS stepped out to the left and right at different
 * heights, and a few small SATELLITE shards floating clear of the edge. The body (column and wings) is ONE 4-connected piece;
 * the satellites are deliberately NOT touching it, but are never farther than {@link #SATELLITE_MAX_GAP} cells away.
 *
 * Grid: x runs left to right, y runs bottom to top, (0, 0) is the bottom-left of the bounding box. One cell is one block.
 * All numbers are PROPOSALS (the owner gave a picture, not a size); they are named so they change in one place.
 */
public final class RiftShape {
    private RiftShape() {}

    /** Bounding box of the whole tear, satellites included. A player must fit through the body, so it is tall and wide enough. */
    public static final int BOX_W = 15;
    public static final int BOX_H = 13;
    /** The centre column: its width and its height range. The body is taller than it is wide, like the picture. */
    public static final int COLUMN_MIN_W = 3;
    public static final int COLUMN_MAX_W = 4;
    public static final int COLUMN_MIN_H = 9;
    public static final int COLUMN_MAX_H = 11;
    /** Wings on each side: how many stacked steps, and how wide each may be. */
    public static final int WING_STEPS_MIN = 1;
    public static final int WING_STEPS_MAX = 2;
    public static final int WING_STEP_MAX_W = 2;
    /** The body must be at least this wide (wings, not a bare column) AND narrower than it is tall (the picture is a TALL tear). */
    public static final int MIN_SPAN_X = 7;
    /** Redraws allowed per seed before the generator accepts the widest attempt it saw, so it can never loop forever. */
    public static final int MAX_REDRAWS = 64;
    /** Satellites: how many, and the farthest (in cells, measured edge to edge) one may float from the body. */
    public static final int SATELLITES_MIN = 2;
    public static final int SATELLITES_MAX = 5;
    public static final int SATELLITE_MAX_GAP = 3;

    /** An immutable tear: the body cells, the satellite cells, and the bounding box they live in. */
    public static final class Shape {
        public final int width;
        public final int height;
        private final boolean[][] body;       // [x][y]
        private final boolean[][] satellite;  // [x][y]

        Shape(int width, int height, boolean[][] body, boolean[][] satellite) {
            this.width = width;
            this.height = height;
            this.body = body;
            this.satellite = satellite;
        }

        public boolean isBody(int x, int y) {
            return in(x, y) && body[x][y];
        }

        public boolean isSatellite(int x, int y) {
            return in(x, y) && satellite[x][y];
        }

        public boolean isCell(int x, int y) {
            return isBody(x, y) || isSatellite(x, y);
        }

        private boolean in(int x, int y) {
            return x >= 0 && y >= 0 && x < width && y < height;
        }

        public int bodyCount() {
            return count(body);
        }

        public int satelliteCount() {
            return count(satellite);
        }

        private int count(boolean[][] g) {
            int n = 0;
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    if (g[x][y]) {
                        n++;
                    }
                }
            }
            return n;
        }

        /** Every cell (body then satellite) as {x, y}, in a fixed order so two equal shapes list identically. */
        public List<int[]> cells() {
            List<int[]> out = new ArrayList<>();
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    if (isCell(x, y)) {
                        out.add(new int[] {x, y});
                    }
                }
            }
            return out;
        }

        /**
         * The RIM: cells that have at least one of their four sides open to nothing (outside the box or an empty cell).
         * This is where the bright warm line is drawn. Satellites are all rim, since they float alone.
         */
        public List<int[]> rim() {
            List<int[]> out = new ArrayList<>();
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    if (isCell(x, y) && (!isCell(x - 1, y) || !isCell(x + 1, y) || !isCell(x, y - 1) || !isCell(x, y + 1))) {
                        out.add(new int[] {x, y});
                    }
                }
            }
            return out;
        }

        /** Number of 4-connected pieces the BODY is made of. A real tear is exactly 1. */
        public int bodyPieces() {
            boolean[][] seen = new boolean[width][height];
            int pieces = 0;
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    if (body[x][y] && !seen[x][y]) {
                        pieces++;
                        flood(x, y, seen);
                    }
                }
            }
            return pieces;
        }

        private void flood(int sx, int sy, boolean[][] seen) {
            int[] stack = new int[width * height];
            int top = 0;
            stack[top++] = sx * height + sy;
            seen[sx][sy] = true;
            while (top > 0) {
                int v = stack[--top];
                int x = v / height;
                int y = v % height;
                int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                for (int[] k : d) {
                    int nx = x + k[0];
                    int ny = y + k[1];
                    if (nx >= 0 && ny >= 0 && nx < width && ny < height && body[nx][ny] && !seen[nx][ny]) {
                        seen[nx][ny] = true;
                        stack[top++] = nx * height + ny;
                    }
                }
            }
        }

        /** True if the satellite cell at (x, y) shares a side OR a corner with any body cell (it must not). */
        public boolean touchesBody(int x, int y) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if ((dx != 0 || dy != 0) && isBody(x + dx, y + dy)) {
                        return true;
                    }
                }
            }
            return false;
        }

        /** Chebyshev distance in cells from (x, y) to the nearest body cell, or Integer.MAX_VALUE if there is no body. */
        public int gapToBody(int x, int y) {
            int best = Integer.MAX_VALUE;
            for (int bx = 0; bx < width; bx++) {
                for (int by = 0; by < height; by++) {
                    if (body[bx][by]) {
                        best = Math.min(best, Math.max(Math.abs(bx - x), Math.abs(by - y)));
                    }
                }
            }
            return best;
        }

        /** Width of the body in cells (leftmost to rightmost body cell), 0 if empty. */
        public int bodySpanX() {
            int lo = Integer.MAX_VALUE;
            int hi = -1;
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    if (body[x][y]) {
                        lo = Math.min(lo, x);
                        hi = Math.max(hi, x);
                    }
                }
            }
            return hi < 0 ? 0 : hi - lo + 1;
        }

        /** Height of the body in cells (lowest to highest body cell), 0 if empty. */
        public int bodySpanY() {
            int lo = Integer.MAX_VALUE;
            int hi = -1;
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    if (body[x][y]) {
                        lo = Math.min(lo, y);
                        hi = Math.max(hi, y);
                    }
                }
            }
            return hi < 0 ? 0 : hi - lo + 1;
        }

        /**
         * The same tear lying FLAT: x and y trade places, so a vertical tear (tall) becomes a horizontal one (wide). Every cell maps
         * to exactly one cell and the body, satellites and rim map with it, so the two orientations are provably one shape.
         */
        public Shape rotated() {
            boolean[][] b = new boolean[height][width];
            boolean[][] s = new boolean[height][width];
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    b[y][x] = body[x][y];
                    s[y][x] = satellite[x][y];
                }
            }
            return new Shape(height, width, b, s);
        }

        /** A stable text form, one row per line, top row first: '#' body, 'o' satellite, '.' empty. Used to compare and to print. */
        public String render() {
            StringBuilder sb = new StringBuilder();
            for (int y = height - 1; y >= 0; y--) {
                for (int x = 0; x < width; x++) {
                    sb.append(body[x][y] ? '#' : satellite[x][y] ? 'o' : '.');
                }
                sb.append('\n');
            }
            return sb.toString();
        }
    }

    /**
     * A small seeded generator I own, so the same seed gives the same tear on every JVM and every side (server, client, test).
     * SplitMix64: a fixed, well-known sequence that depends on nothing else.
     */
    static final class Rng {
        private long state;

        Rng(long seed) {
            this.state = seed;
        }

        long next() {
            long z = (state += 0x9E3779B97F4A7C15L);
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            return z ^ (z >>> 31);
        }

        /** Uniform int in [lo, hi] inclusive. */
        int range(int lo, int hi) {
            long span = (long) hi - lo + 1;
            return lo + (int) Long.remainderUnsigned(next(), span);
        }
    }

    /**
     * Builds the VERTICAL tear for a seed. The same seed always gives the same cells. A draw is redrawn when its body is
     * narrower than {@link #MIN_SPAN_X} or not taller than it is wide; the redraw seed comes from a fixed sequence, so the
     * result is still deterministic. About 31% of first draws are redrawn (measured over 20000 seeds) and every seed was
     * accepted within {@link #MAX_REDRAWS}; if one ever were not, the closest draw is kept so this can never loop forever.
     */
    public static Shape generate(long seed) {
        Rng derive = new Rng(seed);
        Shape best = null;
        long s = seed;
        for (int i = 0; i <= MAX_REDRAWS; i++) {
            Shape cand = draw(s);
            if (acceptable(cand)) {
                return cand;
            }
            if (best == null || score(cand) > score(best)) {
                best = cand;
            }
            s = derive.next();
        }
        return best;
    }

    /** True when the body is wide enough to have wings and still taller than it is wide. */
    static boolean acceptable(Shape sh) {
        return sh.bodySpanX() >= MIN_SPAN_X && sh.bodySpanY() > sh.bodySpanX();
    }

    /** How close a draw is to acceptable (higher is better), used only to pick the best of the redraws if none passes. */
    private static int score(Shape sh) {
        int w = sh.bodySpanX();
        int h = sh.bodySpanY();
        return -(Math.max(0, MIN_SPAN_X - w) + Math.max(0, w - h + 1));
    }

    /** One draw of the vertical tear from exactly this seed, with no width rule applied. */
    static Shape draw(long seed) {
        Rng r = new Rng(seed);
        boolean[][] body = new boolean[BOX_W][BOX_H];
        boolean[][] sat = new boolean[BOX_W][BOX_H];

        // Centre column, roughly in the middle of the box and as tall as it may be.
        int colW = r.range(COLUMN_MIN_W, COLUMN_MAX_W);
        int colH = r.range(COLUMN_MIN_H, COLUMN_MAX_H);
        int colX = (BOX_W - colW) / 2 + r.range(-1, 1);
        int colY = 1 + r.range(0, BOX_H - 2 - colH);
        fill(body, colX, colY, colW, colH);

        // Wings: stepped rectangles stacked on each side, each reaching out from what is already there (so they are always attached).
        wing(r, body, colX - 1, colY, colH, -1);
        wing(r, body, colX + colW, colY, colH, +1);

        // Satellites: single cells or tiny pairs, clear of the body but close to it.
        int want = r.range(SATELLITES_MIN, SATELLITES_MAX);
        int placed = 0;
        for (int tries = 0; tries < 400 && placed < want; tries++) {
            int x = r.range(0, BOX_W - 1);
            int y = r.range(0, BOX_H - 1);
            if (body[x][y] || sat[x][y]) {
                continue;
            }
            if (touches(body, x, y) || touches(sat, x, y)) {
                continue;
            }
            int gap = nearestBody(body, x, y);
            if (gap < 2 || gap > SATELLITE_MAX_GAP) {
                continue;
            }
            sat[x][y] = true;
            placed++;
        }
        return new Shape(BOX_W, BOX_H, body, sat);
    }

    /**
     * Stacks 1 to 3 steps outward from column edge {@code edgeX}, direction {@code dir} (-1 left, +1 right). Each step's row range
     * must OVERLAP the previous step's by at least one row (the first overlaps the column's), so the wing is attached by construction.
     */
    private static void wing(Rng r, boolean[][] body, int edgeX, int colY, int colH, int dir) {
        int steps = r.range(WING_STEPS_MIN, WING_STEPS_MAX);
        int reach = 0;  // how far out the previous steps went
        int prevLo = colY;
        int prevHi = colY + colH - 1;  // rows the previous piece occupies (the column, for the first step)
        for (int s = 0; s < steps; s++) {
            int w = r.range(1, WING_STEP_MAX_W);
            int h = r.range(1, 3);
            // The new step's bottom row y must satisfy: y <= prevHi and y + h - 1 >= prevLo, i.e. at least one shared row.
            int yMin = Math.max(1, prevLo - h + 1);
            int yMax = Math.min(BOX_H - 1 - h, prevHi);
            if (yMin > yMax) {
                break;
            }
            int y = r.range(yMin, yMax);
            for (int i = 0; i < w; i++) {
                int x = edgeX + dir * (reach + i);
                for (int j = 0; j < h; j++) {
                    int yy = y + j;
                    if (x >= 0 && x < BOX_W && yy >= 0 && yy < BOX_H) {
                        body[x][yy] = true;
                    }
                }
            }
            reach += w;
            prevLo = y;
            prevHi = y + h - 1;
        }
    }

    private static void fill(boolean[][] g, int x0, int y0, int w, int h) {
        for (int x = x0; x < x0 + w; x++) {
            for (int y = y0; y < y0 + h; y++) {
                if (x >= 0 && x < BOX_W && y >= 0 && y < BOX_H) {
                    g[x][y] = true;
                }
            }
        }
    }

    private static boolean touches(boolean[][] g, int x, int y) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                int nx = x + dx;
                int ny = y + dy;
                if ((dx != 0 || dy != 0) && nx >= 0 && ny >= 0 && nx < BOX_W && ny < BOX_H && g[nx][ny]) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int nearestBody(boolean[][] body, int x, int y) {
        int best = Integer.MAX_VALUE;
        for (int bx = 0; bx < BOX_W; bx++) {
            for (int by = 0; by < BOX_H; by++) {
                if (body[bx][by]) {
                    best = Math.min(best, Math.max(Math.abs(bx - x), Math.abs(by - y)));
                }
            }
        }
        return best;
    }

    /** The shape for a seed in the requested orientation: vertical as built, or turned flat. */
    public static Shape generate(long seed, boolean horizontal) {
        Shape v = generate(seed);
        return horizontal ? v.rotated() : v;
    }

    /**
     * Builds a shape from text, top row first, the same form {@link Shape#render()} prints: '#' body, 'o' satellite, '.' empty.
     * Every row must have the same length. This lets a test state a shape by hand with a known answer.
     */
    public static Shape fromRows(String... rows) {
        if (rows.length == 0) {
            throw new IllegalArgumentException("no rows");
        }
        int h = rows.length;
        int w = rows[0].length();
        boolean[][] body = new boolean[w][h];
        boolean[][] sat = new boolean[w][h];
        for (int i = 0; i < h; i++) {
            if (rows[i].length() != w) {
                throw new IllegalArgumentException("row " + i + " has length " + rows[i].length() + ", expected " + w);
            }
            int y = h - 1 - i;
            for (int x = 0; x < w; x++) {
                char c = rows[i].charAt(x);
                if (c == '#') {
                    body[x][y] = true;
                } else if (c == 'o') {
                    sat[x][y] = true;
                } else if (c != '.') {
                    throw new IllegalArgumentException("bad cell '" + c + "' at row " + i);
                }
            }
        }
        return new Shape(w, h, body, sat);
    }

    /** The cells of a shape as a sorted list of "x,y" strings, for comparing two shapes exactly. */
    public static List<String> signature(Shape s) {
        List<String> out = new ArrayList<>();
        for (int[] c : s.cells()) {
            out.add(c[0] + "," + c[1]);
        }
        Collections.sort(out);
        return out;
    }
}
