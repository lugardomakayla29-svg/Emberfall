import com.solme.emberfall.wave.*;
public class WrathCheck {
    static int fails = 0;
    static void check(String l, boolean ok, String e) { System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e); if (!ok) fails++; }
    public static void main(String[] a) {
        for (var b : WrathCadence.Beat.values()) {
            boolean below = true; for (int t = 1; t < 50; t++) for (long tick = 20; tick <= 20000; tick += 20) if (WrathCadence.due(b, t, tick)) below = false;
            check(b + ": never fires below the cap (0.1x to 4.9x, 1000 seconds)", below, "");
        }
        // the director calls on multiples of 20: count fires in 60 seconds at the cap
        int[] n = new int[4]; for (long tick = 20; tick <= 1200; tick += 20) for (var b : WrathCadence.Beat.values()) if (WrathCadence.due(b, 50, tick)) n[b.ordinal()]++;
        check("in 60 s at 5.0x: 10 screams / 7 terrifies / 30 thrashes / 6 combusts  (every 6 s, 8 s, 2 s, 10 s)", n[0] == 10 && n[1] == 7 && n[2] == 30 && n[3] == 6, java.util.Arrays.toString(n));
        boolean onBeat = true; for (var b : WrathCadence.Beat.values()) { int every = switch (b) { case SCREAM -> WrathCadence.SCREAM_EVERY; case TERRIFY -> WrathCadence.TERRIFY_EVERY; case THRASH -> WrathCadence.THRASH_EVERY; case COMBUST -> WrathCadence.COMBUST_EVERY; }; if (every % 20 != 0) onBeat = false; }
        check("every beat interval is a multiple of 20 ticks, so a once-a-second tick can never skip one", onBeat, "");
        check("tick 0 never fires (nothing on the instant the cap is reached)", !WrathCadence.due(WrathCadence.Beat.THRASH, 50, 0), "");
        check("the fire lasts " + WrathCadence.FIRE_SECONDS + " s and combusts come every " + WrathCadence.COMBUST_EVERY / 20 + " s, so the player is never permanently alight", WrathCadence.FIRE_SECONDS < WrathCadence.COMBUST_EVERY / 20, "");
        System.out.println(fails == 0 ? "ALL PASS" : "FAILED " + fails);
    }
}
