import com.solme.emberfall.bot.BotEggRules;
import com.solme.emberfall.bot.BotEggRules.Verdict;
import com.solme.emberfall.world.PartyScaling;
import java.util.*;

public class BotEggRulesCheck {
    static int fails = 0;
    static void check(String l, boolean ok, String e) { System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e); if (!ok) fails++; }
    static final long F = 300;
    public static void main(String[] a) {
        check("op, in run, early, room, free name -> OK", BotEggRules.decide(true, true, 40, F, 1, false) == Verdict.OK, "");
        check("non-operator is refused first, whatever else is true", BotEggRules.decide(false, true, 40, F, 1, false) == Verdict.NOT_OPERATOR, "");
        check("outside a run is refused", BotEggRules.decide(true, false, 0, F, 0, false) == Verdict.NO_RUN, "");
        check("one tick before the freeze is still OK", BotEggRules.decide(true, true, F - 1, F, 1, false) == Verdict.OK, "");
        check("exactly at the freeze is refused (the director has counted)", BotEggRules.decide(true, true, F, F, 1, false) == Verdict.PARTY_ALREADY_FROZEN, "");
        check("long after the freeze is refused", BotEggRules.decide(true, true, 6000, F, 1, false) == Verdict.PARTY_ALREADY_FROZEN, "");
        check("9 bodies still has room", BotEggRules.decide(true, true, 10, F, 9, false) == Verdict.OK, "");
        check("10 bodies is full", BotEggRules.decide(true, true, 10, F, 10, false) == Verdict.PARTY_FULL, "");
        check("a name already online is refused", BotEggRules.decide(true, true, 10, F, 2, true) == Verdict.NAME_TAKEN, "");
        check("MAX_PARTY equals PartyScaling.MAX_PARTY (they must not drift)", BotEggRules.MAX_PARTY == PartyScaling.MAX_PARTY, BotEggRules.MAX_PARTY + " vs " + PartyScaling.MAX_PARTY);
        check("every refusal has a sentence, OK has none", Arrays.stream(Verdict.values()).allMatch(v -> (v == Verdict.OK) == (BotEggRules.message(v, F) == null)), "");
        check("the late-join sentence says how many seconds", BotEggRules.message(Verdict.PARTY_ALREADY_FROZEN, F).contains("15 seconds"), BotEggRules.message(Verdict.PARTY_ALREADY_FROZEN, F));
        check("first free name", "EmberTester1".equals(BotEggRules.nextName(Set.of())), "");
        check("skips taken names", "EmberTester3".equals(BotEggRules.nextName(Set.of("EmberTester1", "EmberTester2"))), "");
        check("fills a gap", "EmberTester2".equals(BotEggRules.nextName(Set.of("EmberTester1", "EmberTester3"))), "");
        Set<String> all = new HashSet<>(); for (int i = 1; i <= 99; i++) all.add("EmberTester" + i);
        check("all 99 taken -> null, not a crash", BotEggRules.nextName(all) == null, "");
        // MUTATION: re-implement the rule with the classic off-by-one ('>' instead of '>='). Our boundary assertion must DISAGREE with it,
        // otherwise that assertion could not tell a correct rule from the buggy one.
        java.util.function.BiFunction<Long, Long, Boolean> mutantFrozen = (elapsed, freeze) -> elapsed > freeze;
        boolean realRefusesAtBoundary = BotEggRules.decide(true, true, F, F, 1, false) == Verdict.PARTY_ALREADY_FROZEN;
        boolean mutantRefusesAtBoundary = mutantFrozen.apply(F, F);
        check("MUTATION: an off-by-one rule is caught by the boundary assertion", realRefusesAtBoundary && !mutantRefusesAtBoundary, "real=" + realRefusesAtBoundary + " mutant=" + mutantRefusesAtBoundary);
        System.out.println(fails == 0 ? "ALL PASS" : "FAILED " + fails);
        if (fails != 0) System.exit(1);
    }
}
