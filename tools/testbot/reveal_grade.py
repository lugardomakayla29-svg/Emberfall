#!/usr/bin/env python3
"""Grades chest_reveal_test.js from the SERVER log (the authoritative record). Usage: reveal_grade.py <server.log> <revealId>
The wire half is graded by the bot itself; this checks what the server DECIDED: one reveal sent, the forged closes refused, the real close honoured once,
the repeat refused."""
import re, sys
log = open(sys.argv[1], errors='replace').read(); rid = int(sys.argv[2])
sent = re.findall(r'REVEAL_TEST sent id=(\d+) tier=(.+?) item=(.+?) seed=(-?\d+)', log)
closes = re.findall(r'REVEAL_TEST close id=(\d+) honoured=(true|false)', log)
opens = re.findall(r'OPEN_TEST kind=\w+ rarity=(\w+) relic=(\w+)', log)
res = []
def chk(name, ok, extra=''): res.append(ok); print(('PASS ' if ok else 'FAIL ') + name + ('  ' + extra if extra else ''))
chk('G1 the server sent exactly one reveal in the run', len(sent) == 1, f'sent={len(sent)}')
chk('G2 the id the bot saw is the id the server sent', len(sent) == 1 and int(sent[0][0]) == rid, f'server={sent[0][0] if sent else None} bot={rid}')
byid = [(int(i), h == 'true') for i, h in closes]
forged = [h for i, h in byid if i == rid + 7]
old = [h for i, h in byid if i == 0]
real = [h for i, h in byid if i == rid]
# the id-to-name link, from the SAME Relic object: the open's relic id, and the name the reveal carried. Known pool entries are checked exactly.
# The id-to-name table comes from RelicPool.java itself (the source of truth), so any rolled relic is checkable, not just one.
import os
POOL = os.environ.get('RELICPOOL', os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'src', 'main', 'java', 'com', 'solme', 'emberfall', 'relic', 'RelicPool.java'))
KNOWN = dict(re.findall(r'add\("([a-z_]+)",\s*"([^"]+)"', open(POOL).read()))
rel = opens[0][1] if opens else None
chk('G2b the reveal carries the tier of the open', bool(opens) and bool(sent) and opens[0][0].lower() == sent[0][1].lower(), f'open={opens[:1]} reveal={[s[1] for s in sent]}')
chk('G2c the reveal names the relic the open granted' + ('' if rel in KNOWN else ' (id not in KNOWN: not checkable here)'),
    rel in KNOWN and bool(sent) and sent[0][2] == KNOWN[rel], f'relic={rel} expected={KNOWN.get(rel)} reveal item={[s[2] for s in sent]}')
chk('G3 the forged close (wrong id) was REFUSED', forged == [False], str(forged))
chk('G4 the old-id close (0) was REFUSED', old == [False], str(old))
chk('G5 the real close was honoured exactly once, the repeat refused', real == [True, False], str(real))
chk('G6 no exceptions in the server log', not re.search(r'Exception', log), '')
print('GRADE PASS (%d)' % len(res) if all(res) else 'GRADE FAILED %d of %d' % (res.count(False), len(res)))
sys.exit(0 if all(res) else 1)
