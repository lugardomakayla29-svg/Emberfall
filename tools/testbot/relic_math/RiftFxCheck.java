import com.solme.emberfall.rift.RiftFx;
import com.solme.emberfall.rift.RiftFx.Event;
import com.solme.emberfall.rift.RiftFx.Kind;
import com.solme.emberfall.rift.RiftRules;
import com.solme.emberfall.rift.RiftShape;
import com.solme.emberfall.rift.RiftShape.Shape;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure checks for the Rift's opening and closing schedule. They prove the SCHEDULE: its timing, its order, that each cell is lit
 * once, that the busiest tick stays inside the particle budget, and that it can only ask for particles, sounds, chat and a push,
 * never an entity. They do NOT prove it looks or sounds right, and they do not spawn anything in a world. The live half (packets
 * a real client receives) needs the adapter wired into the mod and is NOT in this PR. The mutation proof is in the PR.
 */
public class RiftFxCheck {
    static int fails = 0;
    static int total = 0;
    static final int SEEDS = 600;
    /** The ONLY keys the schedule may use. An entity name, "summon" or anything else is not on it. */
    static final Set<String> KEYS = new HashSet<>(Arrays.asList(
            "end_rod", "electric_spark", "glow", "dust_ring", "rift_drone", "rift_crackle", "rift_boom", "rift_hum", "rift_push",
            "rift_tearing", "rift_open", "rift_closing", "rift_collapsed"));

