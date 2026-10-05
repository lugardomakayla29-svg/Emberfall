package com.solme.emberfall.tome;

import com.solme.emberfall.EmberfallMod;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/**
 * Design doc 4.1 step 4 target ("a small (5-10 item) starting Tome pool") /
 * 7.1-7.2. Every entry here is a genuinely real, working effect - no
 * reskinned placeholders and (as of the refinement pass below) no pure
 * "does nothing alone, just carries the tag" filler either. Content by tag:
 *   - 3 flat stat Passives (no tag) as filler power.
 *   - Momentum (3): 3 move-speed Passives, each real on its own - synergy
 *     is a one-shot permanent +10% speed grant (see SynergyEffects).
 *   - Fire/Frost/Poison (2 each): a chance-on-hit status Tome (Ember Touch/
 *     Frostbite Fang/Venomous Fang) paired with a real on-KILL status-
 *     spread Tome (Cinder Wisp/Permafrost Shard/Serpent's Mark) that
 *     applies the same status to everything nearby - two independent,
 *     always-useful tools per element instead of 1 real + 2 dead carriers.
 *   - Lightning (2): Static Discharge's chance-based single-target chain +
 *     Storm Sigil, a guaranteed every-Nth-hit chain to 2 targets - chance
 *     vs. cadence, genuinely different tools rather than the same lever twice.
 *   - Explosive (2): Volatile Rounds' on-kill detonate + Unstable Core, an
 *     independent smaller on-kill detonate that can hop to further kills -
 *     two separate detonate rolls that stack naturally if both are owned.
 *   - Lifesteal (2): Bloodletting (on-hit % heal) + Sanguine Locket
 *     (on-kill Absorption shield) - sustain vs. burst defense.
 *   - Summon (3): Loyal Hound + Spectral Hound (each grants a real tamed
 *     Wolf companion via {@link PlayerCompanions}) + Reliquary Shard, now a
 *     real standalone companion stat buff instead of a dead carrier.
 * Each tag's own threshold bonus (SynergyEffects) still exists, but
 * {@link PlayerBuild#tagCount} now sums total STACKS across a tag's Tomes
 * rather than requiring one copy of every distinct id in the family - so
 * repeat-picking either of a 2-Tome family's members is a valid path to
 * that family's synergy, not just a fixed "collect all 3" checklist.
 */
