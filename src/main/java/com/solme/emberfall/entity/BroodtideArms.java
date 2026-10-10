package com.solme.emberfall.entity;

import com.mojang.math.Transformation;
import com.solme.emberfall.boss.BroodtideArmPlan;
import com.solme.emberfall.boss.BroodtideGrab;
import com.solme.emberfall.boss.TideClock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Broodtide's arms: up to {@link BroodtideArmPlan#MAX_ARMS} tentacles of {@link BroodtideArmPlan#LINKS} item displays each, allocated ONCE when the fight
 * starts (40 entities at most) and reused for every phase. An arm that is not in use in the current phase is parked inside the body and stays invisible by
 * scale 0, so a phase change spawns and removes nothing. All decisions (counts, angles, extension, targets, look) come from the pure {@link BroodtideArmPlan};
 * the geometry is the proven {@link TentacleMath}. This class only reads the world and moves displays. Display entities only: no armour stands, no passengers.
 *
 * <p>UNSEEN: nothing here has been rendered on a real client. The look is built from the Kuudra references (thick root, spiked tip, curling) and is a proposal.</p>
 */
public final class BroodtideArms {
    public static final String TAG = "emberfall_broodtide_arm";
    private static final int GLIDE_TICKS = 2;
    private static final int FLOOR_REFRESH_TICKS = 4;
    private static final int FLOOR_SCAN_DEPTH = 10;
    /** Radians per tick the whole ring turns while the arms are out: a slow, restless sweep. */
    private static final double SPIN_PER_TICK = 0.012;

    private final ServerLevel level;
    private final Vec3[][] joints = new Vec3[BroodtideArmPlan.MAX_ARMS][BroodtideArmPlan.LINKS];
    private final List<List<Display.ItemDisplay>> displays = new ArrayList<>();
    private final double[] floorY = new double[BroodtideArmPlan.MAX_ARMS];
    private double spin = 0.0;
    private int sinceFloor = 1000;
    private int activeArms = 0;
    /** The arm doing the Grab and the player it reaches for; -1 when none. */
    private int grabArm = -1;
    private UUID grabTarget;
    /** The arm doing the Devour's reach and the mob (any entity) it reaches for; -1 when none. Independent of the Grab's slot so the two use different limbs. */
    private int reachArm = -1;
    private UUID reachTarget;
    private boolean discarded = false;

    public BroodtideArms(ServerLevel level, Vec3 bodyPos) {
        this.level = level;
        for (int a = 0; a < BroodtideArmPlan.MAX_ARMS; a++) {
            floorY[a] = bodyPos.y;
            Vec3 root = bodyPos.add(BroodtideArmPlan.rootDx(a, BroodtideArmPlan.MAX_ARMS, 0.0), BroodtideArmPlan.ROOT_HEIGHT, BroodtideArmPlan.rootDz(a, BroodtideArmPlan.MAX_ARMS, 0.0));
            TentacleMath.lay(joints[a], root, new Vec3(0.0, 1.0, 0.0), BroodtideArmPlan.BONES);
            List<Display.ItemDisplay> chain = new ArrayList<>(BroodtideArmPlan.LINKS);
            for (int i = 0; i < BroodtideArmPlan.LINKS; i++) {
                Display.ItemDisplay d = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
                d.setItemStack(new ItemStack(itemOf(BroodtideArmPlan.itemFor(i, BroodtideArmPlan.LINKS))));
                d.addTag(TAG);
                d.addTag(TAG + "_" + a + "_" + i);
                d.addTag("emberfall_run");   // run teardown sweeps any stray part
                d.setNoGravity(true);
                d.setInvulnerable(true);
                d.setViewRange(2.5F);
                d.setPosRotInterpolationDuration(GLIDE_TICKS);
                setScale(d, 0.0F);   // parked and invisible until the phase brings this arm in
                Vec3 p = joints[a][i];
                d.setPos(p.x, p.y, p.z);
                level.addFreshEntity(d);
                chain.add(d);
            }
            displays.add(chain);
        }
    }

