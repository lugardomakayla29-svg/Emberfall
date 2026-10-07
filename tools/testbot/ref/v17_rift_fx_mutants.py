# Usage (from the repo root): python3 tools/testbot/ref/v17_rift_fx_mutants.py [M1 M5 ...]
# Applies each mutant to a COPY of RiftFx.java under a fresh temp dir (the repo file is never edited), compiles RiftFxCheck against
# it and reports RED (a check failed, exit non-zero) or STILL GREEN. A pattern that does not match exactly once is reported as
# NOT APPLIED, so a mutant can never silently be a no-op.
import subprocess, os, sys, tempfile
ROOT=os.getcwd()
SRC=ROOT+'/src/main/java/com/solme/emberfall/rift/RiftFx.java'
CHK=ROOT+'/tools/testbot/relic_math/RiftFxCheck.java'
SP=ROOT+'/src/main/java'
JAVA=os.environ.get('JAVA_HOME','/opt/jdk25')+'/bin'
WORK=tempfile.mkdtemp(prefix='v17_fx_')
orig=open(SRC).read()
M=[
 ('no bright point at tick 0', 'out.add(new Event(T_POINT, Kind.PARTICLE, "end_rod", c[0], c[1], RIM_HOT, 0, DENSITY_POINT));', ''),
 ('no chat warning at tick 0', 'out.add(new Event(T_POINT, Kind.CHAT, "rift_tearing", 0, 0, 0, 0));', ''),
 ('crack starts a second early', 'public static final int T_CRACK_START = 10;', 'public static final int T_CRACK_START = 0;'),
 ('crack runs past the flare', 'public static final int T_CRACK_END = 40;', 'public static final int T_CRACK_END = 60;'),
 ('crack grows INWARD (sorted far to near)', 'rim.sort(Comparator.comparingDouble((int[] r) -> dist(r, c)).thenComparingInt(r -> r[0]).thenComparingInt(r -> r[1]));', 'rim.sort(Comparator.comparingDouble((int[] r) -> -dist(r, c)).thenComparingInt(r -> r[0]).thenComparingInt(r -> r[1]));'),
 ('crack lights satellites too (the real bug)', 'if (shape.isBody(r[0], r[1])) {\n                rim.add(r);\n            }', 'rim.add(r);'),
 ('crack skips the last rim cell', 'for (int i = 0; i < n; i++) {\n            int tick = T_CRACK_START', 'for (int i = 0; i < n - 1; i++) {\n            int tick = T_CRACK_START'),
 ('crack lights every cell twice', 'out.add(new Event(tick, Kind.PARTICLE, "electric_spark", r[0], r[1], RIM_WARM, 0, DENSITY_CRACK));', 'out.add(new Event(tick, Kind.PARTICLE, "electric_spark", r[0], r[1], RIM_WARM, 0, DENSITY_CRACK));\n            out.add(new Event(tick, Kind.PARTICLE, "electric_spark", r[0], r[1], RIM_WARM, 0, DENSITY_CRACK));'),
 ('no boom', 'out.add(new Event(T_FLARE, Kind.SOUND, "rift_boom", c[0], c[1], 1.0f, 0.5f));', ''),
 ('boom a second late', 'out.add(new Event(T_FLARE, Kind.SOUND, "rift_boom", c[0], c[1], 1.0f, 0.5f));', 'out.add(new Event(T_FLARE + 20, Kind.SOUND, "rift_boom", c[0], c[1], 1.0f, 0.5f));'),
 ('flare all on ONE tick (no spread)', 'int tick = T_FLARE + (int) ((long) i * FLARE_SPREAD / n);', 'int tick = T_FLARE;'),
 ('flare too dense', 'public static final int DENSITY_FLARE = 3;', 'public static final int DENSITY_FLARE = 30;'),
 ('dust ring huge', 'public static final int DENSITY_DUST = 24;', 'public static final int DENSITY_DUST = 400;'),
 ('fill floods INWARD', 'fill.sort(Comparator.comparingDouble((int[] r) -> dist(r, c)).thenComparingInt(r -> r[0]).thenComparingInt(r -> r[1]));', 'fill.sort(Comparator.comparingDouble((int[] r) -> -dist(r, c)).thenComparingInt(r -> r[0]).thenComparingInt(r -> r[1]));'),
 ('fill starts early', 'public static final int T_FILL_START = 50;', 'public static final int T_FILL_START = 20;'),
 ('fill skips a cell', 'for (int i = 0; i < m; i++) {\n            int tick = T_FILL_START', 'for (int i = 0; i < m - 1; i++) {\n            int tick = T_FILL_START'),
 ('fill lights the rim too', 'if (shape.isBody(cell[0], cell[1]) && !isRim(rim, cell)) {', 'if (shape.isBody(cell[0], cell[1])) {'),
 ('no push', 'out.add(new Event(T_PULSE, Kind.PUSH, "rift_push", c[0], c[1], 0, 0));', ''),
 ('push at the wrong time', 'out.add(new Event(T_PULSE, Kind.PUSH, "rift_push", c[0], c[1], 0, 0));', 'out.add(new Event(T_PULSE - 30, Kind.PUSH, "rift_push", c[0], c[1], 0, 0));'),
 ('no hum', 'out.add(new Event(T_PULSE, Kind.SOUND, "rift_hum", c[0], c[1], 0.6f, 0.8f));', ''),
 ('no open line', 'out.add(new Event(OPEN_TICK, Kind.CHAT, "rift_open", 0, 0, 0, 0));', ''),
 ('open line twice', 'out.add(new Event(OPEN_TICK, Kind.CHAT, "rift_open", 0, 0, 0, 0));', 'out.add(new Event(OPEN_TICK, Kind.CHAT, "rift_open", 0, 0, 0, 0));\n        out.add(new Event(OPEN_TICK - 1, Kind.CHAT, "rift_open", 0, 0, 0, 0));'),
 ('opens a second late', 'public static final int OPEN_TICK = 100;', 'public static final int OPEN_TICK = 120;'),
 ('events not sorted', '        out.sort(Comparator.comparingInt((Event e) -> e.tick));\n        return out;\n    }\n\n    /**\n     * The closing', '        return out;\n    }\n\n    /**\n     * The closing'),
 ('an ENTITY kind exists', 'PUSH\n    }', 'PUSH,\n        /** BAD. */\n        SPAWN\n    }'),
 ('an entity key is used', 'out.add(new Event(T_POINT, Kind.SOUND, "rift_drone", c[0], c[1], 0.8f, 0.5f));', 'out.add(new Event(T_POINT, Kind.SOUND, "armor_stand", c[0], c[1], 0.8f, 0.5f));'),
 ('closing keeps the push', 'if (e.kind == Kind.PUSH) {\n                continue;\n            }', ''),
 ('closing as slow as the opening', 'public static final int CLOSE_TICKS = 34;', 'public static final int CLOSE_TICKS = 100;'),
 ('closing forward, not backward', 'int tick = (int) ((long) (OPEN_TICK - e.tick) * CLOSE_TICKS / OPEN_TICK);', 'int tick = (int) ((long) e.tick * CLOSE_TICKS / OPEN_TICK);'),
 ('closing has no collapse line', 'out.add(new Event(CLOSE_TICKS, Kind.CHAT, "rift_collapsed", 0, 0, 0, 0));', ''),
 ('closing drops its particles', 'out.add(new Event(tick, e.kind, e.key, e.x, e.y, e.a, e.b, e.count));', 'if (e.kind != Kind.PARTICLE) out.add(new Event(tick, e.kind, e.key, e.x, e.y, e.a, e.b, e.count));'),
 ('particle count ignored (always 0)', 'n += e.count;', 'n += 0;'),
 ('particle count counts events not particles', 'n += e.count;', 'n += 1;'),
 ('nondeterministic (clock)', 'int tick = T_CRACK_START + (int) ((long) i * (T_CRACK_END - T_CRACK_START) / n);', 'int tick = T_CRACK_START + (int) ((long) i * (T_CRACK_END - T_CRACK_START) / n) + (int) (System.nanoTime() % 2);'),
 ('empty shape crashes', 'if (shape.cells().isEmpty()) {\n            return out;\n        }', ''),
]
only=sys.argv[1:]
res=[]
for i,(name,old,new) in enumerate(M,1):
    tag='M%d'%i
    if only and tag not in only: continue
    n=orig.count(old)
    if n!=1:
        print('%s: PATTERN MATCHED %d TIMES (need exactly 1), NOT APPLIED: %s'%(tag,n,name)); res.append('NA'); continue
    d='%s/%s'%(WORK,tag); os.makedirs(d+'/src/com/solme/emberfall/rift',exist_ok=True)
    open(d+'/src/com/solme/emberfall/rift/RiftFx.java','w').write(orig.replace(old,new))
    c=subprocess.run([JAVA+'/javac','-sourcepath',d+'/src:'+SP,'-d',d+'/out',CHK],capture_output=True,text=True)
    if c.returncode!=0:
        print('%s: COMPILE ERROR (%s): %s'%(tag,name,c.stderr.strip().splitlines()[0][:110])); res.append('CE'); continue
    r=subprocess.run([JAVA+'/java','-cp',d+'/out','RiftFxCheck'],capture_output=True,text=True,timeout=120)
    red=[l[5:].split('  ')[0] for l in r.stdout.splitlines() if l.startswith('FAIL ')]
    crashed = r.returncode!=0 and not red
    v='RED' if (r.returncode!=0 and (red or crashed)) else 'STILL GREEN'
    print('%s %s -> %s, exit %d, %d red%s: %s'%(tag,name,v,r.returncode,len(red),' (crash)' if crashed else '','; '.join(x[:52] for x in red[:2])))
    res.append(v)
print('SUMMARY mutants=%d red=%d green=%d not_applied_or_error=%d'%(len(res),res.count('RED'),res.count('STILL GREEN'),res.count('NA')+res.count('CE')))
