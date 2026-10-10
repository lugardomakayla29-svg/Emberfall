import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Pins the split between TARGETING and COUNTING for the Devour's swallowed mobs. A swallowed mob is hidden and invulnerable inside the Broodtide: no weapon may pick it
 * (measured: the visible mob beside it lost 92% less damage), but it must still count toward the wave cap and stay inside the arena.
 * Reads the source tree, so a later edit that brings the bug back in either direction fails here. Run from the repo root.
 */
public class TargetableSplitCheck {
    static int fails = 0, total = 0;

    static void check(String l, boolean ok, String e) {
        total++;
        System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e);
        if (!ok) {
            fails++;
        }
    }

    /** The only files allowed to call isEmberfallHostile: the definition, and the counting / containment sites. */
    static final Set<String> COUNTING = Set.of(
            "combat/AutoAttackSystem.java",   // defines it, and isTargetable calls it
            "wave/WaveDirector.java",         // the wave cap
            "world/ArenaBoundary.java",       // containment
            "world/CircleBoundary.java",      // containment
            "command/RelicCommands.java");    // a debug foe count

    public static void main(String[] a) throws IOException {
        Path root = Paths.get("src/main/java/com/solme/emberfall");
        List<Path> files;
        try (Stream<Path> w = Files.walk(root)) {
            files = w.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
        Map<String, String> src = new TreeMap<>();
        for (Path p : files) {
            src.put(root.relativize(p).toString().replace('\\', '/'), Files.readString(p));
        }

        // T1: no targeting file may still use the hostile test directly
        List<String> leaks = new ArrayList<>();
        for (var e : src.entrySet()) {
            if (e.getValue().contains("isEmberfallHostile") && !COUNTING.contains(e.getKey())) {
                leaks.add(e.getKey());
            }
        }
        check("T1 no targeting file calls isEmberfallHostile (a swallowed mob would be picked)", leaks.isEmpty(), leaks.toString());

        // T2: every counting site still sees a swallowed mob
        List<String> lost = new ArrayList<>();
        for (String k : List.of("wave/WaveDirector.java", "world/ArenaBoundary.java", "world/CircleBoundary.java")) {
            String t = src.get(k);
            if (t == null || !t.contains("isEmberfallHostile") || t.contains("isTargetable")) {
                lost.add(k);
            }
        }
        check("T2 the wave cap and both arena boundaries still count swallowed mobs (isEmberfallHostile, never isTargetable)", lost.isEmpty(), lost.toString());

        // T3: the predicate itself
        String aas = src.get("combat/AutoAttackSystem.java");
        check("T3 isTargetable is isEmberfallHostile AND NOT swallowed, tagged with the shared constant",
                aas.contains("return isEmberfallHostile(mob) && !mob.getTags().contains(SWALLOWED_TAG);")
                        && aas.contains("SWALLOWED_TAG = \"emberfall_swallowed\""), "");

        // T4: the weapon systems all use it (a floor, so deleting a whole file's call fails)
        List<String> weapons = List.of("combat/BowSystem.java", "combat/BroadswordSystem.java", "combat/ChainSystem.java", "combat/DaggerSystem.java", "combat/HalberdSystem.java",
                "combat/OrbitWeaponSystem.java", "combat/PhantomBladeSystem.java", "combat/ReapersRiteSystem.java", "combat/StaffSystem.java", "combat/BeaconSystem.java",
                "combat/TotemWeaponSystem.java", "combat/TimedAbilitySystem.java", "combat/OnHitEffects.java", "relic/RelicHitEvents.java", "bot/BotPilot.java");
        List<String> missing = new ArrayList<>();
        for (String w : weapons) {
            String t = src.get(w);
            if (t == null || !t.contains("isTargetable")) {
                missing.add(w);
            }
        }
        check("T4 all 15 weapon, ability, hit-effect and bot files pick targets through isTargetable", missing.isEmpty(), missing.toString());

        // T5: the auto-attack core switched all of its own picks. Counted from the original file: 9 caller lines (the definition and its javadoc are not callers).
        long inCore = aas.lines().filter(l -> !l.trim().startsWith("*") && !l.contains("public static boolean isTargetable") && (l.contains("isTargetable(") || l.contains("::isTargetable"))).count();
        check("T5 AutoAttackSystem's own target picks use isTargetable (exactly 9 caller lines)", inCore == 9, "sites=" + inCore);

        System.out.println();
        System.out.println(fails == 0 ? "ALL PASS (" + total + " checks)" : "FAILED " + fails + " of " + total);
        System.exit(fails == 0 ? 0 : 1);
    }
}
