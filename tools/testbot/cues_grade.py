"""Grades the V4 cue tests from the SERVER LOG. Usage: cues_grade.py server_run.log [part]
part = 'main' (cues_test.js: tome, free chest, shrine), 'swarm' (CUES_MODE=swarm cues_test.js) or 'gate' (cues_gate_test.js). Default 'main'.
Each window is the log between two MARK_ lines; each states EXACTLY how many SOUND_TEST lines of an id it expects, so a missing cue
and a spurious extra cue both fail. A SOUND_TEST line shows the cue's code ran; it does not show anyone can hear it."""
import re, sys
log = open(sys.argv[1], errors='replace').read().splitlines()
part = sys.argv[2] if len(sys.argv) > 2 else 'main'
ok = True
def check(name, cond, note=''):
    global ok
    print(('PASS ' if cond else 'FAIL ') + name + (' ' + note if note else ''))
    ok = ok and cond
def idx(tag):
    hits = [i for i, l in enumerate(log) if re.search(r'\bMARK_' + tag + r'\s*$', l) or ('MARK_' + tag) in l.split('] ')[-1]]
    return hits[-1] if hits else None
def window(a, b):
    i, j = idx(a), idx(b)
    return None if i is None or j is None or j < i else log[i:j]
def cues(w, cid):
    return [l for l in (w or []) if 'SOUND_TEST' in l and re.search(r'\bid=' + cid + r'\b', l)]
def expect(label, a, b, cid, n):
    w = window(a, b)
    if w is None:
        check(label, False, f'(markers MARK_{a}/MARK_{b} not found in the log)')
        return None
    got = cues(w, cid)
    check(label, len(got) == n, f'expected {n} {cid}, saw {len(got)}')
    return got

if part == 'main':
    expect('T1 a Tome pick plays tome_picked exactly once', 'TOME_PICK_BEGIN', 'TOME_PICK_END', 'tome_picked', 1)
    expect('T2 CONTROL: skipping a Tome plays no tome_picked', 'TOME_SKIP_BEGIN', 'TOME_SKIP_END', 'tome_picked', 0)
    expect('F1 a boss free chest plays free_chest_appears exactly once', 'FREE_BEGIN', 'FREE_END', 'free_chest_appears', 1)
    w = window('FREE_REFUSE_BEGIN', 'FREE_REFUSE_END')
    placed = [l for l in (w or []) if 'CHEST_TEST free chest from' in l]
    got = cues(w, 'free_chest_appears')
    check('F2 CONTROL: every further free chest that was really placed plays exactly one cue, and refused ones play none',
          w is not None and len(got) == len(placed), f'placed={len(placed)} cues={len(got)}')
    check('F2b CONTROL: the run cap stopped some of the 14 tries (so refusals were exercised)', w is not None and len(placed) < 14, f'placed={len(placed)} of 14')
    expect('H1 clearing the shrine trial plays shrine_trial_cleared exactly once', 'SHRINE_BEGIN', 'SHRINE_END', 'shrine_trial_cleared', 1)
    allc = [l for l in log if 'SOUND_TEST' in l]
    check('X1 no cue outside its window: total cues = 1 tome + free chests placed + 1 shrine',
          len(allc) == 1 + len([l for l in log if 'CHEST_TEST free chest from' in l]) + 1, f'total SOUND_TEST={len(allc)}')
elif part == 'swarm':
    first = expect('W1 the Final Swarm start plays swarm_begins exactly once', 'SWARM_BEGIN', 'SWARM_END', 'swarm_begins', 1)
    expect('W2 CONTROL: starting the swarm again (already begun) plays nothing', 'SWARM_AGAIN_BEGIN', 'SWARM_AGAIN_END', 'swarm_begins', 0)
    check('W2b the control is not hollow: W1 saw the first cue, so W2 zero means the repeat was really ignored', first is not None and len(first) == 1)
    allc = [l for l in log if 'SOUND_TEST' in l]
    check('X2 swarm_begins is the only cue in the whole run', len(allc) == 1 and 'swarm_begins' in allc[0], f'total SOUND_TEST={len(allc)}')
elif part == 'gate':
    w = window('GATE_HOLD_BEGIN', 'GATE_HOLD_END')
    got = cues(w, 'gate_countdown')
    check('G1 a full 3 second hold plays gate_countdown exactly 3 times (seconds left 3, 2, 1)', w is not None and len(got) == 3, f'saw {len(got)}')
    pitches = [float(m.group(1)) for l in got for m in [re.search(r'pitch=([\d.]+)', l)] if m]
    check('G2 the pitch rises 0.9, 0.95, 1.0 across the three bells', len(pitches) == 3 and all(abs(a - b) < 0.011 for a, b in zip(pitches, [0.9, 0.95, 1.0])), str(pitches))
    w2 = window('GATE_CANCEL_BEGIN', 'GATE_CANCEL_END')
    got2 = cues(w2, 'gate_countdown')
    check('G3 CONTROL: stepping away after the first bell stops the countdown (fewer than 3 bells)', w2 is not None and 1 <= len(got2) < 3, f'saw {len(got2)}')
errs = [l for l in log if re.search(r'Exception|/ERROR\]', l)]
check('E1 no exceptions or ERROR lines in the server log', not errs, errs[0][:120] if errs else '')
print('ALL PASS' if ok else 'SOME FAIL')
sys.exit(0 if ok else 1)
