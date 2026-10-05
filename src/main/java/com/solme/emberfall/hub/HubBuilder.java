package com.solme.emberfall.hub;

import com.mojang.math.Transformation;
import com.solme.emberfall.character.CharacterPool;
import com.solme.emberfall.character.CharacterType;
import com.solme.emberfall.entity.EliteHeads;
import com.solme.emberfall.item.WeaponPool;
import com.solme.emberfall.item.WeaponType;
import com.solme.emberfall.world.BlockJournal;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds and removes the physical hub around an Ember Hearth.
 *
 * Nothing here reshapes terrain. {@link HubSiteAnalyzer} only accepts already-flat ground, so the
 * hub sits ON the surface: small pedestal blocks are placed on top of the grass, and everything else
 * is a display entity. Display entities have no AI and no per-tick cost, which keeps the hub free
 * even with many players around it.
 *
 * Every entity gets the tag {@link #tagFor(BlockPos)}, derived from the Hearth's position. Removal
 * and startup cleanup find the hub's entities by that tag, so there is no list that could be lost in
 * a crash and no orphan can outlive its Hearth. Blocks go through a {@link BlockJournal} so the
 * exact previous state is restored on removal.
 */
public final class HubBuilder {
    /** Tag prefix on every hub entity; the suffix is the Hearth position. */
    public static final String TAG_PREFIX = "emberfall_hub_";

    /** Height above the ground at which a bust's head display sits (on top of a one-block pedestal). */
    private static final float BUST_HEAD_Y = 1.55F;
    /** Height of the hologram's anchor above the ground. */
    private static final float HOLOGRAM_Y = 2.55F;
    /** Display view-range multiplier (vanilla's base is 64 blocks, so 0.5 culls at about 32 blocks). */
    private static final float VIEW_RANGE = 0.5F;

    private HubBuilder() {}

    /** The entity tag that ties every hub entity to the Hearth at {@code hearth}. */
    public static String tagFor(BlockPos hearth) {
        return TAG_PREFIX + hearth.getX() + "_" + hearth.getY() + "_" + hearth.getZ();
    }

    /**
     * Builds the hub with the Hearth at {@code hearth} (which stands on the flat ground surface at
     * y - 1). Returns the number of entities spawned. The caller has already run the terrain check.
     */
    public static int build(ServerLevel level, BlockPos hearth, BlockJournal journal) {
        String tag = tagFor(hearth);
        int spawned = 0;

        // Character busts: a pedestal, a head, and a lore hologram above it.
        List<HubLayout.Spot> spots = HubLayout.bustSpots();
        List<CharacterType> roster = CharacterPool.ALL;
        for (int i = 0; i < spots.size() && i < roster.size(); i++) {
            HubLayout.Spot spot = spots.get(i);
            CharacterType character = roster.get(i);
            BlockPos base = hearth.offset(spot.dx(), 0, spot.dz());
            placePedestal(level, journal, base, Blocks.POLISHED_BLACKSTONE.defaultBlockState());

            HubHeads.Head head = HubHeads.forCharacter(character.id());
            if (head != null) {
                spawned += spawnHead(level, base, spot.yaw(), head.textureValue(), character.id(), tag) ? 1 : 0;
            }
            spawned += spawnHologram(level, base, bustLines(character), tag) ? 1 : 0;
            spawned += spawnHotspot(level, base, HubInteractions.ACT_BUST + character.id(), tag) ? 1 : 0;
        }

        // Shop keeper: a head on a pedestal beside the Hearth, labelled so players know to click it.
        HubLayout.Spot keeper = HubLayout.shopKeeperSpot();
        BlockPos keeperBase = hearth.offset(keeper.dx(), 0, keeper.dz());
        placePedestal(level, journal, keeperBase, Blocks.GILDED_BLACKSTONE.defaultBlockState());
        HubHeads.Head keeperHead = HubHeads.forCharacter("emberwarden");
        if (keeperHead != null) {
            spawned += spawnHead(level, keeperBase, keeper.yaw(), keeperHead.textureValue(), "shopkeeper", tag) ? 1 : 0;
        }
        spawned += spawnHologram(level, keeperBase, List.of(
                Component.literal("§6§lShop Keeper"),
                Component.literal("§7Right-click a weapon bust to choose,"),
                Component.literal("§7or right-click here to trade.")), tag) ? 1 : 0;
        spawned += spawnHotspot(level, keeperBase, HubInteractions.ACT_SHOP, tag) ? 1 : 0;

        // Departure plate: a single pressure plate on the opposite side of the Hearth.
        HubLayout.Spot depart = HubLayout.departureSpot();
        BlockPos plateBase = hearth.offset(depart.dx(), 0, depart.dz());
        // Placed directly on the existing ground: the hub never edits a block that was already there.
        journal.set(plateBase, com.solme.emberfall.block.ModBlocks.DEPARTURE_PLATE.defaultBlockState());
        spawned += spawnHologram(level, plateBase, List.of(
                Component.literal("§c§lExpedition Gate"),
                Component.literal("§7Right-click, then hold still,"),
                Component.literal("§7to begin an expedition.")), tag) ? 1 : 0;
        spawned += spawnHotspot(level, plateBase, HubInteractions.ACT_GATE, tag) ? 1 : 0;

        return spawned;
    }

    /** Removes every entity belonging to the Hearth at {@code hearth}. Returns how many were removed. */
    public static int removeEntities(ServerLevel level, BlockPos hearth) {
        String tag = tagFor(hearth);
        // The hub fits inside the 9x9 footprint and 6 blocks of height; a slightly larger box is safe
        // because only entities carrying this exact tag are touched.
        AABB box = new AABB(hearth).inflate(HubSiteAnalyzer.RADIUS + 2, 8, HubSiteAnalyzer.RADIUS + 2);
        List<Entity> found = new ArrayList<>(level.getEntities((Entity) null, box, e -> e.getTags().contains(tag)));
        for (Entity e : found) {
            e.discard();
        }
        return found.size();
    }

    private static void placePedestal(ServerLevel level, BlockJournal journal, BlockPos base, BlockState state) {
        // base is the air cell resting on top of the flat ground, so the pedestal is added ON the
        // surface and no existing block is replaced.
        journal.set(base, state);
    }

    private static List<Component> bustLines(CharacterType c) {
        WeaponType weapon = WeaponPool.byId(c.weaponId());
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("§6§l" + c.displayName()));
        lines.add(Component.literal("§7" + c.description()));
        if (weapon != null) {
            lines.add(Component.literal("§eWeapon: §f" + weapon.displayName()));
        }
        lines.add(Component.literal("§bPassive: §f" + c.passiveDescription()));
        lines.add(Component.literal("§a▶ Right-click the bust to choose"));
        return lines;
    }

    private static boolean spawnHead(ServerLevel level, BlockPos base, float yaw, String textureValue, String label, String tag) {
        ItemStack head = EliteHeads.skull(textureValue, label.length() > 16 ? label.substring(0, 16) : label);
        Display.ItemDisplay display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
        display.setPos(base.getX() + 0.5, base.getY() + BUST_HEAD_Y, base.getZ() + 0.5);
        display.setItemStack(head);
        display.setItemTransform(ItemDisplayContext.HEAD);
        display.setBillboardConstraints(Display.BillboardConstraints.FIXED);
        display.setViewRange(VIEW_RANGE);
        display.setTransformation(new Transformation(
                new Vector3f(0.0F, 0.0F, 0.0F),
                new Quaternionf().rotateY((float) Math.toRadians(180.0F - yaw)),
                new Vector3f(1.6F, 1.6F, 1.6F),
                new Quaternionf()));
        display.setNoGravity(true);
        display.setInvulnerable(true);
        display.addTag(tag);
        return level.addFreshEntity(display);
    }

    private static boolean spawnHologram(ServerLevel level, BlockPos base, List<Component> lines, String tag) {
        Display.TextDisplay text = new Display.TextDisplay(EntityType.TEXT_DISPLAY, level);
        text.setPos(base.getX() + 0.5, base.getY() + HOLOGRAM_Y, base.getZ() + 0.5);
        net.minecraft.network.chat.MutableComponent joined = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            joined.append(lines.get(i));
            if (i < lines.size() - 1) {
                joined.append(Component.literal("\n"));
            }
        }
        text.setText(joined);
        text.setLineWidth(160);
        text.setBackgroundColor(0x66000000);
        text.setBillboardConstraints(Display.BillboardConstraints.CENTER);
        text.setViewRange(VIEW_RANGE);
        text.setBrightnessOverride(Brightness.FULL_BRIGHT);
        text.setTransformation(new Transformation(
                new Vector3f(0.0F, 0.0F, 0.0F), new Quaternionf(), new Vector3f(0.7F, 0.7F, 0.7F), new Quaternionf()));
        text.setNoGravity(true);
        text.setInvulnerable(true);
        text.addTag(tag);
        return level.addFreshEntity(text);
    }

    /**
     * An invisible click target covering the pedestal and head. Displays have no hitbox, so this
     * {@link Interaction} is what a player's crosshair actually hits. It carries the hub tag (so
     * teardown removes it) and one action tag naming what a click does.
     */
    private static boolean spawnHotspot(ServerLevel level, BlockPos base, String actionTag, String tag) {
        Interaction hotspot = new Interaction(EntityType.INTERACTION, level);
        hotspot.setPos(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
        hotspot.setWidth(1.0F);
        hotspot.setHeight(2.4F);
        hotspot.setResponse(true);
        hotspot.setInvulnerable(true);
        hotspot.addTag(tag);
        hotspot.addTag(actionTag);
        return level.addFreshEntity(hotspot);
    }
}
