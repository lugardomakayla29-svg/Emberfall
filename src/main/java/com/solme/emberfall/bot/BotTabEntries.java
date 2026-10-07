package com.solme.emberfall.bot;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Pure rule for what a human's client is told about an EmberTester in the tab-list packet.
 *
 * Why this exists: the client builds a remote player entity ONLY if it already has a PlayerInfo for that id
 * (ClientPacketListener.createEntityFromPacket logs "Server attempted to add player prior to sending player info" and returns null
 * otherwise). The old mixin cancelled every bot entry, so the server knew the bot but no client ever drew it. The fix is to send
 * the entry with {@code listed = false}: the client keeps a PlayerInfo (so it draws the model and its skin) while the tab list,
 * which reads getListedOnlinePlayers(), never shows it.
 *
 * This class holds only the decision, with no Minecraft types, so a plain-Java check can prove it.
 */
public final class BotTabEntries {
    private BotTabEntries() {}

    /** What to do with one entry of an info-update packet that a human is about to receive. */
    public enum Verdict { KEEP, KEEP_UNLISTED }

    /** A bot entry is kept but unlisted; every other entry is untouched. */
    public static Verdict verdict(boolean isBot) {
        return isBot ? Verdict.KEEP_UNLISTED : Verdict.KEEP;
    }

    /**
     * Rewrites a list of entries: a bot entry is replaced by {@code unlist.apply(entry)}, a human entry is kept as is.
     * Returns the SAME list instance when no bot is present, so the common case allocates nothing.
     */
    public static <E> List<E> rewrite(List<E> entries, Predicate<E> isBot, Function<E, E> unlist) {
        boolean any = false;
        for (E e : entries) {
            if (isBot.test(e)) {
                any = true;
                break;
            }
        }
        if (!any) {
            return entries;
        }
        List<E> out = new ArrayList<>(entries.size());
        for (E e : entries) {
            out.add(isBot.test(e) ? unlist.apply(e) : e);
        }
        return out;
    }

    /** True when the id belongs to a registered bot. Thin wrapper so callers read clearly. */
    public static boolean isBotId(UUID id) {
        return BotRoster.isBot(id);
    }
}
