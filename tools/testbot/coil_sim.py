# Exact port of WormBody.update (chain_sim.py) with 20 parts, head driven round a circle. Answers with numbers:
# what ring does the body form, how long until it closes, what gap is left, and how fast must the head run?
import math
SP=1.5; SAG=0.45; N=20
SIZES=[2.1]+[1.7-0.8*i/(N-2) for i in range(N-1)]    # head + 19 body (taperedScale over 19 segments)
def sub(a,b): return tuple(x-y for x,y in zip(a,b))
def add(a,b): return tuple(x+y for x,y in zip(a,b))
def sc(a,k): return tuple(x*k for x in a)
def ln(a): return math.sqrt(sum(x*x for x in a))
def update(pos, head, floor):
    pos[0]=head
    for i in range(1,len(pos)):
        rest=floor+SIZES[i]*0.5
        if pos[i][1]>rest: pos[i]=(pos[i][0],max(rest,pos[i][1]-SAG),pos[i][2])
    for _ in range(2):
        for i in range(1,len(pos)):
            lead=pos[i-1]; to=sub(pos[i],lead); d=ln(to)
            dr=sc(to,1/d) if d>1e-6 else (0,0,-1)
            pos[i]=add(lead,sc(dr,SP))
    for i in range(1,len(pos)):
        rest=floor+SIZES[i]*0.5
        if pos[i][1]<rest: pos[i]=(pos[i][0],rest,pos[i][2])
FL=69.0
def run(R, speed, ticks, label):
    # head moves on the circle of radius R at `speed` blocks/tick; body starts in a straight line trailing it
    w=speed/R; a0=0.0
    head=(R*math.cos(a0),FL+1.05,R*math.sin(a0))
    tang=(-math.sin(a0),0,math.cos(a0))
    pos=[add(head,sc(tang,-i*SP)) for i in range(N)]
    for t in range(ticks):
        a=a0+w*(t+1); update(pos,(R*math.cos(a),FL+1.05,R*math.sin(a)),FL)
    rad=[math.hypot(p[0],p[2]) for p in pos]
    gap=ln(sub(pos[0],pos[-1]))
    print(f'{label}: R={R} speed={speed} blocks/tick ({speed*20:.0f}/s) ticks={ticks} | body radius min {min(rad[1:]):.2f} max {max(rad):.2f} | tail-to-head gap {gap:.2f} | lap time {2*math.pi*R/speed/20:.1f}s')
    return pos
# the body is 28.5 long: a radius-R ring has circumference 2*pi*R, so coverage fraction is 28.5/(2*pi*R)
for R in (3,4,5,6):
    print(f'R={R}: circumference {2*math.pi*R:.1f}, body 28.5 covers {min(1,28.5/(2*math.pi*R))*100:.0f}% of the ring, free arc {max(0,2*math.pi*R-28.5-1.5):.1f} blocks')
print()
for sp in (0.5,0.7,0.85):
    run(6.0,sp,120,'encircle')
print('\n--- steady state after more laps (does the body settle ON the circle?) ---')
for R in (5.0,6.0,7.0):
    for sp in (0.5,0.7):
        for ticks in (120,240,400):
            run(R,sp,ticks,'laps')
