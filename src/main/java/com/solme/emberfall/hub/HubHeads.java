package com.solme.emberfall.hub;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Player-head skins for the eight character busts, keyed by {@code CharacterType#id()}.
 *
 * Every entry was taken from the minecraft-heads.com database (category "humans") and checked in
 * two ways before being committed: the base64 value decodes to a Mojang texture URL, and that URL
 * answers HTTP 200 from textures.minecraft.net. Franchise-tagged heads (Clash of Clans, Star Wars,
 * Assassin's Creed and so on) and heads from other servers' NPC sets were deliberately skipped, so
 * these are generic fantasy archetypes. No resource pack is needed: a player head renders its skin
 * from the profile texture in vanilla.
 *
 * The {@code uuid} is the database's own id for the head; it is used as the profile id so the same
 * head always stacks and renders identically.
 */
public final class HubHeads {
    /** One head: its database name, profile uuid and base64 texture value. */
    public record Head(String name, String uuid, String textureValue) {}

    private static final Map<String, Head> BY_CHARACTER = build();

    private HubHeads() {}

    /** The head for a character id, or null if none is registered. */
    public static Head forCharacter(String characterId) {
        return BY_CHARACTER.get(characterId);
    }

    private static Map<String, Head> build() {
        Map<String, Head> m = new LinkedHashMap<>();
        m.put("vanguard", new Head("Black Knight", "8a5020df-1921-45a9-9346-3e376b122865",
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNmVkMzExOTU0MDJmODNhMDI5ZGNiYzA5MDUzNmEyNDRkOGNiMjU1MmE4OWIzZGZmZWZmM2Y5ZjUzZjRlNTIzOCJ9fX0="));
        m.put("duelist", new Head("Assassin", "59b4d23c-10bf-406b-9e34-c07226bfc1b3",
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWJhMWQ1Mjk5N2UwYzIxOTRhNmE5YjU4NzNkZDUzNjNmNTk5OWFhZjIwZWUzOTFjMWM1ZmM4M2JkM2EwMGNiMSJ9fX0="));
        m.put("juggernaut", new Head("Gladiator", "01d4dd28-7097-4635-88fa-c3b857cce89b",
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTU1MmI4MDA2ZjQ5ZDE2M2E3OTVlNDdiNjc1MDhiYWU1ODAxN2EzNjgyZjY2N2Y4Y2UwZjA2YjA0NTIyYzRmMSJ9fX0="));
        m.put("gravedigger", new Head("Man with Goat Skull Mask", "e20fb72a-b031-46ce-a74b-44e578670ecb",
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjAzMGM0NjEyZDUwNWQ0ZDM5ZDcwNDg2OTFiNGQ2YjZiN2EyZDJmYWYwZjNiNWQyYzk4NTQ5NDNjOGRlOTM0ZSJ9fX0="));
        m.put("ranger", new Head("Archer", "f5d01012-0362-48d7-be04-1f8721d90f86",
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzFlZWVhMWRkMTY5YTBmMTAzZGVlMmVkOWMzODliM2FkZDE4ZmQ4YmI4ZDJjZGQwODdlMDQ1OGNjOTYwMmIxOSJ9fX0="));
        m.put("battlemage", new Head("Mage", "42ad6b2b-dc96-4154-9ce7-9fc2362c2817",
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzg1YmE1MjkxZjljZWZjNWIzNDU1NGY5NjY4ZjcxNWQzZGE4ZDI1ZWY2NjgyMTY1ZGVmZDBmYTBjZTRiYThhYyJ9fX0="));
        m.put("reaper", new Head("Cultist", "10ba8f14-a077-4900-a988-775ea8fca125",
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZThhZGU2NjJiNzU2YjI1MTI1MzQ5N2E4YzNhMDUzZTFjN2Q1YzM1ODU4NjhkNmYwYjJkNjVkNDJlY2ZkZWI5OCJ9fX0="));
        m.put("emberwarden", new Head("Blacksmith", "524c4cc6-4e7b-413e-a014-0dfaa0bd809d",
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMThmNzk0ZTRhNTY0OThkOTI1NzIwODAzYjkyMDUxODYzZWRlMjA0NTBlYTQ1MzEwMmI0N2ZhM2U5Y2M0ZWIwNCJ9fX0="));
        return java.util.Collections.unmodifiableMap(m);
    }
}
