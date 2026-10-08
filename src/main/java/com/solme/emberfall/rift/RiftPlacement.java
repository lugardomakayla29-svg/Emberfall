package com.solme.emberfall.rift;

/**
 * Pure maths that turns a Rift's grid cell (x across, y up) into a world position. No Minecraft types, so a check can run it
 * without the game jar. One cell is one block (RiftShape's rule). The plane stands upright and faces a direction; the tear is
 * centred on its anchor so the anchor is the middle of the opening, not a corner of the box.
 *
 * Facing is one of four compass directions. The plane's "across" axis runs along the wall, "depth" is the way the Rift faces
 * (the direction a player steps through), and cells are centred in their block, hence the +0.5.
 */
public final class RiftPlacement {
    private RiftPlacement() {}

    /** 0 = faces +Z (across runs along X), 1 = faces -X (across runs along Z), 2 = faces -Z, 3 = faces +X. */
    public static final int FACINGS = 4;

    /** A world position as three doubles. */
    public record Pos(double x, double y, double z) {}

    /**
     * World position of a cell. {@code anchorX/Y/Z} is where the middle of the box sits (the cell at width/2, height/2).
     * {@code facing} is 0..3, {@code width/height} are the box size of the shape.
     */
    public static Pos cell(double anchorX, double anchorY, double anchorZ, int facing, int width, int height, int cx, int cy) {
        double across = cx + 0.5 - width / 2.0;
        double up = cy + 0.5 - height / 2.0;
        int f = ((facing % FACINGS) + FACINGS) % FACINGS;
        return switch (f) {
            case 0 -> new Pos(anchorX + across, anchorY + up, anchorZ);
            case 1 -> new Pos(anchorX, anchorY + up, anchorZ + across);
            case 2 -> new Pos(anchorX - across, anchorY + up, anchorZ);
            default -> new Pos(anchorX, anchorY + up, anchorZ - across);
        };
    }

    /** Unit vector the Rift faces (the way a push goes out of it): index 0 is x, 1 is z. */
    public static double[] normal(int facing) {
        int f = ((facing % FACINGS) + FACINGS) % FACINGS;
        return switch (f) {
            case 0 -> new double[] {0, 1};
            case 1 -> new double[] {-1, 0};
            case 2 -> new double[] {0, -1};
            default -> new double[] {1, 0};
        };
    }

    /** True when a point is within {@code range} blocks of the anchor, measured in 3D. Used to pick who hears and feels it. */
    public static boolean within(double anchorX, double anchorY, double anchorZ, double px, double py, double pz, double range) {
        double dx = px - anchorX;
        double dy = py - anchorY;
        double dz = pz - anchorZ;
        return dx * dx + dy * dy + dz * dz <= range * range;
    }
}
