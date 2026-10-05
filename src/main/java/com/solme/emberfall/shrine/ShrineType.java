package com.solme.emberfall.shrine;

import com.solme.emberfall.entity.EliteHeads;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Design doc Section 8: the three shrine flavors a "shrine" marker can roll
 * into at run-start (see {@link ShrineManager#rollShrines}). Each is a small,
 * two-block pedestal (base block + a lit accent block) topped with a custom
 * minecraft-heads.com head on a floating {@code Display.ItemDisplay} (same
 * technique as the retired HydraBrain's crown) -
 * physically distinct per type without needing a resource pack, matching the
 * standing "customize freely, stay vanilla-compatible" preference.
 *
 * Heads verified live against minecraft-heads.com's public API (2026-09-27):
 * Golden Statue (Greed), Wither Idol (Curse), Demon Idol (Challenge) - each
 * value below decodes to a real textures.minecraft.net URL that returned
 * HTTP 200 before being hardcoded here.
 *
 * Design doc 8's Greed Shrine line promises "a guaranteed high-rarity Tome
 * offer" - this Tome system (Section 7) has no rarity tiers at all (checked:
 * the phrase appears nowhere else in the whole document, and {@code Tome}
 * has no rarity field). Rather than silently ignoring that clause or
 * inventing a whole unrequested rarity system, Greed/Challenge's "guaranteed
 * strong offer" is mapped onto the one real tier distinction the doc itself
 * draws in 7.2: WEAPON-category Tomes are the significant/exciting tier
 * PASSIVE/SYNERGY aren't. See {@link com.solme.emberfall.tome.TomeOfferGenerator#markNextOfferGuaranteedWeapon}.
 */
public enum ShrineType {
    GREED(
            "Greed Shrine",
            "Raises the danger for the rest of this run - in exchange, your next Tome offer is a guaranteed strong one.",
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmMzZGE0MGI3YjQzYWNlZmZhMzlkMzM4ZjEwOGRkMDgyMjk4YjA4ZDIzMDIzMGMyMDY3YmU4Y2RjMGZhNmRiZSJ9fX0=",
            "shrine_greed",
            Blocks.GOLD_BLOCK,
            Blocks.LANTERN
    ),
    CURSE(
            "Curse Shrine",
            "A run-long curse falls on you - in exchange, you get a free Tome pick right now, no level-up required.",
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjYwN2FiOTY5NjFlYzY0YzNkZjAzM2RhMDA2M2ZjYWE4NzA5NjFlNWE1Zjc5MjY5ZTc5YTY5NzdjMjA1ZWNjZCJ9fX0=",
            "shrine_curse",
            Blocks.SOUL_SAND,
            Blocks.SOUL_LANTERN
    ),
    CHALLENGE(
            "Challenge Shrine",
            "Wakes a short, brutal fight right here - clear it before time runs out for a guaranteed strong reward. No penalty for failing beyond missing the reward.",
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmVkOTc2NWIwMWZhNWU0MTY2Mjc2NTZhNmI3MTEwMDBhYzdkYTFhY2JkMjU4ZTRiOGE3ZTI4ZmEyYjlhMzQ2NiJ9fX0=",
            "shrine_challenge",
            Blocks.NETHER_BRICKS,
            Blocks.SHROOMLIGHT
    );

    private final String displayName;
    private final String description;
    private final String headTexture;
    private final String headLabel;
    private final Block baseBlock;
    private final Block accentBlock;

    ShrineType(String displayName, String description, String headTexture, String headLabel,
               Block baseBlock, Block accentBlock) {
        this.displayName = displayName;
        this.description = description;
        this.headTexture = headTexture;
        this.headLabel = headLabel;
        this.baseBlock = baseBlock;
        this.accentBlock = accentBlock;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    /** The pedestal's floor-level block (placed at the marker position minus one). */
    public Block baseBlock() {
        return baseBlock;
    }

    /** The lit block sitting on the pedestal, at the marker's own foot-height position. */
    public Block accentBlock() {
        return accentBlock;
    }

    /** A fresh custom-textured head ItemStack for this shrine's floating display prop. */
    public ItemStack headItem() {
        return EliteHeads.customHead(headTexture, headLabel);
    }
}
