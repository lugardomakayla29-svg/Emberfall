#!/usr/bin/env python3
"""Mutants for the Task 4 reveal checks (Koda 13:55 CT), run on COPIES in a temp dir. The real source is never touched.
Each mutant changes one statement in ChestRevealClock or ChestRevealView; the named check must go red (a FAIL line or a non-zero exit).
A mutant that survives means a new check does not guard what it says."""
import os, shutil, subprocess, tempfile
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
REL = os.path.join(ROOT, "src/main/java/com/solme/emberfall")
CHK = os.path.join(ROOT, "tools/testbot/relic_math")
J = os.environ.get("JAVA_HOME", "/opt/jdk25") + "/bin/"
CLOCK = "relic/ChestRevealClock.java"
VIEW = "relic/ChestRevealView.java"

# (name, file, original text, mutated text, check class that must go red)
M = [
 # case 1/3: extremes and ordering
 ("clock: animationTick lets a negative tick through", CLOCK, "return Math.max(0, Math.min(screenTicks, ChestReveal.TOTAL_TICKS));", "return Math.min(screenTicks, ChestReveal.TOTAL_TICKS);", "ChestRevealClockCheck"),
 ("clock: animationTick has no upper bound", CLOCK, "return Math.max(0, Math.min(screenTicks, ChestReveal.TOTAL_TICKS));", "return Math.max(0, screenTicks);", "ChestRevealClockCheck"),
 ("clock: finished is true for negative ticks", CLOCK, "return screenTicks >= ChestReveal.TOTAL_TICKS;\n    }\n\n    /** True when the screen should close", "return screenTicks >= ChestReveal.TOTAL_TICKS || screenTicks < 0;\n    }\n\n    /** True when the screen should close", "ChestRevealClockCheck"),
 ("clock: autoClose is true for negative ticks", CLOCK, "return screenTicks >= ChestReveal.TOTAL_TICKS + AUTO_CLOSE_AFTER_END;", "return screenTicks >= ChestReveal.TOTAL_TICKS + AUTO_CLOSE_AFTER_END || screenTicks < 0;", "ChestRevealClockCheck"),
 ("clock: onPress closes on a negative tick", CLOCK, "if (finished(screenTicks)) {\n            return Press.CLOSE;", "if (finished(screenTicks) || screenTicks < 0) {\n            return Press.CLOSE;", "ChestRevealClockCheck"),
 # case 2: monotone (a value that turns itself off again)
 ("clock: finished turns off again after 500 ticks", CLOCK, "return screenTicks >= ChestReveal.TOTAL_TICKS;\n    }\n\n    /** True when the screen should close", "return screenTicks >= ChestReveal.TOTAL_TICKS && screenTicks < 500;\n    }\n\n    /** True when the screen should close", "ChestRevealClockCheck"),
 ("clock: autoClose turns off again after 600 ticks", CLOCK, "return screenTicks >= ChestReveal.TOTAL_TICKS + AUTO_CLOSE_AFTER_END;", "return screenTicks >= ChestReveal.TOTAL_TICKS + AUTO_CLOSE_AFTER_END && screenTicks < 600;", "ChestRevealClockCheck"),
 # case 3: auto close before the reveal is finished
 ("clock: autoClose fires at tick 100 (before finished)", CLOCK, "return screenTicks >= ChestReveal.TOTAL_TICKS + AUTO_CLOSE_AFTER_END;", "return screenTicks >= 100;", "ChestRevealClockCheck"),
 ("clock: finished one tick early (139)", CLOCK, "return screenTicks >= ChestReveal.TOTAL_TICKS;\n    }\n\n    /** True when the screen should close", "return screenTicks >= ChestReveal.TOTAL_TICKS - 1;\n    }\n\n    /** True when the screen should close", "ChestRevealClockCheck"),
 # case 5: onPress boundaries
 ("clock: SKIP starts at 41 instead of 40", CLOCK, "return screenTicks >= MIN_WATCH_TICKS ? Press.SKIP : Press.NONE;", "return screenTicks > MIN_WATCH_TICKS ? Press.SKIP : Press.NONE;", "ChestRevealClockCheck"),
 ("clock: SKIP starts at 39", CLOCK, "return screenTicks >= MIN_WATCH_TICKS ? Press.SKIP : Press.NONE;", "return screenTicks >= MIN_WATCH_TICKS - 1 ? Press.SKIP : Press.NONE;", "ChestRevealClockCheck"),
 ("clock: onPress never closes (SKIP forever)", CLOCK, "if (finished(screenTicks)) {\n            return Press.CLOSE;\n        }\n", "", "ChestRevealClockCheck"),
 ("clock: onPress closes at 110 instead of 140", CLOCK, "if (finished(screenTicks)) {\n            return Press.CLOSE;", "if (screenTicks >= ChestReveal.ITEM_STOP_TICK) {\n            return Press.CLOSE;", "ChestRevealClockCheck"),
 ("clock: skipTarget is the tier stop (40)", CLOCK, "return ChestReveal.ITEM_STOP_TICK;", "return ChestReveal.TIER_STOP_TICK;", "ChestRevealClockCheck"),
 # case 6/7/8/9: the view
 ("view: safeBuild rejects ONLY Common + a Rare item (the pinned pair)", VIEW, "return ChestReveal.build(tier, item, ChestRevealPools.tiers(), ChestRevealPools.items(), seed);",
  "for (Relic r : RelicPool.all()) { if (\"Common\".equals(tier) && r.name().equals(item) && r.rarity() == RelicRarity.RARE) { return null; } }\n            return ChestReveal.build(tier, item, ChestRevealPools.tiers(), ChestRevealPools.items(), seed);", "ChestRevealViewCheck"),
 ("view: safeBuild rejects a tier/item mismatch (pin broken)", VIEW, "return ChestReveal.build(tier, item, ChestRevealPools.tiers(), ChestRevealPools.items(), seed);",
  "for (Relic r : RelicPool.all()) { if (r.name().equals(item) && !r.rarity().label().equals(tier)) { return null; } }\n            return ChestReveal.build(tier, item, ChestRevealPools.tiers(), ChestRevealPools.items(), seed);", "ChestRevealViewCheck"),
 ("view: itemRgb colours from a fixed grey", VIEW, "return r.rarity().rgb();\n            }\n        }\n        return fallback;\n    }\n}", "return 0x9D9D9D;\n            }\n        }\n        return fallback;\n    }\n}", "ChestRevealViewCheck"),
 ("view: tierRgb Legendary is the Rare colour", VIEW, "if (r.label().equals(label)) {\n                return r.rgb();", "if (r.label().equals(label)) {\n                return r == RelicRarity.LEGENDARY ? RelicRarity.RARE.rgb() : r.rgb();", "ChestRevealViewCheck"),
 ("view: tierRgb ignores case", VIEW, "if (r.label().equals(label)) {\n                return r.rgb();", "if (r.label().equalsIgnoreCase(label)) {\n                return r.rgb();", "ChestRevealViewCheck"),
 ("view: tierRgb trims the label", VIEW, "if (r.label().equals(label)) {\n                return r.rgb();", "if (label != null && r.label().equals(label.trim())) {\n                return r.rgb();", "ChestRevealViewCheck"),
 ("view: itemRgb ignores case", VIEW, "if (r.name().equals(name)) {\n                return r.rarity().rgb();", "if (r.name().equalsIgnoreCase(name)) {\n                return r.rarity().rgb();", "ChestRevealViewCheck"),
 ("view: tierRgb throws on null", VIEW, "public static int tierRgb(String label, int fallback) {\n", "public static int tierRgb(String label, int fallback) {\n        label.length();\n", "ChestRevealViewCheck"),
 ("view: itemRgb throws on null", VIEW, "public static int itemRgb(String name, int fallback) {\n", "public static int itemRgb(String name, int fallback) {\n        name.length();\n", "ChestRevealViewCheck"),
 ("view: safeBuild throws on a negative seed", VIEW, "public static ChestReveal.Reveal safeBuild(String tier, String item, long seed) {\n        try {", "public static ChestReveal.Reveal safeBuild(String tier, String item, long seed) {\n        if (seed < 0) { throw new IllegalStateException(\"negative seed\"); }\n        try {", "ChestRevealViewCheck"),
 ("view: safeBuild returns null for seed 0", VIEW, "public static ChestReveal.Reveal safeBuild(String tier, String item, long seed) {\n        try {", "public static ChestReveal.Reveal safeBuild(String tier, String item, long seed) {\n        if (seed == 0L) { return null; }\n        try {", "ChestRevealViewCheck"),
]

