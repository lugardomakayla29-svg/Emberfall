"""Independent checks on map_data.json. Reads ONLY the file (not the generator), so it cannot share a generator bug."""
import json, math
from collections import deque
d = json.load(open('map_data.json'))
R, RF = d['radiusOut'], d['radiusFloor']; hf = d['heights']; N = 2 * R + 1
H = lambda x, z: hf[(x + R) * N + (z + R)]
fails = []
def check(name, ok, info=''):
    print(('PASS ' if ok else 'FAIL ') + name + (' ' + str(info) if info else ''))
    if not ok: fails.append(name)
inside = lambda x, z, m=0: math.hypot(x, z) <= RF - m
# 1. neighbour step on the whole floor (walkable without jumping more than 1)
worst = 0
for x in range(-RF, RF):
    for z in range(-RF, RF):
        if inside(x, z) and inside(x + 1, z): worst = max(worst, abs(H(x, z) - H(x + 1, z)))
        if inside(x, z) and inside(x, z + 1): worst = max(worst, abs(H(x, z) - H(x, z + 1)))
check('max neighbour step <= 1', worst <= 1, worst)
# 2. every shrine footprint is dead flat and inside the floor
for s in d['shrines']:
    w, h, l = s['size']; x0 = s['x'] - w // 2; z0 = s['z'] - l // 2
    hs = {H(x0 + i, z0 + k) for i in range(w) for k in range(l)}
    check('shrine %s footprint flat' % s['type'], len(hs) == 1, hs)
    check('shrine %s inside floor by 6' % s['type'], all(inside(x0 + i, z0 + k, 6) for i in (0, w - 1) for k in (0, l - 1)))
# 3. house pads flat (footprint 11x11 around centre)
for n, x, z, r, f in d['houses']:
    hs = {H(x + i, z + k) for i in range(-r, r + 1) for k in range(-r, r + 1)}
    check('house %s pad flat' % n, len(hs) == 1, hs)
