package com.solme.emberfall.entity;

import com.google.common.collect.HashMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.UUID;

/**
 * Real custom head textures for the Corrupted archetypes' helmet slot,
 * sourced live from minecraft-heads.com's public API (2026-09-26) rather
 * than reusing a vanilla mob head or leaving the helmet slot empty - per
 * the user's explicit ask to use that site for any custom heads this pass
 * needs. Each value below is the exact base64 "textures" profile blob the
 * API returned for that named head (verified to decode to a real
 * textures.minecraft.net URL before being hardcoded here), keyed to which
 * archetype it dresses:
 *
 * <ul>
 *   <li>Magma Berserker  - Cinderbrand Reaver (fire/rage melee theme)</li>
 *   <li>Skeleton Sniper  - Blightfeather Marksman (ranged archer theme)</li>
 *   <li>Dark Mage        - Umbral Magus (shadow caster/summoner theme)</li>
 *   <li>Molten Golem     - Corrupted Sentinel (giant molten tank theme)</li>
 *   <li>Necromancer      - Bonecaller Necromancer, base phase</li>
 *   <li>Revenant Horror (enraged) - Bonecaller's Charnel Warden ultimate phase</li>
 * </ul>
 *
 * This is a decorative helmet-item substitute for SlopPack's full
 * player-disguise NPC framework (ZNPCsPlus + LibsDisguises), which Fabric
 * has no equivalent of - the mob itself is still its real vanilla base
 * entity model, just wearing a custom-faced head.
 *
 * {@link #customHead(String, String)} is the public entry point other
 * classes should use for any further one-off minecraft-heads.com textures
 * (e.g. the retired HydraHead's dragon head) - it's the exact same verified
 * GameProfile/PropertyMap plumbing below, just exposed instead of hidden
 * behind this class's own named per-archetype constants.
 */
public final class EliteHeads {
    private EliteHeads() {}

    private static final String MAGMA_BERSERKER =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDkxYmVlMjMwNWI3Y2RiMmE3ZDQ1MjAwMTM2NzhjYWIyYTAyMmY1YzhiNWRlOWFiNzlhZDAyZTMyNmNmZWFlZiJ9fX0=";
    private static final String SKELETON_SNIPER =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjE4YzA3MWYwODBkYmE1MGE2MmE2MjYzZmY3MjRlZGMxNTdjZTRmYjQ4ODNjY2VmZjI0OTFkNWJiZGU4MzBjMSJ9fX0=";
    private static final String DARK_MAGE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODdmZDlkYjBjYThiY2I5NWJmYWI0NmZjM2VlYjYzMTBmNjUyZDRhMjY4ZjQ3ZGIxOWJjYTI3ZjY3NzM5YjM5MiJ9fX0=";
    private static final String MOLTEN_GOLEM =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTExNGRiZDJiMGNjZjcyNDRiM2ZjYzI1ZWY0Y2RjYzBhNTYwYTQyOGU5OTNkNGU3YTI4ZGIzZGRhYjNmOGVlOSJ9fX0=";
    private static final String NECROMANCER =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzJlNmU0NTEwMGQzM2RjMDcyMDE4YzU1OGFiNDkyNTU1ZGE1NDRkYjJjNDBkNjRhMjY2ZTJlNTlkNzUwZjY0NiJ9fX0=";
    private static final String REVENANT_HORROR_ENRAGED =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmE3YmIzNDQ3MTUwOGI0YjIwY2IzNWMzN2ExZGQ2ZThhZmE3ZjJkNTM4ODgyMjEzODgzN2NhZjU5YzIxOTVkOCJ9fX0=";

    // 2026-09-28 elite pass: minecraft-heads.com textures (each verified to decode to a live textures.minecraft.net URL, HTTP 200).
    private static final String PLAGUED_ZOMBIE = // head #75669 "Plagued Zombie"
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODQyNzQzNWYxZWNkZTY3Njg4Y2Q3YmFhZDllZjBmZDViYjM4NDk3NmU4MTQyZTY0NGY5OGNlNzRkNWQwMjZiNiJ9fX0=";
    private static final String PLAGUE_KNIGHT = // head #112014 "Plague Knight"
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjE4MzAzYzgwOTJiYmVhOGY2MGM5ODMwMzU4MzE2NjI5ODlkODhlMGJkMDMxZTUzYzJjMmJiMmY2ZTcxZGZhZSJ9fX0=";
    private static final String BROOD_SPIDER_HEAD = // head #48198 "Spider" (user-chosen)
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWY3ZTgyNDQ2ZmFiMWU0MTU3N2JhNzBhYjQwZTI5MGVmODQxYzI0NTIzMzAxMWYzOTQ1OWFjNmY4NTJjODMzMSJ9fX0=";
    private static final String SPIDER_EGG_SAC = // head #45040 "Spider Egg Sac"
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTI4NmE2Mjg4NjRlZmVjNzZkMjFmMWJmYjg0ZDE4MDliMzAyZGVhYjcyOGI4ZGFiNmJlODA0NjdiN2U2ZmNlOCJ9fX0=";

