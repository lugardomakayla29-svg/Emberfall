package com.solme.emberfall.relic;

import java.util.ArrayList;
import java.util.List;

/**
 * The two lists a chest reveal scrolls through: every tier label and every relic name. {@link ChestReveal#build} THROWS when the true answer is not in its
 * pool, so a screen built from these lists can never be asked for an answer outside them, as long as the lists are built from the REAL catalogue.
 *
 * They are read from {@link RelicRarity#values()} and {@link RelicPool#all()}, never from text, so a relic added with {@code add(...)} OR
 * {@code gated(...)} is in the list the moment it is registered. (A search for {@code add("id", "name"} alone finds 21 of the 24 relics and misses the three
 * gated ones; see docs/review_chest_reveal_wiring.md.)
 *
 * Pure: no Minecraft types, so a check can run it without the game jar. Every list is a fresh copy, in a fixed order (catalogue order), so two calls agree.
 */
public final class ChestRevealPools {
    private ChestRevealPools() {}

    /** Every tier label the server can send, in rarity order (the order of {@link RelicRarity#values()}). */
    public static List<String> tiers() {
        List<String> out = new ArrayList<>();
        for (RelicRarity r : RelicRarity.values()) {
            out.add(r.label());
        }
        return out;
    }

    /** Every relic name the server can send, in catalogue order. Gated relics are included: a reveal can name one once it is unlocked. */
    public static List<String> items() {
        List<String> out = new ArrayList<>();
        for (Relic r : RelicPool.all()) {
            out.add(r.name());
        }
        return out;
    }
}
