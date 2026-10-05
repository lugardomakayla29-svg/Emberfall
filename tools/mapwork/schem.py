import gzip, struct, sys
def parse(data):
    pos=[0]
    def rd(fmt):
        n=struct.calcsize(fmt); v=struct.unpack_from(fmt,data,pos[0]); pos[0]+=n; return v[0] if len(v)==1 else v
    def string():
        n=rd('>H'); s=data[pos[0]:pos[0]+n].decode('utf8'); pos[0]+=n; return s
    def payload(t):
        if t==1: return rd('>b')
        if t==2: return rd('>h')
        if t==3: return rd('>i')
        if t==4: return rd('>q')
        if t==5: return rd('>f')
        if t==6: return rd('>d')
        if t==7:
            n=rd('>i'); v=data[pos[0]:pos[0]+n]; pos[0]+=n; return v
        if t==8: return string()
        if t==9:
            it=rd('>b'); n=rd('>i'); return [payload(it) for _ in range(n)]
        if t==10:
            d={}
            while True:
                tt=rd('>b')
                if tt==0: break
                k=string(); d[k]=payload(tt)
            return d
        if t==11:
            n=rd('>i'); v=struct.unpack_from('>%di'%n,data,pos[0]); pos[0]+=4*n; return list(v)
        if t==12:
            n=rd('>i'); v=struct.unpack_from('>%dq'%n,data,pos[0]); pos[0]+=8*n; return list(v)
        raise Exception('tag %d'%t)
    t=rd('>b'); string(); return payload(t)
def varints(b):
    out=[];i=0
    while i<len(b):
        v=0;s=0
        while True:
            x=b[i];i+=1;v|=(x&0x7f)<<s;s+=7
            if not x&0x80: break
        out.append(v)
    return out
def load(path):
    root=parse(gzip.open(path).read())
    s=root.get('Schematic',root)
    W,H,L=s['Width'],s['Height'],s['Length']
    blk=s['Blocks']
    pal={v:k for k,v in blk['Palette'].items()}
    ids=varints(blk['Data'])
    return s,W,H,L,pal,ids,blk.get('BlockEntities',[])
if __name__=='__main__':
    for f in sys.argv[1:]:
        s,W,H,L,pal,ids,be=load(f)
        print('==',f.split('/')[-1],'size',W,H,L,'offset',s.get('Offset'),'blocks',len(ids),'blockentities',len(be))
        from collections import Counter
        c=Counter(pal[i] for i in ids)
        for k,v in c.most_common(): print('  %5d %s'%(v,k))
