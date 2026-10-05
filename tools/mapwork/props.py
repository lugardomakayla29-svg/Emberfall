"""Hand-designed props as explicit block lists. Each returns [(dx, dy, dz, blockstate)] relative to the prop's anchor,
where dy=0 is the FIRST block above the ground surface (the surface block itself is the map's grass). Nothing random."""
import math

def house(face):
    """Plains-style villager house, 9 wide x 7 deep, door on the side named by `face` (the side that faces the plaza)."""
    W, D = 9, 7                       # footprint, x from -4..4, z from -3..3
    B = []
    OAK_LOG = 'minecraft:oak_log'; PLANK = 'minecraft:oak_planks'; COBBLE = 'minecraft:cobblestone'
    # foundation: cobblestone ring at y=0 with a floor of planks inside
    for x in range(-4, 5):
        for z in range(-3, 4):
            edge = x in (-4, 4) or z in (-3, 3)
            B.append((x, 0, z, COBBLE if edge else PLANK))
    # walls y=1..3; log posts on the four corners
    for y in range(1, 4):
        for x in range(-4, 5):
            for z in range(-3, 4):
                if not (x in (-4, 4) or z in (-3, 3)): continue
                corner = x in (-4, 4) and z in (-3, 3)
                B.append((x, y, z, OAK_LOG if corner else PLANK))
    # door (2 high) centred on the chosen side, and two glass windows on the other long walls
    doors = {'south': (0, 3), 'north': (0, -3), 'east': (4, 0), 'west': (-4, 0)}
    dx, dz = doors[face]
    B[:] = [b for b in B if not (b[0] == dx and b[2] == dz and b[1] in (1, 2))]
    B.append((dx, 1, dz, 'minecraft:oak_door[half=lower]')); B.append((dx, 2, dz, 'minecraft:oak_door[half=upper]'))
    for wx, wz in ((-2, -3), (2, -3), (-2, 3), (2, 3), (-4, 0), (4, 0)):
        if (wx, wz) == (dx, dz): continue
        B[:] = [b for b in B if not (b[0] == wx and b[2] == wz and b[1] == 2)]
        B.append((wx, 2, wz, 'minecraft:glass_pane'))
    # gable roof along x: stairs stepping in from both long sides, ridge of slabs
    for i, z in enumerate((-3, -2, -1)):
        y = 4 + i
        for x in range(-5, 6):
            B.append((x, y, z, 'minecraft:oak_stairs[facing=south,half=bottom]'))
            B.append((x, y, -z, 'minecraft:oak_stairs[facing=north,half=bottom]'))
    for x in range(-5, 6): B.append((x, 7, 0, 'minecraft:oak_slab[type=bottom]'))
    # gable end fill under the roof on the two short walls
    for y, zs in ((4, (-2, -1, 0, 1, 2)), (5, (-1, 0, 1)), (6, (0,))):
        for x in (-4, 4):
            for z in zs: B.append((x, y, z, PLANK))
    return B

def tree(size):
    """Oak tree: log trunk, blobby leaf crown. size 0 small (5 tall), 1 big (7 tall)."""
    h = 5 + 2 * size; B = []
    for y in range(h): B.append((0, y, 0, 'minecraft:oak_log[axis=y]'))
    r = 2 + size
    for y in range(h - 2, h + 2):
        rr = r if y < h else r - 1 if y == h else r - 2
        for x in range(-rr, rr + 1):
            for z in range(-rr, rr + 1):
                if x * x + z * z > rr * rr + 1: continue
                if x == 0 and z == 0 and y < h: continue
                B.append((x, y, z, 'minecraft:oak_leaves[persistent=true]'))
    return B

def boulder(size):
    """A small rock built by hand from stone, cobblestone and andesite. size 1 = 3 wide, size 2 = 5 wide."""
    B = []
    r = 1 if size == 1 else 2
    for x in range(-r, r + 1):
        for z in range(-r, r + 1):
            d = abs(x) + abs(z)
            if d > r + 1: continue
            top = (3 if d == 0 else 2 if d <= r - 1 else 1) if r == 2 else (2 if d == 0 else 1)
            for y in range(top):
                B.append((x, y, z, ['minecraft:stone', 'minecraft:cobblestone', 'minecraft:andesite'][(x * 7 + z * 3 + y) % 3]))
    return B

if __name__ == '__main__':
    for f in ('south', 'north', 'east', 'west'):
        h = house(f); print('house', f, len(h), 'blocks')
    print('tree small', len(tree(0)), 'big', len(tree(1)), '| boulder 1', len(boulder(1)), '2', len(boulder(2)))
