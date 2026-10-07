import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;

/**
 * Pure model of the hazard behind the party-run crash at PinkPools.tickAll (players loop). A loop over a live list whose body can
 * remove an element (hurtServer kills the last player, the run ends, the player leaves the level) throws on the NEXT iteration.
 * A loop over a copy does not. This proves the HAZARD CLASS and that the snapshot pattern removes it. It does NOT prove the live
 * crash was this cause: that is established only by the one server log line (ConcurrentModificationException at PinkPools.java:160).
 */
public final class SnapshotIterationCheck {
    static int fails = 0;
    static void check(String name, boolean ok, String note) { System.out.println((ok ? "PASS " : "FAIL ") + name + " " + note); if (!ok) fails++; }

    /** Visits each "player"; visiting player 0 removes it from the live list, like a death that ends the run. */
    static int visit(List<String> live, boolean snapshot) {
        List<String> source = snapshot ? new ArrayList<>(live) : live;
        int visited = 0;
        for (String p : source) {
            visited++;
            if (p.equals("A")) live.remove(p);
        }
        return visited;
    }

    public static void main(String[] args) {
        boolean threw = false;
        try { visit(new ArrayList<>(List.of("A", "B", "C", "D")), false); } catch (ConcurrentModificationException e) { threw = true; }
        check("a live-list loop throws when the body removes a player mid-iteration (the hazard)", threw, "");
        List<String> live = new ArrayList<>(List.of("A", "B", "C", "D"));
        int seen = -1; boolean threw2 = false;
        try { seen = visit(live, true); } catch (ConcurrentModificationException e) { threw2 = true; }
        check("a snapshot loop does not throw", !threw2, "");
        check("the snapshot loop still visits every player that was present when it started", seen == 4, "visited " + seen);
        check("and the live list really lost the removed player", live.size() == 3 && !live.contains("A"), live + "");
        // MEASURED with plain java.util.ArrayList (not assumed): the live loop throws unless the removal leaves cursor == size, i.e. it survives only when the
        // SECOND-TO-LAST element is removed. A one-element list throws too. So "solo runs are safe" is NOT supported by this model; whether the game's
        // level.players() is a plain ArrayList is a separate fact that this check does not establish.
        int throwsCount = 0, total = 0;
        for (int n = 1; n <= 3; n++) for (int rm = 0; rm < n; rm++) {
            List<String> l = new ArrayList<>(); for (int i = 0; i < n; i++) l.add("p" + i);
            total++;
            try { int i = 0; for (String s : l) { if (i == rm) l.remove(s); i++; } } catch (ConcurrentModificationException e) { throwsCount++; }
        }
        check("the live loop throws in 4 of the 6 (size, removed index) cases up to size 3 and survives only the second-to-last removal", throwsCount == 4 && total == 6, throwsCount + " of " + total);
        System.out.println(fails == 0 ? "ALL PASS" : "FAILED " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }
}
