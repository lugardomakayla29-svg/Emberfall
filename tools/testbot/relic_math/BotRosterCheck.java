import com.solme.emberfall.bot.BotRoster;
import java.util.*;
public class BotRosterCheck {
    static int fails = 0;
    static void check(String l, boolean ok, String e) { System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e); if (!ok) fails++; }
    public static void main(String[] a) {
        UUID bot1 = UUID.randomUUID(), bot2 = UUID.randomUUID(), h1 = UUID.randomUUID(), h2 = UUID.randomUUID();
        check("nobody is a bot at first", !BotRoster.isBot(bot1) && BotRoster.count() == 0, "");
        BotRoster.add(bot1); BotRoster.add(bot2); BotRoster.add(bot1);
        check("adding twice counts once", BotRoster.count() == 2, "count " + BotRoster.count());
        check("a human is not a bot, null is not a bot", !BotRoster.isBot(h1) && !BotRoster.isBot(null), "");
        List<UUID> mixed = List.of(h1, bot1, h2, bot2);
        check("humansOnly drops bots and keeps order", BotRoster.humanIds(mixed).equals(List.of(h1, h2)), "" + BotRoster.humanIds(mixed).size());
        check("an all-human list is returned whole", BotRoster.humanIds(List.of(h1, h2)).equals(List.of(h1, h2)), "");
        check("an all-bot list becomes empty", BotRoster.humanIds(List.of(bot1, bot2)).isEmpty(), "");
        check("allBots true only when every entry is a bot", BotRoster.allBots(List.of(bot1, bot2), x -> x) && !BotRoster.allBots(mixed, x -> x), "");
        check("allBots on an EMPTY list is false (an empty packet is not ours to drop)", !BotRoster.allBots(List.<UUID>of(), x -> x), "");
        check("the input list is never modified", mixed.size() == 4, "");
        BotRoster.remove(bot1);
        check("a removed bot is a human again", !BotRoster.isBot(bot1) && BotRoster.isBot(bot2) && BotRoster.count() == 1, "");
        System.out.println(fails == 0 ? "ALL PASS" : "FAILED " + fails);
    }
}
