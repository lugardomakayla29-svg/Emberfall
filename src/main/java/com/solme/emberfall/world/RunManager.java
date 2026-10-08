package com.solme.emberfall.world;

import com.solme.emberfall.EmberfallMod;
import com.solme.emberfall.block.MarkerBlockEntity;
import com.solme.emberfall.character.CharacterEffects;
import com.solme.emberfall.character.CharacterType;
import com.solme.emberfall.character.PlayerCharacterSelection;
import com.solme.emberfall.item.PlayerWeapon;
import com.solme.emberfall.item.WeaponChoiceManager;
import com.solme.emberfall.item.WeaponPool;
import com.solme.emberfall.leveling.LevelingHandler;
import com.solme.emberfall.tome.CombatStats;
import com.solme.emberfall.tome.PlayerBuild;
import com.solme.emberfall.tome.PlayerTomeCharges;
import com.solme.emberfall.tome.TomeChoiceManager;
import com.solme.emberfall.shrine.ShrineManager;
import com.solme.emberfall.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Owns the grid of arena slots in the emberfall:expedition dimension. Each run
 * gets one slot: a fixed-size cell far enough from its neighbours that arenas
 * never overlap. Pasting a structure into a slot scans it for emberfall:marker
 * blocks (spawn points, shrines, chests, the player entry point, ...), records
 * their world positions, then strips the markers so they never appear in the
 * finished room. Tearing down clears the slot's blocks back to air and frees
 * the slot for reuse.
 *
 * v1 layout: slots are laid out along +X, spaced SLOT_SPACING apart, all at
 * a fixed Y in the void-flat expedition world. Good enough for one arena at a
 * time during development; a real allocator (2D grid, per-slot Y bands, etc.)
 * can replace allocateSlot()/originForSlot() later without touching callers.
 */
public final class RunManager {
    private static final int SLOT_SPACING = 256;
    private static final int BASE_Y = 64;

    private static final TreeSet<Integer> freeSlots = new TreeSet<>();
    private static int nextSlot = 0;
    private static final Map<Integer, ArenaInstance> active = new HashMap<>();
    private static final Map<UUID, Integer> playerSlots = new HashMap<>();

    private RunManager() {}

    private static int allocateSlot() {
        if (!freeSlots.isEmpty()) {
            int slot = freeSlots.pollFirst();
            return slot;
        }
        return nextSlot++;
    }

    private static void freeSlot(int slot) {
        freeSlots.add(slot);
    }

    public static BlockPos originForSlot(int slot) {
        return new BlockPos(slot * SLOT_SPACING, BASE_Y, 0);
    }

    /**
     * Pastes the named structure into a freshly allocated slot, scans it for
     * marker blocks, strips them, and returns the resulting ArenaInstance.
     * Throws IllegalStateException if the expedition level isn't loaded or the
     * structure can't be found.
     */
    public static ArenaInstance pasteArena(MinecraftServer server, Identifier structureId) {
        ServerLevel level = server.getLevel(Dimensions.EXPEDITION);
        if (level == null) {
            throw new IllegalStateException("emberfall:expedition dimension is not loaded");
        }

        StructureTemplate template = level.getStructureManager().getOrCreate(structureId);
        if (template.getSize().getX() == 0 && template.getSize().getY() == 0 && template.getSize().getZ() == 0) {
            throw new IllegalStateException("Structure " + structureId + " is empty or missing");
        }

        int slot = allocateSlot();
        BlockPos origin = originForSlot(slot);

        StructurePlaceSettings settings = new StructurePlaceSettings();
        template.placeInWorld(level, origin, origin, settings, level.getRandom(), 2);

        BoundingBox bounds = template.getBoundingBox(settings, origin);
        Map<String, List<BlockPos>> markers = scanAndStripMarkers(level, bounds);

        ArenaInstance instance = new ArenaInstance(slot, level, origin, bounds, markers, false);
        active.put(slot, instance);
        RunTelemetry.start(slot);
        ShrineManager.rollShrines(level, instance);

        EmberfallMod.LOGGER.info(
                "Pasted arena '{}' into slot {} at {} ({} marker types found)",
                structureId, slot, origin, markers.size());

        return instance;
    }

