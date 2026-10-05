package com.solme.emberfall.item;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The full weapon roster (design doc: "distinct weapon types, each with a
 * unique moveset"). 8 weapons across four moveset families:
 *   - Broadsword: MELEE_SINGLE, the free default - a plain, well-rounded
 *     baseline every player starts with (identical feel to the pre-weapon-
 *     system auto-attack, just now a real Item instead of whatever vanilla
 *     item happened to be in hand).
 *   - Twin Daggers: MELEE_DUAL - fast and light, every 4th hit is a freebie.
 *   - War Halberd: MELEE_CLEAVE - slow and heavy, hits up to 3 targets at once,
 *     Sundering each for stacking armor shred.
 *   - Gravechain: MELEE_HOOK - every hit pulls the target in, every 4th hit
 *     gathers a pile of nearby foes - a gap-closer/crowd-control weapon.
 *   - Hunting Bow: RANGED_SINGLE - long range, moderate single-target bolt damage.
 *   - Arcane Staff: RANGED_AOE - same bolt, but every 4th shot is a nova.
 *   - Spectral Sickles: ORBIT - a standing contact-damage aura, no windup/cooldown.
 *   - Ashen Beacon: TOTEM - plants a pulsing AoE beacon instead of striking.
 * Real vanilla sword numbers (3.0 dmg / -2.4 speed, verified straight from
 * Items.java's own IRON_SWORD/DIAMOND_SWORD registration in the 1.21.11
 * jar) anchor Broadsword; the other 7 are deliberate deltas off that
 * baseline, not invented from nothing.
 */
public final class WeaponPool {
    public static final List<WeaponType> ALL = List.of(
            new WeaponType("broadsword", "Broadsword",
                    "A dependable blade whose sweeping arc widens and repeats as it levels. Ultimate, Sunbrand Sweep: a full spin that hits every foe in a wide radius.",
                    WeaponMoveset.MELEE_SINGLE,
                    3.0F, -2.4F, 0.0F, 0.0F, 3.0, 0L, true),

            new WeaponType("twin_daggers", "Twin Daggers",
                    "Fast strikes that Rend, stacking bleed up to 4x, and a flurry that grows with level. Ultimate, Phantom Blades: two ghost daggers follow you and dart from foe to foe.",
                    WeaponMoveset.MELEE_DUAL,
                    1.0F, -1.0F, 0.0F, 0.0F, 3.0, 100L, false),

            new WeaponType("war_halberd", "War Halberd",
                    "Slow, heavy swings that cleave nearby foes, Sunder them, and chop again as it levels. Ultimate, War Slam: a shock wave with the widest reach of any weapon.",
                    WeaponMoveset.MELEE_CLEAVE,
                    5.0F, -3.2F, 0.0F, 0.0F, 3.5, 150L, false),

            new WeaponType("hunting_bow", "Hunting Bow",
                    "Fires precise bolts from range, more of them per shot as it levels; every 4th shot pierces every foe in its path. Ultimate, Storm of Arrows: a volley rains down.",
                    WeaponMoveset.RANGED_SINGLE,
                    0.0F, 0.0F, 4.0F, -2.0F, 10.0, 150L, false),

            new WeaponType("arcane_staff", "Arcane Staff",
                    "Ranged bolts that multiply as it levels; every 4th shot detonates in a nova. Ultimate, Starfall: stars fall over the next seconds.",
                    WeaponMoveset.RANGED_AOE,
                    0.0F, 0.0F, 2.5F, -2.6F, 9.0, 200L, false),

            new WeaponType("gravechain", "Gravechain",
                    "A hooked chain. Every hit yanks the target in; every 4th hit drags nearby foes into a pile. Ultimate, Grave Legion: foes that die in the vortex rise and fight for you.",
                    WeaponMoveset.MELEE_HOOK,
                    2.0F, -2.0F, 0.0F, 0.0F, 4.5, 175L, false),

            new WeaponType("spectral_sickles", "Spectral Sickles",
                    "Spectral blades circle you in a widening ring that cuts anything inside it. Ultimate, Reaper's Rite: they drag every foe into a pile, then slice it apart and break.",
                    WeaponMoveset.ORBIT,
                    2.0F, 0.0F, 0.0F, 0.0F, 2.2, 175L, false),

            new WeaponType("ashen_beacon", "Ashen Beacon",
                    "Plants a beacon whose living flames hunt the thickest group and pull foes in, more flames as it levels. Ultimate, Pyre Nova: a floating lantern erupts in fire.",
                    WeaponMoveset.TOTEM,
                    0.0F, 0.0F, 3.0F, -2.2F, 8.0, 200L, false)
    );

    /** The player-facing name of each weapon's ultimate, in one place so the descriptions and the "it fired" message agree. */
    private static final Map<String, String> ULTIMATE_NAMES = Map.of(
            "broadsword", "Sunbrand Sweep",
            "twin_daggers", "Phantom Blades",
            "war_halberd", "War Slam",
            "hunting_bow", "Storm of Arrows",
            "arcane_staff", "Starfall",
            "gravechain", "Grave Legion",
            "spectral_sickles", "Reaper's Rite",
            "ashen_beacon", "Pyre Nova");

    /** The ultimate's name for a weapon id, or null for an unknown id. */
    public static String ultimateName(String weaponId) {
        return ULTIMATE_NAMES.get(weaponId);
    }

    private static final Map<String, WeaponType> BY_ID =
            ALL.stream().collect(Collectors.toMap(WeaponType::id, w -> w));

    private WeaponPool() {}

    public static WeaponType byId(String id) {
        return BY_ID.get(id);
    }

    public static List<WeaponType> defaultUnlocked() {
        return ALL.stream().filter(WeaponType::unlockedByDefault).collect(Collectors.toList());
    }
}
