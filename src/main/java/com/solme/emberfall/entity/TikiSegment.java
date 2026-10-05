package com.solme.emberfall.entity;

import com.mojang.math.Transformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One stacked body segment of {@link TikiMagma} - the "totem pole" look the
 * user asked for. Same invisible-ArmorStand-as-rigid-attachment-point
 * technique as the retired HydraAnchor, but unlike HydraAnchor's fixed local
 * offset, this segment's own {@link #getPassengerRidingPosition} computes a
 * TIME-VARYING horizontal offset every tick - the user's reference ("this is
 * the dance i was talking about" -> a snake-charmer/cobra-sway GIF): a
 * traveling sine wave down the stack, each segment's phase delayed behind
 * the one below it, so the whole totem reads as one continuous swaying
 * dance rather than a rigid pole. {@link #getPassengerRidingPosition} is
 * queried every tick by the vanilla passenger-position pipeline regardless
 * of who's asking, so basing the sway on it (rather than a separate tick
 * hook) means whatever rides this segment (the next segment up, or nothing)
 * automatically inherits the live sway with zero extra plumbing.
 *
 * Mask-cycling (2026-09-28 follow-up, per the user's own creative brief):
 * a masked segment doesn't have to keep the same Tiki Mask face forever - it
 * can flicker through a pool of alternate mask textures on a slow timer
 * (2-3s, "a deliberate rotation, not a jitter"), each masked segment's cycle
 * phase-offset by its {@link #segmentIndex} the same way the sway phase is,
 * so a totem with several masked segments never has them all flip in
 * lockstep. {@link TikiMagma} decides per-tier which segments get a mask at
 * all, whether that mask cycles or stays locked, and which texture pool it
 * draws from - this class just executes whatever {@link #configure} tells
 * it (see that method's javadoc for the exact per-tier wiring).
 *
 * Not a {@link com.solme.emberfall.combat.CompositeParts} part and never
 * takes real damage - purely decorative body mass riding the real hittable
 * {@link TikiMagma} mob, same "decoration vs. the one real hitbox" split as
 * the retired Devourer armor-stand plates.
 */
public class TikiSegment extends ArmorStand {
    static final int SWAY_PERIOD_TICKS = 40; // 2s per full sway cycle - a slow, deliberate charmer sway, not a jitter
    static final int PHASE_DELAY_PER_SEGMENT_TICKS = 8; // higher segments lag behind lower ones -> traveling wave down the pole
    static final double SWAY_AMPLITUDE = 0.16;
    static final int MASK_CYCLE_PERIOD_TICKS = 50; // ~2.5s per mask swap, mid-point of the user's requested "2-3s"
    static final int MASK_PHASE_DELAY_PER_SEGMENT_TICKS = 15; // masked segments flicker offset from each other, not in lockstep

    // minecraft-heads.com "Tiki Mask" set (contributor SakurasouShiina), all 4 verified 2026-09-28 to
    // decode to real textures.minecraft.net URLs (200 OK) before being hardcoded - same verification
    // standard as every other custom head texture in this mod. Index 0-2 are the shared cycling pool
    // for the fodder/elite tiers; index 3 is the Corrupted tier's own exclusive, non-cycling face.
    static final String[] MASK_TEXTURES = {
            // ID 116444
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzVmZDZiOWE1OWVjNWI5N2RiOGJkYzE1OGZiZDVmOTFlZjdiMzE3Yjg1OWZjZWJlNmQwOWU3YmQ4MGVhY2E5ZCJ9fX0=",
            // ID 116443
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTEyMmY3YTE5YjMxOTc3NjZiMzgxZmIzNmJmZWI2ZjQ0MmQ2MjUwOWU0NGNjNzg0N2M3NWM4ZThjMzg3MjI1YSJ9fX0=",
            // ID 116442
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjNjODAyZTU4MGJmZWZjMThjNGFmOTRjY2ViODI5NjhiNWI0YWVhYjBkODMyMzQ2YTYzM2E3NDczYTQxZGZhYyJ9fX0=",
            // ID 116441 - Corrupted tier's exclusive face, never in the cycling pool (see class javadoc)
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTY0MzMxYzhmYjc1MGY5MDQzMzM0MzIwYzk0NTgwZTc4OTY5NTU2OTUxNTZkODA2ODllNWQwYTZjNjBhMTBlNyJ9fX0="
    };
    static final int CORRUPTED_MASK_INDEX = 3;

    private int segmentIndex; // 0 = bottommost (rides the real TikiMagma mob), higher = further up the pole
    private boolean hasMask; // true if this segment wears a mask display instead of a plain body block
    private boolean cyclingMask; // true if the mask flickers through maskPool; false = locked to a single fixed texture
    private int[] maskPool; // indices into MASK_TEXTURES this segment cycles through (ignored if !cyclingMask)
    private int fixedMaskIndex; // used when !cyclingMask
    private double verticalStep = 0.5; // per-tier vertical spacing, set by configure()
    private float bodyScale = 0.85F;
    private float headScale = 1.1F;
    private double swayAmplitudeMultiplier = 1.0;

    private Display display;
    private int currentMaskIndexInPool = -1; // -1 = not yet applied, forces the first attachDisplay to set it

    public TikiSegment(EntityType<? extends ArmorStand> type, Level level) {
        super(type, level);
        this.setInvisible(true);
        this.setNoGravity(true);
        this.setInvulnerable(true);
        this.setSilent(true);
        this.noPhysics = true;
    }

    /**
     * Wires up this segment's per-tier look. {@code maskPool} is the set of
     * {@link #MASK_TEXTURES} indices this segment flickers through when
     * {@code cyclingMask} is true (fodder: only the topper gets a mask,
     * cycling indices {0,1,2}; elite: every segment gets a mask, each
     * cycling {0,1,2} phase-offset by its own index; corrupted: every
     * segment gets a mask locked to {@link #CORRUPTED_MASK_INDEX}, no
     * cycling - a still, unmistakable idol face rather than a flicker).
     * When {@code cyclingMask} is false, {@code fixedMaskIndex} is used
     * instead and {@code maskPool} is ignored (pass null).
     */
    public void configure(int segmentIndex, boolean hasMask, boolean cyclingMask, int[] maskPool, int fixedMaskIndex,
                           double verticalStep, float bodyScale, float headScale, double swayAmplitudeMultiplier) {
        this.segmentIndex = segmentIndex;
        this.hasMask = hasMask;
        this.cyclingMask = cyclingMask;
        this.maskPool = maskPool;
        this.fixedMaskIndex = fixedMaskIndex;
        this.verticalStep = verticalStep;
        this.bodyScale = bodyScale;
        this.headScale = headScale;
        this.swayAmplitudeMultiplier = swayAmplitudeMultiplier;
    }

    /** Spawns this segment's visual (a magma-cube-look block for plain body segments, a Tiki Mask face for masked ones). Call once right after adding to the level. */
    public void attachDisplay(ServerLevel level) {
        if (display != null) {
            return;
        }
        if (hasMask) {
            Display.ItemDisplay itemDisplay = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
            itemDisplay.setPos(this.getX(), this.getY(), this.getZ());
            currentMaskIndexInPool = 0;
            itemDisplay.setItemStack(EliteHeads.customHead(MASK_TEXTURES[activeMaskTextureIndex()], "tiki_mask"));
            itemDisplay.setBillboardConstraints(Display.BillboardConstraints.FIXED); // carved pole: faces follow the body yaw, never the camera
            applyTransform(itemDisplay, headScale, 0.15F);
            itemDisplay.setNoGravity(true);
            itemDisplay.setInvulnerable(true);
            level.addFreshEntity(itemDisplay);
            itemDisplay.startRiding(this, true, false);
            this.display = itemDisplay;
        } else {
            Display.BlockDisplay blockDisplay = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
            blockDisplay.setPos(this.getX(), this.getY(), this.getZ());
            blockDisplay.setBlockState(Blocks.MAGMA_BLOCK.defaultBlockState());
            applyTransform(blockDisplay, bodyScale, 0.0F);
            blockDisplay.setNoGravity(true);
            blockDisplay.setInvulnerable(true);
            level.addFreshEntity(blockDisplay);
            blockDisplay.startRiding(this, true, false);
            this.display = blockDisplay;
        }
    }

    private void applyTransform(Display d, float scale, float extraYOffset) {
        d.setTransformation(new Transformation(
                new Vector3f(-scale / 2.0F, -scale / 2.0F + extraYOffset, -scale / 2.0F),
                new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf()));
    }

    private int activeMaskTextureIndex() {
        if (!cyclingMask) {
            return fixedMaskIndex;
        }
        return maskPool[currentMaskIndexInPool % maskPool.length];
    }

    /** The live traveling-sway horizontal offset for whatever rides this segment, in this segment's own facing frame. */
    private Vec3 swayOffset() {
        int phaseTicks = this.tickCount + segmentIndex * PHASE_DELAY_PER_SEGMENT_TICKS;
        float angle = ((float) phaseTicks / (float) SWAY_PERIOD_TICKS) * (Mth.TWO_PI);
        double sway = Mth.sin(angle) * SWAY_AMPLITUDE * swayAmplitudeMultiplier * (1.0 + segmentIndex * 0.35); // amplitude grows up the pole, like a real cobra sway
        // Sway along the mob's own local "side" axis (perpendicular to facing) so it reads as a left-right dance
        // regardless of which way the totem is currently facing, not a fixed world-axis wobble.
        float yawRad = (float) Math.toRadians(this.getYRot() + 90.0F);
        double dx = Mth.cos(yawRad) * sway;
        double dz = Mth.sin(yawRad) * sway;
        return new Vec3(dx, verticalStep, dz);
    }

    @Override
    public Vec3 getPassengerRidingPosition(Entity passenger) {
        return this.position().add(swayOffset());
    }

    @Override
    public void tick() {
        super.tick();
        // Mirror whatever mounts this segment's own facing (kept in sync from TikiMagma -> segment0 -> segment1...
        // via TikiMagma#tick) so the sway direction and the display's own yaw agree with the totem's current heading.
        if (this.getVehicle() != null) {
            this.setYRot(this.getVehicle().getYRot());
        }
        // FIXED billboard means the mask faces its OWN yaw, so keep it aligned with the pole (masked segments only;
        // plain body blocks are symmetric). Only writes when the heading actually changed.
        if (hasMask && display != null && display.isAlive() && display.getYRot() != this.getYRot()) {
            display.setYRot(this.getYRot());
            display.setYHeadRot(this.getYRot());
        }
        if (hasMask && cyclingMask && display instanceof Display.ItemDisplay itemDisplay && this.level() instanceof ServerLevel) {
            int phaseTicks = this.tickCount + segmentIndex * MASK_PHASE_DELAY_PER_SEGMENT_TICKS;
            int targetIndex = (phaseTicks / MASK_CYCLE_PERIOD_TICKS) % maskPool.length;
            if (targetIndex != currentMaskIndexInPool) {
                currentMaskIndexInPool = targetIndex;
                itemDisplay.setItemStack(EliteHeads.customHead(MASK_TEXTURES[activeMaskTextureIndex()], "tiki_mask"));
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        // Same orphan-display leak concern as HydraHead.remove - the mask
        // or body-block display only ever exists as this segment's passenger, and stopping a ride
        // (which is all removing a vehicle does to its passengers) does not discard the passenger
        // itself. Must run for every removal reason, not just KILLED, or any non-combat removal path
        // (debug commands discarding a rerolled TikiMagma, dimension unload, etc.) leaks the display.
        if (display != null && display.isAlive()) {
            display.discard();
        }
        super.remove(reason);
    }

    public boolean hasDisplay() {
        return display != null;
    }
}
