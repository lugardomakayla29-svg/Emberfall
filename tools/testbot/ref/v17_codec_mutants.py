# Usage (from the repo root): python3 tools/testbot/ref/v17_codec_mutants.py [M1 M5 ...]
# Needs the full Minecraft/Fabric classpath (see tools/testbot/codec/run_codec_check.sh). Mutates COPIES of the two payload files under a fresh
# temp dir (the repo files are never edited). A pattern that does not match exactly once is reported as NOT APPLIED.
import subprocess, os, sys, tempfile, glob
ROOT=os.getcwd()
P=ROOT+'/src/main/java/com/solme/emberfall/network/'
CHK=ROOT+'/tools/testbot/codec/ChestRevealCodecCheck.java'
SP=ROOT+'/src/main/java'
JAVA=os.environ.get('JAVA_HOME','/opt/jdk25')+'/bin'
common=sorted(glob.glob(ROOT+'/.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-common-*/*/*.jar'))[0]
fapi=[f for f in glob.glob(ROOT+'/.gradle/loom-cache/remapped_mods/**/*.jar',recursive=True) if 'sources' not in f]
libs=glob.glob(ROOT+'/run/server/libraries/**/*.jar',recursive=True)
loader=sorted(glob.glob(ROOT+'/run/server/.fabric/server/fabric-loader-server-*.jar'))[0]
CP=':'.join([common]+libs+[loader]+fapi)
WORK=tempfile.mkdtemp(prefix='v17_cm_')
files={'open':open(P+'OpenChestRevealPayload.java').read(),'close':open(P+'CloseChestRevealPayload.java').read()}
M=[
 ('open','id not written','buf.writeVarInt(payload.revealId);\n                buf.writeUtf(payload.tier','buf.writeUtf(payload.tier'),
 ('open','id read as a fixed int not a varint','buf.readVarInt(), buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT)','buf.readInt(), buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT)'),
 ('open','tier and item swapped on read','buf -> new OpenChestRevealPayload(buf.readVarInt(), buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT), buf.readLong())','buf -> { int id = buf.readVarInt(); String first = buf.readUtf(MAX_TEXT); String second = buf.readUtf(MAX_TEXT); return new OpenChestRevealPayload(id, second, first, buf.readLong()); }'),
 ('open','tier written twice (item dropped)','buf.writeUtf(payload.item, MAX_TEXT);','buf.writeUtf(payload.tier, MAX_TEXT);'),
 ('open','item written twice (tier dropped)','buf.writeUtf(payload.tier, MAX_TEXT);\n                buf.writeUtf(payload.item','buf.writeUtf(payload.item, MAX_TEXT);\n                buf.writeUtf(payload.item'),
 ('open','seed not written','buf.writeLong(payload.seed);',''),
 ('open','seed written as an int','buf.writeLong(payload.seed);','buf.writeInt((int) payload.seed);'),
 ('open','seed read as an int','buf.readLong())','buf.readInt())'),
 ('open','read limit raised to 1000 (a peer can send long strings)','buf.readVarInt(), buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT)','buf.readVarInt(), buf.readUtf(1000), buf.readUtf(1000)'),
 ('open','write limit raised to 1000 (sender can write what reader refuses)','buf.writeUtf(payload.tier, MAX_TEXT);\n                buf.writeUtf(payload.item, MAX_TEXT);','buf.writeUtf(payload.tier, 1000);\n                buf.writeUtf(payload.item, 1000);'),
 ('open','MAX_TEXT 63','public static final int MAX_TEXT = 64;','public static final int MAX_TEXT = 63;'),
 ('open','MAX_TEXT 65','public static final int MAX_TEXT = 64;','public static final int MAX_TEXT = 65;'),
 ('open','MAX_TEXT huge','public static final int MAX_TEXT = 64;','public static final int MAX_TEXT = 100000;'),
 ('open','wrong channel path','EmberfallMod.id("open_chest_reveal")','EmberfallMod.id("open_shrine")'),
 ('open','type() returns the other packet type','return TYPE;','return CloseChestRevealPayload.TYPE;'),
 ('close','id not written','(payload, buf) -> buf.writeVarInt(payload.revealId),','(payload, buf) -> {},'),
 ('close','id read as a fixed int','buf -> new CloseChestRevealPayload(buf.readVarInt())','buf -> new CloseChestRevealPayload(buf.readInt())'),
 ('close','id always 0 on read','buf -> new CloseChestRevealPayload(buf.readVarInt())','buf -> { buf.readVarInt(); return new CloseChestRevealPayload(0); }'),
 ('close','wrong channel path','EmberfallMod.id("close_chest_reveal")','EmberfallMod.id("choose_shrine")'),
 # The check builds the record with ONE argument, so a real second field cannot compile against it (that is a compile error, not a red check). To prove the
 # reflective "exactly one field" check bites, add a second field AND keep a one-argument constructor, so the check compiles and the reflection sees two fields.
 ('close','close also carries an answer (a second field, with a one-argument constructor kept so the check compiles)',[('public record CloseChestRevealPayload(int revealId) implements','public record CloseChestRevealPayload(int revealId, String item) implements'),('buf -> new CloseChestRevealPayload(buf.readVarInt())','buf -> new CloseChestRevealPayload(buf.readVarInt())'.replace('new CloseChestRevealPayload(buf.readVarInt())','new CloseChestRevealPayload(buf.readVarInt(), "")')),('    @Override\n    public Type<? extends CustomPacketPayload> type() {','    public CloseChestRevealPayload(int revealId) {\n        this(revealId, "");\n    }\n\n    @Override\n    public Type<? extends CustomPacketPayload> type() {')],None),
]
only=sys.argv[1:]
res=[]
for i,(which,name,old,new) in enumerate(M,1):
    tag='M%d'%i
    if only and tag not in only: continue
    src=files[which]
    pairs=old if isinstance(old,list) else [(old,new)]
    bad=[(o,src.count(o)) for o,nw in pairs if src.count(o)!=1 or o==nw]
    if bad:
        print('%s: PATTERN MATCHED %d TIMES / IDENTICAL, NOT APPLIED: %s'%(tag,bad[0][1],name)); res.append('NA'); continue
    mutated=src
    for o,nw in pairs:
        mutated=mutated.replace(o,nw)
    d='%s/%s'%(WORK,tag); os.makedirs(d+'/src/com/solme/emberfall/network',exist_ok=True)
    for k,fn in (('open','OpenChestRevealPayload.java'),('close','CloseChestRevealPayload.java')):
        open(d+'/src/com/solme/emberfall/network/'+fn,'w').write(mutated if k==which else files[k])
    c=subprocess.run([JAVA+'/javac','-nowarn','-cp',CP,'-sourcepath',d+'/src:'+SP,'-d',d+'/out',CHK],capture_output=True,text=True)
    if c.returncode!=0:
        e=[l for l in c.stderr.splitlines() if 'error' in l]
        print('%s COMPILE ERROR (%s): %s'%(tag,name,(e[0] if e else '?')[-110:])); res.append('CE'); continue
    r=subprocess.run([JAVA+'/java','-cp',d+'/out:'+CP,'ChestRevealCodecCheck'],capture_output=True,text=True,timeout=180)
    red=[l[5:].split('  ')[0] for l in r.stdout.splitlines() if l.startswith('FAIL ')]
    v='RED' if (r.returncode!=0) else 'STILL GREEN'
    print('%s %s -> %s, exit %d, %d red: %s'%(tag,name,v,r.returncode,len(red),'; '.join(x[:55] for x in red[:2])))
    res.append(v)
print('SUMMARY mutants=%d red=%d green=%d not_applied_or_error=%d'%(len(res),res.count('RED'),res.count('STILL GREEN'),res.count('NA')+res.count('CE')))
