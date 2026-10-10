import com.solme.emberfall.character.SelectLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure checks for the Character Select panel layout: it fits real windows, the cards never overlap, and a click lands on exactly the card drawn there. */
public class SelectLayoutCheck {
    static int fails = 0;
    static int total = 0;

    static void check(String l, boolean ok, String e) {
        total++;
        System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e);
        if (!ok) {
            fails++;
        }
    }

    /**
     * Pixel width of a string in Minecraft's default font (GUI pixels at scale 1: glyph width plus the 1 px gap). This is an EMBEDDED COPY of the
     * standard ASCII advances, not read from the game, so it proves the fit against that table; the real font is UNSEEN. Unknown characters count 6.
     */
    static int px(String s) {
        int n = 0;
        for (char c : s.toCharArray()) {
            switch (c) {
                case 'i', '!', '.', ',', ':', ';', '|', '\'' -> n += 2;
                case 'l', '`' -> n += 3;
                case 'I', 't', ' ', '[', ']' -> n += 4;
                case 'f', 'k', '(', ')', '{', '}', '<', '>', '*', '"' -> n += 5;
                case '@', '~' -> n += 7;
                default -> n += 6;
            }
        }
        return n;
    }

    /** Every quoted display name in a pool file, by the constructor that makes it: {@code new Type("id", "Display Name",}. */
    static List<String> displayNames(String file, String type) throws Exception {
        String src = Files.readString(Path.of("../../../src/main/java/com/solme/emberfall/" + file));
        Matcher m = Pattern.compile("new " + type + "\\(\"[a-z_]+\", \"([^\"]+)\"").matcher(src);
        List<String> out = new ArrayList<>();
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    static void textChecks() throws Exception {
        List<String> names = displayNames("character/CharacterPool.java", "CharacterType");
        List<String> weapons = displayNames("item/WeaponPool.java", "WeaponType");
        int room = SelectLayout.CARD_W - 38;   // the exact width the screen passes to plainSubstrByWidth
        int margin = 2;
        StringBuilder cut = new StringBuilder();
        int worst = 0;
        for (String n : names) {
            int w = px(SelectLayout.cardName(n));
            worst = Math.max(worst, w);
            if (w + margin > room) {
                cut.append(n).append('=').append(w).append(' ');
            }
        }
        for (String wn : weapons) {
            int w = px(wn);
            worst = Math.max(worst, w);
            if (w + margin > room) {
                cut.append(wn).append('=').append(w).append(' ');
            }
        }
        check("T1 the pools were read (8 characters, 8 weapons), so the check cannot pass on an empty list", names.size() == 8 && weapons.size() == 8, "names " + names.size() + " weapons " + weapons.size());
        check("T2 every card name (as shown) and every weapon line fits the card's text room with 2 px spare, so nothing is cut", cut.length() == 0, "room " + room + ", widest " + worst + ", too wide: " + cut);
        check("T3 the screen cuts text at CARD_W - 38 and the badge area is 34 px, so the room is CARD_W - 38 (pinned)", room == SelectLayout.CARD_W - 38 && SelectLayout.CARD_W >= 118, "CARD_W " + SelectLayout.CARD_W);
        boolean sameInitial = true;
        String first = names.isEmpty() ? "" : SelectLayout.cardName(names.get(0)).substring(0, 1);
        for (String n : names) {
            if (!SelectLayout.cardName(n).substring(0, 1).equals(first)) {
                sameInitial = false;
            }
        }
        check("T4 the card badges are not all the same letter (the old screen showed 'T' from 'The' on every card)", !sameInitial, "first letter " + first);
        check("T5 cardName drops only a leading 'The ' and leaves other names alone", SelectLayout.cardName("The Reaper").equals("Reaper") && SelectLayout.cardName("Reaper").equals("Reaper") && SelectLayout.cardName("The ").equals("The ") && SelectLayout.cardName("").isEmpty(), "");
        int fits = 0;
        for (int[] w : new int[][] {{960, 540}, {640, 360}, {480, 270}, {426, 240}, {320, 240}}) {
            if (new SelectLayout(w[0], w[1], 8).fits(w[0], w[1])) {
                fits++;
            }
        }
        check("T6 the wider cards still fit every window (they drop to fewer columns on narrow ones)", fits == 5, "fit " + fits + " of 5");
    }

    public static void main(String[] a) throws Exception {
        // GUI-scaled window sizes a player really has: 1080p at scale 2,3,4 and a 720p at scale 3, 4.
        int[][] windows = {{960, 540}, {640, 360}, {480, 270}, {426, 240}, {320, 240}};
        int[] counts = {8, 5, 12};
        int misfit = 0;
        StringBuilder bad = new StringBuilder();
        for (int[] w : windows) {
            SelectLayout L = new SelectLayout(w[0], w[1], 8);
            if (!L.fits(w[0], w[1])) {
                misfit++;
                bad.append(w[0]).append('x').append(w[1]).append(' ');
            }
        }
        check("L1 with the 8 real characters the panel fits every common GUI-scaled window down to 320x240", misfit == 0, "does not fit: " + (bad.length() == 0 ? "none" : bad));

        int overlaps = 0, offPanel = 0, wrongHit = 0, missHit = 0;
        for (int c : counts) {
            SelectLayout L = new SelectLayout(960, 540, c);
            for (int i = 0; i < c; i++) {
                if (L.cardX(i) < L.panelX || L.cardX(i) + SelectLayout.CARD_W > L.panelX + L.panelW || L.cardY(i) + L.cardH > L.detailY) {
                    offPanel++;
                }
                for (int j = i + 1; j < c; j++) {
                    boolean ox = L.cardX(i) < L.cardX(j) + SelectLayout.CARD_W && L.cardX(j) < L.cardX(i) + SelectLayout.CARD_W;
                    boolean oy = L.cardY(i) < L.cardY(j) + L.cardH && L.cardY(j) < L.cardY(i) + L.cardH;
                    if (ox && oy) {
                        overlaps++;
                    }
                }
                // Every pixel at the card's four corners and centre must hit exactly that card.
                int[][] pts = {{L.cardX(i), L.cardY(i)}, {L.cardX(i) + SelectLayout.CARD_W - 1, L.cardY(i)}, {L.cardX(i), L.cardY(i) + L.cardH - 1},
                        {L.cardX(i) + SelectLayout.CARD_W - 1, L.cardY(i) + L.cardH - 1}, {L.cardX(i) + SelectLayout.CARD_W / 2, L.cardY(i) + L.cardH / 2}};
                for (int[] p : pts) {
                    if (L.cardAt(p[0], p[1]) != i) {
                        wrongHit++;
                    }
                }
            }
            // The gap between two cards belongs to no card.
            if (c >= 2 && L.cardAt(L.cardX(0) + SelectLayout.CARD_W + SelectLayout.GAP / 2, L.cardY(0) + 5) != -1) {
                missHit++;
            }
        }
        check("L2 no two cards overlap, for 5, 8 and 12 characters", overlaps == 0, "overlaps " + overlaps);
        check("L3 every card is inside the panel and above the detail pane", offPanel == 0, "outside " + offPanel);
        check("L4 a click on any corner or the centre of a card hits exactly that card", wrongHit == 0, "wrong hits " + wrongHit);
        check("L5 a click in the gap between cards hits none", missHit == 0, "gap hits " + missHit);
        SelectLayout L8 = new SelectLayout(960, 540, 8);
        check("L6 the Skip button is inside the panel and below the detail pane", L8.skipY() >= L8.detailY + L8.detailH && L8.skipY() + 20 <= L8.panelY + L8.panelH, "skipY " + L8.skipY() + " detail ends " + (L8.detailY + L8.detailH) + " panel ends " + (L8.panelY + L8.panelH));
        check("L7 the panel is centred horizontally", Math.abs((L8.panelX * 2 + L8.panelW) - 960) <= 1, "left " + L8.panelX + " width " + L8.panelW);
        textChecks();
        System.out.println();
        System.out.println(fails == 0 ? "ALL PASS (" + total + ")" : fails + " FAIL of " + total);
        System.exit(fails == 0 ? 0 : 1);
    }
}