# words that only appear in the checks added for Task 4
NEW_KEYS = ["extremes:", "monotone:", "auto close never happens", "at the last tick before auto close", "onPress boundary table",
            "PINNED:", "every relic: the colour of its tier", "tierRgb(null)", "itemRgb(null)", "tierRgb is case sensitive", "tierRgb does not trim",
            "itemRgb is case sensitive", "builds, lands on the truth"]
res = []
for i, (name, rel, a, b, chk) in enumerate(M):
    src = open(os.path.join(REL, rel)).read()
    if a not in src:
        res.append((name, "NOT APPLIED")); continue
    d = tempfile.mkdtemp(prefix="rem%d_" % i)
    shutil.copytree(os.path.join(REL, "relic"), os.path.join(d, "src/com/solme/emberfall/relic"))
    # copy any sibling package the relic classes import from, so the copy compiles on its own
    for pkg in os.listdir(REL):
        p = os.path.join(REL, pkg)
        if os.path.isdir(p) and pkg != "relic":
            shutil.copytree(p, os.path.join(d, "src/com/solme/emberfall", pkg))
    open(os.path.join(d, "src/com/solme/emberfall", rel), "w").write(src.replace(a, b, 1))
    shutil.copy(os.path.join(CHK, chk + ".java"), d)
    out = os.path.join(d, "out"); os.makedirs(out)
    c = subprocess.run([J + "javac", "-nowarn", "-sourcepath", os.path.join(d, "src"), "-d", out, os.path.join(d, chk + ".java")], capture_output=True, text=True)
    if c.returncode != 0:
        res.append((name, "DID NOT BUILD: " + c.stderr.strip().splitlines()[0][:90] if c.stderr.strip() else "DID NOT BUILD")); continue
    r = subprocess.run([J + "java", "-cp", out, chk], capture_output=True, text=True)
    fails = [l for l in r.stdout.splitlines() if l.startswith("FAIL")]
    ok = "ALL PASS" in r.stdout and r.returncode == 0
    new = [l for l in fails if any(k in l for k in NEW_KEYS)]
    if ok: res.append((name, "GREEN (survived)"))
    elif fails: res.append((name, "red (%d fail, %d from the NEW checks)" % (len(fails), len(new)) + ("" if new else "  <-- only OLD checks caught it")))
    else: res.append((name, "red (crash/exit %d)" % r.returncode))
w = max(len(n) for n, _ in res)
for n, s in res: print(n.ljust(w), s)
print()
print("run %d, red %d, green %d, not applied %d, did not build %d" % (len(res),
      sum(1 for _, s in res if s.startswith("red")), sum(1 for _, s in res if s.startswith("GREEN")),
      sum(1 for _, s in res if s == "NOT APPLIED"), sum(1 for _, s in res if s.startswith("DID NOT BUILD"))))
