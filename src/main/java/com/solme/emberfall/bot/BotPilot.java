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
    /** Allies closer than this push a bot away (blocks). */
    static final double SEPARATION_RADIUS = 3.5;
    static final double SEPARATION_PUSH = 2.5;
    /** Extra blocks added to a foe's score for each ally already heading for it. */
    static final double CLAIM_PENALTY = 7.0;
    static final double TURN_MIN = 4.0;
    static final double TURN_MAX = 26.0;
    static final double ACCEL = 0.04;
    static final double STRAFE_PER_TICK = 0.09;
    static final int S_TAP_TICKS = 7;
    static final boolean NO_SHRINE_VISIT = Boolean.getBoolean("emberfall.noShrineVisit");

    private static final class State {
        List<BotWalk.Point> route = List.of();
        int index;
        long plannedAt = Long.MIN_VALUE;
        double goalX = Double.NaN;
        double goalZ = Double.NaN;
        double startX;
        double startZ;
        BotPersonality mind;            // dealt once from the bot's UUID, so the same bot always plays the same way
        double yaw = Double.NaN;        // smoothed facing, turned toward the wanted bearing at a limited rate
        double speed;                   // eased walking speed in blocks per tick
        BotMotion.Strafe strafe = new BotMotion.Strafe(0, 1);
        int strafeAge;                  // ticks into the current strafe
        int jumpTick = -1;              // ticks into a jump, -1 when on the ground
        double floorY = Double.NaN;     // the height of the floor under the bot; the displayed height is this plus the jump arc
        long nextJumpAt;                // game time of the next hop
        long lastHurtAt = -1_000_000L;  // for the S-tap and the jump reset
        float lastHealth = -1.0F;
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
                // A bot has no client, so two acknowledgements a real client sends never arrive, and each one leaves the server
                // treating the player as invulnerable (ServerPlayer.isInvulnerableTo): ServerboundPlayerLoadedPacket (hasClientLoaded,
                // sent at spawn and after any respawn) and ServerboundAcceptTeleportationPacket, whose only effect on the player is
                // hasChangedDimension(). ServerPlayer.teleport() to another level sets isChangingDimension; nothing else clears it.
                if (!p.connection.hasClientLoaded()) {
                    p.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
                }
                if (p.isChangingDimension()) {
                    p.hasChangedDimension();
                }
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
        if (st.mind == null) {
            st.mind = BotPersonality.fromSeed(bot.getUUID().getMostSignificantBits() ^ bot.getUUID().getLeastSignificantBits());
        }
        BotPersonality mind = st.mind;
        // the other players of this run: they feed separation, the bot's own place round a foe, and how many already chase each foe
        Integer mySlot = RunManager.slotOf(bot);
        List<ServerPlayer> party = new ArrayList<>();
        for (ServerPlayer o : level.getServer().getPlayerList().getPlayers()) {
            if (o.isAlive() && !o.isSpectator() && mySlot != null && mySlot.equals(RunManager.slotOf(o))) {
                party.add(o);
            }
        }
        party.sort(java.util.Comparator.comparing(ServerPlayer::getUUID));
        int myIndex = Math.max(0, party.indexOf(bot));
        List<BotSteer.Mate> mates = new ArrayList<>();
        for (ServerPlayer o : party) {
            if (o != bot) {
                mates.add(new BotSteer.Mate(o.getX(), o.getZ()));
            }
        }
        float hp = bot.getHealth();
        if (st.lastHealth >= 0.0F && hp < st.lastHealth - 0.01F) {
            st.lastHurtAt = now; // hurt this tick
        }
        st.lastHealth = hp;

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
        // how many OTHER bots are already heading for each foe (their last goal is the nearest foe to it), so a party spreads out
        int[] claims = new int[foes.size()];
        for (ServerPlayer o : party) {
            State os = o == bot ? null : STATES.get(o.getUUID());
            if (os != null && !Double.isNaN(os.goalX)) {
                int near = -1;
                double nd = 6.0;
                for (int i = 0; i < foes.size(); i++) {
                    double d = Math.hypot(foes.get(i).x() - os.goalX, foes.get(i).z() - os.goalZ);
                    if (d < nd) {
                        nd = d;
                        near = i;
                    }
                }
                if (near >= 0) {
                    claims[near]++;
                }
            }
        }
        int pick = BotSteer.pickFoe(bot.getX(), bot.getZ(), foes, claims, SIGHT, mind.boldness(), CLAIM_PENALTY);
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
                ? foeGoal(bot, foes.get(pick), mind, BotPlan.standOff(reach), myIndex, party.size(), mates)
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

        // 3. one step along it, shaped the way a person moves
        move(bot, st, mind, now, pick >= 0 ? foes.get(pick) : null, visit || goShrine, level);
    }

    /**
     * The body for one tick. Travelling: walk the route at an eased speed, face where it is going. Fighting (a foe chosen and
     * close): keep the crosshair on the foe, strafe sideways at random intervals, hop now and then, and ease back for a moment
     * after being hit. The facing never turns faster than the turn limit, which is what removes the jitter of snapping to a
     * fresh bearing every tick (it measured 140 degrees in one tick on a re-planned route).
     */
    private static void move(ServerPlayer bot, State st, BotPersonality mind, long now, BotPlan.Foe foe, boolean errand, ServerLevel level) {
        double x = bot.getX();
        double z = bot.getZ();
        // The route is walked from the FLOOR height, never from the displayed one: the displayed one includes the jump arc, so
        // reading it back would add the arc to itself on the next tick. While no jump is on, the floor is simply where the bot is.
        if (Double.isNaN(st.floorY) || st.jumpTick < 0) {
            st.floorY = bot.getY();
        }
        double y = st.floorY;
        double top = bot.getAttributeValue(Attributes.MOVEMENT_SPEED) * SPEED_PER_ATTRIBUTE;
        boolean haveRoute = st.index < st.route.size();
        BotWalk.Step step = null;
        double fightDist = foe == null ? Double.MAX_VALUE : Math.hypot(foe.x() - x, foe.z() - z);
        boolean fighting = !errand && foe != null && fightDist <= 16.0;

        // speed: ease toward the walking speed while there is route to walk, toward a stop otherwise; a hit makes the bot give ground briefly
        double wanted = haveRoute ? top : 0.0;
        double sTap = BotMotion.sTap((int) (now - st.lastHurtAt), S_TAP_TICKS);
        st.speed = BotMotion.easeSpeed(st.speed, wanted, ACCEL);
        if (haveRoute) {
            step = BotWalk.advance(x, y, z, st.route, st.index, st.speed);
            st.index = step.nextIndex();
        }
        double nx = haveRoute ? step.x() : x;
        double nz = haveRoute ? step.z() : z;
        double ny = haveRoute ? step.y() : y;
        st.floorY = ny; // the floor follows the route, so a hop on a slope rides the slope

        // the facing: the foe while fighting, otherwise the way it is walking
        double wantYaw;
        if (fighting) {
            wantYaw = Math.toDegrees(Math.atan2(-(foe.x() - x), foe.z() - z));
        } else if (haveRoute && (nx != x || nz != z)) {
            wantYaw = Math.toDegrees(Math.atan2(-(nx - x), nz - z));
        } else {
            wantYaw = Double.isNaN(st.yaw) ? bot.getYRot() : st.yaw;
        }
        if (Double.isNaN(st.yaw)) {
            st.yaw = bot.getYRot();
        }
        st.yaw = BotMotion.turnToward(st.yaw, wantYaw, BotMotion.turnRate(BotMotion.angleDelta(st.yaw, wantYaw), TURN_MIN, TURN_MAX));

        if (fighting) {
            // strafe: perpendicular to the line to the foe, a direction held for an uneven time, then flipped or rested
            java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
            BotMotion.Strafe before = st.strafe;
            st.strafe = BotMotion.stepStrafe(st.strafe, mind.strafeShare(), rnd.nextDouble(), rnd.nextDouble());
            st.strafeAge = (st.strafe.dir() == before.dir() && before.dir() != 0) ? st.strafeAge + 1 : 0;
            double side = BotMotion.strafeStep(st.strafe, st.strafeAge, STRAFE_PER_TICK);
            if (side != 0.0 && fightDist > 1.0e-6) {
                double px = -(foe.z() - z) / fightDist; // unit vector perpendicular to the foe bearing
                double pz = (foe.x() - x) / fightDist;
                nx += px * side;
                nz += pz * side;
            }
            // S-tap: ease back from the foe for a few ticks after a hit
            if (sTap > 0.0 && fightDist > 1.0e-6) {
                nx -= (foe.x() - x) / fightDist * 0.06 * sTap;
                nz -= (foe.z() - z) / fightDist * 0.06 * sTap;
            }
        }

        // jump: a hop now and then while fighting (and a jump reset right after a hit), placed along the real arc
        if (st.jumpTick < 0 && fighting && now >= st.nextJumpAt) {
            st.jumpTick = 0;
            java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
            st.nextJumpAt = now + (long) (mind.jumpEveryTicks() * (0.5 + rnd.nextDouble()));
        } else if (st.jumpTick < 0 && now - st.lastHurtAt >= 0 && now - st.lastHurtAt <= 1 && mind.agility() > 0.5) {
            st.jumpTick = 0;
        }
        if (st.jumpTick >= 0) {
            st.jumpTick++;
            double h = BotMotion.jumpHeight(st.jumpTick);
            if (h <= 0.0 && st.jumpTick > 1) {
                st.jumpTick = -1;
            } else {
                ny = ny + h; // the arc rides on whatever floor the route is on now, so a hop on a slope does not drag the bot back
            }
        }

        bot.snapTo(nx, ny, nz, (float) st.yaw, bot.getXRot());
        bot.resetFallDistance();
    }

    /**
     * Where this bot wants to stand against a foe. Healthy: on its OWN bearing round the foe at its temperament's stand-off (a
     * rusher inside its reach, a coward well outside it), pushed off allies that are too close. Hurt past its own limit: straight
     * away from the foe, so a scaredy-cat really does run.
     */
    private static BotPlan.Goal foeGoal(ServerPlayer bot, BotPlan.Foe foe, BotPersonality mind, double weaponStandOff, int index, int count,
            List<BotSteer.Mate> mates) {
        double standOff = mind.standOff(weaponStandOff);
        double healthFraction = bot.getHealth() / Math.max(1.0F, bot.getMaxHealth());
        double gx;
        double gz;
        if (mind.shouldFlee(healthFraction)) {
            double dx = bot.getX() - foe.x();
            double dz = bot.getZ() - foe.z();
            double d = Math.max(1.0e-6, Math.hypot(dx, dz));
            double away = Math.max(standOff + 8.0, 14.0);
            gx = foe.x() + dx / d * away;
            gz = foe.z() + dz / d * away;
        } else {
            // a bearing from the bot's place in the sorted party, nudged by a stable wobble from its own personality
            double wobble = (mind.agility() - 0.5) * 0.5;
            double[] slot = BotSteer.slotPoint(foe.x(), foe.z(), standOff, BotSteer.slotBearing(index, count, wobble));
            gx = slot[0];
            gz = slot[1];
        }
        BotSteer.Push push = BotSteer.separation(bot.getX(), bot.getZ(), mates, SEPARATION_RADIUS, SEPARATION_PUSH, index * 1.3);
        return new BotPlan.Goal(gx + push.dx(), gz + push.dz(), true);
    }

    private static double shortestReach(ServerPlayer bot) {
        Loadout loadout = Loadout.peek(bot);
        double reach = Double.MAX_VALUE;
        for (int i = 0; loadout != null && i < loadout.size(); i++) {
            reach = Math.min(reach, loadout.slot(i).weapon().range());
        }
        return reach == Double.MAX_VALUE ? 3.0 : reach;
    }
}
