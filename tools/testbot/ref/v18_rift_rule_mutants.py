# Usage (from the repo root): python3 tools/testbot/ref/v18_rift_rule_mutants.py
# Mutates COPIES of RiftRules.java and RiftSpot.java in a fresh temp dir and runs the pure checks that cover them (RiftRulesCheck, RiftSpotCheck).
# The repo files are never edited. A pattern that does not match exactly once is reported NOT APPLIED. Needs only a JDK (these classes use no Minecraft types).
import subprocess, os, sys, tempfile, shutil
ROOT=os.getcwd(); SRC=ROOT+'/src/main/java'; PK='com/solme/emberfall/rift/'
JAVA=os.environ.get('JAVA_HOME','/opt/jdk25')+'/bin'
rules=open(SRC+'/'+PK+'RiftRules.java').read(); spot=open(SRC+'/'+PK+'RiftSpot.java').read()
M=[
 ('rules','refusal order: spacing checked before the run rule','if (insideActiveRun) {\n            return "inside an active run";\n        }\n        if (!spacingOk(distanceToNearestRift)) {\n            return "too close to another Rift";\n        }','if (!spacingOk(distanceToNearestRift)) {\n            return "too close to another Rift";\n        }\n        if (insideActiveRun) {\n            return "inside an active run";\n        }'),
 ('rules','refusal text for spacing changed','return "too close to another Rift";','return "refused";'),
 ('rules','refusal text for open air changed','return "no open air";','return "refused";'),
 ('rules','refusal text for the run changed','return "inside an active run";','return "refused";'),
 ('rules','placementOk ignores the run rule','return !insideActiveRun && spacingOk(distanceToNearestRift) && hasOpenAir;','return spacingOk(distanceToNearestRift) && hasOpenAir;'),
 ('rules','placementOk ignores open air','return !insideActiveRun && spacingOk(distanceToNearestRift) && hasOpenAir;','return !insideActiveRun && spacingOk(distanceToNearestRift);'),
 ('rules','spacing 47 not 48','MIN_RIFT_SPACING = 48.0','MIN_RIFT_SPACING = 47.0'),
 ('rules','spacing boundary > not >=','return distanceToNearestRift >= MIN_RIFT_SPACING;','return distanceToNearestRift > MIN_RIFT_SPACING;'),
 ('rules','idle timer 30 s','IDLE_TICKS = 20 * 60 * 10','IDLE_TICKS = 20 * 30'),
 ('rules','idle expires even with a player waiting','return waiting <= 0 && openTicks >= IDLE_TICKS;','return openTicks >= IDLE_TICKS;'),
 ('rules','natural distance max 40','NATURAL_MAX_DISTANCE = 32.0','NATURAL_MAX_DISTANCE = 40.0'),
 ('rules','natural cooldown 1 minute','NATURAL_COOLDOWN_TICKS = 20 * 60 * 20','NATURAL_COOLDOWN_TICKS = 20 * 60'),
 ('spot','open share 50%','MIN_OPEN_SHARE = 0.70','MIN_OPEN_SHARE = 0.50'),
 ('spot','open share 90%','MIN_OPEN_SHARE = 0.70','MIN_OPEN_SHARE = 0.90'),
 ('spot','an empty sample counts as open','if (sampledCells <= 0 || openCells < 0 || openCells > sampledCells) {\n            return false;\n        }','if (sampledCells <= 0) {\n            return true;\n        }'),
 ('spot','nearest is 2D (ignores y)','double dy = r.y() - y;','double dy = 0;'),
 ('spot','nearest returns the first not the minimum','best = Math.min(best, Math.sqrt(dx * dx + dy * dy + dz * dz));','if (best == Double.POSITIVE_INFINITY) best = Math.sqrt(dx * dx + dy * dy + dz * dz);'),
 ('spot','facing swaps +X and -X','return dx > 0 ? 3 : 1;','return dx > 0 ? 1 : 3;'),
]
only=sys.argv[1:]; WORK=tempfile.mkdtemp(prefix='v18_rm_'); res=[]
def run(tag,rsrc,ssrc):
    d=WORK+'/'+tag; os.makedirs(d+'/src/'+PK); os.makedirs(d+'/out')
    open(d+'/src/'+PK+'RiftRules.java','w').write(rsrc); open(d+'/src/'+PK+'RiftSpot.java','w').write(ssrc)
    outs=[]
    for chk in ('RiftRulesCheck','RiftSpotCheck'):
        c=subprocess.run([JAVA+'/javac','-nowarn','-sourcepath',d+'/src:'+SRC,'-d',d+'/out',ROOT+'/tools/testbot/relic_math/'+chk+'.java'],capture_output=True,text=True)
        if c.returncode!=0: return 'CE',[ (c.stderr.splitlines() or ['?'])[0][-90:] ]
        r=subprocess.run([JAVA+'/java','-cp',d+'/out',chk],capture_output=True,text=True,timeout=120)
        outs.append((chk,r.returncode,[l[5:].split('  ')[0] for l in r.stdout.splitlines() if l.startswith('FAIL ')]))
    red=[(c,f) for c,rc,fl in outs for f in fl]
    codes=[rc for _,rc,_ in outs]
    return ('RED' if any(codes) else 'GREEN'),red
base,_=run('base',rules,spot)
print('baseline (unmutated copies):',base)
for i,(w,name,old,new) in enumerate(M,1):
    tag='M%d'%i
    if only and tag not in only: continue
    src=rules if w=='rules' else spot
    if src.count(old)!=1 or old==new:
        print('%s NOT APPLIED (%d matches): %s'%(tag,src.count(old),name)); res.append('NA'); continue
    v,red=run(tag,src.replace(old,new) if w=='rules' else rules,spot if w=='rules' else src.replace(old,new))
    print('%s %s -> %s %s'%(tag,name,v,('%d red: %s'%(len(red),'; '.join(f[:48] for _,f in red[:2]))) if red else ''))
    res.append(v)
print('SUMMARY mutants=%d red=%d green=%d not_applied_or_error=%d'%(len(res),res.count('RED'),res.count('GREEN'),res.count('NA')+res.count('CE')))
