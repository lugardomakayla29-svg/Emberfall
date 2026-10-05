"""Player walkability check on the real map data: for each house, can a player-sized body (2 high) walk from the plaza cell
outside the front door to a cell deep inside the house, stepping up at most 1 block per move? World = terrain surface at y=0
(walkable cell = standing y is 1 above the solid block), plus every prop block. Moves are 4-directional; a step up or down of at most 1."""
import json, collections
d = json.load(open('map_data.json'))
step = {'south': (0, 1), 'north': (0, -1), 'east': (1, 0), 'west': (-1, 0)}
R, N = 100, 201
H = lambda x, z: d['heights'][(x + R) * N + (z + R)]
solid = set()
for n, hx, hz, r, f in d['houses']:
    for x, y, z, s in d['houseBlocks'][n]:
        if any(k in s for k in ('_door', 'air', 'torch', 'carpet', 'pressure_plate', 'flower', 'grass', 'brewing', 'dandelion', 'poppy', 'potted', 'bed')):
            # passable or non-solid for a walker: doors are handled below (closed doors open on use)
            if 'bed' in s or 'brewing' in s: solid.add((hx + x, y, hz + z))   # bed/stand are obstacles at foot level
            continue
        solid.add((hx + x, y, hz + z))
def support(x, y, z):   # is there a block to stand on at (x, y-1, z)
    return (x, y - 1, z) in solid or (y - 1 == H(x, z) and False)
def ground(x, z, y):    # the terrain top block is at prop-y = -1 (prop y 0 is one above grass)
    return y - 1 == -1
def floor_at(x, y, z):
    return (x, y - 1, z) in solid or y == 0          # y == 0 means standing on the grass surface
def free(x, y, z):      # 2 high body fits
    return (x, y, z) not in solid and (x, y + 1, z) not in solid
def bfs(start, goal_fn, limit=4000):
    q = collections.deque([start]); seen = {start}
    while q and len(seen) < limit:
        x, y, z = q.popleft()
        if goal_fn(x, y, z): return (x, y, z)
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            for dy in (0, 1, -1):
                nx, ny, nz = x + dx, y + dy, z + dz
                if ny < 0 or (nx, ny, nz) in seen: continue
                if free(nx, ny, nz) and floor_at(nx, ny, nz):
                    if dy == 1 and not free(x, y + 1, z): continue      # headroom to step up
                    seen.add((nx, ny, nz)); q.append((nx, ny, nz))
    return None
ok_all = True
for n, hx, hz, r, f in d['houses']:
    B = {(x, y, z): s for x, y, z, s in d['houseBlocks'][n]}
    doors = [(x, y, z) for (x, y, z), s in B.items() if s.startswith('minecraft:oak_door') and 'half=lower' in s]
    low = min(y for x, y, z in doors); sx, sz = step[f]
    dx, dz = max([(x, z) for x, y, z in doors if y == low], key=lambda c: c[0] * sx + c[1] * sz)
    # a closed door is passable (players and zombies open wooden doors), so remove the door blocks of this house from `solid`
    for (x, y, z), s in B.items():
        if 'door' in s: solid.discard((hx + x, y, hz + z))
    start = (hx + dx + 2 * sx, 0, hz + dz + 2 * sz)
    inside = lambda x, y, z: abs(x - (hx + dx - 2 * sx)) == 0 and abs(z - (hz + dz - 2 * sz)) == 0
    got = bfs(start, lambda x, y, z: (x, z) == (hx + dx - 2 * sx, hz + dz - 2 * sz))
    print('%-8s start %s -> inside cell %s : %s' % (n, start, (hx + dx - 2 * sx, hz + dz - 2 * sz), got))
    ok_all &= got is not None
print('ALL PASS' if ok_all else 'SOME FAIL')
