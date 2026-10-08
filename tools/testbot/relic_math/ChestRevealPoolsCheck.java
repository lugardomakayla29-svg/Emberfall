import com.solme.emberfall.relic.*;
import java.util.*;

/**
 * ChestRevealPoolsCheck: the lists a reveal screen scrolls through must contain EVERY real tier label and EVERY real relic name, because
 * ChestReveal.build throws when the answer is not in its pool and that would crash a client screen. Pure, no game jar. Run from tools/testbot/relic_math.
 * The relic list is read from RelicPool.all(), so the 3 gated relics are in it (a search for add(...) alone sees 21 of 24).
 */
public class ChestRevealPoolsCheck {
    static int fails = 0, total = 0;
    static void check(String name, boolean ok, String extra) { total++; System.out.println((ok ? "PASS " : "FAIL ") + name + (extra.isEmpty() ? "" : "  " + extra)); if (!ok) fails++; }

    public static void main(String[] a) {
        List<String> tiers = ChestRevealPools.tiers(), items = ChestRevealPools.items();
        List<Relic> all = RelicPool.all();

        // P: the lists are the whole catalogue.
        check("P1 one tier label per rarity (4)", tiers.size() == RelicRarity.values().length && tiers.size() == 4, "n=" + tiers.size());
        check("P2 one item per relic in the catalogue", items.size() == all.size(), items.size() + " vs " + all.size());
        check("P3 the catalogue still has 24 relics (the pool check says so too)", all.size() == 24, "n=" + all.size());
        check("P4 no duplicate tier label and no duplicate item name (a duplicate would hide a missing one)", new HashSet<>(tiers).size() == tiers.size() && new HashSet<>(items).size() == items.size(), "");
        check("P5 no tier label or item name is null or empty", tiers.stream().allMatch(s -> s != null && !s.isEmpty()) && items.stream().allMatch(s -> s != null && !s.isEmpty()), "");

        // E: every REAL answer the server can send is in the lists.
        StringBuilder missT = new StringBuilder(), missI = new StringBuilder();
        for (RelicRarity r : RelicRarity.values()) if (!tiers.contains(r.label())) missT.append(r.label()).append(' ');
        for (Relic r : all) if (!items.contains(r.name())) missI.append(r.id()).append(' ');
        check("E1 EVERY rarity label is in the tier list", missT.length() == 0, "missing: " + missT);
        check("E2 EVERY relic name is in the item list", missI.length() == 0, "missing: " + missI);

        // G: the gated relics specifically (the ones a text search misses).
        List<Relic> gated = new ArrayList<>(); for (Relic r : all) if (r.isGated()) gated.add(r);
        check("G1 there ARE gated relics in the catalogue (otherwise this group tests nothing)", gated.size() >= 1, "n=" + gated.size());
        boolean gatedIn = true; StringBuilder gm = new StringBuilder();
        for (Relic r : gated) if (!items.contains(r.name())) { gatedIn = false; gm.append(r.id()).append(' '); }
        check("G2 every gated relic name is in the item list", gatedIn, "missing: " + gm);

        // B: the real property. For EVERY relic, build() with the real lists must not throw, and must carry the true answer.
        int built = 0, threw = 0; StringBuilder bad = new StringBuilder();
        for (Relic r : all) {
            for (long seed : new long[]{0L, 1L, -1L, 123456789L, Long.MAX_VALUE, Long.MIN_VALUE}) {
                try {
                    ChestReveal.Reveal rv = ChestReveal.build(r.rarity().label(), r.name(), tiers, items, seed);
                    if (rv.trueTier().equals(r.rarity().label()) && rv.trueItem().equals(r.name())) built++; else { threw++; bad.append(r.id()).append("(wrong answer) "); }
                } catch (RuntimeException e) { threw++; bad.append(r.id()).append(':').append(e.getClass().getSimpleName()).append(' '); }
            }
        }
        check("B1 build() with the real lists never throws, for all " + all.size() + " relics x 6 seeds, and keeps the true answer", threw == 0 && built == all.size() * 6, "built=" + built + " threw=" + threw + " " + bad);

        // C: the OLD way finds fewer, so this check would have caught it. This is the evidence for the gated relics, computed here, not asserted from memory.
        int named = 0; for (Relic r : all) if (!r.isGated()) named++;
        check("C1 the add(...)-only count (non-gated) is SMALLER than the catalogue, so an add-only list would be short", named < all.size(), "non-gated=" + named + " all=" + all.size());
        List<String> addOnly = new ArrayList<>(); for (Relic r : all) if (!r.isGated()) addOnly.add(r.name());
        boolean threwOnGated = false;
        for (Relic r : gated) { try { ChestReveal.build(r.rarity().label(), r.name(), tiers, addOnly, 1L); } catch (IllegalArgumentException e) { threwOnGated = true; } }
        check("C2 an item list built from non-gated relics ONLY makes build() throw for a gated relic (the crash this class prevents)", threwOnGated, "");

        // S: stable and independent copies.
        check("S1 two calls give equal lists in the same order", tiers.equals(ChestRevealPools.tiers()) && items.equals(ChestRevealPools.items()), "");
        List<String> t2 = ChestRevealPools.tiers(); t2.clear(); List<String> i2 = ChestRevealPools.items(); i2.clear();
        check("S2 each call returns a fresh copy (clearing one does not change the next)", ChestRevealPools.tiers().size() == 4 && ChestRevealPools.items().size() == all.size(), "");
        // The contract says catalogue order. S1 only compares two calls with each other, so a stable re-sort would pass it; compare with the catalogue itself.
        boolean inOrder = items.size() == all.size();
        for (int i = 0; inOrder && i < all.size(); i++) if (!items.get(i).equals(all.get(i).name())) inOrder = false;
        check("S4 the items are in catalogue order (the order of RelicPool.all())", inOrder, "");
        // Hold two results at once: a shared list would make them the same object, so clearing one would empty the other.
        List<String> hold1 = ChestRevealPools.items(), hold2 = ChestRevealPools.items();
        hold1.clear();
        check("S5 two results held at the same time are independent lists (clearing one leaves the other whole)", hold2.size() == all.size() && hold1 != hold2, "");
        List<String> th1 = ChestRevealPools.tiers(), th2 = ChestRevealPools.tiers(); th1.clear();
        check("S6 the same holds for the tier lists", th2.size() == 4 && th1 != th2, "");
        check("S3 the tier order is the rarity order (Common first, Legendary last)", tiers.get(0).equals(RelicRarity.values()[0].label()) && tiers.get(tiers.size() - 1).equals(RelicRarity.values()[RelicRarity.values().length - 1].label()), tiers.toString());

        System.out.println(fails == 0 ? "ALL PASS (" + total + " checks)" : "FAILED " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }
}
