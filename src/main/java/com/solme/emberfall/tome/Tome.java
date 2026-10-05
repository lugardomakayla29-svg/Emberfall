package com.solme.emberfall.tome;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * A single Tome definition (design doc 7.1). Two effect mechanisms exist:
 *   - {@code onApply}: fires once per pick, for effects that are naturally
 *     expressed as a one-shot mutation (a permanent AttributeModifier).
 *     Called with the 1-based stack index this pick represents, so repeat
 *     picks of the same Tome get distinct modifier ids and stack additively.
 *   - On-hit chance effects (ignite/slow/lifesteal) are NOT applied here -
 *     they're derived fresh from owned stack counts by
 *     {@link CombatStats#recompute}, keyed by this Tome's id. That keeps
 *     "how much lifesteal do I have" a pure function of owned Tomes rather
 *     than mutable state two places could disagree about.
 */
public record Tome(
        String id,
        String displayName,
        String description,
        TomeCategory category,
        List<SynergyTag> tags,
        int maxStacks,
        TomeApplyEffect onApply,
        String requiresWeaponId
) {
    /** Existing 7-argument form: offered to every player regardless of weapon (requiresWeaponId null). */
    public Tome(String id, String displayName, String description, TomeCategory category,
                List<SynergyTag> tags, int maxStacks, TomeApplyEffect onApply) {
        this(id, displayName, description, category, tags, maxStacks, onApply, null);
    }

    /** True when this Tome is only useful with one specific weapon and should not be offered otherwise. */
    public boolean isWeaponGated() {
        return requiresWeaponId != null;
    }

    @FunctionalInterface
    public interface TomeApplyEffect {
        void apply(ServerPlayer player, int stackIndex);

        TomeApplyEffect NONE = (player, stackIndex) -> {};
    }
}
