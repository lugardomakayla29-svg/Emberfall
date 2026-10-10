import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

/**
 * P6 guard: every non-ASCII character that Java source can put on screen must be one of the VERIFIED bitmap glyphs in docs/ui_glyphs.json.
 * Reads the source as TEXT (the client sources cannot be linked here), so it needs no Minecraft jar. Run from tools/testbot/relic_math.
 *
 * What counts as "can reach the screen": a literal non-ASCII character, or a \\uXXXX escape above 0x7F, anywhere in src/client or src/main.
 * U+00A7 (the section sign) is the colour-code prefix. The game consumes it and never draws it, so it is allowed, but ONLY when it is
 * followed by a valid code character (0-9, a-f, k-o, r). A section sign followed by anything else WOULD be drawn and is a failure.
 */
public class GlyphGuardCheck {
    static int fails = 0, passes = 0;
    static void check(String l, boolean ok, String e) { System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e); if (ok) passes++; else fails++; }

    static final Path ROOT = Paths.get("../../..");
    static final Pattern ESC = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
    static final String VALID_CODES = "0123456789abcdefklmnorABCDEFKLMNOR";

    /** The verified set, read from the JSON file (the "bitmap" array only; "unifont_unverified" is deliberately NOT allowed). */
    static Set<Integer> verified(String json) {
        int b = json.indexOf("\"bitmap\""), u = json.indexOf("\"unifont_unverified\"");
        String section = json.substring(b, u < 0 ? json.length() : u);
        Set<Integer> out = new TreeSet<>();
        Matcher m = Pattern.compile("\"code\"\\s*:\\s*\"U\\+([0-9A-Fa-f]{4,6})\"").matcher(section);
        while (m.find()) out.add(Integer.parseInt(m.group(1), 16));
        return out;
    }

    /** One finding: file:line, code point, and whether it is allowed. */
    record Hit(String where, int cp, boolean ok, String why) {}

    static List<Hit> scan(String name, String text, Set<Integer> allowed) {
        List<Hit> hits = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i];
            // literal characters
            for (int k = 0; k < l.length(); ) {
                int cp = l.codePointAt(k); int n = Character.charCount(cp);
                if (cp > 0x7F) {
                    String at = name + ":" + (i + 1);
                    if (cp == 0xA7) {
                        char next = k + n < l.length() ? l.charAt(k + n) : '\0';
                        boolean valid = VALID_CODES.indexOf(next) >= 0;
                        // The ONE other legitimate use: a regex that STRIPS colour codes, written exactly "§." or "§[...]" inside replaceAll/matches.
                        // It is never drawn. Only that exact shape is exempt, so a stray section sign anywhere else still fails.
                        boolean stripper = (next == '.' || next == '[') && l.contains("replaceAll(\"\u00A7") && k > 0 && l.charAt(k - 1) == '"';
                        hits.add(new Hit(at, cp, valid || stripper, valid ? "colour code" : stripper ? "colour-code STRIPPING regex, never drawn" : "section sign NOT followed by a colour code char, it would be drawn"));
                    } else hits.add(new Hit(at, cp, allowed.contains(cp), allowed.contains(cp) ? "verified bitmap" : "NOT in the verified bitmap set"));
                }
                k += n;
            }
            // \\uXXXX escapes
            Matcher m = ESC.matcher(l);
            while (m.find()) {
                int cp = Integer.parseInt(m.group(1), 16);
                if (cp <= 0x7F) continue;
                String at = name + ":" + (i + 1);
                if (cp == 0xA7) {
                    int after = m.end(); char next = after < l.length() ? l.charAt(after) : '\0';
                    boolean valid = VALID_CODES.indexOf(next) >= 0;
                    hits.add(new Hit(at, cp, valid, valid ? "colour code (escape)" : "section sign escape NOT followed by a colour code char"));
                } else hits.add(new Hit(at, cp, allowed.contains(cp), allowed.contains(cp) ? "verified bitmap (escape)" : "NOT in the verified bitmap set (escape)"));
            }
        }
        return hits;
    }

    public static void main(String[] a) throws IOException {
        String json = Files.readString(ROOT.resolve("docs/ui_glyphs.json"), StandardCharsets.UTF_8);
        Set<Integer> allowed = verified(json);
        check("G0 the verified set is read from docs/ui_glyphs.json and has the 64 glyphs the doc promises", allowed.size() == 64, "(" + allowed.size() + ")");
        check("G0b the Unifont fallback glyphs are NOT in the allowed set (U+2708 airplane and U+2B06 up arrow are Unifont ones)", !allowed.contains(0x2708) && !allowed.contains(0x2B06), "");

        // Self test on synthetic text: the scanner must be able to FAIL, or a green result means nothing.
        List<Hit> t1 = scan("t", "x = \"\u2605 ok\";", allowed);
        check("G1 scanner: a verified glyph (star) is accepted", t1.size() == 1 && t1.get(0).ok(), t1.toString());
        List<Hit> t2 = scan("t", "x = \"\u2708 plane\";", allowed);
        check("G2 scanner: a Unifont-only glyph (airplane) is REJECTED", t2.size() == 1 && !t2.get(0).ok(), t2.toString());
        List<Hit> t3 = scan("t", "x = \"\\u2708\";", allowed);
        check("G3 scanner: the same glyph written as a \\u escape is also REJECTED", t3.size() == 1 && !t3.get(0).ok(), t3.toString());
        List<Hit> t4 = scan("t", "x = \"\u00A7aGreen\";", allowed);
        check("G4 scanner: a section sign followed by a colour code is accepted", t4.size() == 1 && t4.get(0).ok(), t4.toString());
        List<Hit> t5 = scan("t", "x = \"\u00A7 \";", allowed);
        check("G5 scanner: a section sign followed by a space WOULD be drawn, so it is REJECTED", t5.size() == 1 && !t5.get(0).ok(), t5.toString());
        List<Hit> t5b = scan("t", "s.replaceAll(\"\u00A7.\", \"\")", allowed);
        check("G5b scanner: the exact stripping regex replaceAll(\"section-sign.\") is accepted, it is never drawn", t5b.size() == 1 && t5b.get(0).ok(), t5b.toString());
        List<Hit> t5c = scan("t", "x = \"\u00A7.\";", allowed);
        check("G5c scanner: the same \"section-sign.\" text NOT inside replaceAll is REJECTED (the exemption is narrow)", t5c.size() == 1 && !t5c.get(0).ok(), t5c.toString());
        List<Hit> t6 = scan("t", "plain ascii only", allowed);
        check("G6 scanner: plain ASCII yields no findings", t6.isEmpty(), "");

        // The real tree.
        List<Hit> all = new ArrayList<>(); int files = 0;
        for (String root : new String[]{"src/client", "src/main"}) {
            Path r = ROOT.resolve(root);
            try (Stream<Path> s = Files.walk(r)) {
                for (Path p : s.filter(Files::isRegularFile).filter(x -> x.toString().endsWith(".java") || x.toString().endsWith(".json")).sorted().collect(Collectors.toList())) {
                    files++;
                    all.addAll(scan(ROOT.relativize(p).toString().replace('\\', '/'), Files.readString(p, StandardCharsets.UTF_8), allowed));
                }
            }
        }
        check("G7 the scan actually read source (java and json files under src/client and src/main)", files > 100, "(" + files + " files)");
        long glyphs = all.stream().filter(h -> h.cp() != 0xA7).count();
        long bad = all.stream().filter(h -> !h.ok()).count();
        check("G8 the glyph characters the code can draw were found (a scan that finds none proves nothing; floor 5, the five in RunHud)", glyphs >= 5, "(" + glyphs + " glyph uses; the floor was 6 until the Character Select rewrite dropped its play marker and the hub removal dropped the bust sign, leaving the 5 in RunHud)");
        Set<String> distinct = all.stream().filter(h -> h.cp() != 0xA7).map(h -> String.format("U+%04X", h.cp())).collect(Collectors.toCollection(TreeSet::new));
        System.out.println("DISTINCT non-section glyphs in the source: " + distinct);
        for (Hit h : all) if (!h.ok()) System.out.println("  BAD " + h.where() + " " + String.format("U+%04X", h.cp()) + " " + h.why());
        check("G9 every glyph the code can draw is a VERIFIED bitmap glyph, every section sign is a real colour code", bad == 0, "(" + bad + " bad of " + all.size() + ")");
        System.out.println(fails == 0 ? "ALL PASS (" + passes + " checks)" : "SOME FAIL (" + fails + ")");
        System.exit(fails == 0 ? 0 : 1);
    }
}
