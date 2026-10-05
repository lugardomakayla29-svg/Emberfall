package com.solme.emberfall.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.Entity;

/**
 * One place that decides how a mob's floating name looks. A name is a per-letter gradient between two RGB colours, bold,
 * so elites, veterans and named mobs stop being plain white text. Pure text: no entity is created and nothing is
 * ticked, so it costs nothing per tick (the component is built once, when the mob spawns).
 */
public final class MobNames {
    private MobNames() {}

    /** Colour pairs (from, to) per tier. Kept here so a balance or art pass changes one file. */
    public enum Tier {
        VETERAN(0xFFD36B, 0xFF8A2B),    // amber to orange
        ELITE(0xFF6A3D, 0xC81E1E),      // ember to crimson
        CORRUPTED(0xC46BFF, 0x5B1FA8),  // violet to deep purple
        VENOM(0x9CFF5B, 0x1F8F3A),      // acid to forest (spiders, plague)
        BONE(0xF2EBD3, 0x9C9480),       // ivory to ash (necromancy)
        ARCANE(0xB8A1FF, 0x3F6BFF),     // lilac to blue (casters)
        SLIME(0xFF9AD5, 0xE0409A),      // pink (pink slime)
        LAVA(0xFFC23D, 0xE8470C);       // gold to red (fire, tiki, kraken)

        final int from, to;
        Tier(int from, int to) { this.from = from; this.to = to; }
    }

    /** The gradient as a component; {@code text} is kept exactly, only its colours change. */
    public static MutableComponent gradient(String text, int from, int to) {
        MutableComponent out = Component.empty();
        int n = text.codePointCount(0, text.length());
        if (n == 0) return out;
        int i = 0;
        for (int off = 0; off < text.length(); ) {
            int cp = text.codePointAt(off);
            off += Character.charCount(cp);
            double t = n == 1 ? 0.0 : (double) i / (n - 1);
            out.append(Component.literal(new String(Character.toChars(cp)))
                    .withStyle(Style.EMPTY.withColor(lerp(from, to, t)).withBold(true)));
            i++;
        }
        return out;
    }

    public static MutableComponent of(String text, Tier tier) { return gradient(text, tier.from, tier.to); }

    /** Name a mob and show the name. */
    public static void apply(Entity e, String text, Tier tier) {
        e.setCustomName(of(text, tier));
        e.setCustomNameVisible(true);
    }

    static int lerp(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 255) + ((((b >> 16) & 255) - ((a >> 16) & 255)) * t));
        int g = (int) Math.round(((a >> 8) & 255) + ((((b >> 8) & 255) - ((a >> 8) & 255)) * t));
        int bl = (int) Math.round((a & 255) + (((b & 255) - (a & 255)) * t));
        return (r << 16) | (g << 8) | bl;
    }
}
