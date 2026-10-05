import com.solme.emberfall.relic.*;
import java.util.*;
public class HourglassCheck {
    static int fails = 0;
    static void check(String l, boolean ok, String e) { System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e); if (!ok) fails++; }
    static RelicStats with(Object... kv) { Map<String,Integer> m = new HashMap<>(); for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Integer) kv[i+1]); return new RelicStats(m); }
    public static void main(String[] x) {
        RelicStats h = with("hourglass", 1), none = with();
        check("no relic never slows", !RelicHourglass.slowsFoes(none, 2, 20), "");
        check("holder at 49% slows", RelicHourglass.slowsFoes(h, 9.8, 20), "");
        check("exactly 50% does NOT slow (strictly below)", !RelicHourglass.slowsFoes(h, 10, 20), "");
        check("full health does not slow", !RelicHourglass.slowsFoes(h, 20, 20), "");
        check("1 hp slows", RelicHourglass.slowsFoes(h, 1, 20), "");
        check("dead (0 hp) does not count", !RelicHourglass.slowsFoes(h, 0, 20), "");
        check("zero max health does not count", !RelicHourglass.slowsFoes(h, 5, 0), "");
        check("NaN health does not count", !RelicHourglass.slowsFoes(h, Double.NaN, 20), "");
        check("works with a raised max (Dragon's Heart 60 max, 29 hp)", RelicHourglass.slowsFoes(h, 29, 60), "");
        check("speed factor is 0.55 (vanilla -15% x III)", Math.abs(RelicHourglass.speedFactor() - 0.55) < 1e-9, "" + RelicHourglass.speedFactor());
        check("duration outlasts the 20 tick sweep", RelicHourglass.DURATION_TICKS > 20 && RelicHourglass.DURATION_TICKS <= 40, "");
        System.out.println(fails == 0 ? "RESULT: ALL PASS" : "RESULT: " + fails + " FAILED"); System.exit(fails == 0 ? 0 : 1);
    }
}
