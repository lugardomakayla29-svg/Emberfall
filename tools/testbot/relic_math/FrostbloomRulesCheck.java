import com.solme.emberfall.frost.FrostbloomRules;
import com.solme.emberfall.frost.FrostbloomRules.Tier;
public class FrostbloomRulesCheck {
    static int fails = 0;
    static void check(String l, boolean ok, String e) { System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e); if (!ok) fails++; }
    public static void main(String[] a) {
        // Budget: the replacement must not shift the wave tables. Fodder and elite match the Tiki exactly (14/3 and 120/7).
        check("budget: Bud is 14 hp / 3 dmg and Bloom is 120 hp / 7 dmg, exactly the Tiki's", FrostbloomRules.maxHealth(Tier.BUD) == 14.0 && FrostbloomRules.attackDamage(Tier.BUD) == 3.0 && FrostbloomRules.maxHealth(Tier.BLOOM) == 120.0 && FrostbloomRules.attackDamage(Tier.BLOOM) == 7.0, "");
        check("tiers climb: health, damage and crystals strictly increase Bud < Bloom < Rimeheart", asc(Tier.BUD, Tier.BLOOM) && asc(Tier.BLOOM, Tier.RIMEHEART), "");
        check("crystals are 3, 5, 7", FrostbloomRules.crystals(Tier.BUD) == 3 && FrostbloomRules.crystals(Tier.BLOOM) == 5 && FrostbloomRules.crystals(Tier.RIMEHEART) == 7, "");
        // The fairness core: warning IS the damage moment, and a runner can leave the ring in time.
        check("the burst lands exactly when the warning ends (one constant, not two)", FrostbloomRules.burstTick() == FrostbloomRules.SURFACE_WARNING_TICKS, "");
        check("fair: a sprinting player starting on the spot gets out of the ring before the burst", FrostbloomRules.escapable(), "reach " + FrostbloomRules.reachableDistance() + " vs radius " + FrostbloomRules.BURST_RADIUS);
        check("fair: the escape has a real margin, at least 1 block to spare (not a photo finish)", FrostbloomRules.reachableDistance() - FrostbloomRules.BURST_RADIUS >= 1.0, "margin " + (FrostbloomRules.reachableDistance() - FrostbloomRules.BURST_RADIUS));
        // Burst geometry: both sides of the edge.
        check("burst: hits at the radius, misses just past it", FrostbloomRules.burstHits(2.5) && !FrostbloomRules.burstHits(2.51), "");
        check("burst: full damage at the centre, half at the edge, none outside", FrostbloomRules.burstDamage(Tier.BLOOM, 0) == 7.0 && Math.abs(FrostbloomRules.burstDamage(Tier.BLOOM, 2.5) - 3.5) < 1e-9 && FrostbloomRules.burstDamage(Tier.BLOOM, 2.6) == 0.0, "");
        check("burst: damage never negative and never above the tier's attack, for any distance", boundedDamage(), "");
        check("burst: damage falls as you move away (never rises with distance)", monotoneDamage(), "");
        // Rhythm: hittable but not a sponge, and not a mob that is always up.
        check("rhythm: every tier is hittable between 15% and 45% of its cycle", rhythm(), shares());
        check("rhythm: a heavier tier stays surfaced longer and burrowed longer", FrostbloomRules.surfacedTicks(Tier.BUD) < FrostbloomRules.surfacedTicks(Tier.BLOOM) && FrostbloomRules.surfacedTicks(Tier.BLOOM) < FrostbloomRules.surfacedTicks(Tier.RIMEHEART) && FrostbloomRules.burrowedTicks(Tier.BUD) < FrostbloomRules.burrowedTicks(Tier.BLOOM) && FrostbloomRules.burrowedTicks(Tier.BLOOM) < FrostbloomRules.burrowedTicks(Tier.RIMEHEART), "");
        check("rhythm: the cycle is burrowed + warning + surfaced for each tier", FrostbloomRules.cycleTicks(Tier.BUD) == 60 + 24 + 30 && FrostbloomRules.cycleTicks(Tier.RIMEHEART) == 100 + 24 + 70, "");
        // Shatter: Rimeheart only, at exactly 20% of its health in one window.
        check("shatter: only the Rimeheart shatters; Bud and Bloom never do, however hard they are hit", !FrostbloomRules.shatters(Tier.BUD, 1e9) && !FrostbloomRules.shatters(Tier.BLOOM, 1e9), "");
        check("shatter: Rimeheart at 44 damage (20% of 220) shatters, at 43.9 it does not", FrostbloomRules.shatters(Tier.RIMEHEART, 44.0) && !FrostbloomRules.shatters(Tier.RIMEHEART, 43.9), "");
        check("ground freeze: Bloom and Rimeheart freeze the ground, Bud does not", !FrostbloomRules.freezesGround(Tier.BUD) && FrostbloomRules.freezesGround(Tier.BLOOM) && FrostbloomRules.freezesGround(Tier.RIMEHEART), "");
        System.out.println(fails == 0 ? "ALL PASS" : "FAILED " + fails);
        if (fails != 0) System.exit(1);
    }
    static boolean asc(Tier lo, Tier hi) {
        return FrostbloomRules.maxHealth(lo) < FrostbloomRules.maxHealth(hi) && FrostbloomRules.attackDamage(lo) < FrostbloomRules.attackDamage(hi) && FrostbloomRules.crystals(lo) < FrostbloomRules.crystals(hi);
    }
    static boolean boundedDamage() {
        for (Tier t : Tier.values()) for (double d = -1; d <= 10; d += 0.05) { double x = FrostbloomRules.burstDamage(t, d); if (x < 0 || x > FrostbloomRules.attackDamage(t) + 1e-9) return false; }
        return true;
    }
    static boolean monotoneDamage() {
        for (Tier t : Tier.values()) { double prev = Double.MAX_VALUE; for (double d = 0; d <= 5; d += 0.05) { double x = FrostbloomRules.burstDamage(t, d); if (x > prev + 1e-9) return false; prev = x; } }
        return true;
    }
    static boolean rhythm() { for (Tier t : Tier.values()) { double s = FrostbloomRules.vulnerableShare(t); if (s < 0.15 || s > 0.45) return false; } return true; }
    static String shares() { StringBuilder b = new StringBuilder(); for (Tier t : Tier.values()) b.append(t).append('=').append(String.format("%.2f", FrostbloomRules.vulnerableShare(t))).append(' '); return b.toString(); }
}
