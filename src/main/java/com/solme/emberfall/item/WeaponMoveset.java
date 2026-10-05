package com.solme.emberfall.item;

/**
 * How a weapon's automatic attack actually plays out (design doc-equivalent
 * for the weapon system: distinct player-facing movesets, not just stat
 * sticks). Read by {@link com.solme.emberfall.combat.AutoAttackSystem} to
 * decide *what* the auto-attack does this tick, on top of the existing
 * *when* (the vanilla attack-speed cooldown gate, unchanged).
 */
public enum WeaponMoveset {
    /** Baseline: one vanilla {@code player.attack(target)} on the nearest hostile. Broadsword. */
    MELEE_SINGLE,
    /** Same single vanilla hit, plus a stacking Wither-based "Rend" bleed (see
     *  AutoAttackSystem.REND_MAX_AMPLIFIER) on every landed hit, and every 4th
     *  consecutive landed hit is a guaranteed extra free strike on the same
     *  target (which also stacks Rend). Twin Daggers. */
    MELEE_DUAL,
    /** Same vanilla hit on the primary target, plus the same damage dealt to up to 2 more
     *  hostiles within a short forward cone. War Halberd. */
    MELEE_CLEAVE,
    /** No melee swing at all - fires a tracked bolt at the nearest hostile within the
     *  weapon's long range, dealing its own configured damage on impact. Hunting Bow. */
    RANGED_SINGLE,
    /** Same bolt as RANGED_SINGLE, but every 4th shot instead detonates in a radius around
     *  the target, damaging every hostile caught in it. Arcane Staff. */
    RANGED_AOE,
    /** Same vanilla hit as MELEE_SINGLE, but every landed hit also yanks the target toward
     *  the wielder, and every 4th consecutive landed hit additionally drags nearby hostiles
     *  into a pile at the target's position - a gap-closer/crowd-control weapon, not a
     *  bigger-number one. Gravechain. See {@link com.solme.emberfall.combat.AutoAttackSystem#REND_MAX_AMPLIFIER}
     *  sibling constants for its own tuning. */
    MELEE_HOOK,
    /** No melee swing, no windup, no cooldown - a standing contact-damage aura that spins
     *  continuously around the wielder, entirely independent of this weapon's own
     *  attack-speed cadence (see {@link com.solme.emberfall.combat.OrbitWeaponSystem}).
     *  Spectral Sickles. */
    ORBIT,
    /** Each "shot" (gated by this weapon's own attack-speed cadence, like RANGED_SINGLE)
     *  instantly plants a stationary beacon at the current target's position instead of
     *  striking it - the beacon then pulses AoE damage to anything nearby on its own timer,
     *  independent of the wielder's cadence, until it burns out (see
     *  {@link com.solme.emberfall.combat.TotemWeaponSystem}). Ashen Beacon. */
    TOTEM
}
