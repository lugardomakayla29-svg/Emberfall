# Usage (from the repo root): python3 tools/testbot/ref/v17_rift_rules_mutants.py [M1 M5 ...]
# Applies each mutant to a COPY of RiftRules.java under a fresh temp dir (the repo file is never edited), compiles RiftRulesCheck
# against it and reports RED (a check failed, exit non-zero) or STILL GREEN. A pattern that does not match exactly once is
# reported as NOT APPLIED, so a mutant can never silently be a no-op.
import subprocess, os, sys, tempfile, shutil
ROOT=os.getcwd()
SRC=ROOT+'/src/main/java/com/solme/emberfall/rift/RiftRules.java'
CHK=ROOT+'/tools/testbot/relic_math/RiftRulesCheck.java'
SP=ROOT+'/src/main/java'
JAVA=os.environ.get('JAVA_HOME','/opt/jdk25')+'/bin'
WORK=tempfile.mkdtemp(prefix='v17_rr_')
orig=open(SRC).read()
M=[
 ('roll due one tick early', "return sinceLastRollTicks >= NATURAL_CHECK_TICKS;", "return sinceLastRollTicks >= NATURAL_CHECK_TICKS - 1;"),
 ('roll due only after the interval', "return sinceLastRollTicks >= NATURAL_CHECK_TICKS;", "return sinceLastRollTicks > NATURAL_CHECK_TICKS;"),
 ('negative time counts as due', "return sinceLastRollTicks >= NATURAL_CHECK_TICKS;", "return Math.abs(sinceLastRollTicks) >= NATURAL_CHECK_TICKS;"),
 ('every face of the die wins', "return roll == 0;", "return roll >= 0;"),
 ('no face of the die wins', "return roll == 0;", "return roll == -1;"),
 ('cooldown ends one tick early', "return sinceLastRiftTicks >= 0 && sinceLastRiftTicks < NATURAL_COOLDOWN_TICKS;", "return sinceLastRiftTicks >= 0 && sinceLastRiftTicks < NATURAL_COOLDOWN_TICKS - 1;"),
 ('cooldown lasts one tick too long', "return sinceLastRiftTicks >= 0 && sinceLastRiftTicks < NATURAL_COOLDOWN_TICKS;", "return sinceLastRiftTicks >= 0 && sinceLastRiftTicks <= NATURAL_COOLDOWN_TICKS;"),
 ('a player with no Rift is cooling', "return sinceLastRiftTicks >= 0 && sinceLastRiftTicks < NATURAL_COOLDOWN_TICKS;", "return sinceLastRiftTicks < NATURAL_COOLDOWN_TICKS;"),
 ('spacing exclusive at 48', "return distanceToNearestRift >= MIN_RIFT_SPACING;", "return distanceToNearestRift > MIN_RIFT_SPACING;"),
 ('spacing off by one block', "return distanceToNearestRift >= MIN_RIFT_SPACING;", "return distanceToNearestRift >= MIN_RIFT_SPACING - 1;"),
 ('min distance dropped', "return distanceToPlayer >= NATURAL_MIN_DISTANCE && distanceToPlayer <= NATURAL_MAX_DISTANCE;", "return distanceToPlayer <= NATURAL_MAX_DISTANCE;"),
 ('max distance dropped', "return distanceToPlayer >= NATURAL_MIN_DISTANCE && distanceToPlayer <= NATURAL_MAX_DISTANCE;", "return distanceToPlayer >= NATURAL_MIN_DISTANCE;"),
 ('max distance beyond render range', "public static final double NATURAL_MAX_DISTANCE = 32.0;", "public static final double NATURAL_MAX_DISTANCE = 40.0;"),
 ('active run ignored', "return !insideActiveRun && spacingOk(distanceToNearestRift) && hasOpenAir;", "return spacingOk(distanceToNearestRift) && hasOpenAir;"),
 ('spacing ignored in placement', "return !insideActiveRun && spacingOk(distanceToNearestRift) && hasOpenAir;", "return !insideActiveRun && hasOpenAir;"),
 ('open air ignored', "return !insideActiveRun && spacingOk(distanceToNearestRift) && hasOpenAir;", "return !insideActiveRun && spacingOk(distanceToNearestRift);"),
 ('refusal names the wrong reason first', "        if (insideActiveRun) {\n            return \"inside an active run\";\n        }\n        if (!spacingOk(distanceToNearestRift)) {\n            return \"too close to another Rift\";\n        }", "        if (!spacingOk(distanceToNearestRift)) {\n            return \"too close to another Rift\";\n        }\n        if (insideActiveRun) {\n            return \"inside an active run\";\n        }"),
 ('refusal forgets open air', "        if (!hasOpenAir) {\n            return \"no open air\";\n        }\n        return null;", "        return null;"),
 ('render range exclusive', "return distance >= 0 && distance <= RENDER_RANGE;", "return distance >= 0 && distance < RENDER_RANGE;"),
 ('negative distance in range', "return distance >= 0 && distance <= RENDER_RANGE;", "return distance <= RENDER_RANGE;"),
 ('budget spends with nobody near', "if (playersInRange <= 0 || wanted <= 0) {\n            return 0;\n        }", "if (wanted <= 0) {\n            return 0;\n        }"),
 ('budget cap removed', "return Math.min(wanted, BUDGET_PER_TICK);", "return wanted;"),
 ('budget cap off by one', "return Math.min(wanted, BUDGET_PER_TICK);", "return Math.min(wanted, BUDGET_PER_TICK + 1);"),
 ('budget scales UP with players', "return Math.min(wanted, BUDGET_PER_TICK);", "return Math.min(wanted * playersInRange, BUDGET_PER_TICK * playersInRange);"),
 ('budget lets negatives through', "if (playersInRange <= 0 || wanted <= 0) {\n            return 0;\n        }", "if (playersInRange <= 0) {\n            return 0;\n        }"),
 ('idle hum above the budget', "public static final int IDLE_PER_TICK = 40;", "public static final int IDLE_PER_TICK = 400;"),
 ('idle hum ignores the budget', "return particlesThisTick(IDLE_PER_TICK, playersInRange);", "return playersInRange <= 0 ? 0 : IDLE_PER_TICK;"),
 ('OLD: idle expires with someone waiting', "return waiting <= 0 && openTicks >= IDLE_TICKS;", "return openTicks >= IDLE_TICKS;"),
 ('OLD: party cap 11', "return size >= 0 && size < MAX_PARTY;", "return size >= 0 && size <= MAX_PARTY;"),
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
    shutil.copytree(SP+'/com/solme/emberfall/hub',d+'/src/com/solme/emberfall/hub',dirs_exist_ok=True)
    open(d+'/src/com/solme/emberfall/rift/RiftRules.java','w').write(orig.replace(old,new))
    c=subprocess.run([JAVA+'/javac','-sourcepath',d+'/src:'+SP,'-d',d+'/out',CHK],capture_output=True,text=True)
    if c.returncode!=0:
        print('%s: COMPILE ERROR %s'%(tag,c.stderr[:160])); res.append('CE'); continue
    r=subprocess.run([JAVA+'/java','-cp',d+'/out','RiftRulesCheck'],capture_output=True,text=True,timeout=60)
    red=[l[5:].split('  ')[0] for l in r.stdout.splitlines() if l.startswith('FAIL')]
    v='RED' if (r.returncode!=0 and red) else 'STILL GREEN'
    print('%s %s -> %s, exit %d, %d red: %s'%(tag,name,v,r.returncode,len(red),'; '.join(x[:55] for x in red[:2])))
    res.append(v)
print('SUMMARY mutants=%d red=%d green=%d not_applied_or_error=%d'%(len(res),res.count('RED'),res.count('STILL GREEN'),res.count('NA')+res.count('CE')))
