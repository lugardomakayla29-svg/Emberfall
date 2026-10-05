package com.solme.emberfall.hub;

import java.util.ArrayList;
import java.util.List;

/**
 * Where everything in the hub goes, as pure offsets from the Hearth. No world access, so the layout
 * can be unit-checked for overlaps and footprint fit without a server.
 *
 * The hub fits inside the 9x9 footprint ({@link HubSiteAnalyzer#RADIUS} = 4, so offsets run -4..+4):
 *
 *  - The Hearth sits at the centre (0, 0).
 *  - The 8 character busts stand on a ring of radius 3, evenly spaced, each facing the Hearth.
 *  - The shop keeper stands directly beside the Hearth on the +Z side, at (0, 1).
 *  - The departure plate is the single cell on the opposite side, at (0, -1), so leaving is deliberate.
 *
 * Only the four cells orthogonally adjacent to the Hearth keep a full 2 blocks from every bust (the
 * cells nearer the ring are within 1.41), so the keeper and the plate take two of those four.
 *
 * Positions are rounded onto the block grid. The ring radius of 3 keeps every bust at least 2 blocks
 * from every neighbour, which is what makes a hologram readable above each one.
 */
public final class HubLayout {
    /** Radius of the character bust ring, in blocks from the Hearth. */
    public static final int BUST_RING_RADIUS = 3;
    /** Number of character busts; one per weapon. */
    public static final int BUST_COUNT = 8;

    private HubLayout() {}

    /** An integer offset from the Hearth, plus the yaw (degrees) an entity there should face. */
    public record Spot(int dx, int dz, float yaw) {}

    /**
     * The bust positions, index 0 first, going clockwise from north (-Z). Each faces the Hearth.
     * Yaw follows Minecraft's convention: 0 = south (+Z), 90 = west (-X), 180 = north, 270 = east.
     */
    public static List<Spot> bustSpots() {
        List<Spot> spots = new ArrayList<>(BUST_COUNT);
        for (int i = 0; i < BUST_COUNT; i++) {
            double angle = (Math.PI * 2.0 * i) / BUST_COUNT;
            // Angle 0 points north (-Z); increasing angle sweeps clockwise seen from above (toward +X).
            int dx = (int) Math.round(Math.sin(angle) * BUST_RING_RADIUS);
            int dz = (int) Math.round(-Math.cos(angle) * BUST_RING_RADIUS);
            spots.add(new Spot(dx, dz, yawFacingCentre(dx, dz)));
        }
        return spots;
    }

    /** Where the shop keeper stands: beside the Hearth on the +Z side, facing it. */
    public static Spot shopKeeperSpot() {
        return new Spot(0, 1, yawFacingCentre(0, 1));
    }

    /** The single departure plate cell, on the -Z side of the Hearth. Standing on it starts an expedition. */
    public static Spot departureSpot() {
        return new Spot(0, -1, 0.0F);
    }

    /** The yaw that makes something at (dx, dz) look at the Hearth at (0, 0). */
    static float yawFacingCentre(int dx, int dz) {
        // Direction from the entity to the centre is (-dx, -dz). Minecraft yaw: atan2(-dx, dz) of that direction.
        double toX = -dx;
        double toZ = -dz;
        double yaw = Math.toDegrees(Math.atan2(-toX, toZ));
        return (float) ((yaw % 360.0 + 360.0) % 360.0);
    }
}
