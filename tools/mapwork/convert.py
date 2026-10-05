"""Converts a WorldEdit Sponge v3 .schem into a clean block list for the mod.
Rules from the user (2026-09-30):
  * GRASS BLOCKS are copy markers, not part of the build: DROP them.
  * AIR blocks must not overwrite terrain: DROP them (only solid blocks are written).
  * Everything else is kept exactly, with its block state.
Also trims the bounds to the real build so the stray markers do not inflate the footprint."""
import json, sys, os
from schem import load
def convert(path):
    s,W,H,L,pal,ids,be=load(path)
    blocks=[]; dropped={'air':0,'grass_block':0}
    for y in range(H):
        for z in range(L):
            for x in range(W):
                name=pal[ids[(y*L+z)*W+x]]
                base=name.split('[')[0]
                if base=='minecraft:air': dropped['air']+=1; continue
                if base=='minecraft:grass_block': dropped['grass_block']+=1; continue
                blocks.append((x,y,z,name))
    if not blocks: raise SystemExit('no solid blocks in '+path)
    minx=min(b[0] for b in blocks); miny=min(b[1] for b in blocks); minz=min(b[2] for b in blocks)
    maxx=max(b[0] for b in blocks); maxy=max(b[1] for b in blocks); maxz=max(b[2] for b in blocks)
    out={'source':os.path.basename(path),'originalSize':[W,H,L],
         'trimmedSize':[maxx-minx+1,maxy-miny+1,maxz-minz+1],
         'dropped':dropped,
         'blocks':[[x-minx,y-miny,z-minz,n] for x,y,z,n in blocks],
         'blockEntities':[{'pos':[e['Pos'][0]-minx,e['Pos'][1]-miny,e['Pos'][2]-minz],'id':e['Id']} for e in be]}
    return out
if __name__=='__main__':
    for f in sys.argv[1:]:
        o=convert(f)
        print(o['source'],'orig',o['originalSize'],'-> trimmed',o['trimmedSize'],'solid',len(o['blocks']),'dropped',o['dropped'],'BE',o['blockEntities'])
