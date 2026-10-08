package com.solme.emberfall.client;

import com.solme.emberfall.network.HudStatePayload;
import com.solme.emberfall.network.RunHudPayload;
import com.solme.emberfall.relic.HudLayout;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The run overlay: a timer plate at the top middle and a resource column at the top right.
 *
 * Plain fills and text only (no textures), so it needs no resource pack. The clock is counted here on the
 * client from the last synced start point, so the server sends nothing per second; a periodic packet
 * corrects any drift. Nothing draws outside a run or with the GUI hidden (F1).
 */
public final class RunHud implements HudElement {
    /** Latest server state; null = not in a run. Written on the client thread only. */
    private static volatile RunHudPayload state;
    /** Client wall-clock millis at which {@code state.elapsedSeconds()} was received. */
    private static volatile long receivedAtMillis;

    private static final int PANEL_BG = 0xB0101010;
    private static final int PANEL_EDGE = 0xFF3A3A3A;
    private static final int GOLD = 0xFFFFD84A;
    private static final int SILVER = 0xFFD5DCE4;
    private static final int XP_GREEN = 0xFF7CE04A;
    private static final int KILL_RED = 0xFFE0563C;
    private static final int LABEL = 0xFFB8B8B8;

    public static void set(RunHudPayload payload) {
        if (!payload.active()) {
            state = null;
            return;
        }
        receivedAtMillis = System.currentTimeMillis();
        state = payload;
    }

    public static RunHudPayload current() {
        return state;
    }

    /** Seconds on the clock right now: the synced value plus the time that passed since it arrived. */
    static long clockSeconds(RunHudPayload s, long nowMillis, long receivedMillis) {
        return s.elapsedSeconds() + Math.max(0L, (nowMillis - receivedMillis) / 1000L);
    }