    static void check(String l, boolean ok, String e) {
        total++;
        System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e);
        if (!ok) {
            fails++;
        }
    }

    public static void main(String[] a) {
        // ---- timeline basics ----------------------------------------------------------------------------------------------
        int empty = 0, unsorted = 0, outOfRange = 0, noStart = 0, noEnd = 0;
        for (long s = 0; s < SEEDS; s++) {
            List<Event> ev = RiftFx.opening(RiftShape.generate(s));
            if (ev.isEmpty()) {
                empty++;
                continue;
            }
            for (int i = 1; i < ev.size(); i++) {
                if (ev.get(i).tick < ev.get(i - 1).tick) {
                    unsorted++;
                }
            }
            for (Event e : ev) {
                if (e.tick < 0 || e.tick > 100) {
                    outOfRange++;
                }
            }
            if (ev.get(0).tick != 0) {
                noStart++;
            }
            if (ev.get(ev.size() - 1).tick != 100) {
                noEnd++;
            }
        }
        check("timeline: no shape gives an empty opening", empty == 0, "empty " + empty);
        check("timeline: events are in time order", unsorted == 0, "unsorted " + unsorted);
        check("timeline: every event is between tick 0 and the open tick", outOfRange == 0, "out of range " + outOfRange);
        check("timeline: it starts at tick 0", noStart == 0, "no start " + noStart);
        check("timeline: it ends exactly at the open tick (5 s = 100 ticks)", noEnd == 0 && RiftFx.OPEN_TICK == 100, "no end " + noEnd);
        check("timeline: an empty shape gives an empty opening, not a crash", RiftFx.opening(RiftShape.fromRows("...", "...")).isEmpty(), "");

        // ---- the constants ARE the design's beats (20 ticks per second): the checks below use the literal numbers ------------
        check("design: the constants are the design's times (0, 0.5, 2.0, 2.5, 4.0, 5.0 s = 0, 10, 40, 50, 80, 100 ticks)",
                RiftFx.T_POINT == 0 && RiftFx.T_CRACK_START == 10 && RiftFx.T_CRACK_END == 40 && RiftFx.T_FLARE == 40 && RiftFx.T_FILL_START == 50 && RiftFx.T_FILL_END == 80 && RiftFx.T_PULSE == 80 && RiftFx.OPEN_TICK == 100, "");
        check("design: the closing is 34 ticks (1.7 s), a third of 5 s, and the flare spread ends before the fill", RiftFx.CLOSE_TICKS == 34 && RiftFx.T_FLARE + RiftFx.FLARE_SPREAD < RiftFx.T_FILL_START, "");

        // ---- the design's beats happen at the right moments ---------------------------------------------------------------
        Shape sh = RiftShape.generate(21);
        List<Event> op = RiftFx.opening(sh);
        check("beat: tick 0 has a chat warning, a drone and a bright point", has(op, 0, Kind.CHAT, "rift_tearing") && has(op, 0, Kind.SOUND, "rift_drone") && hasKind(op, 0, Kind.PARTICLE), "");
        check("beat: the boom is at 2.0 s (tick 40)", has(op, 40, Kind.SOUND, "rift_boom"), "");
        check("beat: the push and the hum begin at 4.0 s (tick 80)", has(op, 80, Kind.PUSH, "rift_push") && has(op, 80, Kind.SOUND, "rift_hum"), "");
        check("beat: the chat says open at 5.0 s (tick 100) and not before", has(op, 100, Kind.CHAT, "rift_open") && count(op, Kind.CHAT, "rift_open") == 1, "");
        check("beat: exactly one warning and one open line in the whole opening", count(op, Kind.CHAT, "rift_tearing") == 1 && count(op, Kind.CHAT, null) == 2, "");
        check("beat: nothing happens between the end of the fill and the push except the hum's lead-in", noParticlesBetween(op, 79, 100), "");
        check("beat: the crack starts at 0.5 s (tick 10), not earlier", firstTick(op, "electric_spark") == 10, "first " + firstTick(op, "electric_spark"));
        check("beat: the crack ends before the flare (by tick 40)", lastTick(op, "electric_spark") <= 40, "last " + lastTick(op, "electric_spark"));

        // ---- the crack grows OUTWARD and lights every rim cell exactly once -----------------------------------------------
        int badOrder = 0, notOnce = 0, notRim = 0, crackBeforeStart = 0;
        for (long s = 0; s < SEEDS; s++) {
            Shape x = RiftShape.generate(s);
            List<Event> ev = RiftFx.opening(x);
            int[] c = centreOf(x);
            Set<String> rim = bodyRim(x);
            Set<String> seen = new HashSet<>();
            double lastD = -1;
            for (Event e : ev) {
                if (e.kind == Kind.PARTICLE && e.key.equals("electric_spark")) {
                    String k = e.x + "," + e.y;
                    if (!seen.add(k)) {
                        notOnce++;
                    }
                    if (!rim.contains(k)) {
                        notRim++;
                    }
                    double d = Math.hypot(e.x - c[0], e.y - c[1]);
                    if (d + 1e-9 < lastD) {
                        badOrder++;
                    }
                    lastD = d;
                    if (e.tick < 10) {
                        crackBeforeStart++;
                    }
                }
            }
            if (!seen.equals(rim)) {
                notOnce++;
            }
        }
        check("crack: it grows outward (never closer to the centre than the cell before)", badOrder == 0, "bad order " + badOrder);
        check("crack: every rim cell is lit exactly once and nothing else is", notOnce == 0, "not once " + notOnce);
        check("crack: it only ever lights rim cells", notRim == 0, "not rim " + notRim);
        check("crack: nothing is lit before 0.5 s", crackBeforeStart == 0, "early " + crackBeforeStart);

        // ---- the fill floods in from the centre, once per interior cell ----------------------------------------------------
        int fillBad = 0, fillOrder = 0, fillEarly = 0, fillLate = 0;
        for (long s = 0; s < SEEDS; s++) {
            Shape x = RiftShape.generate(s);
            List<Event> ev = RiftFx.opening(x);
            int[] c = centreOf(x);
            Set<String> rim = bodyRim(x);
            Set<String> want = new HashSet<>();
            for (int[] cell : x.cells()) {
                if (x.isBody(cell[0], cell[1]) && !rim.contains(cell[0] + "," + cell[1])) {
                    want.add(cell[0] + "," + cell[1]);
                }
            }
            Set<String> got = new HashSet<>();
            double lastD = -1;
            for (Event e : ev) {
                if (e.kind == Kind.PARTICLE && e.key.equals("glow")) {
                    if (!got.add(e.x + "," + e.y)) {
                        fillBad++;
                    }
                    double d = Math.hypot(e.x - c[0], e.y - c[1]);
                    if (d + 1e-9 < lastD) {
                        fillOrder++;
                    }
                    lastD = d;
                    if (e.tick < 50) {
                        fillEarly++;
                    }
                    if (e.tick > 80) {
                        fillLate++;
                    }
                }
            }
            if (!got.equals(want)) {
                fillBad++;
            }
        }
        check("fill: every interior body cell is filled exactly once and nothing else", fillBad == 0, "bad " + fillBad);
        check("fill: it floods outward from the centre", fillOrder == 0, "bad order " + fillOrder);
        check("fill: nothing before 2.5 s and nothing after 4.0 s", fillEarly == 0 && fillLate == 0, "early " + fillEarly + " late " + fillLate);

        // ---- the particle budget -------------------------------------------------------------------------------------------
        int over = 0, lost = 0, peakMax = 0, peakMin = Integer.MAX_VALUE;
        for (long s = 0; s < SEEDS; s++) {
            for (boolean flat : new boolean[] {false, true}) {
                List<Event> ev = RiftFx.opening(RiftShape.generate(s, flat));
                int pk = RiftFx.peakParticles(ev);
                peakMax = Math.max(peakMax, pk);
                peakMin = Math.min(peakMin, pk);
                if (pk > RiftRules.BUDGET_PER_TICK) {
                    over++;
                }
                for (int t = 0; t <= 100; t++) {
                    int want = RiftFx.particlesAt(ev, t);
                    if (RiftRules.particlesThisTick(want, 1) != want) {
                        lost++;
                    }
                }
            }
        }
        check("budget: no shape's busiest tick exceeds the per-tick budget", over == 0, "over " + over + " peak " + peakMax + " of " + RiftRules.BUDGET_PER_TICK);
        check("budget: the busiest tick leaves real headroom (under three quarters of the budget)", peakMax * 4 <= RiftRules.BUDGET_PER_TICK * 3, "peak " + peakMax);
        check("budget: the executor never has to cut a particle (the budget clamp changes nothing)", lost == 0, "clamped " + lost);
        check("budget: the schedule is not empty or trivial (the busiest tick has at least 20 particles)", peakMin >= 20, "min peak " + peakMin);
        check("budget: the closing also stays inside the budget", closingPeakOk(), "");
        check("budget: no event carries a negative or absurd density", densitiesOk(), "");
        check("budget: a particle event always has a positive count and a non-particle event has none", countsMatchKind(), "");

        // ---- ZERO ENTITIES: the schedule can only ask for particles, sounds, chat and a push ------------------------------
        check("entities: the only kinds are particle, sound, chat and push (no kind can spawn an entity)", Arrays.equals(Kind.values(), new Kind[] {Kind.PARTICLE, Kind.SOUND, Kind.CHAT, Kind.PUSH}), Arrays.toString(Kind.values()));
        int badKey = 0;
        for (long s = 0; s < SEEDS; s++) {
            for (Event e : RiftFx.opening(RiftShape.generate(s))) {
                if (!KEYS.contains(e.key)) {
                    badKey++;
                }
            }
            for (Event e : RiftFx.closing(RiftShape.generate(s))) {
                if (!KEYS.contains(e.key)) {
                    badKey++;
                }
            }
        }
        check("entities: every key in every opening and closing is on the allow-list (no entity id, no summon)", badKey == 0, "bad keys " + badKey);
        check("entities: the allow-list itself names no entity", noEntityWords(), "");
        int pushBad = 0;
        for (long s = 0; s < SEEDS; s++) {
            if (count(RiftFx.opening(RiftShape.generate(s)), Kind.PUSH, null) != 1) {
                pushBad++;
            }
            if (count(RiftFx.closing(RiftShape.generate(s)), Kind.PUSH, null) != 0) {
                pushBad++;
            }
        }
        check("entities: one gentle push in the opening, none in the closing", pushBad == 0, "bad " + pushBad);

        // ---- closing: the same schedule backwards and faster --------------------------------------------------------------
        int closeLong = 0, closeUnsorted = 0, closeMissing = 0, noCollapse = 0, noClosing = 0, closeOutside = 0;
        for (long s = 0; s < SEEDS; s++) {
            Shape x = RiftShape.generate(s);
            List<Event> o = RiftFx.opening(x);
            List<Event> c = RiftFx.closing(x);
            for (int i = 1; i < c.size(); i++) {
                if (c.get(i).tick < c.get(i - 1).tick) {
                    closeUnsorted++;
                }
            }
            for (Event e : c) {
                if (e.tick < 0 || e.tick > 34) {
                    closeOutside++;
                }
            }
            if (c.get(c.size() - 1).tick > 34) {
                closeLong++;
            }
            if (count(c, Kind.PARTICLE, null) != count(o, Kind.PARTICLE, null) || count(c, Kind.SOUND, null) != count(o, Kind.SOUND, null)) {
                closeMissing++;
            }
            if (count(c, Kind.CHAT, "rift_collapsed") != 1) {
                noCollapse++;
            }
            if (count(c, Kind.CHAT, "rift_closing") != 1) {
                noClosing++;
            }
        }
        check("closing: it is faster than the opening (a third of the time)", RiftFx.CLOSE_TICKS * 3 <= RiftFx.OPEN_TICK + 2 && RiftFx.CLOSE_TICKS < RiftFx.OPEN_TICK, "close " + RiftFx.CLOSE_TICKS);
        check("closing: events are in time order", closeUnsorted == 0, "unsorted " + closeUnsorted);
        check("closing: no event is outside the closing's own length", closeOutside == 0 && closeLong == 0, "outside " + closeOutside + " long " + closeLong);
        check("closing: every particle and sound of the opening is played again, none lost or added", closeMissing == 0, "mismatch " + closeMissing);
        check("closing: it says the Rift is closing at the start and collapsed at the end, once each", noCollapse == 0 && noClosing == 0, "collapse " + noCollapse + " closing " + noClosing);
        List<Event> cl = RiftFx.closing(sh);
        check("closing: it is the opening backwards (the fill leaves before the rim, the bright point goes last)", firstTick(cl, "glow") < firstTick(cl, "electric_spark") && lastTickKind(cl, Kind.PARTICLE) >= firstTick(cl, "electric_spark"), "");
        check("closing: the bright point of the opening is the last particle of the closing", lastParticleKey(cl).equals("end_rod") && lastTickKind(cl, Kind.PARTICLE) == 34, "");

        // ---- determinism and orientation ------------------------------------------------------------------------------------
        int unstable = 0;
        for (long s = 0; s < 100; s++) {
            if (!RiftFx.opening(RiftShape.generate(s)).toString().equals(RiftFx.opening(RiftShape.generate(s)).toString())) {
                unstable++;
            }
        }
        check("determinism: the same shape gives the same schedule every time", unstable == 0, "unstable " + unstable);
        check("determinism: two different shapes give different schedules", !RiftFx.opening(RiftShape.generate(1)).toString().equals(RiftFx.opening(RiftShape.generate(2)).toString()), "");
        int sizeDiffers = 0;
        for (long s = 0; s < 200; s++) {
            List<Event> u = RiftFx.opening(RiftShape.generate(s, false));
            List<Event> f = RiftFx.opening(RiftShape.generate(s, true));
            if (u.size() != f.size() || RiftFx.peakParticles(u) != RiftFx.peakParticles(f)) {
                sizeDiffers++;
            }
        }
        check("orientation: the flat Rift has the same number of events and the same peak as the upright one", sizeDiffers == 0, "differs " + sizeDiffers);

        // ---- hand-made shapes with known answers ----------------------------------------------------------------------------
        Shape tiny = RiftShape.fromRows("###", "###", "###");
        List<Event> te = RiftFx.opening(tiny);
        check("hand: a 3x3 block lights its 8 rim cells in the crack and its 1 centre cell in the fill", count(te, Kind.PARTICLE, "electric_spark") == 8 && count(te, Kind.PARTICLE, "glow") == 1, count(te, Kind.PARTICLE, "electric_spark") + " rim, " + count(te, Kind.PARTICLE, "glow") + " fill");
        check("hand: a 1 cell shape still opens and closes without a crash", RiftFx.opening(RiftShape.fromRows("#")).size() > 0 && RiftFx.closing(RiftShape.fromRows("#")).size() > 0, "");
        check("hand: a satellite is announced by the fill beat, not by the crack (1 crack for the 1 body cell, 1 satellite pop)", count(RiftFx.opening(RiftShape.fromRows("#.o")), Kind.PARTICLE, "electric_spark") == 1 && popsOf(RiftFx.opening(RiftShape.fromRows("#.o")), 2, 0) == 1, "");
        check("hand: the satellite pops at the fill beat (tick 60), never during the crack", onlyAt(RiftFx.opening(RiftShape.fromRows("#.o")), 2, 0, 60), "");

        System.out.println(fails == 0 ? "ALL PASS (" + total + " checks)" : "FAILED " + fails + " of " + total);
        if (fails != 0) {
            System.exit(1);
        }
    }

    // ---- helpers (independent of the class under test where it matters) ---------------------------------------------------------

    static boolean has(List<Event> ev, int tick, Kind k, String key) {
        for (Event e : ev) {
            if (e.tick == tick && e.kind == k && e.key.equals(key)) {
                return true;
            }
        }
        return false;
    }

    static boolean hasKind(List<Event> ev, int tick, Kind k) {
        for (Event e : ev) {
            if (e.tick == tick && e.kind == k) {
                return true;
            }
        }
        return false;
    }

    static int count(List<Event> ev, Kind k, String key) {
        int n = 0;
        for (Event e : ev) {
            if (e.kind == k && (key == null || e.key.equals(key))) {
                n++;
            }
        }
        return n;
    }

    static int firstTick(List<Event> ev, String key) {
        int best = Integer.MAX_VALUE;
        for (Event e : ev) {
            if (e.key.equals(key)) {
                best = Math.min(best, e.tick);
            }
        }
        return best;
    }

    static int lastTick(List<Event> ev, String key) {
        int best = -1;
        for (Event e : ev) {
            if (e.key.equals(key)) {
                best = Math.max(best, e.tick);
            }
        }
        return best;
    }

    static int lastTickKind(List<Event> ev, Kind k) {
        int best = -1;
        for (Event e : ev) {
            if (e.kind == k) {
                best = Math.max(best, e.tick);
            }
        }
        return best;
    }

    static String lastParticleKey(List<Event> ev) {
        String key = "";
        int t = -1;
        for (Event e : ev) {
            if (e.kind == Kind.PARTICLE && e.tick >= t) {
                t = e.tick;
                key = e.key;
            }
        }
        return key;
    }

    static boolean noParticlesBetween(List<Event> ev, int from, int to) {
        for (Event e : ev) {
            if (e.kind == Kind.PARTICLE && e.tick > from && e.tick < to) {
                return false;
            }
        }
        return true;
    }

    /** The rim of the BODY alone, derived here from isBody only (not from Shape.rim, which also lists satellites). */
    static Set<String> bodyRim(Shape s) {
        Set<String> out = new HashSet<>();
        for (int x = 0; x < s.width; x++) {
            for (int y = 0; y < s.height; y++) {
                if (s.isBody(x, y) && (!s.isBody(x - 1, y) || !s.isBody(x + 1, y) || !s.isBody(x, y - 1) || !s.isBody(x, y + 1))) {
                    out.add(x + "," + y);
                }
            }
        }
        return out;
    }

    static Set<String> cellSet(List<int[]> cells) {
        Set<String> s = new HashSet<>();
        for (int[] c : cells) {
            s.add(c[0] + "," + c[1]);
        }
        return s;
    }

    /** Own copy of the centre rule (mean of the body cells, rounded), so the check does not trust the class's helper. */
    static int[] centreOf(Shape s) {
        long sx = 0, sy = 0;
        int n = 0;
        for (int x = 0; x < s.width; x++) {
            for (int y = 0; y < s.height; y++) {
                if (s.isBody(x, y)) {
                    sx += x;
                    sy += y;
                    n++;
                }
            }
        }
        return new int[] {(int) Math.round((double) sx / n), (int) Math.round((double) sy / n)};
    }

    /** Number of particle events at cell (x, y). */
    static int popsOf(List<Event> ev, int x, int y) {
        int n = 0;
        for (Event e : ev) {
            if (e.kind == Kind.PARTICLE && e.x == x && e.y == y) {
                n++;
            }
        }
        return n;
    }

    /** True when every particle event at cell (x, y) is at exactly this tick. */
    static boolean onlyAt(List<Event> ev, int x, int y, int tick) {
        boolean any = false;
        for (Event e : ev) {
            if (e.kind == Kind.PARTICLE && e.x == x && e.y == y) {
                any = true;
                if (e.tick != tick) {
                    return false;
                }
            }
        }
        return any;
    }

    static boolean closingPeakOk() {
        for (long s = 0; s < SEEDS; s++) {
            if (RiftFx.peakParticles(RiftFx.closing(RiftShape.generate(s))) > RiftRules.BUDGET_PER_TICK) {
                return false;
            }
        }
        return true;
    }

    static boolean densitiesOk() {
        int[] d = {RiftFx.DENSITY_POINT, RiftFx.DENSITY_CRACK, RiftFx.DENSITY_FLARE, RiftFx.DENSITY_DUST, RiftFx.DENSITY_FILL, RiftFx.DENSITY_SATELLITE};
        for (int x : d) {
            if (x < 1 || x > RiftRules.BUDGET_PER_TICK) {
                return false;
            }
        }
        return true;
    }

    static boolean countsMatchKind() {
        for (long s = 0; s < 100; s++) {
            for (Event e : RiftFx.opening(RiftShape.generate(s))) {
                if (e.kind == Kind.PARTICLE ? e.count < 1 : e.count != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    static boolean noEntityWords() {
        String[] banned = {"summon", "zombie", "husk", "armor_stand", "display", "item_frame", "marker", "interaction", "spawn"};
        for (String k : KEYS) {
            for (String b : banned) {
                if (k.contains(b)) {
                    return false;
                }
            }
        }
        return true;
    }
}
