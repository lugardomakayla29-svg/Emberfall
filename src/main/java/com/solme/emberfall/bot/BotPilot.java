package com.solme.emberfall.bot;

import com.solme.emberfall.combat.AutoAttackSystem;
import com.solme.emberfall.item.Loadout;
import com.solme.emberfall.pickup.PickupSystem;
import com.solme.emberfall.relic.MerchantManager;
import com.solme.emberfall.shrine.MapShrines;
import com.solme.emberfall.world.ArenaInstance;
import com.solme.emberfall.world.RunManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The legs of an EmberTester. A client-less player cannot be driven by input or velocity (measured), so once a tick this
 * places the bot one short step along a route the game's own pathfinder produced ({@link BotScout}). The game's auto-attack
 * already fights for any player in a run, so the pilot only decides where to stand: {@link BotPlan}.
 *
 * <p>Cost: one path search per bot per {@link #REPLAN_EVERY} ticks, and one {@link BotWalk#advance} plus one
 * {@code snapTo} per bot per tick.
 */
public final class BotPilot {
    /** Ticks between route requests. */
    static final int REPLAN_EVERY = 20;
    /** Blocks the bot sees foes from. */
    static final double SIGHT = 40.0;
    /** Pathfinder search range, in blocks. */
    static final int PATH_RANGE = 48;
    /** Search range when walking to a merchant: he can stand anywhere on the map (about 100 blocks across) and stays only 60 s. */
    static final int MERCHANT_PATH_RANGE = 128;
    /** Blocks per tick per point of movement-speed attribute, measured: a human walks a median 0.1994 at 0.1. */
    static final double SPEED_PER_ATTRIBUTE = 2.0;
    static final double OFF_ROUTE_TOLERANCE = 2.5;
    /** Blocks from the merchant the bot stops at (inside the game's own reach of {@link MerchantManager#REACH}). */
    static final double MERCHANT_STAND = 3.0;
    /** Gold a bot keeps back after a purchase, and the gold at which it goes to look at a stall it has not seen. */
    static final long MERCHANT_RESERVE = 20;
    static final long MERCHANT_LOOK_GOLD = 60;
    /** Ticks before the bot will open the same merchant's screen again. */
    static final long MERCHANT_RETRY = 100;
    /** Blocks from the Challenge Shrine the bot stops at (the game accepts a click within 10 of its anchor). */
    static final double SHRINE_STAND = 4.0;
    /** The bot only starts a trial at this fraction of its health or better. */
    static final double SHRINE_MIN_HEALTH = 0.6;
    /** Ticks before the bot tries the same shrine again (a refused open must not be spammed). */
    static final long SHRINE_RETRY = 100;
    /** Test control (like -Demberfall.noLegion): a suite about something else, such as the walk to one planted foe, sets this. */
    static final boolean NO_SHRINE_VISIT = Boolean.getBoolean("emberfall.noShrineVisit");

    private static final class State {
        List<BotWalk.Point> route = List.of();
        int index;
        long plannedAt = Long.MIN_VALUE;
        double goalX = Double.NaN;
        double goalZ = Double.NaN;
        double startX;
        double startZ;
        long lastShrine = -1_000_000L;
        long lastBrowse = -1_000_000L; // not Long.MIN_VALUE: now - MIN_VALUE overflows and is never >= the retry gap
    }

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private BotPilot() {}

    public static void forget(UUID id) {
        STATES.remove(id);
    }

    public static void tickAll(MinecraftServer server) {
        if (BotRoster.count() == 0) {
            return;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (BotRoster.isBot(p.getUUID()) && p.isAlive() && !p.isSpectator()) {
                Integer slot = RunManager.slotOf(p);
                if (slot != null && p.level() instanceof ServerLevel level) {
                    ArenaInstance arena = RunManager.getActive(slot);
                    if (arena != null) {
                        tick(level, arena, p);
                    }
                }
            }
        }
    }

    private static void tick(ServerLevel level, ArenaInstance arena, ServerPlayer bot) {
        State st = STATES.computeIfAbsent(bot.getUUID(), id -> new State());
        long now = level.getGameTime();

        // 1. where do we want to be
        double reach = shortestReach(bot);
        List<BotPlan.Foe> foes = new ArrayList<>();
        AABB box = bot.getBoundingBox().inflate(BotPilot.SIGHT, 12.0, BotPilot.SIGHT);
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, box, AutoAttackSystem::isEmberfallHostile);
        for (Mob m : mobs) {
            int neighbours = 0;
            for (Mob o : mobs) {
                if (o != m && o.distanceToSqr(m) < 36.0) {
                    neighbours++;
                }
            }
            foes.add(new BotPlan.Foe(m.getX(), m.getZ(), neighbours));
        }
        int pick = BotPlan.pickFoe(bot.getX(), bot.getZ(), foes, SIGHT);
        // a standing merchant the bot can pay for beats fighting: walk over, and open the real screen once in reach
        com.solme.emberfall.entity.Testificate merchant = MerchantManager.standingMerchant(RunManager.slotOf(bot));
        boolean visit = merchant != null
                && BotPlan.shouldVisitMerchant(true, PickupSystem.gold(bot), -1, MERCHANT_RESERVE, MERCHANT_LOOK_GOLD);
        if (visit && bot.distanceToSqr(merchant) <= MerchantManager.REACH * MerchantManager.REACH * 0.81
                && now - st.lastBrowse >= MERCHANT_RETRY) {
            st.lastBrowse = now;
            MerchantManager.onInteract(bot, merchant);
        }
        // the free Challenge Shrine, once per run, when no merchant is worth the trip and the bot is healthy
        BlockPos shrine = visit ? null : MapShrines.openChallengeAnchor(RunManager.slotOf(bot));
        boolean goShrine = shrine != null && !NO_SHRINE_VISIT
                && BotPlan.shouldVisitShrine(true, false, bot.getHealth() / Math.max(1.0F, bot.getMaxHealth()), SHRINE_MIN_HEALTH);
        if (goShrine && Math.hypot(bot.getX() - (shrine.getX() + 0.5), bot.getZ() - (shrine.getZ() + 0.5)) <= SHRINE_STAND + 1.5
                && now - st.lastShrine >= SHRINE_RETRY) {
            st.lastShrine = now;
            MapShrines.botChallenge(bot);
        }
        BotPlan.Goal goal = visit
                ? BotPlan.goalFor(bot.getX(), bot.getZ(), new BotPlan.Foe(merchant.getX(), merchant.getZ(), 0), MERCHANT_STAND)
                : goShrine
                ? BotPlan.goalFor(bot.getX(), bot.getZ(), new BotPlan.Foe(shrine.getX() + 0.5, shrine.getZ() + 0.5, 0), SHRINE_STAND)
                : pick >= 0
                ? BotPlan.goalFor(bot.getX(), bot.getZ(), foes.get(pick), BotPlan.standOff(reach))
                : BotPlan.wander(bot.getX(), bot.getZ(), arena.origin().getX() + 0.5, arena.origin().getZ() + 0.5, 6.0);

        // 2. a new route when due, when the goal moved, or when pushed off the old one
        double moved = Double.isNaN(st.goalX) ? Double.MAX_VALUE : Math.hypot(goal.x() - st.goalX, goal.z() - st.goalZ);
        boolean off = BotWalk.offRoute(bot.getX(), bot.getZ(), st.startX, st.startZ, st.route, st.index, OFF_ROUTE_TOLERANCE);
        if (BotPlan.needsReplan(now - st.plannedAt, REPLAN_EVERY, moved, 3.0) || off) {
            st.startX = bot.getX();
            st.startZ = bot.getZ();
            st.goalX = goal.x();
            st.goalZ = goal.z();
            st.plannedAt = now;
            st.index = 0;
            BlockPos target = BlockPos.containing(goal.x(), bot.getY(), goal.z());
            st.route = BotScout.route(level, bot.getX(), bot.getY(), bot.getZ(), target, visit || goShrine ? MERCHANT_PATH_RANGE : PATH_RANGE);
        }

        // 3. one step along it
        if (st.index >= st.route.size()) {
            return;
        }
        double speed = bot.getAttributeValue(Attributes.MOVEMENT_SPEED) * SPEED_PER_ATTRIBUTE;
        BotWalk.Step step = BotWalk.advance(bot.getX(), bot.getY(), bot.getZ(), st.route, st.index, speed);
        st.index = step.nextIndex();
        float yaw = (float) (Math.toDegrees(Math.atan2(-(step.x() - bot.getX()), step.z() - bot.getZ())));
        bot.snapTo(step.x(), step.y(), step.z(), Double.isNaN(yaw) || (step.x() == bot.getX() && step.z() == bot.getZ()) ? bot.getYRot() : yaw, bot.getXRot());
        bot.resetFallDistance();
    }

    /** The shortest reach among the bot's weapons, so every weapon it holds can hit from where it stands. */
    private static double shortestReach(ServerPlayer bot) {
        Loadout loadout = Loadout.peek(bot);
        double reach = Double.MAX_VALUE;
        for (int i = 0; loadout != null && i < loadout.size(); i++) {
            reach = Math.min(reach, loadout.slot(i).weapon().range());
        }
        return reach == Double.MAX_VALUE ? 3.0 : reach;
    }
}
