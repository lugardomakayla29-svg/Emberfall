import com.solme.emberfall.relic.*;
import java.util.*;

public class StatsCheck {
    static int fails = 0;
    static void check(String label, boolean ok, String extra) { System.out.println((ok ? "PASS " : "FAIL ") + label + "  " + extra); if (!ok) fails++; }
    static RelicStats with(Object... kv) { Map<String,Integer> m = new HashMap<>(); for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Integer) kv[i+1]); return new RelicStats(m); }
    static boolean eq(double a, double b) { return Math.abs(a - b) < 1e-9; }
    public static void main(String[] x) {
        RelicStats none = new RelicStats(Map.of());
        check("no relics = neutral numbers", none.luck()==0 && eq(none.goldMultiplier(),1) && eq(none.xpMultiplier(),1) && none.keyChance()==0
                && eq(none.pickupRangeMultiplier(),1) && none.bonusMaxHealth()==0 && none.speedMultiplierDelta()==0 && none.dodgeChance()==0
                && none.thornDamage()==0 && none.extraStrikes()==0 && eq(none.cooldownMultiplier(),1) && none.lifestealShare()==0
                && none.bonkChance()==0 && none.censerChance()==0 && none.frostChance()==0 && eq(none.damageMultiplier(0),1)
                && !none.hasTotem() && !none.hasMirror() && !none.hasHourglass() && !none.capsChestPrice() && none.threatBonus()==0, "");
        // one check per relic: the relic changes at least one number, and only its own
        check("clover +8 luck/stack", eq(with("clover",3).luck(), 24), "");
        check("gold_nugget +15%/stack", eq(with("gold_nugget",2).goldMultiplier(), 1.30), "");
        check("clockwork_charm +10%/stack", eq(with("clockwork_charm",4).xpMultiplier(), 1.40), "");
        check("ember_key 10%/stack, cap 50%", eq(with("ember_key",2).keyChance(), 0.2) && eq(with("ember_key",5).keyChance(), 0.5), "");
        check("ember_ledger caps chest price", with("ember_ledger",1).capsChestPrice(), "");
        check("magnet_stone +25%/stack", eq(with("magnet_stone",2).pickupRangeMultiplier(), 1.5), "");
        check("oat_loaf +4 hp/stack", eq(with("oat_loaf",3).bonusMaxHealth(), 12), "");
        check("dragons_heart +40 hp and regen", eq(with("dragons_heart",1).bonusMaxHealth(), 40) && eq(with("dragons_heart",1).regenPerSecond(), 0.5), "");
        check("iron_boots +8% speed/stack", eq(with("iron_boots",5).speedMultiplierDelta(), 0.40), "");
        check("pearl_shard 8%/stack, capped 40%", eq(with("pearl_shard",2).dodgeChance(), 0.16) && eq(with("pearl_shard",5).dodgeChance(), 0.40), "");
        check("thorn_vest 2 dmg/stack", eq(with("thorn_vest",3).thornDamage(), 6), "");
        check("campfire_core 0.5 hp/s/stack still", eq(with("campfire_core",4).stillRegenPerSecond(), 2.0), "");
        check("totem/mirror/hourglass flags", with("totem_of_returning",1).hasTotem() && with("mirror_shard",1).hasMirror() && with("hourglass",1).hasHourglass(), "");
        check("quiver_of_plenty +1 strike/stack", with("quiver_of_plenty",3).extraStrikes()==3, "");
        check("wizards_cowl -10%/stack, floor 0.5", eq(with("wizards_cowl",2).cooldownMultiplier(), 0.8) && eq(with("wizards_cowl",3).cooldownMultiplier(), 0.7), "");
        check("blood_chalice 3%/stack", eq(with("blood_chalice",3).lifestealShare(), 0.09), "");
        check("big_bonk 2%/stack, x20", eq(with("big_bonk",2).bonkChance(), 0.04) && RelicStats.BONK_MULTIPLIER == 20.0, "");
        check("spiked_censer 10%/stack", eq(with("spiked_censer",2).censerChance(), 0.2), "");
        check("frostbound_ring 10%/stack", eq(with("frostbound_ring",3).frostChance(), 0.3), "");
        check("anvil_of_dawn +25% dmg", eq(with("anvil_of_dawn",1).damageMultiplier(0), 1.25), "");
        check("wither_crown +50% dmg and +4 threat", eq(with("wither_crown",1).damageMultiplier(0), 1.5) && eq(with("wither_crown",1).threatBonus(), 4), "");
        check("soul_lantern grows per kill, capped", eq(with("soul_lantern",1).damageMultiplier(100), 1.2) && eq(with("soul_lantern",1).damageMultiplier(100000), 2.0), "");
        check("soul_lantern needs the relic", eq(none.damageMultiplier(100000), 1.0), "");
        check("damage multipliers stack multiplicatively", eq(with("anvil_of_dawn",1,"wither_crown",1).damageMultiplier(0), 1.25*1.5), "");
        // bonk roll
        check("bonk never procs without the relic", none.bonkMultiplier(() -> 0.0) == 1.0, "");
        Random r = new Random(3); int hit = 0, N = 400000; RelicStats b = with("big_bonk",1);
        for (int i = 0; i < N; i++) if (b.bonkMultiplier(r::nextDouble) == 20.0) hit++;
        check("bonk proc rate is 2% (sampled)", Math.abs(100.0*hit/N - 2.0) < 0.1, String.format("%.3f%%", 100.0*hit/N));
        // every relic in the pool at max stacks: nothing is NaN, negative, or absurd
        boolean sane = true; StringBuilder worst = new StringBuilder();
        Map<String,Integer> maxed = new HashMap<>(); for (Relic rel : RelicPool.all()) maxed.put(rel.id(), rel.maxStacks());
        RelicStats m = new RelicStats(maxed);
        double[] vals = { m.luck(), m.goldMultiplier(), m.xpMultiplier(), m.keyChance(), m.pickupRangeMultiplier(), m.bonusMaxHealth(), m.speedMultiplierDelta(),
                m.dodgeChance(), m.thornDamage(), m.stillRegenPerSecond(), m.regenPerSecond(), m.cooldownMultiplier(), m.lifestealShare(), m.bonkChance(),
                m.censerChance(), m.frostChance(), m.damageMultiplier(100000) };
        for (double v : vals) if (Double.isNaN(v) || Double.isInfinite(v) || v < 0) sane = false;
        check("everything maxed stays finite and non-negative", sane, "");
        check("maxed caps hold (dodge<=0.40, key<=0.50, cooldown>=0.5)", m.dodgeChance() <= 0.40 && m.keyChance() <= 0.50 && m.cooldownMultiplier() >= 0.5, "");
        System.out.printf("maxed: luck %.0f gold x%.2f xp x%.2f hp +%.0f speed +%.0f%% dmg x%.2f lifesteal %.0f%% pickup x%.2f%n",
                m.luck(), m.goldMultiplier(), m.xpMultiplier(), m.bonusMaxHealth(), 100*m.speedMultiplierDelta(), m.damageMultiplier(100000), 100*m.lifestealShare(), m.pickupRangeMultiplier());
        // NO FILLER: each pool relic must change at least one number when held alone
        List<String> dead = new ArrayList<>();
        for (Relic rel : RelicPool.all()) {
            RelicStats one = with(rel.id(), 1);
            double[] a = { one.luck(), one.goldMultiplier(), one.xpMultiplier(), one.keyChance(), one.pickupRangeMultiplier(), one.bonusMaxHealth(), one.speedMultiplierDelta(),
                    one.dodgeChance(), one.thornDamage(), one.stillRegenPerSecond(), one.regenPerSecond(), one.cooldownMultiplier(), one.lifestealShare(), one.bonkChance(),
                    one.censerChance(), one.frostChance(), one.damageMultiplier(100), one.extraStrikes() };
            double[] base = { 0, 1, 1, 0, 1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0 };
            boolean changed = false; for (int i = 0; i < a.length; i++) if (!eq(a[i], base[i])) changed = true;
            if (one.hasTotem() || one.hasMirror() || one.hasHourglass() || one.capsChestPrice()) changed = true;
            if (!changed) dead.add(rel.id());
        }
        check("no filler: every relic changes a number or flag", dead.isEmpty(), "dead=" + dead);
        System.out.println(fails == 0 ? "RESULT: ALL PASS" : "RESULT: " + fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
