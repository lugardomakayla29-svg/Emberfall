package com.solme.emberfall.relic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleSupplier;

/**
 * What a Testificate merchant sells, as PURE functions of plain inputs, so every branch is provable without a server.
 *
 * A merchant has a TIER (common to legendary, rolled with the player's Luck like a chest, and shown on his banner). He stocks
 * three DIFFERENT relics the player may actually receive. Each slot rolls at his tier, with a {@link #SLIP_CHANCE} chance to slip
 * one tier down, so a Common merchant sells commons and a Legendary one mostly legendaries but not always.
 *
 * Prices follow what a chest costs RIGHT NOW (so they stay fair as the price climbs, and freeze under the Ember Ledger),
 * times a multiplier per relic rarity. A merchant lets the player CHOOSE, so even his cheapest item costs more than a chest.
 */
public final class MerchantOffer {
    private MerchantOffer() {}

    public static final int SLOTS = 3;
    /** Chance one slot rolls a tier lower than the merchant's own. */
    public static final double SLIP_CHANCE = 0.25;
    /** Price multiplier of a relic over the current chest price, by relic rarity ordinal (common to legendary). */
    public static final double[] PRICE_MULT = {1.0, 1.5, 2.2, 3.5};

    /** One shelf slot. */
    public record Item(Relic relic, int price) {}

    /** The whole stall: who the merchant is and what he sells (fewer than three only when the pool runs dry). */
    public record Stall(RelicRarity tier, List<Item> items) {}

    /** Gold for one relic when a chest currently costs {@code chestPrice}. Always at least the chest price. */
    public static int price(RelicRarity rarity, int chestPrice) {
        return Math.max(chestPrice, (int) Math.ceil(chestPrice * PRICE_MULT[rarity.ordinal()]));
    }

    /** Builds a stall. {@code owned} and {@code unlocked} are the buyer's, so nothing offered is maxed out or locked. */
    public static Stall stock(RelicRarity tier, int chestPrice, Map<String, Integer> owned, Set<String> unlocked, DoubleSupplier roll) {
        List<Item> items = new ArrayList<>();
        Map<String, Integer> virtual = new java.util.HashMap<>(owned);
        for (int slot = 0; slot < SLOTS; slot++) {
            RelicRarity want = tier.ordinal() > 0 && roll.getAsDouble() < SLIP_CHANCE ? RelicRarity.values()[tier.ordinal() - 1] : tier;
            Relic relic = pickDistinct(want, virtual, unlocked, items, roll);
            if (relic == null) {
                break; // nothing at all left for this buyer
            }
            items.add(new Item(relic, price(relic.rarity(), chestPrice)));
        }
        return new Stall(tier, List.copyOf(items));
    }

    /** {@link RelicPool#pick} but never a relic already on this shelf. A relic with room for stacks still appears once. */
    private static Relic pickDistinct(RelicRarity want, Map<String, Integer> owned, Set<String> unlocked, List<Item> taken, DoubleSupplier roll) {
        Map<String, Integer> blocked = new java.util.HashMap<>(owned);
        for (Item it : taken) {
            blocked.put(it.relic().id(), Integer.MAX_VALUE / 2); // treat as maxed for this pick only
        }
        return RelicPool.pick(want, blocked, unlocked, roll);
    }
}
