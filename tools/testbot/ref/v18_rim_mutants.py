# Usage (repo root): python3 tools/testbot/ref/v18_rim_mutants.py
# Mutates COPIES of RiftFx.java in a temp dir and runs RiftFxCheck against each. Repo files are never edited. A pattern that does not match exactly once is NOT APPLIED.
import subprocess, os, tempfile
ROOT=os.getcwd(); SRC=ROOT+'/src/main/java'; PK='com/solme/emberfall/rift/'; J=os.environ.get('JAVA_HOME','/opt/jdk25')+'/bin'
fx=open(SRC+'/'+PK+'RiftFx.java').read()
SP='            out.add(new Event(tick, Kind.PARTICLE, "rim_dust", r[0], r[1], RIM_DUST, 0, DENSITY_RIM_DUST));\n'
M=[
 ('dust under the crack sparks removed', SP+'', None, 'spark'),
 ('dust under the flare end_rod removed', None, None, 'flare'),
 ('RIM_DUST changed to pink', 'RIM_DUST = RIM_WARM;','RIM_DUST = 0xFF9BD0;',None),
 ('RIM_DUST changed to white', 'RIM_DUST = RIM_WARM;','RIM_DUST = 0xFFFFFF;',None),
 ('RIM_DUST changed to grey', 'RIM_DUST = RIM_WARM;','RIM_DUST = 0x808080;',None),
 ('RIM_DUST changed to blue', 'RIM_DUST = RIM_WARM;','RIM_DUST = 0x4070FF;',None),
 ('RIM_WARM itself changed to lilac (the dust follows it)','RIM_WARM = 0xFFB070','RIM_WARM = 0xC79BFF',None),
 ('dust count 2 per rim event (over the cap)', 'DENSITY_RIM_DUST = 1;','DENSITY_RIM_DUST = 2;',None),
 ('dust placed one tick late at the flare', None, None, 'flarelate'),
 ('dust placed at the wrong cell (x+1) at the crack', None, None, 'cellshift'),
 ('dust key renamed so it is off the allow-list', 'Kind.PARTICLE, "rim_dust", r[0], r[1], RIM_DUST, 0, DENSITY_RIM_DUST));\n            if (i % 4','Kind.PARTICLE, "rim_dusty", r[0], r[1], RIM_DUST, 0, DENSITY_RIM_DUST));\n            if (i % 4',None),
]
def build(tag,src):
    d=tempfile.mkdtemp(prefix='v18_rim_'+tag+'_'); os.makedirs(d+'/src/'+PK); os.makedirs(d+'/out')
    open(d+'/src/'+PK+'RiftFx.java','w').write(src)
    c=subprocess.run([J+'/javac','-nowarn','-sourcepath',d+'/src:'+SRC,'-d',d+'/out',ROOT+'/tools/testbot/relic_math/RiftFxCheck.java'],capture_output=True,text=True)
    if c.returncode: return 'CE',[c.stderr.splitlines()[0][-80:]]
    r=subprocess.run([J+'/java','-cp',d+'/out','RiftFxCheck'],capture_output=True,text=True,timeout=300)
    fl=[l[5:].split('  ')[0][:70] for l in r.stdout.splitlines() if l.startswith('FAIL ')]
    return ('RED' if r.returncode else 'GREEN'),fl
print('baseline:',build('base',fx)[0])
lines=fx.split('\n'); res=[]
for n,(name,old,new,mode) in enumerate(M,1):
    src=None
    if mode=='spark': 
        k=[i for i,l in enumerate(lines) if '"rim_dust"' in l and 'RIM_DUST' in l]
        src='\n'.join(l for i,l in enumerate(lines) if i!=k[0]) if len(k)==2 else None
    elif mode=='flare':
        k=[i for i,l in enumerate(lines) if '"rim_dust"' in l and 'RIM_DUST' in l]
        src='\n'.join(l for i,l in enumerate(lines) if i!=k[1]) if len(k)==2 else None
    elif mode=='flarelate':
        k=[i for i,l in enumerate(lines) if '"rim_dust"' in l and 'RIM_DUST' in l]
        if len(k)==2:
            L=lines[:]; L[k[1]]=L[k[1]].replace('new Event(tick,','new Event(tick + 1,'); src='\n'.join(L)
    elif mode=='cellshift':
        k=[i for i,l in enumerate(lines) if '"rim_dust"' in l and 'RIM_DUST' in l]
        if len(k)==2:
            L=lines[:]; L[k[0]]=L[k[0]].replace('r[0], r[1]','r[0] + 1, r[1]'); src='\n'.join(L)
    elif fx.count(old)==1 and new is not None: src=fx.replace(old,new)
    if src is None or src==fx: print('M%d NOT APPLIED: %s'%(n,name)); res.append('NA'); continue
    v,fl=build('m%d'%n,src); res.append(v)
    print('M%d %s -> %s %s'%(n,name,v,('%d red: %s'%(len(fl),' | '.join(f[:44] for f in fl[:2]))) if fl else ''))
print('SUMMARY mutants=%d red=%d green=%d not_applied_or_error=%d'%(len(res),res.count('RED'),res.count('GREEN'),res.count('NA')+res.count('CE')))
