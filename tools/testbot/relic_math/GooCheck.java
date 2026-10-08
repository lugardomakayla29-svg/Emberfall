import com.solme.emberfall.entity.GooGrid;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Pure checks for the Broodtide goo (docs/PLAN_broodtide.md, work-split row 5, test 7: "cap, one damage tick per second from ALL goo").
 * They prove the DATA rules: the cap, the lifetime, and that damage is at most one tick per second per player however much goo overlaps them.
 * They do NOT prove a player in a world is hurt, slowed, or sees goo; that is the wiring and the renderer, which are Koda's and not here.
 * Expected values below are written by hand from the rules, not copied from the class.
 */
public class GooCheck {
    static int fails = 0;
    static int total = 0;

    static void check(String l, boolean ok, String e) {
        total++;
        System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e);
        if (!ok) {
            fails++;
        }
    }

    public static void main(String[] a) {
        // ---- the numbers are the plan's, written out here so a drifted constant fails by name ---------------------------------
        check("constants: cap 400, lifetime 400 ticks (20 s), damage every 20 ticks (1 s), slow 0.6",
                GooGrid.MAX_CELLS == 400 && GooGrid.LIFETIME_TICKS == 400 && GooGrid.DAMAGE_INTERVAL_TICKS == 20 && GooGrid.SLOW_FACTOR == 0.6, "");

        // ---- laying and asking ----------------------------------------------------------------------------------------------
        GooGrid g = new GooGrid();
        check("lay: an empty grid has no goo anywhere", g.count(0) == 0 && !g.isGoo(0, 0, 0), "");
        check("lay: a laid cell is goo, its neighbours are not", g.lay(5, 7, 100) && g.isGoo(5, 7, 100) && !g.isGoo(6, 7, 100) && !g.isGoo(5, 8, 100) && !g.isGoo(4, 7, 100) && !g.isGoo(5, 6, 100), "");
        check("lay: x and z are not swapped (5,7 is goo, 7,5 is not)", g.isGoo(5, 7, 100) && !g.isGoo(7, 5, 100), "");
        check("lay: negative coordinates work and do not collide", layNegatives(), "");
        check("lay: the extreme corners of the int range are distinct cells", extremes(), "");

        // ---- lifetime: boundary on both sides ---------------------------------------------------------------------------------
        g = new GooGrid();
        g.lay(1, 1, 1000);
        check("life: still goo one tick before it ends (tick 1399)", g.isGoo(1, 1, 1399), "");
        check("life: gone AT its expiry tick (1400), the first tick it is not goo", !g.isGoo(1, 1, 1400), "");
        check("life: not goo before it was laid is not a thing the class claims, but a cell is goo at the tick it is laid", g.isGoo(1, 1, 1000), "");
        check("life: count agrees with isGoo at the boundary (1 at 1399, 0 at 1400) even before expire runs", g.count(1399) == 1 && g.count(1400) == 0, "");
        check("life: expire removes exactly the dead cell and reports 1", g.expire(1400) == 1 && g.count(1400) == 0, "");
        check("life: expire on an empty grid removes 0", new GooGrid().expire(99999) == 0, "");
        g = new GooGrid();
        g.lay(2, 2, 0);
        g.lay(2, 2, 300);
        check("life: laying again refreshes the expiry (laid at 0 and 300: goo at 699, gone at 700)", g.isGoo(2, 2, 699) && !g.isGoo(2, 2, 700), "");
        check("life: laying again does not make a second cell", g.count(300) == 1, "");

        // ---- the cap ------------------------------------------------------------------------------------------------------
        g = new GooGrid();
        int accepted = 0;
        for (int i = 0; i < 1000; i++) {
            if (g.lay(i, 0, 0)) {
                accepted++;
            }
        }
        check("cap: exactly 400 of 1000 distinct cells are accepted", accepted == 400, "accepted " + accepted);
        check("cap: the grid holds exactly 400 cells, never more", g.count(0) == 400 && g.cells(0).size() == 400, "count " + g.count(0));
        check("cap: the first 400 are goo and the 401st is not", g.isGoo(0, 0, 0) && g.isGoo(399, 0, 0) && !g.isGoo(400, 0, 0), "");
        check("cap: a full grid refuses a new cell and evicts nothing", !g.lay(5000, 5000, 0) && g.isGoo(0, 0, 0) && g.count(0) == 400, "");
        check("cap: laying an EXISTING cell on a full grid still works (it takes no slot)", g.lay(0, 0, 10) && g.count(10) == 400, "");
        check("cap: the cap holds at 399 and at 401 (one under takes a cell, at the cap refuses)", capBoundary(), "");
        check("cap: once old goo expires the room is free again", capFreedByExpiry(), "");
        check("cap: a full grid of EXPIRED goo does not block new goo", fullOfDeadGoo(), "");
        check("cap: a hammering of 100000 lays over a wide area never exceeds 400", hammer(), "");

        // ---- cells(): sorted, live only -----------------------------------------------------------------------------------------
        g = new GooGrid();
        g.lay(3, 9, 0);
        g.lay(-2, 4, 0);
        g.lay(3, -1, 0);
        List<int[]> cs = g.cells(0);
        check("cells: sorted by x then z, negatives first", cs.size() == 3 && eq(cs.get(0), -2, 4) && eq(cs.get(1), 3, -1) && eq(cs.get(2), 3, 9), render(cs));
        check("cells: lists live cells only (none at the expiry tick)", g.cells(400).isEmpty() && g.cells(399).size() == 3, "");

        // ---- speed ("slow and sticky") -------------------------------------------------------------------------------------------
        g = new GooGrid();
        g.lay(0, 0, 0);
        check("slow: 0.6 on goo, 1.0 off it, 1.0 once it has expired", g.speedFactor(0, 0, 0) == 0.6 && g.speedFactor(1, 0, 0) == 1.0 && g.speedFactor(0, 0, 400) == 1.0, "");

        // ---- the damage rule: ONE TICK A SECOND FROM ALL GOO ------------------------------------------------------------------------
        UUID p = new UUID(0, 1);
        UUID q = new UUID(0, 2);
        g = new GooGrid();
        g.lay(0, 0, 0);
        check("damage: a player not on goo is never hurt", !g.tryDamage(p, 9, 9, 0) && !g.tryDamage(p, 9, 9, 5000), "");
        check("damage: the first tick on goo hurts at once", g.tryDamage(p, 0, 0, 0), "");
        check("damage: not again one tick later, nor at 19 ticks", !g.tryDamage(p, 0, 0, 1) && !g.tryDamage(p, 0, 0, 19), "");
        check("damage: again at exactly 20 ticks", g.tryDamage(p, 0, 0, 20), "");
        check("damage: and not at 39, again at 40", !g.tryDamage(p, 0, 0, 39) && g.tryDamage(p, 0, 0, 40), "");
        check("damage: standing 10 seconds (200 ticks), asking every tick, gives exactly 10 ticks", standingTicks(200, 1) == 10, "got " + standingTicks(200, 1));
        check("damage: asking every 7 ticks for 200 ticks gives 10 hits at most spaced 20 or more apart", spacingOk(200, 7), "");
        check("damage: asking every 25 ticks for 250 ticks (asks at 0,25..225 = 10 asks, every gap past 20) hurts exactly 10 times", standingTicks(250, 25) == 10, "got " + standingTicks(250, 25));
        check("damage: asking every 10 ticks for 200 ticks (asks at 0,10..190 = 20 asks) hurts exactly 10 times, at 0,20,40...180", standingTicks(200, 10) == 10, "got " + standingTicks(200, 10));
        check("damage: the rate is per PLAYER, so a second player is hurt on their own clock", independentPlayers(), "");

        // ---- ALL goo together: overlapping cells and patches give no extra ticks --------------------------------------------------------
        g = new GooGrid();
        for (int x = 0; x < 20; x++) {
            for (int z = 0; z < 20; z++) {
                g.lay(x, z, 0);
            }
        }
        int hits = 0;
        for (int t = 0; t < 200; t++) {
            // the player walks across 400 cells of goo, a new cell every half second
            if (g.tryDamage(p, (t / 10) % 20, (t / 10) / 20, t)) {
                hits++;
            }
        }
        check("all goo: walking over many cells of one big patch for 10 s is still 10 ticks, not one per cell", hits == 10, "hits " + hits);
        g = new GooGrid();
        g.lay(0, 0, 0);
        g.lay(50, 50, 0);
        int hops = 0;
        for (int t = 0; t < 200; t++) {
            boolean left = (t / 5) % 2 == 0;
            if (g.tryDamage(p, left ? 0 : 50, left ? 0 : 50, t)) {
                hops++;
            }
        }
        check("all goo: hopping between two separate patches every 5 ticks is still 10 ticks, not 40", hops == 10, "hops " + hops);
        check("all goo: leaving goo and coming back does not reset the clock to cheat a free first tick", leaveAndReturn(), "");
        check("all goo: a player who never stood on goo gets a hit on the first tick, once", firstContact(), "");

        // ---- clock oddities --------------------------------------------------------------------------------------------------------
        g = new GooGrid();
        g.lay(0, 0, 5000);
        check("clock: damage works at a huge tick count", g.tryDamage(p, 0, 0, 5000) && !g.tryDamage(p, 0, 0, 5019) && g.tryDamage(p, 0, 0, 5020), "");
        g = new GooGrid();
        g.lay(0, 0, 0);
        boolean hitAt300 = g.tryDamage(p, 0, 0, 300);
        check("clock: a clock that goes BACKWARDS (hurt at 300, then asked at 100, goo still alive) does not lock the player out", hitAt300 && g.tryDamage(p, 0, 0, 100), "hit at 300: " + hitAt300);
        check("clock: Long.MAX_VALUE as 'now' does not crash and finds no goo", !new GooGrid().isGoo(0, 0, Long.MAX_VALUE) && new GooGrid().count(Long.MAX_VALUE) == 0, "");
        check("clock: goo laid above the int range (tick 3 billion) lives exactly 400 ticks, not zero", hugeTick(), "");
        check("clock: goo laid just under the int max (2147483000) lives exactly 400 ticks across the int boundary", nearIntMax(), "");
        check("clock: laying near Long.MAX_VALUE neither crashes nor wraps negative; the end saturates, so the single last tick MAX is never goo", nearLongMax(), "");

        // ---- clear and forget (no leak into the next run) --------------------------------------------------------------------------------
        g = new GooGrid();
        g.lay(1, 1, 0);
        g.tryDamage(p, 1, 1, 0);
        g.clear();
        check("clear: no goo remains and nothing is counted", g.count(0) == 0 && !g.isGoo(1, 1, 0) && g.cells(0).isEmpty(), "");
        g.lay(1, 1, 0);
        check("clear: the damage clocks are gone too, so the first tick in a new run hurts at once", g.tryDamage(p, 1, 1, 1), "");
        g = new GooGrid();
        g.lay(0, 0, 0);
        g.tryDamage(p, 0, 0, 0);
        g.tryDamage(q, 0, 0, 0);
        g.forget(p);
        check("forget: only that player's clock is cleared (p hurt again at once, q still waiting)", g.tryDamage(p, 0, 0, 1) && !g.tryDamage(q, 0, 0, 1), "");

        System.out.println(fails == 0 ? "ALL PASS (" + total + " checks)" : "FAILED " + fails + " of " + total);
        if (fails != 0) {
            System.exit(1);
        }
    }

    // ---- helpers: each one computes its expected answer from the rule, not from GooGrid ------------------------------------------------

    static boolean eq(int[] c, int x, int z) {
        return c[0] == x && c[1] == z;
    }

    static String render(List<int[]> cs) {
        StringBuilder sb = new StringBuilder();
        for (int[] c : cs) {
            sb.append('(').append(c[0]).append(',').append(c[1]).append(')');
        }
        return sb.toString();
    }

    static boolean layNegatives() {
        GooGrid g = new GooGrid();
        int[][] pts = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}, {-1, 0}, {0, -1}, {0, 0}};
        for (int[] pt : pts) {
            g.lay(pt[0], pt[1], 0);
        }
        if (g.count(0) != pts.length) {
            return false;
        }
        for (int[] pt : pts) {
            if (!g.isGoo(pt[0], pt[1], 0)) {
                return false;
            }
        }
        return !g.isGoo(2, 2, 0) && !g.isGoo(-2, -2, 0);
    }

    static boolean extremes() {
        GooGrid g = new GooGrid();
        int[] v = {Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE};
        int n = 0;
        for (int x : v) {
            for (int z : v) {
                g.lay(x, z, 0);
                n++;
            }
        }
        if (g.count(0) != n) {
            return false;
        }
        Set<String> seen = new HashSet<>();
        for (int[] c : g.cells(0)) {
            seen.add(c[0] + "," + c[1]);
        }
        return seen.size() == n;
    }

    static boolean capBoundary() {
        GooGrid g = new GooGrid();
        for (int i = 0; i < 399; i++) {
            g.lay(i, 0, 0);
        }
        boolean takesThe400th = g.lay(399, 0, 0);
        boolean refusesThe401st = !g.lay(400, 0, 0);
        return takesThe400th && refusesThe401st && g.count(0) == 400;
    }

    static boolean capFreedByExpiry() {
        GooGrid g = new GooGrid();
        for (int i = 0; i < 400; i++) {
            g.lay(i, 0, 0);
        }
        boolean full = !g.lay(999, 999, 399);
        boolean freed = g.lay(999, 999, 400);
        return full && freed && g.count(400) == 1;
    }

    static boolean fullOfDeadGoo() {
        GooGrid g = new GooGrid();
        for (int i = 0; i < 400; i++) {
            g.lay(i, 0, 0);
        }
        int ok = 0;
        for (int i = 0; i < 400; i++) {
            if (g.lay(i, 1, 10000)) {
                ok++;
            }
        }
        return ok == 400 && g.count(10000) == 400 && !g.isGoo(0, 0, 10000);
    }

    static boolean hammer() {
        GooGrid g = new GooGrid();
        long seed = 12345;
        for (int i = 0; i < 100000; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int x = (int) ((seed >>> 33) % 200) - 100;
            int z = (int) ((seed >>> 13) % 200) - 100;
            g.lay(x, z, i / 50);
            if (g.count(i / 50) > GooGrid.MAX_CELLS) {
                return false;
            }
        }
        return true;
    }

    /** A player stands on goo and asks every {@code step} ticks for {@code ticks} ticks; counts hits. */
    static int standingTicks(int ticks, int step) {
        GooGrid g = new GooGrid();
        for (int t = 0; t < ticks + 1000; t += 400) {
            g.lay(0, 0, t);
        }
        UUID p = new UUID(0, 9);
        int hits = 0;
        for (int t = 0; t < ticks; t += step) {
            if (g.tryDamage(p, 0, 0, t)) {
                hits++;
            }
        }
        return hits;
    }

    static boolean spacingOk(int ticks, int step) {
        GooGrid g = new GooGrid();
        g.lay(0, 0, 0);
        g.lay(0, 0, 300);
        UUID p = new UUID(0, 9);
        long last = -1000;
        int hits = 0;
        for (int t = 0; t < ticks; t += step) {
            if (g.tryDamage(p, 0, 0, t)) {
                if (t - last < 20) {
                    return false;
                }
                last = t;
                hits++;
            }
        }
        return hits >= 9 && hits <= 10;
    }

    static boolean independentPlayers() {
        GooGrid g = new GooGrid();
        g.lay(0, 0, 0);
        UUID p = new UUID(0, 1);
        UUID q = new UUID(0, 2);
        boolean a = g.tryDamage(p, 0, 0, 0);
        boolean b = g.tryDamage(q, 0, 0, 3);
        boolean c = !g.tryDamage(p, 0, 0, 10);
        boolean d = !g.tryDamage(q, 0, 0, 10);
        boolean e = g.tryDamage(p, 0, 0, 20);
        boolean f = !g.tryDamage(q, 0, 0, 20);
        boolean h = g.tryDamage(q, 0, 0, 23);
        return a && b && c && d && e && f && h;
    }

    static boolean leaveAndReturn() {
        GooGrid g = new GooGrid();
        g.lay(0, 0, 0);
        UUID p = new UUID(0, 1);
        boolean first = g.tryDamage(p, 0, 0, 0);
        boolean offGoo = !g.tryDamage(p, 9, 9, 5);
        boolean back = !g.tryDamage(p, 0, 0, 10);
        boolean later = g.tryDamage(p, 0, 0, 20);
        return first && offGoo && back && later;
    }

    static boolean firstContact() {
        GooGrid g = new GooGrid();
        g.lay(0, 0, 100);
        UUID p = new UUID(0, 1);
        return g.tryDamage(p, 0, 0, 100) && !g.tryDamage(p, 0, 0, 101);
    }

    static boolean hugeTick() {
        GooGrid g = new GooGrid();
        long t = 3_000_000_000L;
        g.lay(0, 0, t);
        return g.isGoo(0, 0, t) && g.isGoo(0, 0, t + 399) && !g.isGoo(0, 0, t + 400) && g.count(t + 399) == 1 && g.count(t + 400) == 0;
    }

    static boolean nearIntMax() {
        GooGrid g = new GooGrid();
        long t = 2_147_483_000L;
        g.lay(0, 0, t);
        return g.isGoo(0, 0, t) && g.isGoo(0, 0, t + 399) && !g.isGoo(0, 0, t + 400);
    }

    static boolean nearLongMax() {
        // Far enough from the top that nothing saturates: laid at MAX-1000, so it ends at MAX-600 (goo up to MAX-601).
        GooGrid g = new GooGrid();
        long t = Long.MAX_VALUE - 1000;
        boolean laid = g.lay(0, 0, t);
        boolean plain = laid && g.isGoo(0, 0, t) && g.isGoo(0, 0, Long.MAX_VALUE - 601) && !g.isGoo(0, 0, Long.MAX_VALUE - 600) && g.count(t) == 1;
        // Close enough that the add would overflow: laid at MAX-100, the end saturates at MAX, so it is goo up to MAX-1 and never at MAX itself.
        GooGrid h = new GooGrid();
        long u = Long.MAX_VALUE - 100;
        boolean laid2 = h.lay(0, 0, u);
        boolean saturated = laid2 && h.isGoo(0, 0, u) && h.isGoo(0, 0, Long.MAX_VALUE - 1) && !h.isGoo(0, 0, Long.MAX_VALUE) && h.count(u) == 1;
        return plain && saturated;
    }
}
