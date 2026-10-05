package com.solme.emberfall.character;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The full Character roster: one Character per weapon (8). See {@link CharacterType}'s doc for the
 * history of why this started at 5, and for the "no cosmetic-only
 * stats" rule the passive picks below follow. Each entry deliberately
 * avoids reusing its own stat-spread attributes (MAX_HEALTH/MOVEMENT_SPEED)
 * for its passive, so the two fields read as genuinely separate bonuses
 * rather than the same lever pulled twice.
 */
public final class CharacterPool {
    public static final List<CharacterType> ALL = List.of(
            new CharacterType("vanguard", "The Vanguard",
                    "A dependable front-liner built to take a hit. Starts with the Broadsword.",
                    "broadsword",
                    0.20, -0.05,
                    Attributes.KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADD_VALUE, 0.20,
                    "+20% knockback resistance - stands their ground.",
                    true),

            new CharacterType("duelist", "The Duelist",
                    "Fast and fragile - live by hit-and-run. Starts with Twin Daggers.",
                    "twin_daggers",
                    -0.10, 0.15,
                    Attributes.ATTACK_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0.10,
                    "+10% attack speed - quick hands.",
                    true),

            new CharacterType("juggernaut", "The Juggernaut",
                    "Slow, heavy, and built to wade into a crowd. Starts with the War Halberd.",
                    "war_halberd",
                    0.35, -0.15,
                    Attributes.ARMOR, AttributeModifier.Operation.ADD_VALUE, 3.0,
                    "+3 armor - armored bulk.",
                    true),

            new CharacterType("ranger", "The Ranger",
                    "Keeps distance and punishes it. Starts with the Hunting Bow.",
                    "hunting_bow",
                    0.0, 0.10,
                    Attributes.ATTACK_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0.15,
                    "+15% attack speed - faster draws.",
                    true),

            new CharacterType("battlemage", "The Battlemage",
                    "Glass cannon with a ward against the big hits. Starts with the Arcane Staff.",
                    "arcane_staff",
                    -0.10, 0.05,
                    Attributes.ARMOR_TOUGHNESS, AttributeModifier.Operation.ADD_VALUE, 2.0,
                    "+2 armor toughness - an arcane ward against heavy blows.",
                    true),

            new CharacterType("gravedigger", "The Gravedigger",
                    "Hauls the dead and the living alike toward the grave. Starts with the Gravechain.",
                    "gravechain",
                    0.15, -0.05,
                    Attributes.ENTITY_INTERACTION_RANGE, AttributeModifier.Operation.ADD_VALUE, 0.75,
                    "+0.75 reach - the chain finds targets a little further out.",
                    true),

            new CharacterType("reaper", "The Reaper",
                    "Never stops moving, never stops cutting. Starts with the Spectral Sickles.",
                    "spectral_sickles",
                    -0.05, 0.12,
                    Attributes.KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADD_VALUE, 0.10,
                    "+10% knockback resistance - keeps the blades turning through a scrum.",
                    true),

            new CharacterType("emberwarden", "The Emberwarden",
                    "Plants a fire and lets the horde come to it. Starts with the Ashen Beacon.",
                    "ashen_beacon",
                    0.10, 0.0,
                    Attributes.ARMOR, AttributeModifier.Operation.ADD_VALUE, 2.0,
                    "+2 armor - warmed by the fire they tend.",
                    true)
    );

    private static final Map<String, CharacterType> BY_ID =
            ALL.stream().collect(Collectors.toMap(CharacterType::id, c -> c));

    private CharacterPool() {}

    public static CharacterType byId(String id) {
        return BY_ID.get(id);
    }

    public static List<CharacterType> defaultUnlocked() {
        return ALL.stream().filter(CharacterType::unlockedByDefault).collect(Collectors.toList());
    }

    /** The Character auto-assigned to anyone who has never explicitly picked one. */
    public static CharacterType fallback() {
        return ALL.get(0);
    }
}
