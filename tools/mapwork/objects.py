"""Fixed object layout for the EMBERFALL expedition map. Every position is hand-chosen (no random placement
for anything that matters); only tree/boulder DECOR uses a fixed-seed scatter, and each decor item is rejected
unless it keeps clear of everything else. x,z are blocks from the circle centre, +x east, +z south."""
import math, random, layout

# --- hand-placed structures: name, x, z, footprint half-size (blocks), kind ---
SHRINES = [
    ("challenge", -46, -12, 5),   # west, flat ground
    ("curse",      44,  34, 5),   # south east, flat ground
    ("greed",      -6,  44, 4),   # south, flat ground
]
VILLAGE = [   # a small plains village in the north east: 4 houses round a plaza, each facing the plaza
    ("house_a",  30, -52, 5, "south"),
    ("house_b",  44, -44, 5, "west"),
    ("house_c",  18, -44, 5, "east"),
    ("house_d",  30, -36, 5, "north"),
]
# which real vanilla village building stands on each pad (see vanilla_houses.py); keyed by the house name above
HOUSE_KIND = {"house_a": "armorer", "house_b": "mason", "house_c": "cottage", "house_d": "apothecary"}
PLAYER_ENTRY = (0, 0)
BOSS_SPAWN   = (0, -24)
SPAWN_RING   = (54, 86)     # mobs spawn on this ring (radius range), clear of the walls and the shrines
R_FLOOR = 95

def clear_of(x, z, r, placed, gap=3):
    for (px, pz, pr) in placed:
        if math.hypot(x - px, z - pz) < r + pr + gap: return False
    return True

def flat_ring(x, z, r):
    """True when every ground cell within r of (x, z) is exactly height 0, so a prop never straddles a slope."""
    return all(layout.height(x + dx, z + dz) == 0 for dx in range(-r, r + 1) for dz in range(-r, r + 1) if dx * dx + dz * dz <= r * r)

def layout_all(seed=11):
    placed = [(PLAYER_ENTRY[0], PLAYER_ENTRY[1], 8), (BOSS_SPAWN[0], BOSS_SPAWN[1], 9)]
    out = {"shrines": [], "houses": [], "trees": [], "boulders": []}
    for n, x, z, r in SHRINES: out["shrines"].append((n, x, z, r)); placed.append((x, z, r))
    for n, x, z, r, face in VILLAGE: out["houses"].append((n, x, z, r, face)); placed.append((x, z, r))
    rnd = random.Random(seed)
    tries = 0
    while len(out["trees"]) < 60 and tries < 20000:
        tries += 1
        a = rnd.random() * 2 * math.pi; d = rnd.uniform(12, R_FLOOR - 6)
        x, z = int(round(math.cos(a) * d)), int(round(math.sin(a) * d))
        if layout.height(x, z) > 0.5: continue           # trees only on flat ground
        kind = len(out["trees"]) % 2            # alternate small / big, matching props.tree(kind)
        cr = 3 + kind                            # crown radius + 1: small tree r2, big tree r3
        if not clear_of(x, z, cr, placed, 1): continue
        if not flat_ring(x, z, cr): continue
        out["trees"].append((x, z)); placed.append((x, z, cr))
    tries = 0
    while len(out["boulders"]) < 30 and tries < 20000:
        tries += 1
        a = rnd.random() * 2 * math.pi; d = rnd.uniform(14, R_FLOOR - 5)
        x, z = int(round(math.cos(a) * d)), int(round(math.sin(a) * d))
        if layout.height(x, z) > 0.5: continue
        sz = rnd.choice((1, 1, 2))
        if not clear_of(x, z, sz + 1, placed, 1): continue
        if not flat_ring(x, z, sz + 1): continue
        out["boulders"].append((x, z, sz)); placed.append((x, z, sz + 1))
    return out, placed

def register_all_pads():
    # Every structure gets a flat pad bigger than its footprint, plus the player entry and boss spot.
    for n, x, z, r in SHRINES: layout.register_pad(x, z, r + 3, r + 9)
    for n, x, z, r, f in VILLAGE: layout.register_pad(x, z, r + 2, r + 7)
    layout.register_pad(PLAYER_ENTRY[0], PLAYER_ENTRY[1], 9, 15)
    layout.register_pad(BOSS_SPAWN[0], BOSS_SPAWN[1], 10, 16)
    # the village plaza between the four houses
    layout.register_pad(30, -44, 14, 20)

def pad_flatness(x, z, r):
    hs = [layout.height(x + dx, z + dz) for dx in range(-r, r + 1) for dz in range(-r, r + 1) if dx * dx + dz * dz <= r * r]
    return min(hs), max(hs)

if __name__ == "__main__":
    register_all_pads()
    out, placed = layout_all()
    print({k: len(v) for k, v in out.items()})
    for n, x, z, r in SHRINES: print("shrine", n, (x, z), "pad height range", pad_flatness(x, z, r), "dist from centre %.1f" % math.hypot(x, z))
    for n, x, z, r, f in VILLAGE: print("house", n, (x, z), "pad", pad_flatness(x, z, r))
    print("boss spawn pad", pad_flatness(*BOSS_SPAWN, 8), "entry pad", pad_flatness(0, 0, 8))
