package com.solme.emberfall.relic;

import com.solme.emberfall.block.EmberChestBlock;
import com.solme.emberfall.block.ModBlocks;
import com.solme.emberfall.pickup.PickupSystem;
import com.solme.emberfall.world.ArenaInstance;
import com.solme.emberfall.world.RunManager;
import com.solme.emberfall.world.StaticMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Places a run's chests and opens them. ALL state is server side: a chest is a block with no inventory and no entity, plus a
 * record here, so a chest that is not in a live run's registry (a /give'd block, a leftover) does nothing when clicked.
 *
 * The decision of what an opening gives is {@link ChestOpening}; this class only feeds it live inputs and applies the result.
 * Every world write goes through the run's BlockJournal, so teardown restores the ground under each chest exactly.
 */
public final class ChestManager {
    private ChestManager() {}

    /** A click must come from this close to the chest (server-side reach check, independent of the client). */
    public static final double REACH = 6.0;
    /** Scoreboard tag the wave director puts on every elite it spawns. */
    public static final String ELITE_TAG = "emberfall_elite";
    private static final boolean TEST_MODE = Boolean.getBoolean("emberfall.testMode");

    private static final class RunChests {
        final Map<Long, ChestOpening.Kind> kinds = new HashMap<>();
        final Set<Long> opened = new HashSet<>();
        /** Free chests this run has already left behind (feeds {@link FreeChestRule}). */
        int freeGiven;
    }

    private static final Map<Integer, RunChests> RUNS = new HashMap<>();

    // ---- placement ----

    /** Scatters this run's chests on the map. Called once when a map run starts. */
    public static void place(ServerLevel level, ArenaInstance arena) {
        int slot = arena.slot();
        clear(slot);
        List<int[]> candidates = StaticMap.get().chestCandidates(3, 15, 80, 12);
        List<ChestPlan.Spot> plan = ChestPlan.plan(candidates, ChestPlan.PAID_CHESTS, ChestPlan.GOLD_CHESTS,
                new java.util.Random(level.getRandom().nextLong()));
        for (ChestPlan.Spot s : plan) {
            put(level, arena, arena.origin().offset(s.x(), s.y(), s.z()), s.kind());
        }
    }

    /** Puts one chest (also used for free chests dropped by elites, bosses and shrines). Returns false if the spot is taken. */
    public static boolean put(ServerLevel level, ArenaInstance arena, BlockPos pos, ChestOpening.Kind kind) {
        RunChests run = RUNS.computeIfAbsent(arena.slot(), k -> new RunChests());
        if (!level.getBlockState(pos).isAir() || run.kinds.containsKey(pos.asLong())) {
            if (TEST_MODE) {
                com.solme.emberfall.EmberfallMod.LOGGER.info("CHEST_TEST refused spot {} block={} dup={}", pos, level.getBlockState(pos).getBlock(), run.kinds.containsKey(pos.asLong()));
            }
            return false;
        }
        Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(level.getRandom());
        Block block = switch (kind) {
            case PAID -> ModBlocks.CHEST_PAID;
            case FREE -> ModBlocks.CHEST_FREE;
            case GOLD -> ModBlocks.CHEST_GOLD;
        };
        arena.journal().set(pos, block.defaultBlockState().setValue(EmberChestBlock.FACING, facing).setValue(EmberChestBlock.OPENED, false));
        run.kinds.put(pos.asLong(), kind);
        return true;
    }

    /** Forgets a run's chests. The blocks themselves are undone by the journal at teardown. */
    /** Free chests given so far this run. */
    public static int freeGiven(int slot) {
        RunChests r = RUNS.get(slot);
        return r == null ? 0 : r.freeGiven;
    }

