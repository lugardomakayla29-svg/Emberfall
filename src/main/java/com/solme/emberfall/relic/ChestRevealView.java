package com.solme.emberfall.relic;

/**
 * What the chest reveal SCREEN needs on top of the pools: a build that cannot throw, and the colours. {@code ChestReveal.build} throws when the answer is
 * not in a pool (a server naming a relic this client's {@link RelicPool} does not know, e.g. a version mismatch), and an exception inside a packet handler
 * must never take the client down, so {@link #safeBuild} returns {@code null} and the screen shows the answer without the spin. Pure: no Minecraft types.
 */
public final class ChestRevealView {
    private ChestRevealView() {}

    /** A reveal for this answer, or {@code null} when the answer cannot be built (unknown tier or item). Never throws. */
    public static ChestReveal.Reveal safeBuild(String tier, String item, long seed) {
        try {
            return ChestReveal.build(tier, item, ChestRevealPools.tiers(), ChestRevealPools.items(), seed);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The colour (0xRRGGBB) of the tier with this label, or {@code fallback} for an unknown label. */
    public static int tierRgb(String label, int fallback) {
        for (RelicRarity r : RelicRarity.values()) {
            if (r.label().equals(label)) {
                return r.rgb();
            }
        }
        return fallback;
    }

    /** The tier colour of the relic with this name, or {@code fallback} when no relic has it. */
    public static int itemRgb(String name, int fallback) {
        for (Relic r : RelicPool.all()) {
            if (r.name().equals(name)) {
                return r.rarity().rgb();
            }
        }
        return fallback;
    }
}
