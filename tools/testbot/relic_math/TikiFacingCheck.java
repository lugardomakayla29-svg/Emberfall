import com.solme.emberfall.entity.MaskFacing;

/**
 * Pure check of the Tiki mask facing rule. The rule is read from the 1.21.11 client bytecode, not assumed:
 * PlayerHeadSpecialRenderer.submit passes 180.0f as the yaw to SkullBlockRenderer.submitSkull, and SkullModel.setupAnim writes it onto the
 * head ModelPart, so a head ITEM shows its FACE to a viewer only when the display yaw equals (bearing to the viewer + 180).
 * The old live test (tiki_facing_test) asserted "yaw within 25 deg of the bearing", which is the opposite, so it passed while masks looked away.
 */
public class TikiFacingCheck {
    static int fails = 0;
    static void check(String n, boolean ok, String note) { System.out.println((ok ? "PASS " : "FAIL ") + n + " " + note); if (!ok) fails++; }
    static double wrap(double d) { d = ((d % 360) + 540) % 360 - 180; return d; }

    public static void main(String[] a) {
        // The yaw a mask display must show to face a viewer standing at (dx, dz) from the mob. Minecraft yaw 0 faces +Z: bearing = atan2(-dx, dz).
        int bad = 0; double worst = 0;
        double[][] spots = { {8,0},{0,8},{-8,0},{0,-8},{6,6},{-6,6},{-6,-6},{6,-6},{1,0.2},{-30,5} };
        for (double[] s : spots) {
            double bearing = Math.toDegrees(Math.atan2(-s[0], s[1]));
            float shown = MaskFacing.yawToShowFace((float) bearing);                       // what the mob writes to the display
            double faceError = Math.abs(wrap(shown - (bearing + 180.0)));     // 0 = the face is toward the viewer
            if (faceError > 1e-3) { bad++; worst = Math.max(worst, faceError); }
        }
        check("the yaw written for a viewer makes the mask FACE them (display yaw = bearing + 180), all 10 spots", bad == 0, bad + " wrong, worst " + worst + " deg");
        // CONTROL: the rule distinguishes front from back, so a check that only compared with the bearing could not have caught the old bug.
        double bearing = 37.0;
        check("CONTROL: yaw == bearing shows the BACK (180 deg off the face)", Math.abs(wrap(bearing - (bearing + 180.0))) > 179.9, "");
        check("the idle (nobody in range) yaw follows the body but is also turned to show the face forward", Math.abs(wrap(MaskFacing.yawFollowingBody(0.0F) - 180.0)) < 1e-3, "idle(0)=" + MaskFacing.yawFollowingBody(0.0F));
        System.out.println(fails == 0 ? "ALL PASS" : "FAILED " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }
}
