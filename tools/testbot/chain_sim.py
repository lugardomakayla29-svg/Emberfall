import math
SIZES=[2.1,1.7,1.5,1.3,1.1,0.9]; SP=1.5; SAG=0.45
def sub(a,b): return tuple(x-y for x,y in zip(a,b))
def add(a,b): return tuple(x+y for x,y in zip(a,b))
def sc(a,k): return tuple(x*k for x in a)
def ln(a): return math.sqrt(sum(x*x for x in a))
def update(pos, head, floor):            # exact port of WormBody.update steps 1-3
    pos[0]=head
    for i in range(1,len(pos)):          # 1 gravity
        rest=floor+SIZES[i]*0.5
        if pos[i][1]>rest: pos[i]=(pos[i][0],max(rest,pos[i][1]-SAG),pos[i][2])
    for _ in range(2):                   # 2 two-sided distance constraint
        for i in range(1,len(pos)):
            lead=pos[i-1]; to=sub(pos[i],lead); d=ln(to)
            dr=sc(to,1/d) if d>1e-6 else (0,0,-1)
            pos[i]=add(lead,sc(dr,SP))
    for i in range(1,len(pos)):          # 3 floor last
        rest=floor+SIZES[i]*0.5
        if pos[i][1]<rest: pos[i]=(pos[i][0],rest,pos[i][2])
def fresh(head):
    return [add(head,(0,0,-i*SP)) for i in range(6)]
def gaps(pos): return [ln(sub(pos[i],pos[i-1])) for i in range(1,6)]
ok=True
def check(n,c,note):
    global ok; ok&=c; print(('PASS' if c else 'FAIL'),n,note)

# S1: resting head, 200 ticks, must not move at all after it has settled
FL=69.0; head=(0,FL+1.05,0); pos=fresh(head)
for _ in range(20): update(pos,head,FL)
before=list(pos)
for _ in range(200): update(pos,head,FL)
mv=max(ln(sub(a,b)) for a,b in zip(pos,before))
check('S1 rest',mv<1e-9,f'largest move over 200 idle ticks = {mv:.6f}')
check('S1 spacing',all(abs(g-SP)<1e-6 for g in gaps(pos)),f'gaps={[round(g,3) for g in gaps(pos)]}')

# S2: leap arc up 9 blocks over 30 ticks then rest on the SAME floor: never stretches, ends settled on the floor
pos=fresh(head); worst=0; low=99
for t in range(30):
    s=(t+1)/30; y=FL+1.05+4*9*s*(1-s); x=-9*s
    update(pos,(x,y,0),FL); worst=max(worst,max(gaps(pos)))
for _ in range(60): update(pos,(-9,FL+1.05,0),FL); worst=max(worst,max(gaps(pos))); low=min(low,min(p[1]-FL for p in pos[1:]))
top=max(p[1] for p in pos[1:])-FL
check('S2 no stretch',worst<=SP+1e-6,f'largest gap during leap+landing = {worst:.4f} (spacing {SP})')
check('S2 never underground',low>=0.44,f'lowest body centre above floor = {low:.2f} (smallest half-size 0.45)')
check('S2 settles',top<=1.0,f'highest body part 60 ticks after landing = {top:.2f} above floor')

# S3: leap lands on HIGHER ground (the y=76 bug): body must rest on the new floor once the floor is updated
pos=fresh(head); FL2=76.0
for t in range(30):
    s=(t+1)/30; y=FL+1.05+ (FL2-FL)*s+4*9*s*(1-s); update(pos,(-9*s,y,0),FL)
for _ in range(80): update(pos,(-9,FL2+1.05,0),FL2)
check('S3 higher ground',all(abs(pos[i][1]-(FL2+SIZES[i]*.5))<0.5 or pos[i][1]-FL2<2.6 for i in range(1,6)) and max(gaps(pos))<=SP+1e-6,f'body y above new floor={[round(p[1]-FL2,2) for p in pos[1:]]} gaps ok={max(gaps(pos))<=SP+1e-6}')
# the OLD behaviour (floor left at 69 while standing at 76) for contrast
pos=fresh(head)
for t in range(30):
    s=(t+1)/30; update(pos,(-9*s,FL+1.05+(FL2-FL)*s+4*9*s*(1-s),0),FL)
for _ in range(80): update(pos,(-9,FL2+1.05,0),FL)
print('   contrast, old wrong floor 69 while standing at 76: max gap =',round(max(gaps(pos)),3))
print('ALL PASS' if ok else 'SOME FAIL')
