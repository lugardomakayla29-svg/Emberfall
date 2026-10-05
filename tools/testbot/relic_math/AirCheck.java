import com.solme.emberfall.world.StaticMap;
import java.util.*;
public class AirCheck {
    public static void main(String[] a) {
        StaticMap m = StaticMap.get();
        // Every block the map pastes, keyed by position relative to the origin
        Map<String, String> blocks = new HashMap<>();
        for (StaticMap.Put p : m.terrainAndWall()) blocks.put(p.x() + "," + p.y() + "," + p.z(), p.state().getBlock().toString());
        System.out.println("map puts: " + blocks.size());
        List<int[]> c = m.chestCandidates(3, 15, 80, 12);
        int occupied = 0; Map<String, Integer> what = new TreeMap<>();
        for (int[] p : c) { String b = blocks.get(p[0] + "," + p[1] + "," + p[2]); if (b != null && !b.contains("air")) { occupied++; what.merge(b, 1, Integer::sum); } }
        System.out.println("candidates: " + c.size() + ", with a non-air map block at the chest position: " + occupied);
        System.out.println(what);
    }
}
