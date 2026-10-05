package com.solme.emberfall.tome;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A player's current on-hit chance effects, derived fresh from
 * {@link PlayerBuild}'s owned Tomes every time it's recomputed (after every
 * Tome pick - see TomeChoiceManager) rather than mutated incrementally.
 * Keeps "how much lifesteal do I have right now" a pure function of owned
 * Tomes, so there's no way for two code paths to disagree about it.
 *
 * One field group per synergy tag's on-hit Tome (Fire/Frost/Poison/
 * Lightning/Explosive/Lifesteal - design doc 7.1/7.2). Momentum and Summon
 * are the two tags that pay off as one-shot/persistent grants instead
 * (permanent attribute modifier, companion stat bump) so they live in
 * {@link SynergyEffects} / {@link PlayerCompanions} instead of here.
 */
public final class CombatStats {
    private static final Map<UUID, CombatStats> byPlayer = new HashMap<>();

    public double igniteChance = 0.0;
    public int igniteDurationTicks = 0;
    public double lifestealPercent = 0.0;

    public double chillChance = 0.0;
    public int chillDurationTicks = 0;
    public int chillAmplifier = 0;

    public double poisonChance = 0.0;
    public int poisonDurationTicks = 0;
    public int poisonAmplifier = 0;

    public double chainChance = 0.0;
    public int chainTargets = 0;

    public double detonateChance = 0.0;
    public double detonateRadius = 0.0;
    public float detonateDamage = 0.0F;

    /** Cinder Wisp (Tome, Fire tag): on-kill ignite-spread radius, 0 = not owned. Independent
     * of Ember Touch's own on-hit chance - a real standalone tool, not a tag carrier. */
    public double igniteSpreadRadius = 0.0;
    public int igniteSpreadDurationTicks = 0;

    /** Permafrost Shard (Tome, Frost tag): on-kill chill-spread radius, 0 = not owned. */
    public double chillSpreadRadius = 0.0;
    public int chillSpreadDurationTicks = 0;

    /** Serpent's Mark (Tome, Poison tag): on-kill poison-spread radius, 0 = not owned. */
    public double poisonSpreadRadius = 0.0;
    public int poisonSpreadDurationTicks = 0;

    /** Storm Sigil (Tome, Lightning tag): guaranteed chain-lightning every N landed hits,
     * 0 = not owned. {@link #stormHitCounter} is NOT reset by recompute() (see recompute's
     * own comment) - it's live per-hit progress, not a derived stat. */
    public int stormCadenceInterval = 0;
    public int stormHitCounter = 0;

    /** Unstable Core (Tome, Explosive tag): an independent smaller on-kill detonate that can
     * hop to a further kill, separate from Volatile Rounds' own detonateChance above. */
    public double unstableChance = 0.0;
    public double unstableRadius = 0.0;
    public float unstableDamage = 0.0F;
    public int unstableMaxHops = 0;

    /** Sanguine Locket (Tome, Lifesteal tag): on-kill Absorption shield, amplifier -1 = not owned. */
    public int killShieldAmplifier = -1;
    public int killShieldDurationTicks = 0;

    /**
     * Hunter's Instinct (Tome) stack count, capped at 3 - read directly by
     * AutoAttackSystem for the Hunting Bow (see that Tome's javadoc for what
     * each tier does). Unlike the on-hit chance fields above this isn't a
     * scaling percentage - it's a tier switch - so it's exposed as the raw
     * stack count rather than pre-derived into a chance/duration pair.
     */
    public int huntingBowHomingTier = 0;

    /** Arcane Convergence (Tome) stack count, capped at 3 - Arcane Staff's branch of the
     * same idea, read directly by AutoAttackSystem's detonateNova. See that Tome's javadoc. */
    public int arcaneConvergenceTier = 0;

    /** Piercing Laser (Tome) stack count, capped at 3 - Arcane Staff's timed execute beam. */
    public int piercingLaserTier = 0;

    /** Spin Barrage (Tome) stack count, capped at 3 - Hunting Bow's timed bolt spiral. */
    public int spinBarrageTier = 0;

    /** Bleeding Edge (Tome) stack count, capped at 3 - Twin Daggers' branch: merges with Rend
     * rather than reusing homing/nova-chain. See that Tome's javadoc / AutoAttackSystem#meleeDual. */
    public int bleedingEdgeTier = 0;

    /** Sundering Wake (Tome) stack count, capped at 3 - War Halberd's branch: merges with Sunder
     * to scale the cleave itself. See that Tome's javadoc / AutoAttackSystem#meleeCleave. */
    public int sunderingWakeTier = 0;

