import com.solme.emberfall.relic.ChestRevealSessions;
import com.solme.emberfall.relic.ChestRevealSessions.Open;
import java.util.UUID;

/**
 * Pure checks for the server's record of open chest reveals (GAME_PLAN row 2.6, server half). They prove the rule a forged packet must not break:
 * a CLOSE is honoured only for the player who was shown that exact reveal, once, and not after it expired. They do NOT prove a packet is sent or
 * received, that a screen opens, or that the chest calls this: that is Koda's wiring, and no screen exists. Expected values are written by hand from
 * the rules, not copied from the class.
 */
public class ChestRevealSessionsCheck {
    static int fails = 0;
    static int total = 0;

    static void check(String l, boolean ok, String e) {
        total++;
        System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e);
        if (!ok) {
            fails++;
        }
    }

    static final UUID P = new UUID(0, 1);
    static final UUID Q = new UUID(0, 2);

    public static void main(String[] a) {
        try {
            run();
        } catch (RuntimeException e) {
            check("run: no check threw an exception", false, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        System.out.println(fails == 0 ? "ALL PASS (" + total + " checks)" : "FAILED " + fails + " of " + total);
        if (fails != 0) {
            System.exit(1);
        }
    }

    static void run() {
        check("constant: an unclosed reveal expires after 600 ticks (30 s)", ChestRevealSessions.MAX_OPEN_TICKS == 600, "");

        // ---- the forged close: THE rule ------------------------------------------------------------------------------------------------
        check("forged: a close with NO reveal open returns false and does not throw, for any id", noThrowNoReveal(), "");
        ChestRevealSessions s = new ChestRevealSessions();
        check("forged: a close from a player with NO open reveal is ignored", !s.close(P, 1, 0) && !s.close(P, 0, 0) && !s.close(P, -1, 0) && !s.close(P, Integer.MAX_VALUE, 0), "");
        check("forged: an ignored close changes nothing (still no reveal, count 0)", !s.isOpen(P, 0) && s.count(0) == 0, "");
        Open o = s.open(P, "Rare", "Ember Ring", 77L, 100);
        check("open: the first reveal gets id 1 and carries the answer and seed it was given", o.id() == 1 && o.tier().equals("Rare") && o.item().equals("Ember Ring") && o.seed() == 77L && o.openedAt() == 100, o.toString());
        check("open: it is open at the tick it was opened", s.isOpen(P, 100) && s.count(100) == 1 && s.get(P, 100) != null, "");
        check("forged: a close with the WRONG id is ignored and the reveal stays open", !s.close(P, 2, 110) && !s.close(P, 0, 110) && !s.close(P, -1, 110) && s.isOpen(P, 110), "");
        check("forged: ANOTHER player cannot close this player's reveal, even quoting the right id", !s.close(Q, 1, 110) && s.isOpen(P, 110), "");
        check("close: the right player with the right id closes it", s.close(P, 1, 120), "");
        check("close: it is gone afterwards", !s.isOpen(P, 120) && s.count(120) == 0 && s.get(P, 120) == null, "");
        check("forged: closing the SAME id a second time is ignored (replay)", !s.close(P, 1, 121), "");

        // ---- one at a time, ids never reused ------------------------------------------------------------------------------------------------
        s = new ChestRevealSessions();
        Open first = s.open(P, "Common", "Rusty Key", 1L, 0);
        Open second = s.open(P, "Rare", "Ember Ring", 2L, 10);
        check("ids: a second reveal gets a higher id (1 then 2)", first.id() == 1 && second.id() == 2, first.id() + "," + second.id());
        check("replace: opening a second reveal REPLACES the first, so the OLD id no longer closes anything", !s.close(P, first.id(), 11) && s.isOpen(P, 11), "");
        check("replace: the new id still closes it, and it returns the second answer", s.get(P, 11).item().equals("Ember Ring") && s.close(P, second.id(), 12), "");
        check("replace: the player has at most ONE open reveal (count 1 after two opens)", count1(), "");
        s = new ChestRevealSessions();
        s.open(P, "A", "a", 0, 0);
        s.open(Q, "B", "b", 0, 0);
        check("players: each player has their own reveal and their own id (P=1, Q=2)", s.get(P, 0).id() == 1 && s.get(Q, 0).id() == 2 && s.count(0) == 2, "");
        check("players: P cannot close Q's id and Q cannot close P's id", !s.close(P, 2, 1) && !s.close(Q, 1, 1) && s.count(1) == 2, "");
        check("players: closing P's leaves Q's open", s.close(P, 1, 2) && s.isOpen(Q, 2) && !s.isOpen(P, 2) && s.count(2) == 1, "");

        // ---- expiry: boundary on both sides ---------------------------------------------------------------------------------------------------
        s = new ChestRevealSessions();
        s.open(P, "Rare", "Ember Ring", 5L, 1000);
        check("expiry: still open one tick before it expires (1599)", s.isOpen(P, 1599) && s.get(P, 1599) != null && s.count(1599) == 1, "");
        check("expiry: expired AT 600 ticks after opening (1600), the first tick it is not open", !s.isOpen(P, 1600) && s.get(P, 1600) == null && s.count(1600) == 0, "");
        s = new ChestRevealSessions();
        s.open(P, "Rare", "Ember Ring", 5L, 1000);
        check("expiry: a close one tick before expiry (1599) is honoured", s.close(P, 1, 1599), "");
        s = new ChestRevealSessions();
        s.open(P, "Rare", "Ember Ring", 5L, 1000);
        check("expiry: a close AT expiry (1600) is ignored", !s.close(P, 1, 1600), "");
        check("expiry: and that late close removed the dead reveal (a retry is still ignored)", !s.close(P, 1, 1601) && !s.isOpen(P, 1601), "");
        s = new ChestRevealSessions();
        s.open(P, "A", "a", 0, 0);
        s.open(Q, "B", "b", 0, 500);
        check("sweep: at 600 only the older reveal is dropped and sweep reports 1", s.sweep(600) == 1 && !s.isOpen(P, 600) && s.isOpen(Q, 600), "");
        check("sweep: sweeping again drops nothing (0), and sweeping an empty set is 0", s.sweep(600) == 0 && new ChestRevealSessions().sweep(99999) == 0, "");
        check("sweep: count ignores an expired-but-unswept reveal", countIgnoresDead(), "");
        s = new ChestRevealSessions();
        s.open(P, "A", "a", 0, 5000);
        check("clock: a clock that goes BACKWARDS does not expire a reveal (opened 5000, asked at 100)", s.isOpen(P, 100) && s.close(P, 1, 100), "");
        s = new ChestRevealSessions();
        s.open(P, "A", "a", 0, Long.MAX_VALUE - 10);
        check("clock: opening near Long.MAX_VALUE does not overflow into an instant expiry", s.isOpen(P, Long.MAX_VALUE - 10) && s.isOpen(P, Long.MAX_VALUE - 1), "");

        // ---- refusing a bad open ------------------------------------------------------------------------------------------------------------------
        s = new ChestRevealSessions();
        check("refuse: a null tier, null item, empty tier or empty item is refused", throwsOn(() -> new ChestRevealSessions().open(P, null, "x", 0, 0)) && throwsOn(() -> new ChestRevealSessions().open(P, "x", null, 0, 0)) && throwsOn(() -> new ChestRevealSessions().open(P, "", "x", 0, 0)) && throwsOn(() -> new ChestRevealSessions().open(P, "x", "", 0, 0)), "");
        check("refuse: a null player is refused on open", throwsOn(() -> new ChestRevealSessions().open(null, "x", "y", 0, 0)), "");
        check("refuse: a refused open leaves nothing open and does not use an id", refusedOpenIsHarmless(), "");
        check("forged: a close for a null player is ignored, not a crash", !new ChestRevealSessions().close(null, 1, 0), "");

        // ---- forget and clear ---------------------------------------------------------------------------------------------------------------------
        s = new ChestRevealSessions();
        s.open(P, "A", "a", 0, 0);
        s.open(Q, "B", "b", 0, 0);
        s.forget(P);
        check("forget: only that player's reveal is dropped", !s.isOpen(P, 1) && s.isOpen(Q, 1) && s.count(1) == 1 && !s.close(P, 1, 1), "");
        s.forget(UUID.randomUUID());
        check("forget: forgetting a stranger is harmless", s.count(1) == 1, "");
        s = new ChestRevealSessions();
        s.open(P, "A", "a", 0, 0);
        s.open(Q, "B", "b", 0, 0);
        s.clear();
        check("clear: no reveal is open for anyone", s.count(1) == 0 && !s.isOpen(P, 1) && !s.isOpen(Q, 1), "");
        Open afterClear = s.open(P, "C", "c", 0, 5);
        check("clear: ids keep counting up (the next id is 3), so an old id cannot close a new reveal", afterClear.id() == 3 && !s.close(P, 1, 6) && !s.close(P, 2, 6) && s.close(P, 3, 6), "next id " + afterClear.id());

        // ---- the answer is a snapshot --------------------------------------------------------------------------------------------------------------
        s = new ChestRevealSessions();
        Open snap = s.open(P, "Legendary", "Wasp Idol", Long.MIN_VALUE, 0);
        check("answer: the stored answer and an extreme seed come back exactly as opened", s.get(P, 0).tier().equals("Legendary") && s.get(P, 0).item().equals("Wasp Idol") && s.get(P, 0).seed() == Long.MIN_VALUE && snap.equals(s.get(P, 0)), "");

        // ---- a long sequence ------------------------------------------------------------------------------------------------------------------------
        check("sequence: 100 rounds x 50 players, all with a live reveal while wrong closes are tried: 5000 honoured, none wrong, nothing left", longRun(), "");
    }

    interface Thrower {
        void run();
    }

    static boolean throwsOn(Thrower t) {
        try {
            t.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    static boolean noThrowNoReveal() {
        ChestRevealSessions s = new ChestRevealSessions();
        try {
            return !s.close(P, 1, 0) && !s.close(P, 0, 0) && !s.close(P, -7, 0) && !s.close(P, Integer.MIN_VALUE, 0) && !s.close(Q, 1, 99999);
        } catch (RuntimeException e) {
            return false;
        }
    }

    static boolean count1() {
        ChestRevealSessions s = new ChestRevealSessions();
        s.open(P, "A", "a", 0, 0);
        s.open(P, "B", "b", 0, 1);
        return s.count(2) == 1;
    }

    static boolean countIgnoresDead() {
        ChestRevealSessions s = new ChestRevealSessions();
        s.open(P, "A", "a", 0, 0);
        s.open(Q, "B", "b", 0, 500);
        return s.count(600) == 1 && s.count(1100) == 0;
    }

    static boolean refusedOpenIsHarmless() {
        ChestRevealSessions s = new ChestRevealSessions();
        try {
            s.open(P, null, "x", 0, 0);
        } catch (IllegalArgumentException e) {
            // expected
        }
        Open ok = s.open(P, "A", "a", 0, 0);
        return ok.id() == 1 && s.count(0) == 1;
    }

    /**
     * 50 players, 100 rounds. Each round opens a reveal for EVERY player first, so every player has a live reveal while the wrong closes are tried:
     * a wrong id from the owner, a right id from a different player (who also has a live reveal of their own), then the right close, then a replay.
     * Expected counts come from the loop, not from the class.
     */
    static boolean longRun() {
        ChestRevealSessions s = new ChestRevealSessions();
        UUID[] ps = new UUID[50];
        for (int i = 0; i < ps.length; i++) {
            ps[i] = new UUID(9, i);
        }
        long now = 0;
        int honoured = 0;
        for (int round = 0; round < 100; round++) {
            Open[] os = new Open[ps.length];
            for (int i = 0; i < ps.length; i++) {
                os[i] = s.open(ps[i], "T", "I", round, now);
            }
            if (s.count(now) != ps.length) {
                return false;
            }
            for (int i = 0; i < ps.length; i++) {
                int j = (i + 1) % ps.length;
                // Player j has a LIVE reveal; quoting player i's id from player j must not close it, and must not close player i's either.
                if (s.close(ps[j], os[i].id(), now) || !s.isOpen(ps[j], now) || !s.isOpen(ps[i], now)) {
                    return false;
                }
                if (s.close(ps[i], os[i].id() + 1, now) || s.close(ps[i], os[i].id() - 1, now)) {
                    return false;
                }
            }
            for (int i = 0; i < ps.length; i++) {
                if (s.close(ps[i], os[i].id(), now)) {
                    honoured++;
                }
                if (s.close(ps[i], os[i].id(), now)) {
                    return false;
                }
                now++;
            }
        }
        return honoured == 5000 && s.count(now) == 0;
    }
}
