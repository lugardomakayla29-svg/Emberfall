import java.util.*;
public class PoseCheck {
    static int fails=0;
    static void check(String n, boolean ok, String note){ System.out.println((ok?"PASS ":"FAIL ")+n+" "+note); if(!ok) fails++; }
    public static void main(String[] a){
        Vec3 idle=new Vec3(5,0.6,0), rear=new Vec3(1,5,0), strike=new Vec3(6,0,3);
        for(int windup : new int[]{18,24,36,40,50}){
            // P1: at tick 0 the arm is at idle, at the resolve tick exactly on the strike, never on the strike earlier
            Vec3 p0=TentaclePose.blend(idle,rear,strike,TentaclePose.progress(0,windup),false,0.25);
            Vec3 pe=TentaclePose.blend(idle,rear,strike,TentaclePose.progress(windup,windup),false,0.25);
            double earliest=1e9; int firstOn=-1;
            for(int t=0;t<=windup;t++){ Vec3 p=TentaclePose.blend(idle,rear,strike,TentaclePose.progress(t,windup),false,0.25); if(p.distanceTo(strike)<0.05){ firstOn=t; break; } }
            check("P1 windup "+windup+": starts at idle, lands exactly on resolve tick", p0.distanceTo(idle)<1e-9 && pe.distanceTo(strike)<1e-9 && firstOn==windup, "firstOnStrike="+firstOn+"/"+windup);
            // P2: the TARGET may snap in the last quarter (that is the strike); what must hold is that the target never leaves the
            // straight corridor idle -> rear -> strike, so it can never overshoot or wander. Measured joint speed is in JointStrike.
            boolean corridor=true;
            for(int t=0;t<=windup;t++){ Vec3 p=TentaclePose.blend(idle,rear,strike,TentaclePose.progress(t,windup),false,0.25);
                double viaRear=p.distanceTo(idle)+p.distanceTo(rear)-idle.distanceTo(rear);
                double viaStrike=p.distanceTo(rear)+p.distanceTo(strike)-rear.distanceTo(strike);
                if(Math.min(viaRear,viaStrike)>1e-6) corridor=false; }
            check("P2 windup "+windup+": target stays on the idle-rear-strike path", corridor, "");
        }
        // P3: after resolve the pose is the strike, regardless of progress
        check("P3 resolved holds the strike point", TentaclePose.blend(idle,rear,strike,0.3,true,0.25).distanceTo(strike)<1e-9, "");
        // P4: ring arms are on the ring edge and evenly spread
        Vec3 c=new Vec3(10,65,-4); boolean onEdge=true; double minGap=1e9;
        for(int t=0;t<4;t++){ Vec3 s=TentaclePose.ringStrike(c,t,4,24.0,0.7); if(Math.abs(Math.hypot(s.x-c.x,s.z-c.z)-24.0)>1e-9) onEdge=false; Vec3 s2=TentaclePose.ringStrike(c,(t+1)%4,4,24.0,0.7); minGap=Math.min(minGap,s.distanceTo(s2)); }
        check("P4 ring arms land on the outer edge, spread evenly", onEdge && minGap>30.0, "minGap="+String.format("%.1f",minGap));
        // P5: fan strikes stay inside the cone and inside range
        Vec3 o=new Vec3(0,65,0), dir=new Vec3(0.6,0,0.8); boolean inCone=true; double halfDeg=35, range=6.0;
        for(int t=0;t<4;t++){ Vec3 s=TentaclePose.fanStrike(o,dir,range,halfDeg,t,4); Vec3 to=s.subtract(o); double d=Math.hypot(to.x,to.z); double cos=(to.x*dir.x+to.z*dir.z)/d; double ang=Math.toDegrees(Math.acos(Math.min(1,cos)));
            if(d>range||ang>halfDeg) inCone=false; }
        check("P5 fan arms land inside the locked cone and range", inCone, "");
        // P6: sparks: each arm on a circle centre, extra arms reuse the first
        List<Vec3> cs=List.of(new Vec3(1,0,1),new Vec3(5,0,2),new Vec3(-3,0,4));
        check("P6 spark arms land on circle centres", TentaclePose.sparkStrike(cs,0).distanceTo(cs.get(0))<1e-9 && TentaclePose.sparkStrike(cs,2).distanceTo(cs.get(2))<1e-9 && TentaclePose.sparkStrike(cs,3).distanceTo(cs.get(2))<1e-9, "");
        // P7: smoothstep bounds
        check("P7 smoothstep is 0 at 0, 1 at 1, clamped outside", TentaclePose.smooth(0)==0 && TentaclePose.smooth(1)==1 && TentaclePose.smooth(-5)==0 && TentaclePose.smooth(9)==1, "");
        System.out.println(fails==0?"ALL PASS":"SOME FAIL "+fails);
    }
}
