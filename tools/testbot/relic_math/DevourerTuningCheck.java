import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pins the DEVOURER side of the owner's 35% boost by name (Koda, row 10). BossTuningCheck proves the arithmetic of BossTuning; it cannot prove the Devourer
 * READS it, because DevourerBrain needs Minecraft types. So this check reads the two source files as text (no classpath, like GlyphGuardCheck). It proves
 * the wiring exists in the source; it cannot prove the running boss has 351 HP (a server run does). Run from tools/testbot/relic_math, as CI does.
 */
public class DevourerTuningCheck {
    static int fails = 0, total = 0;
    static final Path ROOT = Path.of("../../..");
    static void check(String l, boolean ok, String e) { total++; System.out.println((ok ? "PASS " : "FAIL ") + l + (e.isEmpty() ? "" : "  " + e)); if (!ok) fails++; }

    static String read(String rel) throws Exception { return Files.readString(ROOT.resolve(rel), java.nio.charset.StandardCharsets.UTF_8); }

    /** Source with // and /* *\/ comments removed, so a comment can neither satisfy nor trip a text match. String literals are not special-cased: none matters here. */
    static String code(String src) { return src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", ""); }

    /** The text of the first method whose signature line contains {@code sig}, from that line to the line holding its closing "    }". */
    static String method(String src, String sig) {
        int i = src.indexOf(sig);
        if (i < 0) return "";
        int end = src.indexOf("\n    }", i);
        return end < 0 ? "" : src.substring(i, end);
    }

    public static void main(String[] a) throws Exception {
        String brain = code(read("src/main/java/com/solme/emberfall/entity/DevourerBrain.java"));
        String spawn = code(read("src/main/java/com/solme/emberfall/entity/DevourerSpawn.java"));
        String tuning = code(read("src/main/java/com/solme/emberfall/boss/BossTuning.java"));

        // ---- the boss attributes read BossTuning (not a literal) ----
        String boss = method(brain, "public static AttributeSupplier.Builder createBossAttributes()");
        check("D1 the Devourer's createBossAttributes was found", !boss.isEmpty(), "(" + boss.length() + " chars)");
        check("D2 its MAX_HEALTH comes from BossTuning.devourerHealth(), not a number", Pattern.compile("MAX_HEALTH,\\s*(?:com\\.solme\\.emberfall\\.boss\\.)?BossTuning\\.devourerHealth\\(\\)").matcher(boss).find(), "");
        check("D3 its MOVEMENT_SPEED comes from BossTuning.devourerSpeed(), not a number", Pattern.compile("MOVEMENT_SPEED,\\s*(?:com\\.solme\\.emberfall\\.boss\\.)?BossTuning\\.devourerSpeed\\(\\)").matcher(boss).find(), "");
        check("D4 no raw 260 or 0.32 literal is left in the boss attributes", !boss.contains("260") && !boss.contains("0.32"), "");

        // ---- every damage the boss deals to a player is multiplied by damageScale ----
        List<String> sites = new ArrayList<>();
        Matcher m = Pattern.compile("(?<![A-Za-z0-9_])(?!super\\.)[A-Za-z0-9_.()]+\\.hurtServer\\([^;]*;").matcher(brain);
        while (m.find()) sites.add(m.group());
        check("D5 the boss damages anything through exactly 5 .hurtServer calls (any receiver but super): coil, leap, dash, burst, linger", sites.size() == 5, "(" + sites.size() + ")");
        int scaled = 0; StringBuilder bare = new StringBuilder();
        for (String s : sites) { if (s.contains("* damageScale")) scaled++; else bare.append(s, 0, Math.min(70, s.length())).append(" | "); }
        check("D6 every one of those damage lines multiplies by damageScale", scaled == sites.size() && !sites.isEmpty(), scaled == sites.size() ? "" : "(unscaled: " + bare + ")");
        for (String c : new String[]{"COIL_DAMAGE", "LEAP_LAND_DAMAGE", "DASH_DAMAGE", "BURST_DAMAGE", "LINGER_DAMAGE_PER_SECOND"}) {
            boolean ok = false; for (String s : sites) if (s.contains(c + " * damageScale")) ok = true;
            check("D7 " + c + " reaches a player scaled", ok, "");
        }
        check("D8 damageScale starts at BossTuning.devourerDamageScale() (the boost, before any curse)", Pattern.compile("float damageScale\\s*=\\s*(?:com\\.solme\\.emberfall\\.boss\\.)?BossTuning\\.devourerDamageScale\\(\\)").matcher(brain).find(), "");
        check("D9 the Boss Curse goes through BossTuning.withCurse so it stacks on the boost", Pattern.compile("damageScale\\s*=\\s*\\(float\\)\\s*(?:com\\.solme\\.emberfall\\.boss\\.)?BossTuning\\.withCurse\\(").matcher(brain).find(), "");

        // ---- the Devourer Spawn (the minions) is deliberately NOT boosted ----
        String sp = method(spawn, "public static AttributeSupplier.Builder createAttributes()");
        check("D10 the spawn's createAttributes was found", !sp.isEmpty(), "(" + sp.length() + " chars)");
        check("D11 the spawn is still 25 HP", Pattern.compile("MAX_HEALTH,\\s*25\\.0\\)").matcher(sp).find(), "");
        check("D12 the spawn is still 0.32 speed", Pattern.compile("MOVEMENT_SPEED,\\s*0\\.32\\)").matcher(sp).find(), "");
        check("D13 the spawn is still 4 attack damage", Pattern.compile("ATTACK_DAMAGE,\\s*4\\.0\\)").matcher(sp).find(), "");
        check("D14 the spawn file never mentions BossTuning or damageScale (it is not boosted)", !spawn.contains("BossTuning") && !spawn.contains("damageScale"), "");
        check("D15 the spawn base values are NOT the boss's boosted ones (25 != 351)", !sp.contains("351") && !sp.contains("0.432"), "");

        // ---- BossTuning's Devourer numbers (the ones the lines above call) ----
        check("D16 BossTuning keeps the Devourer base 260 HP and 0.32 speed", Pattern.compile("DEVOURER_BASE_HEALTH\\s*=\\s*260\\.0").matcher(tuning).find() && Pattern.compile("DEVOURER_BASE_SPEED\\s*=\\s*0\\.32").matcher(tuning).find(), "");
        check("D17 the pure arithmetic: 260 x 1.35 = 351, 0.32 x 1.35 = 0.432, scale 1.35", Math.round(260.0 * 1.35) == 351 && Math.abs(0.32 * 1.35 - 0.432) < 1e-9, "");

        System.out.println();
        System.out.println(fails == 0 ? "ALL PASS (" + total + " checks)" : "SOME FAIL (" + fails + " of " + total + ")");
        if (fails > 0) System.exit(1);
    }
}
