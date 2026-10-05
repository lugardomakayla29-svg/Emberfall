package com.solme.emberfall.relic;

import com.solme.emberfall.EmberfallMod;
import com.solme.emberfall.entity.ModEntities;
import com.solme.emberfall.entity.Testificate;
import com.solme.emberfall.network.BuyMerchantItemPayload;
import com.solme.emberfall.network.OpenMerchantPayload;
import com.solme.emberfall.pickup.PickupSystem;
import com.solme.emberfall.world.ArenaInstance;
import com.solme.emberfall.world.RunManager;
import com.solme.emberfall.world.RunTelemetry;
import com.solme.emberfall.world.StaticMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs the Testificate merchants of a map run. ALL state is server side; the client only shows the stall it is sent.
 *
 * One merchant per run at a time ({@link MerchantSchedule}). Each BUYER has their own stall (stock depends on what they own and
 * have unlocked); the merchant's tier is shared. A visit is a small state machine ticked once a second from the run clock, and
 * the exit animation is ticked every game tick, so nothing is scheduled that could outlive the run.
 *
 * Lifecycle: STANDING (waiting for a buyer, name tag counts down) -> LEAVING_HAPPY (bought) or LEAVING_SAD (nobody bought in
 * time) -> gone. Leaving happy: he cheers, rises like a firework and bursts into one of eight designs. Leaving sad: he shakes
 * his head and simply vanishes in a puff.
 */
public final class MerchantManager {
    private MerchantManager() {}

    private static final boolean TEST_MODE = Boolean.getBoolean("emberfall.testMode");
    /** A click or a purchase must come from this close to the merchant. */
    public static final double REACH = 6.0;
    /** Height the rising Testificate climbs before bursting, and the ticks the climb takes. */
    static final double RISE_HEIGHT = 14.0;
    static final int RISE_TICKS = 36;

    private enum Phase { STANDING, LEAVING_HAPPY, LEAVING_SAD }

    private static final class Visit {
        Testificate entity;
        final RelicRarity tier;
        final long arrivedAt;
        Phase phase = Phase.STANDING;
        int phaseTicks;
        int design = -1;
        /** Each buyer's own stall, built on first browse and kept so the price and items do not change under their cursor. */
        final Map<UUID, MerchantOffer.Stall> stalls = new HashMap<>();
        Vec3 home;

        Visit(RelicRarity tier, long arrivedAt) {
            this.tier = tier;
            this.arrivedAt = arrivedAt;
        }
    }

    private static final class RunState {
        Visit visit;
        long leftAt = -1;
        int lastDesign = -1;
        int visits;
        int bought;
    }

    private static final Map<Integer, RunState> RUNS = new HashMap<>();

    // ---- lifecycle ----

    public static void clear(int slot) {
        RunState r = RUNS.remove(slot);
        if (r != null && r.visit != null && r.visit.entity != null) {
            r.visit.entity.discard();
        }
    }

    /**
     * The merchant that is standing in this run and open for business, or null. Read-only: used by the EmberTester bot to
     * decide whether to walk over. A merchant that is leaving, or already removed, is not offered.
     */
    public static Testificate standingMerchant(int slot) {
        RunState r = RUNS.get(slot);
        Visit v = r == null ? null : r.visit;
        return v != null && v.phase == Phase.STANDING && v.entity != null && !v.entity.isRemoved() ? v.entity : null;
    }

    /** Debug/test: visits started, items bought, and the phase of the current one. */
    public static String describe(int slot) {
        RunState r = RUNS.get(slot);
        if (r == null) {
            return "none";
        }
        return "visits=" + r.visits + " bought=" + r.bought + " phase=" + (r.visit == null ? "NONE" : r.visit.phase)
                + (r.visit == null ? "" : " tier=" + r.visit.tier + " left=" + MerchantSchedule.secondsLeft(RunTelemetry.elapsedSeconds(slot), r.visit.arrivedAt));
    }

