import sys; from schem import load
sym={}
def ch(name):
    n=name.split('[')[0].replace('minecraft:','')
    if n=='air': return '.'
    if n=='grass_block': return 'G'
    m={'stone':'s','cobblestone':'c','andesite':'a','redstone_block':'R','gold_block':'$','lodestone':'L','damaged_anvil':'V','skeleton_skull':'K','oxidized_lightning_rod':'!','nether_brick_fence':'f','polished_deepslate_wall':'w'}
    if n in m: return m[n]
    if n.endswith('_slab'): return '-'
    if n.endswith('_stairs'): return '<'
    if n.endswith('_wall'): return 'W'
    return '?'
f=sys.argv[1]
s,W,H,L,pal,ids,be=load(f)
print(f.split('/')[-1],'W',W,'H',H,'L',L,'BE',be)
for y in range(H-1,-1,-1):
    print('y=%d'%y)
    for z in range(L):
        print('  '+''.join(ch(pal[ids[(y*L+z)*W+x]]) for x in range(W)))
