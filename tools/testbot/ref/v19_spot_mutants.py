# Usage (repo root): python3 tools/testbot/ref/v19_spot_mutants.py
# Mutates COPIES of RiftSpot.java in temp dirs, runs RiftSpotCheck against each. Repo files are never edited. A pattern not matching exactly once is NOT APPLIED.
import subprocess, os, tempfile
ROOT=os.getcwd(); SRC=ROOT+'/src/main/java'; PK='com/solme/emberfall/rift/'; J=os.environ.get('JAVA_HOME','/opt/jdk25')+'/bin'
src=open(SRC+'/'+PK+'RiftSpot.java').read()
M=[
 ('MIN_SAMPLED = 0 (the floor removed, Koda\'s required mutant)','MIN_SAMPLED = 15;','MIN_SAMPLED = 0;'),
 ('MIN_SAMPLED = 1','MIN_SAMPLED = 15;','MIN_SAMPLED = 1;'),
 ('MIN_SAMPLED = 14 (one low)','MIN_SAMPLED = 15;','MIN_SAMPLED = 14;'),
 ('MIN_SAMPLED = 16 (one high)','MIN_SAMPLED = 15;','MIN_SAMPLED = 16;'),
 ('floor test < becomes <=','sampledCells < MIN_SAMPLED','sampledCells <= MIN_SAMPLED'),
 ('floor test dropped from the guard','sampledCells < MIN_SAMPLED || openCells < 0','openCells < 0'),
 ('MIN_OPEN_SHARE 0.50','MIN_OPEN_SHARE = 0.70','MIN_OPEN_SHARE = 0.50'),
 ('MIN_OPEN_SHARE 0.90','MIN_OPEN_SHARE = 0.70','MIN_OPEN_SHARE = 0.90'),
 ('rounds down (floor) instead of up','Math.ceil(sampledCells * MIN_OPEN_SHARE)','Math.floor(sampledCells * MIN_OPEN_SHARE)'),
 ('>= becomes >','openCells >= Math.ceil','openCells > Math.ceil'),
 ('open > sampled no longer refused','|| openCells > sampledCells','|| false'),
 ('negative open no longer refused','|| openCells < 0','|| false'),
]
def run(tag,s):
    d=tempfile.mkdtemp(prefix='v19_spot_'+tag+'_'); os.makedirs(d+'/src/'+PK); os.makedirs(d+'/out')
    open(d+'/src/'+PK+'RiftSpot.java','w').write(s)
    c=subprocess.run([J+'/javac','-nowarn','-sourcepath',d+'/src:'+SRC,'-d',d+'/out',ROOT+'/tools/testbot/relic_math/RiftSpotCheck.java'],capture_output=True,text=True)
    if c.returncode: return 'CE',[(c.stderr.splitlines() or ['?'])[0][-80:]]
    r=subprocess.run([J+'/java','-cp',d+'/out','RiftSpotCheck'],capture_output=True,text=True,timeout=120)
    fl=[l[5:].split('  ')[0][:60] for l in r.stdout.splitlines() if l.startswith('FAIL ')]
    return ('RED' if r.returncode else 'GREEN'),fl
print('baseline:',run('base',src)[0]); res=[]
for n,(name,old,new) in enumerate(M,1):
    if src.count(old)!=1: print('M%d NOT APPLIED (%d matches): %s'%(n,src.count(old),name)); res.append('NA'); continue
    v,fl=run('m%d'%n,src.replace(old,new)); res.append(v)
    print('M%d %s -> %s %s'%(n,name,v,('%d red: %s'%(len(fl),' | '.join(f[:34] for f in fl[:3]))) if fl else ''))
print('SUMMARY mutants=%d red=%d green=%d not_applied_or_error=%d'%(len(res),res.count('RED'),res.count('GREEN'),res.count('NA')+res.count('CE')))