    /** Called every game tick for every active map run. */
    public static void tickAll(MinecraftServer server) {
        for (ArenaInstance arena : RunManager.activeInstances()) {
            if (!arena.isMap()) {
                continue;
            }
            RunState run = RUNS.computeIfAbsent(arena.slot(), k -> new RunState());
            Visit v = run.visit;
            if (v != null && (v.entity == null || v.entity.isRemoved())) {
                run.visit = null; // removed by something else (a kill command, a chunk unload): forget it cleanly
                run.leftAt = RunTelemetry.elapsedSeconds(arena.slot());
                continue;
            }
            if (v != null) {
                tickVisit(server, arena, run, v);
            }
            if (server.getTickCount() % 20 == 0 && run.visit == null) {
                long elapsed = RunTelemetry.elapsedSeconds(arena.slot());
                if (MerchantSchedule.shouldArrive(elapsed, run.leftAt, false, -1, false)) {
                    arrive(server, arena, run, null);
                }
            }
        }
    }

    private static void tickVisit(MinecraftServer server, ArenaInstance arena, RunState run, Visit v) {
        ServerLevel level = arena.level();
        long elapsed = RunTelemetry.elapsedSeconds(arena.slot());
        v.phaseTicks++;
        switch (v.phase) {
            case STANDING -> {
                if (server.getTickCount() % 20 == 0) {
                    int left = MerchantSchedule.secondsLeft(elapsed, v.arrivedAt);
                    v.entity.setCustomName(nameTag(v.tier, left));
                    if (left == 10) {
                        level.playSound(null, v.entity.blockPosition(), SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 1.0F, 0.8F);
                    }
                    if (MerchantSchedule.expired(elapsed, v.arrivedAt)) {
                        startLeaving(level, v, Phase.LEAVING_SAD, run);
                        return;
                    }
                }
                lookAtNearest(level, v);
                if (v.phaseTicks % 6 == 0) {
                    glow(level, v);
                }
            }
            case LEAVING_HAPPY -> tickRise(server, arena, run, v);
            case LEAVING_SAD -> {
                if (v.phaseTicks == 1) {
                    level.playSound(null, v.entity.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 0.9F);
                    v.entity.setUnhappyCounter(40);
                }
                if (v.phaseTicks >= 30) {
                    level.sendParticles(ParticleTypes.CLOUD, v.entity.getX(), v.entity.getY() + 1.0, v.entity.getZ(), 24, 0.3, 0.6, 0.3, 0.02);
                    finish(arena, run, v);
                }
            }
        }
    }

    // ---- arrival ----

