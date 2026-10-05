package com.solme.emberfall.entity;

import com.mojang.math.Transformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The Ember Guardian's tentacles: {@code count} chains of {@code links} item displays each, rooted on the head and steered
 * by a target point per tentacle. Display entities only (no armor stands, no passengers), {@code count * links} entities in
 * total, and one small loop per tick. The geometry lives in {@link TentacleMath} so it can be measured without a server.
 *
 * <p>Look, from the Kuudra reference (measured, not guessed): the scene is mostly near black and deep red with only about a
 * tenth bright lava, so a tentacle is dark basalt that heats toward a magma tip, thick at the root and thin at the end.</p>
 */
public final class TentacleRig {
    /** Marks a display as part of a tentacle so tools and run teardown can tell it from the head parts. */
    public static final String TAG = "emberfall_tentacle";
    private static final int GLIDE_TICKS = 2;

    public static final float BASE_SIZE = 1.25F;
    public static final float TIP_SIZE = 0.4F;

    private final int links;
    private final double spacing;
    private final Vec3[][] joints;
    private final Vec3[] roots;
    private final List<List<Display.ItemDisplay>> displays = new ArrayList<>();
    private final ServerLevel level;
    /** Ground height under each tentacle's tip, refreshed every {@link #FLOOR_REFRESH_TICKS} ticks (a block scan per tick is waste). */
    private final double[] floorY;
    private int sinceFloorRefresh = 1000;
    private static final int FLOOR_REFRESH_TICKS = 4;
    private static final int FLOOR_SCAN_DEPTH = 10;

    /**
     * @param roots  where each tentacle attaches (world space), one entry per tentacle
     * @param dirs   the direction each tentacle initially lies in
     */
    public TentacleRig(ServerLevel level, Vec3[] roots, Vec3[] dirs, int links, double spacing) {
        this.links = links;
        this.level = level;
        this.floorY = new double[roots.length];
        for (int t = 0; t < roots.length; t++) {
            floorY[t] = roots[t].y - 1.3;
        }
        this.spacing = spacing;
        this.roots = roots.clone();
        this.joints = new Vec3[roots.length][links];
        for (int t = 0; t < roots.length; t++) {
            TentacleMath.lay(joints[t], roots[t], dirs[t], spacing);
            List<Display.ItemDisplay> chain = new ArrayList<>(links);
            for (int i = 0; i < links; i++) {
                Display.ItemDisplay d = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
                d.setItemStack(new ItemStack(itemFor(i, links)));
                d.addTag(TAG);
                d.addTag(TAG + "_" + t + "_" + i);   // tentacle and link index, so a tool never has to guess the order
                d.addTag("emberfall_run");   // run teardown sweeps any stray part
                d.setNoGravity(true);
                d.setInvulnerable(true);
                d.setViewRange(2.0F);
                d.setPosRotInterpolationDuration(GLIDE_TICKS);
                float s = TentacleMath.taper(i, links, BASE_SIZE, TIP_SIZE);
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(s, s, s), new Quaternionf()));
                Vec3 p = joints[t][i];
                d.setPos(p.x, p.y, p.z);
                level.addFreshEntity(d);
                chain.add(d);
            }
            displays.add(chain);
        }
    }

    /** Dark basalt for most of the arm, blackstone toward the middle, a glowing magma tip. */
    static net.minecraft.world.level.ItemLike itemFor(int i, int n) {
        if (i >= n - 2) {
            return Items.MAGMA_BLOCK;
        }
        return i < n / 2 ? Items.BASALT : Items.BLACKSTONE;
    }

    public int tentacleCount() {
        return joints.length;
    }

    public int entityCount() {
        int n = 0;
        for (List<Display.ItemDisplay> c : displays) {
            n += c.size();
        }
        return n;
    }

    /** Moves tentacle {@code t}'s root (the head moved) and reaches its tip toward {@code target}. */
    public void reach(int t, Vec3 root, Vec3 target) {
        roots[t] = root;
        if (sinceFloorRefresh >= FLOOR_REFRESH_TICKS) {
            // Scan from the higher of the root and the target, so a tip aimed at a hilltop sees the hill.
            floorY[t] = groundBelow(Math.max(root.y, target.y) + 1.0, target.x, target.z, floorY[t]);
        }
        TentacleMath.solve(joints[t], root, target, spacing, 2);
        TentacleMath.keepAboveFloor(joints[t], root, floorY[t], spacing, BASE_SIZE, TIP_SIZE);
        apply(t);
    }

    /** Call once per tick after all tentacles have been reached, to pace the floor scans. */
    public void endTick() {
        sinceFloorRefresh = sinceFloorRefresh >= FLOOR_REFRESH_TICKS ? 1 : sinceFloorRefresh + 1;
    }

    /** Walks down from {@code fromY} to the first solid block top; keeps {@code fallback} when nothing is found. */
    private double groundBelow(double fromY, double x, double z, double fallback) {
        net.minecraft.core.BlockPos.MutableBlockPos p =
                new net.minecraft.core.BlockPos.MutableBlockPos(net.minecraft.util.Mth.floor(x), net.minecraft.util.Mth.floor(fromY), net.minecraft.util.Mth.floor(z));
        for (int i = 0; i < FLOOR_SCAN_DEPTH; i++) {
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
                return p.getY() + 1.0;
            }
            p.move(0, -1, 0);
        }
        return fallback;
    }

    /** The floor height currently used under tentacle {@code t}'s tip (for tests). */
    public double floorUnder(int t) {
        return floorY[t];
    }

    /** World position of joint {@code i} of tentacle {@code t}, for hit tests (displays have no hitbox). */
    public Vec3 jointPos(int t, int i) {
        return joints[t][i];
    }

    public Vec3 tipPos(int t) {
        return joints[t][links - 1];
    }

    public double reachLength() {
        return spacing * (links - 1);
    }

    private void apply(int t) {
        List<Display.ItemDisplay> chain = displays.get(t);
        for (int i = 0; i < links; i++) {
            Display.ItemDisplay d = chain.get(i);
            if (!d.isAlive()) {
                continue;
            }
            Vec3 p = joints[t][i];
            d.setPos(p.x, p.y, p.z);
            // Each joint looks along the arm toward its tip side; the tip looks the way the one before it does.
            Vec3 dir = i < links - 1 ? joints[t][i + 1].subtract(p) : p.subtract(joints[t][i - 1]);
            if (dir.lengthSqr() > 1.0E-4) {
                d.setYRot((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
                d.setXRot((float) -Math.toDegrees(Math.atan2(dir.y, Math.hypot(dir.x, dir.z))));
            }
        }
    }

    /** Removes every display. Idempotent. */
    public void discard() {
        for (List<Display.ItemDisplay> chain : displays) {
            for (Display.ItemDisplay d : chain) {
                if (d.isAlive()) {
                    d.discard();
                }
            }
            chain.clear();
        }
    }
}
