package com.solme.emberfall.entity;

import com.mojang.math.Transformation;
import com.solme.emberfall.EmberfallMod;
import com.solme.emberfall.combat.Fx;
import com.solme.emberfall.world.TerrainScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * "The Ember Guardian" - EMBERFALL's 1st boss, replacing the Hydra (user decision, 2026-09-30). Design and open
 * questions: notes/emberfall/hydra-replacement-design.md.
 *
 * Built like {@link DevourerBrain}: the brain is a fully invisible Silverfish (one cheap hitbox and HP pool), and every
 * visible part is a display entity. That keeps the whole boss at a handful of entities. All player weapons deal damage
 * through {@code mob.hurtServer(level, playerAttack(player), amount)}, so gating the core's armour is done entirely in
 * this class's own {@link #hurtServer}, with no change to any weapon.
 *
 * Lifecycle contract (same as the Hydra it replaces, so BossFight, WaveDirector, telemetry and rewards keep working):
 * {@link #wasDefeated()} is true only after a genuine {@link #die}, {@link #teardownRig} is idempotent, and no custom
 * logic runs once the boss has died (vanilla keeps ticking a dying mob for about 20 ticks).
 */
public class EmberGuardian extends Silverfish {
    /**
     * The head is a stack of three layers (a wide dark base, a magma waist and a small crown) around the glowing core,
     * like a layered magma cube. The core keeps its old height: the pylon beams, the exposure burst and the relight all
     * aim at {@link #CORE_Y}, so moving it would move those effects.
     */
    static final float BASE_SCALE = 4.2F;
    static final float WAIST_SCALE = 3.4F;
    static final float CROWN_SCALE = 2.0F;
    static final float CORE_SCALE = 1.5F;
    static final double BASE_Y = 2.1;
    static final double WAIST_Y = 3.2;
    static final double CROWN_Y = 4.6;
    static final double CORE_Y = 2.9;
    /**
     * Tentacles: {@value #TENTACLES} chains of {@value #TENTACLE_LINKS} displays. The whole boss is then
     * 4 head parts + 32 tentacle parts; the Devourer runs 21, and no display budget is documented, so these three
     * numbers are the knobs to turn if a measurement says it is too many.
     */
    static final int TENTACLES = 4;
    static final int TENTACLE_LINKS = 8;
    static final double TENTACLE_SPACING = 0.95;
    /** How far from the head's axis a tentacle roots, and how high up the head it attaches. */
    static final double TENTACLE_ROOT_RADIUS = 1.5;
    static final double TENTACLE_ROOT_Y = 1.3;
    /** Idle: the resting target orbits this far out from the head, and this high. */
    static final double TENTACLE_IDLE_RADIUS = 5.0;
    static final double TENTACLE_IDLE_Y = 0.6;

    private ServerBossEvent bossEvent;
    private boolean rigSpawned = false;
    private boolean defeated = false;

    private Display.ItemDisplay headBase;
    private Display.ItemDisplay headWaist;
    private Display.ItemDisplay headCrown;
    private Display.ItemDisplay coreDisplay;
    private TentacleRig tentacles;
    /** The strike points of the attack that just resolved, held by the arms for {@link TentaclePose#HOLD_TICKS} ticks. */
    private Vec3[] holdStrike;
    private int holdUntilTick = -1;
    private Attack holdAttackName;

    /** Pylons spawn on a ring this far from the boss: inside the 28 block arena, where players naturally fight. */
    static final int PYLON_RING_MIN = 8;
    static final int PYLON_RING_MAX = 12;
    static final int PYLON_TARGET = 4;
    /** How long the boss takes damage after its last pylon breaks, in ticks (6 s). */
    static final int EXPOSED_TICKS = 120;
    /** The beam is drawn this often, and with this many points per pylon, to keep the packet cost bounded. */
    static final int BEAM_EVERY_TICKS = 4;
    static final int BEAM_POINTS = 10;

    private final List<CinderPylon> pylons = new ArrayList<>();
    /** How many pylons were actually placed (fewer than the target on poor terrain). */
    private int pylonsPlaced = 0;
    private int exposedTicksLeft = 0;
    private boolean gateOpenedOnce = false;
    /** Health fractions at which ONE pylon re-lights (never a full reset, so the player is never sent back to square one). */
    private static final double[] RELIGHT_AT = {0.66, 0.33};
    private int relightsDone = 0;
    private int phase = 1;

    // ---- attacks (numbers are design targets, to be tuned by measurement) ----
    /** Ember Fan: a frontal cone. The telegraph and the hit test read these same constants. */
    static final double FAN_RANGE = 6.0;
    static final double FAN_HALF_ANGLE_DEG = 40.0;
    static final double FAN_COS = Math.cos(Math.toRadians(FAN_HALF_ANGLE_DEG));
    static final int FAN_WINDUP_TICKS = 18;      // 0.9 s, above the 0.5 s floor
    /** Boss Curse: every attack damage is multiplied by this. 1.0 with no curse. Set once at spawn by {@link #applyCurse}. */
    private float damageScale = 1.0F;

    /**
     * Applies the run's Boss Curse: health and attack damage both scale by {@code statMultiplier}. Called once, right after
     * the boss is created and before it is added to the world, so the boss bar and the fight start at the scaled health.
     */
    public void applyCurse(double statMultiplier) {
        if (statMultiplier <= 1.0) {
            return;
        }
        this.damageScale = (float) statMultiplier;
        net.minecraft.world.entity.ai.attributes.AttributeInstance hp = this.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(hp.getBaseValue() * statMultiplier);
            this.setHealth(this.getMaxHealth());
        }
    }

    static final float FAN_DAMAGE = 14.0F;
    static final double FAN_KNOCKBACK = 1.3;
    /** Falling Sparks: circles that fill in on the ground, then burst. */
    static final int SPARK_CIRCLES = 3;
    static final double SPARK_RADIUS = 2.5;
    static final int SPARK_WINDUP_TICKS = 24;    // 1.2 s
    static final float SPARK_DAMAGE = 10.0F;
    static final double SPARK_RANGE = 16.0;
    /** Pause between attacks, and the fixed order they repeat in (learnable, per the Empress of Light loop). */
    static final int ATTACK_GAP_TICKS = 30;
    /** Sweeping Beam (phase 2+): a long thin line, locked at wind-up start. A stone pillar between boss and player blocks it. */
    static final double BEAM_RANGE = 22.0;
    static final double BEAM_HALF_WIDTH = 0.9;
    static final int BEAM_WINDUP_TICKS = 40;     // 2.0 s, the design's wind-up
    static final float BEAM_DAMAGE = 18.0F;
    /** The cover pillar: 2 wide, 3 high, placed on free standable ground only (never replaces terrain), journaled. */
    static final int PILLAR_HEIGHT = 3;
    /** The beam travels level at roughly a player's chest height so that a pillar of PILLAR_HEIGHT really blocks it. */
    static final double BEAM_HEIGHT = 1.2;
    /**
     * Magma Ring (phase 2+): the band between a safe inner circle and the fight's outer edge becomes dangerous. The centre is
     * the point where the fight started, so the shape is fixed and learnable. The safe circle shrinks by phase but never
     * below RING_SAFE_MIN, which keeps the pylon ring (8 to 12 blocks out) and the boss reachable. Damage zone plus particles
     * only: no block is changed, so there is nothing to restore and no terrain is touched.
     */
    static final double RING_OUTER = 24.0;
    static final double RING_SAFE_P2 = 11.0;
    static final double RING_SAFE_P3 = 7.0;
    static final int RING_WINDUP_TICKS = 50;     // 2.5 s
    static final float RING_DAMAGE = 10.0F;
    static final int RING_BURN_TICKS = 60;       // the band keeps hurting for 3 s after the burst
    static final float RING_BURN_DAMAGE = 2.0F;
    /** Blocks between telegraph dots on the ring edges. */
    static final double RING_POINT_SPACING = 2.5;
    /** The band is a full column, not a slab: the arena allows 24 blocks above the floor, so scan well beyond that both ways. */
    static final double RING_COLUMN_HEIGHT = 96.0;
    /**
     * Cinderfall (phase 3 only): a wave of falling sparks in strips. A row is CINDER_LANES lanes side by side across the
     * fight; exactly one lane per row is left OPEN and the open lane moves by one step each row, so there is always a
     * reachable safe lane and the gap is readable. Rows come one after another along the approach axis.
     */
    static final int CINDER_ROWS = 4;
    static final int CINDER_LANES = 7;
    /**
     * Lane width. The worst case step between rows is one lane width (standing at the far edge of the open lane, the next open
     * lane is the neighbour). Measured: the bot walks 4.25 blocks/s, so a 4 block step takes 1.0 s and the 1.2 s between rows
     * left only 0.2 s to spare. At 3 blocks the step takes 0.7 s, so walking has a comfortable margin.
     */
    static final double CINDER_LANE_WIDTH = 3.0;
    /**
     * Every row covers this much ground along the approach axis (same ground each row), centred on the player when the attack
     * begins. Measured: at 6 a player who stepped back during the wind-up (or was left behind as the boss stalked closer) ended
     * 2.7 to 6.4 blocks outside the strip and was hit by none of the four rows, so simply stepping back beat the attack. 14 gives
     * 7 blocks of slack each side, more than a player can retreat in the 0.6 s before the first row.
     */
    static final double CINDER_ROW_DEPTH = 14.0;
    static final int CINDER_WINDUP_TICKS = 36;           // the first row is telegraphed this long before it lands
    /**
     * Each later row lands this long after the one before. Worst case the player stands at the far edge of the open lane and
     * the next open lane is the neighbour, so the step is one lane width: 4 blocks. At 16 ticks that needs 5.0 blocks/s, which
     * only a sprint reaches (walking is 4.32). 24 ticks needs 3.3 blocks/s, so plain walking is enough.
     */
    static final int CINDER_ROW_INTERVAL_TICKS = 24;
    static final float CINDER_DAMAGE = 12.0F;
    /** Spacing of the telegraph dust inside a lane. A 3 by 14 lane is 2 by 6 points at 3.0, about 12 per lane (packet cost rule). */
    static final double CINDER_TELEGRAPH_SPACING = 3.0;
    /** How long before a row lands the row after it is previewed in orange. */
    static final int CINDER_PREVIEW_TICKS = 12;
    /** The longest Cinderfall waits for an airborne or flung target to settle before it fires regardless (3 s). */
    static final int CINDER_SETTLE_CAP_TICKS = 60;
    /** Cinderfall is not tied to the boss's reach, only to the player being in the fight. */
    static final double CINDER_RANGE = 40.0;
    /** Phase 3 pace: shorter pause between attacks (the design raises the tempo, not just the damage). */
    static final int ATTACK_GAP_TICKS_P3 = 18;
    /** Below this fraction the boss drops every attack and lets the player finish it. */
    static final double CALM_BEAT_AT = 0.05;
    private static final boolean TEST_MODE = Boolean.getBoolean("emberfall.testMode");
    private enum Attack { FAN, SPARKS, BEAM, RING, CINDER }
    private static final Attack[] LOOP_P1 = {Attack.FAN, Attack.SPARKS, Attack.FAN, Attack.SPARKS};
    private static final Attack[] LOOP_P2 = {Attack.FAN, Attack.BEAM, Attack.SPARKS, Attack.RING, Attack.BEAM, Attack.SPARKS};
    /** Phase 3 opens with the beam (index 0) as designed, then Cinderfall is woven in between the others. */
    private static final Attack[] LOOP_P3 = {Attack.BEAM, Attack.CINDER, Attack.FAN, Attack.RING, Attack.SPARKS, Attack.CINDER, Attack.BEAM};
    private Vec3 cinderOrigin = Vec3.ZERO;   // where row 0 begins along the approach axis
    private Vec3 cinderAxis = new Vec3(1, 0, 0);   // unit vector along which the rows advance
    private int[] cinderOpenLane = new int[CINDER_ROWS];
    private boolean calmBeat = false;        // true once health is under CALM_BEAT_AT: no more attacks
    private Vec3 arenaCentre = null;         // where the fight started, set in spawnRig
    private int ringBurnLeft = 0;            // ticks of lingering band damage after a Magma Ring burst
    private double ringSafeRadius = RING_SAFE_P2;
    private Vec3 beamOrigin = Vec3.ZERO;
    private Vec3 beamDir = Vec3.ZERO;
    private final List<BlockPos> pillarBlocks = new ArrayList<>();
    private int loopIndex = 0;
    private Attack current = null;          // null = between attacks
    private int attackTicks = 0;            // ticks into the current wind-up
    private int gapTicks = ATTACK_GAP_TICKS;
    private int settleTicks = 0;   // how long Cinderfall has waited for the target to land and stop
    private Vec3 fanOrigin = Vec3.ZERO;
    private Vec3 fanDir = Vec3.ZERO;
    private final List<Vec3> sparkCentres = new ArrayList<>();
    /** How many attacks have resolved, for tests. */
    private int attacksResolved = 0;

    // ---- movement ----
    /** Stalks toward the nearest player, stopping here (inside the 6 block Fan reach, so the cone always matters). */
    static final double STALK_STOP_DISTANCE = 4.5;
    /** Navigation speed multiplier on the 0.22 base. Measured walking pace: 0.55 gave 0.62 blocks/s (too slow), 1.4 gave 2.73 (63% of a walking player). 1.0 targets about 2.</ */
    static final double STALK_SPEED = 1.0;
    /** The path is only recomputed this often, since a fresh path every tick is the costly part of navigation. */
    static final int REPATH_EVERY_TICKS = 10;
    /** Anti-stall, same criteria as the Corrupted Sentinel: under 0.4 blocks of progress for 4 checks in a row. */
    static final double STUCK_MOVE_THRESHOLD_SQ = 0.16;
    static final int STUCK_CHECKS_BEFORE_HOP = 4;
    /** A hop clears a 2 to 3 block ledge; the cooldown stops it bouncing in place against a wall it cannot clear. */
    static final double HOP_UP = 0.95;
    static final double HOP_FORWARD = 0.5;
    static final int HOP_COOLDOWN_TICKS = 60;
    private Vec3 lastStuckCheckPos = null;
    private int stuckChecks = 0;
    private int nextHopAtTick = 0;

    public EmberGuardian(EntityType<? extends Silverfish> type, Level level) {
        super(type, level);
        this.setInvisible(true); // only the display parts are ever seen; never toggled again
        this.setSilent(true);
    }

    public static AttributeSupplier.Builder createBossAttributes() {
        return Silverfish.createAttributes()
                .add(Attributes.MAX_HEALTH, 600.0)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.ATTACK_DAMAGE, 10.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                // A Silverfish steps only 0.6, so one block of ordinary terrain was a wall (measured: a solid block at its own
                // height stopped it dead). 1.1 clears exactly one block and not two.
                .add(Attributes.STEP_HEIGHT, 1.1);
    }

    /** Builds the display rig. Call once, right after this brain has been added to the level. */
    public void spawnRig(ServerLevel level) {
        if (rigSpawned) {
            return;
        }
        rigSpawned = true;
        arenaCentre = this.position();
        this.setPersistenceRequired(); // a boss fight only ever ends via die(), never a silent despawn

        headBase = part(level, Items.BLACKSTONE, BASE_SCALE);
        headWaist = part(level, Items.MAGMA_BLOCK, WAIST_SCALE);
        headCrown = part(level, Items.BASALT, CROWN_SCALE);
        coreDisplay = part(level, Items.SHROOMLIGHT, CORE_SCALE);
        Vec3[] roots = new Vec3[TENTACLES];
        Vec3[] dirs = new Vec3[TENTACLES];
        for (int t = 0; t < TENTACLES; t++) {
            roots[t] = tentacleRoot(t);
            dirs[t] = tentacleRoot(t).subtract(this.position().add(0, TENTACLE_ROOT_Y, 0));
        }
        tentacles = new TentacleRig(level, roots, dirs, TENTACLE_LINKS, TENTACLE_SPACING);
        placeParts();
        spawnPylons(level);

        bossEvent = new ServerBossEvent(
                this.getDisplayName() != null ? this.getDisplayName() : Component.literal("The Ember Guardian"),
                BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.NOTCHED_10);
        EmberfallMod.LOGGER.info("Ember Guardian rig spawned: 4 head parts + {} tentacle parts", tentacles.entityCount());
    }

    /**
     * Places up to {@link #PYLON_TARGET} pylons on standable ground around the boss. Natural terrain is not guaranteed
     * flat, so the ring search may return fewer; the gate then simply scales to however many were placed. With none
     * placed the boss is never gated (a fight that could never be opened would be a soft-lock).
     */
    private void spawnPylons(ServerLevel level) {
        List<BlockPos> spots = TerrainScanner.ringPoints(level, this.blockPosition(), PYLON_TARGET, PYLON_RING_MIN, PYLON_RING_MAX);
        for (BlockPos spot : spots) {
            CinderPylon pylon = new CinderPylon(ModEntities.CINDER_PYLON, level);
            pylon.setPos(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
            pylon.setPersistenceRequired();
            pylon.addTag("emberfall_run");
            level.addFreshEntity(pylon);
            pylon.attachSkin(level);
            pylons.add(pylon);
        }
        pylonsPlaced = pylons.size();
        EmberfallMod.LOGGER.info("Ember Guardian placed {} of {} pylons", pylonsPlaced, PYLON_TARGET);
    }

    /** Number of pylons still standing. */
    public int pylonsAlive() {
        pylons.removeIf(p -> !p.isAlive());
        return pylons.size();
    }

    /** True while the boss cannot be hurt: at least one pylon is lit and the exposure window is not open. */
    public boolean isGated() {
        return pylonsAlive() > 0;
    }

    private Display.ItemDisplay part(ServerLevel level, ItemLike item, float scale) {
        Display.ItemDisplay d = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
        d.setPos(this.getX(), this.getY(), this.getZ());
        d.setItemStack(new ItemStack(item));
        d.setTransformation(new Transformation(
                new Vector3f(0, 0, 0), new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf()));
        d.setNoGravity(true);
        d.setInvulnerable(true);
        d.setViewRange(2.0F);                 // the default 1.0 drops a boss this size from view at range
        d.setPosRotInterpolationDuration(2);  // glide between ticks like the tentacles do
        d.addTag("emberfall_run"); // run teardown sweeps any stray part
        level.addFreshEntity(d);
        return d;
    }

    /** Puts every display at its offset from the brain, turned with the brain's yaw. */
    private void placeParts() {
        Vec3 base = this.position();
        move(headBase, base.add(0, BASE_Y, 0));
        move(headWaist, base.add(0, WAIST_Y, 0));
        move(headCrown, base.add(0, CROWN_Y, 0));
        move(coreDisplay, base.add(0, CORE_Y, 0));
        placeTentacles();
    }

    /** Where tentacle {@code t} attaches: evenly spaced round the head, turned with the boss. */
    private Vec3 tentacleRoot(int t) {
        double a = this.getYRot() * Mth.DEG_TO_RAD + (t + 0.5) * (2.0 * Math.PI / TENTACLES);
        return this.position().add(Math.cos(a) * TENTACLE_ROOT_RADIUS, TENTACLE_ROOT_Y, Math.sin(a) * TENTACLE_ROOT_RADIUS);
    }

    /** Idle sway target for tentacle {@code t}: a point that drifts slowly round the head. */
    private Vec3 idleTarget(int t) {
        double time = this.tickCount * 0.04;
        double a = this.getYRot() * Mth.DEG_TO_RAD + (t + 0.5) * (2.0 * Math.PI / TENTACLES) + Math.sin(time + t * 1.7) * 0.5;
        double r = TENTACLE_IDLE_RADIUS + Math.sin(time * 1.3 + t) * 1.0;
        return this.position().add(Math.cos(a) * r, TENTACLE_IDLE_Y + Math.sin(time * 0.9 + t * 2.1) * 0.6, Math.sin(a) * r);
    }

    /** Where each arm lands for attack {@code a}, from the aim the attack locked at its start. */
    private Vec3[] strikePoints(Attack a) {
        Vec3[] out = new Vec3[TENTACLES];
        double yawRad = this.getYRot() * Mth.DEG_TO_RAD;
        for (int t = 0; t < TENTACLES; t++) {
            switch (a) {
                case FAN -> out[t] = TentaclePose.fanStrike(fanOrigin, fanDir, FAN_RANGE, FAN_HALF_ANGLE_DEG, t, TENTACLES);
                case RING -> out[t] = TentaclePose.ringStrike(this.position(), t, TENTACLES, Math.min(RING_OUTER, 10.0), yawRad);
                case BEAM -> out[t] = TentaclePose.beamStrike(beamOrigin, beamDir, t, TENTACLES);
                case SPARKS -> out[t] = TentaclePose.sparkStrike(sparkCentres, t);
                default -> out[t] = this.position().add(Math.cos(yawRad + t * 1.57) * 4.0, 0.0, Math.sin(yawRad + t * 1.57) * 4.0);
            }
        }
        return out;
    }

    /** Idle sway between attacks; rear back and strike during one; hold the strike briefly after it resolves. */
    private void placeTentacles() {
        if (tentacles == null) {
            return;
        }
        Vec3 head = this.position();
        if (current != null && current != Attack.CINDER) {
            int windup = windupOf(current);
            double progress = TentaclePose.progress(attackTicks, windup);
            Vec3[] strike = strikePoints(current);
            for (int t = 0; t < TENTACLES; t++) {
                Vec3 rear = head.add(0, TentaclePose.REAR_HEIGHT + TENTACLE_ROOT_Y, 0).add(tentacleRoot(t).subtract(head).scale(0.6));
                tentacles.reach(t, tentacleRoot(t), TentaclePose.blend(idleTarget(t), rear, strike[t], progress, false, 0.25));
            }
        } else if (current == Attack.CINDER) {
            // Cinderfall lasts 5 s: the arms stay reared overhead while the rows fall.
            for (int t = 0; t < TENTACLES; t++) {
                Vec3 rear = head.add(0, TentaclePose.REAR_HEIGHT + TENTACLE_ROOT_Y, 0).add(tentacleRoot(t).subtract(head).scale(0.6));
                double k = TentaclePose.smooth(attackTicks / 20.0);
                tentacles.reach(t, tentacleRoot(t), idleTarget(t).lerp(rear, k));
            }
        } else if (holdStrike != null && this.tickCount < holdUntilTick) {
            for (int t = 0; t < TENTACLES; t++) {
                tentacles.reach(t, tentacleRoot(t), holdStrike[t]);
            }
        } else {
            for (int t = 0; t < TENTACLES; t++) {
                tentacles.reach(t, tentacleRoot(t), idleTarget(t));
            }
        }
        tentacles.endTick();
        if (TEST_MODE && (current != null || this.tickCount < holdUntilTick) && current != Attack.CINDER) {
            // Permanent sandbox-only trace: tip positions each tick of an attack, so a test can read the pose over its whole length.
            StringBuilder sb = new StringBuilder();
            for (int t = 0; t < TENTACLES; t++) {
                Vec3 tip = tentacles.tipPos(t);
                sb.append(String.format(" %.2f,%.2f,%.2f", tip.x, tip.y, tip.z));
            }
            EmberfallMod.LOGGER.info("TENTACLE_TEST {} tick={} resolved={} boss={},{},{} tips{}", current == null ? holdAttackName : current,
                    attackTicks, current == null, String.format("%.2f", this.getX()), String.format("%.2f", this.getY()), String.format("%.2f", this.getZ()), sb);
        }
    }

    private int windupOf(Attack a) {
        return a == Attack.FAN ? FAN_WINDUP_TICKS : a == Attack.BEAM ? BEAM_WINDUP_TICKS : a == Attack.RING ? RING_WINDUP_TICKS
                : a == Attack.CINDER ? CINDER_WINDUP_TICKS : SPARK_WINDUP_TICKS;
    }

    private static void move(Display.ItemDisplay d, Vec3 to) {
        if (d != null && d.isAlive()) {
            d.setPos(to.x, to.y, to.z);
        }
    }

    @Override
    protected void registerGoals() {
        // Deliberately empty: movement and attacks are the custom state machine, not vanilla Silverfish goals.
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (defeated || source.getEntity() == this) {
            return false; // a boss HP pool only ever moves from genuine external damage
        }
        // The gate. A visible beam from every lit pylon is the cause, so the immunity is never silent. Void damage
        // (kill, /kill, out of world) is let through so a boss can always be removed.
        if (rigSpawned && isGated() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        return super.hurtServer(level, source, amount);
    }

    @Override
    public void tick() {
        super.tick();
        // Vanilla keeps ticking a dead mob for its ~20 tick death animation; no custom logic may run after die().
        if (defeated || !rigSpawned || !(this.level() instanceof ServerLevel level)) {
            return;
        }
        placeParts();
        updateBossBar(level);
        tickGate(level);
        tickAttacks(level);
        tickRingBurn(level);
        tickMovement(level);
    }

    /**
     * Walks toward the nearest player and faces them. It plants its feet for the whole of an attack wind-up (a fair
     * telegraph needs a boss that stays where it is drawn) and also once exposed, so the punish window is a stationary
     * target rather than a chase.
     */
    private void tickMovement(ServerLevel level) {
        Player target = level.getNearestPlayer(this, 60.0);
        if (target == null) {
            this.getNavigation().stop();
            return;
        }
        // Face the target every tick; the display parts read this yaw in placeParts.
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        this.setYRot(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
        boolean planted = current != null || !isGated();
        if (planted || this.distanceTo(target) <= STALK_STOP_DISTANCE) {
            this.getNavigation().stop();
            return;
        }
        if (this.tickCount % REPATH_EVERY_TICKS == 0) {
            checkStuckAndHop(target);
        }
        if (this.tickCount % REPATH_EVERY_TICKS == 0 || this.getNavigation().isDone()) {
            this.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), STALK_SPEED);
        }
    }

    /** Attacks run only while the boss is gated (a lit pylon exists). Once the gate opens it stops to give a clean window. */
    private void tickAttacks(ServerLevel level) {
        checkCalmBeat(level);
        if (calmBeat) {
            return;
        }
        if (!isGated()) {
            current = null;      // an attack cannot carry over into the exposed window
            attackTicks = 0;
            settleTicks = 0;
            gapTicks = phaseGap();   // a fresh, full pause before the first attack after the boss re-arms
            sparkCentres.clear();
            if (!pillarBlocks.isEmpty()) {
                clearPillar(level);
            }
            ringBurnLeft = 0;   // the exposed window is clean: no lingering band damage
            return;
        }
        Player target = level.getNearestPlayer(this, 40.0);
        if (target == null) {
            return;
        }
        if (current == null) {
            if (gapTicks > 0) {
                gapTicks--;
                return;
            }
            // Only wind up an attack that could actually reach: otherwise the boss would plant its feet and telegraph at
            // empty air from across the arena. Out of reach it keeps walking (tickMovement) and the loop position holds.
            Attack[] loop = phase >= 3 ? LOOP_P3 : phase >= 2 ? LOOP_P2 : LOOP_P1;
            Attack next = loop[loopIndex % loop.length];
            double reach = next == Attack.FAN ? FAN_RANGE - 0.5 : next == Attack.BEAM ? BEAM_RANGE : next == Attack.RING ? RING_OUTER : next == Attack.CINDER ? CINDER_RANGE : SPARK_RANGE;
            if (this.distanceTo(target) <= reach) {
                // Cinderfall is centred on where the target stands at the cast and lasts 5.4 s, so wait for a target that is
                // airborne or being flung (knockback from the attack before it carried players 7 blocks out of the strip, so the
                // attack hit nothing). Capped, so a player who keeps bouncing cannot stall the boss: after the cap it fires anyway.
                boolean unsettled = next == Attack.CINDER && (!target.onGround() || target.getDeltaMovement().horizontalDistanceSqr() > 0.01);
                if (unsettled && settleTicks < CINDER_SETTLE_CAP_TICKS) {
                    settleTicks++;
                    return;
                }
                settleTicks = 0;
                beginAttack(level, target, next);
            }
            return;
        }
        attackTicks++;
        int windup = current == Attack.FAN ? FAN_WINDUP_TICKS : current == Attack.BEAM ? BEAM_WINDUP_TICKS : current == Attack.RING ? RING_WINDUP_TICKS : current == Attack.CINDER ? CINDER_WINDUP_TICKS + (CINDER_ROWS - 1) * CINDER_ROW_INTERVAL_TICKS : SPARK_WINDUP_TICKS;
        if (current == Attack.CINDER) {
            tickCinder(level);
        }
        // Telegraph only during the wind-up, and no faster than every 4 ticks (measured cost rule).
        if (attackTicks % 4 == 0 && current != Attack.CINDER) {
            double progress = attackTicks / (double) windup;
            if (current == Attack.FAN) {
                Fx.telegraphCone(level, fanOrigin, fanDir, FAN_RANGE, FAN_HALF_ANGLE_DEG, Fx.WARN_RED);
            } else if (current == Attack.RING) {
                drawRing(level, progress);
            } else if (current == Attack.BEAM) {
                Fx.telegraphLine(level, beamOrigin, beamOrigin.add(beamDir.scale(BEAM_RANGE)), BEAM_HALF_WIDTH, Fx.WARN_RED);
            } else {
                for (Vec3 c : sparkCentres) {
                    Fx.telegraphFill(level, c, SPARK_RADIUS, progress, Fx.WARN_ORANGE);
                }
            }
        }
        if (attackTicks >= windup) {
            if (current == Attack.FAN) {
                resolveFan(level);
            } else if (current == Attack.RING) {
                resolveRing(level);
            } else if (current == Attack.BEAM) {
                resolveBeam(level);
            } else if (current == Attack.CINDER) {
                // every row already landed on its own tick in tickCinder
            } else {
                resolveSparks(level);
            }
            holdStrike = strikePoints(current);
            holdAttackName = current;
            holdUntilTick = this.tickCount + TentaclePose.HOLD_TICKS;
            current = null;
            attackTicks = 0;
            gapTicks = phaseGap();
            loopIndex++;
            attacksResolved++;
        }
    }

    /**
     * If the boss wants to walk but has made almost no progress for several checks (a ledge taller than it can step,
     * or a wall), it hops up and toward the target. A player on a 2 block rock is therefore never untouchable.
     */
    private void checkStuckAndHop(Player target) {
        Vec3 pos = this.position();
        Vec3 last = lastStuckCheckPos;
        lastStuckCheckPos = pos;
        if (last == null || last.distanceToSqr(pos) >= STUCK_MOVE_THRESHOLD_SQ) {
            stuckChecks = 0;
            return;
        }
        stuckChecks++;
        if (stuckChecks < STUCK_CHECKS_BEFORE_HOP || this.tickCount < nextHopAtTick || !this.onGround()) {
            return;
        }
        stuckChecks = 0;
        nextHopAtTick = this.tickCount + HOP_COOLDOWN_TICKS;
        Vec3 to = new Vec3(target.getX() - this.getX(), 0.0, target.getZ() - this.getZ());
        Vec3 dir = to.lengthSqr() < 1.0E-6 ? Vec3.ZERO : to.normalize();
        this.setDeltaMovement(dir.x * HOP_FORWARD, HOP_UP, dir.z * HOP_FORWARD);
        this.hurtMarked = true;
    }

    private void beginAttack(ServerLevel level, Player target, Attack next) {
        current = next;
        EmberfallMod.LOGGER.info("Ember Guardian attack {} (phase {})", next, phase); // one line per attack: cheap, and the tests count it
        attackTicks = 0;
        if (next == Attack.FAN) {
            // Direction and origin are locked NOW, so a player who moves during the wind-up can dodge it.
            fanOrigin = this.position();
            Vec3 to = target.position().subtract(this.position());
            fanDir = new Vec3(to.x, 0.0, to.z).normalize();
            level.playSound(null, this.getX(), this.getY(), this.getZ(),
                    net.minecraft.sounds.SoundEvents.RAVAGER_ROAR, net.minecraft.sounds.SoundSource.HOSTILE, 1.4F, 0.6F);
        } else if (next == Attack.RING) {
            ringSafeRadius = phase >= 3 ? RING_SAFE_P3 : RING_SAFE_P2;
            level.playSound(null, this.getX(), this.getY(), this.getZ(),
                    net.minecraft.sounds.SoundEvents.RAVAGER_ROAR, net.minecraft.sounds.SoundSource.HOSTILE, 1.6F, 0.4F);
        } else if (next == Attack.BEAM) {
            // Aim is locked NOW: a player who keeps moving sideways, or steps behind the pillar, is missed.
            // Fired from player chest height, level with the ground, NOT from the core (2.9 up): a beam from the core passes
            // over a 3 block pillar (measured: origin y 74.6 vs pillar top y 72), so cover would never work.
            // Starts at the HIGHER of the two floors: from the lower one a boss on a mound would fire from inside solid rock
            // (measured: boss column solid at the player's floor height) and never hit anyone below it.
            beamOrigin = new Vec3(this.getX(), Math.max(this.getY(), target.getY()) + BEAM_HEIGHT, this.getZ());
            Vec3 to = target.position().add(0, 1.0, 0).subtract(beamOrigin);
            Vec3 flat = new Vec3(to.x, 0.0, to.z);
            beamDir = flat.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : flat.normalize();
            placePillar(level, target);
            level.playSound(null, this.getX(), this.getY(), this.getZ(),
                    net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE, net.minecraft.sounds.SoundSource.HOSTILE, 1.6F, 0.5F);
        } else if (next == Attack.CINDER) {
            // The rows advance AWAY from the boss, through the player's spot, perpendicular lanes across the approach line.
            Vec3 to = target.position().subtract(this.position());
            Vec3 flat = new Vec3(to.x, 0.0, to.z);
            cinderAxis = flat.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : flat.normalize();
            // The ground is centred on the player, so they stand in the middle of it with room to step sideways.
            cinderOrigin = target.position().subtract(cinderAxis.scale(CINDER_ROW_DEPTH * 0.5));
            cinderOpenLane = pickOpenLanes(new java.util.Random(this.getRandom().nextLong()), CINDER_ROWS, CINDER_LANES);
            if (TEST_MODE) { // sandbox test server only: the dodge tests read the lane plan from these lines
                EmberfallMod.LOGGER.info("CINDERDBG begin open={} phase={}", java.util.Arrays.toString(cinderOpenLane), phase);
                for (int r = 0; r < CINDER_ROWS; r++) {
                    Vec3 c = cinderLaneCentre(r, cinderOpenLane[r]);
                    EmberfallMod.LOGGER.info("CINDERDBG safe row={} x={} y={} z={}", r, String.format("%.2f", c.x), String.format("%.2f", c.y), String.format("%.2f", c.z));
                }
            }
            level.playSound(null, this.getX(), this.getY(), this.getZ(),
                    net.minecraft.sounds.SoundEvents.BLAZE_SHOOT, net.minecraft.sounds.SoundSource.HOSTILE, 1.6F, 0.4F);
        } else {
            sparkCentres.clear();
            // One circle on the target, the others scattered around it so the player must read and move, not stand still.
            Vec3 base = target.position();
            sparkCentres.add(base);
            for (int i = 1; i < SPARK_CIRCLES; i++) {
                double a = this.getRandom().nextDouble() * Math.PI * 2.0;
                double r = 3.0 + this.getRandom().nextDouble() * 3.0;
                sparkCentres.add(base.add(Math.cos(a) * r, 0.0, Math.sin(a) * r));
            }
            level.playSound(null, this.getX(), this.getY(), this.getZ(),
                    net.minecraft.sounds.SoundEvents.BLAZE_SHOOT, net.minecraft.sounds.SoundSource.HOSTILE, 1.4F, 0.5F);
        }
    }

    private void resolveFan(ServerLevel level) {
        level.playSound(null, fanOrigin.x, fanOrigin.y, fanOrigin.z,
                net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.HOSTILE, 1.4F, 0.8F);
        Fx.burst(level, ParticleTypes.FLAME, fanOrigin.add(fanDir.scale(FAN_RANGE * 0.6)).add(0, 0.4, 0), 30, FAN_RANGE * 0.3, 0.05);
        AABB box = AABB.ofSize(fanOrigin, FAN_RANGE * 2, 6.0, FAN_RANGE * 2);
        for (Player p : level.getEntitiesOfClass(Player.class, box, Player::isAlive)) {
            Vec3 to = p.position().subtract(fanOrigin);
            Vec3 flat = new Vec3(to.x, 0.0, to.z);
            double dist = flat.length();
            if (dist > FAN_RANGE || dist < 0.001) {
                continue;
            }
            if (fanDir.dot(flat.scale(1.0 / dist)) < FAN_COS) {
                continue;
            }
            p.hurtServer(level, this.damageSources().mobAttack(this), FAN_DAMAGE * damageScale);
            p.setDeltaMovement(fanDir.x * FAN_KNOCKBACK, 0.35, fanDir.z * FAN_KNOCKBACK);
            p.hurtMarked = true;
        }
    }

    /** True when a player stands in the dangerous band: beyond the safe circle, inside the outer edge. */
    private boolean inRingBand(Player p) {
        if (arenaCentre == null) {
            return false;
        }
        double dx = p.getX() - arenaCentre.x, dz = p.getZ() - arenaCentre.z;
        double d2 = dx * dx + dz * dz;
        return d2 > ringSafeRadius * ringSafeRadius && d2 <= RING_OUTER * RING_OUTER;
    }

    /**
     * Wind-up telegraph. Fx.telegraphRing is a fixed 16 points whatever the radius, which on a 24 block circle is a dot
     * every 9 blocks and unreadable, so this draws its own ring at about one point per 2.5 blocks. To keep the cost bounded
     * it draws only the two edges that define the band (the safe circle in orange, the outer edge in red), and only every
     * 8 ticks (callers pass progress; the wind-up loop runs this every 4 ticks, so odd steps are skipped here).
     */
    private void drawRing(ServerLevel level, double progress) {
        if (arenaCentre == null || attackTicks % 8 != 0) {
            return;
        }
        ringEdge(level, ringSafeRadius, Fx.WARN_ORANGE);
        ringEdge(level, RING_OUTER, Fx.WARN_RED);
    }

    private void burstCircle(ServerLevel level, double radius, net.minecraft.core.particles.ParticleOptions particle) {
        int points = Math.max(16, (int) Math.round(Math.PI * 2.0 * radius / RING_POINT_SPACING));
        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2.0 * i) / points;
            level.sendParticles(particle, arenaCentre.x + Math.cos(a) * radius, arenaCentre.y + 0.2, arenaCentre.z + Math.sin(a) * radius,
                    1, 0.0, 0.1, 0.0, 0.02);
        }
    }

    private void ringEdge(ServerLevel level, double radius, int rgb) {
        net.minecraft.core.particles.DustParticleOptions dust = new net.minecraft.core.particles.DustParticleOptions(rgb, 1.3F);
        int points = Math.max(16, (int) Math.round(Math.PI * 2.0 * radius / RING_POINT_SPACING));
        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2.0 * i) / points;
            level.sendParticles(dust, true, true,
                    arenaCentre.x + Math.cos(a) * radius, arenaCentre.y + 0.1, arenaCentre.z + Math.sin(a) * radius,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private void resolveRing(ServerLevel level) {
        if (arenaCentre == null) {
            return;
        }
        level.playSound(null, arenaCentre.x, arenaCentre.y, arenaCentre.z,
                net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.HOSTILE, 1.8F, 0.5F);
        // One burst along three circles (safe edge, middle of the band, outer edge), scaled so it reads as a ring.
        burstCircle(level, ringSafeRadius, ParticleTypes.FLAME);
        burstCircle(level, (ringSafeRadius + RING_OUTER) * 0.5, ParticleTypes.LAVA);
        burstCircle(level, RING_OUTER, ParticleTypes.FLAME);
        for (Player p : level.getEntitiesOfClass(Player.class, AABB.ofSize(arenaCentre, RING_OUTER * 2, RING_COLUMN_HEIGHT, RING_OUTER * 2), Player::isAlive)) {
            boolean inBand = inRingBand(p);
            if (inBand) {
                p.hurtServer(level, this.damageSources().mobAttack(this), RING_DAMAGE * damageScale);
            }
        }
        ringBurnLeft = RING_BURN_TICKS;
    }

    /** After the burst the band keeps burning for a few seconds: a once-a-second tick, so standing in it stays costly. */
    private void tickRingBurn(ServerLevel level) {
        if (ringBurnLeft <= 0 || arenaCentre == null) {
            return;
        }
        ringBurnLeft--;
        if (ringBurnLeft % 20 != 0) {
            return;
        }
        for (Player p : level.getEntitiesOfClass(Player.class, AABB.ofSize(arenaCentre, RING_OUTER * 2, RING_COLUMN_HEIGHT, RING_OUTER * 2), Player::isAlive)) {
            if (inRingBand(p)) {
                p.hurtServer(level, this.damageSources().mobAttack(this), RING_BURN_DAMAGE * damageScale);
                level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 0.2, p.getZ(), 6, 0.3, 0.1, 0.3, 0.02);
            }
        }
    }

    /**
     * Drops a cover pillar on free ground about a third of the way from the boss to the player, across the beam line.
     * Only standable air is used: an existing block is never replaced (no terrain is cleared or levelled), and every
     * block goes through the journal so the arena restores exactly. If the spot is not clean, no pillar is made.
     */
    private void placePillar(ServerLevel level, Player target) {
        clearPillar(level);
        double along = Math.min(6.0, Math.max(2.5, this.distanceTo(target) * 0.4));
        Vec3 c = this.position().add(beamDir.scale(along));
        Vec3 side = new Vec3(-beamDir.z, 0.0, beamDir.x);
        // Each of the two columns needs real standable ground (solid below, air above). The two must be at the same height
        // so the pillar stands flat, and all PILLAR_HEIGHT blocks of each column must be free air.
        BlockPos[] feet = new BlockPos[2];
        for (int w = 0; w < 2; w++) {
            Vec3 col = c.add(side.scale(w - 0.5));
            feet[w] = com.solme.emberfall.world.TerrainScanner.findStandable(level,
                    net.minecraft.util.Mth.floor(col.x), net.minecraft.util.Mth.floor(col.z), this.blockPosition().getY());
            if (feet[w] == null) {
                return;
            }
        }
        if (feet[0].getY() != feet[1].getY()) {
            return;
        }
        List<BlockPos> wanted = new ArrayList<>();
        for (BlockPos f : feet) {
            for (int h = 0; h < PILLAR_HEIGHT; h++) {
                BlockPos b = f.above(h);
                if (!level.getBlockState(b).isAir()) {
                    return;
                }
                wanted.add(b);
            }
        }
        for (BlockPos b : wanted) {
            com.solme.emberfall.world.RunManager.setBlockJournaled(level, b, net.minecraft.world.level.block.Blocks.POLISHED_BLACKSTONE.defaultBlockState());
            pillarBlocks.add(b);
        }
        level.playSound(null, c.x, c.y, c.z, net.minecraft.sounds.SoundEvents.STONE_PLACE,
                net.minecraft.sounds.SoundSource.HOSTILE, 1.4F, 0.7F);
    }


    /**
     * Picks which lane is open in each row. Row 0 is random; every later row moves the open lane by exactly one step,
     * left or right, bouncing off the ends. One step is at most CINDER_LANE_WIDTH blocks, so a player standing in the open
     * lane of a row can always reach the open lane of the next row. Pure function so it can be checked without a server.
     */
    static int[] pickOpenLanes(java.util.Random rng, int rows, int lanes) {
        int[] open = new int[rows];
        // The strip is centred on the player, so they start in the middle lane. The first open lane is within ONE lane of it, so the
        // first move is the same size as every later one (3 blocks). A free pick put it up to 3 lanes (9 blocks) away, which plain
        // walking could not cover in the 1.8 s wind-up, so the attack was unfair from its first row.
        int mid = lanes / 2;
        open[0] = mid - 1 + rng.nextInt(3);
        for (int r = 1; r < rows; r++) {
            int step = rng.nextBoolean() ? 1 : -1;
            int next = open[r - 1] + step;
            if (next < 0 || next >= lanes) {
                next = open[r - 1] - step;
            }
            open[r] = next;
        }
        return open;
    }

    /**
     * True when a point is inside a STRUCK lane of a row. (along, across) are measured from the fight origin: along the
     * approach axis and across it. The open lane is the only lane in the row that is not struck.
     */
    static boolean cinderStrikes(double along, double across, int row, int openLane, int lanes) {
        // Every row covers the SAME ground (the player's spot), only the open lane differs. Rows that advanced away from the
        // player were measured to be ignorable: a player who stood still was never inside rows 1 to 3.
        if (along < 0 || along >= CINDER_ROW_DEPTH) {
            return false;
        }
        double half = lanes * CINDER_LANE_WIDTH * 0.5;
        if (across < -half || across >= half) {
            return false;
        }
        int lane = (int) Math.floor((across + half) / CINDER_LANE_WIDTH);
        return lane != openLane;
    }

    /** World position of the centre of one lane of one row. */
    private Vec3 cinderLaneCentre(int row, int lane) {
        Vec3 across = new Vec3(-cinderAxis.z, 0.0, cinderAxis.x);
        double half = CINDER_LANES * CINDER_LANE_WIDTH * 0.5;
        double a = -half + (lane + 0.5) * CINDER_LANE_WIDTH;
        double along = CINDER_ROW_DEPTH * 0.5;
        return cinderOrigin.add(cinderAxis.scale(along)).add(across.scale(a));
    }

    /**
     * Runs every tick of a Cinderfall. Row r lands at CINDER_WINDUP_TICKS + r * CINDER_ROW_INTERVAL_TICKS. Each row is
     * telegraphed from the start (its struck lanes glow, the open lane does not), so the player can read the whole pattern and
     * see the open lane move. Telegraph cost is bounded: one glow per struck lane, every 8 ticks, and only for rows that
     * have not landed yet.
     */
    private void tickCinder(ServerLevel level) {
        // Every row covers the SAME ground, so drawing all rows at once stacked their dust and hid which lane was open.
        // Draw the NEXT row to land in red (live danger). In the last CINDER_PREVIEW_TICKS before it lands, also preview the row
        // AFTER it in orange, so the player can start the next step early: a row lands every 24 ticks (1.2 s) and crossing one
        // lane boundary takes a walking player about 0.35 s, but a human reaction eats 0.3 to 0.7 s of that gap.
        if (attackTicks % 8 == 0) {
            boolean drewNext = false;
            for (int r = 0; r < CINDER_ROWS; r++) {
                int lands = CINDER_WINDUP_TICKS + r * CINDER_ROW_INTERVAL_TICKS;
                if (attackTicks >= lands) {
                    continue; // already landed
                }
                boolean next = !drewNext;
                if (!next && lands - attackTicks > CINDER_ROW_INTERVAL_TICKS + CINDER_PREVIEW_TICKS) {
                    // 'lands' is the row AFTER the imminent one, 24 ticks later than it, so this is: imminent row more than 12 ticks away.
                    break; // the imminent row is still more than the preview window away: keep the picture clean
                }
                for (int l = 0; l < CINDER_LANES; l++) {
                    if (l != cinderOpenLane[r]) {
                        Fx.telegraphRect(level, cinderLaneCentre(r, l), cinderAxis, CINDER_ROW_DEPTH, CINDER_LANE_WIDTH,
                                CINDER_TELEGRAPH_SPACING, next ? Fx.WARN_RED : Fx.WARN_ORANGE);
                    }
                }
                if (!next) {
                    break;
                }
                drewNext = true;
            }
        }
        for (int r = 0; r < CINDER_ROWS; r++) {
            if (attackTicks == CINDER_WINDUP_TICKS + r * CINDER_ROW_INTERVAL_TICKS) {
                landCinderRow(level, r);
            }
        }
    }

    private void landCinderRow(ServerLevel level, int row) {
        Vec3 across = new Vec3(-cinderAxis.z, 0.0, cinderAxis.x);
        for (int l = 0; l < CINDER_LANES; l++) {
            if (l == cinderOpenLane[row]) {
                continue;
            }
            Vec3 c = cinderLaneCentre(row, l);
            Fx.burst(level, ParticleTypes.LAVA, c.add(0, 0.3, 0), 6, CINDER_LANE_WIDTH * 0.35, 0.1);
            Fx.burst(level, ParticleTypes.FLAME, c.add(0, 0.2, 0), 10, CINDER_LANE_WIDTH * 0.4, 0.05);
        }
        Vec3 mid = cinderLaneCentre(row, CINDER_LANES / 2);
        level.playSound(null, mid.x, mid.y, mid.z, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),
                net.minecraft.sounds.SoundSource.HOSTILE, 1.2F, 0.9F);
        // The strip is CINDER_LANES * CINDER_LANE_WIDTH wide and CINDER_ROW_DEPTH deep; the box only needs to hold that, at any
        // heading, plus the full-height column (the arena allows 24 above the floor).
        double reach = CINDER_LANES * CINDER_LANE_WIDTH + CINDER_ROW_DEPTH;
        for (Player p : level.getEntitiesOfClass(Player.class, AABB.ofSize(mid, reach, 96.0, reach), Player::isAlive)) {
            Vec3 rel = p.position().subtract(cinderOrigin);
            double along = rel.x * cinderAxis.x + rel.z * cinderAxis.z;
            double acr = rel.x * across.x + rel.z * across.z;
            boolean struck = cinderStrikes(along, acr, row, cinderOpenLane[row], CINDER_LANES);
            if (TEST_MODE) { // sandbox test server only (-Demberfall.testMode); the dodge tests judge from this line
                EmberfallMod.LOGGER.info("Ember Guardian cinder row {} t={} open={} struck={} along={} across={}", row, attackTicks, cinderOpenLane[row], struck,
                        String.format("%.2f", along), String.format("%.2f", acr));
            }
            if (struck) {
                p.hurtServer(level, this.damageSources().mobAttack(this), CINDER_DAMAGE * damageScale);
            }
        }
    }

    /** Removes the pillar blocks this attack placed, but only where they are still our block. */
    private void clearPillar(ServerLevel level) {
        for (BlockPos b : pillarBlocks) {
            if (level.getBlockState(b).is(net.minecraft.world.level.block.Blocks.POLISHED_BLACKSTONE)) {
                com.solme.emberfall.world.RunManager.setBlockJournaled(level, b, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
        }
        pillarBlocks.clear();
    }

    /** True when a solid block lies on the segment between two points (the pillar, or any terrain, stops the beam). */
    private boolean beamBlockedBetween(ServerLevel level, Vec3 from, Vec3 to) {
        net.minecraft.world.phys.BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(from, to,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK;
    }

    private void resolveBeam(ServerLevel level) {
        level.playSound(null, beamOrigin.x, beamOrigin.y, beamOrigin.z,
                net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.HOSTILE, 1.6F, 0.6F);
        // Draw the beam up to the first block it meets, so a pillar visibly stops it.
        Vec3 end = beamOrigin.add(beamDir.scale(BEAM_RANGE));
        net.minecraft.world.phys.BlockHitResult stop = level.clip(new net.minecraft.world.level.ClipContext(beamOrigin, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this));
        double len = stop.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK ? stop.getLocation().distanceTo(beamOrigin) : BEAM_RANGE;
        for (int i = 1; i <= 14; i++) {
            Vec3 at = beamOrigin.add(beamDir.scale(len * i / 14.0));
            level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 2, 0.15, 0.15, 0.15, 0.01);
        }
        AABB box = new AABB(beamOrigin, end).inflate(BEAM_HALF_WIDTH + 1.0, 4.0, BEAM_HALF_WIDTH + 1.0);
        for (Player p : level.getEntitiesOfClass(Player.class, box, Player::isAlive)) {
            Vec3 to = p.position().subtract(beamOrigin);
            double along = to.x * beamDir.x + to.z * beamDir.z;
            if (along < 0.0 || along > len) {
                continue;
            }
            double sideways = Math.abs(to.x * -beamDir.z + to.z * beamDir.x);
            if (sideways > BEAM_HALF_WIDTH + 0.3) {
                continue;
            }
            boolean blocked = beamBlockedBetween(level, beamOrigin, p.position().add(0, 1.0, 0));
            if (blocked) {
                continue;   // cover worked
            }
            p.hurtServer(level, this.damageSources().mobAttack(this), BEAM_DAMAGE * damageScale);
        }
        clearPillar(level);
    }

    private void resolveSparks(ServerLevel level) {
        for (Vec3 c : sparkCentres) {
            level.playSound(null, c.x, c.y, c.z, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),
                    net.minecraft.sounds.SoundSource.HOSTILE, 0.8F, 1.2F);
            Fx.burst(level, ParticleTypes.LAVA, c.add(0, 0.3, 0), 8, SPARK_RADIUS * 0.4, 0.1);
            AABB box = AABB.ofSize(c, SPARK_RADIUS * 2, 4.0, SPARK_RADIUS * 2);
            for (Player p : level.getEntitiesOfClass(Player.class, box, Player::isAlive)) {
                double dx = p.getX() - c.x, dz = p.getZ() - c.z;
                if (dx * dx + dz * dz <= SPARK_RADIUS * SPARK_RADIUS) {
                    p.hurtServer(level, this.damageSources().mobAttack(this), SPARK_DAMAGE * damageScale);
                }
            }
        }
        sparkCentres.clear();
    }

    /** Attacks resolved so far. For tests. */
    public int attacksResolved() {
        return attacksResolved;
    }

    /** Opens the exposure window the tick the last pylon breaks, and draws the beams while any pylon is lit. */
    private void tickGate(ServerLevel level) {
        int alive = pylonsAlive();
        if (alive == 0 && pylonsPlaced > 0 && !gateOpenedOnce) {
            gateOpenedOnce = true;
            exposedTicksLeft = EXPOSED_TICKS;
            level.playSound(null, this.getX(), this.getY(), this.getZ(),
                    net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.HOSTILE, 1.4F, 0.7F);
            Fx.burst(level, ParticleTypes.LAVA, this.position().add(0, CORE_Y, 0), 24, 0.8, 0.2);
            EmberfallMod.LOGGER.info("Ember Guardian exposed: all {} pylons broken", pylonsPlaced);
        }
        if (exposedTicksLeft > 0) {
            exposedTicksLeft--;
        }
        checkRelight(level);
        if (alive > 0 && this.tickCount % BEAM_EVERY_TICKS == 0) {
            Vec3 core = this.position().add(0, CORE_Y, 0);
            for (CinderPylon pylon : pylons) {
                Vec3 from = pylon.position().add(0, 1.4, 0);
                for (int i = 1; i <= BEAM_POINTS; i++) {
                    Vec3 at = from.lerp(core, i / (double) BEAM_POINTS);
                    level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
        }
    }

    /**
     * At each health mark one pylon re-lights and the gate closes again. Only the pylon count matters to the gate, so the
     * re-lit pylon is a fresh {@link CinderPylon}. Skipped when the terrain gave no pylon spot at all (nothing to gate on).
     */
    private void checkRelight(ServerLevel level) {
        if (relightsDone >= RELIGHT_AT.length || pylonsPlaced == 0 || pylonsAlive() > 0) {
            return;
        }
        double fraction = this.getHealth() / this.getMaxHealth();
        if (fraction > RELIGHT_AT[relightsDone]) {
            return;
        }
        List<BlockPos> spot = TerrainScanner.ringPoints(level, this.blockPosition(), 1, PYLON_RING_MIN, PYLON_RING_MAX);
        if (spot.isEmpty()) {
            relightsDone++; // no standable spot this time: skip this mark instead of retrying every tick
            return;
        }
        relightsDone++;
        phase++;
        CinderPylon pylon = new CinderPylon(ModEntities.CINDER_PYLON, level);
        pylon.setPos(spot.get(0).getX() + 0.5, spot.get(0).getY(), spot.get(0).getZ() + 0.5);
        pylon.setPersistenceRequired();
        pylon.addTag("emberfall_run");
        level.addFreshEntity(pylon);
        pylon.attachSkin(level);
        pylons.add(pylon);
        gateOpenedOnce = false; // the next break opens a fresh exposure window
        exposedTicksLeft = 0;
        level.playSound(null, pylon.getX(), pylon.getY(), pylon.getZ(),
                net.minecraft.sounds.SoundEvents.BLAZE_SHOOT, net.minecraft.sounds.SoundSource.HOSTILE, 1.2F, 0.6F);
        EmberfallMod.LOGGER.info("Ember Guardian phase {}: a pylon re-lit at {} hp fraction", phase,
                String.format("%.2f", fraction));
    }

    /** Pause between attacks: phase 3 raises the tempo (shorter pauses), not only the damage. */
    private int phaseGap() {
        return phase >= 3 ? ATTACK_GAP_TICKS_P3 : ATTACK_GAP_TICKS;
    }

    /**
     * Under CALM_BEAT_AT of its health the boss drops every attack for good and the last lit pylon collapses, so the
     * player gets a clean, visibly earned finishing window instead of fighting an invulnerable boss that no longer attacks.
     * The pylon dies through its normal death path, so the gate, the exposure window and the skin cleanup all run as usual.
     */
    private void checkCalmBeat(ServerLevel level) {
        if (calmBeat || !rigSpawned) {
            return;
        }
        if (this.getHealth() / this.getMaxHealth() > CALM_BEAT_AT) {
            return;
        }
        calmBeat = true;
        if (TEST_MODE) {
            EmberfallMod.LOGGER.info("CINDERDBG calm BEFORE pylonsAlive={} current={} attackTicks={} gated={}", pylonsAlive(), current, attackTicks, isGated());
        }
        current = null;
        attackTicks = 0;
        sparkCentres.clear();
        ringBurnLeft = 0;
        if (!pillarBlocks.isEmpty()) {
            clearPillar(level);
        }
        for (CinderPylon pylon : new ArrayList<>(pylons)) {
            if (pylon.isAlive()) {
                pylon.kill(level);
            }
        }
        if (TEST_MODE) {
            EmberfallMod.LOGGER.info("CINDERDBG calm AFTER pylonsAlive={} gated={}", pylonsAlive(), isGated());
        }
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                net.minecraft.sounds.SoundEvents.BEACON_DEACTIVATE, net.minecraft.sounds.SoundSource.HOSTILE, 1.6F, 0.6F);
        EmberfallMod.LOGGER.info("Ember Guardian calm beat at {} hp fraction",
                String.format("%.3f", this.getHealth() / this.getMaxHealth()));
    }

    /** Current phase, 1 to 3. */
    public int phase() {
        return phase;
    }

    /** Ticks of exposure left after the last pylon broke (0 when not exposed). For tests and the phase logic. */
    public int exposedTicksLeft() {
        return exposedTicksLeft;
    }

    private void updateBossBar(ServerLevel level) {
        if (bossEvent == null) {
            return;
        }
        bossEvent.setProgress(Mth.clamp(this.getHealth() / this.getMaxHealth(), 0.0F, 1.0F));
        for (ServerPlayer player : level.players()) {
            boolean shouldTrack = player.distanceToSqr(this) < 64.0 * 64.0;
            boolean tracking = bossEvent.getPlayers().contains(player);
            if (shouldTrack && !tracking) {
                bossEvent.addPlayer(player);
            } else if (!shouldTrack && tracking) {
                bossEvent.removePlayer(player);
            }
        }
    }

    // ---- lifecycle ----

    /** True only once {@link #die} has actually run; also false for a rig discarded early by run teardown. */
    public boolean wasDefeated() {
        return defeated;
    }

    /** Removes every display part and the boss bar. Idempotent. */
    public void teardownRig(ServerLevel level) {
        clearPillar(level);   // a pillar from a wind-up in progress must not outlive the fight
        if (tentacles != null) {
            tentacles.discard();
            tentacles = null;
        }
        for (Display.ItemDisplay d : new Display.ItemDisplay[]{headBase, headWaist, headCrown, coreDisplay}) {
            if (d != null && d.isAlive()) {
                d.discard();
            }
        }
        for (CinderPylon pylon : List.copyOf(pylons)) {
            if (pylon.isAlive()) {
                pylon.discard();
            }
        }
        pylons.clear();
        headBase = null;
        headWaist = null;
        headCrown = null;
        coreDisplay = null;
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
            EmberfallMod.LOGGER.info("Ember Guardian died");
            teardownRig(level);
        }
    }
}
