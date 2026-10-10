import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.Base64;

/**
 * The three owner-chosen fodder heads (2026-10-10). Engine-free: it reads the SOURCE text, so it needs no Minecraft jar.
 * Expected URLs are written here from the owner's own /give commands, NOT read back from the code, so a corrupted base64 string cannot agree with itself.
 */
public class FodderHeadCheck {
    static int fails = 0;
    static void check(String name, boolean ok, String note) {
        System.out.println((ok ? "PASS " : "FAIL ") + name + (note.isEmpty() ? "" : "  " + note));
        if (!ok) fails++;
    }

    static final String ROOT = "src/main/java/com/solme/emberfall/entity/";

    static String constant(String src, String name) {
        Matcher m = Pattern.compile("static final String " + name + "\\s*=[^\\n]*\\n\\s*\"([A-Za-z0-9+/=]+)\"").matcher(src);
        return m.find() ? m.group(1) : null;
    }

    static String url(String b64) {
        String json = new String(Base64.getDecoder().decode(b64));
        Matcher m = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        return m.find() ? m.group(1) : "";
    }

    static String methodBody(String src, String sig) {
        int i = src.indexOf(sig);
        if (i < 0) return "";
        int depth = 0, start = src.indexOf('{', i);
        for (int k = start; k < src.length(); k++) {
            if (src.charAt(k) == '{') depth++;
            if (src.charAt(k) == '}' && --depth == 0) return src.substring(start, k + 1);
        }
        return "";
    }

    public static void main(String[] args) throws Exception {
        String heads = Files.readString(Path.of(ROOT + "EliteHeads.java"));
        String[][] want = {
            {"UNDEAD_KNIGHT", "http://textures.minecraft.net/texture/aef904c66f4cd357eb80a1e405783f521d284503435aede603c89db15e685709", "undeadKnightHead", "undead_knight", "HordeShieldbearer"},
            {"ELITE_ZOMBIE", "http://textures.minecraft.net/texture/7ba757aa3bc19212eee44c56bd077eccf99febe0ff6f6cc85ae12d9d215da064", "eliteZombieHead", "elite_zombie", "HordeCharger"},
            {"ZOMBIE_TIER_2", "http://textures.minecraft.net/texture/2523ea773e01ccc5c0f7cadd3fb88b2b751f31c27899ef026e647e872cae44e5", "zombieTier2Head", "zombie_tier2", "HordeSpitter"},
        };
        Set<String> seen = new HashSet<>();
        for (String[] w : want) {
            String c = constant(heads, w[0]);
            check("H1 " + w[0] + " exists", c != null, "");
            String got = c == null ? "" : url(c);
            check("H2 " + w[0] + " decodes to the owner's texture URL", got.equals(w[1]), got);
            seen.add(got);
            Matcher lm = Pattern.compile(w[2] + "\\(\\) \\{ return skull\\(" + w[0] + ", \"([^\"]*)\"\\); \\}").matcher(heads);
            String codeLabel = lm.find() ? lm.group(1) : "";
            check("H3 the label in the CODE ('" + codeLabel + "') fits the 16-char GameProfile limit and is not empty", !codeLabel.isEmpty() && codeLabel.length() <= 16, "len " + codeLabel.length());
            check("H4 " + w[2] + "() builds the skull from " + w[0] + " with label " + w[3], heads.contains(w[2] + "() { return skull(" + w[0] + ", \"" + w[3] + "\"); }"), "");
            String cls = Files.readString(Path.of(ROOT + w[4] + ".java"));
            String body = methodBody(cls, "public void prepare()");
            check("H5 " + w[4] + ".prepare() wears " + w[2] + "()", body.contains("EliteHeads.wear(this, EliteHeads." + w[2] + "())"), "");
        }
        check("H6 the three heads are three different textures", seen.size() == 3, "distinct=" + seen.size());
        String wear = methodBody(heads, "public static void wear(");
        check("H7 wear() puts the head in the HEAD slot", wear.contains("EquipmentSlot.HEAD, head"), "");
        check("H8 wear() zeroes the head drop chance (a worn head is never loot)", wear.contains("setDropChance(net.minecraft.world.entity.EquipmentSlot.HEAD, 0.0F)"), "");
        // The plain horde zombie must stay plain: the whole point is that these three LOOK different from it.
        String plain = Files.readString(Path.of(ROOT + "HordeZombie.java"));
        check("H9 the regular Horde Zombie wears none of the three heads", !plain.contains("undeadKnightHead") && !plain.contains("eliteZombieHead") && !plain.contains("zombieTier2Head"), "");
        System.out.println(fails == 0 ? "ALL PASS" : "FAILED " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }
}
