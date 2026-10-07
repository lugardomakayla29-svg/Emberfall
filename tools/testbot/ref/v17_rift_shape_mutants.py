import subprocess, os, sys, re
# Usage (from the repo root): python3 tools/testbot/ref/v17_rift_shape_mutants.py [M1 M5 ...]
# Applies each mutant to a COPY of RiftShape.java under a fresh temp dir (the repo file is never edited), compiles RiftShapeCheck
# against it and reports RED (check failed, exit non-zero) or STILL GREEN. A pattern that does not match exactly once is
# reported as NOT APPLIED, so a mutant can never silently be a no-op.
import tempfile
ROOT=os.getcwd()
SRC=ROOT+'/src/main/java/com/solme/emberfall/rift/RiftShape.java'
CHK=ROOT+'/tools/testbot/relic_math/RiftShapeCheck.java'
JAVA=os.environ.get('JAVA_HOME','/opt/jdk25')+'/bin'
WORK=tempfile.mkdtemp(prefix='v17_mut_')
orig=open(SRC).read()
# (name, exact old text, new text)
M=[
 ('M1 wing steps not forced to overlap', "int yMin = Math.max(1, prevLo - h + 1);\n            int yMax = Math.min(BOX_H - 1 - h, prevHi);", "int yMin = 1;\n            int yMax = BOX_H - 1 - h;"),
 ('M2 satellites may touch the body', "if (gap < 2 || gap > SATELLITE_MAX_GAP) {", "if (gap > SATELLITE_MAX_GAP) {"),
 ('M3 rotation drops one cell', "b[y][x] = body[x][y];\n                    s[y][x] = satellite[x][y];", "b[y][x] = body[x][y] && !(x == 4 && y == 4);\n                    s[y][x] = satellite[x][y];"),
 ('M4 the seed is ignored', "        Rng r = new Rng(seed);\n        boolean[][] body", "        Rng r = new Rng(7L);\n        boolean[][] body"),
 ('M5 width and height rule removed', "return sh.bodySpanX() >= MIN_SPAN_X && sh.bodySpanY() > sh.bodySpanX();", "return true;"),
 ('M6 rim includes interior cells', "if (isCell(x, y) && (!isCell(x - 1, y) || !isCell(x + 1, y) || !isCell(x, y - 1) || !isCell(x, y + 1))) {", "if (isCell(x, y)) {"),
 ('M7 rim misses the top side', "if (isCell(x, y) && (!isCell(x - 1, y) || !isCell(x + 1, y) || !isCell(x, y - 1) || !isCell(x, y + 1))) {", "if (isCell(x, y) && (!isCell(x - 1, y) || !isCell(x + 1, y) || !isCell(x, y - 1))) {"),
 ('M8 nondeterministic (clock in the seed)', "        Rng r = new Rng(seed);\n        boolean[][] body", "        Rng r = new Rng(seed ^ System.nanoTime());\n        boolean[][] body"),
 ('M9 satellites may float anywhere', "if (gap < 2 || gap > SATELLITE_MAX_GAP) {", "if (gap < 2) {"),
 ('M10 body may be empty (column height 0)', "fill(body, colX, colY, colW, colH);", "fill(body, colX, colY, colW, 0);"),
 ('M11 bodyPieces counts diagonals as connected', "int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};\n                for (int[] k : d) {\n                    int nx = x + k[0];", "int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};\n                for (int[] k : d) {\n                    int nx = x + k[0];"),
 ('M12 satellites may touch the body (the REAL guard removed)', "if (touches(body, x, y) || touches(sat, x, y)) {", "if (touches(sat, x, y)) {"),
 ('M13 satellites may touch each other', "if (touches(body, x, y) || touches(sat, x, y)) {", "if (touches(body, x, y)) {"),
 ('M14 satellites may touch the body (BOTH guards removed)', "if (touches(body, x, y) || touches(sat, x, y)) {\n                continue;\n            }\n            int gap = nearestBody(body, x, y);\n            if (gap < 2 || gap > SATELLITE_MAX_GAP) {", "if (touches(sat, x, y)) {\n                continue;\n            }\n            int gap = nearestBody(body, x, y);\n            if (gap > SATELLITE_MAX_GAP) {"),
]
only=sys.argv[1:]
res=[]
for i,(name,old,new) in enumerate(M,1):
    tag='M%d'%i
    if only and tag not in only: continue
    n=orig.count(old)
    if n!=1:
        print('%s: PATTERN MATCHED %d TIMES (need exactly 1), skipped'%(tag,n)); res.append((name,'NOT APPLIED',0,0)); continue
    d='%s/%s'%(WORK,tag); os.makedirs(d+'/src/com/solme/emberfall/rift',exist_ok=True)
    open(d+'/src/com/solme/emberfall/rift/RiftShape.java','w').write(orig.replace(old,new))
    assert open(d+'/src/com/solme/emberfall/rift/RiftShape.java').read()!=orig
    c=subprocess.run([JAVA+'/javac','-sourcepath',d+'/src','-d',d+'/out',CHK],capture_output=True,text=True)
    if c.returncode!=0:
        print('%s: COMPILE ERROR %s'%(tag,c.stderr[:200])); res.append((name,'COMPILE ERROR',0,0)); continue
    r=subprocess.run([JAVA+'/java','-cp',d+'/out','RiftShapeCheck'],capture_output=True,text=True,timeout=120)
    out=r.stdout
    red=[l[5:].split('  ')[0] for l in out.splitlines() if l.startswith('FAIL')]
    verdict='RED' if (r.returncode!=0 and red) else 'STILL GREEN'
    print('%s %s -> %s, exit %d, %d red: %s'%(tag,name,verdict,r.returncode,len(red),'; '.join(x[:60] for x in red[:4])))
    res.append((name,verdict,len(red),r.returncode))
print('SUMMARY applied=%d red=%d green=%d'%(sum(1 for r in res if r[1] in('RED','STILL GREEN')),sum(1 for r in res if r[1]=='RED'),sum(1 for r in res if r[1]=='STILL GREEN')))
