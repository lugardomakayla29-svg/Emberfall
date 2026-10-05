package com.solme.emberfall.item;

/**
 * One weapon's full definition: display info, its moveset (see
 * {@link WeaponMoveset}), the numbers that drive it, and its meta-currency
 * unlock cost (design doc: weapons are unlocked permanently via the
 * currency shop, or found/picked mid-run - see {@link WeaponUnlocks} and
 * {@link WeaponChoiceManager}).
 *
 * Two independent damage paths, matching how the two moveset families
 * actually deal damage:
 *   - MELEE_* weapons deal damage through the item's own vanilla
 *     ATTACK_DAMAGE/ATTACK_SPEED attribute modifiers (set on the Item in
 *     {@link ModItems}) via the exact same {@code player.attack(target)}
 *     call AutoAttackSystem already used pre-weapon-system - meleeDamage/
 *     meleeAttackSpeedModifier here are only the numbers used to BUILD
 *     those attribute modifiers at registration time, not re-read at
 *     attack time.
 *   - RANGED_* weapons never call player.attack() at all (there is no
 *     vanilla "melee" happening) - rangedDamage is applied directly to the
 *     bolt's impact by AutoAttackSystem/OnHitEffects, and
 *     rangedAttackSpeedModifier still drives the item's ATTACK_SPEED
 *     attribute purely so the existing getAttackStrengthScale(0) >= 1 cadence
 *     gate keeps controlling "how often does this weapon fire" for free,
 *     exactly like it already does for melee.
 *
 * v1 balance numbers - an explicit first pass (same honesty as
 * RunRewardCalculator's own currency formula), not a tuned final balance.
 */
public record WeaponType(
        String id,
        String displayName,
        String description,
        WeaponMoveset moveset,
        float meleeDamage,
        float meleeAttackSpeedModifier,
        float rangedDamage,
        float rangedAttackSpeedModifier,
        double range,
        long unlockCost,
        boolean unlockedByDefault
) {
    /**
     * Which attribute branch {@link com.solme.emberfall.item.ModItems#register} builds for
     * this weapon: true routes it through rangedDamage/rangedAttackSpeedModifier (no
     * ATTACK_DAMAGE modifier - it never calls {@code player.attack()}), false routes it
     * through meleeDamage/meleeAttackSpeedModifier. TOTEM counts as ranged here purely for
     * that attribute wiring (it needs an ATTACK_SPEED-gated "shot" cadence like a bolt, and
     * its pulse damage comes from rangedDamage) even though it never fires a travelling
     * projectile. ORBIT deliberately stays on the melee branch: its contact damage is read
     * directly off {@link #meleeDamage()} rather than the live ATTACK_DAMAGE attribute (it
     * never calls player.attack() either, but ticks on its own unconditional loop instead of
     * this attribute-speed-gated cadence at all - see {@link com.solme.emberfall.combat.OrbitWeaponSystem}).
     */
    public boolean isRanged() {
        return moveset == WeaponMoveset.RANGED_SINGLE || moveset == WeaponMoveset.RANGED_AOE
                || moveset == WeaponMoveset.TOTEM;
    }
}
