package com.solme.emberfall.client;

import com.solme.emberfall.network.HudStatePayload;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Always-on weapon and tome panel, bottom left, in the empty space beside the hotbar.
 *
 * Two rows of four cells: weapons on top, tomes below. An unlocked empty cell is a dark frame, a
 * LOCKED cell (not bought yet) is dimmer with a lock bar, an owned cell shows a short name tag and,
 * for tomes, the stack count. It draws plain fills and text only (no textures), so it needs no
 * resource pack and looks the same on any vanilla client.
 *
 * The panel scales itself down when the free space beside the hotbar is narrow, and hides itself
 * when there is no room at all, so it can never overlap the hotbar or the status bars.
 */
public final class LoadoutHud implements HudElement {
    /** Latest server state; null means "no run", draw nothing. Written on the client thread only. */
    private static volatile HudStatePayload state;

    private static final int CELLS = 4;
    private static final int GAP = 2;
    private static final int MARGIN = 4;
    private static final int PREFERRED_CELL = 30;
    private static final int MIN_CELL = 16;
    private static final int ROW_GAP = 3;
    /** Height of the ultimate meter bar at the bottom of a weapon cell. */
    private static final int METER_HEIGHT = 3;
    /** Vanilla hotbar: 182 wide, centred. Keep clear of it. */
    private static final int HOTBAR_HALF_WIDTH = 91;

    public static void set(HudStatePayload payload) {
        boolean hidden = payload.weaponSlots() == 0 && payload.tomeSlots() == 0;
        state = hidden ? null : payload;
    }

    /** Test/inspection hook: what the panel is currently told to draw. */
    public static HudStatePayload current() {
        return state;
    }

    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        HudStatePayload s = state;
        Minecraft mc = Minecraft.getInstance();
        if (s == null || mc.options.hideGui || mc.player == null) {
            return;
        }
        int screenW = g.guiWidth();
        int screenH = g.guiHeight();

        // Free width left of the hotbar, minus margins. Shrink the cell to fit, or hide if hopeless.
        int free = screenW / 2 - HOTBAR_HALF_WIDTH - MARGIN * 2;
        int cell = Math.min(PREFERRED_CELL, (free - GAP * (CELLS - 1)) / CELLS);
        if (cell < MIN_CELL) {
            return;
        }
        int panelH = cell * 2 + ROW_GAP;
        int x = MARGIN;
        int weaponY = screenH - MARGIN - panelH;
        int tomeY = weaponY + cell + ROW_GAP;

