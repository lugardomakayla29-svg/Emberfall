import re, sys
log = open(sys.argv[1]).read().splitlines()
rs = [l for l in log if 'CHEST_RESPAWN' in l]
ok = True
def check(n, c, note=''):
    global ok
    print(('PASS ' if c else 'FAIL ') + n + ' ' + note)
    ok = ok and c
check('G1 exactly one CHEST_RESPAWN line in the whole run (cap of one per run)', len(rs) == 1, str(len(rs)))
m = re.search(r'CHEST_RESPAWN slot=(\d+) kind=(\w+) at=.* used=(\d+)', rs[0]) if rs else None
check('G2 the line is well formed', bool(m), rs[0][-100:] if rs else 'none')
if m:
    check('G3 it is a PAID or GOLD chest, never FREE', m.group(2) in ('PAID', 'GOLD'), m.group(2))
    check('G4 the per-run counter reads 1', m.group(3) == '1', 'used=' + m.group(3))
opens = [l for l in log if 'OPEN_TEST' in l]
check('G5 the chest was opened three times in total (loot, loot again, a different chest)', len(opens) == 3, str(len(opens)))
check('G6 no exceptions in the server log', not any('Exception' in l for l in log))
print('ALL PASS' if ok else 'SOME FAIL')
sys.exit(0 if ok else 1)
