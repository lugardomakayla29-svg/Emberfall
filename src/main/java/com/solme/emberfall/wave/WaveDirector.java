package com.solme.emberfall.wave;

import com.solme.emberfall.EmberfallMod;
import com.solme.emberfall.boss.DevourerBossFight;
import com.solme.emberfall.boss.GuardianBossFight;
import com.solme.emberfall.entity.BlightfeatherMarksman;
import com.solme.emberfall.entity.BoilRiddenMarksman;
import com.solme.emberfall.entity.BonecallerNecromancer;
import com.solme.emberfall.entity.BroodmotherStalker;
import com.solme.emberfall.entity.CinderbrandReaver;
import com.solme.emberfall.entity.CorruptedSentinel;
import com.solme.emberfall.entity.HordeSkeleton;
import com.solme.emberfall.entity.HordeSpider;
import com.solme.emberfall.entity.HordeZombie;
import com.solme.emberfall.entity.ModEntities;
import com.solme.emberfall.entity.PlagueColossus;
import com.solme.emberfall.entity.UmbralMagus;
import com.solme.emberfall.world.ArenaInstance;
import com.solme.emberfall.world.Dimensions;
import com.solme.emberfall.world.RunManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Design doc 4.3: a per-run controller that spawns enemies at the arena's hot
 * spawn markers on an escalating schedule, driven by a threat-level scalar
 * that rises with elapsed run time. Enforces the hard concurrent-hostile cap
 * from 2.4 - if the arena is already at the cap, the next spawn tick is
 * skipped rather than exceeding it. Also owns 4.3's boss hand-off: "triggering
 * boss spawns at configured time marks, replacing normal wave spawning for
 * the duration of the boss fight" - {@link #BOSS_SPAWN_AT_TICK} below is a
 * short placeholder mark for proving the hand-off, not a tuned pacing number
 * (see the retired HydraBrain's javadoc on that).
 *
 * v1: single horde enemy pool (HordeZombie) + a single boss (Hydra, Section
 * 6.2), spawns only from "spawn_point" markers, linear threat ramp. Weighted
 * per-tier enemy pools (JSON-driven, per design doc 2.2/4.3) come later -
 * this establishes the ticking/cap/scheduling skeleton they'll plug into.
 *
 * Veteran roll: a ported-and-adapted mob-affix idea (see
 * {@link com.solme.emberfall.entity.HordeZombie#becomeVeteran()} for why
 * this is deliberately NOT a Section 6 composite rig) - a small, rare
 * per-spawn chance for a horde zombie to come out buffed. Kept as a flat
 * chance independent of threatLevel for now; scaling it with threat is a
 * natural follow-up once there's more than one mob tier to weight against.
 *
 * Elite spawn track (added once all 5 Corrupted archetypes existed and were
 * verified individually via {@code /emberfall spawnelite} - see command
 * class): a second, independent schedule alongside the horde one, gated
 * behind {@link #ELITE_MIN_THREAT} so a run's opening minute stays pure
 * horde-filler before anything mechanically heavier can appear. Picks
 * uniformly among the 5 ported Corrupted archetypes plus {@link
 * com.solme.emberfall.entity.TikiMagma}'s own Elite/Corrupted tiers
 * (2026-09-28 addition - see that class's javadoc) and scales each spawn's
 * statMultiplier with the run's current threatLevel (1.0 at threat 0, up to
 * 2.0 at the threat cap) - the same knob {@code /emberfall spawnelite}
 * exposes manually, now driven automatically. {@code ELITE_MIN_THREAT} and
 * the elite interval constants are placeholder pacing numbers in the same
 * spirit as {@link #BOSS_SPAWN_AT_TICK} - proving the hand-off works, not a
 * tuned balance pass (that's Section 12/later work, same as everywhere else
 * in this class).
 *
 * Two-tier boss escalation (2026-09-28, owner-directed): Hydra was already
 * the only boss with a real automatic trigger ({@link #BOSS_SPAWN_AT_TICK});
 * the Devourer existed and worked but had zero player-reachable path to it
 * (gamemaster-only {@code /emberfall} debug trigger only) - a fully-built
 * boss that no real player would ever see. Fix: Hydra is now explicitly the
 * tier-1 boss, and defeating it (a real kill, not a discarded/torn-down rig
 * - see {@link #onHydraDefeated}) escalates this run to tier 2 via {@link
 * #tier}. Tier 2 means "same enemies, more stats and mechanics" rather than
 * an entirely new mob roster (deliberate - the horde-filler Veteran tier and
 * the elite track's statMultiplier already ARE "same enemy, more mechanics/
 * stats", so tier 2 just leans harder on levers that already exist: {@link
 * #TIER2_VETERAN_CHANCE} replaces the tier-1 rare-roll chance outright, and
 * {@link #TIER2_ELITE_STAT_BONUS} adds onto the elite track's existing
 * threat-based statMultiplier). {@link #DEVOURER_SPAWN_AFTER_TIER2_TICKS}
 * is the Devourer's own placeholder time mark, counted from the moment tier
 * 2 starts (not from run start) - same "proving the hand-off, not a tuned
 * number" spirit as {@link #BOSS_SPAWN_AT_TICK}. {@link #tickAmbience}
 * gives tier 2 a genuinely visible "the world got worse" signal (drifting
 * ash + soul-fire embers across the arena) rather than a purely internal
 * stat change nobody would ever notice - ties into the same corruption
 * visual language {@link com.solme.emberfall.entity.TikiMagma}'s Corrupted
 * tier and the 5 ported Corrupted archetypes already established. Devourer
 * defeat does not escalate further (no tier 3 in this pass) - the run
 * simply continues at tier-2 difficulty until the player dies or leaves;
 * a third tier or boss rotation is later pacing work, not this pass's scope.
 */
public final class WaveDirector {
    // Default 40 = shipped behaviour. The system property exists only so a TEST server can measure tick time at 60 or 80 (issue #13,
    // party step 0); nothing in the game or its config sets it.
    private static final int HOSTILE_CAP = Integer.getInteger("emberfall.hostileCap", 40);
    /**
     * Party size for THIS run, frozen once at the first spawn decision (tick {@link #BASE_SPAWN_INTERVAL_TICKS}) so a party that
     * formed during the gate countdown is fully counted, and never read again (issue #13, step 3b). 0 means "not frozen yet".
     * For one player every scaled value below equals the old constant, so a solo run is unchanged.
     */
    private int partySize = 0;
    /** The party is counted at this tick (15 s) and never again. Spawns before it use the solo values. */
    static final long PARTY_FREEZE_AT_TICK = 300;
    private static final int BASE_SPAWN_INTERVAL_TICKS = 100; // 5s at threat 0
    private static final int MIN_SPAWN_INTERVAL_TICKS = 15;   // 0.75s floor at max threat
    private static final double THREAT_RAMP_PER_TICK = 1.0 / 1200.0; // threat +1 every 60s
    private static final double THREAT_CAP = 20.0;
    private static final int SPAWNS_PER_TICK_EVENT = 1;
    /**
     * The Hydra arrives at 10:00 (threat 10 of 20): the design doc's own "minute 10" benchmark (section 12),
     * late enough that the player has a real build and the boss reads as an event rather than more
     * horde, per section 4.3. Overridable with {@code -Demberfall.bossAtTicks=N} so the sandbox test
     * server can compress a run; the default is what players get.
     */
    private static final long BOSS_SPAWN_AT_TICK = Long.getLong("emberfall.bossAtTicks", 12000L);
    private static final double VETERAN_CHANCE = 0.04; // placeholder, see class javadoc

    // Elite spawn track - see class javadoc.
    private static final double ELITE_MIN_THREAT = 1.0; // ~60s in before any elite can appear
    private static final int ELITE_BASE_INTERVAL_TICKS = 2400; // 120s at threat == ELITE_MIN_THREAT
    private static final int ELITE_MIN_INTERVAL_TICKS = 600;   // 30s floor at max threat
    private static final int ELITE_COUNT = 9;

    // Tier-2 escalation (post-Hydra) - see class javadoc's "Two-tier boss escalation" section.
    private static final double TIER2_VETERAN_CHANCE = 0.30; // placeholder, replaces VETERAN_CHANCE outright once escalated
    private static final double TIER2_ELITE_STAT_BONUS = 0.5; // added on top of the elite track's own threat-based multiplier
    private static final double TIER2_ELITE_INTERVAL_SCALE = 0.5; // elites arrive roughly twice as often once escalated
    /**
     * The Devourer arrives 5:00 into tier 2, giving the escalated act room to be felt before its boss.
     * Overridable with {@code -Demberfall.devourerAfterTicks=N} for compressed sandbox tests.
     */
    private static final long DEVOURER_SPAWN_AFTER_TIER2_TICKS = Long.getLong("emberfall.devourerAfterTicks", 6000L);
    private static final long AMBIENCE_INTERVAL_TICKS = 20; // once a second
    private static final int AMBIENCE_BURSTS_PER_TICK = 3;

    private static final Map<Integer, WaveDirector> active = new HashMap<>();

    private final ArenaInstance instance;
    private long elapsedTicks = 0;
    private long nextSpawnAtTick = BASE_SPAWN_INTERVAL_TICKS;
    private double threatLevel = 0.0;
    private double bonusThreat = 0.0; // permanent, run-long add-on from a Greed Shrine (design doc 8)
    /** Live threat from the party's relics (Wither Crown), refreshed once a second so taking the relic off removes it. */
    private double relicThreat = 0.0;
    private long totalSpawned = 0;
    private long totalVeteransSpawned = 0;
    private long totalElitesSpawned = 0;
    private long nextEliteSpawnAtTick = 0;
    private boolean paused = false;
    private boolean bossActive = false;
    private boolean hydraSpawned = false;
    private boolean devourerSpawned = false;
    private int tier = 1;
    private long tierStartTick = 0;
    private long lastAmbienceTick = 0;
    /** Final Swarm: true once every boss is dead. Ordinary spawning stops and {@link FinalSwarm} drives the crowd instead. */
    private boolean swarm = false;
    private long swarmStartTick = 0;
    private int swarmSteps = 0;

    private WaveDirector(ArenaInstance instance) {
        this.instance = instance;
    }

    public static WaveDirector start(ArenaInstance instance) {
        WaveDirector director = new WaveDirector(instance);
        active.put(instance.slot(), director);
        EmberfallMod.LOGGER.info("Wave Director started for slot {}", instance.slot());
        // Plan step 3a (issue #13): the party size at run start. Nothing reads it yet; 3b must read it once, here, and keep it.
        EmberfallMod.LOGGER.info("PARTYSIZE slot={} size={}", instance.slot(), com.solme.emberfall.world.RunManager.partySize(instance.slot()));
        return director;
    }

    public static void stop(int slot) {
        if (active.remove(slot) != null) {
            EmberfallMod.LOGGER.info("Wave Director stopped for slot {}", slot);
        }
    }

    public static WaveDirector get(int slot) {
        return active.get(slot);
    }

    public static void tickAll(MinecraftServer server) {
        if (active.isEmpty()) {
            return;
        }
        for (WaveDirector director : new java.util.ArrayList<>(active.values())) {
            director.tick(director.instance.level());
        }
    }

    /** Design doc 4.1 step 6: freezes the ramp/spawn schedule while a player has a Tome Choice screen open. */
    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public boolean isPaused() {
        return paused;
    }

    /**
     * Design doc 4.3: freezes the ramp/spawn schedule for the duration of a
     * boss fight, same effect as {@link #setPaused} but tracked separately
     * so the two independent reasons to pause (a Tome Choice screen vs. an
     * active boss fight) can never accidentally clear each other's pause.
     */
    public void setBossActive(boolean bossActive) {
        this.bossActive = bossActive;
    }

    public boolean isBossActive() {
        return bossActive;
    }

    public int tier() {
        return tier;
    }

    /**
     * Called by {@link GuardianBossFight#tickAll} exactly once, the moment
     * Hydra's brain confirms a genuine kill (not a rig discarded early by
     * run teardown) - escalates this run to tier 2. Idempotent guard against
     * {@code tier >= 2} even though Hydra is already a one-shot spawn
     * ({@link #hydraSpawned}), since defensive code here costs nothing and
     * a future change to that invariant shouldn't silently double-escalate.
     */
    public void onHydraDefeated(ServerLevel level) {
        if (tier >= 2) {
            return;
        }
        tier = 2;
        tierStartTick = elapsedTicks;
        // A boss is a milestone: it always leaves a free chest (unless the run is already at the free-chest cap).
        com.solme.emberfall.relic.ChestManager.dropFree(level, instance, bossDeathSpot(level),
                com.solme.emberfall.relic.FreeChestRule.Source.BOSS);
        RunManager.broadcastToSlot(level.getServer(), instance.slot(),
                Component.literal("§4§lThe corruption deepens... something far worse now stirs."));
        EmberfallMod.LOGGER.info("Slot {} escalated to tier 2 after Hydra's defeat", instance.slot());
    }

    /** Where the boss fell: the arena's boss_spawn marker (the boss arena floor), else the arena origin. */
    private BlockPos bossDeathSpot(ServerLevel level) {
        List<BlockPos> boss = instance.markers("boss_spawn");
        return boss.isEmpty() ? instance.origin() : boss.get(0);
    }

    /** Manually triggers the Ember Guardian (the tier-1 boss, formerly the Hydra) immediately, skipping the time-mark wait. For testing/debug commands. */
    public void triggerBossNow(ServerLevel level) {
        if (bossActive) {
            return;
        }
        hydraSpawned = true;
        RunManager.broadcastToSlot(level.getServer(), instance.slot(),
                Component.literal("§c§lThe Ember Guardian awakens."));
        GuardianBossFight.spawn(level, instance);
        bossActive = true;
    }

    /**
     * Triggers the Devourer boss immediately - either the automatic tier-2
     * hand-off ({@link #tick}) or a gamemaster's manual {@code /emberfall}
     * test trigger. Both paths share this one entry point now (unlike
     * Hydra's split between {@link #triggerBossNow} and the tick-driven
     * time mark, which stay separate methods only because Hydra's tick path
     * also needs to flip {@link #hydraSpawned}) since the Devourer's own
     * one-shot guard is {@link #devourerSpawned}, set by the caller.
     */
    public void triggerDevourerNow(ServerLevel level) {
        if (bossActive) {
            return;
        }
        devourerSpawned = true;
        RunManager.broadcastToSlot(level.getServer(), instance.slot(),
                Component.literal("§5§lThe Devourer awakens beneath the arena."));
        DevourerBossFight.spawn(level, instance);
        bossActive = true;
    }

    private void tick(ServerLevel level) {
        if (paused || bossActive) {
            return;
        }
        elapsedTicks++;
        if (elapsedTicks % 20 == 0) {
            refreshRelicThreat();
        }
        threatLevel = Math.min(THREAT_CAP, elapsedTicks * THREAT_RAMP_PER_TICK + bonusThreat + relicThreat);

        if (swarm) {
            tickSwarm(level);
            return;
        }

        if (!hydraSpawned && elapsedTicks >= BOSS_SPAWN_AT_TICK) {
            triggerBossNow(level);
            return;
        }

        if (tier >= 2 && !devourerSpawned && elapsedTicks - tierStartTick >= DEVOURER_SPAWN_AFTER_TIER2_TICKS) {
            triggerDevourerNow(level);
            return;
        }

        if (tier >= 2) {
            tickAmbience(level);
        }

        if (threatLevel >= ELITE_MIN_THREAT && elapsedTicks >= nextEliteSpawnAtTick) {
            nextEliteSpawnAtTick = elapsedTicks + currentEliteSpawnIntervalTicks();
            trySpawnElite(level);
        }

        if (elapsedTicks < nextSpawnAtTick) {
            return;
        }

        int interval = currentSpawnIntervalTicks();
        nextSpawnAtTick = elapsedTicks + interval;

        for (int i = 0; i < SPAWNS_PER_TICK_EVENT; i++) {
            trySpawnOne(level);
        }
    }

    /** Tag on every Final Swarm mob: counted for the crowd, and NOT an elite, so swarm kills never drop free chests. */
    public static final String SWARM_TAG = "emberfall_swarm";

    /**
     * Starts the Final Swarm. Called once, the moment the last boss falls. Ordinary spawning stops for good; from here the
     * crowd, its speed and the silver multiplier all follow {@link FinalSwarm}. Idempotent.
     */
    public void beginSwarm(ServerLevel level) {
        if (swarm) {
            return;
        }
        swarm = true;
        swarmStartTick = elapsedTicks;
        swarmSteps = 0;
        SwarmPortal.open(instance.slot(), bossDeathSpot(level));
        RunManager.broadcastToSlot(level.getServer(), instance.slot(),
                Component.literal("§4§lThe last guardian is dead... and the whole map wakes up. §7A portal has opened where it fell."));
        EmberfallMod.LOGGER.info("Slot {} began the Final Swarm", instance.slot());
    }

    /** True while a boss fight is running (the normal waves are paused). */
    public boolean bossActive() {
        return bossActive;
    }

    public boolean swarmActive() {
        return swarm;
    }

    /** Whole seconds since the swarm began, 0 when it has not. */
    public long swarmSeconds() {
        return swarm ? Math.max(0, (elapsedTicks - swarmStartTick) / 20) : 0;
    }

    /** The silver multiplier right now, in tenths (1 = 0.1x). 0 when there is no swarm. */
    public int swarmTenths() {
        return swarm ? FinalSwarm.tenthsAtStep(FinalSwarm.stepsAfter(swarmSeconds())) : 0;
    }

    /** Debug/test: jumps the swarm clock forward so a test can reach a high multiplier without waiting. */
    public void skipSwarmSeconds(long seconds) {
        if (swarm) {
            swarmStartTick -= Math.max(0, seconds) * 20L;
        }
    }

    private void tickSwarm(ServerLevel level) {
        if (elapsedTicks % 20 != 0) {
            return;
        }
        int steps = FinalSwarm.stepsAfter(swarmSeconds());
        int tenths = FinalSwarm.tenthsAtStep(steps);
        if (steps != swarmSteps) {
            swarmSteps = steps;
            FinalSwarm.Tier tier = FinalSwarm.tierOf(tenths);
            // Announce each whole-number step, and every tier change, not all 49 of them.
            if (tenths % 10 == 0 || FinalSwarm.tierOf(tenths - 1) != tier) {
                RunManager.broadcastToSlot(level.getServer(), instance.slot(),
                        Component.literal("§6Silver multiplier §e" + String.format("%.1f", tenths / 10.0) + "x §7(" + tier.label() + ")"));
            }
            if (FinalSwarm.wrath(tenths)) {
                RunManager.broadcastToSlot(level.getServer(), instance.slot(),
                        Component.literal("§4§lMAXIMUM. The swarm screams. Leave now, or burn."));
            }
        }
        int players = 0;
        for (net.minecraft.server.level.ServerPlayer p : level.players()) {
            Integer slot = RunManager.slotOf(p);
            if (slot != null && slot == instance.slot()) {
                players++;
            }
        }
        int want = FinalSwarm.targetMobs(tenths, players);
        int have = currentHostileCount(level);
        // At most 3 a second, so the crowd arrives as a stream and never as a lag spike, and never above the target or the ceiling.
        for (int i = 0; i < 3 && have < Math.min(want, FinalSwarm.MOB_CEILING); i++, have++) {
            spawnSwarmMob(level, tenths);
        }
        SwarmWrath.tick(level, instance, tenths, elapsedTicks);
    }

    private void spawnSwarmMob(ServerLevel level, int tenths) {
        List<BlockPos> spawnPoints = instance.markers("spawn_point");
        if (spawnPoints.isEmpty()) {
            return;
        }
        BlockPos pos = spawnPoints.get(level.getRandom().nextInt(spawnPoints.size()));
        // Stronger as the multiplier climbs: the same scale an elite gets at full threat, plus the tier-2 bonus, plus up to +1.0 more.
        double statMultiplier = 2.0 + TIER2_ELITE_STAT_BONUS + FinalSwarm.progress(tenths);
        net.minecraft.world.entity.Entity mob = switch (level.getRandom().nextInt(ELITE_COUNT)) {
            case 0 -> CinderbrandReaver.spawn(level, pos, statMultiplier);
            case 1 -> BlightfeatherMarksman.spawn(level, pos, statMultiplier);
            case 2 -> UmbralMagus.spawn(level, pos, statMultiplier);
            case 3 -> CorruptedSentinel.spawn(level, pos, statMultiplier);
            case 4 -> BonecallerNecromancer.spawn(level, pos, statMultiplier);
            case 5 -> com.solme.emberfall.entity.TikiMagma.spawnElite(level, pos, statMultiplier);
            case 6 -> PlagueColossus.spawn(level, pos, statMultiplier);
            case 7 -> BoilRiddenMarksman.spawn(level, pos, statMultiplier);
            default -> BroodmotherStalker.spawn(level, pos, statMultiplier);
        };
        if (mob instanceof Mob m) {
            m.addTag(SWARM_TAG);
            double speed = FinalSwarm.speedBonus(tenths);
            var attr = m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            if (attr != null && speed > 0) {
                attr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                        com.solme.emberfall.EmberfallMod.id("swarm_speed"), speed,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
        totalSpawned++;
    }

    /**
     * Ambient "the world got worse" visual for tier 2 - drifting ash plus
     * occasional soul-fire embers scattered across the arena's bounds, on a
     * flat once-a-second cadence (independent of threatLevel; this is a
     * constant mood signal for "you are in tier 2 now", not a ramping
     * intensity effect). Uses vanilla particle types only (see class
     * javadoc) so it needs no resource pack and matches the corruption
     * visual language already established by Tiki Magma's Corrupted tier
     * and the 5 ported Corrupted archetypes.
     */
    private void tickAmbience(ServerLevel level) {
        if (elapsedTicks - lastAmbienceTick < AMBIENCE_INTERVAL_TICKS) {
            return;
        }
        lastAmbienceTick = elapsedTicks;

        BoundingBox bounds = instance.bounds();
        RandomSource random = level.getRandom();
        double spanX = Math.max(1, bounds.maxX() - bounds.minX());
        double spanY = Math.max(1, bounds.maxY() - bounds.minY());
        double spanZ = Math.max(1, bounds.maxZ() - bounds.minZ());

        for (int i = 0; i < AMBIENCE_BURSTS_PER_TICK; i++) {
            double x = bounds.minX() + random.nextDouble() * spanX;
            double y = bounds.minY() + random.nextDouble() * spanY;
            double z = bounds.minZ() + random.nextDouble() * spanZ;
            level.sendParticles(ParticleTypes.ASH, x, y + 1.0, z, 3, 0.6, 0.6, 0.6, 0.02);
            if (random.nextDouble() < 0.35) {
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 1, 0.2, 0.2, 0.2, 0.01);
            }
        }
    }

    private int currentSpawnIntervalTicks() {
        double t = threatLevel / THREAT_CAP; // 0..1
        int span = BASE_SPAWN_INTERVAL_TICKS - MIN_SPAWN_INTERVAL_TICKS;
        int solo = (int) Math.round(BASE_SPAWN_INTERVAL_TICKS - t * span);
        return com.solme.emberfall.world.PartyScaling.spawnIntervalTicks(solo, MIN_SPAWN_INTERVAL_TICKS, frozenPartySize());
    }

    /** The party size for this run: counted once, on first use, then kept. Never below 1. */
    private int frozenPartySize() {
        // Not before the grace window ends: a party that is still arriving (gate countdown, or /emberfall join in a test) must be
        // fully counted. Until then the answer is "one player" and nothing is frozen, so the first spawns are the solo values.
        if (partySize == 0 && elapsedTicks < PARTY_FREEZE_AT_TICK) {
            return 1;
        }
        if (partySize == 0) {
            partySize = Math.max(1, RunManager.partySize(instance.slot()));
            EmberfallMod.LOGGER.info("PARTYFROZEN slot={} size={}", instance.slot(), partySize);
        }
        return partySize;
    }

    /**
     * Most hostiles alive at once for this run. The solo value is the old HOSTILE_CAP (including the test-only system property), so
     * a solo run is unchanged; a party adds {@code PartyScaling}'s extra on top of it.
     */
    private int hostileCap() {
        int n = frozenPartySize();
        return HOSTILE_CAP + (com.solme.emberfall.world.PartyScaling.hostileCap(n) - com.solme.emberfall.world.PartyScaling.hostileCap(1));
    }

    private int currentEliteSpawnIntervalTicks() {
        double t = threatLevel / THREAT_CAP; // 0..1
        int span = ELITE_BASE_INTERVAL_TICKS - ELITE_MIN_INTERVAL_TICKS;
        int base = (int) Math.round(ELITE_BASE_INTERVAL_TICKS - t * span);
        return tier >= 2 ? Math.max(ELITE_MIN_INTERVAL_TICKS, (int) Math.round(base * TIER2_ELITE_INTERVAL_SCALE)) : base;
    }

    /**
     * Picks one of the 6 elite-track archetypes uniformly and spawns it
     * at a random spawn_point marker, same cap/marker-availability guards as
     * {@link #trySpawnOne}. statMultiplier scales linearly with the run's
     * current threatLevel (1.0 at threat 0 -> 2.0 at THREAT_CAP), the same
     * knob {@code /emberfall spawnelite} takes manually, plus a flat {@link
     * #TIER2_ELITE_STAT_BONUS} once this run has escalated to tier 2.
     */
    private void trySpawnElite(ServerLevel level) {
        if (currentHostileCount(level) >= hostileCap()) {
            return; // at cap - skip this elite spawn tick, per 2.4
        }
        spawnEliteAt(level);
    }

    /** Debug/test entry: the same spawn (and the same elite tag) as the timer, without the hostile-cap guard. */
    public void spawnEliteNow(ServerLevel level) {
        spawnEliteAt(level);
    }

    private void spawnEliteAt(ServerLevel level) {

        List<BlockPos> spawnPoints = instance.markers("spawn_point");
        if (spawnPoints.isEmpty()) {
            return; // no hot spawn markers registered for this arena
        }

        BlockPos pos = spawnPoints.get(level.getRandom().nextInt(spawnPoints.size()));
        double statMultiplier = 1.0 + (threatLevel / THREAT_CAP);
        if (tier >= 2) {
            statMultiplier += TIER2_ELITE_STAT_BONUS;
        }

        net.minecraft.world.entity.Entity elite = switch (level.getRandom().nextInt(ELITE_COUNT)) {
            case 0 -> CinderbrandReaver.spawn(level, pos, statMultiplier);
            case 1 -> BlightfeatherMarksman.spawn(level, pos, statMultiplier);
            case 2 -> UmbralMagus.spawn(level, pos, statMultiplier);
            case 3 -> CorruptedSentinel.spawn(level, pos, statMultiplier);
            case 4 -> BonecallerNecromancer.spawn(level, pos, statMultiplier);
            case 5 -> com.solme.emberfall.entity.TikiMagma.spawnElite(level, pos, statMultiplier);
            case 6 -> PlagueColossus.spawn(level, pos, statMultiplier);
            case 7 -> BoilRiddenMarksman.spawn(level, pos, statMultiplier);
            default -> BroodmotherStalker.spawn(level, pos, statMultiplier);
        };
        // The one place elites are born: mark them so a kill can tell a real elite from any other tier-2 mob.
        if (elite != null) {
            elite.addTag(com.solme.emberfall.relic.ChestManager.ELITE_TAG);
        }
        totalElitesSpawned++;
        EmberfallMod.LOGGER.info("Wave Director spawned an elite for slot {} (statMultiplier {})",
                instance.slot(), String.format("%.2f", statMultiplier));
    }

    /**
     * Horde-filler mix (2026-09-27 elite/horde-variety pass): zombie stays the
     * majority filler (melee grind, the default horde silhouette), skeleton
     * and spider are the minority spice giving the pool a ranged kiter and a
     * fast erratic-movement threat respectively - see {@link HordeSkeleton}/
     * {@link HordeSpider} javadocs. Weights are a starting placeholder split
     * (same "proving the mix works, not a tuned balance pass" spirit as the
     * rest of this class's placeholder constants) - 60% zombie / 20% skeleton
     * / 20% spider.
     */
    private static final double SKELETON_SPAWN_WEIGHT = 0.2;
    private static final double SPIDER_SPAWN_WEIGHT = 0.2;
    private static final double TIKI_SPAWN_WEIGHT = 0.15;
    private static final double WITCH_SPAWN_WEIGHT = 0.12;
    private static final double IMP_SPAWN_WEIGHT = 0.04; // one event = a whole pack (3-5), so this is the share of SPAWN EVENTS
    private static final double SHIELDBEARER_SPAWN_WEIGHT = 0.045;
    private static final double SPITTER_SPAWN_WEIGHT = 0.05;
    private static final double CHARGER_SPAWN_WEIGHT = 0.045;
    private static final double BOMBER_SPAWN_WEIGHT = 0.05; // zombie takes the remainder (0.25), still the horde's biggest single share
    // Cumulative thresholds, computed once, in the SAME order as the else-if chain in trySpawnOne (plain zombie is the
    // remainder). A branch now compares against one named value instead of a hand-written growing sum, so adding a type
    // cannot leave an earlier sum stale.
    private static final double CUT_SKELETON = SKELETON_SPAWN_WEIGHT;
    private static final double CUT_SPIDER = CUT_SKELETON + SPIDER_SPAWN_WEIGHT;
    private static final double CUT_WITCH = CUT_SPIDER + WITCH_SPAWN_WEIGHT;
    private static final double CUT_TIKI = CUT_WITCH + TIKI_SPAWN_WEIGHT;
    private static final double CUT_BOMBER = CUT_TIKI + BOMBER_SPAWN_WEIGHT;
    private static final double CUT_CHARGER = CUT_BOMBER + CHARGER_SPAWN_WEIGHT;
    private static final double CUT_SHIELDBEARER = CUT_CHARGER + SHIELDBEARER_SPAWN_WEIGHT;
    private static final double CUT_SPITTER = CUT_SHIELDBEARER + SPITTER_SPAWN_WEIGHT;
    private static final double CUT_IMP = CUT_SPITTER + IMP_SPAWN_WEIGHT;

    private void trySpawnOne(ServerLevel level) {
        if (currentHostileCount(level) >= hostileCap()) {
            return; // at cap - skip this spawn tick, per 2.4
        }

        List<BlockPos> spawnPoints = instance.markers("spawn_point");
        if (spawnPoints.isEmpty()) {
            return; // no hot spawn markers registered for this arena
        }

        BlockPos pos = spawnPoints.get(level.getRandom().nextInt(spawnPoints.size()));
        double veteranChance = tier >= 2 ? TIER2_VETERAN_CHANCE : VETERAN_CHANCE;
        boolean rollVeteran = level.getRandom().nextDouble() < veteranChance;
        double roll = level.getRandom().nextDouble();

        if (roll < CUT_SKELETON) {
            HordeSkeleton skeleton = new HordeSkeleton(ModEntities.HORDE_SKELETON, level);
            skeleton.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            skeleton.setYRot(level.getRandom().nextFloat() * 360.0F);
            skeleton.equipBow();
            if (rollVeteran) {
                skeleton.becomeVeteran();
                totalVeteransSpawned++;
            }
            com.solme.emberfall.wave.PartyHealth.applyMob(skeleton, frozenPartySize());
            level.addFreshEntity(skeleton);
        } else if (roll < CUT_SPIDER) {
            HordeSpider spider = new HordeSpider(ModEntities.HORDE_SPIDER, level);
            spider.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            spider.setYRot(level.getRandom().nextFloat() * 360.0F);
            if (rollVeteran) {
                spider.becomeVeteran();
                totalVeteransSpawned++;
            }
            com.solme.emberfall.wave.PartyHealth.applyMob(spider, frozenPartySize());
            level.addFreshEntity(spider);
        } else if (roll < CUT_WITCH) {
            com.solme.emberfall.entity.HordeWitch witch = new com.solme.emberfall.entity.HordeWitch(ModEntities.HORDE_WITCH, level);
            witch.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            witch.setYRot(level.getRandom().nextFloat() * 360.0F);
            if (rollVeteran) {
                witch.becomeVeteran();
                totalVeteransSpawned++;
            }
            com.solme.emberfall.wave.PartyHealth.applyMob(witch, frozenPartySize());
            level.addFreshEntity(witch);
        } else if (roll < CUT_TIKI) {
            // TikiMagma.spawn already calls level.addFreshEntity itself (it also has to build/mount
            // its stacked segments right after) - see that method's javadoc.
            com.solme.emberfall.entity.TikiMagma tiki = com.solme.emberfall.entity.TikiMagma.spawn(
                    level, new net.minecraft.world.phys.Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), rollVeteran);
            tiki.setYRot(level.getRandom().nextFloat() * 360.0F);
            if (rollVeteran) {
                totalVeteransSpawned++;
            }
        } else if (roll < CUT_BOMBER) {
            com.solme.emberfall.entity.HordeBomber bomber = new com.solme.emberfall.entity.HordeBomber(ModEntities.HORDE_BOMBER, level);
            bomber.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            bomber.setYRot(level.getRandom().nextFloat() * 360.0F);
            bomber.prepare();
            if (rollVeteran) {
                bomber.becomeVeteran();
                totalVeteransSpawned++;
            }
            com.solme.emberfall.wave.PartyHealth.applyMob(bomber, frozenPartySize());
            level.addFreshEntity(bomber);
        } else if (roll < CUT_CHARGER) {
            com.solme.emberfall.entity.HordeCharger charger = new com.solme.emberfall.entity.HordeCharger(ModEntities.HORDE_CHARGER, level);
            charger.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            charger.setYRot(level.getRandom().nextFloat() * 360.0F);
            charger.prepare();
            if (rollVeteran) {
                charger.becomeVeteran();
                totalVeteransSpawned++;
            }
            com.solme.emberfall.wave.PartyHealth.applyMob(charger, frozenPartySize());
            level.addFreshEntity(charger);
        } else if (roll < CUT_SHIELDBEARER) {
            com.solme.emberfall.entity.HordeShieldbearer shield = new com.solme.emberfall.entity.HordeShieldbearer(ModEntities.HORDE_SHIELDBEARER, level);
            shield.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            shield.setYRot(level.getRandom().nextFloat() * 360.0F);
            shield.prepare();
            if (rollVeteran) {
                shield.becomeVeteran();
                totalVeteransSpawned++;
            }
            com.solme.emberfall.wave.PartyHealth.applyMob(shield, frozenPartySize());
            level.addFreshEntity(shield);
        } else if (roll < CUT_SPITTER) {
            com.solme.emberfall.entity.HordeSpitter spitter = new com.solme.emberfall.entity.HordeSpitter(ModEntities.HORDE_SPITTER, level);
            spitter.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            spitter.setYRot(level.getRandom().nextFloat() * 360.0F);
            spitter.prepare();
            if (rollVeteran) {
                spitter.becomeVeteran();
                totalVeteransSpawned++;
            }
            com.solme.emberfall.wave.PartyHealth.applyMob(spitter, frozenPartySize());
            level.addFreshEntity(spitter);
        } else if (roll < CUT_IMP) {
            // A pack is ONE spawn event, sized to the room left under HOSTILE_CAP so it can never overshoot the cap.
            int pack = com.solme.emberfall.entity.HordeImp.packSize(level.getRandom(), hostileCap(), currentHostileCount(level));
            for (int i = 0; i < pack; i++) {
                com.solme.emberfall.entity.HordeImp imp = new com.solme.emberfall.entity.HordeImp(ModEntities.HORDE_IMP, level);
                imp.setPos(pos.getX() + 0.5 + (level.getRandom().nextDouble() - 0.5) * 2.0, pos.getY() + 1.5 + level.getRandom().nextDouble(),
                        pos.getZ() + 0.5 + (level.getRandom().nextDouble() - 0.5) * 2.0);
                imp.prepare();
                if (rollVeteran) {
                    imp.becomeVeteran();
                }
                com.solme.emberfall.wave.PartyHealth.applyMob(imp, frozenPartySize());
            level.addFreshEntity(imp);
            }
            if (rollVeteran && pack > 0) {
                totalVeteransSpawned++;
            }
        } else {
            HordeZombie zombie = new HordeZombie(ModEntities.HORDE_ZOMBIE, level);
            zombie.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            zombie.setYRot(level.getRandom().nextFloat() * 360.0F);
            if (rollVeteran) {
                zombie.becomeVeteran();
                totalVeteransSpawned++;
            }
            com.solme.emberfall.wave.PartyHealth.applyMob(zombie, frozenPartySize());
            level.addFreshEntity(zombie);
        }
        totalSpawned++;
    }

    private int currentHostileCount(ServerLevel level) {
        AABB box = new AABB(
                instance.bounds().minX(), instance.bounds().minY(), instance.bounds().minZ(),
                instance.bounds().maxX() + 1, instance.bounds().maxY() + 1, instance.bounds().maxZ() + 1);
        return level.getEntitiesOfClass(Mob.class, box,
                m -> m.isAlive() && com.solme.emberfall.combat.AutoAttackSystem.isEmberfallHostile(m)).size();
    }

    public double threatLevel() {
        return threatLevel;
    }

    /**
     * Design doc 8 (Greed Shrine): permanently raises this run's threat
     * level by a fixed amount - "harder waves for the remainder of the
     * run", not a one-tick spike. Additive with the normal time-based
     * ramp and still clamped to THREAT_CAP by {@link #tick}.
     */
    /** Sums the relic threat of this slot's players (see {@link com.solme.emberfall.relic.RelicThreat}); one pass a second, no allocation when nobody holds a relic. */
    private void refreshRelicThreat() {
        java.util.List<Double> perPlayer = null;
        for (net.minecraft.server.level.ServerPlayer p : instance.level().players()) {
            Integer slot = com.solme.emberfall.world.RunManager.slotOf(p);
            if (slot == null || slot != instance.slot() || !com.solme.emberfall.relic.PlayerRelics.active(p)) {
                continue;
            }
            double t = com.solme.emberfall.relic.RelicEffects.stats(p).threatBonus();
            if (t > 0.0) {
                if (perPlayer == null) {
                    perPlayer = new java.util.ArrayList<>(4);
                }
                perPlayer.add(t);
            }
        }
        relicThreat = perPlayer == null ? 0.0 : com.solme.emberfall.relic.RelicThreat.partyThreat(perPlayer);
    }

    public double relicThreat() {
        return relicThreat;
    }

    public void addBonusThreat(double amount) {
        this.bonusThreat += amount;
    }

    public long totalSpawned() {
        return totalSpawned;
    }

    public long totalVeteransSpawned() {
        return totalVeteransSpawned;
    }

    public long totalElitesSpawned() {
        return totalElitesSpawned;
    }
}
