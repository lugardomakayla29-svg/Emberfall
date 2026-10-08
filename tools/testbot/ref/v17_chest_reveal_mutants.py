# Usage (from the repo root): python3 tools/testbot/ref/v17_chest_reveal_mutants.py [M1 M5 ...]
# Applies each mutant to a COPY of ChestReveal.java under a fresh temp dir (the repo file is never edited), compiles ChestRevealCheck
# against it and reports RED (a check failed, exit non-zero) or STILL GREEN. A pattern that does not match exactly once is reported as
# NOT APPLIED, so a mutant can never silently be a no-op.
import subprocess, os, sys, tempfile
ROOT=os.getcwd()
SRC=ROOT+'/src/main/java/com/solme/emberfall/relic/ChestReveal.java'
CHK=ROOT+'/tools/testbot/relic_math/ChestRevealCheck.java'
SP=ROOT+'/src/main/java'
JAVA=os.environ.get('JAVA_HOME','/opt/jdk25')+'/bin'
WORK=tempfile.mkdtemp(prefix='v17_cr_')
orig=open(SRC).read()
M=[
 # landing on the truth
 ('item reel stops on a decoy', 'if (t >= stop) {\n                return new Reel(State.STOPPED, truth);\n            }', 'if (t >= stop) {\n                return new Reel(State.STOPPED, decoys.isEmpty() ? truth : decoys.get(0));\n            }'),
 ('tier reel stops on the item', 'return new Frame(reel(t, 0, TIER_STOP_TICK, trueTier, tierDecoys), reel(t, TIER_STOP_TICK + GAP_TICKS, ITEM_STOP_TICK, trueItem, itemDecoys));', 'return new Frame(reel(t, 0, TIER_STOP_TICK, trueItem, tierDecoys), reel(t, TIER_STOP_TICK + GAP_TICKS, ITEM_STOP_TICK, trueItem, itemDecoys));'),
 ('item reel stops on the tier', 'return new Frame(reel(t, 0, TIER_STOP_TICK, trueTier, tierDecoys), reel(t, TIER_STOP_TICK + GAP_TICKS, ITEM_STOP_TICK, trueItem, itemDecoys));', 'return new Frame(reel(t, 0, TIER_STOP_TICK, trueTier, tierDecoys), reel(t, TIER_STOP_TICK + GAP_TICKS, ITEM_STOP_TICK, trueTier, itemDecoys));'),
 ('reels swapped', 'return new Frame(reel(t, 0, TIER_STOP_TICK, trueTier, tierDecoys), reel(t, TIER_STOP_TICK + GAP_TICKS, ITEM_STOP_TICK, trueItem, itemDecoys));', 'return new Frame(reel(t, TIER_STOP_TICK + GAP_TICKS, ITEM_STOP_TICK, trueItem, itemDecoys), reel(t, 0, TIER_STOP_TICK, trueTier, tierDecoys));'),
 ('reel never stops', 'if (t >= stop) {\n                return new Reel(State.STOPPED, truth);\n            }', ''),
 ('reel stops one tick late', 'if (t >= stop) {', 'if (t > stop) {'),
 ('reel stops one tick early', 'if (t >= stop) {', 'if (t >= stop - 1) {'),
 # tier first, then item
 ('item reel starts with the tier (no wait)', 'reel(t, TIER_STOP_TICK + GAP_TICKS, ITEM_STOP_TICK, trueItem, itemDecoys)', 'reel(t, 0, ITEM_STOP_TICK, trueItem, itemDecoys)'),
 ('item reel starts before the tier has stopped', 'reel(t, TIER_STOP_TICK + GAP_TICKS, ITEM_STOP_TICK, trueItem, itemDecoys)', 'reel(t, TIER_STOP_TICK - 5, ITEM_STOP_TICK, trueItem, itemDecoys)'),
 ('no gap (item starts at the tier stop)', 'public static final int GAP_TICKS = 10;', 'public static final int GAP_TICKS = 0;'),
 ('item reel never hidden', 'if (t < start) {\n                return new Reel(State.HIDDEN, null);\n            }', ''),
 ('hidden reel still shows a symbol', 'return new Reel(State.HIDDEN, null);', 'return new Reel(State.HIDDEN, "");'),
 ('item stops BEFORE the tier', 'public static final int ITEM_STOP_TICK = TIER_STOP_TICK + GAP_TICKS + ITEM_SPIN_TICKS;', 'public static final int ITEM_STOP_TICK = TIER_STOP_TICK - 5;'),
 # no spoilers, pools
 ('the answer is a decoy (spoiler)', 'if (!s.equals(truth) && !out.contains(s)) {', 'if (!out.contains(s)) {'),
 ('decoys keep duplicates', 'if (!s.equals(truth) && !out.contains(s)) {', 'if (!s.equals(truth)) {'),
 ('decoys drop one entry', 'for (String s : pool) {\n            if (!s.equals(truth)', 'for (String s : pool.subList(0, pool.size() - 1)) {\n            if (!s.equals(truth)'),
 ('spinning reel shows the answer when the pool is one', 'return new Reel(State.SPINNING, "");', 'return new Reel(State.SPINNING, truth);'),
 ('decoys not shuffled by seed (seed ignored)', 'Random r = new Random(seed);', 'Random r = new Random(0);'),
 ('decoys not shuffled at all', 'for (int i = out.size() - 1; i > 0; i--) {', 'for (int i = out.size() - 1; i > 100; i--) {'),
 ('nondeterministic (clock)', 'Random r = new Random(seed);', 'Random r = new Random(seed + System.nanoTime());'),
 ('decoy index not wrapped (can crash)', 'return new Reel(State.SPINNING, decoys.get(symbolAt(t - start, stop - start) % decoys.size()));', 'return new Reel(State.SPINNING, decoys.get(symbolAt(t - start, stop - start)));'),
 ('decoys can come from the OTHER pool', 'return new Reveal(trueTier, trueItem, decoys(trueTier, tierPool, r), decoys(trueItem, itemPool, r));', 'return new Reveal(trueTier, trueItem, decoys(trueTier, tierPool, r), decoys(trueItem, tierPool, r));'),
 # refusals
 ('a tier outside the pool is accepted', 'if (!tierPool.contains(trueTier)) {\n            throw new IllegalArgumentException("tier \'" + trueTier + "\' is not in the tier pool");\n        }', ''),
 ('an item outside the pool is accepted', 'if (!itemPool.contains(trueItem)) {\n            throw new IllegalArgumentException("item \'" + trueItem + "\' is not in the item pool");\n        }', ''),
 ('null not refused', 'if (trueTier == null || trueItem == null || tierPool == null || itemPool == null) {\n            throw new IllegalArgumentException("null argument");\n        }', ''),
 # time bounds
 ('negative ticks not clamped', 'int t = Math.max(0, Math.min(tick, TOTAL_TICKS));', 'int t = Math.min(tick, TOTAL_TICKS);'),
 # M27 below is a KNOWN EQUIVALENT mutant: the upper half of the clamp is redundant because 't >= stop' already returns the final state for any larger tick.
 # Proven by hashing 303240 frames (ticks -50..5000 plus 100000, Integer.MAX_VALUE, Integer.MIN_VALUE) of original and mutant: identical. It is expected to stay green.
 ('ticks past the end not clamped', 'int t = Math.max(0, Math.min(tick, TOTAL_TICKS));', 'int t = Math.max(0, tick);'),
 # slowdown
 ('reel never slows', 'int step = FAST_STEP * 3;', 'int step = FAST_STEP;'),
 ('reel speeds UP at the end', 'int step = FAST_STEP * 3;', 'int step = 1;'),
 ('symbol index can go backwards', 'return base + slow / step;', 'return base - slow / step;'),
 ('symbolAt negative for early ticks', 'if (elapsed <= 0) {\n            return 0;\n        }', ''),
 ('fast phase wrong length', 'int fastEnd = length * 2 / 3;', 'int fastEnd = length / 3;'),
 # timing constants
 ('tier stops at 30', 'public static final int TIER_STOP_TICK = 40;', 'public static final int TIER_STOP_TICK = 30;'),
 ('item spins 70', 'public static final int ITEM_SPIN_TICKS = 60;', 'public static final int ITEM_SPIN_TICKS = 70;'),
 ('no hold at the end', 'public static final int HOLD_TICKS = 30;', 'public static final int HOLD_TICKS = 0;'),
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
    open(d+'/src/com/solme/emberfall/relic/ChestReveal.java','w').write(orig.replace(old,new))
    c=subprocess.run([JAVA+'/javac','-sourcepath',d+'/src:'+SP,'-d',d+'/out',CHK],capture_output=True,text=True)
    if c.returncode!=0:
        print('%s COMPILE ERROR (%s): %s'%(tag,name,c.stderr.strip().splitlines()[0][:110])); res.append('CE'); continue
    r=subprocess.run([JAVA+'/java','-cp',d+'/out','ChestRevealCheck'],capture_output=True,text=True,timeout=120)
    red=[l[5:].split('  ')[0] for l in r.stdout.splitlines() if l.startswith('FAIL ')]
    crashed = r.returncode!=0 and not red
    v='RED' if (r.returncode!=0 and (red or crashed)) else 'STILL GREEN'
    print('%s %s -> %s, exit %d, %d red%s: %s'%(tag,name,v,r.returncode,len(red),' (crash)' if crashed else '','; '.join(x[:50] for x in red[:2])))
    res.append(v)
print('SUMMARY mutants=%d red=%d green=%d not_applied_or_error=%d'%(len(res),res.count('RED'),res.count('STILL GREEN'),res.count('NA')+res.count('CE')))
