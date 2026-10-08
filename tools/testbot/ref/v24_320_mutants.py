#!/usr/bin/env python3
"""Mutants for HudLayout.weaponBoxFits, run on COPIES in a temp dir (the real source is never touched).
Each mutant changes one statement. A mutant counts as PROVEN at 320x240 only if a Q (320x240) check fails, not merely any check."""
import os, shutil, subprocess, tempfile
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
SRC = os.path.join(ROOT, "src/main/java/com/solme/emberfall/relic/HudLayout.java")
CHK = os.path.join(ROOT, "tools/testbot/relic_math/HudLayoutCheck.java")
J = os.environ.get("JAVA_HOME", "/opt/jdk25") + "/bin/"
orig = open(SRC).read()
FIT = "return !weapon.overlaps(timer) && !weapon.overlaps(stats);"
M = [
 ("weaponBoxFits: always true (Koda's mutant)", FIT, "return true;"),
 ("weaponBoxFits: always false (never drawn)", FIT, "return false;"),
 ("weaponBoxFits: ignores the timer", FIT, "return !weapon.overlaps(stats);"),
 # KNOWN EQUIVALENT: weaponBox() places the box at stats.right() + GAP (HudLayout.java line 45), so it never overlaps the stats panel for any input.
 # Searched 3,067,812 (stats width, stats height, rows, widest) cases: 0 overlaps. No check can tell this mutant from the original.
 ("weaponBoxFits: ignores the stats panel (EQUIVALENT)", FIT, "return !weapon.overlaps(timer);"),
 ("weaponBoxFits: timer test inverted", FIT, "return weapon.overlaps(timer) && !weapon.overlaps(stats);"),
 ("weaponBoxFits: OR instead of AND", FIT, "return !weapon.overlaps(timer) || !weapon.overlaps(stats);"),
 # LIMIT, not a bug in the rule: caught only by the older O1 and one other check. At 320x240 the timer plate is far from the weapon box in every
 # sampled layout, so a 4 px error in the timer edge changes no 320x240 outcome. The Q checks do not cover this kind of small error.
 ("weaponBoxFits: timer shrunk by 4 px (late hide)", FIT, "return !weapon.overlaps(new Box(timer.x() + 4, timer.y(), timer.w() - 4, timer.h())) && !weapon.overlaps(stats);"),
]
res = []
for i, (name, a, b) in enumerate(M):
    if orig.count(a) != 1:
        res.append((name, "NOT APPLIED (matched %d times)" % orig.count(a))); continue
    d = tempfile.mkdtemp(prefix="q%d_" % i)
    pkg = os.path.join(d, "src/com/solme/emberfall/relic"); os.makedirs(pkg)
    open(os.path.join(pkg, "HudLayout.java"), "w").write(orig.replace(a, b, 1))
    shutil.copy(CHK, d)
    out = os.path.join(d, "out"); os.makedirs(out)
    c = subprocess.run([J+"javac", "-nowarn", "-sourcepath", os.path.join(d, "src"), "-d", out, os.path.join(d, "HudLayoutCheck.java")], capture_output=True, text=True)
    if c.returncode != 0:
        res.append((name, "DID NOT BUILD")); continue
    r = subprocess.run([J+"java", "-cp", out, "HudLayoutCheck"], capture_output=True, text=True)
    fails = [l.split(" (")[0][:60] for l in r.stdout.splitlines() if l.startswith("FAIL")]
    q = [f for f in fails if f.startswith("FAIL Q") or f.startswith("FAIL N320")]
    if "ALL PASS" in r.stdout and r.returncode == 0:
        res.append((name, "GREEN (survived)"))
    elif q:
        res.append((name, "red (%d fail; 320x240 checks: %s)" % (len(fails), ", ".join(x.split()[1] for x in q))))
    else:
        res.append((name, "red (%d fail, NONE from the 320x240 checks: %s)" % (len(fails), ", ".join(x.split()[1] for x in fails[:4]))))
w = max(len(n) for n, _ in res)
for n, s in res: print(n.ljust(w), s)
print()
print("run %d, red %d, green %d, not applied %d, did not build %d, caught by a 320x240 check %d" % (len(res),
      sum(1 for _, s in res if s.startswith("red")), sum(1 for _, s in res if s.startswith("GREEN")),
      sum(1 for _, s in res if s.startswith("NOT APPLIED")), sum(1 for _, s in res if s == "DID NOT BUILD"),
      sum(1 for _, s in res if "320x240 checks:" in s and "NONE" not in s)))
