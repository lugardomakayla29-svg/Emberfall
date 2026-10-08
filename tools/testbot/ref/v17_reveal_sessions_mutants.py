# Usage (from the repo root): python3 tools/testbot/ref/v17_reveal_sessions_mutants.py [M1 M5 ...]
# Applies each mutant to a COPY of ChestRevealSessions.java under a fresh temp dir (the repo file is never edited), compiles
# ChestRevealSessionsCheck against it and reports RED or STILL GREEN. A pattern that does not match exactly once is reported as
# NOT APPLIED, so a mutant can never silently be a no-op.
import subprocess, os, sys, tempfile
ROOT=os.getcwd()
SRC=ROOT+'/src/main/java/com/solme/emberfall/relic/ChestRevealSessions.java'
CHK=ROOT+'/tools/testbot/relic_math/ChestRevealSessionsCheck.java'
SP=ROOT+'/src/main/java'
JAVA=os.environ.get('JAVA_HOME','/opt/jdk25')+'/bin'
WORK=tempfile.mkdtemp(prefix='v17_rs_')
orig=open(SRC).read()
M=[
 # the forged close
 ('close honours any id (id not checked)', 'if (o == null || o.id() != id) {', 'if (o == null) {'),
 ('close honours a player with no reveal', 'if (o == null || o.id() != id) {\n            return false;\n        }', 'if (o != null && o.id() != id) {\n            return false;\n        }'),
 ('close honours a wrong id but still only for a real reveal (id ignored after the null check)', 'if (o == null || o.id() != id) {\n            return false;\n        }', 'if (o == null) {\n            return false;\n        }'),
 ('close looks at the first reveal of anyone, not the caller', 'Open o = open.get(player);\n        if (o == null || o.id() != id) {', 'Open o = open.values().stream().findFirst().orElse(null);\n        if (o == null || o.id() != id) {'),
 ('a good close does not remove the reveal (replay works)', 'open.remove(player);\n        return true;\n    }', 'return true;\n    }'),
 ('a wrong-id close removes the reveal', 'if (o == null || o.id() != id) {\n            return false;\n        }', 'if (o == null || o.id() != id) {\n            open.remove(player);\n            return false;\n        }'),
 # M7 and M26 below are KNOWN EQUIVALENT mutants, proven by hashing the results of 200000 random operations (opens, closes with null players and random ids,
 # isOpen, count, sweep, random clocks) on original and mutant: identical. M7: HashMap.get(null) returns null, so a null player is ignored either way.
 # M26: a late close that leaves the dead reveal in the map is invisible through the public API (it still reads as expired); the cost is memory until sweep().
 # Both are expected to stay green.
 ('null player crashes instead of being ignored', 'if (player == null) {\n            return false;\n        }\n        Open o = open.get(player);', 'Open o = open.get(player);'),
 # ids and one-at-a-time
 ('ids start at 0', 'private int nextId = 1;', 'private int nextId = 0;'),
 ('every reveal gets id 1', 'Open o = new Open(nextId++, tier, item, seed, now);', 'Open o = new Open(1, tier, item, seed, now);'),
 ('ids skip', 'Open o = new Open(nextId++, tier, item, seed, now);', 'Open o = new Open(nextId += 2, tier, item, seed, now);'),
 ('a second open keeps the first (does not replace)', 'open.put(player, o);\n        return o;', 'open.putIfAbsent(player, o);\n        return o;'),
 ('opens share one slot for all players', 'open.put(player, o);\n        return o;', 'open.clear();\n        open.put(new UUID(0, 0), o);\n        return o;'),
 ('open stores a different seed', 'Open o = new Open(nextId++, tier, item, seed, now);', 'Open o = new Open(nextId++, tier, item, seed + 1, now);'),
 ('open swaps tier and item', 'Open o = new Open(nextId++, tier, item, seed, now);', 'Open o = new Open(nextId++, item, tier, seed, now);'),
 # refusals
 ('null player accepted on open', 'if (player == null) {\n            throw new IllegalArgumentException("player is null");\n        }', ''),
 ('null tier accepted', 'if (tier == null || tier.isEmpty() || item == null || item.isEmpty()) {', 'if (tier == null || item == null || item.isEmpty()) {'),
 ('empty item accepted', 'if (tier == null || tier.isEmpty() || item == null || item.isEmpty()) {', 'if (tier == null || tier.isEmpty() || item == null) {'),
 ('a refused open still uses an id', 'if (player == null) {\n            throw new IllegalArgumentException("player is null");\n        }', 'nextId++;\n        if (player == null) {\n            throw new IllegalArgumentException("player is null");\n        }'),
 # expiry
 ('expiry 601 ticks', 'public static final long MAX_OPEN_TICKS = 20L * 30L;', 'public static final long MAX_OPEN_TICKS = 20L * 30L + 1;'),
 ('expiry 599 ticks', 'public static final long MAX_OPEN_TICKS = 20L * 30L;', 'public static final long MAX_OPEN_TICKS = 20L * 30L - 1;'),
 ('expiry off by one (> not >=)', 'return now >= o.openedAt() && now - o.openedAt() >= MAX_OPEN_TICKS;', 'return now >= o.openedAt() && now - o.openedAt() > MAX_OPEN_TICKS;'),
 ('never expires', 'return now >= o.openedAt() && now - o.openedAt() >= MAX_OPEN_TICKS;', 'return false;'),
 ('always expired', 'return now >= o.openedAt() && now - o.openedAt() >= MAX_OPEN_TICKS;', 'return true;'),
 ('a backwards clock expires a reveal', 'return now >= o.openedAt() && now - o.openedAt() >= MAX_OPEN_TICKS;', 'return Math.abs(now - o.openedAt()) >= MAX_OPEN_TICKS;'),
 ('close ignores expiry (honours a dead reveal)', 'if (expired(o, now)) {\n            // Expired: it is gone, but a late close is not an honoured close.\n            open.remove(player);\n            return false;\n        }', ''),
 ('a late close leaves the dead reveal', 'if (expired(o, now)) {\n            // Expired: it is gone, but a late close is not an honoured close.\n            open.remove(player);\n            return false;\n        }', 'if (expired(o, now)) {\n            return false;\n        }'),
 ('isOpen ignores expiry', 'return o != null && !expired(o, now);\n    }\n\n    /** The open reveal', 'return o != null;\n    }\n\n    /** The open reveal'),
 ('get returns an expired reveal', 'return o != null && !expired(o, now) ? o : null;', 'return o;'),
 ('count counts dead reveals', 'if (!expired(o, now)) {\n                n++;', 'if (true) {\n                n++;'),
 ('sweep removes nothing', 'open.values().removeIf(o -> expired(o, now));', ''),
 ('sweep removes everything', 'open.values().removeIf(o -> expired(o, now));', 'open.values().removeIf(o -> true);'),
 # forget and clear
 ('forget forgets everyone', 'public void forget(UUID player) {\n        open.remove(player);', 'public void forget(UUID player) {\n        open.clear();'),
 ('forget forgets nobody', 'public void forget(UUID player) {\n        open.remove(player);', 'public void forget(UUID player) {'),
 ('clear leaves reveals open', 'public void clear() {\n        open.clear();', 'public void clear() {'),
 ('clear resets the ids (an old id can close a new reveal)', 'public void clear() {\n        open.clear();', 'public void clear() {\n        open.clear();\n        nextId = 1;'),
]
only=sys.argv[1:]
res=[]
for i,(name,old,new) in enumerate(M,1):
    tag='M%d'%i
    if only and tag not in only: continue
    n=orig.count(old)
    if n!=1:
        print('%s: PATTERN MATCHED %d TIMES (need exactly 1), NOT APPLIED: %s'%(tag,n,name)); res.append('NA'); continue
    d='%s/%s'%(WORK,tag); os.makedirs(d+'/src/com/solme/emberfall/relic',exist_ok=True)
    open(d+'/src/com/solme/emberfall/relic/ChestRevealSessions.java','w').write(orig.replace(old,new))
    c=subprocess.run([JAVA+'/javac','-sourcepath',d+'/src:'+SP,'-d',d+'/out',CHK],capture_output=True,text=True)
    if c.returncode!=0:
        print('%s COMPILE ERROR (%s): %s'%(tag,name,c.stderr.strip().splitlines()[0][:110])); res.append('CE'); continue
    r=subprocess.run([JAVA+'/java','-cp',d+'/out','ChestRevealSessionsCheck'],capture_output=True,text=True,timeout=120)
    red=[l[5:].split('  ')[0] for l in r.stdout.splitlines() if l.startswith('FAIL ')]
    crashed = r.returncode!=0 and not red
    v='RED' if (r.returncode!=0 and (red or crashed)) else 'STILL GREEN'
    print('%s %s -> %s, exit %d, %d red%s: %s'%(tag,name,v,r.returncode,len(red),' (crash)' if crashed else '','; '.join(x[:50] for x in red[:2])))
    res.append(v)
print('SUMMARY mutants=%d red=%d green=%d not_applied_or_error=%d'%(len(res),res.count('RED'),res.count('STILL GREEN'),res.count('NA')+res.count('CE')))
