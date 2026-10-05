"""Proves whether a player can WALK/JUMP from the floor to the top of the wall.
Model (vanilla): a player stands on a solid block with 2 free blocks above; from a standable cell it can
move to a horizontal neighbour (4-dir, plus diagonals) whose floor is at most +1 higher (a jump), or any
amount LOWER (a fall). Pillar/ledge notches in the wall create standable cells; if any chain reaches y >= TOP-1
the wall is climbable."""
import math, sys
from collections import deque
from wall import build, solid, R_OUT, R_FLOOR as R_IN, TOP
def prove(heights, x_range=range(-101,102)):
    S=lambda x,y,z: solid(x,y,z,heights)
    def standable(x,y,z):  # feet at y, floor at y-1 solid, y and y+1 free
        return S(x,y-1,z) and not S(x,y,z) and not S(x,y+1,z)
    # start: any standable cell on the play floor (y=0) just inside the wall
    starts=[]
    for x in range(-R_IN,R_IN+1):
        for z in range(-R_IN,R_IN+1):
            d=math.hypot(x,z)
            if R_IN-3<=d<R_IN-1 and standable(x,0,z): starts.append((x,0,z))
    seen=set(starts); q=deque(starts); best=0; bestpos=None
    while q:
        x,y,z=q.popleft()
        if y>best: best=y; bestpos=(x,y,z)
        for dx in (-1,0,1):
            for dz in (-1,0,1):
                if dx==0 and dz==0: continue
                nx,nz=x+dx,z+dz
                if math.hypot(nx,nz)>R_OUT+1: continue
                for ny in (y+1,y,y-1,y-2,y-3):   # up at most 1; down up to 3 counted as a safe drop
                    if (nx,ny,nz) in seen: continue
                    if standable(nx,ny,nz):
                        # moving up 1 needs headroom over the current cell too (y+2 free)
                        if ny==y+1 and S(x,y+2,z): continue
                        seen.add((nx,ny,nz)); q.append((nx,ny,nz))
    return best,bestpos,len(seen)
if __name__=='__main__':
    h=build()
    best,pos,n=prove(h)
    print('reachable standable cells:',n,'| highest reachable y =',best,'at',pos,'| wall top min',min(h))
    print('CLIMBABLE' if best>=min(h)-1 else 'NOT CLIMBABLE by walking/jumping (highest reachable y=%d of %d)'%(best,min(h)))
