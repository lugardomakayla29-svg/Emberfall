import com.solme.emberfall.bot.BotTabEntries;
import java.util.*;
import java.util.function.*;

/** Pure check of the rule that decides what a human's client is told about an EmberTester (see BotTabEntries). */
public class BotTabEntriesCheck {
    static int fails = 0;
    static void check(String l, boolean ok, String e) { System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e); if (!ok) fails++; }

    /** A stand-in for the packet entry: just an id and the listed flag. */
    record E(String id, boolean listed) {}

    public static void main(String[] a) {
        Set<String> bots = Set.of("botA", "botB");
        Predicate<E> isBot = e -> bots.contains(e.id());
        Function<E, E> unlist = e -> new E(e.id(), false);

        // A human-only packet comes back as the SAME instance: zero allocation in the common case.
        List<E> humans = List.of(new E("h1", true), new E("h2", true));
        check("human-only packet is returned untouched (same instance)", BotTabEntries.rewrite(humans, isBot, unlist) == humans, "");

        // A bot entry is KEPT (the client needs it to draw the model) but unlisted.
        List<E> in = List.of(new E("h1", true), new E("botA", true), new E("h2", true));
        List<E> out = BotTabEntries.rewrite(in, isBot, unlist);
        check("the bot entry is still present (client needs a PlayerInfo)", out.stream().anyMatch(e -> e.id().equals("botA")), "size " + out.size());
        check("the bot entry is unlisted", out.stream().filter(e -> e.id().equals("botA")).allMatch(e -> !e.listed()), "");
        check("human entries stay listed", out.stream().filter(e -> e.id().startsWith("h")).allMatch(E::listed), "");
        check("order and size are preserved", out.size() == 3 && out.get(0).id().equals("h1") && out.get(1).id().equals("botA") && out.get(2).id().equals("h2"), "");
        check("the input list is not modified", in.get(1).listed(), "");

        // An all-bot packet is NOT dropped any more (old behaviour cancelled it, which hid the bot completely).
        List<E> allBots = List.of(new E("botA", true), new E("botB", true));
        List<E> outBots = BotTabEntries.rewrite(allBots, isBot, unlist);
        check("an all-bot packet is kept, not dropped", outBots.size() == 2, "size " + outBots.size());
        check("an all-bot packet is entirely unlisted", outBots.stream().noneMatch(E::listed), "");

        // Empty list is handled.
        check("an empty packet stays empty", BotTabEntries.rewrite(List.<E>of(), isBot, unlist).isEmpty(), "");

        // The verdict helper.
        check("verdict: bot -> keep unlisted", BotTabEntries.verdict(true) == BotTabEntries.Verdict.KEEP_UNLISTED, "");
        check("verdict: human -> keep", BotTabEntries.verdict(false) == BotTabEntries.Verdict.KEEP, "");

        // MUTATION: the OLD behaviour (drop bot entries) must be caught by the same assertions.
        List<E> dropped = new ArrayList<>(in); dropped.removeIf(isBot);
        check("MUTATION: dropping the bot entry is detected (bot missing)", dropped.stream().noneMatch(e -> e.id().equals("botA")), "");
        // A broken unlist (forgets to clear the flag) must make the SAME "is unlisted" assertion fail, proving that assertion has teeth.
        Function<E, E> brokenUnlist = e -> e;
        List<E> brokenOut = BotTabEntries.rewrite(in, isBot, brokenUnlist);
        boolean brokenLooksUnlisted = brokenOut.stream().filter(e -> e.id().equals("botA")).allMatch(e -> !e.listed());
        check("MUTATION: an unlist that forgets the flag is caught", !brokenLooksUnlisted, "");

        System.out.println(fails == 0 ? "ALL PASS" : "FAILED " + fails);
        if (fails != 0) System.exit(1);
    }
}