    /** Puts a merchant on the map. {@code forcedTier} is for debug and tests; null rolls the tier with the party's best Luck. */
    public static boolean arrive(MinecraftServer server, ArenaInstance arena, RunState run, RelicRarity forcedTier) {
        ServerLevel level = arena.level();
        List<ServerPlayer> party = playersOf(server, arena.slot());
        if (party.isEmpty()) {
            return false;
        }
        BlockPos spot = pickSpot(level, arena, party);
        if (spot == null) {
            return false;
        }
        double luck = 0;
        for (ServerPlayer p : party) {
            luck = Math.max(luck, new RelicStats(PlayerRelics.all(p)).luck());
        }
        RelicRarity tier = forcedTier != null ? forcedTier : RelicMath.rollRarity(luck, level.getRandom()::nextDouble);
        Testificate t = new Testificate(ModEntities.TESTIFICATE, level);
        t.dressAsNitwit(level);
        t.setPos(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        long elapsed = RunTelemetry.elapsedSeconds(arena.slot());
        t.setCustomName(nameTag(tier, MerchantSchedule.STAY));
        t.addTag("emberfall_testificate");
        if (!level.addFreshEntity(t)) {
            return false;
        }
        Visit v = new Visit(tier, elapsed);
        v.entity = t;
        v.home = t.position();
        run.visit = v;
        run.visits++;
        level.playSound(null, spot, SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 1.2F, 1.1F);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, spot.getX() + 0.5, spot.getY() + 1.0, spot.getZ() + 0.5, 20, 0.4, 0.6, 0.4, 0.05);
        Component msg = Component.literal("A ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(tier.label() + " Testificate").withStyle(s -> s.withColor(tier.rgb()).withBold(true)))
                .append(Component.literal(" has arrived nearby! He leaves in " + MerchantSchedule.STAY + "s.").withStyle(ChatFormatting.GRAY));
        RunManager.broadcastToSlot(server, arena.slot(), msg);
        if (TEST_MODE) {
            EmberfallMod.LOGGER.info("MERCHANT_TEST arrive tier={} at {} elapsed={}", tier, spot, elapsed);
        }
        return true;
    }

    /** Debug/test entry: arrive with a chosen tier. */
    public static boolean arriveNow(MinecraftServer server, int slot, RelicRarity tier) {
        ArenaInstance arena = RunManager.getActive(slot);
        if (arena == null || !arena.isMap()) {
            return false;
        }
        RunState run = RUNS.computeIfAbsent(slot, k -> new RunState());
        if (run.visit != null) {
            return false;
        }
        return arrive(server, arena, run, tier);
    }

    /** A flat spot far enough from every player and every unopened chest. Null if the map has none. */
    private static BlockPos pickSpot(ServerLevel level, ArenaInstance arena, List<ServerPlayer> party) {
        List<int[]> candidates = new ArrayList<>(StaticMap.get().chestCandidates(4, 10, 80, 12));
        java.util.Collections.shuffle(candidates, new java.util.Random(level.getRandom().nextLong()));
        for (int[] c : candidates) {
            BlockPos p = arena.origin().offset(c[0], c[1], c[2]);
            if (!level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir() || !level.getBlockState(p.below()).isSolid()) {
                continue;
            }
            boolean clear = true;
            for (ServerPlayer pl : party) {
                if (pl.distanceToSqr(p.getX() + 0.5, p.getY(), p.getZ() + 0.5) < MerchantSchedule.MIN_PLAYER_DISTANCE * MerchantSchedule.MIN_PLAYER_DISTANCE) {
                    clear = false;
                    break;
                }
            }
            if (clear) {
                int[] near = ChestManager.nearestClosed(arena.slot(), p);
                if (near != null && Math.hypot(near[0] - p.getX(), near[2] - p.getZ()) < MerchantSchedule.MIN_CHEST_DISTANCE) {
                    clear = false;
                }
            }
            if (clear) {
                return p;
            }
        }
        return null;
    }

    // ---- browsing and buying ----

    /** A right click on a Testificate: builds this player's stall (once) and opens the screen. */
    public static void onInteract(ServerPlayer player, Testificate merchant) {
        Integer slot = RunManager.slotOf(player);
        RunState run = slot == null ? null : RUNS.get(slot);
        Visit v = run == null ? null : run.visit;
        if (v == null || v.entity != merchant || v.phase != Phase.STANDING || !PlayerRelics.active(player)) {
            return; // a stray or leaving merchant does nothing
        }
        if (player.distanceToSqr(merchant) > REACH * REACH) {
            return;
        }
        ServerLevel level = (ServerLevel) merchant.level();
        MerchantOffer.Stall stall = v.stalls.computeIfAbsent(player.getUUID(), id -> MerchantOffer.stock(v.tier,
                PlayerRelics.nextChestPrice(player), PlayerRelics.all(player),
                RelicUnlocks.get(level.getServer()).unlockedIds(player.getUUID()), level.getRandom()::nextDouble));
        level.playSound(null, merchant.blockPosition(), SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 1.0F, 1.0F); // the curious "hmm"
        if (stall.items().isEmpty()) {
            player.sendSystemMessage(Component.literal("The Testificate has nothing left to sell you.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        send(player, v, stall);
        if (TEST_MODE) {
            EmberfallMod.LOGGER.info("MERCHANT_TEST browse player={} items={}", player.getGameProfile().name(),
                    stall.items().stream().map(i -> i.relic().id() + ":" + i.price()).toList());
        }
    }

    private static void send(ServerPlayer player, Visit v, MerchantOffer.Stall stall) {
        int gold = PickupSystem.gold(player);
        List<OpenMerchantPayload.Item> items = new ArrayList<>();
        for (MerchantOffer.Item it : stall.items()) {
            items.add(new OpenMerchantPayload.Item(it.relic().id(), it.price(), gold >= it.price()));
        }
        long elapsed = RunTelemetry.elapsedSeconds(RunManager.slotOf(player));
        ServerPlayNetworking.send(player, new OpenMerchantPayload(true, stall.tier().ordinal(), MerchantSchedule.secondsLeft(elapsed, v.arrivedAt), gold, items));
    }

    /** C2S: slot 0..2 buys; -1 means the player closed the screen without buying. Everything is re-validated here. */
    public static void onBuyReceived(ServerPlayer player, BuyMerchantItemPayload payload) {
        Integer slot = RunManager.slotOf(player);
        RunState run = slot == null ? null : RUNS.get(slot);
        Visit v = run == null ? null : run.visit;
        if (v == null || v.phase != Phase.STANDING || v.entity == null || v.entity.isRemoved() || !PlayerRelics.active(player)) {
            return;
        }
        MerchantOffer.Stall stall = v.stalls.get(player.getUUID());
        if (stall == null || player.distanceToSqr(v.entity) > (REACH + 2) * (REACH + 2)) {
            return;
        }
        ServerLevel level = (ServerLevel) v.entity.level();
        int index = payload.index();
        if (index == -1) {
            level.playSound(null, v.entity.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.9F, 1.0F); // disappointed, but he stays
            v.entity.setUnhappyCounter(30);
            return;
        }
        if (index < 0 || index >= stall.items().size()) {
            return;
        }
        MerchantOffer.Item item = stall.items().get(index);
        Relic relic = item.relic();
        if (PlayerRelics.stacks(player, relic.id()) >= relic.maxStacks()) {
            player.sendSystemMessage(Component.literal("You cannot carry any more of that relic.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (PickupSystem.gold(player) < item.price()) {
            player.sendSystemMessage(Component.literal("Need " + item.price() + " Gold (you have " + PickupSystem.gold(player) + ")").withStyle(ChatFormatting.RED), true);
            level.playSound(null, v.entity.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8F, 1.0F);
            return;
        }
        // Take the gold first, so a failed spend hands out nothing; then give. Never charge for nothing.
        if (!PickupSystem.spendGold(player, item.price())) {
            return;
        }
        if (!PlayerRelics.give(player, relic.id())) {
            return;
        }
        run.bought++;
        ServerPlayNetworking.send(player, OpenMerchantPayload.CLOSE);
        RelicRarity rarity = relic.rarity();
        player.sendSystemMessage(Component.literal("Bought: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(relic.name()).withStyle(s -> s.withColor(rarity.rgb()).withBold(true)))
                .append(Component.literal("  " + relic.description()).withStyle(ChatFormatting.DARK_GRAY)), false);
        player.sendSystemMessage(Component.literal("-" + item.price() + " Gold").withStyle(ChatFormatting.GOLD), true);
        if (TEST_MODE) {
            EmberfallMod.LOGGER.info("MERCHANT_TEST buy player={} relic={} rarity={} price={} tier={}", player.getGameProfile().name(), relic.id(), rarity, item.price(), v.tier);
        }
        startLeaving(level, v, Phase.LEAVING_HAPPY, run);
    }

    // ---- leaving ----

    private static void startLeaving(ServerLevel level, Visit v, Phase phase, RunState run) {
        v.phase = phase;
        v.phaseTicks = 0;
        if (phase == Phase.LEAVING_HAPPY) {
            v.design = MerchantFireworks.choose(run.lastDesign, level.getRandom()::nextDouble);
            run.lastDesign = v.design;
            level.playSound(null, v.entity.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.2F, 1.0F);
            v.entity.playCelebrateSound();
        }
        closeScreens(level, v);
    }

    /** Closes the stall window of everyone who was browsing this merchant. */
    private static void closeScreens(ServerLevel level, Visit v) {
        for (UUID id : v.stalls.keySet()) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                ServerPlayNetworking.send(p, OpenMerchantPayload.CLOSE);
            }
        }
    }

    /** Happy exit: a smooth accelerating climb with sparks, then a burst above the players' heads. */
    private static void tickRise(MinecraftServer server, ArenaInstance arena, RunState run, Visit v) {
        ServerLevel level = arena.level();
        Testificate t = v.entity;
        double f = Math.min(1.0, v.phaseTicks / (double) RISE_TICKS);
        double eased = f * f; // accelerating, like a rocket leaving
        t.setPos(v.home.x, v.home.y + RISE_HEIGHT * eased, v.home.z);
        t.setDeltaMovement(Vec3.ZERO);
        t.fallDistance = 0;
        t.setNoGravity(true);
        t.setYRot(t.getYRot() + 28.0F); // the silly spin
        level.sendParticles(ParticleTypes.FIREWORK, t.getX(), t.getY() + 0.2, t.getZ(), 3, 0.12, 0.05, 0.12, 0.01);
        if (v.phaseTicks % 4 == 0) {
            level.playSound(null, t.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.NEUTRAL, 0.5F, 1.0F + 0.5F * (float) f);
        }
        if (v.phaseTicks >= RISE_TICKS) {
            burst(level, t.position(), v.design);
            finish(arena, run, v);
        }
    }

    /** One rocket entity for one tick: it carries the design, we broadcast its explosion event to clients, and discard it. */
    static void burst(ServerLevel level, Vec3 at, int design) {
        MerchantFireworks.Design d = MerchantFireworks.DESIGNS[Math.max(0, Math.min(MerchantFireworks.DESIGNS.length - 1, design))];
        List<FireworkExplosion> blasts = new ArrayList<>();
        for (MerchantFireworks.Blast b : d.blasts()) {
            blasts.add(new FireworkExplosion(FireworkExplosion.Shape.values()[b.shape()], new it.unimi.dsi.fastutil.ints.IntArrayList(b.colors()),
                    new it.unimi.dsi.fastutil.ints.IntArrayList(b.fade()), b.trail(), b.twinkle()));
        }
        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FIREWORK_ROCKET);
        stack.set(net.minecraft.core.component.DataComponents.FIREWORKS, new net.minecraft.world.item.component.Fireworks(1, blasts));
        net.minecraft.world.entity.projectile.FireworkRocketEntity rocket =
                new net.minecraft.world.entity.projectile.FireworkRocketEntity(level, at.x, at.y, at.z, stack);
        level.addFreshEntity(rocket);
        level.broadcastEntityEvent(rocket, (byte) 17); // the client draws the burst from this event
        rocket.discard(); // never explodes server side, so it can hurt nobody
        if (TEST_MODE) {
            EmberfallMod.LOGGER.info("MERCHANT_TEST burst design={} at {}", d.name(), at);
        }
    }

    private static void finish(ArenaInstance arena, RunState run, Visit v) {
        if (v.entity != null) {
            v.entity.discard();
        }
        run.visit = null;
        run.leftAt = RunTelemetry.elapsedSeconds(arena.slot());
        if (TEST_MODE) {
            EmberfallMod.LOGGER.info("MERCHANT_TEST left phase={} visits={} bought={}", v.phase, run.visits, run.bought);
        }
    }

    // ---- small helpers ----

    private static List<ServerPlayer> playersOf(MinecraftServer server, int slot) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Integer s = RunManager.slotOf(p);
            if (s != null && s == slot) {
                out.add(p);
            }
        }
        return out;
    }

    private static Component nameTag(RelicRarity tier, int secondsLeft) {
        return Component.literal(tier.label() + " Testificate").withStyle(s -> s.withColor(tier.rgb()).withBold(true))
                .append(Component.literal("  " + secondsLeft + "s").withStyle(ChatFormatting.GRAY));
    }

    private static void lookAtNearest(ServerLevel level, Visit v) {
        ServerPlayer best = null;
        double bestD = 12 * 12;
        for (ServerPlayer p : level.players()) {
            double d = p.distanceToSqr(v.entity);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        if (best != null) {
            v.entity.getLookControl().setLookAt(best, 30.0F, 30.0F);
            double dx = best.getX() - v.entity.getX(), dz = best.getZ() - v.entity.getZ();
            float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            v.entity.setYRot(yaw);
            v.entity.setYHeadRot(yaw);
        }
    }

    /** A gentle shimmer in the tier colour so a merchant is easy to spot from across the map. */
    private static void glow(ServerLevel level, Visit v) {
        level.sendParticles(v.tier.ordinal() >= RelicRarity.RARE.ordinal() ? ParticleTypes.END_ROD : ParticleTypes.HAPPY_VILLAGER,
                v.entity.getX(), v.entity.getY() + 2.3, v.entity.getZ(), 1 + v.tier.ordinal(), 0.25, 0.15, 0.25, 0.01);
    }
}
