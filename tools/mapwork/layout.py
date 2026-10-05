"""EMBERFALL expedition map blueprint. Deterministic: no random calls except a fixed-seed generator for
decor placement. Coordinates are blocks relative to the CENTRE of the circle. +x east, +z south.
Radius 100 (200 across). Playable radius = 95 (the wall ring is 95..100)."""
import math, random
R_OUT=100; R_IN=95
FLOOR=0            # the flat play surface is y=0 (relative), base occupies -6..-1
def smooth(t): 
    t=max(0.0,min(1.0,t)); return t*t*(3-2*t)
def hill(x,z,cx,cz,r,h,flat=0.45):
    """A big rounded hill with a flat top plateau. r = radius of the whole footprint, h = height."""
    d=math.hypot(x-cx,z-cz)
    if d>=r: return 0.0
    t=1-d/r
    return h*smooth(t/(1-flat)) if t<(1-flat) else h
def ramp(x,z,x0,z0,x1,z1,w,h):
    """A straight ramp from (x0,z0) at height 0 up to (x1,z1) at height h, width w, then a flat top."""
    dx,dz=x1-x0,z1-z0; L=math.hypot(dx,dz); ux,uz=dx/L,dz/L
    px,pz=x-x0,z-z0
    along=px*ux+pz*uz; across=abs(-px*uz+pz*ux)
    if along<0 or along>L or across>w/2: return 0.0
    edge=smooth((w/2-across)/3.0)            # soft 3-block shoulders
    return h*smooth(along/L)*edge
# ---- named landforms (the whole design fits in this list) ----
HILLS=[  # name, cx, cz, radius, height
 ("North Bluff",     -10, -62, 30, 9),
 ("East Ridge",       64,  -8, 26, 12),
 ("West Knoll",      -68,  20, 22,  8),
 ("South Mesa",       18,  66, 28, 10),
 ("Overlook",        -60, -52, 14, 12),
]
RAMPS=[ # name, x0,z0 -> x1,z1, width, height
 ("Bluff Ramp",   -10, -34, -10, -50, 9, 7),
 ("Mesa Ramp",     18,  40,  18,  54, 9, 6),
]
# flat pads: (cx, cz, flat radius, blend radius, height). Inside the flat radius the ground is EXACTLY this height;
# out to the blend radius it eases back to the natural terrain. Used under every structure so nothing hangs or sinks.
PADS=[]   # filled by objects.py through register_pad before the field is built
def register_pad(cx,cz,flat_r,blend_r,height=0.0):
    global _field
    PADS.append((cx,cz,flat_r,blend_r,height)); _field=None
def _raw(x,z):
    if math.hypot(x,z)>R_IN: return 0.0
    h=0.0
    for _,cx,cz,r,hh in HILLS: h=max(h,hill(x,z,cx,cz,r,hh))
    for _,x0,z0,x1,z1,w,hh in RAMPS: h=max(h,ramp(x,z,x0,z0,x1,z1,w,hh))
    for cx,cz,fr,br,ph in PADS:
        d=math.hypot(x-cx,z-cz)
        if d<=fr: return ph
        if d<br:
            t=smooth((d-fr)/(br-fr)); h=ph*(1-t)+h*t
    return h
SLOPE=1.0   # max blocks of rise per block of run, so every hill is walkable without jumping
_field=None
def _build():
    global _field
    N=2*R_OUT+1
    f=[[_raw(x-R_OUT,z-R_OUT) for z in range(N)] for x in range(N)]
    # relax: a cell may not exceed a neighbour by more than SLOPE (two sweeps each direction)
    for _ in range(2):
        for x in range(N):
            for z in range(N):
                for dx,dz in ((-1,0),(1,0),(0,-1),(0,1)):
                    nx,nz=x+dx,z+dz
                    if 0<=nx<N and 0<=nz<N and f[x][z]>f[nx][nz]+SLOPE: f[x][z]=f[nx][nz]+SLOPE
        for x in range(N-1,-1,-1):
            for z in range(N-1,-1,-1):
                for dx,dz in ((-1,0),(1,0),(0,-1),(0,1)):
                    nx,nz=x+dx,z+dz
                    if 0<=nx<N and 0<=nz<N and f[x][z]>f[nx][nz]+SLOPE: f[x][z]=f[nx][nz]+SLOPE
    _field=f
def height(x,z):
    if _field is None: _build()
    ix,iz=int(round(x))+R_OUT,int(round(z))+R_OUT
    if 0<=ix<=2*R_OUT and 0<=iz<=2*R_OUT: return _field[ix][iz]
    return 0.0
if __name__=='__main__':
    from PIL import Image
    S=4
    im=Image.new('RGB',(2*R_OUT*S//2+0,2*R_OUT*S//2+0))
    N=2*R_OUT
    im=Image.new('RGB',(N*2,N*2))
    px=im.load()
    for ix in range(N*2):
        for iz in range(N*2):
            x=ix/2-R_OUT; z=iz/2-R_OUT
            d=math.hypot(x,z)
            if d>R_OUT: px[ix,iz]=(200,220,255); continue
            if d>R_IN: px[ix,iz]=(70,60,60); continue
            h=height(x,z)
            g=int(90+h*11)
            px[ix,iz]=(60,min(255,g),60)
    im.save('height_v1.png'); print('saved',im.size)
