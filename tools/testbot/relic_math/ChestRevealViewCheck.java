import com.solme.emberfall.relic.ChestReveal;
import com.solme.emberfall.relic.ChestRevealView;
import com.solme.emberfall.relic.Relic;
import com.solme.emberfall.relic.RelicPool;
import com.solme.emberfall.relic.RelicRarity;
import java.util.HashSet;
import java.util.List;

/**
 * Pure checks for the pools the chest reveal screen spins through. The failure that matters: ChestReveal.build THROWS when the answer is not in a pool,
 * and on a client that would be an exception inside a packet handler. These prove (1) every REAL tier and relic can be built, so a genuine answer never
 * throws; (2) an answer this client does not know returns null instead of throwing; (3) the colours match the real tiers. They do NOT prove a screen opens
 * or how it looks (no graphical client). Expected values are written by hand, not read back from the class under test.
 */
public class ChestRevealViewCheck {
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
        try {
            run();
        } catch (RuntimeException e) {
            check("run: no check threw an exception", false, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        System.out.println(fails == 0 ? "ALL PASS (" + total + ")" : "FAILED " + fails + " of " + total);
        if (fails != 0) {
            System.exit(1);
        }
    }

    static void run() {
        List<String> tiers = com.solme.emberfall.relic.ChestRevealPools.tiers();
        List<String> items = com.solme.emberfall.relic.ChestRevealPools.items();
        check("four tiers, in tier order", tiers.equals(List.of("Common", "Uncommon", "Rare", "Legendary")), tiers.toString());
        check("one item per relic in the pool", items.size() == RelicPool.all().size() && items.size() > 0, "items=" + items.size());
        check("no two relics share a name (a duplicate would hide a decoy)", new HashSet<>(items).size() == items.size(), "distinct=" + new HashSet<>(items).size());
        check("the pool has no blank name", items.stream().noneMatch(s -> s == null || s.isBlank()), "");

        // (1) EVERY real answer builds. The exact failure that would crash a client.
        int built = 0;
        int sameSpin = 0;
        for (Relic r : RelicPool.all()) {
            ChestReveal.Reveal v = ChestRevealView.safeBuild(r.rarity().label(), r.name(), 12345L);
            if (v != null) {
                built++;
                if (v.trueItem().equals(r.name()) && v.trueTier().equals(r.rarity().label())) {
                    sameSpin++;
                }
            }
        }
        check("EVERY real relic builds a reveal (none returns null)", built == RelicPool.all().size(), built + " of " + RelicPool.all().size());
        check("every built reveal lands on the real tier and the real name", sameSpin == RelicPool.all().size(), sameSpin + " of " + RelicPool.all().size());
        check("no decoy list ever contains the answer, any relic", noDecoyHoldsAnswer(), "");

        // (2) A name this client does not know must not throw.
        check("unknown item gives null, not an exception", ChestRevealView.safeBuild("Common", "Relic From The Future", 1L) == null, "");
        check("unknown tier gives null, not an exception", ChestRevealView.safeBuild("Mythic", items.get(0), 1L) == null, "");
        check("null tier gives null, not an exception", ChestRevealView.safeBuild(null, items.get(0), 1L) == null, "");
        check("null item gives null, not an exception", ChestRevealView.safeBuild("Common", null, 1L) == null, "");
        check("empty strings give null, not an exception", ChestRevealView.safeBuild("", "", 1L) == null, "");
        check("a tier label in the item slot gives null (swapped fields)", ChestRevealView.safeBuild(items.get(0), "Common", 1L) == null, "");

        // (3) Colours. Hand-written from RelicRarity's design values.
        check("Common is grey 9D9D9D", ChestRevealView.tierRgb("Common", -1) == 0x9D9D9D, "");
        check("Uncommon is blue 4FA8FF", ChestRevealView.tierRgb("Uncommon", -1) == 0x4FA8FF, "");
        check("Rare is purple B266FF", ChestRevealView.tierRgb("Rare", -1) == 0xB266FF, "");
        check("Legendary is gold FFC247", ChestRevealView.tierRgb("Legendary", -1) == 0xFFC247, "");
        check("an unknown tier label uses the fallback", ChestRevealView.tierRgb("Mythic", 0x123456) == 0x123456, "");
        boolean itemColours = true;
        for (Relic r : RelicPool.all()) {
            itemColours &= ChestRevealView.itemRgb(r.name(), -1) == r.rarity().rgb();
        }
        check("each relic name maps to its own tier colour", itemColours, "");
        check("an unknown relic name uses the fallback", ChestRevealView.itemRgb("Relic From The Future", 0xABCDEF) == 0xABCDEF, "");

        // Determinism: the same answer and seed spin the same way on every client.
        ChestReveal.Reveal x = ChestRevealView.safeBuild("Rare", items.get(3), 99L);
        ChestReveal.Reveal y = ChestRevealView.safeBuild("Rare", items.get(3), 99L);
        check("same answer + same seed = same item decoys", x != null && y != null && x.itemDecoys().equals(y.itemDecoys()), "");
        // The seed must matter: across 20 seeds the item decoy ORDER takes more than one shape. (Same seed above shows determinism; this shows it is not ignored.)
        java.util.Set<List<String>> orders = new HashSet<>();
        for (long seed = 1; seed <= 20; seed++) {
            ChestReveal.Reveal v = ChestRevealView.safeBuild("Rare", items.get(3), seed);
            orders.add(v.itemDecoys());
        }
        check("different seeds give different decoy orders (the seed is not ignored)", orders.size() > 10, "distinct orders over 20 seeds=" + orders.size());
        check("the pool counts are the real ones: 24 relics, 6 in each tier", items.size() == 24 && perTierIsSix(), "items=" + items.size());
        check("the pools are fresh lists (a caller cannot corrupt the next build)", mutateAndRebuild(), "");
    }

    static boolean perTierIsSix() {
        for (RelicRarity r : RelicRarity.values()) {
            int n = 0;
            for (Relic x : RelicPool.all()) {
                if (x.rarity() == r) {
                    n++;
                }
            }
            if (n != 6) {
                return false;
            }
        }
        return true;
    }

    static boolean noDecoyHoldsAnswer() {
        for (Relic r : RelicPool.all()) {
            ChestReveal.Reveal v = ChestRevealView.safeBuild(r.rarity().label(), r.name(), 7L);
            if (v == null || v.itemDecoys().contains(r.name()) || v.tierDecoys().contains(r.rarity().label())) {
                return false;
            }
        }
        return true;
    }

    static boolean mutateAndRebuild() {
        List<String> t = com.solme.emberfall.relic.ChestRevealPools.tiers();
        t.clear();
        List<String> i = com.solme.emberfall.relic.ChestRevealPools.items();
        i.clear();
        return com.solme.emberfall.relic.ChestRevealPools.tiers().size() == RelicRarity.values().length && com.solme.emberfall.relic.ChestRevealPools.items().size() == RelicPool.all().size();
    }
}
