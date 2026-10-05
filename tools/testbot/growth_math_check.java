import java.util.random.RandomGenerator;
public class Check {
  static int fails = 0;
  static void t(String n, boolean ok, String x) { System.out.println((ok ? "PASS " : "FAIL ") + n + " " + x); if (!ok) fails++; }
  public static void main(String[] a) {
    // levels
    StringBuilder sb = new StringBuilder(); for (int l = 1; l <= 10; l++) sb.append(WeaponGrowth.killsForLevel(l)).append(' ');
    t("G1 kills table", sb.toString().trim().equals("0 4 10 18 28 40 54 70 88 108"), sb.toString());
    boolean mono = true, inv = true;
    for (int l = 2; l <= 10; l++) if (WeaponGrowth.killsForLevel(l) <= WeaponGrowth.killsForLevel(l - 1)) mono = false;
    for (int l = 1; l <= 10; l++) { int k = WeaponGrowth.killsForLevel(l); if (WeaponGrowth.levelForKills(k) != l || (l > 1 && WeaponGrowth.levelForKills(k - 1) != l - 1)) inv = false; }
    t("G2 strictly increasing", mono, "");
    t("G3 levelForKills inverts killsForLevel exactly at every boundary", inv, "");
    t("G4 caps at MAX_LEVEL", WeaponGrowth.levelForKills(1_000_000) == 10 && WeaponGrowth.levelForKills(-5) == 1, "");
    // roll: exact for whole numbers, right mean for fractions
    RandomGenerator r = new java.util.SplittableRandom(12345);
    boolean whole = true; for (int i = 0; i < 1000; i++) if (WeaponGrowth.roll(3.0, r) != 3) whole = false;
    t("G5 roll(3.0) is always 3", whole, "");
    t("G6 roll of 0, negative, NaN is 0", WeaponGrowth.roll(0, r) == 0 && WeaponGrowth.roll(-2, r) == 0 && WeaponGrowth.roll(Double.NaN, r) == 0, "");
    for (double avg : new double[]{0.25, 0.5, 1.5, 2.75, 4.1}) {
      long sum = 0; int n = 400_000, min = 99, max = 0;
      for (int i = 0; i < n; i++) { int v = WeaponGrowth.roll(avg, r); sum += v; min = Math.min(min, v); max = Math.max(max, v); }
      double mean = (double) sum / n; double tol = 0.01;
      t("G7 roll(" + avg + ") mean", Math.abs(mean - avg) < tol && min >= Math.floor(avg) && max <= Math.floor(avg) + 1, String.format("mean %.4f range %d..%d", mean, min, max));
    }
    // scale
    t("G8 scale", WeaponGrowth.scale(1.0, 0.25, 1) == 1.0 && WeaponGrowth.scale(1.0, 0.25, 5) == 2.0 && WeaponGrowth.scale(1.0, 0.25, 99) == 1.0 + 0.25 * 9, "");
    // meter
    int m = WeaponGrowth.fill(950, 100);
    t("G9 meter fills past max", m == 1050 && WeaponGrowth.ready(m) && !WeaponGrowth.ready(999), "m=" + m);
    t("G10 overflow kept after firing", WeaponGrowth.afterFire(m) == 50 && WeaponGrowth.afterFire(1000) == 0, "");
    t("G11 huge gain cannot bank two ultimates", WeaponGrowth.afterFire(WeaponGrowth.fill(0, Integer.MAX_VALUE)) < WeaponGrowth.METER_MAX, "after=" + WeaponGrowth.afterFire(WeaponGrowth.fill(0, Integer.MAX_VALUE)));
    t("G12 negative input is harmless", WeaponGrowth.fill(500, -300) == 500 && WeaponGrowth.fill(-9, 10) == 10, "");
    System.out.println(fails == 0 ? "ALL PASS" : "SOME FAIL " + fails);
  }
}
