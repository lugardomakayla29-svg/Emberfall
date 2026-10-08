#!/usr/bin/env python3
"""Mutants for HudLayout, run on COPIES in a temp dir (the real source is never touched).
Each mutant changes one statement; the check must go red (a FAIL line or a non-zero exit)."""
import os, re, shutil, subprocess, sys, tempfile
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
SRC = os.path.join(ROOT, "src/main/java/com/solme/emberfall/relic/HudLayout.java")
CHK = os.path.join(ROOT, "tools/testbot/relic_math/HudLayoutCheck.java")
J = os.environ.get("JAVA_HOME", "/opt/jdk25") + "/bin/"
orig = open(SRC).read()

M = [
 ("overlaps: < to <= (touching counts)", "return x < o.right() && o.x < right() && y < o.bottom() && o.y < bottom();", "return x <= o.right() && o.x <= right() && y <= o.bottom() && o.y <= bottom();"),
 ("overlaps: drops the y test", "return x < o.right() && o.x < right() && y < o.bottom() && o.y < bottom();", "return x < o.right() && o.x < right();"),
 ("overlaps: drops the x test", "return x < o.right() && o.x < right() && y < o.bottom() && o.y < bottom();", "return y < o.bottom() && o.y < bottom();"),
 ("weaponBox: no gap", "return new Box(stats.right() + GAP, stats.y(), w, h);", "return new Box(stats.right(), stats.y(), w, h);"),
 ("weaponBox: starts inside the stats panel", "return new Box(stats.right() + GAP, stats.y(), w, h);", "return new Box(stats.right() - 8, stats.y(), w, h);"),
 ("weaponBox: wrong top edge", "return new Box(stats.right() + GAP, stats.y(), w, h);", "return new Box(stats.right() + GAP, stats.y() + 3, w, h);"),
 ("weaponBox: row cap removed", "int n = Math.max(0, Math.min(weapons, MAX_WEAPON_ROWS));", "int n = Math.max(0, weapons);"),
 ("weaponBox: negative rows allowed", "int n = Math.max(0, Math.min(weapons, MAX_WEAPON_ROWS));", "int n = Math.min(weapons, MAX_WEAPON_ROWS);"),
 ("weaponBox: title row dropped", "int h = padding * 2 + (n + 1) * rowH - 2;", "int h = padding * 2 + n * rowH - 2;"),
 ("weaponBox: cap applied to title too", "int h = padding * 2 + (n + 1) * rowH - 2;", "int h = padding * 2 + Math.min(n + 1, MAX_WEAPON_ROWS) * rowH - 2;"),
 ("weaponBox: trim dropped", "int h = padding * 2 + (n + 1) * rowH - 2;", "int h = padding * 2 + (n + 1) * rowH;"),
 ("weaponBox: padding on one side only", "int w = padding * 2 + widestRow;", "int w = padding + widestRow;"),
 ("weaponBoxFits: ignores the timer", "return !weapon.overlaps(timer) && !weapon.overlaps(stats);", "return !weapon.overlaps(stats);"),
 ("weaponBoxFits: ignores the stats panel", "return !weapon.overlaps(timer) && !weapon.overlaps(stats);", "return !weapon.overlaps(timer);"),
 ("weaponBoxFits: always true", "return !weapon.overlaps(timer) && !weapon.overlaps(stats);", "return true;"),
 ("relicBox: overlaps the stats panel", "return new Box(stats.x(), stats.bottom() + GAP, w, h);", "return new Box(stats.x(), stats.bottom() - 2, w, h);"),
 ("timerBox: not centred", "return new Box(screenW / 2 - w / 2, MARGIN, w, h);", "return new Box(screenW / 2, MARGIN, w, h);"),
 ("autoScale: threshold 320 to 300", "while (windowW / (s + 1) >= 320 && windowH / (s + 1) >= 240) {", "while (windowW / (s + 1) >= 300 && windowH / (s + 1) >= 240) {"),
 ("autoScale: height test dropped", "while (windowW / (s + 1) >= 320 && windowH / (s + 1) >= 240) {", "while (windowW / (s + 1) >= 320) {"),
 ("relicRowsShown: cap ignored", "return Math.max(0, Math.min(owned, cap));", "return Math.max(0, owned);"),
 ("relicBoxHeight: no +N more row", "(hidden > 0 ? rowH : 0);\n    }", "0;\n    }"),
 ("relicBoxHeight: -2 dropped", "rowH * (rows + 1) - 2 +", "rowH * (rows + 1) +"),
 ("loadoutTop: wrong margin", "return screenH - 4 - (cell * 2 + 3);", "return screenH - 8 - (cell * 2 + 3);"),
 ("loadoutTop: row gap wrong", "return screenH - 4 - (cell * 2 + 3);", "return screenH - 4 - (cell * 2);"),
 ("loadoutTop: hide threshold 16 to 8", "if (cell < 16) {", "if (cell < 8) {"),
 ("loadoutTop: cell max 30 to 40", "int cell = Math.min(30, (free - 2 * 3) / 4);", "int cell = Math.min(40, (free - 2 * 3) / 4);"),
 ("relicRowsFit: never shrinks", "while (rows > 1 && top + relicBoxHeight(owned, rows, rowH, padding) > limit) {", "while (rows > 1 && false) {"),
 ("relicRowsFit: shrinks to zero", "while (rows > 1 &&", "while (rows > 0 &&"),
 ("relicRowsFit: off by one (> to >=)", "relicBoxHeight(owned, rows, rowH, padding) > limit) {", "relicBoxHeight(owned, rows, rowH, padding) >= limit) {"),
 ("relicRowsFit: cap ignored", "int rows = Math.min(cap, owned);\n        while", "int rows = owned;\n        while"),
]
res = []
for i, (name, a, b) in enumerate(M):
    if a not in orig:
        res.append((name, "NOT APPLIED")); continue
    d = tempfile.mkdtemp(prefix="hlm%d_" % i)
    pkg = os.path.join(d, "src/com/solme/emberfall/relic"); os.makedirs(pkg)
    open(os.path.join(pkg, "HudLayout.java"), "w").write(orig.replace(a, b, 1))
    shutil.copy(CHK, d)
    out = os.path.join(d, "out"); os.makedirs(out)
    c = subprocess.run([J+"javac", "-nowarn", "-sourcepath", os.path.join(d, "src"), "-d", out, os.path.join(d, "HudLayoutCheck.java")], capture_output=True, text=True)
    if c.returncode != 0:
        res.append((name, "DID NOT BUILD")); continue
    r = subprocess.run([J+"java", "-cp", out, "HudLayoutCheck"], capture_output=True, text=True)
    fails = [l for l in r.stdout.splitlines() if l.startswith("FAIL")]
    ok = "ALL PASS" in r.stdout and r.returncode == 0
    res.append((name, "GREEN (survived)" if ok else "red (%d fail)" % len(fails)))
w = max(len(n) for n, _ in res)
for n, s in res: print(n.ljust(w), s)
print()
print("run %d, red %d, green %d, not applied %d, did not build %d" % (len(res),
      sum(1 for _, s in res if s.startswith("red")), sum(1 for _, s in res if s.startswith("GREEN")),
      sum(1 for _, s in res if s == "NOT APPLIED"), sum(1 for _, s in res if s == "DID NOT BUILD")))