        drawWeaponRow(g, mc, s, x, weaponY, cell);
        drawTomeRow(g, mc, s, x, tomeY, cell);
    }

    private static void drawWeaponRow(GuiGraphics g, Minecraft mc, HudStatePayload s, int x, int y, int cell) {
        for (int i = 0; i < CELLS; i++) {
            int cx = x + i * (cell + GAP);
            boolean unlocked = i < s.weaponSlots();
            boolean owned = i < s.weapons().size();
            drawFrame(g, cx, y, cell, unlocked, owned ? 0xFFE0A030 : 0xFF6E6E6E);
            if (owned) {
                HudStatePayload.WeaponEntry w = s.weapons().get(i);
                drawWeaponLabel(g, mc, w, cx, y, cell);
                drawMeter(g, w.meterStep(), cx, y, cell, mc.level == null ? 0L : mc.level.getGameTime());
            } else if (!unlocked) {
                drawLock(g, cx, y, cell);
            }
        }
    }

    /**
     * The weapon's name, centred in the upper part of the cell. The weapon LEVEL is not drawn here any more: it has its own box beside the stats
     * panel at the top left ({@code RunHud}), so this cell only carries the name and the meter bar and nothing in it can overlap.
     */
    private static void drawWeaponLabel(GuiGraphics g, Minecraft mc, HudStatePayload.WeaponEntry w, int x, int y, int size) {
        drawTag(g, mc, w.name(), x, y, size, w.level() >= com.solme.emberfall.item.WeaponGrowth.MAX_LEVEL ? 0xFFFFD84A : 0xFFFFFFFF);
    }

    /**
     * The ultimate meter: a thin bar along the inside of the cell's bottom edge. Dark track, orange fill in {@code meterStep} / METER_STEPS. When
     * full it turns bright gold and breathes with the game clock, which reads as "about to fire" without any text. The ultimate fires by itself,
     * so this is a readout, not a button.
     */
    private static void drawMeter(GuiGraphics g, int meterStep, int x, int y, int size, long gameTime) {
        int left = x + 2;
        int right = x + size - 2;
        int bottom = y + size - 2;
        int top = bottom - METER_HEIGHT;
        g.fill(left, top, right, bottom, 0xFF202020);
        int steps = HudStatePayload.METER_STEPS;
        int clamped = Math.max(0, Math.min(steps, meterStep));
        if (clamped <= 0) {
            return;
        }
        int fillRight = left + (right - left) * clamped / steps;
        if (clamped >= steps) {
            // Full: 0xC0..0xFF red-channel-free gold that breathes once a second.
            int glow = 0xC0 + (int) (0x3F * (0.5 + 0.5 * Math.sin(gameTime * (Math.PI * 2.0 / 20.0))));
            g.fill(left, top, right, bottom, 0xFF000000 | (glow << 16) | (glow * 0xD8 / 0xFF << 8) | 0x4A);
        } else {
            g.fill(left, top, fillRight, bottom, 0xFFE0702A);
        }
    }

    private static void drawTomeRow(GuiGraphics g, Minecraft mc, HudStatePayload s, int x, int y, int cell) {
        List<HudStatePayload.TomeEntry> tomes = s.tomes();
        for (int i = 0; i < CELLS; i++) {
            int cx = x + i * (cell + GAP);
            boolean unlocked = i < s.tomeSlots();
            boolean owned = i < tomes.size();
            HudStatePayload.TomeEntry t = owned ? tomes.get(i) : null;
            // Stat tomes have no practical cap (maxStacks 99): never show them as maxed, and show only the count, not "7/99".
            boolean uncapped = t != null && t.maxStacks() > 20;
            boolean maxed = t != null && !uncapped && t.stacks() >= t.maxStacks();
            drawFrame(g, cx, y, cell, unlocked, owned ? (maxed ? 0xFFFFD84A : 0xFF6FA8DC) : 0xFF6E6E6E);
            if (t != null) {
                drawTag(g, mc, t.name(), cx, y, cell, 0xFFFFFFFF);
                String count = uncapped ? "x" + t.stacks() : t.stacks() + "/" + t.maxStacks();
                int w = mc.font.width(count);
                g.drawString(mc.font, count, cx + cell - w - 2, y + cell - mc.font.lineHeight - 1,
                        maxed ? 0xFFFFD84A : 0xFFCFCFCF, true);
            } else if (!unlocked) {
                drawLock(g, cx, y, cell);
            }
        }
    }

    /** Dark translucent cell with a 1 px border. Locked cells are dimmer so they read as unavailable. */
    private static void drawFrame(GuiGraphics g, int x, int y, int size, boolean unlocked, int border) {
        g.fill(x, y, x + size, y + size, unlocked ? 0xA0101010 : 0x70000000);
        g.fill(x, y, x + size, y + 1, border);
        g.fill(x, y + size - 1, x + size, y + size, border);
        g.fill(x, y, x + 1, y + size, border);
        g.fill(x + size - 1, y, x + size, y + size, border);
    }

    /** A padlock made of fills: body plus shackle, centred in the cell. */
    private static void drawLock(GuiGraphics g, int x, int y, int size) {
        int bw = Math.max(6, size / 3);
        int bh = Math.max(5, size / 4);
        int bx = x + (size - bw) / 2;
        int by = y + size / 2 - bh / 2 + 2;
        g.fill(bx, by, bx + bw, by + bh, 0xFF8A8A8A);
        int sx = bx + 1;
        int sw = bw - 2;
        int top = by - Math.max(3, bh / 2);
        g.fill(sx, top, sx + sw, top + 1, 0xFF8A8A8A);
        g.fill(sx, top, sx + 1, by, 0xFF8A8A8A);
        g.fill(sx + sw - 1, top, sx + sw, by, 0xFF8A8A8A);
    }

    /** Name tag: as many characters as fit the cell width, centred in the upper part of the cell. */
    private static void drawTag(GuiGraphics g, Minecraft mc, String name, int x, int y, int size, int colour) {
        String shown = name;
        int max = size - 4;
        while (shown.length() > 1 && mc.font.width(shown) > max) {
            shown = shown.substring(0, shown.length() - 1);
        }
        int w = mc.font.width(shown);
        g.drawString(mc.font, shown, x + (size - w) / 2, y + 3, colour, true);
    }
}
