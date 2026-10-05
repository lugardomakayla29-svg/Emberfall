import re, sys
log = open(sys.argv[1]).read().splitlines()
esc = [l for l in log if 'SWARM_TEST escape' in l]
pay = [l for l in log if 'SWARM_TEST payout' in l]
ok = True
def check(n, c, note=''):
    global ok
    print(('PASS ' if c else 'FAIL ') + n + ' ' + note)
    ok = ok and c
check('G1 the portal exit was recorded once at 5.0x', len(esc) == 1 and 'tenths=50' in esc[0], esc[0][-50:] if esc else 'none')
m = re.search(r'tenths=(\d+) before=(\d+) after=(\d+)', pay[0]) if pay else None
check('G2 the payout was computed on that exit', bool(m), pay[0][-60:] if pay else 'none')
if m:
    t, b, a = map(int, m.groups())
    check('G3 payout equals base x 5.0 (never less than base)', a == max(b, round(b * t / 10.0)) and a >= b, f'base {b} -> {a}')
earned = [l for l in log if 'earned' in l and 'meta-currency' in l]
check('G4 the run reward line matches the paid amount', bool(earned) and bool(m) and f"earned {m.group(3)} meta-currency" in earned[-1], earned[-1][-90:] if earned else 'none')
check('G5 no exceptions in the server log', not any('Exception' in l for l in log))
print('ALL PASS' if ok else 'SOME FAIL')