    public static int reserveMapSlot() {
        return allocateSlot();
    }

    /** Gives a reserved map slot back without starting a run (the map build failed or the player left while it built). */
    public static void releaseMapSlot(int slot) {
        freeSlot(slot);
    }

    /**
     * Starts a run on the STATIC expedition map in the expedition dimension. The map for this slot must already be built
     * ({@link MapManager}); the caller builds it first and calls this from the completion callback. Every marker list the
     * wave director and shrine code expect comes straight from the map data, so nothing is scanned or guessed.
     *
     * The arena counts as in-place for the journal and the mob systems (the map is permanent, only what the run itself
     * changes is reverted) and is flagged as a map so the circle guard owns it and the player is teleported in.
     */
    public static ArenaInstance startOnMap(ServerLevel level, int mapSlot) {
        StaticMap map = StaticMap.get();
        BlockPos origin = originForSlot(mapSlot);
        List<BlockPos> spawns = new ArrayList<>();
        for (int[] p : map.spawnPoints(4)) {
            spawns.add(origin.offset(p[0], p[1], p[2]));
        }
        List<BlockPos> shrines = new ArrayList<>();
        for (Object[] sp : map.shrineSpots()) {
            shrines.add(origin.offset((Integer) sp[1], 1, (Integer) sp[2]));
        }
        int[] e = map.entry();
        int[] b = map.boss();
        Map<String, List<BlockPos>> markers = new HashMap<>();
        markers.put("spawn_point", spawns);
        markers.put("shrine", shrines);
        markers.put("boss_spawn", List.of(origin.offset(b[0], 1, b[1])));
        markers.put("player_entry", List.of(origin.offset(e[0], 1, e[1])));

        int r = 96; // bounds a little wider than the floor so the circle guard's radius min(93, 96 - 2) is 93
        BoundingBox bounds = new BoundingBox(origin.getX() - r, origin.getY() - StaticMap.BASE_DEPTH, origin.getZ() - r,
                origin.getX() + r - 1, origin.getY() + 24, origin.getZ() + r - 1);
        ArenaInstance instance = new ArenaInstance(mapSlot, level, origin, bounds, markers, true, true);
        active.put(mapSlot, instance);
        instance.journal().persistTo(BlockJournal.journalDir(level.getServer()), "run_slot_" + mapSlot);
        RunTelemetry.start(mapSlot);
        com.solme.emberfall.shrine.MapShrines.spawnHotspots(level, mapSlot);
        com.solme.emberfall.relic.ChestManager.place(level, instance);
        EmberfallMod.LOGGER.info("Started MAP run in slot {} at {} ({} spawn points, {} shrine spots)",
                mapSlot, origin, spawns.size(), shrines.size());
        return instance;
    }

