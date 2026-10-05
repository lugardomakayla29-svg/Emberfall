# After a coil the parts sit on a radius-5.5 ring; the chain solve then resumes with the head walking away. Does it recover to a line without stretching or burying parts?
import math
exec(open('coil_sim.py').read().split("FL=69.0")[0])
FL=69.0; R=5.5
ang=0.0
pos=[(R*math.cos(ang-i*SP/R),FL+SIZES[i]*0.5,R*math.sin(ang-i*SP/R)) for i in range(N)]
pos[0]=(R*math.cos(ang),FL+1.05,R*math.sin(ang))
head=list(pos[0]); worst_gap=0; min_y=99
for t in range(80):
    # the boss dives: head sinks and moves outward 0.5 blocks/tick like a dash
    head[0]+=0.5; update(pos,(head[0],head[1],head[2]),FL)
    gaps=[ln(sub(pos[i],pos[i-1])) for i in range(1,N)]
    worst_gap=max(worst_gap,max(gaps)); min_y=min(min_y,min(p[1]-SIZES[i]*0.5 for i,p in enumerate(pos) if i>0))
    if t in (0,10,40,79): print(f'tick {t}: gaps {min(gaps):.2f}-{max(gaps):.2f}, tail distance from head {ln(sub(pos[-1],pos[0])):.1f}')
print('worst neighbour gap', round(worst_gap,3), '| lowest part bottom above floor', round(min_y,3))
