import com.solme.emberfall.bot.BotSkins;
import java.io.File;
import java.util.*;

public class BotSkinsCheck {
    static int fails = 0;
    static void check(String l, boolean ok, String e) { System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e); if (!ok) fails++; }
    static String findAssets() {
        File d = new File("").getAbsoluteFile();
        for (int i = 0; i < 6 && d != null; i++, d = d.getParentFile()) {
            File c = new File(d, "src/main/resources/assets/emberfall/");
            if (c.isDirectory()) return c.getPath() + File.separator;
        }
        return "src/main/resources/assets/emberfall/";
    }

    public static void main(String[] a) {
        check("same name, same skin", BotSkins.indexFor("EmberTester7") == BotSkins.indexFor("EmberTester7"), "");
        boolean inRange = true;
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) { int k = BotSkins.indexFor("EmberTester" + i); inRange &= k >= 1 && k <= BotSkins.COUNT; seen.add(k); }
        check("every index is within 1..COUNT", inRange, "");
        check("all " + BotSkins.COUNT + " skins get used across many names", seen.size() == BotSkins.COUNT, "used " + seen.size());
        check("null and empty are safe", BotSkins.indexFor(null) == 1 && BotSkins.indexFor("") == 1, "");
        check("a negative hashCode never gives a negative index", BotSkins.indexFor("polygenelubricants") >= 1, "");
        // Every skin the code can ask for must exist on disk, or a bot would fall back to a blank texture.
        // Works from the repo root AND from tools/testbot/relic_math (where CI runs it): walk up until src/main/resources is found.
        String root = a.length > 0 ? a[0] : findAssets();
        boolean allExist = true;
        for (int i = 1; i <= BotSkins.COUNT; i++) allExist &= new File(root + "textures/entity/bot/skin" + i + ".png").isFile();
        check("every skin file the code can pick exists", allExist, root);
        // Mutation: if COUNT were larger than the files shipped, the existence check must catch it.
        boolean wouldMiss = !new File(root + "textures/entity/bot/skin" + (BotSkins.COUNT + 1) + ".png").isFile();
        check("MUTATION: asking for skin COUNT+1 would be missing (so the check has teeth)", wouldMiss, "");
        System.out.println(fails == 0 ? "ALL PASS" : "FAILED " + fails);
        if (fails != 0) System.exit(1);
    }
}