    private static Map<String, List<BlockPos>> scanAndStripMarkers(ServerLevel level, BoundingBox bounds) {
        Map<String, List<BlockPos>> markers = new HashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()),
                new BlockPos(bounds.maxX(), bounds.maxY(), bounds.maxZ()))) {
            if (level.getBlockState(pos).is(ModBlocks.MARKER)) {
                BlockEntity be = level.getBlockEntity(pos);
                String type = (be instanceof MarkerBlockEntity marker) ? marker.getMarkerType() : "";
                markers.computeIfAbsent(type, k -> new ArrayList<>()).add(pos.immutable());
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
        }
        return markers;
    }

    /**
     * The distinct levels that currently host a live run (the expedition dimension for a pasted debug
     * arena, the player's own dimension for an in-place run). Per-tick systems iterate these instead
     * of assuming a single fixed dimension. Empty (and allocation-free) when no run is active.
     */
    public static java.util.Collection<ServerLevel> activeLevels() {
        if (active.isEmpty()) {
            return java.util.List.of();
        }
        java.util.Set<ServerLevel> levels = new java.util.LinkedHashSet<>();
        for (ArenaInstance instance : active.values()) {
            levels.add(instance.level());
        }
        return levels;
    }

    /**
     * The journal of the in-place run whose bounds contain {@code pos} in {@code level}, or null when
     * the position is not inside a live in-place run (pasted debug arenas are wiped wholesale, so they
     * need no journal). Lets any block-writer opt in with one call, without knowing about runs.
     */
    public static BlockJournal journalFor(ServerLevel level, BlockPos pos) {
        for (ArenaInstance instance : active.values()) {
            if (instance.inPlace() && instance.level() == level && instance.bounds().isInside(pos)) {
                return instance.journal();
            }
        }
        return null;
    }

    /**
     * Drop-in for {@code level.setBlockAndUpdate}: if {@code pos} is inside a live in-place run the
     * original block (and block-entity data) is recorded first, so it is restored when the run ends.
     */
    public static void setBlockJournaled(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        BlockJournal journal = journalFor(level, pos);
        if (journal != null) {
            journal.set(pos, state);
        } else {
            level.setBlockAndUpdate(pos, state);
        }
    }

    /** Snapshot of every active arena, for systems that act on all runs (boundary, mob purge). */
    public static java.util.List<ArenaInstance> activeInstances() {
        return new ArrayList<>(active.values());
    }

    public static ArenaInstance getActive(int slot) {
        return active.get(slot);
    }

    /**
     * Ends a run and frees its slot.
     *
     * Pasted (debug) arenas: the whole bounding box is cleared back to air, as before.
     * In-place runs: NOTHING is bulk-cleared, since the box is the player's real world. Instead the
     * run's {@link BlockJournal} restores every block it touched to exactly what it was.
     * Both modes discard leftover non-player entities inside the bounds (horde mobs, boss rigs).
     */
    public static void teardownArena(MinecraftServer server, ArenaInstance instance) {
        ServerLevel level = instance.level();
        BoundingBox bounds = instance.bounds();
        discardNonPlayerEntitiesIn(level, bounds, instance.inPlace());
        if (instance.inPlace()) {
            instance.journal().restoreAll();
        } else {
            for (BlockPos pos : BlockPos.betweenClosed(
                    new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()),
                    new BlockPos(bounds.maxX(), bounds.maxY(), bounds.maxZ()))) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
        }
        active.remove(instance.slot());
        freeSlot(instance.slot());
        playerSlots.values().removeIf(slot -> slot.equals(instance.slot()));
        RunTelemetry.clear(instance.slot());
        ShrineManager.stop(instance.slot());
        com.solme.emberfall.shrine.RunModifiers.clear(instance.slot());
        com.solme.emberfall.shrine.MapShrines.stop(instance.slot(), instance.level());
        com.solme.emberfall.relic.ChestManager.clear(instance.slot());
        com.solme.emberfall.relic.MerchantManager.clear(instance.slot());
        com.solme.emberfall.wave.SwarmPortal.clear(instance.slot());
        EmberfallMod.LOGGER.info("Tore down {} arena in slot {}", instance.inPlace() ? "in-place" : "pasted", instance.slot());
    }

    /**
     * Discards every non-player entity still inside the torn-down bounds -
     * horde mobs, and critically any still-live boss rig (Section 6): a
     * HydraAnchor has setNoGravity(true), so an orphaned rig would never
     * self-resolve by falling into the void the way a leftover ground mob
     * eventually might. Blocks were already cleared above; this is the
     * entity-side half of the same "run teardown must not leak anything
     * into the shared expedition dimension" requirement (design doc 3.5).
     */
    private static void discardNonPlayerEntitiesIn(ServerLevel level, BoundingBox bounds, boolean onlyEmberfall) {
        AABB box = new AABB(
                bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX() + 1, bounds.maxY() + 1, bounds.maxZ() + 1);
        for (Entity entity : level.getEntitiesOfClass(Entity.class, box,
                e -> !(e instanceof ServerPlayer) && (!onlyEmberfall || isRunEntity(e)))) {
            entity.discard();
        }
    }

    /**
     * Chokepoint tagger. Mod mobs spawn many vanilla-typed helpers (item/block displays for models,
     * arrows, small fireballs, effect clouds, armor-stand rigs). Those have no emberfall namespace, so
     * in-place teardown could not otherwise tell them apart from a player's own property.
     * Rather than patch every one of the ~13 creation sites, tag at the single load event: any such
     * entity that appears inside a LIVE in-place run's bounds, in that run's level, gets the
     * {@code emberfall_run} tag. Types a player can own (item frames, paintings, dropped items) are
     * deliberately excluded.
     */
    /**
     * Server lifecycle safety net for in-place runs, which edit the player's REAL world:
     *  - on start: restore anything an interrupted previous session (crash, kill) left behind;
     *  - on stop: end every live run so its journal restores the world before it is saved.
     */
    public static void registerLifecycleSafety() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(
                BlockJournal::recoverLeftovers);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (ArenaInstance instance : new ArrayList<>(active.values())) {
                com.solme.emberfall.wave.WaveDirector.stop(instance.slot());
                for (ServerPlayer p : new ArrayList<>(server.getPlayerList().getPlayers())) {
                    Integer s = playerSlots.get(p.getUUID());
                    if (s != null && s == instance.slot()) {
                        leavePlayer(p);
                    }
                }
                teardownArena(server, instance);
            }
        });
    }

    public static void registerRunEntityTagger() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (active.isEmpty() || entity instanceof ServerPlayer) {
                return;
            }
            var type = entity.getType();
            boolean taggable = type == net.minecraft.world.entity.EntityType.ITEM_DISPLAY
                    || type == net.minecraft.world.entity.EntityType.BLOCK_DISPLAY
                    || type == net.minecraft.world.entity.EntityType.TEXT_DISPLAY
                    || type == net.minecraft.world.entity.EntityType.ARMOR_STAND
                    || type == net.minecraft.world.entity.EntityType.ARROW
                    || type == net.minecraft.world.entity.EntityType.SMALL_FIREBALL
                    || type == net.minecraft.world.entity.EntityType.AREA_EFFECT_CLOUD;
            if (!taggable) {
                return;
            }
            for (ArenaInstance instance : active.values()) {
                if (instance.inPlace() && instance.level() == world
                        && instance.bounds().isInside(entity.blockPosition())) {
                    entity.addTag("emberfall_run");
                    return;
                }
            }
        });
    }

    /**
     * True for entities the run itself created. In an in-place run the bounds are the player's REAL
     * world, so teardown must never delete their pets, villagers, dropped items or anything else that
     * merely happens to be standing there: only mod-namespaced mobs, plus the display/armor-stand
     * riders and shrine props those mobs and shrines create.
     */
    private static boolean isRunEntity(Entity e) {
        var key = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        if (key != null && key.getNamespace().equals(EmberfallMod.MOD_ID)) {
            return true;
        }
        return e.getTags().contains("emberfall_run");
    }

    /**
     * Teleports a player into an already-pasted arena's "player_entry"
     * marker and records them as belonging to that slot. Falls back to the
     * arena's origin if no player_entry marker was authored. Also resets
     * this run's Tome build and weapon state, and equips the starting
     * weapon fixed by the player's selected Character (design doc Section
     * 5 - see {@link com.solme.emberfall.character.CharacterEffects}).
     */
    public static void joinPlayer(ServerLevel level, ArenaInstance instance, ServerPlayer player) {
        // In-place runs happen exactly where the player is standing: never teleport them.
        // Only a pasted (debug) arena in the expedition dimension needs the player moved in.
        if (!instance.inPlace() || instance.isMap()) {
            List<BlockPos> entry = instance.markers("player_entry");
            BlockPos pos = entry.isEmpty() ? instance.origin() : entry.get(0);
            player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                    java.util.Set.of(), player.getYRot(), player.getXRot(), true);
        }
        playerSlots.put(player.getUUID(), instance.slot());
        LevelingHandler.enterRun(player);
        RunStats.clear(player); // a new run starts its kill count from zero
        com.solme.emberfall.music.RunMusic.start(player);
        PlayerBuild.reset(player);
        com.solme.emberfall.relic.PlayerRelics.reset(player);
        PlayerTomeCharges.reset(player);
        CombatStats.recompute(player);
        PlayerWeapon.reset(player);
        com.solme.emberfall.progression.UpgradeEffects.apply(player);
        // Design doc Section 5: the Character selected via /character
        // fixes the starting weapon outright - this replaces the earlier
        // free "pick any unlocked weapon" screen WeaponChoiceManager
        // used to open here. Anyone who never ran /character select gets
        // CharacterPool.fallback() automatically, so nobody is ever
        // blocked from starting a run.
        MinecraftServer server = level.getServer();
        CharacterType character = PlayerCharacterSelection.get(server).selectedOrFallback(player.getUUID());
        var weapon = WeaponPool.byId(character.weaponId());
        if (weapon != null) {
            PlayerWeapon.equip(player, weapon);
            PlayerWeapon.markPicked(player);
        }
        CharacterEffects.apply(player, character);
    }

    /** Removes the player's run-membership record (does not teleport them anywhere). */
    public static void leavePlayer(ServerPlayer player) {
        playerSlots.remove(player.getUUID());
        RunStats.clear(player);
        com.solme.emberfall.music.RunMusic.stop(player);
        ArenaBoundary.clear(player);
        LevelingHandler.exitRun(player);
        TomeChoiceManager.cancelPending(player);
        WeaponChoiceManager.cancelPending(player);
        CharacterEffects.clear(player);
        PlayerBuild.clear(player);
        com.solme.emberfall.relic.PlayerRelics.clear(player);
        com.solme.emberfall.relic.ChestManager.forgetReveal(player);
        PlayerTomeCharges.clear(player);
        com.solme.emberfall.tome.TomeOfferGenerator.clear(player);
        CombatStats.clear(player);
        PlayerWeapon.clear(player);
        com.solme.emberfall.combat.TimedAbilitySystem.clear(player);
        com.solme.emberfall.combat.OrbitWeaponSystem.clear(player);
        com.solme.emberfall.combat.TotemWeaponSystem.clear(player);
        com.solme.emberfall.combat.PhantomBladeSystem.clear(player);
        com.solme.emberfall.combat.ReapersRiteSystem.clear(player);
    }

    public static Integer slotOf(ServerPlayer player) {
        return playerSlots.get(player.getUUID());
    }

    /** Whether any player is still recorded as belonging to this slot. */
    public static boolean hasAnyPlayers(int slot) {
        return playerSlots.containsValue(slot);
    }

    /** How many players are recorded as belonging to this slot's run. 0 if none. Read-only; changes no state. */
    public static int partySize(int slot) {
        int n = 0;
        for (int s : playerSlots.values()) {
            if (s == slot) n++;
        }
        return n;
    }

    /**
     * Sends a system-chat message to every player currently recorded as
     * belonging to this slot's run. Added for the 2026-09-28 tier-escalation
     * work (see {@link com.solme.emberfall.wave.WaveDirector}) so a boss
     * spawning or a tier escalating is an announced, player-facing moment
     * rather than a silent internal flag flip - a rig appearing somewhere
     * in the arena with zero feedback is exactly the kind of half-baked
     * feel the owner has repeatedly flagged against.
     */
    public static void broadcastToSlot(MinecraftServer server, int slot, net.minecraft.network.chat.Component message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Integer playerSlot = playerSlots.get(player.getUUID());
            if (playerSlot != null && playerSlot == slot) {
                player.sendSystemMessage(message);
            }
        }
    }
}
