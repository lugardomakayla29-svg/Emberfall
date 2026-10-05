import re, sys
log = open(sys.argv[1]).read().splitlines()
starts = [i for i, l in enumerate(log) if 'Started MAP run' in l]
ends = [i for i, l in enumerate(log) if 'Tore down in-place arena' in l or 'abandoned (last player left)' in l]
ok = True
def check(n, c, note=''):
    global ok
    print(('PASS ' if c else 'FAIL ') + n + ' ' + note); ok = ok and c
check('G1 exactly 2 runs started in the whole test (one per deliberate click)', len(starts) == 2, f'{len(starts)} starts')
check('G2 every run that started also ended', len(ends) >= len(starts), f'{len(ends)} ends')
sec = lambda l: int(l[1:3]) * 3600 + int(l[4:6]) * 60 + int(l[7:9])
# the loop signature: a start within 3 s of an end
loop = [(a, b) for a in ends for b in starts if b > a and sec(log[b]) - sec(log[a]) <= 3]
check('G3 no run started within 3 s of one ending (the plate loop signature)', not loop, str(loop[:2]))
check('G4 no exceptions in the server log', not any('Exception' in l for l in log))
print('ALL PASS' if ok else 'SOME FAIL')
