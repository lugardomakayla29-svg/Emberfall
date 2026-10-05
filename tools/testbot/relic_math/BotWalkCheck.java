import com.solme.emberfall.bot.BotWalk;
import com.solme.emberfall.bot.BotWalk.*;
import java.util.*;
public class BotWalkCheck {
    static int fails = 0;
    static void check(String l, boolean ok, String e) { System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e); if (!ok) fails++; }
    static boolean near(double a, double b) { return Math.abs(a - b) < 1e-9; }
    public static void main(String[] args) {
        List<Point> line = List.of(new Point(10, 64, 0), new Point(10, 64, 10));
        Step s = BotWalk.advance(0, 64, 0, line, 0, 0.25);
        check("one tick moves exactly the speed toward the point", near(s.x(), 0.25) && near(s.z(), 0) && s.nextIndex() == 0 && !s.arrived(), s.x() + "," + s.z());
        double x = 0, y = 64, z = 0; int idx = 0; double maxStep = 0; int ticks = 0; boolean arrived = false;
        while (!arrived && ticks < 1000) { Step t = BotWalk.advance(x, y, z, line, idx, 0.25); maxStep = Math.max(maxStep, Math.hypot(t.x() - x, t.z() - z)); x = t.x(); y = t.y(); z = t.z(); idx = t.nextIndex(); arrived = t.arrived(); ticks++; }
        check("a 20-block route at 0.25 per tick takes exactly 80 ticks", ticks == 80, "ticks " + ticks);
        check("it ends exactly on the last point", near(x, 10) && near(z, 10), x + "," + z);
        check("no tick ever moves further than the speed", maxStep <= 0.25 + 1e-9, "max " + maxStep);
        Step c = BotWalk.advance(9.9, 64, 0, line, 0, 0.5);
        check("a corner keeps the rest of the tick (0.1 to the corner, 0.4 along the next leg)", near(c.x(), 10) && near(c.z(), 0.4) && c.nextIndex() == 1, c.x() + "," + c.z());
        List<Point> ramp = List.of(new Point(10, 66, 0));
        Step r = BotWalk.advance(0, 64, 0, ramp, 0, 1.0);
        check("a 2-block rise over 10 blocks rises 0.2 per block walked", near(r.y(), 64.2), "y " + r.y());
        double yy = 64, xx = 0, maxRise = 0; int ii = 0; boolean done = false;
        while (!done) { Step t = BotWalk.advance(xx, yy, 0, ramp, ii, 0.25); maxRise = Math.max(maxRise, Math.abs(t.y() - yy)); xx = t.x(); yy = t.y(); ii = t.nextIndex(); done = t.arrived(); }
        check("no tick changes height by more than 0.05 on that ramp, and it ends at 66", maxRise <= 0.05 + 1e-9 && near(yy, 66), "maxRise " + maxRise);
        check("already on the final point: arrives", BotWalk.advance(10, 64, 0, List.of(new Point(10, 64, 0)), 0, 0.25).arrived(), "");
        check("an empty route arrives at once and does not move", BotWalk.advance(1, 2, 3, List.of(), 0, 0.25).arrived() && near(BotWalk.advance(1, 2, 3, List.of(), 0, 0.25).x(), 1), "");
        check("an index past the end arrives (never throws)", BotWalk.advance(1, 2, 3, line, 5, 0.25).arrived(), "");
        check("zero speed never moves and does not arrive", near(BotWalk.advance(0, 64, 0, line, 0, 0.0).x(), 0) && !BotWalk.advance(0, 64, 0, line, 0, 0.0).arrived(), "");
        check("a tiny speed still makes progress", BotWalk.advance(0, 64, 0, line, 0, 0.001).x() > 0, "");
        check("several short points in one tick land on the right leg", BotWalk.advance(0, 64, 0, List.of(new Point(0.1, 64, 0), new Point(0.2, 64, 0), new Point(0.3, 64, 0), new Point(5, 64, 0)), 0, 0.5).nextIndex() == 3, "");
        check("offRoute: walking the first leg from its start, on the line, is on route", !BotWalk.offRoute(0.2, 0, 0, 0, line, 0, 2.0), "");
        check("offRoute: 5 blocks beside the first leg is off route", BotWalk.offRoute(5, 5, 0, 0, line, 0, 2.0), "");
        check("offRoute: on the second leg, on the line, is on route", !BotWalk.offRoute(10, 5, 0, 0, line, 1, 2.0), "");
        check("offRoute: 5 blocks sideways on the second leg (still near its end) is off route", BotWalk.offRoute(5, 5, 0, 0, line, 1, 2.0), "");
        check("offRoute: exactly at the tolerance is on route, just past it is off", !BotWalk.offRoute(8, 5, 0, 0, line, 1, 2.0) && BotWalk.offRoute(7.9, 5, 0, 0, line, 1, 2.0), "");
        check("offRoute: past the end of a leg is measured to its end point", BotWalk.offRoute(10, 14, 0, 0, line, 1, 2.0) && !BotWalk.offRoute(10, 11, 0, 0, line, 1, 2.0), "");
        check("offRoute: a zero-length leg does not divide by zero", !BotWalk.offRoute(1, 1, 1, 1, List.of(new Point(1, 64, 1)), 0, 2.0), "");
        check("offRoute: a finished route is never off route", !BotWalk.offRoute(99, 99, 0, 0, line, 5, 2.0), "");
        System.out.println(fails == 0 ? "ALL PASS" : "FAILED " + fails);
    }
}
