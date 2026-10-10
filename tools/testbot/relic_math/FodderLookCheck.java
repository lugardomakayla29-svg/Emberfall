import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Fodder armour and size (owner design 2026-10-10). Engine-free: reads the SOURCE text. The expected colours are typed here from the pixel means I measured
 * on the head skins, and the size rules are the owner's words, so a wrong constant cannot agree with itself.
 */
public class FodderLookCheck {
    static int fails = 0;
    static void check(String name, boolean ok, String note) {
        System.out.println((ok ? "PASS " : "FAIL ") + name + (note.isEmpty() ? "" : "  " + note));
        if (!ok) fails++;
    }
    static final String ROOT = "src/main/java/com/solme/emberfall/entity/";
    static String read(String f) throws Exception { return Files.readString(Path.of(ROOT + f)); }

    static double[] pair(String src, String name) {
        Matcher m = Pattern.compile("double\\[\\] " + name + "\\s*=\\s*\\{\\s*([0-9.]+)\\s*,\\s*([0-9.]+)\\s*\\}").matcher(src);
        return m.find() ? new double[] {Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2))} : null;
    }
    static int[] rgb(String src, String name) {
        Matcher m = Pattern.compile("int " + name + "\\s*=\\s*rgb\\((\\d+),\\s*(\\d+),\\s*(\\d+)\\)").matcher(src);
        return m.find() ? new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))} : null;
    }
    static String methodBody(String src, String sig) {
        int i = src.indexOf(sig);
        if (i < 0) return "";
        int open = src.indexOf('{', i), depth = 0;
        for (int k = open; k < src.length(); k++) {
            if (src.charAt(k) == '{') depth++;
            if (src.charAt(k) == '}' && --depth == 0) return src.substring(open, k + 1);
        }
        return "";
    }
    static boolean near(int[] a, int r, int g, int b) { return a != null && Math.abs(a[0]-r) <= 2 && Math.abs(a[1]-g) <= 2 && Math.abs(a[2]-b) <= 2; }

    public static void main(String[] args) throws Exception {
        String look = read("FodderLook.java");
        String shield = read("HordeShieldbearer.java"), charger = read("HordeCharger.java"), spitter = read("HordeSpitter.java");

        // L1-L2 colours match the measured head pixels
        check("L1 the rust dye is the Undead Knight helmet colour (102,73,80)", near(rgb(look, "RUST"), 102, 73, 80), Arrays.toString(rgb(look, "RUST")));
        check("L2 the charger dye is the Elite Zombie red (119,43,43)", near(rgb(look, "CHARGER_RED"), 119, 43, 43), Arrays.toString(rgb(look, "CHARGER_RED")));

        // L3-L8 sizes
        double[] sb = pair(look, "SHIELDBEARER_SCALE"), ch = pair(look, "CHARGER_SCALE"), sp = pair(look, "SPITTER_SCALE");
        check("L3 all three size pairs exist", sb != null && ch != null && sp != null, "");
        if (sb == null || ch == null || sp == null) { System.out.println("FAILED"); System.exit(1); }
        check("L4 a veteran is LARGER than the normal one of the same type, for every type", sb[1] > sb[0] && ch[1] > ch[0] && sp[1] > sp[0], Arrays.toString(sb) + Arrays.toString(ch) + Arrays.toString(sp));
        check("L5 the spitter is SMALLER than a plain zombie in both tiers", sp[0] < 1.0 && sp[1] < 1.0, Arrays.toString(sp));
        check("L6 the charger is LARGER than a plain zombie in both tiers", ch[0] > 1.0 && ch[1] > 1.0, Arrays.toString(ch));
        check("L7 a veteran spitter is still smaller than a plain zombie but larger than a normal spitter", sp[1] < 1.0 && sp[1] > sp[0], "");
        check("L8 no size is absurd (all within 0.7 to 1.5)", Arrays.stream(new double[] {sb[0], sb[1], ch[0], ch[1], sp[0], sp[1]}).allMatch(v -> v >= 0.7 && v <= 1.5), "");

        // L9-L13 which pieces each type wears
        String dsb = methodBody(look, "static void dressShieldbearer"), dch = methodBody(look, "static void dressCharger"), dsp = methodBody(look, "static void dressSpitter");
        if (dsb.isEmpty()) { dsb = methodBody(look, "void dressShieldbearer"); dch = methodBody(look, "void dressCharger"); dsp = methodBody(look, "void dressSpitter"); }
        check("L9 shieldbearer wears a FULL leather set (chest, legs, feet) in the rust dye", dsb.contains("LEATHER_CHESTPLATE") && dsb.contains("LEATHER_LEGGINGS") && dsb.contains("LEATHER_BOOTS") && dsb.contains("RUST"), "");
        check("L10 charger wears exactly ONE piece, the leather chestplate, in the charger red", dch.contains("LEATHER_CHESTPLATE") && dch.contains("CHARGER_RED") && !dch.contains("LEGGINGS") && !dch.contains("BOOTS") && !dch.contains("HELMET"), "");
        check("L11 spitter wears a FULL chainmail set (chest, legs, feet)", dsp.contains("CHAINMAIL_CHESTPLATE") && dsp.contains("CHAINMAIL_LEGGINGS") && dsp.contains("CHAINMAIL_BOOTS"), "");
        check("L12 nobody's HEAD slot is touched by the armour (the custom skull stays)", !dsb.contains("EquipmentSlot.HEAD") && !dch.contains("EquipmentSlot.HEAD") && !dsp.contains("EquipmentSlot.HEAD") && !dsb.contains("HELMET") && !dsp.contains("HELMET"), "");
        check("L13 every worn piece has drop chance 0 (armour never becomes loot)", look.contains("setDropChance(slot, 0.0F)"), "");

        // L14-L16 armour points are cancelled from the ITEMS, not measured off a not-yet-updated attribute
        String cancel = methodBody(look, "private static void cancel(");
        check("L14 the armour cancel reads the worn pieces (wornTotal) and does not use getValue() minus base", cancel.contains("wornTotal") && !cancel.contains("getValue()") && !cancel.contains("getBaseValue()"), "");
        check("L15 the cancel removes the old modifier first (calling twice never stacks)", cancel.indexOf("removeModifier") >= 0 && cancel.indexOf("removeModifier") < cancel.indexOf("addPermanentModifier"), "");
        check("L16 both armour and toughness are cancelled", look.contains("cancel(mob, Attributes.ARMOR, ARMOR_CANCEL_ID)") && look.contains("Attributes.ARMOR_TOUGHNESS"), "");

        // L17-L20 wiring: each class dresses, sizes normal in prepare, sizes veteran in becomeVeteran
        for (Object[] c : new Object[][] {{"Shieldbearer", shield, "dressShieldbearer", "SHIELDBEARER_SCALE"}, {"Charger", charger, "dressCharger", "CHARGER_SCALE"}, {"Spitter", spitter, "dressSpitter", "SPITTER_SCALE"}}) {
            String src = (String) c[1];
            String prep = methodBody(src, "public void prepare()"), vet = methodBody(src, "public void becomeVeteran()");
            check("L17 " + c[0] + " prepare() dresses and sizes it normal", prep.contains("FodderLook." + c[2] + "(this)") && prep.contains("FodderLook.applyScale(this, FodderLook." + c[3] + ", false)"), "");
            check("L18 " + c[0] + " becomeVeteran() sizes it veteran and does not dress again", vet.contains("FodderLook.applyScale(this, FodderLook." + c[3] + ", true)") && !vet.contains("FodderLook.dress"), "");
        }
        System.out.println(fails == 0 ? "ALL PASS" : "FAILED " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }
}
