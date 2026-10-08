package com.solme.emberfall.rift;

/**
 * The pure rules for stepping into an open Rift by right click. A Rift is particles only, and a right click on empty air with an empty hand
 * sends the server NOTHING (checked in the 1.21.11 client and server bytecode), so each open Rift carries ONE invisible vanilla Interaction
 * entity as its click target, as the old Expedition Gate hotspot did. This class decides where that target sits, how big it is, and which
 * targets are honoured or stale. No Minecraft types, so a check can run it without the game jar.
 *
 * The target covers the tall middle column of the silhouette (RiftShape's column), not the whole 15 by 13 box: the wings and satellites are
 * thin, and a box-sized target would accept clicks on empty air beside the tear.
 */
public final class RiftEntry {
    private RiftEntry() {}

    /** Tag every Rift click target carries, so a leftover one (a restart keeps entities, Rifts live in memory) can be found and removed. */
    public static final String TAG = "emberfall_rift_target";

    /** Width of the click target: under the narrowest column (RiftShape.COLUMN_MIN_W), so a click always lands on the tear and not beside it. */
    public static final float TARGET_WIDTH = RiftShape.COLUMN_MIN_W - 0.2F;
    /** Height of the click target: under the shortest column (RiftShape.COLUMN_MIN_H). */
    public static final float TARGET_HEIGHT = RiftShape.COLUMN_MIN_H - 0.4F;

    /** Y of the bottom of the click target for a Rift anchored (middle of its box) at {@code anchorY}: the bottom edge of the box. */
    public static double targetBaseY(double anchorY) {
        return anchorY - RiftShape.BOX_H / 2.0;
    }

    /** How near a live Rift's anchor a target must be to belong to it. The target stands inside the box, so the box height is generous. */
    public static final double MATCH_RADIUS = RiftShape.BOX_H;

    /** Whether a target at distance {@code d} from a Rift's anchor belongs to that Rift. */
    public static boolean belongsTo(double d) {
        return d >= 0.0 && d <= MATCH_RADIUS;
    }

    /**
     * Whether a click on a target is honoured: a live Rift stands at it, it is open (the 5 second opening has played) and it is not closing.
     * A target with no live Rift behind it (left over from before a restart) is never honoured, and is removed by the caller.
     */
    public static boolean honoured(boolean riftExists, boolean riftOpen) {
        return riftExists && riftOpen;
    }

    /** Why a click on an existing target is refused, or null when it is honoured. The Rift is "still opening" before its 5 seconds are up. */
    public static String refusal(boolean riftExists, boolean riftOpen) {
        if (!riftExists) {
            return "the Rift has faded";
        }
        if (!riftOpen) {
            return "the Rift is still opening";
        }
        return null;
    }

    /**
     * Distance from a player to the click target, measured to the NEAREST point of the target column and not to its middle. The target is
     * {@link #TARGET_HEIGHT} tall, so the middle can be over 4 blocks above a player who is standing right at its foot; measuring to the middle
     * would tell that player they are too far away. Horizontal distance is taken to the column's centre line (the column is thin), vertical
     * distance is zero anywhere between the base and the top, and the gap above or below otherwise.
     *
     * @param dx horizontal x offset, player minus target
     * @param dz horizontal z offset, player minus target
     * @param playerY the player's feet y
     * @param baseY the y of the bottom of the target ({@link #targetBaseY})
     */
    public static double reachDistance(double dx, double dz, double playerY, double baseY) {
        double top = baseY + TARGET_HEIGHT;
        double dy = playerY < baseY ? baseY - playerY : (playerY > top ? playerY - top : 0.0);
        return Math.sqrt(dx * dx + dz * dz + dy * dy);
    }
}
