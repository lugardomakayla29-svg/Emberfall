package com.solme.emberfall.relic;

import java.util.Map;
import java.util.Set;
import java.util.function.DoubleSupplier;

/**
 * The whole decision of opening one chest, as a PURE function of plain inputs, so every branch is provable without a server.
 * The server layer only has to apply the returned {@link Outcome}: take the gold, count the opening, hand over the relic.
 *
 * Three kinds. PAID: costs the current price, may open free through a Key, and counts toward the next price (unless the
 * Ember Ledger stops the count, which PlayerRelics.addChestOpened already handles). FREE: dropped by elites, bosses and
 * shrines; costs nothing, never counts toward the price, Keys do not matter. GOLD: a paid chest with a floor of RARE.
 *
 * Nothing is charged unless a relic is actually handed over, so an exhausted pool can never take a player's gold for nothing.
 */
public final class ChestOpening {
    private ChestOpening() {}

    public enum Kind { PAID, FREE, GOLD }

    /** Lowest tier a gold chest can give. */
    public static final RelicRarity GOLD_FLOOR = RelicRarity.RARE;

    /** Why an opening did not happen. */
    public enum Refusal { NONE, NOT_ENOUGH_GOLD, NOTHING_LEFT }

    /**
     * @param opened       whether a relic was handed over
     * @param relic        the relic, or null
     * @param goldCost     gold to take (0 when free, or when nothing was given)
     * @param countsOpening true when this opening raises the price counter
     * @param keyProc      true when a Key made a paid chest free
     * @param refusal      why nothing happened (NONE when opened)
     */
    public record Outcome(boolean opened, Relic relic, int goldCost, boolean countsOpening, boolean keyProc, Refusal refusal) {}

    public static Outcome open(Kind kind, int gold, int openedBefore, double luck, int keyStacks, Map<String, Integer> owned,
                               Set<String> unlocked, DoubleSupplier roll) {
        boolean paidKind = kind != Kind.FREE;
        boolean keyProc = paidKind && roll.getAsDouble() < RelicMath.keyChance(keyStacks);
        int price = paidKind && !keyProc ? RelicMath.chestPrice(openedBefore) : 0;
        if (gold < price) {
            return new Outcome(false, null, 0, false, false, Refusal.NOT_ENOUGH_GOLD);
        }
        RelicRarity tier = RelicMath.rollRarity(luck, roll);
        if (kind == Kind.GOLD && tier.ordinal() < GOLD_FLOOR.ordinal()) {
            tier = GOLD_FLOOR;
        }
        Relic relic = RelicPool.pick(tier, owned, unlocked, roll);
        if (relic == null) {
            return new Outcome(false, null, 0, false, false, Refusal.NOTHING_LEFT);
        }
        // A Key proc is free AND does not raise the price; only a real paid opening counts.
        return new Outcome(true, relic, price, paidKind && !keyProc, keyProc, Refusal.NONE);
    }
}
