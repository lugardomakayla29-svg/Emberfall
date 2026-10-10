import com.solme.emberfall.boss.BroodtideRules;
import com.solme.emberfall.boss.TideClock;
import com.solme.emberfall.boss.BossTuning;

/**
 * Pure checks for the Broodtide body's small rules (BroodtideRules): the rooted jump delay survives the aggressive divide, the Tide armour is applied
 * once and is never zero, a resize never heals past max, and the Brood-Kin cap is 6. They do NOT prove the entity calls these (a jar check does) or that
 * the boss looks right (the owner judges that).
 */
public class BroodtideRulesCheck {
    static int fails = 0, total = 0;

    static void check(String l, boolean ok, String e) {
        total++;
        System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e);
        if (!ok) fails++;
    }

    public static void main(String[] a) {
        int d = BroodtideRules.ROOTED_JUMP_DELAY;
        check("R1 the rooted jump delay is positive (an int overflow would go negative and hop every tick)", d > 0, "delay " + d);
        check("R2 even divided by 3 (aggressive, per the Slime bytecode) it stays above one hour of ticks", BroodtideRules.aggressiveDelay(d) > BroodtideRules.NEVER_JUMPS_TICKS, "aggressive " + BroodtideRules.aggressiveDelay(d));
        check("R3 so the body cannot hop within an hour, aggressive or not", !BroodtideRules.canJumpWithinAnHour(d), "");
        check("R4 the check CAN see a bad delay: a delay of 100 ticks can hop", BroodtideRules.canJumpWithinAnHour(100) && BroodtideRules.canJumpWithinAnHour(20 * 60 * 60 * 2 / 1000), "");
        check("R5 a delay just under the floor times 3 is still caught (the divide matters)", BroodtideRules.canJumpWithinAnHour((int) (BroodtideRules.NEVER_JUMPS_TICKS * 2)), "");

        check("R6 horizontal motion is always zero, whatever it was", BroodtideRules.rootedHorizontal(5.0) == 0.0 && BroodtideRules.rootedHorizontal(-0.3) == 0.0 && BroodtideRules.rootedHorizontal(0.0) == 0.0, "");

        float ebb = BroodtideRules.damageTaken(10F, 0);
        float flood = BroodtideRules.damageTaken(10F, TideClock.EBB_TICKS);
        check("R7 a hit of 10 does 10 in Ebb and 3.5 in Flood", Math.abs(ebb - 10F) < 1e-4 && Math.abs(flood - 3.5F) < 1e-4, "ebb " + ebb + " flood " + flood);
        check("R8 a positive hit is never reduced to zero, even a tiny one in Flood", BroodtideRules.damageTaken(0.01F, TideClock.EBB_TICKS) > 0F, "tiny " + BroodtideRules.damageTaken(0.01F, TideClock.EBB_TICKS));
        check("R9 the armour is applied ONCE (a hit of 100 in Flood is 35, not 12.25)", Math.abs(BroodtideRules.damageTaken(100F, TideClock.EBB_TICKS) - 35F) < 1e-3, "got " + BroodtideRules.damageTaken(100F, TideClock.EBB_TICKS));
        check("R10 healing (a negative amount) and zero are passed through untouched", BroodtideRules.damageTaken(0F, 0) == 0F && BroodtideRules.damageTaken(-4F, TideClock.EBB_TICKS) == -4F, "");

        check("R11 a resize fraction is clamped to 0..1 (never heals past max, never negative)", BroodtideRules.clampedFraction(1.7) == 1.0 && BroodtideRules.clampedFraction(-0.4) == 0.0 && BroodtideRules.clampedFraction(0.5) == 0.5, "");

        check("R12 the Brood-Kin cap is the plan's 6", BroodtideRules.KIN_CAP == 6, "cap " + BroodtideRules.KIN_CAP);
        check("R13 eating is allowed with 5 alive and refused with 6 (no off-by-one)", BroodtideRules.mayDevour(5) && !BroodtideRules.mayDevour(6) && !BroodtideRules.mayDevour(7), "");
        check("R14 eating is allowed with none alive", BroodtideRules.mayDevour(0), "");

        // The 1024 attribute ceiling: effective health (attribute / factor) must equal the pool the curse promises, at every tier.
        double base = BossTuning.broodtideHealth();
        double[] tiers = {1.0, 1.2, 1.3, 1.4, 1.5};
        boolean allExact = true; String worst = "";
        for (double t : tiers) {
            double wanted = base * t;
            double eff = BroodtideRules.attributeFor(wanted) / BroodtideRules.overflowFactor(wanted);
            if (Math.abs(eff - wanted) > wanted * 1e-5) { allExact = false; worst += " tier " + t + " wanted " + wanted + " got " + eff; }
        }
        check("R15 effective health equals the wanted pool at every curse tier, including the ones past the 1024 ceiling" , allExact, worst);
        check("R16 the attribute never exceeds the ceiling (it would be silently clamped otherwise)", BroodtideRules.attributeFor(base * 1.5) <= BroodtideRules.ATTRIBUTE_CEILING && BroodtideRules.attributeFor(1e9) == BroodtideRules.ATTRIBUTE_CEILING, "attr " + BroodtideRules.attributeFor(base * 1.5));
        check("R17 a pool of exactly 1024 fits with no damage factor, 1025 needs one (no off-by-one)", BroodtideRules.overflowFactor(1024.0) == 1.0F && BroodtideRules.overflowFactor(1025.0) < 1.0F, "at 1024 " + BroodtideRules.overflowFactor(1024.0) + " at 1025 " + BroodtideRules.overflowFactor(1025.0));
        check("R18 the curse IV Broodtide (the live bug: 1215 became 1024) now holds 1215 effective", Math.abs(BroodtideRules.attributeFor(base * 1.5) / BroodtideRules.overflowFactor(base * 1.5) - 1215.0) < 0.5, "base " + base);
        check("R19 a small pool is untouched: attribute equals the pool, factor 1.0", BroodtideRules.attributeFor(810.0) == 810.0 && BroodtideRules.overflowFactor(810.0) == 1.0F, "");

        System.out.println();
        System.out.println(fails == 0 ? "ALL PASS (" + total + " checks)" : "SOME FAIL (" + fails + " of " + total + ")");
        if (fails > 0) System.exit(1);
    }
}