    /** Steady Hand (Tome) stack count, capped at 2 - Broadsword's branch: unlike the other 4
     * weapon Tomes this doesn't scale off an existing built-in hook (Broadsword's moveset
     * has none by design) - it originates its own consecutive-hit-streak mechanic instead.
     * Named distinctly from the unrelated MOMENTUM SynergyTag (move-speed family) despite
     * the similar concept. See that Tome's javadoc / AutoAttackSystem#meleeSingleWeapon. */
    public int steadyHandTier = 0;

    /** Widening Gyre (Tome) stack count, capped at 3 - Spectral Sickles' branch: unlike the
     * other 4 weapon Tomes (which merge with an existing gated-hit hook) this originates its
     * own decaying hit-streak stack, since Orbit has no windup/combo cadence to merge with at
     * all. Read directly by OrbitWeaponSystem. See that Tome's javadoc. */
    public int wideningGyreTier = 0;

    /** Undying Embers (Tome) stack count, capped at 3 - Ashen Beacon's branch: a beacon that's
     * replaced or expires leaves behind a weaker residual Ember instead of a hard cutoff. Read
     * directly by TotemWeaponSystem. See that Tome's javadoc. */
    public int undyingEmbersTier = 0;

    /** Grave Anchor (Tome) stack count, capped at 3 - Gravechain's branch: merges with the
     * weapon's own every-4th-hit Gather trigger. Read directly by AutoAttackSystem#meleeHook.
     * See that Tome's javadoc. */
    public int graveAnchorTier = 0;

    private CombatStats() {}

    public static CombatStats of(ServerPlayer player) {
        return byPlayer.computeIfAbsent(player.getUUID(), k -> new CombatStats());
    }

    public static void clear(ServerPlayer player) {
        byPlayer.remove(player.getUUID());
    }

