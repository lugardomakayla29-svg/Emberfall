#!/usr/bin/env python3
"""Mutants for HudLayout.markerColumn / markerOffset, run on COPIES in a temp dir (the real source is never touched).
Each mutant changes one statement. A mutant counts as PROVEN only if one of the NEW 'I' checks fails, not merely any check."""
import os, shutil, subprocess, tempfile
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
SRC = os.path.join(ROOT, "src/main/java/com/solme/emberfall/relic/HudLayout.java")
CHK = os.path.join(ROOT, "tools/testbot/relic_math/HudLayoutCheck.java")
J = os.environ.get("JAVA_HOME", "/opt/jdk25") + "/bin/"
orig = open(SRC).read()
COL = "int w = Math.max(0, minWidth);"
M = [
 ("markerColumn: ignores the glyphs", "w = Math.max(w, g);", "w = w;"),
 ("markerColumn: takes the narrowest, not the widest", "w = Math.max(w, g);", "w = Math.min(w, g);"),
 ("markerColumn: only the first glyph counts", "for (int g : glyphWidths) {\n            w = Math.max(w, g);\n        }", "if (glyphWidths.length > 0) {\n            w = Math.max(w, glyphWidths[0]);\n        }"),
 ("markerColumn: drops the minimum (can shrink under 5)", COL, "int w = 0;"),
 ("markerColumn: negative minimum not clamped", COL, "int w = minWidth;"),
 ("markerColumn: minimum is added, not a floor", "w = Math.max(w, g);", "w = w + g;"),
 ("markerColumn: always one pixel wider", "return w;\n    }\n\n    /**\n     * How far", "return w + 1;\n    }\n\n    /**\n     * How far"),
 ("markerColumn: negative glyph width wins", "w = Math.max(w, g);", "w = Math.max(w, Math.abs(g));"),
 ("markerOffset: not centred (always 0)", "return Math.max(0, (column - Math.max(0, glyphWidth)) / 2);", "return 0;"),
 ("markerOffset: right aligned", "return Math.max(0, (column - Math.max(0, glyphWidth)) / 2);", "return Math.max(0, column - Math.max(0, glyphWidth));"),
 ("markerOffset: rounds up", "return Math.max(0, (column - Math.max(0, glyphWidth)) / 2);", "return Math.max(0, (column - Math.max(0, glyphWidth) + 1) / 2);"),
 ("markerOffset: can go negative", "return Math.max(0, (column - Math.max(0, glyphWidth)) / 2);", "return (column - Math.max(0, glyphWidth)) / 2;"),
 ("markerOffset: negative glyph width not clamped", "(column - Math.max(0, glyphWidth)) / 2", "(column - glyphWidth) / 2"),
 ("markerOffset: divides by 3", "(column - Math.max(0, glyphWidth)) / 2", "(column - Math.max(0, glyphWidth)) / 3"),
 ("markerOffset: ignores the column", "return Math.max(0, (column - Math.max(0, glyphWidth)) / 2);", "return Math.max(0, (8 - Math.max(0, glyphWidth)) / 2);"),
]
res = []
for i, (name, a, b) in enumerate(M):
    if orig.count(a) != 1:
        res.append((name, "NOT APPLIED (matched %d times)" % orig.count(a))); continue
    d = tempfile.mkdtemp(prefix="mk%d_" % i)
    pkg = os.path.join(d, "src/com/solme/emberfall/relic"); os.makedirs(pkg)
    open(os.path.join(pkg, "HudLayout.java"), "w").write(orig.replace(a, b, 1))
    shutil.copy(CHK, d)
    out = os.path.join(d, "out"); os.makedirs(out)
    c = subprocess.run([J+"javac", "-nowarn", "-sourcepath", os.path.join(d, "src"), "-d", out, os.path.join(d, "HudLayoutCheck.java")], capture_output=True, text=True)
    if c.returncode != 0:
        res.append((name, "DID NOT BUILD")); continue
    r = subprocess.run([J+"java", "-cp", out, "HudLayoutCheck"], capture_output=True, text=True)
    fails = [l for l in r.stdout.splitlines() if l.startswith("FAIL")]
    new = [l for l in fails if l.startswith("FAIL I")]
    if "ALL PASS" in r.stdout and r.returncode == 0:
        res.append((name, "GREEN (survived)"))
    elif new:
        res.append((name, "red (%d fail, %d from the NEW I checks)" % (len(fails), len(new))))
    else:
        res.append((name, "red (%d fail, 0 from the NEW checks)  <-- only OLD checks caught it" % len(fails)))
w = max(len(n) for n, _ in res)
for n, s in res: print(n.ljust(w), s)
print()
print("run %d, red %d, green %d, not applied %d, did not build %d, caught by a NEW check %d" % (len(res),
      sum(1 for _, s in res if s.startswith("red")), sum(1 for _, s in res if s.startswith("GREEN")),
      sum(1 for _, s in res if s.startswith("NOT APPLIED")), sum(1 for _, s in res if s == "DID NOT BUILD"),
      sum(1 for _, s in res if "from the NEW I" in s)))
