# Usage (from the repo root): python3 tools/testbot/ref/v17_goo_mutants.py [M1 M5 ...]
# Applies each mutant to a COPY of GooGrid.java under a fresh temp dir (the repo file is never edited), compiles GooCheck against it
# and reports RED (a check failed, exit non-zero) or STILL GREEN. A pattern that does not match exactly once is reported as
# NOT APPLIED, so a mutant can never silently be a no-op.
import subprocess, os, sys, tempfile
ROOT=os.getcwd()
SRC=ROOT+'/src/main/java/com/solme/emberfall/entity/GooGrid.java'
CHK=ROOT+'/tools/testbot/relic_math/GooCheck.java'
SP=ROOT+'/src/main/java'
JAVA=os.environ.get('JAVA_HOME','/opt/jdk25')+'/bin'
WORK=tempfile.mkdtemp(prefix='v17_goo_')
orig=open(SRC).read()
M=[
 # the cap
 ('cap 401 not 400', 'public static final int MAX_CELLS = 400;', 'public static final int MAX_CELLS = 401;'),
 ('cap 399 not 400', 'public static final int MAX_CELLS = 400;', 'public static final int MAX_CELLS = 399;'),
 ('cap off by one (> not >=)', 'if (expiry.size() >= MAX_CELLS) {', 'if (expiry.size() > MAX_CELLS) {'),
 ('cap removed', 'if (expiry.size() >= MAX_CELLS) {\n            return false;\n        }', ''),
 ('full grid evicts instead of refusing', 'if (expiry.size() >= MAX_CELLS) {\n            return false;\n        }', 'if (expiry.size() >= MAX_CELLS) {\n            expiry.remove(expiry.keySet().iterator().next());\n        }'),
 ('re-laying takes a slot (refreshing refused when full)', 'if (expiry.containsKey(k)) {\n            expiry.put(k, until);\n            return true;\n        }', ''),
 ('lay forgets to sweep dead goo first (the REAL sweep call removed)', '        expire(now);\n        long k = key(x, z);', '        long k = key(x, z);'),
 # lifetime
 ('lifetime one tick short', 'public static final int LIFETIME_TICKS = 20 * 20;', 'public static final int LIFETIME_TICKS = 20 * 20 - 1;'),
 ('lifetime one tick long', 'public static final int LIFETIME_TICKS = 20 * 20;', 'public static final int LIFETIME_TICKS = 20 * 20 + 1;'),
 ('goo lasts through its expiry tick (<= not <)', 'return until != null && now < until;', 'return until != null && now <= until;'),
 ('goo never expires', 'return until != null && now < until;', 'return until != null;'),
 ('re-laying does not refresh expiry', 'if (expiry.containsKey(k)) {\n            expiry.put(k, until);\n            return true;\n        }', 'if (expiry.containsKey(k)) {\n            return true;\n        }'),
 ('count counts dead cells', 'if (now < until) {\n                n++;', 'if (true) {\n                n++;'),
 ('cells lists dead cells', 'if (now < e.getValue()) {\n                out.add', 'if (true) {\n                out.add'),
 ('expire removes nothing', 'expiry.values().removeIf(until -> now >= until);', ''),
 ('expire removes one tick early', 'expiry.values().removeIf(until -> now >= until);', 'expiry.values().removeIf(until -> now + 1 >= until);'),
 # coordinates
 ('x and z swapped in the key', 'return ((long) x << 32) | (z & 0xFFFFFFFFL);', 'return ((long) z << 32) | (x & 0xFFFFFFFFL);'),
 ('negative z corrupts x (no mask)', 'return ((long) x << 32) | (z & 0xFFFFFFFFL);', 'return ((long) x << 32) | z;'),
 ('key drops x (only z)', 'return ((long) x << 32) | (z & 0xFFFFFFFFL);', 'return (z & 0xFFFFFFFFL);'),
 ('key drops z (only x)', 'return ((long) x << 32) | (z & 0xFFFFFFFFL);', 'return ((long) x << 32);'),
 ('cells not sorted', "out.sort((p, q) -> p[0] != q[0] ? Integer.compare(p[0], q[0]) : Integer.compare(p[1], q[1]));", ''),
 # the heart: one damage tick a second from ALL goo
 ('damage every tick (no rate limit)', 'if (last != null && now - last < DAMAGE_INTERVAL_TICKS && now >= last) {\n            return false;\n        }', ''),
 ('damage interval 19 ticks', 'public static final int DAMAGE_INTERVAL_TICKS = 20;', 'public static final int DAMAGE_INTERVAL_TICKS = 19;'),
 ('damage interval 21 ticks', 'public static final int DAMAGE_INTERVAL_TICKS = 20;', 'public static final int DAMAGE_INTERVAL_TICKS = 21;'),
 ('damage gate off by one (<= not <)', 'now - last < DAMAGE_INTERVAL_TICKS && now >= last', 'now - last <= DAMAGE_INTERVAL_TICKS && now >= last'),
 ('damage hurts off goo', 'if (!isGoo(x, z, now)) {\n            return false;\n        }', ''),
 ('damage clock shared by ALL players', 'Long last = lastDamage.get(player);', 'Long last = lastDamage.values().stream().max(Long::compare).orElse(null);'),
 ('damage clock per CELL, not per player (stand on more goo, take more)', 'Long last = lastDamage.get(player);', 'Long last = lastDamage.get(new UUID(x, z));'),
 ('damage clock never starts', 'lastDamage.put(player, now);\n        return true;', 'return true;'),
 ('leaving goo resets the clock', 'if (!isGoo(x, z, now)) {\n            return false;\n        }', 'if (!isGoo(x, z, now)) {\n            lastDamage.remove(player);\n            return false;\n        }'),
 ('a backwards clock locks the player out', 'if (last != null && now - last < DAMAGE_INTERVAL_TICKS && now >= last) {', 'if (last != null && now - last < DAMAGE_INTERVAL_TICKS) {'),
 # slow, clear, forget
 ('slow factor 0.5', 'public static final double SLOW_FACTOR = 0.6;', 'public static final double SLOW_FACTOR = 0.5;'),
 ('slow applies off goo', 'return isGoo(x, z, now) ? SLOW_FACTOR : 1.0;', 'return SLOW_FACTOR;'),
 ('slow never applies', 'return isGoo(x, z, now) ? SLOW_FACTOR : 1.0;', 'return 1.0;'),
 ('clear leaves the goo', 'expiry.clear();\n        lastDamage.clear();', 'lastDamage.clear();'),
 ('clear leaves the damage clocks (they leak into the next run)', 'expiry.clear();\n        lastDamage.clear();', 'expiry.clear();'),
 ('forget forgets everyone', 'lastDamage.remove(player);\n    }\n}', 'lastDamage.clear();\n    }\n}'),
 ('forget forgets nobody', 'lastDamage.remove(player);\n    }\n}', '}\n}'),
 # the bug I fixed: ticks beyond the int range
 ('expiry back to an int (the bug I fixed)', 'long until = now > Long.MAX_VALUE - LIFETIME_TICKS ? Long.MAX_VALUE : now + LIFETIME_TICKS;', 'long until = (int) Math.min(Integer.MAX_VALUE, now + LIFETIME_TICKS);'),
 ('expiry add overflows past Long.MAX_VALUE', 'long until = now > Long.MAX_VALUE - LIFETIME_TICKS ? Long.MAX_VALUE : now + LIFETIME_TICKS;', 'long until = now + LIFETIME_TICKS;'),
]
only=sys.argv[1:]
res=[]
for i,(name,old,new) in enumerate(M,1):
    tag='M%d'%i
    if only and tag not in only: continue
    n=orig.count(old)
    if n!=1:
        print('%s: PATTERN MATCHED %d TIMES (need exactly 1), NOT APPLIED: %s'%(tag,n,name)); res.append('NA'); continue
    d='%s/%s'%(WORK,tag); os.makedirs(d+'/src/com/solme/emberfall/entity',exist_ok=True)
    open(d+'/src/com/solme/emberfall/entity/GooGrid.java','w').write(orig.replace(old,new))
    c=subprocess.run([JAVA+'/javac','-sourcepath',d+'/src:'+SP,'-d',d+'/out',CHK],capture_output=True,text=True)
    if c.returncode!=0:
        print('%s COMPILE ERROR (%s): %s'%(tag,name,c.stderr.strip().splitlines()[0][:110])); res.append('CE'); continue
    r=subprocess.run([JAVA+'/java','-cp',d+'/out','GooCheck'],capture_output=True,text=True,timeout=120)
    red=[l[5:].split('  ')[0] for l in r.stdout.splitlines() if l.startswith('FAIL ')]
    crashed = r.returncode!=0 and not red
    v='RED' if (r.returncode!=0 and (red or crashed)) else 'STILL GREEN'
    print('%s %s -> %s, exit %d, %d red%s: %s'%(tag,name,v,r.returncode,len(red),' (crash)' if crashed else '','; '.join(x[:50] for x in red[:2])))
    res.append(v)
print('SUMMARY mutants=%d red=%d green=%d not_applied_or_error=%d'%(len(res),res.count('RED'),res.count('STILL GREEN'),res.count('NA')+res.count('CE')))
