import re,sys,collections
log=sys.argv[1]
cuts=collections.defaultdict(list)
for l in open(log,errors='ignore'):
    m=re.search(r'SICKLE_TEST cut tag=(\S+) flat=([\d.]+) ring=([\d.]+) tick=(\d+)',l)
    if m: cuts[m.group(1)].append((float(m.group(2)),int(m.group(4)),float(m.group(3))))
for tag in ['inner','edge','outer']:
    v=cuts.get(tag,[])
    span=(v[-1][1]-v[0][1])/20 if len(v)>1 else 0
    print(f"{tag:6} cuts={len(v):3d}  flat range={min((x[0] for x in v),default=0):.2f}..{max((x[0] for x in v),default=0):.2f}  ring={v[0][2] if v else '-'}  first->last {span:.1f}s")
