package com.solme.emberfall.relic;

import java.util.List;

/**
 * The icon glyph for every row of every expedition menu, as PURE DATA so a check can read the very table the screens draw from (a check that read a copy
 * could pass while the screen drew something else). It follows the stats panel in {@code RunHud}: one glyph per row, in the row's own colour, drawn through
 * {@link HudLayout#markerColumn} and {@link HudLayout#markerOffset} so every label of a menu starts at the same x. The screen measures each glyph with
 * {@code font.width}; the widths below are only for the check and come from {@code docs/ui_glyphs.json}.
 *
 * Every glyph is from the 64 VERIFIED bitmap glyphs of docs/UI_GLYPHS.md. A glyph is chosen by the KIND of row, never by an item's name, because weapon,
 * upgrade, tome and relic names are open-ended data and a per-name table could not be complete.
 *
 * Tested headless, look unverified: how any of these LOOKS has not been seen.
 */
public final class MenuGlyphs {
    private MenuGlyphs() {}

    /** The last pixel row a glyph may reach: the font's text sits on row 7, so a glyph reaching row 8 or lower hangs below the line. */
    public static final int MAX_LAST_ROW = 7;

    /** One row: its meaning (a short key), the glyph drawn, and why that glyph means that. */
    public record Row(String meaning, String glyph, String why) {}

    /** A menu and its rows, in the order they are drawn. */
    public record Menu(String name, List<Row> rows) {}

    private static Row r(String meaning, String glyph, String why) {
        return new Row(meaning, glyph, why);
    }

    // ----------------------------------------------------------------------------------------------------------------------------------------
    // RULE: a glyph the stats panel already uses keeps exactly that meaning everywhere and is used for nothing else:
    // star = level, diamond = gold, envelope = chest, white diamond = silver, skull = kills. Every other row gets a glyph of its own.
    // Rows marked PROPOSAL have no glyph whose meaning is obviously right; the pick is a taste call for the owner, not a fact.
    // ----------------------------------------------------------------------------------------------------------------------------------------

    /** Shop: weapons and permanent upgrades. Chosen by kind, never by name. Prices are in Silver, so a price glyph would be the silver one. */
    public static final Menu SHOP = new Menu("ShopScreen", List.of(
            r("weapon", "\u2694", "crossed swords: a weapon row"),
            r("upgrade", "\u266F", "PROPOSAL sharp: raised a level; no verified glyph says upgrade"),
            r("silver", "\u2662", "white diamond: the shop is priced in Silver, same glyph as the stats panel")));

    /** Tome choice: the card title, Banish and Reroll. */
    public static final Menu TOME = new Menu("TomeChoiceScreen", List.of(
            r("tome", "\u270E", "pencil: a tome is written knowledge"),
            r("banish", "\u2702", "scissors: cut the offer away"),
            r("reroll", "\u2194", "left-right arrow: swap for another set")));

    /** Weapon choice: the card title. */
    public static final Menu WEAPON = new Menu("WeaponChoiceScreen", List.of(
            r("weapon", "\u2694", "crossed swords: a weapon card")));

    /** Merchant: relic cards, the gold price and the time left. */
    public static final Menu MERCHANT = new Menu("MerchantScreen", List.of(
            r("relic", "\u2665", "PROPOSAL heart suit: a relic is something prized; no verified glyph says relic"),
            r("gold", "\u2666", "diamond: prices and your balance are gold, same glyph as the stats panel"),
            r("time", "\u231B", "hourglass: the merchant leaves in N seconds")));

    /** Shrine: its three kinds (challenge, curse, greed). */
    public static final Menu SHRINE = new Menu("ShrineScreen", List.of(
            r("challenge", "\u2694", "crossed swords: a trial to fight"),
            r("curse", "\u2604", "PROPOSAL comet: a curse falling on the boss; no verified glyph says curse"),
            r("greed", "\u2663", "PROPOSAL club suit: greed; no verified glyph says greed")));

    /** Run end: the result lines, each reusing the stats panel glyph for the same stat. */
    public static final Menu RUN_END = new Menu("RunEndScreen", List.of(
            r("time", "\u231B", "hourglass: time survived"),
            r("level", "\u2605", "star: level reached, the stats panel level glyph"),
            r("kills", "\u2620", "skull: enemies defeated, the stats panel kills glyph"),
            r("gold", "\u2666", "diamond: gold collected, the stats panel gold glyph"),
            r("bosses", "\u2694", "crossed swords: bosses defeated"),
            r("silver", "\u2662", "white diamond: silver earned, the stats panel silver glyph")));

    /** Chest reveal: the tier line and the item line. */
    public static final Menu CHEST = new Menu("ChestRevealScreen", List.of(
            r("chest", "\u2709", "envelope: the chest, the stats panel chest glyph"),
            r("relic", "\u2665", "PROPOSAL heart suit: the relic that dropped, same glyph as the merchant's relic row")));

    /** Every menu, for the check. */
    public static final List<Menu> ALL = List.of(SHOP, TOME, WEAPON, MERCHANT, SHRINE, RUN_END, CHEST);

    /** The glyph for a row of a menu, or "" when the menu has no such row (the screen then draws no glyph rather than a wrong one). */
    public static String glyph(Menu menu, String meaning) {
        for (Row row : menu.rows()) {
            if (row.meaning().equals(meaning)) {
                return row.glyph();
            }
        }
        return "";
    }
}
