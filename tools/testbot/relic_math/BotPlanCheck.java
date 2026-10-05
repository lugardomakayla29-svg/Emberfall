import com.solme.emberfall.bot.BotPlan;
import com.solme.emberfall.bot.BotPlan.*;
import java.util.*;
public class BotPlanCheck {
    static int fails = 0;
    static void check(String l, boolean ok, String e) { System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e); if (!ok) fails++; }
    static boolean near(double a, double b) { return Math.abs(a - b) < 1e-9; }
    public static void main(String[] args) {
        check("stand-off is 0.8 of the reach", near(BotPlan.standOff(10), 8.0), "");
        check("stand-off never drops under 1.5 (no hugging a foe)", near(BotPlan.standOff(1.0), 1.5), "");
        List<Foe> foes = List.of(new Foe(10, 0, 0), new Foe(0, 6, 0), new Foe(-20, 0, 0));
        check("nearest foe wins when nobody has neighbours", BotPlan.pickFoe(0, 0, foes, 40) == 1, "");
        check("a foe beyond sight is ignored", BotPlan.pickFoe(0, 0, List.of(new Foe(50, 0, 0)), 40) == -1, "");
        check("no foes gives -1", BotPlan.pickFoe(0, 0, List.of(), 40) == -1, "");
        check("a crowd 3 further away beats a lone nearer foe (3 neighbours = 4.5 off)", BotPlan.pickFoe(0, 0, List.of(new Foe(5, 0, 0), new Foe(8, 0, 3)), 40) == 1, "");
        check("a crowd 6 further away does NOT beat the lone foe (4.5 off is not enough)", BotPlan.pickFoe(0, 0, List.of(new Foe(5, 0, 0), new Foe(11, 0, 3)), 40) == 0, "");
        check("sight limit is inclusive at the edge", BotPlan.pickFoe(0, 0, List.of(new Foe(40, 0, 0)), 40) == 0 && BotPlan.pickFoe(0, 0, List.of(new Foe(40.01, 0, 0)), 40) == -1, "");
        Goal g = BotPlan.goalFor(0, 0, new Foe(10, 0, 0), 4);
        check("goal is 4 blocks from the foe on the bot's side", near(g.x(), 6) && near(g.z(), 0) && g.hasFoe(), g.x() + "," + g.z());
        Goal diag = BotPlan.goalFor(0, 0, new Foe(6, 8, 0), 5);
        check("diagonal goal is exactly the stand-off from the foe", near(Math.hypot(diag.x() - 6, diag.z() - 8), 5), "");
        Goal stay = BotPlan.goalFor(2, 0, new Foe(5, 0, 0), 4);
        check("already inside the stand-off: stays put", near(stay.x(), 2) && near(stay.z(), 0), "");
        Goal exact = BotPlan.goalFor(1, 0, new Foe(5, 0, 0), 4);
        check("exactly at the stand-off: stays put", near(exact.x(), 1), "");
        Goal onFoe = BotPlan.goalFor(5, 0, new Foe(5, 0, 0), 4);
        check("standing exactly on the foe does not divide by zero", near(onFoe.x(), 5) && near(onFoe.z(), 0), "");
        Goal w = BotPlan.wander(50, 0, 0, 0, 6);
        check("no foe: walk to the centre", near(w.x(), 0) && !w.hasFoe(), "");
        Goal held = BotPlan.wander(3, 0, 0, 0, 6);
        check("within 6 of the centre: hold", near(held.x(), 3) && !held.hasFoe(), "");
        check("replan when the interval is up", BotPlan.needsReplan(20, 20, 0, 3), "");
        check("replan when the goal moved past the slack", BotPlan.needsReplan(2, 20, 3.1, 3), "");
        check("no replan while fresh and the goal is steady", !BotPlan.needsReplan(2, 20, 3.0, 3), "");
        System.out.println(fails == 0 ? "ALL PASS" : "FAILED " + fails);
    }
}
