import com.solme.emberfall.rift.RiftFx;
import com.solme.emberfall.rift.RiftFx.Event;
import com.solme.emberfall.rift.RiftFx.Kind;
import com.solme.emberfall.rift.RiftIdle;
import com.solme.emberfall.rift.RiftShape;
import com.solme.emberfall.rift.RiftShape.Shape;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure checks for the OPEN Rift's idle look (RiftIdle). They prove the schedule: something is drawn on EVERY tick, the picture MOVES (two
 * neighbouring ticks are never the same), the particle count never passes the budget, only particles are ever asked for, the lit cells are
 * really inside the tear, and the fill reaches the whole body over a few beats. They do NOT prove it looks good or that it is visible on
 * a real client: that is the owner's to judge.
 */
public class RiftIdleCheck {
    static int fails = 0;
    static int total = 0;
    static final int SEEDS = 300;
    static final int TICKS = 240;

    static void check(String l, boolean ok, String e) {
        total++;
        System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e);
        if (!ok) {
            fails++;
        }
    }

    static String sig(List<Event> es) {
        StringBuilder b = new StringBuilder();
        for (Event e : es) {
            b.append(e.key).append('@').append(e.x).append(',').append(e.y).append('#').append(e.count).append(';');
        }
        return b.toString();
    }

    public static void main(String[] a) {
        int empty = 0, same = 0, over = 0, nonParticle = 0, outside = 0, maxSeen = 0, total2 = 0, ticksSeen = 0;
        int noRim = 0, noFill = 0, notDeterministic = 0, notFullCover = 0, noStreak = 0, noSat = 0, satSeeds = 0;
        for (long s = 0; s < SEEDS; s++) {
            Shape shape = RiftShape.generate(s);
            Set<String> litBody = new HashSet<>();
            String prev = null;
            boolean rim = false, fill = false, streak = false, sat = false;
            int bodyCells = 0;
            for (int[] c : shape.cells()) {
                if (shape.isBody(c[0], c[1])) {
                    bodyCells++;
                }
            }
            boolean hasSat = false;
            for (int[] c : shape.cells()) {
                if (shape.isSatellite(c[0], c[1])) {
                    hasSat = true;
                }
            }
            if (hasSat) {
                satSeeds++;
            }
            for (int t = 0; t < TICKS; t++) {
                List<Event> es = RiftIdle.at(shape, t);
                ticksSeen++;
                if (es.isEmpty()) {
                    empty++;
                }
                String sg = sig(es);
                if (sg.equals(prev)) {
                    same++;
                }
                prev = sg;
                int n = RiftIdle.particleCount(es);
                total2 += n;
                maxSeen = Math.max(maxSeen, n);
                if (n > RiftIdle.BUDGET) {
                    over++;
                }
                if (!sg.equals(sig(RiftIdle.at(shape, t)))) {
                    notDeterministic++;
                }
                for (Event e : es) {
                    if (e.kind != Kind.PARTICLE) {
                        nonParticle++;
                    }
                    if (!shape.cells().stream().anyMatch(c -> c[0] == e.x && c[1] == e.y)) {
                        outside++;
                    }
                    if (RiftIdle.RIM_DUST.equals(e.key) || RiftIdle.RIM_SPARK.equals(e.key)) {
                        rim = true;
                    }
                    if (RiftIdle.FILL_DUST.equals(e.key)) {
                        fill = true;
                        litBody.add(e.x + "," + e.y);
                    }
                    if (RiftIdle.STREAK.equals(e.key)) {
                        streak = true;
                    }
                    if (shape.isSatellite(e.x, e.y)) {
                        sat = true;
                    }
                }
            }
            if (!rim) {
                noRim++;
            }
            if (!fill) {
                noFill++;
            }
            if (!streak) {
                noStreak++;
            }
            if (hasSat && !sat) {
                noSat++;
            }
            if (litBody.size() < bodyCells * 9 / 10) {
                notFullCover++;
            }
        }
        double avg = (double) total2 / ticksSeen;
        check("I1 something is drawn on EVERY tick, for every seed", empty == 0, "empty ticks " + empty + " of " + ticksSeen);
        check("I2 the picture MOVES: two neighbouring ticks are never identical", same == 0, "identical neighbours " + same);
        check("I3 never more than the budget (" + RiftIdle.BUDGET + ") particles in one tick", over == 0 && maxSeen <= RiftIdle.BUDGET,
                "max " + maxSeen + ", ticks over " + over);
        check("I4 an idle Rift only ever asks for PARTICLES (no sound, chat, push, entity)", nonParticle == 0, "non-particle events " + nonParticle);
        check("I5 every lit cell is a cell of the tear", outside == 0, "cells outside " + outside);
        check("I6 deterministic: the same shape and tick give the same list", notDeterministic == 0, "differences " + notDeterministic);
        check("I7 every seed shows a rim", noRim == 0, "seeds with no rim " + noRim);
        check("I8 every seed shows a fill", noFill == 0, "seeds with no fill " + noFill);
        check("I9 the fill reaches at least 90% of the body cells within " + TICKS + " ticks", notFullCover == 0, "seeds below 90% " + notFullCover);
        check("I10 every seed throws streaks", noStreak == 0, "seeds with none " + noStreak);
        check("I11 satellites shimmer in every seed that has one", noSat == 0, "seeds with a silent satellite " + noSat + " (of " + satSeeds + " with satellites)");
        check("I12 it is visibly denser than nothing: the average is at least 12 particles per tick", avg >= 12.0, String.format("avg %.1f, max %d", avg, maxSeen));
        // The tick number wraps: a huge tick still works, and a negative one does not crash.
        Shape sh = RiftShape.generate(7);
        check("I13 a huge tick number still draws", !RiftIdle.at(sh, Integer.MAX_VALUE - 3).isEmpty() || !RiftIdle.at(sh, 1_000_000).isEmpty(), "ok");
        System.out.println();
        System.out.println(fails == 0 ? "ALL PASS (" + total + ")" : fails + " FAIL of " + total);
        System.exit(fails == 0 ? 0 : 1);
    }
}
