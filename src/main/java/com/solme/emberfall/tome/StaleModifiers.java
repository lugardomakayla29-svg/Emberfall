package com.solme.emberfall.tome;

/**
 * Which attribute-modifier ids belong to ONE RUN and so must not outlive it. Pure string rule, no Minecraft types, so it is proven without a server.
 *
 * Why this exists: a permanent AttributeModifier is saved in the player file, but the bookkeeping that removes it ({@link PlayerBuild}'s owned map and
 * cleanup actions) lives in memory only. If the server stops, crashes, or the player is dropped before the run's clean-up has run, the modifier survives and
 * the bookkeeping does not. The next run then starts its stack index at 1, builds the same id, and addPermanentModifier throws "Modifier is already applied
 * on this attribute!" inside the server tick loop, which crashes the server (seen live in TomeChoiceManager.tickAll). A stale synergy bonus would also
 * silently carry into the next run as a free buff.
 *
 * Only ids that are run-scoped are listed. Meta-progression ("shop_upgrade_...") is deliberately NOT here: it is bought once and must persist.
 */
public final class StaleModifiers {
    private StaleModifiers() {}

    /** Path prefixes (the part after "emberfall:") of modifiers a run adds to a PLAYER and removes again when it ends. */
    private static final String[] RUN_SCOPED_PREFIXES = {"tome_", "synergy_", "shrine_curse_"};

    /** True if a modifier with this namespace and path is added by a run and must be removed when no run is active. */
    public static boolean isRunScoped(String namespace, String path) {
        if (!"emberfall".equals(namespace) || path == null) {
            return false;
        }
        for (String prefix : RUN_SCOPED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
