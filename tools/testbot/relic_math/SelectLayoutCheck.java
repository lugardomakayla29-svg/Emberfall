import com.solme.emberfall.character.SelectLayout;

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

    public static void main(String[] a) {
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
        System.out.println();
        System.out.println(fails == 0 ? "ALL PASS (" + total + ")" : fails + " FAIL of " + total);
        System.exit(fails == 0 ? 0 : 1);
    }
}
