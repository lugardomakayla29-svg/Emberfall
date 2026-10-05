import com.solme.emberfall.bot.BotChoices;
import com.solme.emberfall.bot.BotChoices.*;
import java.util.*;
public class BotChoicesCheck {
    static int fails = 0;
    static void check(String l, boolean ok, String e) { System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e); if (!ok) fails++; }
    static TomeOffer t(String id, boolean passive, String... tags) { return new TomeOffer(id, new HashSet<>(Arrays.asList(tags)), passive); }
    public static void main(String[] a) {
        var any = (java.util.function.Predicate<String>) id -> true;
        var offers = List.of(t("b_fire", false, "FIRE"), t("a_frost", false, "FROST"), t("c_plain", true));
        check("no owned tags, healthy: tie goes to the lowest id", BotChoices.pickTome(offers, Set.of(), false, any) == 1, "");
        check("owned FIRE: the FIRE tome wins (synergy +3)", BotChoices.pickTome(offers, Set.of("FIRE"), false, any) == 0, "");
        check("low health: the passive wins over a synergy tome (4 beats 3)", BotChoices.pickTome(offers, Set.of("FIRE"), true, any) == 2, "");
        check("a disallowed tome is never picked", BotChoices.pickTome(offers, Set.of("FIRE"), false, id -> !id.equals("b_fire")) != 0, "");
        check("every offer disallowed gives -1 (skip)", BotChoices.pickTome(offers, Set.of(), false, id -> false) == -1, "");
        check("an empty offer list gives -1", BotChoices.pickTome(List.of(), Set.of(), false, any) == -1, "");
        check("two shared tags beat one", BotChoices.pickTome(List.of(t("x", false, "FIRE"), t("y", false, "FIRE", "SUMMON")), Set.of("FIRE", "SUMMON"), false, any) == 1, "");
        var w = List.of(new WeaponOffer("sword", "melee"), new WeaponOffer("bow", "ranged"), new WeaponOffer("axe", "melee"));
        check("starting pick takes the first offer", BotChoices.pickWeapon(w, Set.of(), Set.of(), true) == 0, "");
        check("later pick skips an owned weapon and takes a NEW archetype", BotChoices.pickWeapon(w, Set.of("sword"), Set.of("melee"), false) == 1, "");
        check("when every archetype is owned it takes the first unowned weapon (sword and bow owned, so axe)", BotChoices.pickWeapon(w, Set.of("sword", "bow"), Set.of("melee", "ranged"), false) == 2, "");
        check("an owned sword with both archetypes owned still skips the sword (bow is unowned, so index 1)", BotChoices.pickWeapon(w, Set.of("sword"), Set.of("melee", "ranged"), false) == 1, "");
        check("everything owned gives -1 (skip)", BotChoices.pickWeapon(w, Set.of("sword", "bow", "axe"), Set.of("melee", "ranged"), false) == -1, "");
        check("empty weapon offer gives -1", BotChoices.pickWeapon(List.of(), Set.of(), Set.of(), false) == -1, "");
        var shop = List.of(new Priced("a", 50, true), new Priced("b", 20, true), new Priced("c", 10, false));
        check("buys the cheapest AFFORDABLE line (not the cheaper unaffordable one)", BotChoices.pickPurchase(shop, 100, 0) == 1, "");
        check("keeps a reserve: 100 gold, reserve 90 buys nothing", BotChoices.pickPurchase(shop, 100, 90) == -1, "");
        check("reserve boundary: 100 gold, reserve 80 buys the 20-gold line", BotChoices.pickPurchase(shop, 100, 80) == 1, "");
        check("nothing affordable gives -1", BotChoices.pickPurchase(List.of(new Priced("z", 5, false)), 100, 0) == -1, "");
        check("shrine takes the first enabled option", BotChoices.pickEnabled(List.of(false, true, true)) == 1, "");
        check("shrine with none enabled gives -1", BotChoices.pickEnabled(List.of(false, false)) == -1 && BotChoices.pickEnabled(List.of()) == -1, "");
        System.out.println(fails == 0 ? "ALL PASS" : "FAILED " + fails);
    }
}
