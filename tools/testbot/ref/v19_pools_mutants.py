# Usage (repo root): python3 tools/testbot/ref/v19_pools_mutants.py
# Mutates COPIES of ChestRevealPools.java in temp dirs and runs ChestRevealPoolsCheck against each. Repo files are never edited.
# A pattern that does not match exactly once is reported NOT APPLIED. A copy that does not compile is reported DOES NOT BUILD, never counted as caught.
import subprocess, os, tempfile
ROOT=os.getcwd(); SRC=ROOT+'/src/main/java'; PK='com/solme/emberfall/relic/'; J=os.environ.get('JAVA_HOME','/opt/jdk25')+'/bin'
src=open(SRC+'/'+PK+'ChestRevealPools.java').read()
M=[
 ('items: gated relics left out (the add(...)-only mistake)','for (Relic r : RelicPool.all()) {\n            out.add(r.name());','for (Relic r : RelicPool.all()) {\n            if (r.isGated()) continue;\n            out.add(r.name());'),
 ('items: only the first 20 relics','for (Relic r : RelicPool.all()) {\n            out.add(r.name());\n        }','for (Relic r : RelicPool.all()) {\n            if (out.size() >= 20) break;\n            out.add(r.name());\n        }'),
 ('items: the relic id instead of the name','out.add(r.name());','out.add(r.id());'),
 ('items: the last relic dropped','return out;\n    }\n}','if (!out.isEmpty()) out.remove(out.size() - 1);\n        return out;\n    }\n}'),
 ('items: every name listed twice','out.add(r.name());','out.add(r.name()); out.add(r.name());'),
 ('tiers: the rarity name() instead of label()','out.add(r.label());','out.add(r.name());'),
 ('tiers: Legendary left out','for (RelicRarity r : RelicRarity.values()) {\n            out.add(r.label());','for (RelicRarity r : RelicRarity.values()) {\n            if (r.ordinal() == RelicRarity.values().length - 1) continue;\n            out.add(r.label());'),
 ('tiers: Common left out','for (RelicRarity r : RelicRarity.values()) {\n            out.add(r.label());','for (RelicRarity r : RelicRarity.values()) {\n            if (r.ordinal() == 0) continue;\n            out.add(r.label());'),
 ('tiers: order reversed','return out;\n    }\n\n    /** Every relic name','java.util.Collections.reverse(out);\n        return out;\n    }\n\n    /** Every relic name'),
 ('items: order shuffled by name','return out;\n    }\n}','java.util.Collections.sort(out);\n        return out;\n    }\n}'),
 ('items: a shared list returned (not a fresh copy)','public static List<String> items() {\n        List<String> out = new ArrayList<>();','private static final List<String> SHARED = new ArrayList<>();\n    public static List<String> items() {\n        List<String> out = SHARED; out.clear();'),
 ('items: a null name added','out.add(r.name());','out.add(r.isGated() ? null : r.name());'),
 ('items: an empty list','for (Relic r : RelicPool.all()) {\n            out.add(r.name());\n        }','for (Relic r : java.util.List.<Relic>of()) {\n            out.add(r.name());\n        }'),
]
def run(tag,s):
    d=tempfile.mkdtemp(prefix='v19_pools_'+tag+'_'); os.makedirs(d+'/src/'+PK); os.makedirs(d+'/out')
    open(d+'/src/'+PK+'ChestRevealPools.java','w').write(s)
    c=subprocess.run([J+'/javac','-nowarn','-sourcepath',d+'/src:'+SRC,'-d',d+'/out',ROOT+'/tools/testbot/relic_math/ChestRevealPoolsCheck.java'],capture_output=True,text=True)
    if c.returncode: return 'CE',[(c.stderr.splitlines() or ['?'])[0][-90:]]
    r=subprocess.run([J+'/java','-cp',d+'/out','ChestRevealPoolsCheck'],capture_output=True,text=True,timeout=120)
    fl=[l[5:].split('  ')[0][:58] for l in r.stdout.splitlines() if l.startswith('FAIL ')]
    return ('RED' if r.returncode else 'GREEN'),fl
print('baseline:',run('base',src)[0]); res=[]
for n,(name,old,new) in enumerate(M,1):
    if src.count(old)!=1: print('M%d NOT APPLIED (%d matches): %s'%(n,src.count(old),name)); res.append('NA'); continue
    v,fl=run('m%d'%n,src.replace(old,new)); res.append(v)
    print('M%d %s -> %s %s'%(n,name,v,('%d red: %s'%(len(fl),' | '.join(f[:30] for f in fl[:3]))) if fl else ''))
print('SUMMARY mutants=%d red=%d green=%d no_build=%d not_applied=%d'%(len(res),res.count('RED'),res.count('GREEN'),res.count('CE'),res.count('NA')))
