package com.solme.emberfall.entity;

import com.mojang.math.Transformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * A worm made of item displays ONLY: one head and N body segments, no armor stands and no passengers.
 *
 * <p>Why this replaces the old rig: the old body was an {@code ArmorStand} carrying a rider display, and
 * the surface burst called {@code setInvisible(false)} on those stands, so after the first surfacing
 * players saw vanilla armor stands and no worm. It also trailed the head by a fixed number of TICKS, which
 * bunches the body up when the head slows and stretches it when the head speeds up. Here the body trails by
 * a follow-the-leader chain: every segment keeps at most {@code spacing} blocks from the part in front of
 * it, so spacing stays even at any speed, a resting worm holds perfectly still, and a segment can never be
 * flung away from its leader (walking a recorded trail did all three wrong).
 *
 * <p>Cost: {@code 1 + N} entities total (the old rig was {@code 1 + 2N + 1}), one small loop per tick.
 */
public final class WormBody {
    /** Marks a display as belonging to a worm so tools and cleanup can tell it from hub decor and minions. */
    public static final String WORM_TAG = "emberfall_worm";
    private static final int GLIDE_TICKS = 2;
    /** How fast a hanging body segment settles toward the floor, in blocks per tick. */
    private static final double SAG_PER_TICK = 0.45;

    private final List<Display.ItemDisplay> displays = new ArrayList<>();
    private final float[] sizes;
    private final double spacing;
    private final Vec3[] pos;
    private Vec3 headDirection = new Vec3(0.0, 0.0, 1.0);
    private boolean visible = true;
    private boolean threat = false;

