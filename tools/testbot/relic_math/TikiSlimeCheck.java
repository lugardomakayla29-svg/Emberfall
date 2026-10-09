import com.solme.emberfall.entity.TikiSlimeRules;
import com.solme.emberfall.entity.TikiSlimeRules.Move;
import com.solme.emberfall.entity.TikiSlimeRules.Tier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tiki Slime pure rules (docs/design/TIKI_REPLACEMENT.md). No server, no client. The numbers the rules mirror are RE-READ from the real source files here,
 * so if WaveDirector, TikiMagma or TikiVoice changes, this goes red instead of a comment going stale. Run from tools/testbot/relic_math, as CI does.
 */
public class TikiSlimeCheck {
    static int fails = 0, passes = 0;
    static void check(String l, boolean ok, String e) { System.out.println((ok ? "PASS " : "FAIL ") + l + (e.isEmpty() ? "" : "  " + e)); if (ok) passes++; else fails++; }

    /** CI runs from tools/testbot/relic_math, so the repo root is three levels up (the same as GlyphGuardCheck). */
    static final Path ROOT = Path.of("../../..");
    static String read(String p) throws Exception { return Files.readString(ROOT.resolve(p)); }

    /** The number assigned to {@code name} in {@code src}, or NaN when the constant is not found. */
    static double constant(String src, String name) {
        Matcher m = Pattern.compile("\\b" + Pattern.quote(name) + "\\s*=\\s*(-?[0-9]+(?:\\.[0-9]+)?)").matcher(src);
        return m.find() ? Double.parseDouble(m.group(1)) : Double.NaN;
    }

