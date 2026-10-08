import com.solme.emberfall.relic.ChestReveal;
import com.solme.emberfall.relic.ChestReveal.Frame;
import com.solme.emberfall.relic.ChestReveal.Reveal;
import com.solme.emberfall.relic.ChestReveal.State;
import com.solme.emberfall.relic.RelicRarity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure checks for the chest slot-machine reveal sequence (GAME_PLAN row 2.6). They prove the SEQUENCE: tier first then item, both reels land on the
 * TRUE answer and never a decoy, the item reel is hidden until the tier has locked, nothing is shown that is not in its pool, the answer is not shown
 * early, and the whole thing is finite and deterministic. They do NOT prove a screen opens, draws, or feels good: no Screen class exists in this PR,
 * and nothing here was seen. The packet that carries the answer is the server wiring and is not here.
 */
public class ChestRevealCheck {
    static int fails = 0;
    static int total = 0;

    static void check(String l, boolean ok, String e) {
        total++;
        System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e);
        if (!ok) {
            fails++;
        }
    }

    static final List<String> TIERS = tierLabels();
    static final List<String> ITEMS = Arrays.asList("Iron Fang", "Ember Ring", "Moth Cloak", "Glass Bell", "Rusty Key", "Hollow Coin", "Wasp Idol", "Salt Charm");

    static List<String> tierLabels() {
        List<String> l = new ArrayList<>();
        for (RelicRarity r : RelicRarity.values()) {
            l.add(r.label());
        }
        return l;
    }

    public static void main(String[] a) {
        try {
            run();
        } catch (RuntimeException e) {
            // A check that THROWS is a failed check, named, not a silent crash of the whole run.
            check("run: no check threw an exception", false, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        System.out.println(fails == 0 ? "ALL PASS (" + total + " checks)" : "FAILED " + fails + " of " + total);
        if (fails != 0) {
            System.exit(1);
        }
    }

    static void run() {
        // ---- the timing constants are the design's, written out so a drift fails by name ---------------------------------------------
        check("timing: tier stops at 40 (2 s), a 10 tick gap, item spins 60 ticks so stops at 110, holds 30, total 140",
                ChestReveal.TIER_STOP_TICK == 40 && ChestReveal.GAP_TICKS == 10 && ChestReveal.ITEM_SPIN_TICKS == 60 && ChestReveal.ITEM_STOP_TICK == 110 && ChestReveal.HOLD_TICKS == 30 && ChestReveal.TOTAL_TICKS == 140, "");
        check("tiers: there are four tiers and the reel pool is exactly the real RelicRarity labels", TIERS.size() == 4 && TIERS.equals(Arrays.asList("Common", "Uncommon", "Rare", "Legendary")), TIERS.toString());

        // ---- THE rule: every tier x every item lands on the truth -----------------------------------------------------------------------
        int wrongTier = 0, wrongItem = 0, combos = 0;
        for (String tier : TIERS) {
            for (String item : ITEMS) {
                for (long seed = 0; seed < 5; seed++) {
                    combos++;
                    Reveal v = ChestReveal.build(tier, item, TIERS, ITEMS, seed);
                    Frame end = v.frame(ChestReveal.TOTAL_TICKS);
                    if (end.tier().state() != State.STOPPED || !tier.equals(end.tier().symbol())) {
                        wrongTier++;
                    }
                    if (end.item().state() != State.STOPPED || !item.equals(end.item().symbol())) {
                        wrongItem++;
                    }
                }
            }
        }
        check("answer: the tier reel ends STOPPED on the true tier for every tier, item and seed", wrongTier == 0, combos + " combos, wrong " + wrongTier);
        check("answer: the item reel ends STOPPED on the true item for every tier, item and seed", wrongItem == 0, combos + " combos, wrong " + wrongItem);

        // ---- TIER FIRST, THEN ITEM -------------------------------------------------------------------------------------------------------
        Reveal v = ChestReveal.build("Rare", "Ember Ring", TIERS, ITEMS, 7);
        check("order: the item reel is HIDDEN at every tick up to the tier stop and for the whole gap (0 to 49)", hiddenUntil(v, 49), "");
        check("order: the item reel is first visible at tick 50 (tier stop 40 + gap 10), not a tick earlier", v.frame(49).item().state() == State.HIDDEN && v.frame(50).item().state() == State.SPINNING, "");
        check("order: the tier reel is STOPPED before the item reel starts (at 40 stopped, item not yet shown)", v.frame(40).tier().state() == State.STOPPED && v.frame(40).item().state() == State.HIDDEN, "");
        check("order: the tier stops at exactly tick 40 (spinning at 39, stopped at 40)", v.frame(39).tier().state() == State.SPINNING && v.frame(40).tier().state() == State.STOPPED, "");
        check("order: the item stops at exactly tick 110 (spinning at 109, stopped at 110)", v.frame(109).item().state() == State.SPINNING && v.frame(110).item().state() == State.STOPPED, "");
        check("order: at no tick is the item STOPPED while the tier is still spinning", orderNeverBroken(), "");
        check("order: the tier reel never stops after the item reel has started", firstTick(v, true, State.STOPPED) < firstTick(v, false, State.SPINNING), "");
        check("order: both reels are visible from tick 0 only for the tier; the item has no symbol while hidden", v.frame(0).tier().state() == State.SPINNING && v.frame(0).item().symbol() == null, "");

        // ---- NO SPOILER: the truth never appears before its stop --------------------------------------------------------------------------
        int spoiledTier = 0, spoiledItem = 0;
        for (String tier : TIERS) {
            for (String item : ITEMS) {
                for (long seed = 0; seed < 4; seed++) {
                    Reveal r = ChestReveal.build(tier, item, TIERS, ITEMS, seed);
                    for (int t = 0; t < ChestReveal.TIER_STOP_TICK; t++) {
                        if (tier.equals(r.frame(t).tier().symbol())) {
                            spoiledTier++;
                        }
                    }
                    for (int t = 0; t < ChestReveal.ITEM_STOP_TICK; t++) {
                        if (item.equals(r.frame(t).item().symbol())) {
                            spoiledItem++;
                        }
                    }
                }
            }
        }
        check("spoiler: the true tier is never shown before the tier reel stops", spoiledTier == 0, "spoiled " + spoiledTier);
        check("spoiler: the true item is never shown before the item reel stops", spoiledItem == 0, "spoiled " + spoiledItem);
        check("spoiler: the decoy lists never contain the answer", noAnswerInDecoys(), "");

        // ---- every symbol shown is from its pool -------------------------------------------------------------------------------------------
        int outside = 0;
        for (String tier : TIERS) {
            for (String item : ITEMS) {
                Reveal r = ChestReveal.build(tier, item, TIERS, ITEMS, 3);
                for (int t = 0; t <= ChestReveal.TOTAL_TICKS; t++) {
                    Frame f = r.frame(t);
                    if (f.tier().symbol() != null && !TIERS.contains(f.tier().symbol())) {
                        outside++;
                    }
                    if (f.item().symbol() != null && !ITEMS.contains(f.item().symbol())) {
                        outside++;
                    }
                }
            }
        }
        check("pool: no frame ever shows a symbol that is not in its own reel's pool", outside == 0, "outside " + outside);
        check("pool: a tier name is never shown on the item reel and an item name never on the tier reel", noCrossOver(), "");
        check("pool: while spinning, the tier reel shows every decoy tier in turn (nothing is skipped)", showsAllDecoys(), "");

        // ---- time bounds ---------------------------------------------------------------------------------------------------------------------
        check("bounds: ticks below zero act as tick 0 and ticks past the end act as the last tick", v.frame(-5).tier().state() == v.frame(0).tier().state() && v.frame(-5).tier().symbol().equals(v.frame(0).tier().symbol()) && v.frame(99999).item().symbol().equals("Ember Ring") && v.frame(Integer.MAX_VALUE).tier().symbol().equals("Rare") && v.frame(Integer.MIN_VALUE).item().state() == State.HIDDEN, "");
        check("bounds: the whole reveal is 140 ticks (7 s) and finishes STOPPED on both", ChestReveal.TOTAL_TICKS == 140 && v.frame(140).tier().state() == State.STOPPED && v.frame(140).item().state() == State.STOPPED, "");

        // ---- the slowdown ------------------------------------------------------------------------------------------------------------------
        check("slow: symbolAt never decreases over a 60 tick spin and never goes negative", monotone(60) && monotone(40) && monotone(1) && monotone(0), "");
        check("slow: the reel changes symbol every 2 ticks while fast and every 6 ticks near the end", ChestReveal.symbolAt(4, 60) - ChestReveal.symbolAt(2, 60) == 1 && ChestReveal.symbolAt(60, 60) - ChestReveal.symbolAt(54, 60) == 1 && ChestReveal.symbolAt(60, 60) - ChestReveal.symbolAt(55, 60) <= 1, "");
        check("slow: symbolAt of zero or negative elapsed is 0 and does not crash", ChestReveal.symbolAt(0, 60) == 0 && ChestReveal.symbolAt(-9, 60) == 0, "");
        check("slow: the last stretch is slower than the first (fewer changes in the final 20 ticks than the first 20)", ChestReveal.symbolAt(60, 60) - ChestReveal.symbolAt(40, 60) < ChestReveal.symbolAt(20, 60) - ChestReveal.symbolAt(0, 60), "");

        // symbolAt hand-derived for a 60 tick spin: fast (one symbol per 2 ticks) up to tick 40, then one per 6 ticks.
        check("slow: symbolAt(40,60)=20 (the end of the fast phase) and symbolAt(41,60)=20, symbolAt(46,60)=21, symbolAt(52,60)=22, symbolAt(58,60)=23",
                ChestReveal.symbolAt(40, 60) == 20 && ChestReveal.symbolAt(41, 60) == 20 && ChestReveal.symbolAt(46, 60) == 21 && ChestReveal.symbolAt(52, 60) == 22 && ChestReveal.symbolAt(58, 60) == 23,
                ChestReveal.symbolAt(40, 60) + "," + ChestReveal.symbolAt(41, 60) + "," + ChestReveal.symbolAt(46, 60) + "," + ChestReveal.symbolAt(52, 60) + "," + ChestReveal.symbolAt(58, 60));
        check("slow: symbolAt(20,60)=10 and symbolAt(39,60)=19 (still fast just before tick 40)", ChestReveal.symbolAt(20, 60) == 10 && ChestReveal.symbolAt(39, 60) == 19, "");
        check("slow: for a 40 tick tier spin the fast phase ends at tick 26 (40*2/3): symbolAt(26,40)=13, symbolAt(32,40)=14, symbolAt(38,40)=15", ChestReveal.symbolAt(26, 40) == 13 && ChestReveal.symbolAt(32, 40) == 14 && ChestReveal.symbolAt(38, 40) == 15, "");
        check("refuse: a null item gives an IllegalArgumentException, not a NullPointerException", throwsNamed(() -> ChestReveal.build("Rare", null, TIERS, ITEMS, 1)), "");
        check("refuse: a null tier pool gives an IllegalArgumentException, not a NullPointerException", throwsNamed(() -> ChestReveal.build("Rare", "Ember Ring", null, ITEMS, 1)), "");
        check("pool: a long run over every tick of a 3 item reel never throws (the decoy index always wraps)", wrapsOk(), "");

        // ---- determinism and variety ----------------------------------------------------------------------------------------------------------
        check("determinism: the same answer, pools and seed give identical frames at every tick", sameEverywhere(5, 5), "");
        check("determinism: different seeds give a different decoy order (at least 6 of 10 seeds differ from seed 0)", variety(), "");
        check("determinism: building does not change the pools passed in", poolsUntouched(), "");

        // ---- refusing a wrong build, loudly ----------------------------------------------------------------------------------------------------------
        check("refuse: a tier not in the tier pool is refused, not quietly replaced", throwsOn(() -> ChestReveal.build("Mythic", "Ember Ring", TIERS, ITEMS, 1)), "");
        check("refuse: an item not in the item pool is refused", throwsOn(() -> ChestReveal.build("Rare", "Nope", TIERS, ITEMS, 1)), "");
        check("refuse: null arguments are refused", throwsOn(() -> ChestReveal.build(null, "Ember Ring", TIERS, ITEMS, 1)) && throwsOn(() -> ChestReveal.build("Rare", null, TIERS, ITEMS, 1)) && throwsOn(() -> ChestReveal.build("Rare", "Ember Ring", null, ITEMS, 1)) && throwsOn(() -> ChestReveal.build("Rare", "Ember Ring", TIERS, null, 1)), "");
        check("refuse: an empty pool is refused (it cannot contain the answer)", throwsOn(() -> ChestReveal.build("Rare", "Ember Ring", new ArrayList<>(), ITEMS, 1)) && throwsOn(() -> ChestReveal.build("Rare", "Ember Ring", TIERS, new ArrayList<>(), 1)), "");

        // ---- hand-made edge pools with known answers --------------------------------------------------------------------------------------------
        Reveal one = ChestReveal.build("Rare", "Ember Ring", Collections.singletonList("Rare"), Collections.singletonList("Ember Ring"), 1);
        check("edge: a pool of ONE has no decoys, spins on a blank and still lands on the truth", one.tierDecoys().isEmpty() && one.itemDecoys().isEmpty() && "".equals(one.frame(10).tier().symbol()) && "".equals(one.frame(60).item().symbol()) && "Rare".equals(one.frame(140).tier().symbol()) && "Ember Ring".equals(one.frame(140).item().symbol()), "");
        Reveal two = ChestReveal.build("Rare", "Ember Ring", Arrays.asList("Rare", "Common"), Arrays.asList("Ember Ring", "Iron Fang"), 1);
        check("edge: a pool of TWO flips between the one decoy and then the truth", allEqual(two, "Common", true) && allEqual(two, "Iron Fang", false), "");
        Reveal dup = ChestReveal.build("Rare", "Ember Ring", Arrays.asList("Rare", "Common", "Common", "Rare"), Arrays.asList("Ember Ring", "Iron Fang", "Iron Fang", "Ember Ring"), 1);
        check("edge: duplicate names in a pool are collapsed (one decoy each) and the truth is still never a decoy", dup.tierDecoys().equals(Collections.singletonList("Common")) && dup.itemDecoys().equals(Collections.singletonList("Iron Fang")), dup.tierDecoys() + " " + dup.itemDecoys());
        Reveal big = ChestReveal.build("Item 0", "Item 0", manyNames(500), manyNames(500), 9);
        check("edge: a pool of 500 never crashes and lands on the truth at the end", "Item 0".equals(big.frame(140).item().symbol()) && big.itemDecoys().size() == 499 && lastNotCrash(big), "");
        check("edge: the tier and item pools may be the same list without confusing the two reels", sameNameBothReels(), "");
    }

    // ---- helpers: each computes its expected answer from the rule, not from ChestReveal -------------------------------------------------------

    interface Thrower {
        void run();
    }

    static boolean throwsOn(Thrower t) {
        try {
            t.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    /** True only for an IllegalArgumentException: any other exception (a NullPointerException) is a failure of the check, not a pass. */
    static boolean throwsNamed(Thrower t) {
        try {
            t.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    static boolean wrapsOk() {
        List<String> three = Arrays.asList("X", "Y", "Z");
        try {
            for (String truth : three) {
                Reveal r = ChestReveal.build(truth, truth, three, three, 8);
                for (int t = -2; t <= ChestReveal.TOTAL_TICKS + 2; t++) {
                    r.frame(t);
                }
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    static boolean hiddenUntil(Reveal v, int last) {
        for (int t = 0; t <= last; t++) {
            if (v.frame(t).item().state() != State.HIDDEN || v.frame(t).item().symbol() != null) {
                return false;
            }
        }
        return true;
    }

    static boolean orderNeverBroken() {
        for (String tier : TIERS) {
            Reveal r = ChestReveal.build(tier, "Ember Ring", TIERS, ITEMS, 2);
            for (int t = 0; t <= ChestReveal.TOTAL_TICKS; t++) {
                Frame f = r.frame(t);
                if (f.item().state() != State.HIDDEN && f.tier().state() != State.STOPPED) {
                    return false;
                }
                if (f.item().state() == State.STOPPED && f.tier().state() != State.STOPPED) {
                    return false;
                }
            }
        }
        return true;
    }

    static int firstTick(Reveal v, boolean tierReel, State s) {
        for (int t = 0; t <= ChestReveal.TOTAL_TICKS; t++) {
            State got = tierReel ? v.frame(t).tier().state() : v.frame(t).item().state();
            if (got == s) {
                return t;
            }
        }
        return Integer.MAX_VALUE;
    }

    static boolean noAnswerInDecoys() {
        for (String tier : TIERS) {
            for (String item : ITEMS) {
                Reveal r = ChestReveal.build(tier, item, TIERS, ITEMS, 1);
                if (r.tierDecoys().contains(tier) || r.itemDecoys().contains(item)) {
                    return false;
                }
                if (r.tierDecoys().size() != TIERS.size() - 1 || r.itemDecoys().size() != ITEMS.size() - 1) {
                    return false;
                }
            }
        }
        return true;
    }

    static boolean noCrossOver() {
        Reveal r = ChestReveal.build("Rare", "Ember Ring", TIERS, ITEMS, 4);
        for (int t = 0; t <= ChestReveal.TOTAL_TICKS; t++) {
            Frame f = r.frame(t);
            if (f.tier().symbol() != null && ITEMS.contains(f.tier().symbol())) {
                return false;
            }
            if (f.item().symbol() != null && TIERS.contains(f.item().symbol())) {
                return false;
            }
        }
        return true;
    }

    static boolean showsAllDecoys() {
        Reveal r = ChestReveal.build("Rare", "Ember Ring", TIERS, ITEMS, 6);
        Set<String> seen = new HashSet<>();
        for (int t = 0; t < ChestReveal.TIER_STOP_TICK; t++) {
            seen.add(r.frame(t).tier().symbol());
        }
        return seen.equals(new HashSet<>(Arrays.asList("Common", "Uncommon", "Legendary")));
    }

    static boolean monotone(int length) {
        int prev = -1;
        for (int e = -3; e <= length + 5; e++) {
            int s = ChestReveal.symbolAt(e, length);
            if (s < 0 || s < prev) {
                return false;
            }
            prev = s;
        }
        return true;
    }

    static boolean sameEverywhere(int answers, int seeds) {
        for (int i = 0; i < answers; i++) {
            for (long s = 0; s < seeds; s++) {
                Reveal x = ChestReveal.build(TIERS.get(i % 4), ITEMS.get(i), TIERS, ITEMS, s);
                Reveal y = ChestReveal.build(TIERS.get(i % 4), ITEMS.get(i), TIERS, ITEMS, s);
                for (int t = 0; t <= ChestReveal.TOTAL_TICKS; t++) {
                    if (!x.frame(t).equals(y.frame(t))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    static boolean variety() {
        Reveal base = ChestReveal.build("Rare", "Ember Ring", TIERS, ITEMS, 0);
        int differ = 0;
        for (long s = 1; s <= 10; s++) {
            Reveal o = ChestReveal.build("Rare", "Ember Ring", TIERS, ITEMS, s);
            if (!o.itemDecoys().equals(base.itemDecoys())) {
                differ++;
            }
        }
        return differ >= 6;
    }

    static boolean poolsUntouched() {
        List<String> t = new ArrayList<>(TIERS);
        List<String> i = new ArrayList<>(ITEMS);
        ChestReveal.build("Rare", "Ember Ring", t, i, 5);
        return t.equals(TIERS) && i.equals(ITEMS);
    }

    static boolean allEqual(Reveal r, String decoy, boolean tierReel) {
        int from = tierReel ? 0 : ChestReveal.TIER_STOP_TICK + ChestReveal.GAP_TICKS;
        int to = tierReel ? ChestReveal.TIER_STOP_TICK : ChestReveal.ITEM_STOP_TICK;
        for (int t = from; t < to; t++) {
            String s = tierReel ? r.frame(t).tier().symbol() : r.frame(t).item().symbol();
            if (!decoy.equals(s)) {
                return false;
            }
        }
        return true;
    }

    static List<String> manyNames(int n) {
        List<String> l = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            l.add("Item " + i);
        }
        return l;
    }

    static boolean lastNotCrash(Reveal r) {
        for (int t = 0; t <= ChestReveal.TOTAL_TICKS; t++) {
            r.frame(t);
        }
        return true;
    }

    static boolean sameNameBothReels() {
        List<String> shared = Arrays.asList("A", "B", "C");
        Reveal r = ChestReveal.build("B", "C", shared, shared, 1);
        Frame end = r.frame(ChestReveal.TOTAL_TICKS);
        return "B".equals(end.tier().symbol()) && "C".equals(end.item().symbol()) && !r.tierDecoys().contains("B") && !r.itemDecoys().contains("C");
    }
}
