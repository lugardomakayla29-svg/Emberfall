"""Converts real vanilla 1.21 village structure files into the map's prop format.

A prop is [(dx, dy, dz, 'blockstate string')] relative to an anchor, dy = 0 being the first block above the grass.
Vanilla files are x,y,z boxes with y = 0 the foundation layer, which lines up with dy = 0 for buildings whose
foundation is cobblestone or planks (the four chosen in HOUSES). Buildings with a dirt/grass yard at y = 0 are NOT
used because that layer would raise the ground.

Steps per block: jigsaw -> its final_state, structure_void/air skipped, then rotate the whole box so the front door
faces the wanted side, rotating every direction property (facing, axis, shape stays, rotation for signs is unused).
"""
import gzip, sys, re, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from schem import parse

HERE = os.path.dirname(os.path.abspath(__file__))
# (file, side the building's FRONT DOOR faces in the vanilla file, after reading the door block facing)
# vanilla: the front door block has facing=east and the entrance jigsaw sits on the west (x = 0) face, so the door
# faces OUT towards -x (west). We record the side the player stands on when entering: 'west'.
HOUSES = {
    'armorer':    'plains_armorer_house_1',
    'mason':      'plains_masons_house_1',
    'cottage':    'plains_small_house_1',
    'apothecary': 'plains_temple_4',
}
DROP = {'minecraft:chest', 'minecraft:lava', 'minecraft:structure_void', 'minecraft:air', 'minecraft:barrel',
        'minecraft:trapped_chest'}

DIRS = ['north', 'east', 'south', 'west']          # clockwise seen from above
def rot_dir(d, quarter_turns):
    if d in DIRS: return DIRS[(DIRS.index(d) + quarter_turns) % 4]
    return d                                        # up / down unchanged

def rot_props(props, q):
    out = {}
    for k, v in props.items():
        if k == 'facing' and v in DIRS: out[k] = rot_dir(v, q)
        elif k == 'axis' and v in ('x', 'z') and q % 2 == 1: out[k] = 'z' if v == 'x' else 'x'
        elif k in ('north', 'east', 'south', 'west') and v in ('none', 'low', 'tall', 'true', 'false', 'side', 'up'):
            out[rot_dir(k, q)] = v                  # fences, walls, panes: the side flags move with the rotation
        else: out[k] = v
    return out

def state_string(name, props):
    if not props: return name
    return '%s[%s]' % (name, ','.join('%s=%s' % (k, props[k]) for k in sorted(props)))

def rotate_xz(x, z, sx, sz, q):
    """Rotate a cell of an sx by sz box by q quarter turns clockwise (seen from above). Returns new x, z and new sx, sz."""
    for _ in range(q % 4):
        x, z = sz - 1 - z, x
        sx, sz = sz, sx
    return x, z, sx, sz

def load(file):
    d = parse(gzip.open(os.path.join(HERE, 'vanilla', file + '.nbt')).read())
    return d

def convert(file, q):
    """Return (blocks, width_x, depth_z) for the building rotated q quarter turns clockwise.
    Local cells are shifted so the box is centred on 0,0 (x from -w//2, z from -d//2)."""
    d = load(file)
    pal = d['palette']; sx, sy, sz = d['size']
    cells = []
    for b in d['blocks']:
        st = pal[b['state']]; name = st['Name']; props = dict(st.get('Properties', {}))
        if name == 'minecraft:jigsaw':
            fs = (b.get('nbt') or {}).get('final_state', 'minecraft:air')
            m = re.match(r'([a-z_:0-9]+)(?:\[(.*)\])?$', fs)
            name = m.group(1); props = dict(p.split('=') for p in m.group(2).split(',')) if m.group(2) else {}
        if name in DROP: continue
        if 'waterlogged' in props: props['waterlogged'] = 'false'
        x, y, z = b['pos']
        nx, nz, w, dd = rotate_xz(x, z, sx, sz, q)
        cells.append((nx, y, nz, name, rot_props(props, q)))
    _, _, w, dd = rotate_xz(0, 0, sx, sz, q)
    ox, oz = w // 2, dd // 2
    blocks = [(x - ox, y, z - oz, state_string(n, p)) for x, y, z, n, p in cells]
    return blocks, w, dd

# the vanilla front door faces WEST in every chosen file. Quarter turns clockwise needed to make it face `side`:
TURNS = {'west': 0, 'north': 1, 'east': 2, 'south': 3}

def house(kind, side):
    blocks, w, d = convert(HOUSES[kind], TURNS[side])
    return blocks

def door_cell(kind, side):
    """Local x,z of the cell just OUTSIDE the front door, for the verifier."""
    blocks, w, d = convert(HOUSES[kind], TURNS[side])
    doors = [(x, y, z) for x, y, z, s in blocks if s.startswith('minecraft:oak_door') and 'half=lower' in s]
    front = min(doors, key=lambda c: c[1])           # the lowest door is the ground floor front door
    return [(x, z) for x, y, z in doors if y == front[1]]

if __name__ == '__main__':
    for k in HOUSES:
        for side in ('west', 'north', 'east', 'south'):
            b, w, d = convert(HOUSES[k], TURNS[side])
            xs = [c[0] for c in b]; zs = [c[2] for c in b]
            print('%-10s %-6s box %2dx%2d  blocks %4d  x %3d..%3d  z %3d..%3d  doors %s' % (
                k, side, w, d, len(b), min(xs), max(xs), min(zs), max(zs), door_cell(k, side)))
