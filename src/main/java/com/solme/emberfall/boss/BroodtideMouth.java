package com.solme.emberfall.boss;

/**
 * The Broodtide's particle mouth (owner, 2026-10-10): a shape of particles in front of the upper slime's face that periodically changes, an arrow
 * like shape, a circle, and others that suit a slime. Pure arithmetic, no Minecraft types: a shape is a list of points in the mouth's own plane,
 * x to the right and y up, in units of the mouth radius, and {@link #SHAPES} says how many there are. The entity side only turns points into particles.
 *
 * <p>Every shape has exactly {@link #POINTS} points, so the particle cost per redraw is a constant, never a surprise. The look is UNSEEN.</p>
 */
public final class BroodtideMouth {
    private BroodtideMouth() {}

    /** Particles per redraw, the same for every shape. */
    public static final int POINTS = 24;
    /** Ticks each shape is shown before the next one. */
    public static final int HOLD_TICKS = 50;
    /** Ticks between redraws of the current shape. */
    public static final int REDRAW_TICKS = 4;

    public static final int ARROW = 0, CIRCLE = 1, DROP = 2, GRIN = 3, SPIRAL = 4, CROSS = 5;
    public static final int SHAPES = 6;

    /** Which shape is shown at {@code fightTick}: they take turns in a fixed order, {@link #HOLD_TICKS} each. */
    public static int shapeAt(long fightTick) {
        long t = Math.max(0L, fightTick);
        return (int) ((t / HOLD_TICKS) % SHAPES);
    }

    /** True on the ticks the mouth redraws. */
    public static boolean redrawsAt(long fightTick) {
        return fightTick >= 0 && fightTick % REDRAW_TICKS == 0;
    }

    /**
     * The points of {@code shape}: {@link #POINTS} pairs {x, y}. {@code phase} (0 to 1) animates the shape slowly, so a held shape is alive rather than a
     * still picture: the circle turns, the spiral winds, the drop falls. Unknown shapes give the circle.
     */
    public static double[][] points(int shape, double phase) {
        double[][] p = new double[POINTS][2];
        switch (shape) {
            case ARROW -> {
                // a chevron pointing down at the player: two arms of 12 points rising from the tip at (0, -0.8), breathing in and out with the phase
                double breathe = 1.0 + 0.08 * Math.sin(phase * Math.PI * 2.0);
                for (int i = 0; i < POINTS; i++) {
                    boolean left = i < POINTS / 2;
                    double t = (left ? i : i - POINTS / 2) / (double) (POINTS / 2 - 1);   // 0 at the arm's far end, 1 at the tip
                    p[i][0] = (left ? -1 : 1) * (1.0 - t) * 0.9 * breathe;
                    p[i][1] = -0.8 + (1.0 - t) * 1.2 * breathe;
                }
            }
            case DROP -> {
                // a teardrop: a point on top, a round belly below, sliding down as the phase advances
                for (int i = 0; i < POINTS; i++) {
                    double a = i / (double) POINTS * Math.PI * 2.0;
                    double r = 0.55 * (1.0 - 0.45 * Math.max(0.0, Math.sin(a)));
                    p[i][0] = Math.cos(a) * r * (1.0 - 0.5 * Math.max(0.0, Math.sin(a)));
                    p[i][1] = Math.sin(a) * 0.9 * (Math.sin(a) > 0 ? 1.0 : 0.6) - 0.2 - 0.25 * Math.sin(phase * Math.PI * 2.0);
                }
            }
            case GRIN -> {
                // a wide curved smile with a tooth every fourth point
                for (int i = 0; i < POINTS; i++) {
                    double t = i / (double) (POINTS - 1) * 2.0 - 1.0;
                    boolean tooth = i % 4 == 2;
                    p[i][0] = t * 1.0;
                    p[i][1] = (t * t) * 0.7 - 0.45 - (tooth ? 0.22 : 0.0) + Math.sin((phase + t) * Math.PI) * 0.03;
                }
            }
            case SPIRAL -> {
                for (int i = 0; i < POINTS; i++) {
                    double t = i / (double) (POINTS - 1);
                    double a = t * Math.PI * 4.0 + phase * Math.PI * 2.0;
                    double r = 0.12 + t * 0.85;
                    p[i][0] = Math.cos(a) * r;
                    p[i][1] = Math.sin(a) * r;
                }
            }
            case CROSS -> {
                // four short arms: a plus that slowly turns
                for (int i = 0; i < POINTS; i++) {
                    int arm = i % 4;
                    double t = (i / 4 + 1) / (double) (POINTS / 4) * 0.9;
                    double a = arm * Math.PI / 2.0 + phase * Math.PI * 0.5;
                    p[i][0] = Math.cos(a) * t;
                    p[i][1] = Math.sin(a) * t;
                }
            }
            default -> {   // CIRCLE, the ring from the owner's reference, turning
                for (int i = 0; i < POINTS; i++) {
                    double a = i / (double) POINTS * Math.PI * 2.0 + phase * Math.PI * 2.0;
                    p[i][0] = Math.cos(a) * 0.9;
                    p[i][1] = Math.sin(a) * 0.9;
                }
            }
        }
        return p;
    }
}
