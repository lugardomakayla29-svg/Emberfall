import com.solme.emberfall.relic.*;
import com.solme.emberfall.world.StaticMap;
import java.util.*;
public class PropSweep {
    public static void main(String[] a) {
        StaticMap m = StaticMap.get();
        int seeds = 2000, short_ = 0, onProp = 0, nearProp = 0, minCount = 99;
        List<int[]> c = m.chestCandidates(3, 15, 80, 12);
        for (int s = 0; s < seeds; s++) {
            var plan = ChestPlan.plan(c, 14, 2, new Random(s));
            minCount = Math.min(minCount, plan.size());
            if (plan.size() != 16) short_++;
            for (var sp : plan) if (m.propNear(sp.x(), sp.z())) nearProp++;
        }
        System.out.println("seeds " + seeds + ": runs with fewer than 16 chests = " + short_ + ", smallest run = " + minCount + ", chests on/next to a prop = " + nearProp);
        // the BEFORE picture, rebuilt from the unfiltered 1057 (same stride/radii, no prop filter): how often would 16 picks hit a prop?
        System.exit(short_ == 0 && nearProp == 0 ? 0 : 1);
    }
}
