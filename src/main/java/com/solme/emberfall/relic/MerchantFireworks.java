package com.solme.emberfall.relic;

/**
 * The Testificate's leaving fireworks: eight silly designs, as PURE data, so the variety is provable without a server.
 * A design is one or two explosions. {@code shape} is the vanilla firework shape id (0 small ball, 1 large ball, 2 star,
 * 3 creeper, 4 burst). Colours are RGB. The chooser never repeats the design used last.
 */
public final class MerchantFireworks {
    private MerchantFireworks() {}

    public static final int SMALL_BALL = 0, LARGE_BALL = 1, STAR = 2, CREEPER = 3, BURST = 4;

    /** One explosion of a design. */
    public record Blast(int shape, int[] colors, int[] fade, boolean trail, boolean twinkle) {}

    /** A named design: what the player sees when the Testificate goes. */
    public record Design(String name, Blast[] blasts) {}

    private static Blast b(int shape, int[] c, int[] f, boolean trail, boolean twinkle) { return new Blast(shape, c, f, trail, twinkle); }

    public static final Design[] DESIGNS = {
            new Design("Confetti Pop", new Blast[]{b(LARGE_BALL, new int[]{0xFF5555, 0xFFFF55, 0x55FF55, 0x55FFFF, 0xFF55FF}, new int[]{0xFFFFFF}, false, true)}),
            new Design("Golden Star", new Blast[]{b(STAR, new int[]{0xFFC247, 0xFFE999}, new int[]{0xFF8800}, true, true)}),
            new Design("Creeper Salute", new Blast[]{b(CREEPER, new int[]{0x55FF55, 0x00AA00}, new int[]{0x003300}, false, false)}),
            new Design("Double Bang", new Blast[]{b(SMALL_BALL, new int[]{0xFF4444}, new int[]{0xFFAAAA}, false, false), b(LARGE_BALL, new int[]{0x4444FF}, new int[]{0xAAAAFF}, true, false)}),
            new Design("Sparkler Burst", new Blast[]{b(BURST, new int[]{0xFFFFFF, 0xFFE999}, new int[]{0xFFC247}, true, true)}),
            new Design("Rainbow Ring", new Blast[]{b(LARGE_BALL, new int[]{0xFF0000, 0xFF8800, 0xFFFF00, 0x00FF00, 0x0088FF, 0x8800FF}, new int[]{0xFFFFFF}, true, false)}),
            new Design("Pink Puff", new Blast[]{b(SMALL_BALL, new int[]{0xFF88CC, 0xFFBBEE}, new int[]{0xFFFFFF}, false, true), b(STAR, new int[]{0xFF2288}, new int[]{0xFFBBEE}, false, false)}),
            new Design("Emerald Finale", new Blast[]{b(STAR, new int[]{0x17DD62, 0x9EFFC2}, new int[]{0x006633}, true, true), b(BURST, new int[]{0xFFFFFF}, new int[]{0x17DD62}, false, true)}),
    };

    /** A random design that is not {@code last} (pass -1 for none). */
    public static int choose(int last, java.util.function.DoubleSupplier roll) {
        int n = DESIGNS.length;
        int pick = Math.min(n - 1, (int) (roll.getAsDouble() * (n - 1)));
        return last >= 0 && pick >= last ? pick + 1 : pick;
    }
}