    public static void main(String[] a) throws Exception {
        String wave = read("src/main/java/com/solme/emberfall/wave/WaveDirector.java");
        String magma = read("src/main/java/com/solme/emberfall/entity/TikiMagma.java");
        String voice = read("src/main/java/com/solme/emberfall/entity/TikiVoice.java");
        String pink = read("src/main/java/com/solme/emberfall/entity/PinkSlime.java");

        // ---- T1 tier table (masks) ----
        check("T1a masks are 1 / 3 / 4", TikiSlimeRules.masks(Tier.FODDER) == 1 && TikiSlimeRules.masks(Tier.ELITE) == 3 && TikiSlimeRules.masks(Tier.CORRUPTED) == 4, "");
        check("T1b the elite mask count equals the old Tiki Magma ELITE_SEGMENT_COUNT in the source", TikiSlimeRules.masks(Tier.ELITE) == (int) constant(magma, "ELITE_SEGMENT_COUNT"), "(" + constant(magma, "ELITE_SEGMENT_COUNT") + ")");
        check("T1c the corrupted mask count equals the old CORRUPTED_SEGMENT_COUNT in the source", TikiSlimeRules.masks(Tier.CORRUPTED) == (int) constant(magma, "CORRUPTED_SEGMENT_COUNT"), "(" + constant(magma, "CORRUPTED_SEGMENT_COUNT") + ")");
        check("T1d the masks strictly increase with the tier", TikiSlimeRules.masks(Tier.FODDER) < TikiSlimeRules.masks(Tier.ELITE) && TikiSlimeRules.masks(Tier.ELITE) < TikiSlimeRules.masks(Tier.CORRUPTED), "");

        // ---- T2 spawn weight and wave slots stay as they are ----
        check("T2a spawn weight is 0.15", TikiSlimeRules.SPAWN_WEIGHT == 0.15, "");
        check("T2b it equals WaveDirector.TIKI_SPAWN_WEIGHT in the source", TikiSlimeRules.SPAWN_WEIGHT == constant(wave, "TIKI_SPAWN_WEIGHT"), "(" + constant(wave, "TIKI_SPAWN_WEIGHT") + ")");
        check("T2c the elite count equals WaveDirector.ELITE_COUNT in the source", TikiSlimeRules.ELITE_COUNT == (int) constant(wave, "ELITE_COUNT"), "(" + constant(wave, "ELITE_COUNT") + ")");
        check("T2d the elite slot is in range", TikiSlimeRules.ELITE_SLOT >= 0 && TikiSlimeRules.ELITE_SLOT < TikiSlimeRules.ELITE_COUNT, "");
        check("T2e the source still spawns the Tiki at that slot (case " + TikiSlimeRules.ELITE_SLOT + " -> TikiMagma.spawnElite)", Pattern.compile("case " + TikiSlimeRules.ELITE_SLOT + " -> com\\.solme\\.emberfall\\.entity\\.TikiMagma\\.spawnElite").matcher(wave).find(), "");
        check("T2f the corrupted share equals TikiMagma.CORRUPTED_CHANCE in the source", TikiSlimeRules.CORRUPTED_CHANCE == constant(magma, "CORRUPTED_CHANCE"), "(" + constant(magma, "CORRUPTED_CHANCE") + ")");

        // ---- T3 split cap ----
        check("T3a the split cap is 2", TikiSlimeRules.SPLIT_CAP == 2, "");
        check("T3b a Corrupted at full health has shed none", TikiSlimeRules.shedsAt(Tier.CORRUPTED, 1.0) == 0, "");
        check("T3c at exactly 50% it has shed one", TikiSlimeRules.shedsAt(Tier.CORRUPTED, 0.50) == 1, "");
        check("T3d at 49% it has still shed only one", TikiSlimeRules.shedsAt(Tier.CORRUPTED, 0.49) == 1, "");
        check("T3e at exactly 25% it has shed two", TikiSlimeRules.shedsAt(Tier.CORRUPTED, 0.25) == 2, "");
        check("T3f at 0% it has shed two and NEVER more than the cap", TikiSlimeRules.shedsAt(Tier.CORRUPTED, 0.0) == 2, "");
        check("T3g Fodder and Elite shed none at any health", TikiSlimeRules.shedsAt(Tier.FODDER, 0.1) == 0 && TikiSlimeRules.shedsAt(Tier.ELITE, 0.1) == 0, "");
        check("T3h a third shed is refused (cap)", !TikiSlimeRules.maySplit(Tier.CORRUPTED, 2) && TikiSlimeRules.maySplit(Tier.CORRUPTED, 1) && TikiSlimeRules.maySplit(Tier.CORRUPTED, 0), "");
        check("T3i a non-Corrupted may never split", !TikiSlimeRules.maySplit(Tier.ELITE, 0) && !TikiSlimeRules.maySplit(Tier.FODDER, 0), "");
        // Probes just ABOVE each threshold: a threshold drifted upward (50% -> 60%) would shed here, and the exact-threshold probes above cannot see that.
        check("T3k just above 50% (51%) nothing has been shed yet", TikiSlimeRules.shedsAt(Tier.CORRUPTED, 0.51) == 0, "");
        check("T3l at 55% nothing has been shed yet", TikiSlimeRules.shedsAt(Tier.CORRUPTED, 0.55) == 0, "");
        check("T3m just above 25% (26%) only the first has been shed", TikiSlimeRules.shedsAt(Tier.CORRUPTED, 0.26) == 1, "");
        check("T3n the thresholds are exactly 50% then 25%, descending", TikiSlimeRules.SPLIT_AT_HEALTH_FRACTION.length == 2 && TikiSlimeRules.SPLIT_AT_HEALTH_FRACTION[0] == 0.50 && TikiSlimeRules.SPLIT_AT_HEALTH_FRACTION[1] == 0.25, "");
        // The cap must hold by itself, not because the list happens to be short: the number of thresholds may never exceed the cap.
        check("T3o there are no more shed thresholds than the cap allows (so the clamp is never the only thing holding the cap)", TikiSlimeRules.SPLIT_AT_HEALTH_FRACTION.length <= TikiSlimeRules.SPLIT_CAP, "");
        boolean never = true;
        for (int i = 0; i <= 100; i++) if (TikiSlimeRules.shedsAt(Tier.CORRUPTED, i / 100.0) > TikiSlimeRules.SPLIT_CAP) never = false;
        check("T3j across 0..100% health the shed count never exceeds the cap", never, "");

        // ---- T4 every attack winds up at least 0.5 s, and the telegraph and the hit share ONE value ----
        check("T4a the minimum wind-up is 0.5 s = 10 ticks", TikiSlimeRules.MIN_WINDUP_TICKS == 10, "");
        boolean allOk = true; String worst = "";
        for (Move m : Move.values()) if (TikiSlimeRules.isAttack(m) && TikiSlimeRules.windup(m) < TikiSlimeRules.MIN_WINDUP_TICKS) { allOk = false; worst += m + " "; }
        check("T4b every attack move winds up at least the minimum", allOk, worst.isEmpty() ? "" : "(" + worst + ")");
        check("T4c the Bounce Shriek keeps the old shriek wind-up (TikiVoice.SHRIEK_WINDUP_TICKS)", TikiSlimeRules.windup(Move.BOUNCE_SHRIEK) == (int) constant(voice, "SHRIEK_WINDUP_TICKS"), "(" + constant(voice, "SHRIEK_WINDUP_TICKS") + ")");
        check("T4d the Goo Jet keeps the old laser wind-up (TikiVoice.LASER_WINDUP_TICKS)", TikiSlimeRules.windup(Move.GOO_JET) == (int) constant(voice, "LASER_WINDUP_TICKS"), "(" + constant(voice, "LASER_WINDUP_TICKS") + ")");
        check("T4e the Mask Spit uses the Pink Slime spit wind-up (PinkSlime.SPIT_WINDUP)", TikiSlimeRules.windup(Move.MASK_SPIT) == (int) constant(pink, "SPIT_WINDUP"), "(" + constant(pink, "SPIT_WINDUP") + ")");
        boolean shared = true;
        for (Move m : Move.values()) if (TikiSlimeRules.resolveTick(m, 100L) != 100L + TikiSlimeRules.windup(m)) shared = false;
        check("T4f for every move the hit lands exactly one wind-up after the start (the telegraph and the hit share one value)", shared, "");
        check("T4g the hop and the trail are not attacks and have no wind-up", !TikiSlimeRules.isAttack(Move.HOP) && !TikiSlimeRules.isAttack(Move.GOO_TRAIL) && TikiSlimeRules.windup(Move.HOP) == 0, "");
        check("T4h every other move counts as an attack", TikiSlimeRules.isAttack(Move.BOUNCE_SHRIEK) && TikiSlimeRules.isAttack(Move.MASK_SPIT) && TikiSlimeRules.isAttack(Move.GOO_JET) && TikiSlimeRules.isAttack(Move.SPLIT_TOTEM) && TikiSlimeRules.isAttack(Move.SWALLOW_MASK), "");

        // ---- T5 which tier has which move ----
        check("T5a every tier has hop, shriek and trail", List.of(Tier.values()).stream().allMatch(t -> TikiSlimeRules.has(t, Move.HOP) && TikiSlimeRules.has(t, Move.BOUNCE_SHRIEK) && TikiSlimeRules.has(t, Move.GOO_TRAIL)), "");
        check("T5b Fodder has no spit and no jet", !TikiSlimeRules.has(Tier.FODDER, Move.MASK_SPIT) && !TikiSlimeRules.has(Tier.FODDER, Move.GOO_JET), "");
        check("T5c Elite and Corrupted have spit and jet", TikiSlimeRules.has(Tier.ELITE, Move.MASK_SPIT) && TikiSlimeRules.has(Tier.ELITE, Move.GOO_JET) && TikiSlimeRules.has(Tier.CORRUPTED, Move.MASK_SPIT) && TikiSlimeRules.has(Tier.CORRUPTED, Move.GOO_JET), "");
        check("T5d only the Corrupted sheds or swallows", TikiSlimeRules.has(Tier.CORRUPTED, Move.SPLIT_TOTEM) && TikiSlimeRules.has(Tier.CORRUPTED, Move.SWALLOW_MASK) && !TikiSlimeRules.has(Tier.ELITE, Move.SPLIT_TOTEM) && !TikiSlimeRules.has(Tier.ELITE, Move.SWALLOW_MASK) && !TikiSlimeRules.has(Tier.FODDER, Move.SPLIT_TOTEM), "");

        System.out.println(fails == 0 ? "ALL PASS (" + passes + " checks)" : "SOME FAIL (" + fails + ")");
        System.exit(fails == 0 ? 0 : 1);
    }
}
