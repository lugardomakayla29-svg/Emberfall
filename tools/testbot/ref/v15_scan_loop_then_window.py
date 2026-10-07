import re,glob,sys
loop_re=re.compile(r'while \(Date\.now\(\) - \w+ < \d+|for \(let \w+ = 0; \w+ < \d+; \w+\+\+\) \{ await sleep|clearInterval')
win_re=re.compile(r'(const|let) (\w+) = (trace\(\)\.length|lines\.length|log\(\)\.length|\w+\(\)\.length);|\.slice\((t0|w0|n0|n|start|from)\)|lines\.slice\(')
out=[]
for f in sorted(glob.glob('*_test.js')):
    L=open(f).read().split('\n')
    # lines where a timed pull/hold loop ENDS: clearInterval, or the closing brace of a while(Date.now()-x<N)
    ends=[]
    for i,l in enumerate(L):
        if 'clearInterval' in l: ends.append((i+1,'clearInterval'))
        m=re.search(r'while \(Date\.now\(\) - \w+ < (\d+)',l)
        if m:
            depth=0;j=i
            while j<len(L):
                depth+=L[j].count('{')-L[j].count('}')
                if depth<=0 and j>=i: break
                j+=1
            ends.append((j+1,'while<%sms'%m.group(1)))
    wins=[(i+1,l.strip()[:90]) for i,l in enumerate(L) if re.search(r'= (trace\(\)|lines|log\(\)|\w+\(\))\.length;|\.slice\((t0|w0|n0|n)\)|const \w+ = lines\.length',l)]
    cand=[]
    for e,kind in ends:
        after=[w for w in wins if w[0]>e and w[0]-e<=6]
        for w in after: cand.append((kind,e,w[0],w[1]))
    if cand: out.append((f,cand))
print(len(out),'files with a window opening within 6 lines AFTER a loop end')
for f,c in out:
    for kind,e,w,t in c: print('%s: loop(%s) ends L%d, window opens L%d: %s'%(f,kind,e,w,t))
