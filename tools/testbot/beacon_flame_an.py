import re,sys,collections
c=collections.Counter(); n=0
for l in open(sys.argv[1],errors='ignore'):
    m=re.search(r'BEACON_TEST flame-hit tag=(\S+) dealt=([\d.]+) tick=(\d+)',l)
    if m:
        n+=1
        for t in m.group(1).split('+'):
            if t!='keep': c[t]+=1
print('flame hits total',n,dict(c))
grp=sum(c[t] for t in ('g1','g2','g3','g4'))
print('F1 far hit by a flame:',c['far']>0,'(',c['far'],')')
print('F2 group hits',grp,'vs lone',c['lone'],'->',grp>c['lone'])
