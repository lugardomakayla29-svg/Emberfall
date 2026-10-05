"""Writes the static EMBERFALL expedition map as a compact JSON data file plus a verification report.
Everything is deterministic. The game reads this file; it never generates anything at run time.
Frame: x,z are blocks from the circle centre (+x east, +z south). y=0 is the play surface; the base fills y -6..-1."""
import json, math, sys
sys.path.insert(0, '.')
import layout, objects, wall
from convert import convert

I = __import__('os').environ.get('EMBERFALL_ASSETS', 'incoming_files') + '/'
SCHEMS = {"challenge": '1d0a6c1b5_challenge_shrine.schem', "curse": 'c5887f4f2_curse_boss.schem', "greed": '08d5134ae_shrine_of_greed.schem'}
R_OUT, R_FLOOR = 100, 95

def build():
    wall.R_FLOOR = R_FLOOR; wall.R_OUT = R_OUT; wall.lean = lambda y: 0
    objects.register_all_pads()
    wall_tops = wall.build()
    # integer height field over the floor disc, index = (x+100)*201 + (z+100)
    hf = []
    for x in range(-R_OUT, R_OUT + 1):
        for z in range(-R_OUT, R_OUT + 1):
            hf.append(int(round(layout.height(x, z))) if math.hypot(x, z) <= R_FLOOR else 0)
    out, _ = objects.layout_all()
    shrines = []
    for name, x, z, r in objects.SHRINES:
        s = convert(I + SCHEMS[name])
        shrines.append({"type": name, "x": x, "z": z, "size": s["trimmedSize"], "blocks": s["blocks"], "blockEntities": s["blockEntities"]})
    import props, vanilla_houses
    data = {"radiusOut": R_OUT, "radiusFloor": R_FLOOR, "baseDepth": 6, "heights": hf, "wallTops": wall_tops,
            "shrines": shrines, "houses": out["houses"], "trees": out["trees"], "boulders": out["boulders"],
            "houseBlocks": {n: vanilla_houses.house(objects.HOUSE_KIND[n], f) for n, x, z, r, f in out["houses"]},
            "treeBlocks": [props.tree(0), props.tree(1)], "boulderBlocks": {"1": props.boulder(1), "2": props.boulder(2)},
            "entry": list(objects.PLAYER_ENTRY), "boss": list(objects.BOSS_SPAWN), "spawnRing": list(objects.SPAWN_RING)}
    return data

if __name__ == '__main__':
    d = build()
    json.dump(d, open('map_data.json', 'w'), separators=(',', ':'))
    import os
    print('map_data.json', os.path.getsize('map_data.json'), 'bytes;',
          {k: (len(v) if isinstance(v, list) else v) for k, v in d.items() if k not in ('shrines',)}, 'shrines', [(s['type'], s['size'], len(s['blocks'])) for s in d['shrines']])
