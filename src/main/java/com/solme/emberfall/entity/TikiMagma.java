package com.solme.emberfall.entity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3f;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Fourth horde-filler enemy (2026-09-28 non-Corrupted refinement pass, per
 * the user's own creative brief): a "Tiki" totem-pole horde mob - a size-1
 * {@link MagmaCube} (Emberfall's fire/ember theme fit better than a plain
 * green Slime, per the user's "whatever fits theme" call) that carries a
 * tiki pole. The pole is DISPLAY-ONLY (see {@link MobRig}, no invisible
 * carrier entities): rotating minecraft-heads.com Tiki Mask player heads
 * under a wide roof. Fodder stacks a second REAL magma cube ({@link TikiCube})
 * below its single head. The whole pole sways in the
 * traveling cobra-charmer dance the user linked, driven by {@link #animate}.
 *
 * ENGINE FACT that shaped this class (confirmed via decompiled
 * {@code Slime#setSize}, not guessed): {@code setSize(int, boolean)}
 * unconditionally overwrites {@code MAX_HEALTH}/{@code MOVEMENT_SPEED}/
 * {@code ATTACK_DAMAGE} with a {@code size}-based formula (health = size^2,
 * etc.), even when {@code bl} (the "heal to full" flag) is false - so it
 * must be called BEFORE this class's own {@link #createAttributes} values
 * are re-applied at spawn time, not after, or the size call silently stomps
 * this mob's real stats back down to the vanilla baseline for that size.
 * (The Pink Slime follows the same rule: its {@code applySize} re-applies its stats after every call.)
 *
 * Melee-only, matching {@link HordeSpider}'s "the base creature already
 * reads as different, no ranged kit needed" philosophy.
 *
 * THREE independent size/toughness tiers, two separate axes (2026-09-28
 * mask-cycling follow-up, per the user's explicit spawn-track decision):
 *
 * <ol>
 *   <li><b>Horde-filler axis</b> (unchanged from the original build):
 *   plain Tiki Magma spawns from {@link com.solme.emberfall.wave.WaveDirector}'s
 *   normal horde-filler pool, small chance to roll {@code veteran} (4
 *   segments instead of 3, 1.8x stats, "totem tantrum" knockback pulse on
 *   landing). Only the topmost segment wears a mask, cycling the 3-texture
 *   fodder/elite pool on a slow ~2.5s timer.</li>
 *
 *   <li><b>Elite tier</b> ({@link #spawnElite}): joins the game's REAL Elite
 *   spawn track ({@code WaveDirector#trySpawnElite}) as a 6th archetype
 *   alongside the 5 ported Corrupted mobs, using the same statMultiplier
 *   convention they all take. 3 segments, EVERY segment wears a mask (no
 *   plain body-block segments at all - a genuine multi-faced idol), each
 *   cycling the same 3-texture pool independently phase-offset from its
 *   neighbors. Visually and mechanically bigger than any horde-filler Tiki
 *   (see {@link #ELITE_SCALE}, {@link #ELITE_BASE_MAX_HEALTH}).</li>
 *
 *   <li><b>Corrupted tier</b> (a rare sub-roll INSIDE {@link #spawnElite},
 *   see {@link #CORRUPTED_CHANCE} - not its own spawn-pool entry, the same
 *   way a horde-filler's Veteran roll lives inside the normal spawn call
 *   rather than being a separate pool slot): 4 segments, EVERY segment
 *   locked to the exclusive 4th mask texture (no cycling - a still,
 *   unmistakable idol face reads as more ominous than a flicker), biggest
 *   scale of all three tiers, extra stat multiplier on top of the elite
 *   track's own scaling.</li>
 * </ol>
 */
public class TikiMagma extends MagmaCube {
    // --- Horde-filler axis (unchanged) ---
    private static final double BASE_MAX_HEALTH = 14.0;
    private static final double BASE_MOVEMENT_SPEED = 0.18;
    private static final double BASE_ATTACK_DAMAGE = 3.0;
    private static final double HORDE_VETERAN_MULTIPLIER = 1.8;


    // --- Elite tier (new: real Elite spawn track) ---
    private static final double ELITE_BASE_MAX_HEALTH = 120.0; // in line with the other ported elites' 100-200 HP baseline
    private static final double ELITE_BASE_ATTACK_DAMAGE = 7.0;
    private static final double ELITE_MOVEMENT_SPEED = 0.20;
    private static final float ELITE_SCALE = 1.6F; // Attributes.SCALE, same knob CorruptedSentinel uses (its is 3.0F for a golem-class elite; Tiki reads as a slow dancer, not a giant, hence smaller)
    private static final int ELITE_SEGMENT_COUNT = 3;
    private static final double ELITE_SWAY_MULTIPLIER = 1.15;

    // --- Corrupted tier (rare sub-roll inside spawnElite) ---
    private static final double CORRUPTED_CHANCE = 0.2; // placeholder pacing number, same spirit as VETERAN_CHANCE/ELITE_MIN_THREAT elsewhere in this mod - tune later, not guessed to be final
    private static final double CORRUPTED_STAT_BONUS = 1.35; // extra multiplier stacked on top of the elite track's own statMultiplier
    private static final float CORRUPTED_SCALE = 2.1F;
    private static final int CORRUPTED_SEGMENT_COUNT = 4;
    private static final double CORRUPTED_SWAY_MULTIPLIER = 1.3;

    // --- Fodder/elite shared cosmetic constants ---
    private static final float FODDER_HEAD_SCALE = 1.1F;
    private static final int[] CYCLING_MASK_POOL = {0, 1, 2}; // MASK_TEXTURES indices shared by fodder topper + every elite segment

    private static final double TANTRUM_KNOCKBACK = 0.55;
    private static final double TANTRUM_RADIUS = 2.2;
    private static final boolean TEST_MODE = Boolean.getBoolean("emberfall.testMode");

    private static final float CUBE_HEIGHT = 0.52F;   // a size-1 MagmaCube (2.04 * 0.255)
    private static final float ROOF_OVERHANG = 1.35F; // roof is this many times wider than a head, like the reference

    public enum EliteTier { NONE, ELITE, CORRUPTED }

    /** Display-only rig: the rotating heads and the roof. No invisible carrier entities. */
    private final MobRig rig = new MobRig(this);
    private TikiCube secondCube;
    private int headCount;
    private int firstHeadPart = -1;
    private int roofPart = -1;
    private int[] headSkin = new int[0];
    private int[] headMaskPool = CYCLING_MASK_POOL;
    private boolean headsCycle = true;
    private int fixedHeadSkin = 0;
    private float headSize = 1.0F;
    private double swayMultiplier = 1.0;
    private boolean rigBuilt = false;
    private float headYaw = Float.NaN;    // the yaw every mask currently shows; NaN until first aimed
    private static final float HEAD_TURN_DEG_PER_TICK = 18.0F;   // a half turn takes 10 ticks, so it reads as looking, not snapping
    private boolean hordeVeteran = false;
    private EliteTier eliteTier = EliteTier.NONE;
    private boolean wasOnGroundLastTick = true;
    private final TikiVoice voice = new TikiVoice();   // shriek cone + laser lane, see TikiVoice
    private final TikiLevitate levitate = new TikiLevitate();   // lift off, glide to the player, drop with the landing shove

    public TikiMagma(EntityType<? extends MagmaCube> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, BASE_MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, BASE_MOVEMENT_SPEED)
                .add(Attributes.ATTACK_DAMAGE, BASE_ATTACK_DAMAGE)
                .add(Attributes.ARMOR, 2.0);
    }

    /** Spawns a fresh horde-filler Tiki Magma (and its totem rig) at {@code pos}. Call this instead of plain construction. */
    public static TikiMagma spawn(ServerLevel level, Vec3 pos, boolean veteran) {
        TikiMagma tiki = new TikiMagma(ModEntities.TIKI_MAGMA, level);
        tiki.setSize(1, false); // lock the visual model to the smallest magma-cube scale before...
        tiki.reapplyBaseStats(); // ...re-applying our real stats, undoing setSize's own size-based overwrite (see class javadoc)
        tiki.setPos(pos.x, pos.y, pos.z);
        tiki.hordeVeteran = veteran;
        if (veteran) {
            AttributeInstance hp = tiki.getAttribute(Attributes.MAX_HEALTH);
            AttributeInstance dmg = tiki.getAttribute(Attributes.ATTACK_DAMAGE);
            if (hp != null) hp.setBaseValue(BASE_MAX_HEALTH * HORDE_VETERAN_MULTIPLIER);
            if (dmg != null) dmg.setBaseValue(BASE_ATTACK_DAMAGE * HORDE_VETERAN_MULTIPLIER);
            tiki.setHealth((float) tiki.getMaxHealth());
            MobNames.apply(tiki, "Tiki Magma", MobNames.Tier.LAVA);
        }
        level.addFreshEntity(tiki);
        tiki.buildStack(level);
        return tiki;
    }

    /**
     * Spawns an Elite-tier Tiki Magma (or, on the {@link #CORRUPTED_CHANCE}
     * roll, its Corrupted upgrade) - the entry point
     * {@code WaveDirector#trySpawnElite} calls alongside the other 5 ported
     * archetypes, taking the same {@code statMultiplier} convention they
     * all do (1.0 at threat 0 -> 2.0 at the threat cap).
     */
    public static TikiMagma spawnElite(ServerLevel level, BlockPos pos, double statMultiplier) {
        TikiMagma tiki = new TikiMagma(ModEntities.TIKI_MAGMA, level);
        tiki.setSize(1, false);
        tiki.reapplyBaseStats();
        tiki.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        tiki.setYRot(level.getRandom().nextFloat() * 360.0F);

        boolean corrupted = level.getRandom().nextDouble() < CORRUPTED_CHANCE;
        tiki.eliteTier = corrupted ? EliteTier.CORRUPTED : EliteTier.ELITE;

        double statBonus = corrupted ? CORRUPTED_STAT_BONUS : 1.0;
        double hpValue = ELITE_BASE_MAX_HEALTH * statMultiplier * statBonus;
        double dmgValue = ELITE_BASE_ATTACK_DAMAGE * statMultiplier * statBonus;

        AttributeInstance hp = tiki.getAttribute(Attributes.MAX_HEALTH);
        AttributeInstance dmg = tiki.getAttribute(Attributes.ATTACK_DAMAGE);
        AttributeInstance speed = tiki.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance scale = tiki.getAttribute(Attributes.SCALE);
        if (hp != null) hp.setBaseValue(hpValue);
        if (dmg != null) dmg.setBaseValue(dmgValue);
        if (speed != null) speed.setBaseValue(ELITE_MOVEMENT_SPEED);
        if (scale != null) scale.setBaseValue(corrupted ? CORRUPTED_SCALE : ELITE_SCALE);
        tiki.setHealth((float) tiki.getMaxHealth());

        if (corrupted) {
            MobNames.apply(tiki, "Corrupted Tiki Idol", MobNames.Tier.CORRUPTED);
        } else {
            MobNames.apply(tiki, "Tiki Idol", MobNames.Tier.LAVA);
        }
        tiki.setCustomNameVisible(true);
        tiki.setPersistenceRequired();

        level.addFreshEntity(tiki);
        tiki.buildStack(level);
        return tiki;
    }

    public EliteTier getEliteTier() {
        return eliteTier;
    }

    private void reapplyBaseStats() {
        AttributeInstance hp = this.getAttribute(Attributes.MAX_HEALTH);
        AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance dmg = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (hp != null) hp.setBaseValue(BASE_MAX_HEALTH);
        if (speed != null) speed.setBaseValue(BASE_MOVEMENT_SPEED);
        if (dmg != null) dmg.setBaseValue(BASE_ATTACK_DAMAGE);
        this.setHealth((float) this.getMaxHealth());
    }

    /**
     * Builds the totem on displays only (no invisible carrier entities). Layout, bottom to top, in blocks at mob
     * scale 1.0 (the rig multiplies by the mob's SCALE, so Elite and Corrupted grow with no extra maths):
     * the real mob cube is the hitbox; Fodder stacks a second REAL magma cube above it, then one rotating-skin
     * head; Elite and Corrupted stack flush heads directly (3 and 4); every tier is capped with a roof.
     * A head at scale s is 0.5 * s blocks tall (skull box is 8x8x8 px, read from SkullModel), so stacking
     * heads at 0.5 * s leaves no gap and no overlap.
     */
    private void buildStack(ServerLevel level) {
        if (rigBuilt) {
            return;
        }
        rigBuilt = true;
        boolean fancy = eliteTier != EliteTier.NONE;
        headCount = switch (eliteTier) {
            case CORRUPTED -> CORRUPTED_SEGMENT_COUNT;
            case ELITE -> ELITE_SEGMENT_COUNT;
            case NONE -> 1;
        };
        headSize = fancy ? 1.0F : FODDER_HEAD_SCALE;
        swayMultiplier = switch (eliteTier) {
            case CORRUPTED -> CORRUPTED_SWAY_MULTIPLIER;
            case ELITE -> ELITE_SWAY_MULTIPLIER;
            case NONE -> 1.0;
        };
        headsCycle = eliteTier != EliteTier.CORRUPTED;
        fixedHeadSkin = TikiSegment.CORRUPTED_MASK_INDEX;
        headSkin = new int[headCount];

        float y = CUBE_HEIGHT;
        if (!fancy) {
            TikiCube cube = new TikiCube(ModEntities.TIKI_CUBE, level);
            cube.setSize(1, false);
            cube.bind(this);
            cube.setPos(this.getX(), this.getY() + y, this.getZ());
            level.addFreshEntity(cube);
            secondCube = cube;
            y += CUBE_HEIGHT;
        }
        float step = 0.5F * headSize;
        for (int i = 0; i < headCount; i++) {
            int skin = headsCycle ? CYCLING_MASK_POOL[i % CYCLING_MASK_POOL.length] : fixedHeadSkin;
            headSkin[i] = skin;
            var display = rig.addItem(level, EliteHeads.customHead(TikiSegment.MASK_TEXTURES[skin], "tiki_mask"),
                    new Vector3f(0.0F, y + step / 2.0F, 0.0F), headSize, MaskFacing.CLIENT_HEAD_TURN_DEG, false);   // a head item is drawn a half turn from the display yaw
            display.setBillboardConstraints(Display.BillboardConstraints.FIXED);
            if (i == 0) {
                firstHeadPart = rig.size() - 1;
            }
            y += step;
        }
        // Roof: one wide, flat dark-oak slab, wider than the heads like the reference hat.
        float roofW = headSize * 0.5F * ROOF_OVERHANG;
        roofPart = rig.size();
        rig.addBlock(level, Blocks.DARK_OAK_SLAB.defaultBlockState(),
                new Vector3f(0.0F, y + roofW * 0.5F, 0.0F), roofW, 0.0F);
    }

    /**
     * Turns every mask toward the nearest player, so the skins are always seen face on. Uses the current target when there
     * is one and the nearest player otherwise (within 64 blocks), and eases toward the bearing at a fixed rate. With nobody
     * in range the masks go back to following the body. Cost: one nearest-player lookup per tick, no entities.
     */
    private void aimHeads() {
        net.minecraft.world.entity.player.Player who = this.getTarget() instanceof net.minecraft.world.entity.player.Player t
                && t.isAlive() ? t : this.level().getNearestPlayer(this, 64.0);
        if (who == null || who.isSpectator()) {
            if (!Float.isNaN(headYaw)) {
                headYaw = Float.NaN;
                for (int i = 0; i < headCount; i++) {
                    rig.setYaw(firstHeadPart + i, Float.NaN);
                }
            }
            return;
        }
        double dx = who.getX() - this.getX(), dz = who.getZ() - this.getZ();
        if (dx * dx + dz * dz < 1.0E-4) {
            return;   // standing exactly on top of the player: the bearing is undefined, keep the last yaw
        }
        // The display yaw that shows the FACE of a head item to the player is the bearing plus a half turn (see MaskFacing: the client
        // draws a head item 180 degrees from the display yaw), so aiming the display at the player would show them the back of the skull.
        float want = MaskFacing.yawToShowFace((float) (Math.atan2(-dx, dz) * 180.0 / Math.PI));
        if (Float.isNaN(headYaw)) {
            headYaw = want;   // first aim: start on target instead of sweeping in from the body yaw
        } else {
            float diff = net.minecraft.util.Mth.wrapDegrees(want - headYaw);
            headYaw = net.minecraft.util.Mth.wrapDegrees(headYaw
                    + net.minecraft.util.Mth.clamp(diff, -HEAD_TURN_DEG_PER_TICK, HEAD_TURN_DEG_PER_TICK));
        }
        for (int i = 0; i < headCount; i++) {
            rig.setYaw(firstHeadPart + i, headYaw);
        }
    }

    /**
     * Once a tick: the traveling sway (each head lags the one below, amplitude grows up the pole like a
     * charmed cobra), the second cube and roof following the sway, and the slow skin rotation. The rig itself
     * only writes positions every second tick and lets the client glide between them.
     */
    private void animate() {
        float t = (float) this.tickCount;
        float amp = (float) (TikiSegment.SWAY_AMPLITUDE * swayMultiplier);
        float y = CUBE_HEIGHT + (secondCube != null ? CUBE_HEIGHT : 0.0F);
        float step = 0.5F * headSize;
        float lastSway = 0.0F;
        for (int i = 0; i < headCount; i++) {
            float phase = t + (secondCube != null ? 1 : i + 1) * TikiSegment.PHASE_DELAY_PER_SEGMENT_TICKS;
            float level = (i + (secondCube != null ? 2 : 1));
            float sway = net.minecraft.util.Mth.sin(phase / TikiSegment.SWAY_PERIOD_TICKS * net.minecraft.util.Mth.TWO_PI)
                    * amp * (1.0F + level * 0.35F);
            rig.setLocal(firstHeadPart + i, sway, y + step / 2.0F, 0.0F);
            lastSway = sway;
            y += step;
        }
        aimHeads();
        if (roofPart >= 0) {
            float roofW = headSize * 0.5F * ROOF_OVERHANG;
            rig.setLocal(roofPart, lastSway, y + roofW * 0.5F, 0.0F);
        }
        if (secondCube != null && secondCube.isAlive()) {
            // The second cube sits directly on the first and is nudged by a small share of the sway.
            double side = net.minecraft.util.Mth.sin((t + TikiSegment.PHASE_DELAY_PER_SEGMENT_TICKS)
                    / TikiSegment.SWAY_PERIOD_TICKS * net.minecraft.util.Mth.TWO_PI) * amp * 0.5;
            float yawRad = this.yBodyRot * net.minecraft.util.Mth.DEG_TO_RAD;
            double rx = -net.minecraft.util.Mth.cos(yawRad), rz = -net.minecraft.util.Mth.sin(yawRad);
            secondCube.setPos(this.getX() + rx * side, this.getY() + CUBE_HEIGHT * this.getScale(), this.getZ() + rz * side);
            secondCube.setYRot(this.getYRot());
            secondCube.setYBodyRot(this.yBodyRot);
            secondCube.setYHeadRot(this.yHeadRot);
        }
        if (headsCycle) {
            for (int i = 0; i < headCount; i++) {
                int phaseTicks = this.tickCount + i * TikiSegment.MASK_PHASE_DELAY_PER_SEGMENT_TICKS;
                int idx = (phaseTicks / TikiSegment.MASK_CYCLE_PERIOD_TICKS) % headMaskPool.length;
                int skin = headMaskPool[idx];
                if (skin != headSkin[i]) {
                    headSkin[i] = skin;
                    rig.setItem(firstHeadPart + i, EliteHeads.customHead(TikiSegment.MASK_TEXTURES[skin], "tiki_mask"));
                }
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (rigBuilt && this.level() instanceof ServerLevel && this.isAlive()) {
            animate();
            rig.tick();
        }
        if (this.level() instanceof ServerLevel serverLevel && this.isAlive()) {
            // "Totem tantrum": a shove pulse the instant it lands from a hop, themed as the idol
            // stomping mid-dance - reuses the same onGround-edge detection Slime#tick already does
            // internally for its squish-particle burst, just our own copy since that method is private
            // state on the vanilla class, not exposed for reuse. Horde-filler Veteran and both Elite
            // tiers get this; plain fodder does not.
            boolean tantrumEligible = hordeVeteran || eliteTier != EliteTier.NONE;
            boolean onGroundNow = this.onGround();
            if (tantrumEligible && onGroundNow && !wasOnGroundLastTick) {
                if (TEST_MODE && levitate.droppedFromGlide(serverLevel.getGameTime())) { // sandbox test server only; the levitation test judges from this line
                    com.solme.emberfall.EmberfallMod.LOGGER.info("TIKI_TEST glide landing tier={} shove radius={}", eliteTier, TANTRUM_RADIUS);
                }
                tantrumPulse(serverLevel);
            }
            if (tantrumEligible && wasOnGroundLastTick && !onGroundNow) {
                // Just left the ground: mark the landing zone now so the shove is dodgeable, not a surprise.
                double mult = switch (eliteTier) { case CORRUPTED -> 1.6; case ELITE -> 1.3; case NONE -> 1.0; };
                com.solme.emberfall.combat.Fx.telegraphRing(serverLevel, this.position(), TANTRUM_RADIUS * mult,
                        com.solme.emberfall.combat.Fx.WARN_ORANGE);
            }
            wasOnGroundLastTick = onGroundNow;
            // Shriek (veteran fodder, elite, corrupted) and laser (elite, corrupted): a player-only ranged pair.
            voice.tick(this, serverLevel, this.getTarget(), eliteTier, hordeVeteran,
                    this.getAttributeValue(Attributes.ATTACK_DAMAGE), levitate.aloft());
            levitate.tick(this, serverLevel, this.getTarget(), eliteTier, hordeVeteran, voice.busy());
            net.minecraft.world.entity.player.Player facingTarget = TEST_MODE ? serverLevel.getNearestPlayer(this, 60.0) : null;
            if (TEST_MODE && rigBuilt && firstHeadPart >= 0 && this.tickCount % 10 == 0 && facingTarget != null) {
                // Sandbox test server only: head yaw vs the exact bearing to the target, same tick, so facing is judged
                // from server state and not from chat latency. Bearing uses the same yaw convention as MobRig (0 = +Z).
                double bx = facingTarget.getX() - this.getX(), bz = facingTarget.getZ() - this.getZ();
                float bearing = (float) (Math.atan2(-bx, bz) * 180.0 / Math.PI);
                com.solme.emberfall.EmberfallMod.LOGGER.info("TIKI_FACE tier={} vet={} aloft={} hasTarget={} head={} body={} bearing={} dist={}",
                        eliteTier, hordeVeteran, levitate.aloft(), this.getTarget() != null, String.format("%.0f", rig.yawOf(firstHeadPart)),
                        String.format("%.0f", this.yBodyRot), String.format("%.0f", bearing),
                        String.format("%.1f", Math.sqrt(bx * bx + bz * bz)));
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        levitate.cancel(this);
        // Same orphan-passenger leak concern as HydraHead.remove - stopping a
        // ride only unmounts the passenger, it does NOT discard it. The previous isAlive()-check-in-
        // tick() approach this replaced was unreliable: it only ever ran on the entity's OWN next
        // tick, which never happens for direct discard() removals (e.g. a debug command discarding a
        // rerolled non-Corrupted attempt) since a discarded entity is pulled from the level's tick
        // list immediately - confirmed via a live repro (48 orphaned TikiSegments + 45 orphaned
        // displays left behind after /kill-ing 3 TikiMagma that between them should have produced far
        // fewer). remove() runs for every removal path (death, discard, unload), so this covers all of
        // them, matching the rest of this mod's composite-mob convention.
        rig.discard();
        if (secondCube != null && !secondCube.isRemoved()) {
            secondCube.discard();
        }
        super.remove(reason);
    }

    private void tantrumPulse(ServerLevel level) {
        double radiusMultiplier = switch (eliteTier) {
            case CORRUPTED -> 1.6;
            case ELITE -> 1.3;
            case NONE -> 1.0;
        };
        double radius = TANTRUM_RADIUS * radiusMultiplier;
        double knockback = TANTRUM_KNOCKBACK * radiusMultiplier;
        for (net.minecraft.world.entity.LivingEntity nearby : level.getEntitiesOfClass(
                net.minecraft.world.entity.LivingEntity.class,
                this.getBoundingBox().inflate(radius),
                e -> e instanceof net.minecraft.world.entity.player.Player)) {
            Vec3 away = nearby.position().subtract(this.position());
            double dist = Math.max(0.5, away.length());
            Vec3 push = away.normalize().scale(knockback * (1.0 - Math.min(1.0, dist / radius)));
            nearby.setDeltaMovement(nearby.getDeltaMovement().add(push.x, 0.15, push.z));
        }
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                net.minecraft.sounds.SoundEvents.MAGMA_CUBE_JUMP, net.minecraft.sounds.SoundSource.HOSTILE, 1.2F, 0.6F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                this.getX(), this.getY() + 0.2, this.getZ(), (int) (20 * radiusMultiplier), 0.6, 0.2, 0.6, 0.02);
        // Show the exact shove radius so the player learns how far to stay (red/orange = damaging, per the
        // colour language shared by Fx). Same radius the knockback loop above actually uses.
        com.solme.emberfall.combat.Fx.telegraphRing(level, this.position(), radius, com.solme.emberfall.combat.Fx.WARN_ORANGE);
    }
}