public final class TomePool {
    public static final List<Tome> ALL = List.of(
            statTome("vitality_charm", "Vitality Charm",
                    "+2.7 Max Health (each extra copy adds 15% less)", List.of(), Attributes.MAX_HEALTH, 2.0, AttributeModifier.Operation.ADD_VALUE, true),
            statTome("iron_will", "Iron Will",
                    "+1.3 Attack Damage (each extra copy adds 15% less)", List.of(), Attributes.ATTACK_DAMAGE, 1.0, AttributeModifier.Operation.ADD_VALUE, false),
            statTome("bulwark_plating", "Bulwark Plating",
                    "+2.7 Armor (each extra copy adds 15% less)", List.of(), Attributes.ARMOR, 2.0, AttributeModifier.Operation.ADD_VALUE, false),

            statTome("swift_boots", "Swift Boots",
                    "+9.8% Move Speed (each extra copy adds 25% less)", List.of(SynergyTag.MOMENTUM), Attributes.MOVEMENT_SPEED, 0.06, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, false),
            statTome("momentum_core", "Momentum Core",
                    "+13.1% Move Speed (each extra copy adds 25% less)", List.of(SynergyTag.MOMENTUM), Attributes.MOVEMENT_SPEED, 0.08, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, false),
            statTome("sprinters_edge", "Sprinter's Edge",
                    "+6.6% Move Speed (each extra copy adds 25% less)", List.of(SynergyTag.MOMENTUM), Attributes.MOVEMENT_SPEED, 0.04, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, false),

            new Tome("ember_touch", "Ember Touch",
                    "25% chance on hit to ignite the target for 3s",
                    TomeCategory.PASSIVE, List.of(SynergyTag.FIRE), 5, Tome.TomeApplyEffect.NONE),
            new Tome("cinder_wisp", "Cinder Wisp",
                    "On kill, the target bursts into embers, igniting every enemy within 3 blocks (+1 block per copy) for 3s",
                    TomeCategory.PASSIVE, List.of(SynergyTag.FIRE), 3, Tome.TomeApplyEffect.NONE),

            new Tome("frostbite_fang", "Frostbite Fang",
                    "20% chance on hit to chill the target, slowing it for 3s",
                    TomeCategory.PASSIVE, List.of(SynergyTag.FROST), 5, Tome.TomeApplyEffect.NONE),
            new Tome("permafrost_shard", "Permafrost Shard",
                    "On kill, releases a burst of frost that chills every enemy within 3 blocks (+1 block per copy) for 3s",
                    TomeCategory.PASSIVE, List.of(SynergyTag.FROST), 3, Tome.TomeApplyEffect.NONE),

            new Tome("venomous_fang", "Venomous Fang",
                    "20% chance on hit to poison the target for 4s",
                    TomeCategory.PASSIVE, List.of(SynergyTag.POISON), 5, Tome.TomeApplyEffect.NONE),
            new Tome("serpents_mark", "Serpent's Mark",
                    "On kill, releases a cloud of toxic gas that poisons every enemy within 3 blocks (+1 block per copy) for 4s",
                    TomeCategory.PASSIVE, List.of(SynergyTag.POISON), 3, Tome.TomeApplyEffect.NONE),

            new Tome("static_discharge", "Static Discharge",
                    "20% chance on hit to arc lightning to a nearby enemy for the same damage",
                    TomeCategory.PASSIVE, List.of(SynergyTag.LIGHTNING), 5, Tome.TomeApplyEffect.NONE),
            new Tome("storm_sigil", "Storm Sigil",
                    "Every 6th landed hit unconditionally arcs lightning to the 2 nearest enemies (interval drops to every 5th, then every 4th, per extra copy)",
                    TomeCategory.PASSIVE, List.of(SynergyTag.LIGHTNING), 3, Tome.TomeApplyEffect.NONE),

            new Tome("volatile_rounds", "Volatile Rounds",
                    "15% chance for a kill to detonate the target, damaging nearby enemies",
                    TomeCategory.PASSIVE, List.of(SynergyTag.EXPLOSIVE), 5, Tome.TomeApplyEffect.NONE),
            new Tome("unstable_core", "Unstable Core",
                    "20% chance on kill to trigger a smaller chain detonation that can hop to another kill (+1 hop per copy, up to 3)",
                    TomeCategory.PASSIVE, List.of(SynergyTag.EXPLOSIVE), 3, Tome.TomeApplyEffect.NONE),

            new Tome("bloodletting", "Bloodletting",
                    "Heal 15% of damage dealt on every hit",
                    TomeCategory.PASSIVE, List.of(SynergyTag.LIFESTEAL), 5, Tome.TomeApplyEffect.NONE),
            new Tome("sanguine_locket", "Sanguine Locket",
                    "On kill, gain a brief Absorption shield (stronger per copy, up to 3) for 5s",
                    TomeCategory.PASSIVE, List.of(SynergyTag.LIFESTEAL), 3, Tome.TomeApplyEffect.NONE),

            summonTome("loyal_hound", "Loyal Hound",
                    "Summon a loyal hound that fights at your side", 2, "Loyal Hound", false),
            summonTome("spectral_hound", "Spectral Hound",
                    "Summon a spectral hound, glowing and fierce", 1, "Spectral Hound", true),
            new Tome("reliquary_shard", "Reliquary Shard",
                    "Every owned companion gains +1 Attack Damage and +20% Max Health per copy (up to 3)",
                    TomeCategory.PASSIVE, List.of(SynergyTag.SUMMON), 3,
                    (player, stackIndex) -> PlayerCompanions.applyReliquaryBonusToAll(player, stackIndex)),

            // First WEAPON-category Tome (the category existed but was deferred - see
            // TomeCategory's javadoc - until real weapon movesets landed). Hunting
            // Bow-specific: not a flat number buff but a real behavior upgrade, gated
            // behind an in-run pick so it's never available "out of the box" - stack 1
            // makes normal shots home in on their target (a moving target no longer
            // slips a straight shot), stack 2 additionally lets Piercing Volley's beam
            // bend to catch targets that are close to, but not perfectly on, the line -
            // the pierce and homing mechanics deliberately merge into one weapon rather
            // than living as two separate bolt-on effects. See CombatStats.
            new Tome("hunters_instinct", "Hunter's Instinct",
                    "Hunting Bow shots home in on their target. 2nd copy: Piercing Volley's beam also bends to catch nearby off-line targets. 3rd copy: a Piercing Volley that kills everything it hits immediately fires again at the next nearest foe",
                    TomeCategory.WEAPON, List.of(), 3, Tome.TomeApplyEffect.NONE, "hunting_bow"),

            // Second WEAPON-category Tome - Arcane Staff's branch of the same "homing"
            // idea as Hunter's Instinct, deliberately built differently rather than
            // reusing the bow's in-flight steering: Nova stays a stationary detonation,
            // but now spawns seeking sparks (same TrackedProjectiles.launchHoming used by
            // the bow and by BlightfeatherMarksman) that hunt down nearby enemies OUTSIDE
            // the blast and trigger a smaller chained nova on impact. Stack 2 lets that
            // chained nova spawn one further hop of sparks. See CombatStats/AutoAttackSystem.
            new Tome("arcane_convergence", "Arcane Convergence",
                    "Nova detonations fire seeking sparks that hunt down nearby enemies and trigger a smaller nova on impact. 2nd copy: those secondary novas chain one hop further. 3rd copy: any Nova in the chain that kills something fires one bonus spark, even past the usual hop limit",
                    TomeCategory.WEAPON, List.of(), 3, Tome.TomeApplyEffect.NONE, "arcane_staff"),

            // Third WEAPON-category Tome - Twin Daggers' branch: merges with the weapon's own
            // built-in Rend rather than adding a separate proc-chain. The base moveset's every-
            // 4th-hit "free strike" already lands as a single +100% bonus swing (not a genuine
            // second same-tick hit - see AutoAttackSystem#meleeDual's javadoc for why a literal
            // second hit on the same target is a dead end under vanilla's hit-invulnerability
            // rules). Bleeding Edge grows that Empowered swing's bonus further based on the
            // target's current Rend stacks, and stack 2 lowers the combo threshold to every 3rd
            // landed hit. See CombatStats/AutoAttackSystem#meleeDual.
            new Tome("bleeding_edge", "Bleeding Edge",
                    "Every 4th landed Twin Daggers hit is an Empowered swing that grows stronger with the target's Rend stacks. 2nd copy: Empowered swings trigger every 3rd hit instead. 3rd copy: an Empowered swing that kills its target spreads its Rend stacks to the nearest foe",
                    TomeCategory.WEAPON, List.of(), 3, Tome.TomeApplyEffect.NONE, "twin_daggers"),

            // Fourth WEAPON-category Tome - War Halberd's branch. Also merges with the
            // weapon's own stacking effect (Sunder) rather than reusing a proc-chain: the
            // cleave itself scales off how much the primary target was ALREADY Sundered by
            // previous hits (read before this hit's own Sunder application, so it's always
            // one hit behind - a halberd rewards having already committed to a target).
            // Stack 1: cleave radius grows +0.75 per prior Sunder stack (up to +1.5 at max
            // Sunder). Stack 2: once the primary target is already at max Sunder, cleave
            // hits up to 3 additional foes instead of 2. See CombatStats/AutoAttackSystem#meleeCleave.
            new Tome("sundering_wake", "Sundering Wake",
                    "Cleave radius grows with the primary target's existing Sunder stacks. 2nd copy: at max Sunder, cleave hits one extra target. 3rd copy: killing a max-Sundered target shatters its armor, instantly Sundering every foe in the cleave radius",
                    TomeCategory.WEAPON, List.of(), 3, Tome.TomeApplyEffect.NONE, "war_halberd"),

            // Fifth and final WEAPON-category Tome - Broadsword's branch, and the odd one out
            // by design: Broadsword's own moveset is the plain vanilla hit with no built-in
            // hook to scale (see WeaponMoveset#MELEE_SINGLE's javadoc), so unlike the other 4
            // this one originates a brand-new mechanic instead of amplifying an existing one -
            // a consecutive-landed-hit streak (reusing the same PlayerWeapon counter Twin
            // Daggers/Arcane Staff already use for their own "every 4th" cadence). Named
            // "Steady Hand" rather than "Momentum" to avoid colliding with the unrelated
            // pre-existing MOMENTUM SynergyTag (Swift Boots/Momentum Core/Sprinter's Edge's
            // move-speed family) - same underlying English word, deliberately different Tome.
            // Stack 1: every 4th landed hit is an Empowered Strike, dealing the primary hit's
            // damage again at +50%. Stack 2: that Empowered Strike also grants a brief Strength I,
            // rewarding staying on the offensive with a real window of general damage-up.
            // See CombatStats/AutoAttackSystem#meleeSingleWeapon.
            new Tome("steady_hand", "Steady Hand",
                    "Every 4th landed Broadsword hit is an Empowered Strike, dealing bonus damage. 2nd copy: an Empowered Strike also grants a brief Strength boost. 3rd copy: an Empowered Strike that kills its target instantly re-arms - your very next landed hit is already Empowered",
                    TomeCategory.WEAPON, List.of(), 3, Tome.TomeApplyEffect.NONE, "broadsword"),

            // Sixth WEAPON-category Tome - Gravechain's branch: merges with the weapon's own
            // every-4th-hit Gather trigger rather than adding a separate proc-chain, same
            // pattern as Bleeding Edge/Sundering Wake. Stack 1: gathered mobs (and the primary
            // target) are also rooted with a brief near-Slowness, so the pile doesn't
            // immediately scatter. Stack 2: the Gather threshold drops to every 3rd landed hit,
            // same lowered-interval pattern Bleeding Edge uses. See CombatStats/AutoAttackSystem#meleeHook.
            new Tome("grave_anchor", "Grave Anchor",
                    "Gravechain's Gather also roots every mob it pulls in with a brief Slowness. 2nd copy: Gather triggers every 3rd hit instead of every 4th. 3rd copy: killing a rooted mob snaps the chain down harder, refreshing the root on every other mob still in that same pile",
                    TomeCategory.WEAPON, List.of(), 3, Tome.TomeApplyEffect.NONE, "gravechain"),

            // Seventh WEAPON-category Tome - Spectral Sickles' branch: unlike the other weapon
            // Tomes above, Orbit has no existing gated hit/combo cadence to merge with at all
            // (see WeaponMoveset#ORBIT - it ticks unconditionally, with no windup or cooldown),
            // so this originates its own mechanic from scratch: a decaying hit-streak stack
            // that grows on every landed blade hit (any target) and decays if the blades go
            // quiet for a couple seconds. Stack 1: each stack held adds +8% damage to the next
            // landed hit, up to +32% at 4 stacks (the 5th hit lands at max). Stack 2: at max
            // stacks, the blades' effective contact radius also grows, catching targets that
            // would otherwise pass just outside the ring. See CombatStats/OrbitWeaponSystem.
            new Tome("widening_gyre", "Widening Gyre",
                    "Spectral Sickles' blades hit harder the longer they've been landing hits, up to +32%. 2nd copy: at max stacks, the blades' reach also grows. 3rd copy: a kill at max stacks earns one bonus stack beyond the cap, pushing the blades to +40% until it decays",
                    TomeCategory.WEAPON, List.of(), 3, Tome.TomeApplyEffect.NONE, "spectral_sickles"),

            // Eighth and final WEAPON-category Tome - Ashen Beacon's branch: merges with the
            // weapon's own beacon-replace mechanic rather than adding a separate proc-chain.
            // Stack 1: a beacon that's replaced (by planting a new one) or expires naturally
            // leaves behind a weaker, shorter-lived residual Ember at its old position instead
            // of just vanishing - so relocating the main beacon doesn't fully give up the old
            // ground. Stack 2: an Ember that burns out on its own (not cut short by a second
            // replace) detonates once more in a bigger final burst before it's gone for good.
            // See CombatStats/TotemWeaponSystem.
            new Tome("undying_embers", "Undying Embers",
                    "A replaced or expired Ashen Beacon leaves behind a weaker Ember at its old spot. 2nd copy: an Ember that burns out on its own detonates in one final, bigger burst. 3rd copy: any beacon pulse that lands a kill immediately plants a fresh Ember at the kill site",
                    TomeCategory.WEAPON, List.of(), 3, Tome.TomeApplyEffect.NONE, "ashen_beacon"),

            // Ninth WEAPON-category Tome - Arcane Staff's second branch, inspired by SlopPack's Golden
            // Shortbow "Piercing Laser" (not ported verbatim: that version was a manual click-combo
            // spell). Here it is a passive, cooldown-driven beam: every LASER_COOLDOWN the staff
            // marks the nearest hostile, charges for half a second (a visible lane warns of it),
            // then fires an instant 26-block beam that hits everything on the line once and
            // executes anything under 30% health. Stack 2 shortens the cooldown, stack 3 lets the
            // beam pass through and chain to a second lane off the last target. See AutoAttackSystem.
            new Tome("piercing_laser", "Piercing Laser",
                    "Every 9s the Arcane Staff charges a beam that pierces every foe in a 26-block line and executes anything under 30% health. 2nd copy: the beam recharges 30% faster. 3rd copy: the beam forks a second lane off its last target.",
                    TomeCategory.WEAPON, List.of(), 3, Tome.TomeApplyEffect.NONE, "arcane_staff"),

            // Tenth WEAPON-category Tome - Hunting Bow's second branch: SlopPack's "Spin Barrage",
            // rebuilt so it costs no entities. The original overwrote the player's velocity every tick
            // and spawned ~53 real arrows per cast; this fires VIRTUAL sweep bolts (see
            // TrackedProjectiles.launchSweep) that stop against any block and leave nothing behind.
            // Every SPIN_COOLDOWN the bow fires rings of bolts outward for two seconds while the player
            // keeps full control of movement. Stack 2 adds a second offset ring, stack 3 doubles
            // the bolts per ring. See AutoAttackSystem.
            new Tome("spin_barrage", "Spin Barrage",
                    "Every 14s the Hunting Bow unleashes a two-second spiral of bolts in all directions. You keep moving freely and the bolts vanish on impact. 2nd copy: a second counter-rotating spiral. 3rd copy: twice as many bolts per ring.",
                    TomeCategory.WEAPON, List.of(), 3, Tome.TomeApplyEffect.NONE, "hunting_bow")
    );

