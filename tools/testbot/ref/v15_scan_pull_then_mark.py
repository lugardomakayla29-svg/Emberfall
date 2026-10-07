import re,glob
mark=re.compile(r"=\s*(trace\(\)|lines|log\(\)|\w+\(\))\.length\s*[;,]|const \w+ = lines\.length")
res=[]
for f in sorted(glob.glob('*_test.js')):
    L=open(f).read().split('\n')
    loops=[]
    for i,l in enumerate(L):
        m=re.search(r'while \(Date\.now\(\) - \w+ < (\d+)',l)
        if m and ('tp ' in ' '.join(L[i:i+4]) or 'run tp' in ' '.join(L[i:i+4])): loops.append((i+1,'pull-while %sms'%m.group(1)))
        if 'clearInterval(hold)' in l or 'clearInterval(pin)' in l: loops.append((i+1,'hold-ends'))
    marks=[i+1 for i,l in enumerate(L) if mark.search(l)]
    # a mark after a pull-while loop (not a hold) is the S5 shape; holds ending mid-file are normal cleanup
    for (ln,k) in loops:
        if k.startswith('pull-while'):
            after=[m for m in marks if m>ln]
            if after: res.append((f,ln,k,after[:3]))
print(len(res),'files: a window mark appears after a pull-back (tp inside while) loop')
for f,ln,k,a in res: print('%s: %s at L%d; later window marks at %s'%(f,k,ln,a))
