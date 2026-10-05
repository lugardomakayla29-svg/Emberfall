package com.solme.emberfall.relic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleSupplier;

/**
 * The relic catalogue (first set: 24, six per tier). Names and effects are Emberfall/Minecraft originals inspired by
 * the owner's Megabonk research, not copies. To add a relic: one {@code add(...)} line here and one case in
 * {@link RelicEffects}. Order here is the order shown in lists and the creative tab.
 */
public final class RelicPool {
    private RelicPool() {}

    private static final Map<String, Relic> BY_ID = new LinkedHashMap<>();

    private static void add(String id, String name, RelicRarity rarity, int max, String desc) {
        put(new Relic(id, name, rarity, desc, max, null, null));
    }

    private static void gated(String id, String name, RelicRarity rarity, int max, String desc, String unlockId, String unlockText) {
        put(new Relic(id, name, rarity, desc, max, unlockId, unlockText));
    }

    private static void put(Relic r) {
        if (BY_ID.put(r.id(), r) != null) {
            throw new IllegalStateException("duplicate relic id " + r.id());
        }
    }

    static {
        // COMMON
        add("clover", "Four-Leaf Clover", RelicRarity.COMMON, 10, "+8 Luck: rarer relics drop more often.");
        gated("ember_key", "Ember Key", RelicRarity.COMMON, 5, "+10% chance a paid chest opens free (max 50%).",
                "open_25_chests", "Open 25 paid chests across runs.");
        add("oat_loaf", "Oat Loaf", RelicRarity.COMMON, 10, "+4 max health (2 hearts).");
        add("gold_nugget", "Golden Nugget", RelicRarity.COMMON, 10, "+15% Gold from kills.");
        add("clockwork_charm", "Clockwork Charm", RelicRarity.COMMON, 10, "+10% XP from kills.");
        gated("iron_boots", "Iron Boots", RelicRarity.COMMON, 5, "+8% movement speed.",
                "reach_level_15", "Reach level 15 in a run.");
        // UNCOMMON
        add("quiver_of_plenty", "Quiver of Plenty", RelicRarity.UNCOMMON, 3, "+1 extra strike or projectile on multi-hit weapons.");
        add("pearl_shard", "Ender Pearl Shard", RelicRarity.UNCOMMON, 5, "8% chance to shrug off a hit.");
        add("campfire_core", "Campfire Core", RelicRarity.UNCOMMON, 5, "Regenerate health while standing still.");
        add("ember_ledger", "Ember Ledger", RelicRarity.UNCOMMON, 1, "Paid chests stop getting more expensive.");
        add("thorn_vest", "Thorn Vest", RelicRarity.UNCOMMON, 5, "Melee attackers take damage back.");
        add("magnet_stone", "Magnet Stone", RelicRarity.UNCOMMON, 5, "+25% pickup radius for XP and Gold.");
        // RARE
        add("big_bonk", "Big Bonk Hammer", RelicRarity.RARE, 3, "2% chance a hit deals 20x damage.");
        add("spiked_censer", "Spiked Censer", RelicRarity.RARE, 3, "Hits can burst into a small blast.");
        add("frostbound_ring", "Frostbound Ring", RelicRarity.RARE, 3, "Hits can chill and freeze foes.");
        add("wizards_cowl", "Wizard's Cowl", RelicRarity.RARE, 3, "-10% weapon cooldowns.");
        add("blood_chalice", "Blood Chalice", RelicRarity.RARE, 3, "Heal for a share of damage dealt.");
        gated("anvil_of_dawn", "Anvil of Dawn", RelicRarity.RARE, 1, "Every weapon strike deals a bonus 25%.",
                "clear_3_challenges", "Clear 3 Challenge Shrines across runs.");
        // LEGENDARY
        add("totem_of_returning", "Totem of Returning", RelicRarity.LEGENDARY, 1, "Survive one fatal hit per run.");
        add("mirror_shard", "Mirror Shard", RelicRarity.LEGENDARY, 1, "Reflect damage and gain a moment of invulnerability when hit.");
        add("soul_lantern", "Soul Lantern", RelicRarity.LEGENDARY, 1, "Every kill adds permanent damage this run.");
        add("hourglass", "Hourglass", RelicRarity.LEGENDARY, 1, "Below 50% health, foes move at half speed.");
        add("dragons_heart", "Dragon's Heart", RelicRarity.LEGENDARY, 1, "+40 max health and strong regeneration.");
        add("wither_crown", "Wither Crown", RelicRarity.LEGENDARY, 1, "+50% damage, but the run grows more dangerous.");
    }

    public static Relic byId(String id) {
        return BY_ID.get(id);
    }

    public static List<Relic> all() {
        return List.copyOf(BY_ID.values());
    }

    public static List<Relic> ofRarity(RelicRarity rarity) {
        List<Relic> out = new ArrayList<>();
        for (Relic r : BY_ID.values()) {
            if (r.rarity() == rarity) {
                out.add(r);
            }
        }
        return out;
    }

    /**
     * Picks a relic of the given tier the player may receive: unlocked, and not already at its stack cap.
     * If the tier has nothing eligible it falls back DOWN a tier (never up), and returns null only when nothing at all
     * is eligible. {@code owned} maps relic id to stacks held; {@code unlocked} is the player's unlocked gated ids.
     */
    public static Relic pick(RelicRarity rarity, Map<String, Integer> owned, Set<String> unlocked, DoubleSupplier roll) {
        for (int tier = rarity.ordinal(); tier >= 0; tier--) {
            List<Relic> eligible = new ArrayList<>();
            for (Relic r : ofRarity(RelicRarity.values()[tier])) {
                if (r.isGated() && !unlocked.contains(r.unlockId())) {
                    continue;
                }
                if (owned.getOrDefault(r.id(), 0) >= r.maxStacks()) {
                    continue;
                }
                eligible.add(r);
            }
            if (!eligible.isEmpty()) {
                return eligible.get(Math.min(eligible.size() - 1, (int) (roll.getAsDouble() * eligible.size())));
            }
        }
        return null;
    }
}
