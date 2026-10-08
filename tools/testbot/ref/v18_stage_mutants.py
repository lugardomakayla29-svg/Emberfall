# Usage (repo root, after a Gradle build): python3 tools/testbot/ref/v18_stage_mutants.py
# Mutates COPIES of RiftStage.java in temp dirs and runs tools/testbot/codec/RiftParticleMapCheck against each. Repo files are never edited.
import subprocess, os, tempfile, glob
ROOT=os.getcwd(); SRC=ROOT+'/src/main/java'; PK='com/solme/emberfall/rift/'; J=os.environ.get('JAVA_HOME','/opt/jdk25')+'/bin'
G=ROOT+'/.gradle/loom-cache/minecraftMaven/net/minecraft'
common=sorted(glob.glob(G+'/minecraft-common-*/*/*.jar'))[0]
fapi=':'.join(f for f in glob.glob(ROOT+'/.gradle/loom-cache/remapped_mods/**/*.jar',recursive=True) if 'sources' not in f)
libs=':'.join(glob.glob(ROOT+'/run/server/libraries/**/*.jar',recursive=True)); loader=sorted(glob.glob(ROOT+'/run/server/.fabric/server/fabric-loader-server-*.jar'))[0]
CP=common+':'+libs+':'+loader+':'+fapi
st=open(SRC+'/'+PK+'RiftStage.java').read()
LINE='            case "rim_dust" -> new DustParticleOptions((int) e.a & 0xFFFFFF, RIM_DUST_SIZE);\n'
M=[
 ('the rim_dust case is missing (falls to the END_ROD default)', LINE, ''),
 ('rim_dust maps to END_ROD explicitly', LINE, '            case "rim_dust" -> ParticleTypes.END_ROD;\n'),
 ('rim_dust ignores the event colour (fixed red)', '(int) e.a & 0xFFFFFF, RIM_DUST_SIZE', '0xFF0000, RIM_DUST_SIZE'),
 ('rim_dust uses the fill pink', '(int) e.a & 0xFFFFFF, RIM_DUST_SIZE', '0xFF9BD0, RIM_DUST_SIZE'),
 ('rim_dust as big as the fill (1.1)', 'RIM_DUST_SIZE = 0.8F', 'RIM_DUST_SIZE = 1.1F'),
 ('rim_dust oversized (3.0)', 'RIM_DUST_SIZE = 0.8F', 'RIM_DUST_SIZE = 3.0F'),
 ('electric_spark mapped to END_ROD', 'case "electric_spark" -> ParticleTypes.ELECTRIC_SPARK;', 'case "electric_spark" -> ParticleTypes.END_ROD;'),
 ('dust_ring mapped to END_ROD', 'case "dust_ring" -> ParticleTypes.DUST_PLUME;', 'case "dust_ring" -> ParticleTypes.END_ROD;'),
 ('glow loses its colour (END_ROD)', 'case "glow" -> new DustParticleOptions((int) e.a & 0xFFFFFF, 1.1F);', 'case "glow" -> ParticleTypes.END_ROD;'),
 ('the default fallback becomes ELECTRIC_SPARK', 'default -> ParticleTypes.END_ROD;', 'default -> ParticleTypes.ELECTRIC_SPARK;'),
]
def run(tag,src):
    d=tempfile.mkdtemp(prefix='v18_st_'+tag+'_'); os.makedirs(d+'/src/'+PK); os.makedirs(d+'/out')
    open(d+'/src/'+PK+'RiftStage.java','w').write(src)
    c=subprocess.run([J+'/javac','-nowarn','-cp',CP,'-sourcepath',d+'/src:'+SRC,'-d',d+'/out',ROOT+'/tools/testbot/codec/RiftParticleMapCheck.java'],capture_output=True,text=True)
    if c.returncode: return 'CE',[ (c.stderr.splitlines() or ['?'])[0][-90:] ]
    r=subprocess.run([J+'/java','-cp',d+'/out:'+CP,'com.solme.emberfall.rift.RiftParticleMapCheck'],capture_output=True,text=True,timeout=240)
    fl=[l.split('FAIL ',1)[1].split('  ')[0][:70] for l in r.stdout.splitlines() if 'FAIL ' in l]
    ok='ALL PASS (10 checks)' in r.stdout
    return ('GREEN' if ok and r.returncode==0 else 'RED'),fl
print('baseline:',run('base',st)[0]); res=[]
for n,(name,old,new) in enumerate(M,1):
    if st.count(old)!=1: print('M%d NOT APPLIED (%d matches): %s'%(n,st.count(old),name)); res.append('NA'); continue
    v,fl=run('m%d'%n,st.replace(old,new)); res.append(v)
    print('M%d %s -> %s %s'%(n,name,v,('%d red: %s'%(len(fl),' | '.join(f[:42] for f in fl[:2]))) if fl else ''))
print('SUMMARY mutants=%d red=%d green=%d not_applied_or_error=%d'%(len(res),res.count('RED'),res.count('GREEN'),res.count('NA')+res.count('CE')))
