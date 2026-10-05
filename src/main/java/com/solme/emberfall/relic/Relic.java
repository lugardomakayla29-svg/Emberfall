package com.solme.emberfall.relic;

/**
 * One relic definition. Pure data: the effect itself is derived from how many stacks a player holds
 * ({@link RelicEffects}), never stored here, so adding a relic is one line in {@link RelicPool} plus one case there.
 *
 * {@code unlockId} null means available to everyone from the start. Otherwise the relic cannot drop for a player until
 * that unlock condition has been met once (it then stays unlocked across runs, see {@link RelicUnlocks}).
 */
public record Relic(
        String id,
        String name,
        RelicRarity rarity,
        String description,
        int maxStacks,
        String unlockId,
        String unlockText
) {
    public boolean isGated() {
        return unlockId != null;
    }
}
