import com.solme.emberfall.rift.RiftSelect;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class RiftSelectCheck {
    static int fails = 0;
    static void check(String l, boolean ok, String e) { System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e); if (!ok) fails++; }
    static UUID u(int i) { return new UUID(0L, i); }
    static Set<UUID> of(int... ids) { Set<UUID> s = new LinkedHashSet<>(); for (int i : ids) s.add(u(i)); return s; }

    public static void main(String[] a) {
        check("timeout is 30 s = 600 ticks", RiftSelect.TIMEOUT_SECONDS == 30 && RiftSelect.TIMEOUT_TICKS == 600L, "");

        // A: a phase waits for everyone, and ends when the last one resolves
        var p = new RiftSelect.Phase(100, of(1, 2, 3));
        check("A1 three members start pending", p.pendingCount() == 3 && p.isPending(u(1)) && p.isPending(u(2)) && p.isPending(u(3)), "");
        check("A2 not done while anyone is pending", !p.done(100) && !p.done(101), "");
        check("A3 resolving one returns true and leaves two", p.resolve(u(2)) && p.pendingCount() == 2 && !p.isPending(u(2)), "");
        check("A4 still not done with two pending", !p.done(150), "");
        p.resolve(u(1));
        check("A5 not done with one pending", !p.done(160), "");
        p.resolve(u(3));
        check("A6 done the moment the last one resolves (no waiting for the timeout)", p.done(161) && p.pendingCount() == 0, "");
        check("A7 everyone is still in the party after resolving", p.party().equals(of(1, 2, 3)), p.party().toString());

        // B: a repeat or a stranger changes nothing (a double click, a late packet, a player who was never in the party)
        var q = new RiftSelect.Phase(0, of(1, 2));
        check("B1 first resolve is true", q.resolve(u(1)), "");
        check("B2 the SAME member again is false and changes nothing", !q.resolve(u(1)) && q.pendingCount() == 1, "");
        check("B3 a stranger is false and changes nothing", !q.resolve(u(99)) && q.pendingCount() == 1 && !q.party().contains(u(99)), "");

        // C: a member who leaves or dies is no longer waited for AND no longer goes
        var r = new RiftSelect.Phase(0, of(1, 2, 3));
        r.resolve(u(1));
        check("C1 dropping a pending member is true", r.drop(u(2)), "");
        check("C2 the dropped member is out of the party", !r.party().contains(u(2)) && r.party().equals(of(1, 3)), r.party().toString());
        check("C3 not done: member 3 is still deciding", !r.done(10), "");
        r.resolve(u(3));
        check("C4 done once the remaining member resolves (the dropped one is not waited for)", r.done(11), "");
        var s = new RiftSelect.Phase(0, of(1, 2));
        s.resolve(u(1));
        check("C5 dropping an ALREADY-resolved member returns false but still removes them from the party", !s.drop(u(1)) && s.party().equals(of(2)), s.party().toString());
        check("C6 dropping a stranger changes nothing", !s.drop(u(77)) && s.party().equals(of(2)), "");

        // D: the timeout is exact, and it overrides a member who never answers (screen left open, mod blocks the packet, AFK)
        var t = new RiftSelect.Phase(1000, of(1, 2));
        check("D1 at 599 ticks after the start: not timed out, still waiting", !t.timedOut(1599) && !t.done(1599), "");
        check("D2 at exactly 600 ticks: timed out and done even though both are pending", t.timedOut(1600) && t.done(1600) && t.pendingCount() == 2, "");
        check("D3 long after the timeout: still done", t.done(999999), "");
        check("D4 a timeout does not remove anyone from the party (they keep their stored character and go)", t.party().equals(of(1, 2)), "");
        check("D5 a clock before the start never reads as timed out", !new RiftSelect.Phase(1000, of(1)).timedOut(0), "");

        // E: an empty phase
        var e = new RiftSelect.Phase(0, of());
        check("E1 a phase with no members is empty and done at once", e.empty() && e.done(0), "");
        var f = new RiftSelect.Phase(0, of(1));
        f.drop(u(1));
        check("E2 everyone dropped: empty, so the caller starts NO run", f.empty() && f.party().isEmpty(), "");
        check("E3 a normal phase is not empty", !new RiftSelect.Phase(0, of(1)).empty(), "");

        // F: the reminder number
        check("F1 30 at the start, 30 at 19 ticks (rounds up), 29 at 20", RiftSelect.secondsLeft(0) == 30 && RiftSelect.secondsLeft(1) == 30 && RiftSelect.secondsLeft(20) == 29, "");
        check("F2 1 at 580 ticks, 1 at 599, 0 at 600", RiftSelect.secondsLeft(580) == 1 && RiftSelect.secondsLeft(599) == 1 && RiftSelect.secondsLeft(600) == 0, "");
        check("F3 never negative or above 30, even far out or for a negative input", RiftSelect.secondsLeft(100000) == 0 && RiftSelect.secondsLeft(-50) == 30, "");

        // G: the party snapshot cannot be used to change the phase
        var g = new RiftSelect.Phase(0, of(1, 2));
        g.party().clear();
        check("G1 clearing the returned party does not change the phase", g.party().equals(of(1, 2)), "");
        Set<UUID> src = of(1, 2);
        var h = new RiftSelect.Phase(0, src);
        src.clear();
        check("G2 clearing the set passed in does not change the phase", h.pendingCount() == 2 && h.party().equals(of(1, 2)), "");

        Set<UUID> keep = of(4, 5, 6);
        new RiftSelect.Phase(0, keep);
        check("G3 building a phase leaves the caller's set untouched", keep.equals(of(4, 5, 6)), keep.toString());

        System.out.println(fails == 0 ? "ALL PASS" : "FAILED " + fails);
    }
}