    /**
     * Rolls {@link FreeChestRule} for this source and, on a hit, drops a FREE chest on the nearest free ground to {@code near}.
     * Returns true if a chest was placed. The counter only rises when a chest really stands, so a blocked spot never burns the cap.
     */
    public static boolean dropFree(ServerLevel level, ArenaInstance arena, BlockPos near, FreeChestRule.Source source) {
        int slot = arena.slot();
        RunChests run = RUNS.computeIfAbsent(slot, k -> new RunChests());
        if (TEST_MODE) {
            com.solme.emberfall.EmberfallMod.LOGGER.info("CHEST_TEST dropFree {} near {} given={} chance={}", source, near, run.freeGiven, FreeChestRule.chance(source, run.freeGiven));
        }
        if (!FreeChestRule.drops(source, run.freeGiven, level.getRandom().nextDouble())) {
            return false;
        }
        for (int r = 0; r <= 4; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    BlockPos p = groundAbove(level, near.offset(dx, 0, dz));
                    if (p == null) {
                        continue;
                    }
                    if (put(level, arena, p, ChestOpening.Kind.FREE)) {
                        run.freeGiven++;
                        com.solme.emberfall.pickup.Cue.play(level, "free_chest_appears", SoundEvents.VAULT_OPEN_SHUTTER, SoundSource.BLOCKS,
                                net.minecraft.world.phys.Vec3.atCenterOf(p), 1.2F, 1.1F);
                        if (TEST_MODE) {
                            com.solme.emberfall.EmberfallMod.LOGGER.info("CHEST_TEST free chest from {} at {} (given {})", source, p, run.freeGiven);
                        }
                        return true;
                    }
                }
            }
        }
        if (TEST_MODE) {
            com.solme.emberfall.EmberfallMod.LOGGER.info("CHEST_TEST dropFree found no spot near {}", near);
        }
        return false;
    }

    /** The first air block that has solid ground under it, searching 3 blocks down and 6 up from {@code from}; null if none. */
    private static BlockPos groundAbove(ServerLevel level, BlockPos from) {
        for (int dy = -3; dy <= 6; dy++) {
            BlockPos p = from.offset(0, dy, 0);
            if (level.getBlockState(p).isAir() && level.getBlockState(p.below()).isSolid()) {
                return p;
            }
        }
        return null;
    }

    public static void clear(int slot) {
        RUNS.remove(slot);
    }

    public static int count(int slot) {
        RunChests r = RUNS.get(slot);
        return r == null ? 0 : r.kinds.size();
    }

    /** The chest nearest to {@code from} in this run that is still closed, as {x,y,z,kindOrdinal}, or null. For debug and bots. */
    public static int[] nearestClosed(int slot, BlockPos from) {
        RunChests r = RUNS.get(slot);
        if (r == null) {
            return null;
        }
        int[] best = null;
        double bestD = Double.MAX_VALUE;
        for (Map.Entry<Long, ChestOpening.Kind> e : r.kinds.entrySet()) {
            if (r.opened.contains(e.getKey())) {
                continue;
            }
            BlockPos p = BlockPos.of(e.getKey());
            double d = p.distSqr(from);
            if (d < bestD) {
                bestD = d;
                best = new int[]{p.getX(), p.getY(), p.getZ(), e.getValue().ordinal()};
            }
        }
        return best;
    }

    public static int unopened(int slot) {
        RunChests r = RUNS.get(slot);
        return r == null ? 0 : r.kinds.size() - r.opened.size();
    }

    // ---- opening ----

    /** Right click on a chest block. Validates everything on the server; a refusal changes nothing. */
    public static void tryOpen(ServerPlayer player, Level world, BlockPos pos, BlockState state) {
        if (!(world instanceof ServerLevel level)) {
            return;
        }
        Integer slot = RunManager.slotOf(player);
        ArenaInstance arena = slot == null ? null : RunManager.getActive(slot);
        RunChests run = slot == null ? null : RUNS.get(slot);
        if (arena == null || run == null || arena.level() != level || !PlayerRelics.active(player)) {
            return; // not this player's run: a stray block does nothing
        }
        ChestOpening.Kind kind = run.kinds.get(pos.asLong());
        if (kind == null || run.opened.contains(pos.asLong()) || state.getValue(EmberChestBlock.OPENED)) {
            return;
        }
        double dist = Math.sqrt(player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        if (TEST_MODE) {
            com.solme.emberfall.EmberfallMod.LOGGER.info("CHEST_TEST click dist={} reach={} refused={}", String.format("%.2f", dist), REACH, dist > REACH);
        }
        if (dist > REACH) {
            return;
        }
        Map<String, Integer> owned = PlayerRelics.all(player);
        RelicStats stats = new RelicStats(owned);
        int gold = PickupSystem.gold(player);
        ChestOpening.Outcome out = ChestOpening.open(kind, gold, PlayerRelics.chestsOpened(player), stats.luck(),
                owned.getOrDefault("ember_key", 0), owned,
                RelicUnlocks.get(level.getServer()).unlockedIds(player.getUUID()), level.getRandom()::nextDouble);
        if (!out.opened()) {
            refuse(player, level, pos, out, kind);
            return;
        }
        // Order matters: take the gold first, so a failed spend (a race with another spender) hands out nothing.
        if (out.goldCost() > 0 && !PickupSystem.spendGold(player, out.goldCost())) {
            return;
        }
        if (!PlayerRelics.give(player, out.relic().id())) {
            return; // cannot happen (the pool skips maxed relics), but never charge for nothing
        }
        if (out.countsOpening()) {
            PlayerRelics.addChestOpened(player);
        }
        run.opened.add(pos.asLong());
        arena.journal().set(pos, state.setValue(EmberChestBlock.OPENED, true));
        if (TEST_MODE) {
            com.solme.emberfall.EmberfallMod.LOGGER.info("OPEN_TEST kind={} rarity={} relic={} key={} cost={} counter={} luck={}",
                    kind, out.relic().rarity(), out.relic().id(), out.keyProc(), out.goldCost(), PlayerRelics.chestsOpened(player), stats.luck());
        }
        reveal(player, level, pos, out, kind);
        RelicUnlocks.announce(player, RelicUnlocks.get(level.getServer()).addProgress(player.getUUID(), "open_25_chests", 1));
    }

    private static void refuse(ServerPlayer player, ServerLevel level, BlockPos pos, ChestOpening.Outcome out, ChestOpening.Kind kind) {
        if (out.refusal() == ChestOpening.Refusal.NOT_ENOUGH_GOLD) {
            int price = PlayerRelics.nextChestPrice(player);
            player.sendSystemMessage(Component.literal("Need " + price + " Gold (you have " + PickupSystem.gold(player) + ")")
                    .withStyle(net.minecraft.ChatFormatting.RED), true);
            level.playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 0.7F, 1.0F);
        } else {
            player.sendSystemMessage(Component.literal("The chest is empty: you already own every relic.")
                    .withStyle(net.minecraft.ChatFormatting.GRAY), true);
            level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.7F, 1.0F);
        }
    }

    private static void reveal(ServerPlayer player, ServerLevel level, BlockPos pos, ChestOpening.Outcome out, ChestOpening.Kind kind) {
        Relic relic = out.relic();
        RelicRarity rarity = relic.rarity();
        double x = pos.getX() + 0.5, y = pos.getY() + 0.9, z = pos.getZ() + 0.5;
        level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 1.0F, 1.0F);
        int boom = 8 + rarity.ordinal() * 10;
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, boom, 0.3, 0.3, 0.3, 0.08 + 0.03 * rarity.ordinal());
        if (rarity.ordinal() >= RelicRarity.RARE.ordinal()) {
            level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.8F, 0.8F + 0.2F * rarity.ordinal());
        }
        if (rarity == RelicRarity.LEGENDARY) {
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, 40, 0.4, 0.6, 0.4, 0.3);
        }
        MutableComponent head = Component.literal(out.keyProc() ? "Key! Free chest: " : kind == ChestOpening.Kind.FREE ? "Free chest: " : "Relic: ")
                .withStyle(net.minecraft.ChatFormatting.GRAY);
        MutableComponent name = Component.literal(relic.name()).withStyle(s -> s.withColor(rarity.rgb()).withBold(true));
        player.sendSystemMessage(head.append(name).append(Component.literal("  " + relic.description()).withStyle(net.minecraft.ChatFormatting.DARK_GRAY)), false);
        if (out.goldCost() > 0) {
            player.sendSystemMessage(Component.literal("-" + out.goldCost() + " Gold").withStyle(net.minecraft.ChatFormatting.GOLD), true);
        }
    }
}