# 4. no overlap between any two structures (circle test with the footprint radius + 2 gap)
items = [(s['type'], s['x'], s['z'], max(s['size'][0], s['size'][2]) // 2 + 1) for s in d['shrines']] + [(h[0], h[1], h[2], h[3]) for h in d['houses']]
bad = [(a[0], b[0]) for i, a in enumerate(items) for b in items[i + 1:] if math.hypot(a[1] - b[1], a[2] - b[2]) < a[3] + b[3] + 2]
check('no structure overlaps another', not bad, bad)
# 5. decor on flat ground, clear of structures, inside the floor
tb = [t for t in d['trees']] ; bb = d['boulders']
check('trees on flat ground', all(H(x, z) == 0 for x, z in tb))
check('boulders on flat ground', all(H(x, z) == 0 for x, z, _ in bb))
check('decor inside floor by 4', all(inside(x, z, 4) for x, z in tb) and all(inside(x, z, 4) for x, z, _ in bb))
clash = [(x, z) for x, z in tb if any(math.hypot(x - a[1], z - a[2]) < a[3] + 2 for a in items)] + [(x, z) for x, z, _ in bb if any(math.hypot(x - a[1], z - a[2]) < a[3] + 2 for a in items)]
check('decor clear of structures', not clash, clash[:5])
# 6. entry and boss pads flat and clear
ex, ez = d['entry']; bx, bz = d['boss']
check('entry flat', len({H(ex + i, ez + k) for i in range(-5, 6) for k in range(-5, 6)}) == 1)
check('boss spot flat', len({H(bx + i, bz + k) for i in range(-8, 9) for k in range(-8, 9)}) == 1)
# 7. spawn ring: enough flat standable spots, none inside a structure
lo, hi = d['spawnRing']; spots = [(x, z) for x in range(-RF, RF) for z in range(-RF, RF) if lo <= math.hypot(x, z) <= hi and H(x, z) == 0 and not any(math.hypot(x - a[1], z - a[2]) < a[3] + 2 for a in items)]
check('spawn ring has >= 400 clear flat spots', len(spots) >= 400, len(spots))
# 8. connectivity: flood fill over walkable cells (step <= 1) from the entry; every structure and spawn spot must be reached
seen = {(ex, ez)}; q = deque([(ex, ez)])
while q:
    x, z = q.popleft()
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        n = (x + dx, z + dz)
        if n in seen or not inside(*n) or abs(H(*n) - H(x, z)) > 1: continue
        seen.add(n); q.append(n)
total = sum(1 for x in range(-RF, RF + 1) for z in range(-RF, RF + 1) if inside(x, z))
check('whole floor reachable from entry', len(seen) == total, '%d of %d' % (len(seen), total))
check('every shrine reachable', all((s['x'], s['z']) in seen for s in d['shrines']))
check('every house reachable', all((h[1], h[2]) in seen for h in d['houses']))
check('boss spot reachable', (bx, bz) in seen)
# 8b. REAL block footprints: expand every prop to absolute cells and check overlaps, ground, and the wall
occ = {}
def put(owner, x, y, z):
    k = (x, y, z)
    if k in occ and occ[k] != owner: clashes.append((owner, occ[k], k))
    occ[k] = owner
clashes = []
below = []
for n, hx, hz, r, f in d['houses']:
    for bx, by, bz, st in d['houseBlocks'][n]:
        put('house:' + n, hx + bx, by, hz + bz)
        if by == 0 and H(hx + bx, hz + bz) != 0: below.append(('house', n))
for i, (tx, tz) in enumerate(d['trees']):
    for bx, by, bz, st in d['treeBlocks'][i % 2]:
        put('tree%d' % i, tx + bx, by, tz + bz)
for i, (bx0, bz0, sz) in enumerate(d['boulders']):
    for bx, by, bz, st in d['boulderBlocks'][str(sz)]:
        put('rock%d' % i, bx0 + bx, by, bz0 + bz)
for s_ in d['shrines']:
    w, h_, l = s_['size']; x0 = s_['x'] - w // 2; z0 = s_['z'] - l // 2
    for bx, by, bz, st in s_['blocks']: put('shrine:' + s_['type'], x0 + bx, by, z0 + bz)
check('no two props share a block', not clashes, clashes[:4])
cells = {(x, z) for (x, y, z) in occ}
check('every prop block inside the floor by 2', all(inside(x, z, 2) for x, z in cells), [c for c in cells if not inside(*c, 2)][:3])
base_cells = {(x, z) for (x, y, z), o in occ.items() if y == 0}
check('every prop BASE (y=0 layer) stands on flat ground', all(H(x, z) == 0 for x, z in base_cells), sorted({H(x, z) for x, z in base_cells}))
# a path to each house door: the cell outside the door must be free of props and walkable
for n, hx, hz, r, f in d['houses']:
    # the ground floor front door is the LOWEST lower-half door; the cell outside is one step further along `f`
    doors = [(bx, by, bz) for bx, by, bz, st in d['houseBlocks'][n] if st.startswith('minecraft:oak_door') and 'half=lower' in st]
    low = min(b[1] for b in doors)
    front = [(bx, bz) for bx, by, bz in doors if by == low]
    step = {'south': (0, 1), 'north': (0, -1), 'east': (1, 0), 'west': (-1, 0)}[f]
    # the door that actually faces the plaza is the one furthest along `f`
    bx, bz = max(front, key=lambda c: c[0] * step[0] + c[1] * step[1])
    cx, cz = hx + bx + step[0], hz + bz + step[1]
    # standing cell = the step/floor outside; it and the two cells above it must be free of any prop block
    check('house %s: cell outside the door is free' % n, all(((cx, y, cz) not in occ) for y in (low + 1, low + 2)), (cx, cz))
    # REAL test: the door cell must be closer to the plaza centre (30,-44) than the house centre is, i.e. it looks inward
    d_door = math.hypot(hx + bx - 30, hz + bz + 44); d_mid = math.hypot(hx - 30, hz + 44)
    check('house %s: door is on the plaza side (door %.1f < centre %.1f from plaza)' % (n, d_door, d_mid), d_door < d_mid, (bx, bz))
# shrine entrances: every shrine has at least one free side at ground level within 2 blocks
for s_ in d['shrines']:
    w, h_, l = s_['size']; x0 = s_['x'] - w // 2; z0 = s_['z'] - l // 2
    free = [(x, z) for x in range(x0 - 2, x0 + w + 2) for z in range(z0 - 2, z0 + l + 2) if not (x0 <= x < x0 + w and z0 <= z < z0 + l) and (x, 1, z) not in occ]
    check('shrine %s has free standing room around it' % s_['type'], len(free) >= 12, len(free))
# 9. wall tops
wt = d['wallTops']
check('wall tops in 17..20', min(wt) >= 17 and max(wt) <= 20, (min(wt), max(wt)))
# 10. shrine data integrity: no grass marker, no air, skull block entity kept
for s in d['shrines']:
    names = {b[3].split('[')[0] for b in s['blocks']}
    check('shrine %s has no grass marker or air' % s['type'], not ({'minecraft:grass_block', 'minecraft:air'} & names))
check('greed keeps its skull block entity', any(e['id'] == 'minecraft:skull' for s in d['shrines'] if s['type'] == 'greed' for e in s['blockEntities']))
print('\nALL PASS' if not fails else '\nFAILED: %s' % fails)