    /** "MM:SS", or "H:MM:SS" once past an hour. */
    static String formatClock(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long sec = seconds % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%02d:%02d", m, sec);
    }

    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        RunHudPayload s = state;
        Minecraft mc = Minecraft.getInstance();
        if (s == null || mc.options.hideGui || mc.player == null) {
            return;
        }
        HudLayout.Box timer = drawTimer(g, mc, s);
        HudLayout.Box stats = drawCounters(g, mc, s);
        drawWeaponBox(g, mc, stats, timer);
        drawRelics(g, mc, s, stats.bottom() + HudLayout.GAP);
    }

    private static HudLayout.Box drawTimer(GuiGraphics g, Minecraft mc, RunHudPayload s) {
        String text = formatClock(clockSeconds(s, System.currentTimeMillis(), receivedAtMillis));
        float scale = 1.5F;
        int textW = Math.round(mc.font.width(text) * scale);
        int textH = Math.round(mc.font.lineHeight * scale);
        int padX = 8;
        int padY = 4;
        int w = textW + padX * 2;
        int h = textH + padY * 2;
        int x = g.guiWidth() / 2 - w / 2;
        int y = 6;
        panel(g, x, y, w, h);
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x + padX, y + padY + 0.5F);
        pose.scale(scale, scale);
        g.drawString(mc.font, text, 0, 0, 0xFFFFFFFF, true);
        pose.popMatrix();
        return new HudLayout.Box(x, y, w, h);
    }

    /** Draws the resource column and returns its box; the weapon box sits to its right and the relic list under it. */
    private static HudLayout.Box drawCounters(GuiGraphics g, Minecraft mc, RunHudPayload s) {
        boolean canAfford = s.gold() >= s.chestPrice();
        String[] labels = {"LV " + s.level(), "Gold", "Chest", "Silver", "Kills"};
        String[] values = {s.xpPercent() + "%", Long.toString(s.gold()), Integer.toString(s.chestPrice()), Long.toString(s.silver()), Long.toString(s.kills())};
        // The chest row is gold when the next chest is affordable and red when it is not, so it reads as "you can open one".
        int[] colours = {XP_GREEN, GOLD, canAfford ? GOLD : KILL_RED, SILVER, KILL_RED};
        int rowH = mc.font.lineHeight + 4;
        int labelW = 0;
        int valueW = 0;
        for (int i = 0; i < labels.length; i++) {
            labelW = Math.max(labelW, mc.font.width(labels[i]));
            valueW = Math.max(valueW, mc.font.width(values[i]));
        }
        int padding = 5;
        int dot = 5;
        int w = padding + dot + 4 + labelW + 8 + valueW + padding;
        int h = rowH * labels.length + padding * 2 - 4 + 2;
        // Top LEFT: the top right is where vanilla draws the potion effect icons, which this panel used to cover.
        int x = 6;
        int y = 6;
        panel(g, x, y, w, h);
        for (int i = 0; i < labels.length; i++) {
            int ry = y + padding + i * rowH;
            // A small coloured square marks each currency, so the rows read at a glance.
            g.fill(x + padding, ry + 2, x + padding + dot, ry + 2 + dot, colours[i]);
            g.drawString(mc.font, labels[i], x + padding + dot + 4, ry + 1, LABEL, true);
            g.drawString(mc.font, values[i], x + w - padding - mc.font.width(values[i]), ry + 1, colours[i], true);
        }
        // XP progress bar under the level row's neighbours: a thin bar across the panel bottom.
        int barY = y + h - 3;
        g.fill(x + padding, barY, x + w - padding, barY + 1, 0xFF303030);
        int filled = (int) ((w - padding * 2) * (s.xpPercent() / 100.0));
        g.fill(x + padding, barY, x + padding + filled, barY + 1, XP_GREEN);
        return new HudLayout.Box(x, y, w, h);
    }

    /**
     * The weapon levels, in a box of their own right of the stats panel: a gold square, the weapon name and "Lv N". Plain fills and text like the rest
     * of the HUD. At most {@link HudLayout#MAX_WEAPON_ROWS} rows, and it is skipped (not drawn over anything) when there is no room before the timer plate.
     * The numbers come from the loadout state the bottom-left panel already receives, so there is no new packet.
     */
    private static void drawWeaponBox(GuiGraphics g, Minecraft mc, HudLayout.Box stats, HudLayout.Box timer) {
        HudStatePayload hud = LoadoutHud.current();
        if (hud == null || hud.weapons().isEmpty()) {
            return;
        }
        int rows = Math.min(hud.weapons().size(), HudLayout.MAX_WEAPON_ROWS);
        int rowH = mc.font.lineHeight + 2;
        int padding = 5;
        int dot = 5;
        String[] names = new String[rows];
        String[] badges = new String[rows];
        int nameW = mc.font.width("Weapons");
        int badgeW = 0;
        for (int i = 0; i < rows; i++) {
            HudStatePayload.WeaponEntry w = hud.weapons().get(i);
            names[i] = w.name();
            badges[i] = "Lv" + w.level();
            nameW = Math.max(nameW, mc.font.width(names[i]));
            badgeW = Math.max(badgeW, mc.font.width(badges[i]));
        }
        int widest = dot + 4 + nameW + 8 + badgeW;
        HudLayout.Box box = HudLayout.weaponBox(stats, widest, rows, rowH, padding);
        if (!HudLayout.weaponBoxFits(stats, box, timer) || box.right() > g.guiWidth() - HudLayout.MARGIN) {
            return;
        }
        panel(g, box.x(), box.y(), box.w(), box.h());
        g.drawString(mc.font, "Weapons", box.x() + padding, box.y() + padding + 1, LABEL, true);
        for (int i = 0; i < rows; i++) {
            HudStatePayload.WeaponEntry w = hud.weapons().get(i);
            boolean top = w.level() >= com.solme.emberfall.item.WeaponGrowth.MAX_LEVEL;
            int colour = top ? GOLD : SILVER;
            int ry = box.y() + padding + rowH * (i + 1);
            g.fill(box.x() + padding, ry + 2, box.x() + padding + dot, ry + 2 + dot, colour);
            g.drawString(mc.font, names[i], box.x() + padding + dot + 4, ry + 1, 0xFFFFFFFF, true);
            g.drawString(mc.font, badges[i], box.right() - padding - mc.font.width(badges[i]), ry + 1, colour, true);
        }
    }

    /** Most relic rows drawn before the rest fold into "+N more"; keeps the list clear of the hotbar on small screens. */
    static final int MAX_RELIC_ROWS = 12;

    /**
     * Owned relics, top left under the counters: a rarity-coloured square, the name in the same colour, and "xN" for
     * stacks. Plain fills and text like the rest of the HUD. Rows come in pool order, which is common to legendary.
     */
    private static void drawRelics(GuiGraphics g, Minecraft mc, RunHudPayload s, int top) {
        int[] pairs = s.relics();
        int count = pairs.length / 2;
        if (count == 0) {
            return;
        }
        var pool = com.solme.emberfall.relic.RelicPool.all();
        int rowH = mc.font.lineHeight + 2;
        // The list stops above the loadout panel at the bottom left, so a long relic list cannot run into it on a small GUI.
        int rows = HudLayout.relicRowsFit(count, MAX_RELIC_ROWS, top, HudLayout.loadoutTop(g.guiWidth(), g.guiHeight()), rowH, 5);
        int hidden = count - rows;
        String[] names = new String[rows];
        String[] stacks = new String[rows];
        int[] rgb = new int[rows];
        int nameW = mc.font.width("Relics");
        int stackW = 0;
        for (int i = 0; i < rows; i++) {
            int idx = pairs[i * 2];
            if (idx < 0 || idx >= pool.size()) {
                names[i] = "?";
                rgb[i] = LABEL;
            } else {
                var relic = pool.get(idx);
                names[i] = relic.name();
                rgb[i] = 0xFF000000 | relic.rarity().rgb();
            }
            stacks[i] = pairs[i * 2 + 1] > 1 ? "x" + pairs[i * 2 + 1] : "";
            nameW = Math.max(nameW, mc.font.width(names[i]));
            stackW = Math.max(stackW, mc.font.width(stacks[i]));
        }
        String more = hidden > 0 ? "+" + hidden + " more" : "";
        nameW = Math.max(nameW, mc.font.width(more));
        int padding = 5;
        int dot = 5;
        int w = padding + dot + 4 + nameW + (stackW > 0 ? 8 + stackW : 0) + padding;
        int h = HudLayout.relicBoxHeight(count, rows, rowH, padding);
        int x = HudLayout.MARGIN;
        panel(g, x, top, w, h);
        g.drawString(mc.font, "Relics", x + padding, top + padding, LABEL, true);
        for (int i = 0; i < rows; i++) {
            int ry = top + padding + rowH * (i + 1);
            g.fill(x + padding, ry + 2, x + padding + dot, ry + 2 + dot, rgb[i]);
            g.drawString(mc.font, names[i], x + padding + dot + 4, ry + 1, rgb[i], true);
            if (!stacks[i].isEmpty()) {
                g.drawString(mc.font, stacks[i], x + w - padding - mc.font.width(stacks[i]), ry + 1, 0xFFFFFFFF, true);
            }
        }
        if (hidden > 0) {
            g.drawString(mc.font, more, x + padding, top + padding + rowH * (rows + 1), LABEL, true);
        }
    }

    /** Dark translucent plate with a thin edge, matching the loadout panel. */
    private static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL_BG);
        g.fill(x, y, x + w, y + 1, PANEL_EDGE);
        g.fill(x, y + h - 1, x + w, y + h, PANEL_EDGE);
        g.fill(x, y, x + 1, y + h, PANEL_EDGE);
        g.fill(x + w - 1, y, x + w, y + h, PANEL_EDGE);
    }
}
