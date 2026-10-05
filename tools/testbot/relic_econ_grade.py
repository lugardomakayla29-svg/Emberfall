import re,sys,math
import os
SERVERLOG=sys.argv[1] if len(sys.argv)>1 else '/tmp/one_relic_econ_test_server.log'
log=open(SERVERLOG).read().splitlines()
runs={}; cur=None; order=[]; runs_sep={}
for l in log:
    m=re.search(r'\[EmberTester\] ECON_RUN (\w+)',l)
    if m:
        cur=m.group(1); seq=sum(1 for k in order if k.split('#')[0]==cur); key=cur+('#%d'%seq); order.append(key); runs.setdefault(cur,[]); runs_sep[key]=[]; continue
    m=re.search(r'OPEN_TEST kind=(\w+) rarity=(\w+) relic=(\w+) key=(\w+) cost=(\d+) counter=(\d+) luck=([\d.]+)',l)
    if m and cur is not None:
        rec=dict(kind=m[1],rar=m[2],relic=m[3],key=m[4]=='true',cost=int(m[5]),counter=int(m[6]),luck=float(m[7]))
        runs[cur].append(rec); runs_sep[order[-1]].append(rec)
fails=0
def check(n,ok,d=''):
    global fails
    print(('PASS ' if ok else 'FAIL ')+n+'  '+d)
    if not ok: fails+=1
for k,v in runs.items(): print('run',k,len(v),'openings')
A=runs.get('A_control',[]); B=runs.get('B_ledger',[]); C=runs.get('C_key',[])
check('A control: 8 paid openings happened',len(A)==8,str(len(A)))
check('A control: the price climbs (costs strictly rise)',len(A)>=2 and all(A[i]['cost']<A[i+1]['cost'] for i in range(len(A)-1)),str([a['cost'] for a in A]))
check('B ledger: 8 paid openings happened',len(B)==8,str(len(B)))
check('B ledger: every opening costs the base 30 and the counter never moves',len(B)>0 and all(b['cost']==30 and b['counter']==0 for b in B),str([(b['cost'],b['counter']) for b in B]))
freeC=[x for x in C if x['key']]; paidC=[x for x in C if not x['key']]
check('C key x5: all 16 chests opened',len(C)==16,str(len(C)))
check('C key x5: free openings cost 0 and paid ones cost more than 0',all(x['cost']==0 for x in freeC) and all(x['cost']>0 for x in paidC),f'{len(freeC)} free, {len(paidC)} paid')
# 16 trials at 0.5: mean 8, sd 2; allow 3 sd (2..14) so the test fails only when the key is really not wired
check('C key x5: the free count fits 50% (2 to 14 of 16; 0 means not wired)',2<=len(freeC)<=14,f'{len(freeC)} of 16')
# the counter may only rise on a paid, non-key opening, and never once the Ledger has been picked up by chance
cnt_ok=True; ledger=False; prev=0; why=''
for x in C:
    if x['counter']<prev: cnt_ok=False; why='counter fell'
    if x['counter']>prev and (x['key'] or ledger): cnt_ok=False; why='rose on key/ledger'
    if x['counter']>prev+1: cnt_ok=False; why='jumped'
    prev=x['counter']
    if x['relic']=='ember_ledger': ledger=True
check('C key x5: the counter never rises on a key proc or after the Ledger, one step at a time',cnt_ok and prev<=len(paidC),f'final counter {prev}, paid {len(paidC)}, ledger picked={ledger} {why}')
def share(tags):
    rows=[x for t in tags for x in runs.get(t,[])]
    hi=sum(1 for x in rows if x['rar'] in('RARE','LEGENDARY')); return hi,len(rows),rows
h0,n0,r0=share(['D0_clover']); h10,n10,r10=share(['D10_clover'])
print('clover 0:',h0,'/',n0,' clover 10:',h10,'/',n10)
check('D clover: both arms opened at least 30 free chests',n0>=30 and n10>=30,f'{n0} vs {n10}')
def luck_ok(tag,base):
    # per physical run: luck must equal 8 per Clover stack owned when that chest was rolled (clovers picked from earlier chests count)
    ok=True; first=None; start=[]
    for key,rows in runs_sep.items():
        if key.split('#')[0]!=tag or not rows: continue
        own=base; start.append(rows[0]['luck'])
        for x in rows:
            if abs(x['luck']-8.0*own)>1e-9: ok=False; first=first or (key,x['luck'],own)
            if x['relic']=='clover': own+=1
    return ok,first,start
ok0,f0,s0=luck_ok('D0_clover',0); ok10,f10,s10=luck_ok('D10_clover',10)
check('D clover: luck is exactly 8 per owned Clover in both arms (80 at the start of the 10-Clover arm)',ok0 and ok10 and all(v==80.0 for v in s10) and all(v==0.0 for v in s0),f'starts {s0} vs {s10}; first mismatch {f0 or f10}')
check('D clover: all were FREE chests',all(x['kind']=='FREE' for x in r0+r10),'')
p0=h0/max(1,n0); p10=h10/max(1,n10)
check('D clover: rare-or-better share is clearly higher with 10 clovers',p10>p0+0.10,f'{p0:.2f} -> {p10:.2f}')
print('RESULT: ALL PASS' if fails==0 else f'RESULT: {fails} FAILED'); 
