package com.solme.emberfall.entity;

import com.mojang.math.Transformation;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * "The Devourer" - EMBERFALL's 2nd boss. See notes/emberfall/boss-concepts.md
 * for the full design write-up; this is the literal implementation of it.
 *
 * A segmented burrowing worm (Calamity's Devourer of Gods / vanilla
 * Destroyer inspiration, per the owner's steer), built the same
 * brain+display-rig way the retired HydraBrain is, but for a genuinely mobile
 * creature rather than a mostly-stationary one:
 *
 * <ul>
 *   <li>The brain itself IS the head - a fully invisible Silverfish (base
 *   chosen for the same reason the retired HydraHead uses one: it's a cheap,
 *   simple hitbox/AI driver with no visual role of its own) wearing a
 *   minecraft-heads.com "Worm" head display as the only thing a
 *   player actually sees, exactly like HydraHead's dragon-head technique -
 *   just applied to the real boss HP pool this time, not a redirect part.</li>
 *   <li>{@code registerGoals()} is empty (no vanilla wander/hide behavior
 *   to fight a fully custom burrow state machine), but unlike HydraHead
 *   this brain DOES need real ground movement while surfaced, so its
 *   {@code customServerAiStep} override calls {@code super} first to keep
 *   navigation/moveControl ticking alive, then layers the state machine
 *   on top - zero-goal {@code goalSelector.tick()} is a harmless no-op.</li>
 *   <li>the body plates trail through the head's
 *   actual recent path (a rolling position-history buffer sampled per
 *   segment), not a fixed ring offset - correct through turns, unlike a
 *   passenger-riding rig would be for a long mobile chain.</li>
 * </ul>
 *
 * Phase 1 "The Hunt" (100-66% HP): SURFACED (visible/vulnerable, drifts
 * toward the nearest player, fires one committed line-dash partway
 * through the window) -> TELEGRAPH_DIVE (0.5-1s tell, still vulnerable -
 * deliberately NOT invulnerable yet, so an aggressive player can punish
 * the tell instead of the dive being pure-dodge-only) -> BURROWED
 * (invisible/invulnerable, body hidden, lerps underground toward a picked
 * burst point, telegraphs a ground-crack particle warning at that surface
 * point in its last ~0.8s) -> surfaces at the burst point with a knockback
 * + damage AoE, then back to SURFACED.
 *
 * Phase 2 "The Swarm" (<=66% HP, one-time): spawns 2 {@link DevourerSpawn}
 * minis - real independent mobs, not more of this composite rig - the
 * "spitting out mini versions of itself" mechanic the owner asked for.
 * Same burrow cycle continues underneath.
 *
 * Phase 3 "The Frenzy" (<=33% HP, one-time): the whole cycle above is
 * retimed shorter/faster (enraged), each burst now also drops a lingering
 * magma patch that punishes standing in it, and one extra mini spawns at
 * each of the 20%/10% HP breakpoints - a bounded, escalating last stand
 * (max 2(phase2) + 2(breakpoints) = 4 minis for the whole fight, never
 * replenished beyond that - no add-snowballing).
 */
public class DevourerBrain extends Silverfish {
    // minecraft-heads.com "Worm" (ID 129527, Hypixel-NPC-tagged) - the
    // user's own proposed replacement for the head texture, verified
    // 2026-09-27 to decode to a real textures.minecraft.net URL before
    // being hardcoded here, same verification standard as EliteHeads.
    static final String SANDWORM_HEAD_TEXTURE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODE4MDdmZDA2NjYwMDI5MDg0MmFmYzMwZjAxNTJhYjlkODUyODMxMjQ2M2M2YjU3YzhkYjY1MDNhOWQ5NTM5NyJ9fX0=";

    /** minecraft-heads.com "Worm (body)" (ID 129528), same set as the head; verified to decode to a textures.minecraft.net URL. */
    private static final String SEGMENT_HEAD_TEXTURE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODZiMjZhZjJmOGIxOWMwNjFhNzU3YTFlNmQ2Y2IwN2QyZDE1MTUwNjA3ZmNjYTlmOWJjZWJkYjk0Y2NmNjY5In19fQ==";
    /** Glued-chain growth (owner, 2026-10-10: no gaps). Per-link spacing makes the body 22.3 blocks long instead of 28.5, which would open the Coil from a 4.6 to an 11.4 block gap; growing every part by this factor keeps the length (28.5 / 22.26) and the taper. */
    private static final float GLUE_GROWTH = 1.28F;
    private static final float HEAD_DISPLAY_SCALE = 2.1F * GLUE_GROWTH;
    private static final double HEAD_Y_OFFSET = 0.8;
    private static final double HEAD_FORWARD_OFFSET = 0.5;

    /** 19 body segments + the head = 20 parts (28.5 blocks of body): the shortest worm whose ring leaves a fair gap (measured, see devourer_expansion_design.md). */
    private static final int SEGMENT_COUNT = 19;
    /** Distance between neighbouring worm parts along its path, in blocks. */

    // Fractions of max HP at which a one-time phase transition fires.
    private static final double[] PHASE_THRESHOLDS = {0.66, 0.33};
    // Phase-3-only extra mini-wave breakpoints (only ever reachable after
    // phase 3 has already started, since HP has to be this low anyway).
    private static final double[] MINI_WAVE_THRESHOLDS = {0.20, 0.10};

    // Phase 1/2 cycle timing.
    private static final int SURFACED_DURATION_TICKS = 120; // 6s
    private static final int DASH_TRIGGER_TICK = 50; // ~2.5s into the surfaced window
    private static final int DASH_DURATION_TICKS = 14; // ~0.7s
    private static final double DASH_SPEED = 0.85; // blocks/tick horizontal
    private static final double DASH_HIT_RADIUS = 1.4;
    /** Boss Curse: attack damage multiplier and add-spawn multiplier. Both 1.0 with no curse. Set once by {@link #applyCurse}. */
    private float damageScale = com.solme.emberfall.boss.BossTuning.devourerDamageScale();   // the base boss boost; the Boss Curse multiplies on top
    /** Party scaling: the share of each hit that lands, below 1.0 only when the pool is bigger than the max_health attribute can hold. */
    private float partyDamageFactor = 1.0F;

    /** Called once by the boss fight right after creation; stores the factor {@code PartyHealth.applyBoss} returned. */
    public void setPartyDamageFactor(float factor) {
        this.partyDamageFactor = Math.max(0.0001F, Math.min(1.0F, factor));
    }
    private double spawnScale = 1.0;

    /** Applies the run's Boss Curse. Called once right after creation, before the boss is added to the world. */
    public void applyCurse(double statMultiplier, double spawnMultiplier) {
        this.spawnScale = Math.max(1.0, spawnMultiplier);
        if (statMultiplier <= 1.0) {
            return;
        }
        this.damageScale = (float) com.solme.emberfall.boss.BossTuning.withCurse(statMultiplier);
        net.minecraft.world.entity.ai.attributes.AttributeInstance hp = this.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(hp.getBaseValue() * statMultiplier);
            this.setHealth(this.getMaxHealth());
        }
    }

    private static final float DASH_DAMAGE = 7.0F;
    private static final double DASH_KNOCKBACK = 1.1;

    private static final int TELEGRAPH_DIVE_TICKS = 16; // ~0.8s
    private static final int BURROW_DURATION_TICKS = 50; // 2.5s
    private static final int CRACK_TELEGRAPH_LEAD_TICKS = 16; // last ~0.8s of burrow warns the surface point
    private static final double BURROW_DEPTH = 3.0;

    // Leap: rear up (marked landing ring), then a real parabola. Peak/airtime from the measured 0.08 gravity
    // integrator: a 9-block peak needs about 1.26 b/t and hangs about 30 ticks.
    private static final int LEAP_WINDUP_TICKS = 24;
    private static final int LEAP_AIR_TICKS = 30;
    private static final double LEAP_PEAK = 9.0;
    private static final double LEAP_MAX_RANGE = 14.0;
    private static final double LEAP_MIN_RANGE = 4.0;
    private static final double LEAP_LAND_RADIUS = 4.5;
    private static final float LEAP_LAND_DAMAGE = 10.0F;
    private static final double LEAP_LAND_KNOCKBACK = 1.5;
    private static final int LEAP_TRIGGER_TICK = 20;

    private static final double BURST_RADIUS = 3.2;
    private static final float BURST_DAMAGE = 9.0F;
    private static final double BURST_KNOCKBACK = 1.4;
    private static final double BURST_UPWARD = 0.5;

    // Phase 3 retiming (enraged - shorter tells, faster cycle) + lingering hazard.
    private static final double PHASE3_TIME_MULT = 0.65;

    // COIL: the body is laid on a ring round the player with one walkable gap (geometry measured in bot/coil_sim.py).
    private static final double COIL_RADIUS_START = 6.0;   // gap 7.7 blocks
    private static final double COIL_RADIUS_END = 5.5;     // gap 4.6 blocks, still walkable
    private static final int COIL_WINDUP_TICKS = 14;        // 0.7 s telegraph, no damage (every attack needs a wind-up of at least 0.5 s)
    private static final int COIL_HOLD_TICKS = 70;          // 3.5 s
    private static final double COIL_TURN_PER_TICK = 0.035; // radians: the gap circles the player once in ~9 s
    private static final double COIL_HIT_RADIUS = 1.25;     // reach of a body part (displays have no hitbox)
    private static final int COIL_PULSE_TICKS = 10;
    private static final float COIL_DAMAGE = 6.0F;
    private static final double COIL_KNOCKBACK = 0.9;
    /** A coil replaces the leap on every second surfaced window from phase 2 on, so it rotates with the other attacks. */
    private static final int COIL_EVERY_N_SURFACES = 2;
    private static final int LINGER_DURATION_TICKS = 60; // 3s
    private static final double LINGER_RADIUS = 2.2;
    private static final float LINGER_DAMAGE_PER_SECOND = 2.0F;

    private enum State { SURFACED, LEAP_WINDUP, LEAPING, TELEGRAPH_DIVE, BURROWED, COIL_WINDUP, COIL_HOLD }

    private record LingeringPatch(Vec3 pos, long expireTick) {}

    private WormBody worm;
    private final Deque<Double> pendingPhaseThresholds = new ArrayDeque<>();
    private final Deque<Double> pendingMiniWaveThresholds = new ArrayDeque<>();
    private final List<DevourerSpawn> minions = new ArrayList<>();
    private final List<LingeringPatch> lingeringPatches = new ArrayList<>();
    private final Set<UUID> dashHitThisSwing = new HashSet<>();

    private ServerBossEvent bossEvent;
    private boolean rigSpawned = false;
    private boolean defeated = false;

    private int phase = 1;
    private State state = State.SURFACED;
    private int ticksInState = 0;
    private boolean dashFiredThisSurface = false;
    private int dashTicksRemaining = 0;
    private Vec3 dashDirection = Vec3.ZERO;
    private Vec3 burstTarget = Vec3.ZERO;
    private Vec3 leapFrom = Vec3.ZERO;
    private Vec3 leapTo = Vec3.ZERO;
    private boolean leapFiredThisSurface = false;
    private Vec3 coilCentre = Vec3.ZERO;
    private double coilAngle = 0.0;
    private int surfacesSinceCoil = 0;
    private Vec3 burrowStartPos = Vec3.ZERO;
    private Vec3 spawnOrigin = Vec3.ZERO;
    /** Height of the ground the boss last stood on, used as the body's resting floor. */
    private double groundY = Double.NaN;

    private int surfacedDurationTicks = SURFACED_DURATION_TICKS;
    private int dashTriggerTick = DASH_TRIGGER_TICK;
    private int telegraphDurationTicks = TELEGRAPH_DIVE_TICKS;
    private int burrowDurationTicks = BURROW_DURATION_TICKS;

    public DevourerBrain(EntityType<? extends Silverfish> type, Level level) {
        super(type, level);
        this.setInvisible(true); // the real Silverfish model never shows - the WormBody head display is the whole visible head. Never toggled again.
        this.setSilent(true);
    }

    public static AttributeSupplier.Builder createBossAttributes() {
        return Silverfish.createAttributes()
                .add(Attributes.MAX_HEALTH, com.solme.emberfall.boss.BossTuning.devourerHealth())
                .add(Attributes.MOVEMENT_SPEED, com.solme.emberfall.boss.BossTuning.devourerSpeed());
    }

    /** Body segment scale, tapering from 1.7 behind the head to 0.9 at the tail. */
    private static float segmentScale(int i) {
        return (1.7F - 0.8F * i / (SEGMENT_COUNT - 1)) * GLUE_GROWTH;
    }

    /** Builds the full head-display + segment-chain rig. Call once, right after this brain is added to the level. */
    public void spawnRig(ServerLevel level) {
        if (rigSpawned) {
            return;
        }
        rigSpawned = true;
        // Same "must never silently vanilla-despawn mid-fight" requirement
        // as Hydra - a boss fight only ever ends via die().
        this.setPersistenceRequired();
        this.spawnOrigin = this.position();
        this.groundY = this.getY();

        for (double threshold : PHASE_THRESHOLDS) {
            pendingPhaseThresholds.addLast(threshold);
        }
        for (double threshold : MINI_WAVE_THRESHOLDS) {
            pendingMiniWaveThresholds.addLast(threshold);
        }

        float[] sizes = new float[1 + SEGMENT_COUNT];
        sizes[0] = HEAD_DISPLAY_SCALE;
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            sizes[1 + i] = segmentScale(i);
        }
        worm = new WormBody(level, this.position().add(0.0, sizes[0] * 0.5, 0.0), horizontalFacing(),
                headItemStack(), EliteHeads.customHead(SEGMENT_HEAD_TEXTURE, "devourer_seg"),
                sizes);

        bossEvent = new ServerBossEvent(
                this.getDisplayName() != null ? this.getDisplayName() : Component.literal("The Devourer"),
                BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.NOTCHED_10);

        com.solme.emberfall.EmberfallMod.LOGGER.info("Devourer boss rig spawned: {} worm parts", worm.size());
    }

    private static ItemStack headItemStack() {
        return EliteHeads.customHead(SANDWORM_HEAD_TEXTURE, "devourer_head");
    }

    @Override
    protected void registerGoals() {
        // Deliberately empty - see class javadoc. All movement is the
        // custom burrow/dash state machine below, driven through
        // customServerAiStep, not vanilla Silverfish goals.
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.getEntity() == this) {
            return false;
        }
        // Party pool beyond the attribute ceiling: scale the hit, but never a void/kill hit, so a boss can always be removed.
        if (partyDamageFactor < 1.0F && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            amount *= partyDamageFactor;
        }
        return super.hurtServer(level, source, amount);
    }

    @Override
    public void tick() {
        super.tick();
        // Vanilla LivingEntity keeps ticking a dead mob for its ~20-tick
        // death animation before actually removing it - concretely
        // observed via server log ordering: "entered phase 3" logged
        // AFTER "Devourer boss died"/teardownRig had already run, from
        // checkPhaseThresholds still executing in that window. Once
        // die() has fired, none of this custom logic may run again -
        // teardownRig already tore everything down.
        if (defeated || !rigSpawned || !(this.level() instanceof ServerLevel level)) {
            return;
        }
        updateBossBar(level);
        checkPhaseThresholds(level);
        updateLingeringPatches(level);
        recoverIfOutOfBounds(level);
    }

    /**
     * Belt-and-suspenders safety net alongside the dash/burst-target ground
     * checks above: if the brain is ever more than a few blocks below its
     * own recorded spawn floor while NOT deliberately burrowed (BURROW_DEPTH
     * is only 3), something got it off the platform anyway (a future attack
     * added later, a knockback interaction, anything) - teleport it back to
     * the spawn point and reset the cycle rather than let it free-fall into
     * the void indefinitely.
     */
    private void recoverIfOutOfBounds(ServerLevel level) {
        if (state == State.BURROWED) {
            return;
        }
        if (this.getY() >= spawnOrigin.y - (BURROW_DEPTH + 2.0)) {
            return;
        }
        com.solme.emberfall.EmberfallMod.LOGGER.warn(
                "Devourer fell out of the arena bounds (y={}) - recovering to spawn point", this.getY());
        this.setPos(spawnOrigin.x, spawnOrigin.y, spawnOrigin.z);
        this.setDeltaMovement(Vec3.ZERO);
        dashTicksRemaining = 0;
        enterState(State.SURFACED);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level); // keeps navigation/moveControl ticking alive despite empty goals
        if (defeated || !rigSpawned) {
            return;
        }
        updateWorm();
        runStateMachine(level);
    }

    private void updateBossBar(ServerLevel level) {
        if (bossEvent == null) {
            return;
        }
        bossEvent.setProgress(net.minecraft.util.Mth.clamp(this.getHealth() / this.getMaxHealth(), 0.0F, 1.0F));
        for (ServerPlayer player : level.players()) {
            double distSq = player.distanceToSqr(this);
            boolean shouldTrack = distSq < 64.0 * 64.0;
            boolean tracking = bossEvent.getPlayers().contains(player);
            if (shouldTrack && !tracking) {
                bossEvent.addPlayer(player);
            } else if (!shouldTrack && tracking) {
                bossEvent.removePlayer(player);
            }
        }
    }

    // ---- worm body ----

    private Vec3 horizontalFacing() {
        Vec3 v = this.getViewVector(1.0F);
        return new Vec3(v.x, 0.0, v.z);
    }

    /** Head centre sits half its own size above the feet so the head rests ON the floor, never in it. */
    private void updateWorm() {
        if (worm == null) {
            return;
        }
        if (state == State.COIL_WINDUP || state == State.COIL_HOLD) {
            return;   // the ring is placed directly by the coil handlers; the chain solve would drag it back into a line
        }
        worm.setHeadDirection(this.getViewVector(1.0F));
        // The body must rest on the ground the boss ACTUALLY stands on, not the spawn floor: a leap can land on
        // higher ground (a WIDEDBG log showed the landing at y=76 against a spawn floor of 69, which left the body
        // sagging toward the wrong floor and stretched the first link past 3 blocks). Only a standing boss updates
        // it, so the burrow dive and the leap arc keep the last real ground.
        if (state == State.SURFACED || state == State.TELEGRAPH_DIVE || state == State.LEAP_WINDUP) {
            groundY = this.getY();
        }
        worm.update(this.position().add(0.0, HEAD_DISPLAY_SCALE * 0.5, 0.0), groundY);
    }

    // ---- phase transitions ----

    private void checkPhaseThresholds(ServerLevel level) {
        double fraction = this.getMaxHealth() > 0 ? this.getHealth() / this.getMaxHealth() : 0;
        if (!pendingPhaseThresholds.isEmpty() && fraction <= pendingPhaseThresholds.peekFirst()) {
            pendingPhaseThresholds.pollFirst();
            enterPhase(phase + 1, level);
        }
        if (phase >= 3 && !pendingMiniWaveThresholds.isEmpty() && fraction <= pendingMiniWaveThresholds.peekFirst()) {
            pendingMiniWaveThresholds.pollFirst();
            spawnMiniWorms(level, 1);
            level.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.HOSTILE, 1.2F, 0.7F);
        }
    }

    private void enterPhase(int newPhase, ServerLevel level) {
        this.phase = Math.min(3, newPhase);
        applyPhaseTiming();
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.6F, phase >= 3 ? 0.5F : 0.7F);
        if (phase == 2) {
            spawnMiniWorms(level, 2);
        }
        com.solme.emberfall.EmberfallMod.LOGGER.info("Devourer entered phase {}", phase);
    }

    private void applyPhaseTiming() {
        if (phase >= 3) {
            surfacedDurationTicks = (int) (SURFACED_DURATION_TICKS * PHASE3_TIME_MULT);
            dashTriggerTick = (int) (DASH_TRIGGER_TICK * PHASE3_TIME_MULT);
            telegraphDurationTicks = (int) (TELEGRAPH_DIVE_TICKS * PHASE3_TIME_MULT);
            burrowDurationTicks = (int) (BURROW_DURATION_TICKS * PHASE3_TIME_MULT);
        } else {
            surfacedDurationTicks = SURFACED_DURATION_TICKS;
            dashTriggerTick = DASH_TRIGGER_TICK;
            telegraphDurationTicks = TELEGRAPH_DIVE_TICKS;
            burrowDurationTicks = BURROW_DURATION_TICKS;
        }
    }

    /**
     * Add count under a Boss Curse. Plain rounding swallowed the curse at low tiers (x1.1 of 1 or 2 adds rounds back to 1 or 2),
     * so the whole part always spawns and the fractional part is a chance: x1.1 of 2 adds is 2, plus a 20% chance of a third.
     * The expected count then matches the multiplier at every tier. Never below the base count.
     */
    static int scaledAddCount(int baseCount, double multiplier, double roll) {
        double exact = baseCount * Math.max(1.0, multiplier);
        int whole = (int) Math.floor(exact);
        return Math.max(baseCount, whole + (roll < exact - whole ? 1 : 0));
    }

    private void spawnMiniWorms(ServerLevel level, int baseCount) {
        int count = scaledAddCount(baseCount, spawnScale, this.getRandom().nextDouble());
        for (int i = 0; i < count; i++) {
            double angle = this.getRandom().nextDouble() * Math.PI * 2;
            double x = this.getX() + Math.cos(angle) * 1.5;
            double z = this.getZ() + Math.sin(angle) * 1.5;
            DevourerSpawn mini = new DevourerSpawn(ModEntities.DEVOURER_SPAWN, level);
            mini.setPos(x, this.getY(), z);
            mini.setPersistenceRequired();
            level.addFreshEntity(mini);
            mini.attachDisplay(level);
            minions.add(mini);
        }
        level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 0.3, this.getZ(),
                12, 0.5, 0.2, 0.5, 0.02);
    }

    // ---- Phase 1/2/3 burrow-dash state machine ----

    private void runStateMachine(ServerLevel level) {
        switch (state) {
            case SURFACED -> handleSurfaced(level);
            case LEAP_WINDUP -> handleLeapWindup(level);
            case LEAPING -> handleLeaping(level);
            case TELEGRAPH_DIVE -> handleTelegraphDive(level);
            case BURROWED -> handleBurrowed(level);
            case COIL_WINDUP -> handleCoilWindup(level);
            case COIL_HOLD -> handleCoilHold(level);
        }
    }

    private void enterState(State next) {
        this.state = next;
        this.ticksInState = 0;
    }

    private void handleSurfaced(ServerLevel level) {
        ticksInState++;
        Player target = level.getNearestPlayer(this, 32.0);
        if (target != null) {
            this.lookAt(target, 60.0F, 60.0F);
            if (dashTicksRemaining <= 0 && ticksInState % 10 == 0) {
                this.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), 1.0);
            }
            if (!leapFiredThisSurface && dashTicksRemaining <= 0 && ticksInState >= LEAP_TRIGGER_TICK
                    && ticksInState % 5 == 0 && beginLeapWindup(level, target)) {
                // Only a leap that really started consumes the attack. A refused attempt (target too close, no
                // safe landing) is retried every 5 ticks for the rest of the window instead of being burned.
                leapFiredThisSurface = true;
                return;
            }
            if (!dashFiredThisSurface && ticksInState >= dashTriggerTick) {
                dashFiredThisSurface = true;
                startDash(level, target);
            }
        }
        runDash(level);
        if (ticksInState >= surfacedDurationTicks) {
            dashFiredThisSurface = false;
            leapFiredThisSurface = false;
            surfacesSinceCoil++;
            if (phase >= 2 && surfacesSinceCoil >= COIL_EVERY_N_SURFACES && target != null && beginCoil(level, target)) {
                surfacesSinceCoil = 0;
                return;
            }
            enterState(State.TELEGRAPH_DIVE);
        }
    }

    /** True when ground exists all round a ring of the given radius, so the body never hangs over the void or the wall. */
    private boolean ringFits(ServerLevel level, Vec3 centre, double radius) {
        for (int k = 0; k < 16; k++) {
            double a = k * Math.PI / 8.0;
            if (!hasSolidGroundBelow(level, centre.x + Math.cos(a) * radius, spawnOrigin.y, centre.z + Math.sin(a) * radius)) {
                return false;
            }
        }
        return true;
    }

    private boolean beginCoil(ServerLevel level, Player target) {
        Vec3 centre = new Vec3(target.getX(), spawnOrigin.y, target.getZ());
        if (!ringFits(level, centre, COIL_RADIUS_START)) {
            return false;   // too close to the rim: skip this coil instead of hanging the body over nothing
        }
        coilCentre = centre;   // locked for the whole coil, so the ring is learnable and the player can walk out of it
        // start with the gap on the far side of the boss: the head begins nearest the boss's own position
        coilAngle = Math.atan2(this.getZ() - centre.z, this.getX() - centre.x);
        this.getNavigation().stop();
        enterState(State.COIL_WINDUP);
        if (worm != null) {
            worm.setThreat(true);
        }
        com.solme.emberfall.EmberfallMod.LOGGER.info("Devourer COIL windup: centre {} radius {}", coilCentre, COIL_RADIUS_START);
        level.playSound(null, coilCentre.x, coilCentre.y, coilCentre.z,
                SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.6F, 0.5F);
        return true;
    }

    /** Draws the ring footprint as dust points (wind-up only, every 4 ticks, 1 point per 2 blocks: cheap). */
    private void coilTelegraph(ServerLevel level, double radius) {
        int points = (int) Math.ceil(2.0 * Math.PI * radius / 2.0);
        for (int k = 0; k < points; k++) {
            double a = k * 2.0 * Math.PI / points;
            level.sendParticles(ParticleTypes.SMALL_FLAME, coilCentre.x + Math.cos(a) * radius, coilCentre.y + 0.15,
                    coilCentre.z + Math.sin(a) * radius, 1, 0.05, 0.02, 0.05, 0.0);
        }
    }

    private void handleCoilWindup(ServerLevel level) {
        ticksInState++;
        placeRing(COIL_RADIUS_START);
        if (ticksInState % 4 == 1) {
            coilTelegraph(level, COIL_RADIUS_START);
        }
        if (ticksInState >= COIL_WINDUP_TICKS) {
            enterState(State.COIL_HOLD);
            dashHitThisSwing.clear();
        }
    }

    private void handleCoilHold(ServerLevel level) {
        ticksInState++;
        double t = Math.min(1.0, ticksInState / (double) COIL_HOLD_TICKS);
        double radius = Mth.lerp(t, COIL_RADIUS_START, COIL_RADIUS_END);
        coilAngle += COIL_TURN_PER_TICK;    // the gap circles the player
        placeRing(radius);
        if (ticksInState % COIL_PULSE_TICKS == 0) {
            coilPulse(level);
        }
        if (ticksInState >= COIL_HOLD_TICKS) {
            endCoil(level);
        }
    }

    /** Lays the body on the ring and keeps the (invisible) brain under the head so hits, targeting and the boss bar follow it. */
    private void placeRing(double radius) {
        if (worm == null) {
            return;
        }
        // Same heights as the chain path: brain on the ground the boss stands on, head display half a head-scale above it.
        worm.placeOnRing(coilCentre, radius, coilAngle, groundY, groundY + HEAD_DISPLAY_SCALE * 0.5);
        Vec3 head = worm.partPos(0);
        this.setPos(head.x, groundY, head.z);
    }

    /** Every body part is a damage source for a player standing within reach of it. */
    private void coilPulse(ServerLevel level) {
        if (worm == null) {
            return;
        }
        // Snapshot: hurtServer can kill a player, which ends the run and removes them from this level mid-iteration.
        for (ServerPlayer player : new java.util.ArrayList<>(level.players())) {
            for (int i = 0; i < worm.size(); i++) {
                Vec3 p = worm.partPos(i);
                double dx = player.getX() - p.x;
                double dz = player.getZ() - p.z;
                if (dx * dx + dz * dz <= COIL_HIT_RADIUS * COIL_HIT_RADIUS && Math.abs(player.getY() - coilCentre.y) < 3.0) {
                    player.hurtServer(level, level.damageSources().mobAttack(this), COIL_DAMAGE * damageScale);
                    // knock the player away from the ring's line, toward the open middle
                    double len = Math.max(0.001, Math.hypot(coilCentre.x - player.getX(), coilCentre.z - player.getZ()));
                    player.push((coilCentre.x - player.getX()) / len * COIL_KNOCKBACK, 0.25, (coilCentre.z - player.getZ()) / len * COIL_KNOCKBACK);
                    player.hurtMarked = true;
                    com.solme.emberfall.EmberfallMod.LOGGER.info("Devourer COIL hit {} via part {}", player.getName().getString(), i);
                    break;   // one hit per pulse per player, never one per overlapping part
                }
            }
        }
    }

    private void endCoil(ServerLevel level) {
        if (worm != null) {
            worm.setThreat(false);
        }
        com.solme.emberfall.EmberfallMod.LOGGER.info("Devourer COIL ended");
        enterState(State.TELEGRAPH_DIVE);
    }

    /** Picks a landing spot on real ground near the target. Returns false (no leap) if none is safe. */
    private boolean beginLeapWindup(ServerLevel level, Player target) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < LEAP_MIN_RANGE) {
            return false;
        }
        double reach = Math.min(dist, LEAP_MAX_RANGE);
        for (int attempt = 0; attempt < 4; attempt++) {
            double f = reach * (1.0 - attempt * 0.2) / dist;
            double x = this.getX() + dx * f;
            double z = this.getZ() + dz * f;
            if (hasSolidGroundBelow(level, x, spawnOrigin.y, z)) {
                leapFrom = this.position();
                leapTo = new Vec3(x, spawnOrigin.y, z);
                this.getNavigation().stop();
                this.lookAt(target, 60.0F, 60.0F);
                enterState(State.LEAP_WINDUP);
                if (worm != null) {
                    worm.setThreat(true);
                }
                com.solme.emberfall.EmberfallMod.LOGGER.info("Devourer LEAP windup: from {} to {} (range {})", leapFrom, leapTo, String.format("%.1f", dist));
                level.playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.8F, 0.6F);
                return true;
            }
        }
        return false;
    }

    private void handleLeapWindup(ServerLevel level) {
        ticksInState++;
        this.getNavigation().stop();
        // The landing ring is the tell. Drawn every 4 ticks (telegraph cost rule) and sized to the real damage radius.
        if (ticksInState % 4 == 0) {
            int points = 24;
            for (int i = 0; i < points; i++) {
                double a = (Math.PI * 2 * i) / points;
                level.sendParticles(ParticleTypes.FLAME, leapTo.x + Math.cos(a) * LEAP_LAND_RADIUS,
                        leapTo.y + 0.15, leapTo.z + Math.sin(a) * LEAP_LAND_RADIUS, 1, 0.0, 0.0, 0.0, 0.0);
            }
            level.sendParticles(ParticleTypes.LAVA, leapTo.x, leapTo.y + 0.2, leapTo.z, 2, 0.6, 0.1, 0.6, 0.0);
        }
        // Rear up: a shallow crouch then a lift, so the head visibly winds back before the jump.
        double t = ticksInState / (double) LEAP_WINDUP_TICKS;
        this.setPos(leapFrom.x, leapFrom.y + Math.sin(t * Math.PI) * 0.6, leapFrom.z);
        if (ticksInState >= LEAP_WINDUP_TICKS) {
            this.noPhysics = true;
            this.setNoGravity(true);
            this.fallDistance = 0.0F;
            enterState(State.LEAPING);
            com.solme.emberfall.EmberfallMod.LOGGER.info("Devourer LEAP airborne");
            level.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.4F, 1.4F);
        }
    }

    private void handleLeaping(ServerLevel level) {
        ticksInState++;
        double s = Math.min(1.0, ticksInState / (double) LEAP_AIR_TICKS);
        double x = Mth.lerp(s, leapFrom.x, leapTo.x);
        double z = Mth.lerp(s, leapFrom.z, leapTo.z);
        double y = Mth.lerp(s, leapFrom.y, leapTo.y) + 4.0 * LEAP_PEAK * s * (1.0 - s);
        this.setPos(x, y, z);
        level.sendParticles(ParticleTypes.FLAME, x, y + 0.5, z, 3, 0.4, 0.4, 0.4, 0.01);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.5, z, 1, 0.3, 0.3, 0.3, 0.0);
        if (ticksInState >= LEAP_AIR_TICKS) {
            landLeap(level);
        }
    }

    private void landLeap(ServerLevel level) {
        com.solme.emberfall.EmberfallMod.LOGGER.info("Devourer LEAP landed at {}", leapTo);
        this.setPos(leapTo.x, leapTo.y, leapTo.z);
        this.noPhysics = false;
        this.setNoGravity(false);
        this.fallDistance = 0.0F;
        level.playSound(null, leapTo.x, leapTo.y, leapTo.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.0F, 0.7F);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, leapTo.x, leapTo.y + 0.3, leapTo.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.LAVA, leapTo.x, leapTo.y + 0.3, leapTo.z, 30, LEAP_LAND_RADIUS * 0.5, 0.2, LEAP_LAND_RADIUS * 0.5, 0.0);
        // Snapshot: hurtServer can kill a player, which ends the run and removes them from this level mid-iteration.
        for (ServerPlayer player : new java.util.ArrayList<>(level.players())) {
            double dx = player.getX() - leapTo.x;
            double dz = player.getZ() - leapTo.z;
            double distSq = dx * dx + dz * dz;
            if (distSq > LEAP_LAND_RADIUS * LEAP_LAND_RADIUS || Math.abs(player.getY() - leapTo.y) > 3.0) {
                continue;
            }
            player.hurtServer(level, level.damageSources().mobAttack(this), LEAP_LAND_DAMAGE * damageScale);
            double dist = Math.max(0.1, Math.sqrt(distSq));
            player.push((dx / dist) * LEAP_LAND_KNOCKBACK, 0.6, (dz / dist) * LEAP_LAND_KNOCKBACK);
            player.hurtMarked = true;
        }
        if (phase >= 3) {
            lingeringPatches.add(new LingeringPatch(leapTo, level.getGameTime() + LINGER_DURATION_TICKS));
        }
        if (worm != null) {
            worm.setThreat(false);
        }
        enterState(State.SURFACED);
        ticksInState = LEAP_TRIGGER_TICK; // resume mid-window: the dash and the dive still follow, the leap does not repeat
    }

    private void startDash(ServerLevel level, Player target) {
        dashTicksRemaining = DASH_DURATION_TICKS;
        Vec3 dir = new Vec3(target.getX() - this.getX(), 0.0, target.getZ() - this.getZ());
        dashDirection = dir.lengthSqr() > 0.001 ? dir.normalize() : this.getViewVector(1.0F);
        dashHitThisSwing.clear();
        this.getNavigation().stop();
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.2F, 1.3F);
    }

    private void runDash(ServerLevel level) {
        if (dashTicksRemaining <= 0) {
            return;
        }
        Vec3 next = this.position().add(dashDirection.scale(DASH_SPEED));
        if (!hasSolidGroundBelow(level, next.x, next.y, next.z)) {
            // The arena is a bounded platform, not infinite terrain - a
            // committed line-charge that happened to aim at the edge must
            // stop AT the edge, not carry the brain off into the void
            // (concretely reproduced via the diagnostic hurtServer log:
            // repeated "outOfWorld" damage source hits after a dash).
            dashTicksRemaining = 0;
            return;
        }
        dashTicksRemaining--;
        this.setPos(next.x, next.y, next.z);
        level.sendParticles(ParticleTypes.CRIT, next.x, next.y + 0.3, next.z, 3, 0.2, 0.1, 0.2, 0.02);
        // Snapshot: hurtServer can kill a player, which ends the run and removes them from this level mid-iteration.
        for (ServerPlayer player : new java.util.ArrayList<>(level.players())) {
            if (dashHitThisSwing.contains(player.getUUID())) {
                continue;
            }
            if (player.distanceToSqr(next.x, next.y, next.z) <= DASH_HIT_RADIUS * DASH_HIT_RADIUS) {
                dashHitThisSwing.add(player.getUUID());
                player.hurtServer(level, level.damageSources().mobAttack(this), DASH_DAMAGE * damageScale);
                Vec3 push = dashDirection.scale(DASH_KNOCKBACK);
                player.push(push.x, 0.3, push.z);
                player.hurtMarked = true;
            }
        }
    }

    /** Ground exists (non-air) directly beneath the given point - the arena-bounds guard used by dash/burst-target picking. */
    private boolean hasSolidGroundBelow(ServerLevel level, double x, double y, double z) {
        BlockPos below = BlockPos.containing(x, y - 0.1, z);
        return !level.getBlockState(below).isAir();
    }

    private void handleTelegraphDive(ServerLevel level) {
        ticksInState++;
        double progress = ticksInState / (double) telegraphDurationTicks;
        // Purely a particle/sound tell - still visible/vulnerable through
        // the whole thing (deliberately not invulnerable yet, so an
        // aggressive player can punish the wind-up instead of the dive
        // being pure-dodge-only). Deliberately NOT a position change: an
        // earlier version cosmetically sank this.getY() each tick, which
        // embedded the hitbox in the floor and caused real "inWall"
        // suffocation damage - concretely reproduced via a diagnostic
        // hurtServer log, not guessed. Ground-level particles read as
        // "cracking/sinking" without ever moving the actual entity.
        level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 0.1, this.getZ(),
                2, 0.3, 0.02, 0.3, 0.01);
        level.sendParticles(ParticleTypes.SMALL_FLAME, this.getX(), this.getY() + 0.05, this.getZ(),
                1, 0.15, 0.01, 0.15, 0.0);
        if (progress >= 1.0) {
            burstTarget = pickBurstTarget(level);
            burrowStartPos = this.position();
            beginBurrowed(level);
        }
    }

    private Vec3 pickBurstTarget(ServerLevel level) {
        Player target = level.getNearestPlayer(this, 32.0);
        double baseX = target != null ? target.getX() : this.getX();
        double baseZ = target != null ? target.getZ() : this.getZ();
        // Randomized offset so it's a real dodge check, not a guaranteed
        // direct hit unless the player stands still through the whole tell -
        // but never past the edge of the arena platform: a handful of
        // shrinking retries, then a guaranteed-safe fallback (the brain's
        // own current, necessarily-on-solid-ground position).
        for (int attempt = 0; attempt < 4; attempt++) {
            double offsetAngle = this.getRandom().nextDouble() * Math.PI * 2;
            double offsetRadius = this.getRandom().nextDouble() * (2.0 / (attempt + 1));
            double x = baseX + Math.cos(offsetAngle) * offsetRadius;
            double z = baseZ + Math.sin(offsetAngle) * offsetRadius;
            if (hasSolidGroundBelow(level, x, spawnOrigin.y, z)) {
                return new Vec3(x, spawnOrigin.y, z);
            }
        }
        return new Vec3(this.getX(), spawnOrigin.y, this.getZ());
    }

    private void beginBurrowed(ServerLevel level) {
        enterState(State.BURROWED);
        this.setInvulnerable(true);
        this.noPhysics = true;
        this.setNoGravity(true);
        // Stale accumulated fall-distance from normal ground movement must
        // not survive into the noPhysics/noGravity manual-position phase -
        // otherwise re-enabling gravity on resurface can apply spurious
        // fall damage for a "fall" that was actually a teleport (confirmed
        // via the same diagnostic hurtServer log as the inWall fix above).
        this.fallDistance = 0.0F;
        if (worm != null) {
            worm.setVisible(false);
        }
        level.playSound(null, burrowStartPos.x, burrowStartPos.y, burrowStartPos.z,
                SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 1.4F, 0.5F);
    }

    private void handleBurrowed(ServerLevel level) {
        ticksInState++;
        double t = Math.min(1.0, ticksInState / (double) burrowDurationTicks);
        double x = Mth.lerp(t, burrowStartPos.x, burstTarget.x);
        double z = Mth.lerp(t, burrowStartPos.z, burstTarget.z);
        this.setPos(x, burstTarget.y - BURROW_DEPTH, z);

        // A low rumble under the player's feet once a second says "it is coming" without a single particle.
        if (ticksInState % 20 == 0) {
            level.playSound(null, x, burstTarget.y, z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.6F, 0.5F);
        }

        int ticksRemaining = burrowDurationTicks - ticksInState;
        if (ticksRemaining >= 0 && ticksRemaining <= CRACK_TELEGRAPH_LEAD_TICKS) {
            spawnCrackTelegraph(level);
        }

        if (ticksInState >= burrowDurationTicks) {
            executeSurfaceBurst(level);
        }
    }

    private void spawnCrackTelegraph(ServerLevel level) {
        int points = 8;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2 * i) / points;
            double x = burstTarget.x + Math.cos(angle) * 0.6;
            double z = burstTarget.z + Math.sin(angle) * 0.6;
            level.sendParticles(ParticleTypes.CRIT, x, burstTarget.y + 0.1, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        if (ticksInState % 4 == 0) {
            level.sendParticles(ParticleTypes.SMALL_FLAME, burstTarget.x, burstTarget.y + 0.1, burstTarget.z,
                    2, 0.3, 0.05, 0.3, 0.01);
            level.playSound(null, burstTarget.x, burstTarget.y, burstTarget.z,
                    SoundEvents.LAVA_POP, SoundSource.HOSTILE, 0.7F, 1.3F);
        }
    }

    private void executeSurfaceBurst(ServerLevel level) {
        this.setPos(burstTarget.x, burstTarget.y, burstTarget.z);
        this.setInvulnerable(false);
        this.noPhysics = false;
        this.setNoGravity(false);
        this.fallDistance = 0.0F;
        if (worm != null) {
            worm.setVisible(true);
            // updateWorm() runs BEFORE the state machine each tick, so on the burst tick the displays were last written at the
            // burrow position (head 2 blocks under the floor) while the brain is already back on it: one visible tick with
            // the head underground and the first link stretched to 2.9 (seen as head y=63.00 against brain y=65.00 in 2 of 5
            // runs). Re-place the worm now, in the same tick as the reveal.
            updateWorm();
        }

        level.playSound(null, burstTarget.x, burstTarget.y, burstTarget.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.5F, 0.9F);
        level.sendParticles(ParticleTypes.EXPLOSION, burstTarget.x, burstTarget.y + 0.3, burstTarget.z,
                1, 0.0, 0.0, 0.0, 0.0);

        // Snapshot: hurtServer can kill a player, which ends the run and removes them from this level mid-iteration.
        for (ServerPlayer player : new java.util.ArrayList<>(level.players())) {
            double dx = player.getX() - burstTarget.x;
            double dz = player.getZ() - burstTarget.z;
            double distSq = dx * dx + dz * dz;
            if (distSq > BURST_RADIUS * BURST_RADIUS) {
                continue;
            }
            player.hurtServer(level, level.damageSources().mobAttack(this), BURST_DAMAGE * damageScale);
            double dist = Math.max(0.1, Math.sqrt(distSq));
            player.push((dx / dist) * BURST_KNOCKBACK, BURST_UPWARD, (dz / dist) * BURST_KNOCKBACK);
            player.hurtMarked = true;
        }

        if (phase >= 3) {
            lingeringPatches.add(new LingeringPatch(burstTarget, level.getGameTime() + LINGER_DURATION_TICKS));
        }

        enterState(State.SURFACED);
    }

    private void updateLingeringPatches(ServerLevel level) {
        if (lingeringPatches.isEmpty()) {
            return;
        }
        long gameTime = level.getGameTime();
        lingeringPatches.removeIf(patch -> gameTime >= patch.expireTick());
        if (gameTime % 10 == 0) {
            for (LingeringPatch patch : lingeringPatches) {
                level.sendParticles(ParticleTypes.SMALL_FLAME, patch.pos().x, patch.pos().y + 0.1, patch.pos().z,
                        4, LINGER_RADIUS * 0.5, 0.05, LINGER_RADIUS * 0.5, 0.01);
            }
        }
        if (gameTime % 20 != 0) {
            return;
        }
        for (LingeringPatch patch : lingeringPatches) {
            // Snapshot: hurtServer can kill a player, which ends the run and removes them from this level mid-iteration.
            for (ServerPlayer player : new java.util.ArrayList<>(level.players())) {
                if (player.distanceToSqr(patch.pos().x, patch.pos().y, patch.pos().z) <= LINGER_RADIUS * LINGER_RADIUS) {
                    player.hurtServer(level, level.damageSources().generic(), LINGER_DAMAGE_PER_SECOND * damageScale);
                }
            }
        }
    }

    // ---- lifecycle ----

    public boolean wasDefeated() {
        return defeated;
    }

    public void teardownRig(ServerLevel level) {
        if (worm != null) {
            worm.discard();
            worm = null;
        }
        for (DevourerSpawn mini : List.copyOf(minions)) {
            if (mini.isAlive()) {
                mini.discard();
            }
        }
        minions.clear();
        lingeringPatches.clear();
        if (bossEvent != null) {
            bossEvent.removeAllPlayers();
            bossEvent = null;
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        defeated = true;
        if (this.level() instanceof ServerLevel level) {
            com.solme.emberfall.EmberfallMod.LOGGER.info("Devourer boss died");
            teardownRig(level);
        }
    }
}
