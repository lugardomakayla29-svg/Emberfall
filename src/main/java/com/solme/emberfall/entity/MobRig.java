package com.solme.emberfall.entity;

import com.mojang.math.Transformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Glues decorative display entities to a mob's BODY instead of to a vanilla passenger seat.
 *
 * <p>Why not {@code startRiding}: a passenger is positioned by the mount's passenger attachment
 * point, which for a Spider is the top of its back, for a Slime the top of its cube, and so on. Any
 * accessory that rides a raw vanilla mob therefore starts at the wrong place, does not follow the
 * mob's SCALE attribute, and (measured in an earlier pass) makes {@code isVehicle()} true, which
 * silently disables vanilla melee goals. Real models were read from the mapped client jar (SpiderModel,
 * SlimeModel): the spider head cube is centred 9/16 up and 7/16 forward of the feet, not on the back.
 *
 * <p>Each part stores a LOCAL offset in body space (right, up, forward, in blocks at scale 1.0). Every
 * tick the rig converts that to world space using the mob's body yaw and scale, and lets the client
 * interpolate between ticks so the accessory glides with the body instead of snapping.
 *
 * <p>Performance: one small loop per rig per tick, no allocations beyond a Vec3, and interpolation
 * lets us update every {@link #UPDATE_INTERVAL} ticks rather than every tick.
 */
public final class MobRig {
    /** Client-side glide length. Slightly longer than the update interval so motion never visibly stops. */
    private static final int INTERP_TICKS = 3;
    private static final int UPDATE_INTERVAL = 2;

    private final LivingEntity owner;
    private final List<Part> parts = new ArrayList<>();

    public MobRig(LivingEntity owner) {
        this.owner = owner;
    }

    private static final class Part {
        final Display display;
        final Vector3f local;        // right, up, forward, blocks at scale 1
        final float yawOffsetDeg;    // extra yaw relative to the body
        final boolean followBodyYaw; // false = billboard toward the camera instead
        final float baseSize;        // display scale at mob scale 1.0
        final boolean isBlock;       // block displays are corner-anchored and need a centring shift
        float appliedScale = Float.NaN;
        float yawOverride = Float.NaN;   // NaN = follow the body; otherwise the yaw the owner wants this part to show
        Part(Display d, Vector3f local, float yawOffsetDeg, boolean followBodyYaw, float baseSize, boolean isBlock) {
            this.display = d; this.local = local; this.yawOffsetDeg = yawOffsetDeg; this.followBodyYaw = followBodyYaw;
            this.baseSize = baseSize; this.isBlock = isBlock;
        }
    }

    /**
     * Adds an item accessory (for example a player head).
     *
     * @param local        right/up/forward offset from the mob's feet, in blocks at scale 1.0
     * @param size         uniform display scale at mob scale 1.0
     * @param yawOffsetDeg extra yaw so a face points the way the mob looks
     * @param billboard    true to always face the camera (useful for flat sprite items)
     */
    public Display.ItemDisplay addItem(ServerLevel level, ItemStack stack, Vector3f local, float size,
                                       float yawOffsetDeg, boolean billboard) {
        Display.ItemDisplay d = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
        d.setItemStack(stack);
        prepare(d, size);
        if (billboard) {
            d.setBillboardConstraints(Display.BillboardConstraints.CENTER);
        }
        parts.add(new Part(d, local, yawOffsetDeg, !billboard, size, false));
        place(parts.get(parts.size() - 1), true);
        level.addFreshEntity(d);
        return d;
    }

    /** Adds a block accessory (armour plates, spikes, a crystal, and so on). */
    public Display.BlockDisplay addBlock(ServerLevel level, BlockState state, Vector3f local, float size, float yawOffsetDeg) {
        Display.BlockDisplay d = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
        d.setBlockState(state);
        prepare(d, size);
        parts.add(new Part(d, local, yawOffsetDeg, true, size, true));
        place(parts.get(parts.size() - 1), true);
        level.addFreshEntity(d);
        return d;
    }

    private void prepare(Display d, float size) {
        d.setNoGravity(true);
        d.setInvulnerable(true);
        d.setPosRotInterpolationDuration(INTERP_TICKS);
        d.setViewRange(1.5F);
        d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(size, size, size), new Quaternionf()));
    }

    /** Call once per server tick from the owning mob. */
    public void tick() {
        if (owner.isRemoved()) {
            discard();
            return;
        }
        if (owner.tickCount % UPDATE_INTERVAL != 0) {
            return;
        }
        for (Part p : parts) {
            place(p, false);
        }
    }

    private void place(Part p, boolean snap) {
        float scale = owner.getScale();
        float yawRad = owner.yBodyRot * Mth.DEG_TO_RAD;
        double sin = Mth.sin(yawRad);
        double cos = Mth.cos(yawRad);
        // Minecraft: yaw 0 faces +Z (south); "forward" is (-sin, cos), "right" (as seen from behind) is (-cos, -sin).
        double fx = -sin, fz = cos;
        double rx = -cos, rz = -sin;
        double x = owner.getX() + (rx * p.local.x + fx * p.local.z) * scale;
        double y = owner.getY() + p.local.y * scale;
        double z = owner.getZ() + (rz * p.local.x + fz * p.local.z) * scale;
        float yaw = !Float.isNaN(p.yawOverride) ? p.yawOverride
                : p.followBodyYaw ? owner.yBodyRot + p.yawOffsetDeg : p.display.getYRot();
        if (snap) {
            p.display.setPos(x, y, z);
            p.display.setYRot(yaw);
            p.display.setXRot(0.0F);
        } else {
            p.display.setPos(x, y, z);
            p.display.setYRot(yaw);
        }
        // Keep the accessory in proportion when the mob's SCALE attribute is not 1.0.
        if (p.appliedScale != scale) {
            p.appliedScale = scale;
            applyScale(p, scale, snap);
        }
    }

    private static void applyScale(Part p, float mobScale, boolean snap) {
        float s = p.baseSize * mobScale;
        // A block display's origin is its corner, so shift by half its size to centre it on the anchor.
        Vector3f translation = p.isBlock ? new Vector3f(-s / 2.0F, -s / 2.0F, -s / 2.0F) : new Vector3f();
        if (!snap) {
            p.display.setTransformationInterpolationDelay(0);
            p.display.setTransformationInterpolationDuration(INTERP_TICKS);
        }
        p.display.setTransformation(new Transformation(translation, new Quaternionf(), new Vector3f(s, s, s), new Quaternionf()));
    }

    /** Live-updates part {@code index}'s local (right, up, forward) offset, for example a sway. Applied on the next tick. */
    public void setLocal(int index, float right, float up, float forward) {
        if (index < 0 || index >= parts.size()) {
            return;   // rig not attached (plain /summon) or part removed: nothing to move, never crash the tick
        }
        parts.get(index).local.set(right, up, forward);
    }

    /** Swaps the item shown by an item part, for example a rotating skin. Does nothing for block parts. */
    public void setItem(int index, ItemStack stack) {
        if (index < 0 || index >= parts.size()) {
            return;
        }
        if (parts.get(index).display instanceof Display.ItemDisplay item) {
            item.setItemStack(stack);
        }
    }

    /**
     * Makes part {@code index} show this yaw (degrees, Minecraft convention: 0 faces +Z) instead of following the body,
     * for example a head that looks at the target. Pass NaN to go back to following the body. Applied on the next update.
     */
    public void setYaw(int index, float yawDeg) {
        if (index < 0 || index >= parts.size()) {
            return;
        }
        parts.get(index).yawOverride = yawDeg;
    }

    /** The yaw currently written to part {@code index}'s display, or NaN when there is no such part. Read-only, for tests. */
    public float yawOf(int index) {
        return index < 0 || index >= parts.size() ? Float.NaN : parts.get(index).display.getYRot();
    }

    public int size() {
        return parts.size();
    }

    /** Removes every part. Safe to call more than once. */
    public void discard() {
        for (Part p : parts) {
            if (!p.display.isRemoved()) {
                p.display.discard();
            }
        }
        parts.clear();
    }

    public boolean isEmpty() {
        return parts.isEmpty();
    }
}