    /**
     * @param sizes   display scale for the head (index 0) then each body segment, biggest first
     * @param spacing distance between neighbouring parts, in blocks
     */
    public WormBody(ServerLevel level, Vec3 origin, Vec3 facing, ItemStack head, ItemStack body, float[] sizes, double spacing) {
        this.sizes = sizes.clone();
        this.spacing = spacing;
        this.pos = new Vec3[sizes.length];
        for (int i = 0; i < sizes.length; i++) {
            Display.ItemDisplay d = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
            d.setItemStack(i == 0 ? head : body);
            d.addTag(WORM_TAG);
            d.addTag(WORM_TAG + "_" + i);   // chain index, so tools never have to guess the order
            d.setNoGravity(true);
            d.setInvulnerable(true);
            d.setViewRange(2.0F);
            d.setPosRotInterpolationDuration(GLIDE_TICKS);
            float s = sizes[i];
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(s, s, s), new Quaternionf()));
            d.setPos(origin.x, origin.y, origin.z);
            level.addFreshEntity(d);
            displays.add(d);
        }
        // Start stretched out in a straight line BEHIND the head, so a worm at rest is already a worm.
        Vec3 back = facing.lengthSqr() > 1.0E-6 ? facing.normalize().scale(-1.0) : new Vec3(0.0, 0.0, -1.0);
        this.headDirection = back.scale(-1.0);
        for (int i = 0; i < pos.length; i++) {
            pos[i] = origin.add(back.scale(i * spacing));
        }
    }

    /** Number of displays owned (head plus body). */
    public int size() {
        return displays.size();
    }

    /**
     * Places every part with a follow-the-leader chain. {@code headCentre} is where the head cube's centre
     * goes. Each body segment is only ever pulled TOWARD the part in front of it, and only when it is farther
     * than {@code spacing}. That makes a resting worm perfectly still (nothing is dragged, so nothing moves)
     * and it cannot fling a segment away, unlike walking a recorded trail where every tiny head wobble
     * shifted every segment and a leap's arc lingered in the path for seconds.
     */
    public void update(Vec3 headCentre, double floorY) {
        pos[0] = headCentre;
        // 1. Gravity: ease every hanging body part toward the floor (a body hauled up by a leap would otherwise
        //    hang in the sky forever, since a part already near its neighbour is never pulled).
        for (int i = 1; i < pos.length; i++) {
            double rest = floorY + sizes[i] * 0.5;
            if (pos[i].y > rest) {
                pos[i] = new Vec3(pos[i].x, Math.max(rest, pos[i].y - SAG_PER_TICK), pos[i].z);
            }
        }
        // 2. A TWO-SIDED distance constraint: every part sits at exactly `spacing` from the one in front, so the
        //    chain can neither stretch (a fast head outruns it) nor fold up (a part tucked into its neighbour).
        //    Two passes per tick let a head that moved a long way drag the whole chain in ONE tick.
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 1; i < pos.length; i++) {
                Vec3 lead = pos[i - 1];
                Vec3 toMe = pos[i].subtract(lead);
                double d = toMe.length();
                Vec3 dir = d > 1.0E-6 ? toMe.scale(1.0 / d) : new Vec3(0.0, 0.0, -1.0);
                pos[i] = lead.add(dir.scale(spacing));
            }
        }
        // 3. Floor last, so nothing the constraint did can leave a body part underground.
        for (int i = 1; i < pos.length; i++) {
            double rest = floorY + sizes[i] * 0.5;
            if (pos[i].y < rest) {
                pos[i] = new Vec3(pos[i].x, rest, pos[i].z);
            }
        }
        for (int i = 0; i < displays.size(); i++) {
            Display.ItemDisplay d = displays.get(i);
            Vec3 p = pos[i];
            d.setPos(p.x, p.y, p.z);
            Vec3 dir = i == 0 ? headDirection : pos[i - 1].subtract(p);
            if (dir.lengthSqr() > 1.0E-4) {
                d.setYRot((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
                d.setXRot((float) -Math.toDegrees(Math.atan2(dir.y, Math.hypot(dir.x, dir.z))));
            }
        }
    }

    /**
     * Lays the whole worm on a circle: part i sits at angle {@code headAngle - i * spacing / radius}, so neighbours are one
     * {@code spacing} apart along the arc (chord within 0.5% of it) and the body reads as one continuous ring with a gap
     * behind the tail. The follow-the-leader chain cannot do this: driven round a circle it cuts the inside of the turn and
     * the tail ends up crossing the centre (measured in bot/coil_sim.py), so the ring is placed directly.
     *
     * <p>Rings are counter-clockwise seen from above (increasing angle). Every part rests on {@code floorY} like the chain does.
     * The gap left behind the tail is {@code 2*pi*radius - (parts) * spacing} blocks of arc.
     */
    public void placeOnRing(Vec3 centre, double radius, double headAngle, double floorY, double headY) {
        for (int i = 0; i < pos.length; i++) {
            double a = headAngle - i * spacing / radius;
            double y = i == 0 ? headY : floorY + sizes[i] * 0.5;
            pos[i] = new Vec3(centre.x + Math.cos(a) * radius, y, centre.z + Math.sin(a) * radius);
        }
        // Head faces along the direction of travel (the tangent, counter-clockwise).
        headDirection = new Vec3(-Math.sin(headAngle), 0.0, Math.cos(headAngle));
        for (int i = 0; i < displays.size(); i++) {
            Display.ItemDisplay d = displays.get(i);
            Vec3 p = pos[i];
            d.setPos(p.x, p.y, p.z);
            Vec3 dir = i == 0 ? headDirection : pos[i - 1].subtract(p);
            if (dir.lengthSqr() > 1.0E-4) {
                d.setYRot((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
                d.setXRot((float) -Math.toDegrees(Math.atan2(dir.y, Math.hypot(dir.x, dir.z))));
            }
        }
    }

    /** World position of part {@code i} as last placed (used to hit-test the ring, since displays have no hitbox). */
    public Vec3 partPos(int i) {
        return pos[i];
    }

    /** The direction the head faces, used to orient part 0 (it has no leader to look at). */
    public void setHeadDirection(Vec3 dir) {
        this.headDirection = dir;
    }

    /** Hides or shows the whole worm by moving nothing: only the displays' own view range changes. */
    public void setVisible(boolean visible) {
        if (this.visible == visible) {
            return;
        }
        this.visible = visible;
        for (Display.ItemDisplay d : displays) {
            d.setViewRange(visible ? 2.0F : 0.0F);
        }
    }

    /**
     * Red outline plus full-bright on the head (index 0) while it is winding up, so the tell reads from far
     * away and in a dark arena. Both are single synced data fields on an entity that already exists, so this
     * costs one small packet per change and no extra entity. Pass false to restore normal lighting.
     */
    public void setThreat(boolean threatening) {
        if (displays.isEmpty() || this.threat == threatening) {
            return;
        }
        this.threat = threatening;
        Display.ItemDisplay head = displays.get(0);
        head.setGlowingTag(threatening);
        head.setGlowColorOverride(threatening ? 0xFF2A1A : -1);
        head.setBrightnessOverride(threatening ? new net.minecraft.util.Brightness(15, 15) : null);
    }

    public void discard() {
        for (Display.ItemDisplay d : displays) {
            if (d.isAlive()) {
                d.discard();
            }
        }
        displays.clear();
    }
}