    // 2026-10-10 owner-chosen fodder heads (his /give commands, base64 copied unchanged; each decodes to a textures.minecraft.net URL, checked by FodderHeadCheck).
    static final String UNDEAD_KNIGHT = // mcheads.ru #46697 "Undead Knight": the Horde Shieldbearer
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWVmOTA0YzY2ZjRjZDM1N2ViODBhMWU0MDU3ODNmNTIxZDI4NDUwMzQzNWFlZGU2MDNjODlkYjE1ZTY4NTcwOSJ9fX0=";
    static final String ELITE_ZOMBIE = // minecraft-heads #60468 "Elite Zombie": the Horde Charger
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2JhNzU3YWEzYmMxOTIxMmVlZTQ0YzU2YmQwNzdlY2NmOTlmZWJlMGZmNmY2Y2M4NWFlMTJkOWQyMTVkYTA2NCJ9fX0=";
    static final String ZOMBIE_TIER_2 = // minecraft-heads #60474 "Zombie Tier 2": the Horde Spitter
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjUyM2VhNzczZTAxY2NjNWMwZjdjYWRkM2ZiODhiMmI3NTFmMzFjMjc4OTllZjAyNmU2NDdlODcyY2FlNDRlNSJ9fX0=";

    public static ItemStack undeadKnightHead() { return skull(UNDEAD_KNIGHT, "undead_knight"); }
    public static ItemStack eliteZombieHead() { return skull(ELITE_ZOMBIE, "elite_zombie"); }
    public static ItemStack zombieTier2Head() { return skull(ZOMBIE_TIER_2, "zombie_tier2"); }

    /** Puts a fodder head on a mob and makes sure it can never drop (a worn head must not become loot). */
    public static void wear(net.minecraft.world.entity.Mob mob, ItemStack head) {
        mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, head);
        mob.setDropChance(net.minecraft.world.entity.EquipmentSlot.HEAD, 0.0F);
    }

    public static ItemStack plaguedZombieHead() { return skull(PLAGUED_ZOMBIE, "plague_colossus"); }
    public static ItemStack plagueKnightHead() { return skull(PLAGUE_KNIGHT, "plague_knight"); }
    public static ItemStack broodSpiderHead() { return skull(BROOD_SPIDER_HEAD, "brood_head"); }
    public static ItemStack spiderEggSacHead() { return skull(SPIDER_EGG_SAC, "brood_sac"); }

    public static ItemStack magmaBerserkerHead() { return skull(MAGMA_BERSERKER, "magma_berserker"); }
    public static ItemStack skeletonSniperHead() { return skull(SKELETON_SNIPER, "skeleton_sniper"); }
    public static ItemStack darkMageHead() { return skull(DARK_MAGE, "dark_mage"); }
    public static ItemStack moltenGolemHead() { return skull(MOLTEN_GOLEM, "molten_golem"); }
    public static ItemStack necromancerHead() { return skull(NECROMANCER, "necromancer"); }
    public static ItemStack revenantHorrorHead() { return skull(REVENANT_HORROR_ENRAGED, "revenant_horror"); }

    /** Public entry point for any other class that needs a one-off custom minecraft-heads.com texture. */
    public static ItemStack customHead(String base64Textures, String label) {
        return skull(base64Textures, label);
    }

    public static ItemStack skull(String base64Textures, String label) {
        // GameProfile names are validated against the vanilla username rules
        // (<=16 chars) by the equipment codec - anything longer fails to
        // encode/serialize (crashes the client connection). Caller-supplied
        // labels must already fit; assert here so a future too-long label
        // fails loudly at dev-time instead of crashing a real client.
        if (label.length() > 16) {
            throw new IllegalArgumentException("elite head label '" + label + "' exceeds the 16-char GameProfile name limit");
        }
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        // GameProfile(UUID, String) hands back an immutable empty PropertyMap,
        // and PropertyMap's own constructor ALWAYS defensively copies whatever
        // multimap it's given into an ImmutableMultimap (verified via bytecode -
        // there is no mutable PropertyMap in this authlib version). So the
        // property has to go into a plain mutable Multimap first, then get
        // wrapped - the PropertyMap itself is built already-populated.
        HashMultimap<String, Property> backing = HashMultimap.create();
        backing.put("textures", new Property("textures", base64Textures));
        // No "elite_" prefix here - GameProfile names are capped at 16 chars
        // (see the length check above) and every label is already <=16 on its own.
        GameProfile profile = new GameProfile(UUID.randomUUID(), label, new PropertyMap(backing));
        stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
        return stack;
    }
}
