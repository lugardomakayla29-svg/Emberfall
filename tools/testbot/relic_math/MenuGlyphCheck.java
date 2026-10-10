import com.solme.emberfall.relic.MenuGlyphs;
import com.solme.emberfall.relic.MenuGlyphs.Menu;
import com.solme.emberfall.relic.MenuGlyphs.Row;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Menu icon glyphs (Drop 2 Task A). Reads the SAME table the screens draw from (relic/MenuGlyphs), the verified glyph list (docs/ui_glyphs.json) and the
 * screen sources, so it cannot pass on a copy. Run from tools/testbot/relic_math, as CI does. No server, no client: it proves the glyphs are VERIFIED, fit
 * the row, and are used consistently; it cannot prove how any menu LOOKS.
 */
public class MenuGlyphCheck {
    static int fails = 0, passes = 0;
    static final Path ROOT = Path.of("../../..");
    static void check(String l, boolean ok, String e) { System.out.println((ok ? "PASS " : "FAIL ") + l + (e.isEmpty() ? "" : "  " + e)); if (ok) passes++; else fails++; }

    /** The five glyphs RunHud's stats panel uses, with the stat each means. A glyph in this map keeps that meaning everywhere and is used for nothing else. */
    static final Map<String, String> PANEL = Map.of("\u2605", "level", "\u2666", "gold", "\u2709", "chest", "\u2662", "silver", "\u2620", "kills");