    private static Item itemOf(String id) {
        return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("emberfall", id));
    }

    private static void setScale(Display.ItemDisplay d, float s) {
        d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(s, s, s), new Quaternionf()));
    }

    /** Total display entities this rig owns. Constant for the whole fight: a phase change never adds or removes any. */
    public int entityCount() {
        int n = 0;
        for (List<Display.ItemDisplay> c : displays) {
            n += c.size();
        }
        return n;
    }

    public int activeArms() {
        return activeArms;
    }

    /** The Grab says which player it is reaching for, so the nearest arm lunges at them. Cleared with {@link #clearGrab()}. */
    public void startGrab(UUID player) {
        this.grabTarget = player;
    }

    public void clearGrab() {
        this.grabTarget = null;
        this.grabArm = -1;
    }

    /** The Devour says which mob it is reaching for; a free arm lunges at it. Cleared with {@link #clearReach()}. */
    public void startReach(UUID entity) {
        this.reachTarget = entity;
    }

    public void clearReach() {
        this.reachTarget = null;
        this.reachArm = -1;
    }

    public int reachingArm() {
        return reachArm;
    }

    /**
     * Once a tick. {@code ticksIntoState} is how long the Tide has been in its current state (from {@link TideClock}); the arms open in Ebb and curl in Flood.
     * Everything is skipped when the rig has been discarded.
     */
    public void tick(Vec3 body, BroodtideGrab.Phase phase, TideClock.State state, long ticksIntoState) {
        if (discarded) {
            return;
        }
        int n = BroodtideArmPlan.armCount(phase);
        activeArms = n;
        double ext = BroodtideArmPlan.extension(state, ticksIntoState);
        if (ext > 0.0) {
            spin += SPIN_PER_TICK;
        }
        Player nearest = nearestPlayer(body);
        Player grabbed = grabTarget == null ? null : level.getPlayerByUUID(grabTarget);
        if (grabbed == null || !grabbed.isAlive()) {
            grabbed = null;
        }
        if (grabbed != null) {
            double bearing = Math.atan2(grabbed.getZ() - body.z, grabbed.getX() - body.x);
            grabArm = BroodtideArmPlan.grabbingArm(n, spin, bearing);
        } else {
            grabArm = -1;
        }
        Entity reached = reachTarget == null ? null : level.getEntity(reachTarget);
        if (reached == null || !reached.isAlive()) {
            reached = null;
        }
        if (reached != null) {
            double bearing = Math.atan2(reached.getZ() - body.z, reached.getX() - body.x);
            reachArm = BroodtideArmPlan.reachingArm(n, spin, bearing, grabArm);
        } else {
            reachArm = -1;
        }
        boolean refreshFloor = sinceFloor >= FLOOR_REFRESH_TICKS;
        for (int a = 0; a < BroodtideArmPlan.MAX_ARMS; a++) {
            if (a >= n) {
                park(a, body);
                continue;
            }
            Vec3 root = body.add(BroodtideArmPlan.rootDx(a, n, spin), BroodtideArmPlan.ROOT_HEIGHT, BroodtideArmPlan.rootDz(a, n, spin));
            double[] curl = BroodtideArmPlan.curlTarget(a, n, spin);
            Entity aim = a == grabArm ? grabbed : a == reachArm ? reached : nearest;
            double[] hunt = aim == null
                    ? BroodtideArmPlan.huntTarget(a, n, spin, false, 0, 0, 0)
                    : BroodtideArmPlan.huntTarget(a, n, spin, true, aim.getX() - body.x, aim.getY() + 0.6 - body.y, aim.getZ() - body.z);
            // The grabbing arm and the reaching arm are always fully out: they lunge even if the Tide has begun to close the others.
            double useExt = a == grabArm || a == reachArm ? 1.0 : ext;
            double[] t = BroodtideArmPlan.blend(curl, hunt, useExt);
            Vec3 target = new Vec3(body.x + t[0], body.y + t[1], body.z + t[2]);
            if (refreshFloor) {
                floorY[a] = groundBelow(Math.max(root.y, target.y) + 1.0, target.x, target.z, floorY[a]);
            }
            TentacleMath.solve(joints[a], root, target, BroodtideArmPlan.BONES, 2);
            TentacleMath.limitBend(joints[a], root, BroodtideArmPlan.BONES, BroodtideArmPlan.MAX_BEND_DEG);
            TentacleMath.keepAboveFloor(joints[a], root, floorY[a], BroodtideArmPlan.BONES, BroodtideArmPlan.SIZES);
            apply(a);
        }
        sinceFloor = refreshFloor ? 1 : sinceFloor + 1;
    }

    /** An arm not used in this phase sits at the body, scale 0, so it costs the client nothing to draw. */
    private void park(int a, Vec3 body) {
        List<Display.ItemDisplay> chain = displays.get(a);
        for (int i = 0; i < chain.size(); i++) {
            Display.ItemDisplay d = chain.get(i);
            if (!d.isAlive()) {
                continue;
            }
            setScale(d, 0.0F);
            d.setPos(body.x, body.y + BroodtideArmPlan.ROOT_HEIGHT, body.z);
        }
    }

    private void apply(int a) {
        List<Display.ItemDisplay> chain = displays.get(a);
        int links = BroodtideArmPlan.LINKS;
        for (int i = 0; i < links; i++) {
            Display.ItemDisplay d = chain.get(i);
            if (!d.isAlive()) {
                continue;
            }
            Vec3 p = joints[a][i];
            d.setPos(p.x, p.y, p.z);
            setScale(d, BroodtideArmPlan.scaleFor(i, links));
            Vec3 dir = i < links - 1 ? joints[a][i + 1].subtract(p) : p.subtract(joints[a][i - 1]);
            if (dir.lengthSqr() > 1.0E-4) {
                d.setYRot((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
                d.setXRot((float) -Math.toDegrees(Math.atan2(dir.y, Math.hypot(dir.x, dir.z))));
            }
        }
    }

    private Player nearestPlayer(Vec3 body) {
        Player best = null;
        double bestD = Double.MAX_VALUE;
        for (Player p : new ArrayList<>(level.players())) {   // a snapshot: the list can change while we read it
            if (!p.isAlive() || p.isSpectator()) {
                continue;
            }
            double d = p.distanceToSqr(body.x, body.y, body.z);
            if (d < bestD && d <= BroodtideArmPlan.HUNT_REACH * BroodtideArmPlan.HUNT_REACH * 4.0) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    private double groundBelow(double fromY, double x, double z, double fallback) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(net.minecraft.util.Mth.floor(x), net.minecraft.util.Mth.floor(fromY), net.minecraft.util.Mth.floor(z));
        for (int i = 0; i < FLOOR_SCAN_DEPTH; i++) {
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
                return p.getY() + 1.0;
            }
            p.move(0, -1, 0);
        }
        return fallback;
    }

    /** World position of the tip of arm {@code a} (for tests). */
    public Vec3 tipPos(int a) {
        return joints[a][BroodtideArmPlan.LINKS - 1];
    }

    public int grabbingArm() {
        return grabArm;
    }

    /** Removes every display. Idempotent: safe to call from every exit path (death, discard, unload, run teardown). */
    public void discard() {
        discarded = true;
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