    /** Rebuilds this player's on-hit stats from scratch from their currently owned Tomes. */
    public static void recompute(ServerPlayer player) {
        CombatStats stats = of(player);
        stats.igniteChance = 0.0;
        stats.igniteDurationTicks = 0;
        stats.lifestealPercent = 0.0;
        stats.chillChance = 0.0;
        stats.chillDurationTicks = 0;
        stats.chillAmplifier = 0;
        stats.poisonChance = 0.0;
        stats.poisonDurationTicks = 0;
        stats.poisonAmplifier = 0;
        stats.chainChance = 0.0;
        stats.chainTargets = 0;
        stats.detonateChance = 0.0;
        stats.detonateRadius = 0.0;
        stats.detonateDamage = 0.0F;
        stats.igniteSpreadRadius = 0.0;
        stats.igniteSpreadDurationTicks = 0;
        stats.chillSpreadRadius = 0.0;
        stats.chillSpreadDurationTicks = 0;
        stats.poisonSpreadRadius = 0.0;
        stats.poisonSpreadDurationTicks = 0;
        stats.stormCadenceInterval = 0;
        // stormHitCounter deliberately NOT reset here - it's live per-hit progress
        // toward the next guaranteed proc, not a value derived from owned Tomes.
        stats.unstableChance = 0.0;
        stats.unstableRadius = 0.0;
        stats.unstableDamage = 0.0F;
        stats.unstableMaxHops = 0;
        stats.killShieldAmplifier = -1;
        stats.killShieldDurationTicks = 0;
        stats.huntingBowHomingTier = 0;
        stats.arcaneConvergenceTier = 0;
        stats.piercingLaserTier = 0;
        stats.spinBarrageTier = 0;
        stats.bleedingEdgeTier = 0;
        stats.sunderingWakeTier = 0;
        stats.steadyHandTier = 0;
        stats.wideningGyreTier = 0;
        stats.undyingEmbersTier = 0;
        stats.graveAnchorTier = 0;

        int emberTouchStacks = PlayerBuild.stacksOf(player, "ember_touch");
        if (emberTouchStacks > 0) {
            stats.igniteChance = Math.min(1.0, 0.25 * emberTouchStacks);
            stats.igniteDurationTicks = 60; // 3s, flat regardless of stacks - stacks raise chance, not duration
            if (SynergyEffects.fireBonusActive(player)) {
                stats.igniteChance = Math.min(1.0, stats.igniteChance + 0.25);
                stats.igniteDurationTicks += 40; // +2s
            }
        }

        int cinderWispStacks = PlayerBuild.stacksOf(player, "cinder_wisp");
        if (cinderWispStacks > 0) {
            stats.igniteSpreadRadius = 2.0 + cinderWispStacks; // 3/4/5 blocks at stack 1/2/3
            stats.igniteSpreadDurationTicks = 60; // 3s
        }

        int bloodlettingStacks = PlayerBuild.stacksOf(player, "bloodletting");
        if (bloodlettingStacks > 0) {
            stats.lifestealPercent = Math.min(0.75, 0.15 * bloodlettingStacks);
        }

        int sanguineLocketStacks = PlayerBuild.stacksOf(player, "sanguine_locket");
        if (sanguineLocketStacks > 0) {
            stats.killShieldAmplifier = Math.min(2, sanguineLocketStacks - 1); // Absorption I/II/III
            stats.killShieldDurationTicks = 100; // 5s
        }

        int frostStacks = PlayerBuild.stacksOf(player, "frostbite_fang");
        if (frostStacks > 0) {
            stats.chillChance = Math.min(1.0, 0.20 * frostStacks);
            stats.chillDurationTicks = 60; // 3s
            if (SynergyEffects.frostBonusActive(player)) {
                stats.chillChance = Math.min(1.0, stats.chillChance + 0.20);
                stats.chillDurationTicks += 40; // +2s
                stats.chillAmplifier = 1; // Slowness II
            }
        }

        int permafrostShardStacks = PlayerBuild.stacksOf(player, "permafrost_shard");
        if (permafrostShardStacks > 0) {
            stats.chillSpreadRadius = 2.0 + permafrostShardStacks;
            stats.chillSpreadDurationTicks = 60;
        }

        int poisonStacks = PlayerBuild.stacksOf(player, "venomous_fang");
        if (poisonStacks > 0) {
            stats.poisonChance = Math.min(1.0, 0.20 * poisonStacks);
            stats.poisonDurationTicks = 80; // 4s
            if (SynergyEffects.poisonBonusActive(player)) {
                stats.poisonChance = Math.min(1.0, stats.poisonChance + 0.20);
                stats.poisonDurationTicks += 40; // +2s
                stats.poisonAmplifier = 1; // Poison II
            }
        }

        int serpentsMarkStacks = PlayerBuild.stacksOf(player, "serpents_mark");
        if (serpentsMarkStacks > 0) {
            stats.poisonSpreadRadius = 2.0 + serpentsMarkStacks;
            stats.poisonSpreadDurationTicks = 80;
        }

        int lightningStacks = PlayerBuild.stacksOf(player, "static_discharge");
        if (lightningStacks > 0) {
            stats.chainChance = Math.min(1.0, 0.20 * lightningStacks);
            stats.chainTargets = 1;
            if (SynergyEffects.lightningBonusActive(player)) {
                stats.chainChance = Math.min(1.0, stats.chainChance + 0.20);
                stats.chainTargets = 2;
            }
        }

        int stormSigilStacks = PlayerBuild.stacksOf(player, "storm_sigil");
        if (stormSigilStacks > 0) {
            stats.stormCadenceInterval = 7 - stormSigilStacks; // 6/5/4 hits at stack 1/2/3
        }

        int explosiveStacks = PlayerBuild.stacksOf(player, "volatile_rounds");
        if (explosiveStacks > 0) {
            stats.detonateChance = Math.min(1.0, 0.15 * explosiveStacks);
            stats.detonateRadius = 3.0;
            stats.detonateDamage = 4.0F;
            if (SynergyEffects.explosiveBonusActive(player)) {
                stats.detonateChance = Math.min(1.0, stats.detonateChance + 0.20);
                stats.detonateRadius += 1.5;
                stats.detonateDamage += 2.0F;
            }
        }

        int unstableCoreStacks = PlayerBuild.stacksOf(player, "unstable_core");
        if (unstableCoreStacks > 0) {
            stats.unstableChance = Math.min(1.0, 0.20 * unstableCoreStacks);
            stats.unstableRadius = 2.5;
            stats.unstableDamage = 3.0F;
            stats.unstableMaxHops = unstableCoreStacks;
        }

        stats.huntingBowHomingTier = Math.min(3, PlayerBuild.stacksOf(player, "hunters_instinct"));
        stats.arcaneConvergenceTier = Math.min(3, PlayerBuild.stacksOf(player, "arcane_convergence"));
        stats.piercingLaserTier = Math.min(3, PlayerBuild.stacksOf(player, "piercing_laser"));
        stats.spinBarrageTier = Math.min(3, PlayerBuild.stacksOf(player, "spin_barrage"));
        stats.bleedingEdgeTier = Math.min(3, PlayerBuild.stacksOf(player, "bleeding_edge"));
        stats.sunderingWakeTier = Math.min(3, PlayerBuild.stacksOf(player, "sundering_wake"));
        stats.steadyHandTier = Math.min(3, PlayerBuild.stacksOf(player, "steady_hand"));
        stats.wideningGyreTier = Math.min(3, PlayerBuild.stacksOf(player, "widening_gyre"));
        stats.undyingEmbersTier = Math.min(3, PlayerBuild.stacksOf(player, "undying_embers"));
        stats.graveAnchorTier = Math.min(3, PlayerBuild.stacksOf(player, "grave_anchor"));
    }
}
