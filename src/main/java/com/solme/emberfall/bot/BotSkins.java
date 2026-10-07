package com.solme.emberfall.bot;

/**
 * Which of the bundled skins an EmberTester wears. Pure and deterministic: the same name always gets the same skin, so a bot looks
 * identical on every client and across restarts. Skins are shipped inside the mod (assets/emberfall/textures/entity/bot/skinN.png)
 * because a vanilla client only downloads skins from Mojang's own domains, so a hosted skin file can never work.
 */
public final class BotSkins {
    /** How many skin files ship in the mod. Keep in step with the PNGs in textures/entity/bot. */
    public static final int COUNT = 10;

    private BotSkins() {}

    /** 1-based skin number for a bot name. */
    public static int indexFor(String name) {
        if (name == null || name.isEmpty()) {
            return 1;
        }
        return Math.floorMod(name.hashCode(), COUNT) + 1;
    }

    /** Path under the mod's assets namespace. */
    public static String pathFor(String name) {
        return "textures/entity/bot/skin" + indexFor(name) + ".png";
    }
}
