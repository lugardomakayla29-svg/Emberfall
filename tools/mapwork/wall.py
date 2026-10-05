"""Ring wall v2 (overhang lip). x,z relative to circle centre; y=0 is the play surface, base occupies y -6..-1.
Play floor radius R_FLOOR=95. The wall is thicker than the user's 5: solid from R_BASE (91) out to R_OUT (100)
at the bottom, but its INNER face recedes OUTWARD as it rises (an overhang), so the wall leans away from the player:
   inner_face_radius(y) = R_FLOOR + lean(y)   (lean grows with height; at y>=OVER_START it steps out 1 block per 3 up)
Below y=0 the face sits at R_FLOOR. Nothing inside the face is solid above y=0 except the floor.
Tops are jagged: fixed-seed basalt pillars, 3-6 blocks wide (in arc), tops 17..20 high."""
import math, random
R_OUT=100; R_FLOOR=88; TOP=20; OVER_START=4   # floor radius 88: the wall is 12 thick at the base
def build(seed=7):
    rnd=random.Random(seed); n_arc=int(2*math.pi*R_OUT); heights=[]; i=0
    while i<n_arc:
        w=rnd.randint(3,6); h=rnd.randint(17,TOP)
        heights += [h]*min(w,n_arc-i); i+=w
    return heights
def lean(y):            # how far the inner face has moved OUTWARD at height y (blocks)
    return 0 if y<OVER_START else min(4,1+(y-OVER_START)//4)
def pillar_top(x,z,heights):
    ang=(math.atan2(z,x)%(2*math.pi)); return heights[int(ang/(2*math.pi)*len(heights))%len(heights)]
def solid(x,y,z,heights):
    d=math.hypot(x,z)
    if d>R_OUT or y<-6: return False
    if y<0: return True                       # the 6-thick base, whole disc
    if d<R_FLOOR+lean(y): return False        # open air inside the (receding) face
    return y<pillar_top(x,z,heights)
if __name__=='__main__':
    h=build(); print('tops',min(h),max(h))
    for y in (0,4,7,10,13,16,19): print('y=%2d inner face radius %d, wall thickness %d'%(y,R_FLOOR+lean(y),R_OUT-(R_FLOOR+lean(y))))