    public static void main(String[] a) throws Exception {
        // ---- the verified table, read from the real file (JDK only: CI compiles checks with javac -sourcepath and no libraries) ----
        String json = Files.readString(ROOT.resolve("docs/ui_glyphs.json"), java.nio.charset.StandardCharsets.UTF_8);
        int split = json.indexOf("\"unifont_unverified\"");
        String bitmapPart = split < 0 ? json : json.substring(0, split);
        String unifontPart = split < 0 ? "" : json.substring(split);
        Pattern entry = Pattern.compile("\"char\"\\s*:\\s*\"([^\"]+)\"[^}]*?\"width\"\\s*:\\s*(\\d+)[^}]*?\"rows\"\\s*:\\s*\"(\\d+)\\.\\.(\\d+)\"");
        Map<String, int[]> bitmap = new HashMap<>(); // glyph -> {width, firstRow, lastRow}
        Matcher em = entry.matcher(bitmapPart);
        while (em.find()) bitmap.put(em.group(1), new int[]{Integer.parseInt(em.group(2)), Integer.parseInt(em.group(3)), Integer.parseInt(em.group(4))});
        Set<String> unverified = new HashSet<>();
        Matcher um = Pattern.compile("\"char\"\\s*:\\s*\"([^\"]+)\"").matcher(unifontPart);
        while (um.find()) unverified.add(um.group(1));
        check("G0 the verified table has the 64 bitmap glyphs", bitmap.size() == 64, "(" + bitmap.size() + ")");

        // ---- G1..G4 per row: verified, not unifont, fits the row, has a meaning and a reason ----
        int rowsTotal = 0, notVerified = 0, tooTall = 0, wide = 0, blank = 0, inUnifont = 0;
        StringBuilder why = new StringBuilder();
        for (Menu m : MenuGlyphs.ALL) {
            for (Row r : m.rows()) {
                rowsTotal++;
                int[] g = bitmap.get(r.glyph());
                if (g == null) { notVerified++; why.append(m.name()).append(".").append(r.meaning()).append(" "); continue; }
                if (unverified.contains(r.glyph())) inUnifont++;
                if (g[2] > MenuGlyphs.MAX_LAST_ROW) { tooTall++; why.append(m.name()).append(".").append(r.meaning()).append("(row ").append(g[2]).append(") "); }
                if (g[0] > 9) wide++;
                if (r.meaning().isBlank() || r.why().isBlank()) blank++;
            }
        }
        check("G1 every menu row's glyph is in the VERIFIED bitmap table", notVerified == 0, notVerified == 0 ? "(" + rowsTotal + " rows)" : "(" + why + ")");
        check("G2 no row uses a glyph from the unifont_unverified section", inUnifont == 0, "(" + inUnifont + ")");
        check("G3 every glyph fits the row: it ends on row " + MenuGlyphs.MAX_LAST_ROW + " or above (none hangs below the text line)", tooTall == 0, tooTall == 0 ? "" : "(" + why + ")");
        check("G4 no glyph is wider than 9 px", wide == 0, "(" + wide + ")");
        check("G5 every row has a meaning and a reason", blank == 0, "(" + blank + ")");
        check("G6 there are rows to check (the table is not empty)", rowsTotal >= 15, "(" + rowsTotal + ")");

        // ---- G7 one meaning per glyph within a menu ----
        boolean dupOk = true; String dup = "";
        for (Menu m : MenuGlyphs.ALL) {
            Map<String, String> seen = new HashMap<>();
            for (Row r : m.rows()) {
                String prev = seen.put(r.glyph(), r.meaning());
                if (prev != null && !prev.equals(r.meaning())) { dupOk = false; dup += m.name() + ":" + r.glyph() + "=" + prev + "/" + r.meaning() + " "; }
            }
        }
        check("G7 inside one menu, two different meanings never share a glyph", dupOk, dupOk ? "" : "(" + dup + ")");

        // ---- G8 the stats panel glyphs keep their meaning everywhere and are used for nothing else ----
        boolean panelOk = true; String bad = "";
        for (Menu m : MenuGlyphs.ALL) for (Row r : m.rows()) {
            String owner = PANEL.get(r.glyph());
            if (owner != null && !owner.equals(r.meaning())) { panelOk = false; bad += m.name() + "." + r.meaning() + "=" + r.glyph() + "(panel: " + owner + ") "; }
        }
        check("G8 a glyph the stats panel uses is only used for that stat", panelOk, panelOk ? "" : "(" + bad + ")");
        boolean allowedPanelMeanings = true;
        for (Menu m : MenuGlyphs.ALL) for (Row r : m.rows()) if (PANEL.containsValue(r.meaning()) && !r.glyph().equals(invert(PANEL).get(r.meaning()))) allowedPanelMeanings = false;
        check("G9 a row that means a panel stat (level, gold, chest, silver, kills) uses that stat's panel glyph", allowedPanelMeanings, "");

        // ---- G10 the glyphs in RunHud (read from its source) are exactly the ones this check believes ----
        String hud = Files.readString(ROOT.resolve("src/client/java/com/solme/emberfall/client/RunHud.java"));
        Matcher mm = Pattern.compile("STAT_ICONS\\s*=\\s*\\{([^}]*)\\}").matcher(hud);
        Set<String> hudGlyphs = new HashSet<>();
        if (mm.find()) { Matcher u = Pattern.compile("\\\\u([0-9A-Fa-f]{4})").matcher(mm.group(1)); while (u.find()) hudGlyphs.add(String.valueOf((char) Integer.parseInt(u.group(1), 16))); }
        check("G10 the stats panel's own glyphs are the five this check protects", hudGlyphs.equals(PANEL.keySet()), "(" + hudGlyphs.size() + " found)");

        // ---- G11..G13 the SCREENS use the table: every meaning they ask for exists, and every screen of the brief is covered ----
        Pattern ask = Pattern.compile("MenuGlyphs\\.glyph\\(MenuGlyphs\\.([A-Z_]+),\\s*\"([a-z]+)\"\\)");
        Map<String, Menu> byConst = Map.of("SHOP", MenuGlyphs.SHOP, "TOME", MenuGlyphs.TOME, "WEAPON", MenuGlyphs.WEAPON, "MERCHANT", MenuGlyphs.MERCHANT, "SHRINE", MenuGlyphs.SHRINE, "RUN_END", MenuGlyphs.RUN_END, "CHEST", MenuGlyphs.CHEST);
        String[] screens = {"ShopScreen", "TomeChoiceScreen", "WeaponChoiceScreen", "MerchantScreen", "ShrineScreen", "RunEndScreen", "ChestRevealScreen"};
        boolean allAsk = true, allExist = true; String missing = "", unknown = "";
        for (String sc : screens) {
            String src = Files.readString(ROOT.resolve("src/client/java/com/solme/emberfall/client/" + sc + ".java"));
            Matcher q = ask.matcher(src); int n = 0;
            while (q.find()) {
                n++;
                Menu m = byConst.get(q.group(1));
                if (m == null || MenuGlyphs.glyph(m, q.group(2)).isEmpty()) { allExist = false; unknown += sc + ":" + q.group(1) + "." + q.group(2) + " "; }
            }
            if (n == 0) { allAsk = false; missing += sc + " "; }
        }
        check("G11 each of the seven screens draws at least one glyph from the table", allAsk, allAsk ? "" : "(none in: " + missing + ")");
        check("G12 every (menu, meaning) a screen asks for exists in the table (a typo would silently draw nothing)", allExist, allExist ? "" : "(" + unknown + ")");

        // ---- G12b the two screens that hand the meaning to the table THROUGH A VARIABLE. G12 cannot see those (it only reads literal glyph(X, "m") calls), so a misspelt
        // "kills" -> "killz" passed and drew no icon. RunEndScreen.line(g, cx, y, "<meaning>", ...) feeds RUN_END; ShopScreen rowKinds.add("<meaning>") feeds SHOP. ----
        String runEnd = Files.readString(ROOT.resolve("src/client/java/com/solme/emberfall/client/RunEndScreen.java"));
        String shop = Files.readString(ROOT.resolve("src/client/java/com/solme/emberfall/client/ShopScreen.java"));
        Matcher lc = Pattern.compile("(?<![A-Za-z0-9_])(?:this\\s*\\.\\s*)?line\\s*\\(\\s*g\\s*,\\s*cx\\s*,\\s*y\\s*,\\s*\"([^\"]*)\"").matcher(runEnd);
        List<String> runEndMeanings = new ArrayList<>(); while (lc.find()) runEndMeanings.add(lc.group(1));
        Matcher rk = Pattern.compile("rowKinds\\.add\\(\"([^\"]*)\"\\)").matcher(shop);
        List<String> shopMeanings = new ArrayList<>(); while (rk.find()) shopMeanings.add(rk.group(1));
        String badVar = "";
        for (String mn : runEndMeanings) if (MenuGlyphs.glyph(MenuGlyphs.RUN_END, mn).isEmpty()) badVar += "RunEndScreen line(\"" + mn + "\") ";
        for (String mn : shopMeanings) if (MenuGlyphs.glyph(MenuGlyphs.SHOP, mn).isEmpty()) badVar += "ShopScreen rowKinds.add(\"" + mn + "\") ";
        check("G12b every meaning passed through a variable (RunEnd line(...), Shop rowKinds.add(...)) exists in its menu", badVar.isEmpty(), badVar.isEmpty() ? "(" + runEndMeanings.size() + " + " + shopMeanings.size() + " read)" : "(" + badVar + ")");
        // The scan above is only as good as its patterns, so pin the counts: every call that feeds a variable meaning must have been READ. A new call written another way would
        // change the total below and turn this red instead of slipping past G12b.
        int runEndCalls = 0; Matcher anyLine = Pattern.compile("(?<![A-Za-z0-9_])(?:this\\s*\\.\\s*)?line\\s*\\(\\s*g\\s*,").matcher(runEnd); while (anyLine.find()) runEndCalls++;
        int shopAdds = 0; Matcher anyAdd = Pattern.compile("rowKinds\\.add\\(").matcher(shop); while (anyAdd.find()) shopAdds++;
        check("G12c every call that feeds a variable meaning was read: RunEnd line(g, ...) calls == meanings read, Shop rowKinds.add calls == meanings read", runEndCalls == runEndMeanings.size() && shopAdds == shopMeanings.size() && !runEndMeanings.isEmpty() && !shopMeanings.isEmpty(), "(RunEnd " + runEndCalls + " calls / " + runEndMeanings.size() + " read, Shop " + shopAdds + " / " + shopMeanings.size() + ")");
        // The two variable sinks themselves must be exactly the ones this section knows about: a third glyph(X, <variable>) call would be a new blind spot.
        int variableSinks = 0; Matcher sink = Pattern.compile("MenuGlyphs\\.glyph\\(MenuGlyphs\\.[A-Z_]+,\\s*+(?!\")").matcher(runEnd + shop); while (sink.find()) variableSinks++;
        int allSinks = 0; for (String sc : screens) { Matcher z = Pattern.compile("MenuGlyphs\\.glyph\\(MenuGlyphs\\.[A-Z_]+,\\s*+(?!\")").matcher(Files.readString(ROOT.resolve("src/client/java/com/solme/emberfall/client/" + sc + ".java"))); while (z.find()) allSinks++; }
        check("G12d exactly two glyph(...) calls take a variable meaning (RunEnd line, Shop rowKinds.get), and both are in the files G12b reads", variableSinks == 2 && allSinks == 2, "(in RunEnd+Shop " + variableSinks + ", all screens " + allSinks + ")");

        // ---- G13 no screen holds a glyph literal of its own (it would bypass this table) ----
        boolean noLiteral = true; String lit = "";
        for (String sc : screens) {
            String src = Files.readString(ROOT.resolve("src/client/java/com/solme/emberfall/client/" + sc + ".java"));
            if (Pattern.compile("\\\\u2[0-9A-Fa-f]{3}|[\u2190-\u2bff]").matcher(src).find()) { noLiteral = false; lit += sc + " "; }
        }
        check("G13 no menu screen holds a glyph of its own: they all come from MenuGlyphs", noLiteral, noLiteral ? "" : "(" + lit + ")");

        // ---- G14 the glyph does not run into a button's label. Per-character widths are MEASURED from the 1.21.11 client jar's ascii.png (advance = rightmost lit
        // column + 2; the blank space is 4, its vanilla value, because a blank cell measures 0). Cross-checked against known vanilla widths (i 2, l 3, I 4, t 4, f 5). ----
        final int[] ADV = {4,2,4,6,6,6,6,2,4,4,4,6,2,6,2,6,6,6,6,6,6,6,6,6,6,6,2,2,5,6,5,6,7,6,6,6,6,6,6,6,6,4,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,4,6,4,6,6,3,6,6,6,6,6,5,6,6,2,6,5,3,6,6,6,6,6,6,6,4,6,6,6,6,6,6,4,2,4,7}; // chars 32..126
        final int inset = 6, glyphMax = 9, margin = 2;
        int longestShop = 0; String longestName = "";
        StringBuilder all = new StringBuilder();
        try (var walk = Files.walk(ROOT.resolve("src/main/java"))) {
            for (Path f : (Iterable<Path>) walk.filter(x -> x.toString().endsWith(".java"))::iterator) all.append(Files.readString(f, java.nio.charset.StandardCharsets.UTF_8));
        }
        Matcher names = Pattern.compile("new (?:WeaponType|UpgradeType)\\(\"[a-z_0-9]+\",\\s*\"([^\"]+)\"").matcher(all);
        int nameCount = 0;
        while (names.find()) {
            nameCount++;
            // The shop appends " - 99999 currency" (a weapon) or " (10/10) - 99999 currency" (an upgrade); take the longer suffix for every name to be safe.
            String label = names.group(1) + " (10/10) - 99999 currency";
            int w = 0; for (char c : label.toCharArray()) w += (c >= 32 && c <= 126) ? ADV[c - 32] : 7;
            if (w > longestShop) { longestShop = w; longestName = names.group(1); }
        }
        check("G14a the shop names were found in the source (weapons and upgrades)", nameCount >= 10, "(" + nameCount + ")");
        Object[][] buttons = {{"Shop row (longest: " + longestName + ")", 280, longestShop}, {"Shrine option", 170, w("Curse III", ADV)}, {"Merchant buy", 98, w("Buy 99999", ADV)},
                {"Banish", 70, w("Banish", ADV)}, {"Reroll", 150, w("Reroll (999 gold)", ADV)}};
        boolean fit = true; String tight = ""; StringBuilder gaps = new StringBuilder();
        for (Object[] b : buttons) {
            int labelLeft = ((Integer) b[1] - (Integer) b[2]) / 2;
            gaps.append(b[0]).append(" ").append(labelLeft - (inset + glyphMax)).append("px; ");
            if (labelLeft < inset + glyphMax + margin) { fit = false; tight += b[0] + "(label starts " + labelLeft + ") "; }
        }
        check("G14b every button's label starts at least " + margin + " px clear of the glyph, with real measured widths", fit, fit ? "(clear by: " + gaps + ")" : "(" + tight + ")");

        System.out.println(fails == 0 ? "ALL PASS (" + passes + " checks)" : "SOME FAIL (" + fails + ")");
        System.exit(fails == 0 ? 0 : 1);
    }

    static int w(String s, int[] adv) { int t = 0; for (char c : s.toCharArray()) t += (c >= 32 && c <= 126) ? adv[c - 32] : 7; return t; }

    static Map<String, String> invert(Map<String, String> m) { Map<String, String> r = new HashMap<>(); m.forEach((k, v) -> r.put(v, k)); return r; }
}
