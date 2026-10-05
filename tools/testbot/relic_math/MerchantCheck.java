import com.solme.emberfall.relic.*;
import com.solme.emberfall.relic.MerchantOffer.*;
import java.util.*;
public class MerchantCheck {
    static int fails = 0;
    static void check(String l, boolean ok, String e) { System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e); if (!ok) fails++; }
    public static void main(String[] a) {
        // ---- prices
        for (RelicRarity r : RelicRarity.values()) check("price never below the chest price (" + r + ", chest 30 and 1)", MerchantOffer.price(r, 30) >= 30 && MerchantOffer.price(r, 1) >= 1, MerchantOffer.price(r, 30) + "/" + MerchantOffer.price(r, 1));
        check("prices at chest 30: 30 / 45 / 66 / 105", MerchantOffer.price(RelicRarity.COMMON, 30) == 30 && MerchantOffer.price(RelicRarity.UNCOMMON, 30) == 45 && MerchantOffer.price(RelicRarity.RARE, 30) == 66 && MerchantOffer.price(RelicRarity.LEGENDARY, 30) == 105,
                MerchantOffer.price(RelicRarity.COMMON, 30) + "/" + MerchantOffer.price(RelicRarity.UNCOMMON, 30) + "/" + MerchantOffer.price(RelicRarity.RARE, 30) + "/" + MerchantOffer.price(RelicRarity.LEGENDARY, 30));
        boolean rising = true; for (int c : new int[]{30, 38, 143, 500}) for (int i = 0; i < 3; i++) rising &= MerchantOffer.price(RelicRarity.values()[i], c) < MerchantOffer.price(RelicRarity.values()[i + 1], c);
        check("a higher rarity always costs strictly more at every chest price", rising, "");
        // ---- stalls, 20000 per tier, fresh buyer (all ungated unlocked none)
        Random rnd = new Random(11); int N = 20000;
        for (RelicRarity tier : RelicRarity.values()) {
            int short3 = 0, dupes = 0, slips = 0, slots = 0, wrongAbove = 0, badPrice = 0;
            for (int i = 0; i < N; i++) {
                Stall s = MerchantOffer.stock(tier, 30, Map.of(), Set.of(), rnd::nextDouble);
                if (s.items().size() != 3) short3++;
                Set<String> ids = new HashSet<>();
                for (Item it : s.items()) {
                    if (!ids.add(it.relic().id())) dupes++;
                    slots++;
                    if (it.relic().rarity().ordinal() < tier.ordinal()) slips++;
                    if (it.relic().rarity().ordinal() > tier.ordinal()) wrongAbove++;
                    if (it.price() != MerchantOffer.price(it.relic().rarity(), 30)) badPrice++;
                    if (it.relic().isGated()) wrongAbove += 1000; // a locked relic must never be offered to a buyer with nothing unlocked
                }
            }
            double slipRate = slips / (double) slots;
            boolean slipOk = tier == RelicRarity.COMMON ? slips == 0 : Math.abs(slipRate - MerchantOffer.SLIP_CHANCE) < 0.02;
            check(tier + ": always 3 items, never a duplicate, never above the tier, never a locked relic, price matches the rule", short3 == 0 && dupes == 0 && wrongAbove == 0 && badPrice == 0, "short=" + short3 + " dupes=" + dupes + " above/locked=" + wrongAbove + " badPrice=" + badPrice);
            check(tier + ": slip rate " + (tier == RelicRarity.COMMON ? "is 0 (nothing lower)" : "near 25%"), slipOk, String.format("%.3f", slipRate));
        }
        // ---- the buyer's state is respected
        Map<String, Integer> maxed = new HashMap<>(); for (Relic r : RelicPool.ofRarity(RelicRarity.LEGENDARY)) if (!r.id().equals("hourglass")) maxed.put(r.id(), r.maxStacks());
        int bad = 0, got = 0; for (int i = 0; i < 2000; i++) { Stall s = MerchantOffer.stock(RelicRarity.LEGENDARY, 30, maxed, Set.of(), rnd::nextDouble); for (Item it : s.items()) { got++; if (maxed.containsKey(it.relic().id())) bad++; } }
        check("a Legendary merchant never offers a legendary the buyer already maxed", bad == 0 && got > 0, "bad=" + bad + " of " + got);
        Set<String> all = new HashSet<>(); for (Relic r : RelicPool.all()) if (r.isGated()) all.add(r.unlockId());
        int gatedSeen = 0; for (int i = 0; i < 4000; i++) for (Item it : MerchantOffer.stock(RelicRarity.COMMON, 30, Map.of(), all, rnd::nextDouble).items()) if (it.relic().isGated()) gatedSeen++;
        check("control: once the goals are unlocked the gated relics DO show up", gatedSeen > 0, "gated offers " + gatedSeen);
        // ---- a dry pool gives fewer, never crashes, never duplicates
        Map<String, Integer> everything = new HashMap<>(); for (Relic r : RelicPool.all()) everything.put(r.id(), r.maxStacks());
        Stall dry = MerchantOffer.stock(RelicRarity.RARE, 30, everything, all, rnd::nextDouble);
        check("a buyer who owns everything gets an empty stall, no crash", dry.items().isEmpty(), "items " + dry.items().size());
        Map<String, Integer> nearly = new HashMap<>(everything); nearly.remove("clover"); nearly.remove("oat_loaf");
        Stall two = MerchantOffer.stock(RelicRarity.COMMON, 30, nearly, all, rnd::nextDouble);
        Set<String> ids = new HashSet<>(); for (Item it : two.items()) ids.add(it.relic().id());
        check("only two relics left: exactly those two, no duplicate", two.items().size() == 2 && ids.equals(Set.of("clover", "oat_loaf")), ids.toString());
        // ---- determinism: same inputs and same rolls give the same stall
        Stall x = MerchantOffer.stock(RelicRarity.RARE, 57, Map.of(), Set.of(), new Random(99)::nextDouble), y = MerchantOffer.stock(RelicRarity.RARE, 57, Map.of(), Set.of(), new Random(99)::nextDouble);
        check("same rolls, same stall", x.items().stream().map(i -> i.relic().id() + ":" + i.price()).toList().equals(y.items().stream().map(i -> i.relic().id() + ":" + i.price()).toList()), x.items().stream().map(i -> i.relic().id()).toList().toString());
        System.out.println(fails == 0 ? "ALL PASS" : "FAILED " + fails);
    }
}
