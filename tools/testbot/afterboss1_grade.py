import re, sys
log = open(sys.argv[1]).read().splitlines()
ab = [l for l in log if 'AFTERBOSS1' in l]
ok = True
def check(n, c, note=''):
    global ok
    print(('PASS ' if c else 'FAIL ') + n + ' ' + note)
    ok = ok and c
check('G1 exactly one AFTERBOSS1 line (the warning and sound fire once per run)', len(ab) == 1, str(len(ab)))
m = re.search(r'warned=(\d+) heard=(\d+) tier=(\d+) soloIntervalBefore=(\d+) soloIntervalAfter=(\d+)', ab[0]) if ab else None
check('G2 the line is well formed', bool(m), ab[0][-110:] if ab else 'none')
if m:
    warned, heard, tier, b, a = map(int, m.groups())
    check('G3 the warning was sent', warned == 1)
    check('G4 the sound was played to at least one player', heard >= 1, 'heard=' + str(heard))
    check('G5 the run is at tier 2 when it fires', tier == 2, 'tier=' + str(tier))
    check('G6 the spawn interval after boss 1 is LOWER than before it', a < b, f'{b} -> {a} ticks')
    check('G7 and it never goes under the 15-tick floor', a >= 15, str(a))
esc = [l for l in log if 'escalated to tier 2 after Hydra' in l]
check('G8 the existing escalation still happened once', len(esc) == 1, str(len(esc)))
check('G9 no exceptions in the server log', not any('Exception' in l for l in log))
print('ALL PASS' if ok else 'SOME FAIL')
sys.exit(0 if ok else 1)
