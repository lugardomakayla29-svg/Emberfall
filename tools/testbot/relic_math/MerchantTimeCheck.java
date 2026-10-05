import com.solme.emberfall.relic.*;
import java.util.*;
public class MerchantTimeCheck {
    static int fails = 0;
    static void check(String l, boolean ok, String e) { System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e); if (!ok) fails++; }
    public static void main(String[] a) {
        // ---- schedule
        check("nothing before the first arrival time", !MerchantSchedule.shouldArrive(119, -1, false, -1, false) && MerchantSchedule.shouldArrive(120, -1, false, -1, false), "");
        check("never while one is standing", !MerchantSchedule.shouldArrive(9999, -1, true, -1, false), "");
        check("the next one waits GAP seconds after the last left", !MerchantSchedule.shouldArrive(399, 220, false, -1, false) && MerchantSchedule.shouldArrive(400, 220, false, -1, false), "220+180=400");
        check("never once the final swarm has begun", !MerchantSchedule.shouldArrive(5000, 100, false, -1, true), "");
        check("none in the last 30 s of a run with a known end", !MerchantSchedule.shouldArrive(571, -1, false, 600, false) && MerchantSchedule.shouldArrive(569, -1, false, 600, false), "end 600");
        check("open-ended run (-1) has no quiet period", MerchantSchedule.shouldArrive(100000, 500, false, -1, false), "");
        check("seconds left counts down 60 -> 0 and never goes negative", MerchantSchedule.secondsLeft(100, 100) == 60 && MerchantSchedule.secondsLeft(130, 100) == 30 && MerchantSchedule.secondsLeft(160, 100) == 0 && MerchantSchedule.secondsLeft(999, 100) == 0, "");
        check("expired exactly at STAY", !MerchantSchedule.expired(159, 100) && MerchantSchedule.expired(160, 100), "");
        // a whole run: with a 1200 s run and merchants that always wait their full stay, how many visits?
        long t = 0, left = -1; int visits = 0; boolean present = false; long arrived = 0; List<Long> at = new ArrayList<>();
        for (t = 0; t < 1200; t++) {
            if (present && MerchantSchedule.expired(t, arrived)) { present = false; left = t; }
            if (MerchantSchedule.shouldArrive(t, left, present, 1200, false)) { present = true; arrived = t; visits++; at.add(t); }
        }
        check("a 20 minute run where nobody buys sees 5 visits at 120, 360, 600, 840, 1080", visits == 5 && at.equals(List.of(120L, 360L, 600L, 840L, 1080L)), at.toString());
        // ---- fireworks
        check("eight distinct designs", MerchantFireworks.DESIGNS.length == 8 && Arrays.stream(MerchantFireworks.DESIGNS).map(d -> d.name()).distinct().count() == 8, "");
        boolean shapes = true, colours = true; for (var d : MerchantFireworks.DESIGNS) { if (d.blasts().length < 1 || d.blasts().length > 2) shapes = false; for (var b : d.blasts()) { if (b.shape() < 0 || b.shape() > 4 || b.colors().length == 0) colours = false; } }
        check("every design has 1 or 2 blasts, valid shapes, at least one colour", shapes && colours, "");
        Set<Integer> shapesUsed = new HashSet<>(); for (var d : MerchantFireworks.DESIGNS) for (var b : d.blasts()) shapesUsed.add(b.shape());
        check("all five vanilla shapes appear across the designs", shapesUsed.size() == 5, shapesUsed.toString());
        Random r = new Random(3); int repeats = 0; int[] seen = new int[8]; int last = -1; int oob = 0;
        for (int i = 0; i < 100000; i++) { int p = MerchantFireworks.choose(last, r::nextDouble); if (p < 0 || p > 7) oob++; if (p == last) repeats++; seen[Math.max(0, Math.min(7, p))]++; last = p; }
        int min = Arrays.stream(seen).min().getAsInt(), max = Arrays.stream(seen).max().getAsInt();
        check("100000 picks: never out of range, never the same design twice in a row", oob == 0 && repeats == 0, "oob=" + oob + " repeats=" + repeats);
        check("every design is used and the spread is even (max/min < 1.1)", min > 0 && max / (double) min < 1.1, Arrays.toString(seen));
        check("edge rolls 0.0 and 0.999999 stay in range for every 'last'", java.util.stream.IntStream.rangeClosed(-1, 7).allMatch(l -> { int lo = MerchantFireworks.choose(l, () -> 0.0), hi = MerchantFireworks.choose(l, () -> 0.999999); return lo >= 0 && hi <= 7 && lo != l && hi != l; }), "");
        System.out.println(fails == 0 ? "ALL PASS" : "FAILED " + fails);
    }
}