    private TomePool() {}

    public static Tome byId(String id) {
        for (Tome tome : ALL) {
            if (tome.id().equals(id)) {
                return tome;
            }
        }
        return null;
    }

    private static Tome statTome(String id, String displayName, String description, List<SynergyTag> tags,
                                  net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                  double amountPerStack, AttributeModifier.Operation operation, boolean healOnApply) {
        Tome.TomeApplyEffect effect = (player, stackIndex) -> {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) {
                return;
            }
            Identifier modifierId = Identifier.fromNamespaceAndPath(EmberfallMod.MOD_ID,
                    "tome_" + id + "_" + stackIndex + "_" + player.getUUID());
            double amount = stackValue(amountPerStack, stackIndex,
                    attribute.equals(Attributes.MOVEMENT_SPEED) ? SPEED_DECAY : STAT_DECAY);
            instance.addPermanentModifier(new AttributeModifier(modifierId, amount, operation));
            // Stat Tomes are meant to last only for the run (design doc
            // 10.1 / class javadoc) - register the matching removal so
            // PlayerBuild.clear() actually un-does this when the run ends,
            // instead of it living on the player entity forever and
            // colliding with itself on a repeat pick in a future run.
            PlayerBuild.addCleanup(player, p -> {
                AttributeInstance liveInstance = p.getAttribute(attribute);
                if (liveInstance != null) {
                    liveInstance.removeModifier(modifierId);
                }
            });
            if (healOnApply) {
                player.heal((float) amount);
            }
        };
        return new Tome(id, displayName, description, TomeCategory.PASSIVE, tags, STAT_TOME_MAX_STACKS, effect);
    }

    /** Stat tomes have no practical cap: each extra stack is worth {@link #STAT_DECAY} of the one before, so the total flattens out. */
    public static final int STAT_TOME_MAX_STACKS = 99;
    public static final double STAT_DECAY = 0.85;
    /** Movement speed is the one stat that makes a run trivial when it runs away, so its tomes fade faster (combined limit +118%). */
    public static final double SPEED_DECAY = 0.75;

    /** Scales a series so its FIRST FIVE stacks add up to exactly five of the old flat stacks (the old cap). */
    public static double scaleFor(double decay) {
        return 5.0 * (1.0 - decay) / (1.0 - Math.pow(decay, 5));
    }

    /** The value of the n-th stack (1-based) of a stat tome whose old flat value was {@code flat}. Pure maths, unit-checked. */
    public static double stackValue(double flat, int stackIndex, double decay) {
        return flat * scaleFor(decay) * Math.pow(decay, Math.max(0, stackIndex - 1));
    }

    /** A Summon-tagged Tome whose onApply spawns one real companion per pick (see PlayerCompanions). */
    private static Tome summonTome(String id, String displayName, String description, int maxStacks,
                                    String companionName, boolean spectral) {
        Tome.TomeApplyEffect effect = (player, stackIndex) ->
                PlayerCompanions.spawnCompanion(player, companionName, spectral);
        return new Tome(id, displayName, description, TomeCategory.PASSIVE, List.of(SynergyTag.SUMMON), maxStacks, effect);
    }

}
