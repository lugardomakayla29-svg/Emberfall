import java.util.Random;
public class Lanes {
    static final double CINDER_LANE_WIDTH = 3.0;
    static final double CINDER_ROW_DEPTH = 14.0;
    static boolean cinderStrikes(double along, double across, int row, int openLane, int lanes) {
        // Every row covers the SAME ground (the player's spot), only the open lane differs. Rows that advanced away from the
        // player were measured to be ignorable: a player who stood still was never inside rows 1 to 3.
        if (along < 0 || along >= CINDER_ROW_DEPTH) {
            return false;
        }
        double half = lanes * CINDER_LANE_WIDTH * 0.5;
        if (across < -half || across >= half) {
            return false;
        }
        int lane = (int) Math.floor((across + half) / CINDER_LANE_WIDTH);
        return lane != openLane;
    }
    static int[] pickOpenLanes(java.util.Random rng, int rows, int lanes) {
        int[] open = new int[rows];
        // The strip is centred on the player, so they start in the middle lane. The first open lane is within ONE lane of it, so the
        // first move is the same size as every later one (3 blocks). A free pick put it up to 3 lanes (9 blocks) away, which plain
        // walking could not cover in the 1.8 s wind-up, so the attack was unfair from its first row.
        int mid = lanes / 2;
        open[0] = mid - 1 + rng.nextInt(3);
        for (int r = 1; r < rows; r++) {
            int step = rng.nextBoolean() ? 1 : -1;
            int next = open[r - 1] + step;
            if (next < 0 || next >= lanes) {
                next = open[r - 1] - step;
            }
            open[r] = next;
        }
        return open;
    }
}
