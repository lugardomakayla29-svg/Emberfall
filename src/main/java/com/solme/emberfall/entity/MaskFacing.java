package com.solme.emberfall.entity;

/**
 * Which yaw a head ITEM display must show so its FACE is toward a viewer. Free of engine types so it can be proven on its own.
 *
 * <p>The rule comes from the 1.21.11 client bytecode (read, not assumed): {@code PlayerHeadSpecialRenderer.submit} passes {@code 180.0f}
 * as the yaw to {@code SkullBlockRenderer.submitSkull}, and {@code SkullModel.setupAnim} writes that value onto the head model part. A
 * player head carried by an item display is therefore drawn a half turn away from the display's own yaw. The display yaw that shows
 * the face to a viewer is the bearing to the viewer PLUS 180 degrees, and a display aimed straight AT the viewer shows the back of the skull.
 *
 * <p>Minecraft yaw convention: 0 faces +Z, and the bearing from a mob to a viewer at (dx, dz) is {@code atan2(-dx, dz)} in degrees.
 */
public final class MaskFacing {
    private MaskFacing() {}

    /** The half turn the client adds to a head item (the 180.0f in PlayerHeadSpecialRenderer.submit). */
    public static final float CLIENT_HEAD_TURN_DEG = 180.0F;

    /** The display yaw that shows the face of a head item to a viewer at the given bearing (degrees). */
    public static float yawToShowFace(float bearingDeg) {
        return wrap(bearingDeg + CLIENT_HEAD_TURN_DEG);
    }

    /** The display yaw for a mask that follows the body (nobody to look at): the face points the way the body does. */
    public static float yawFollowingBody(float bodyYawDeg) {
        return wrap(bodyYawDeg + CLIENT_HEAD_TURN_DEG);
    }

    /** Wraps to (-180, 180]. */
    public static float wrap(float deg) {
        float d = deg % 360.0F;
        if (d > 180.0F) d -= 360.0F;
        if (d <= -180.0F) d += 360.0F;
        return d;
    }
}
