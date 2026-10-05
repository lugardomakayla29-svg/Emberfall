public class Check {
    static int fails=0;
    static void check(String n, boolean ok, String note){ System.out.println((ok?"PASS ":"FAIL ")+n+" "+note); if(!ok) fails++; }
    public static void main(String[] a){
        int N=8; double S=0.9; Vec3 root=new Vec3(0,2,0);
        Vec3[] j=new Vec3[N]; TentacleMath.lay(j,root,new Vec3(1,0,0),S);
        // T1: reachable target, tip lands on it, root pinned, links exact
        Vec3 tgt=new Vec3(3,0.5,2);  // 3.6 away, reach is 6.3
        TentacleMath.solve(j,root,tgt,S,4);
        check("T1 tip reaches a reachable target", j[N-1].distanceTo(tgt)<0.02, "tipErr="+String.format("%.4f",j[N-1].distanceTo(tgt)));
        check("T2 root never moves", j[0].distanceTo(root)<1e-9, "rootErr="+j[0].distanceTo(root));
        check("T3 every link is one spacing", TentacleMath.worstLinkError(j,S)<0.02, "worst="+String.format("%.4f",TentacleMath.worstLinkError(j,S)));
        // T4: unreachable target: straight line pointing at it, tip at full reach
        Vec3 far=new Vec3(20,2,0); TentacleMath.solve(j,root,far,S,2);
        check("T4 unreachable target gives a straight full-length chain", Math.abs(j[N-1].distanceTo(root)-S*(N-1))<1e-6 && TentacleMath.worstLinkError(j,S)<1e-6, "len="+String.format("%.3f",j[N-1].distanceTo(root)));
        check("T5 unreachable chain points at the target", j[N-1].x>j[0].x+6 && Math.abs(j[N-1].z)<1e-6, "tip="+j[N-1].x);
        // T6: target exactly at the root (fully folded request) must not produce NaN
        TentacleMath.lay(j,root,new Vec3(1,0,0),S); TentacleMath.solve(j,root,root,S,4);
        boolean nan=false; for(Vec3 p:j) if(Double.isNaN(p.x)||Double.isNaN(p.y)||Double.isNaN(p.z)) nan=true;
        check("T6 target at the root stays finite", !nan, "");
        // T7: dragging the target around for 400 ticks never breaks links or detaches the root
        double worst=0, rootDrift=0; TentacleMath.lay(j,root,new Vec3(0,1,0),S);
        for(int t=0;t<400;t++){
            double ang=t*0.09; Vec3 g=new Vec3(Math.cos(ang)*4.5, 1.0+Math.sin(t*0.05)*3.0, Math.sin(ang)*4.5);
            TentacleMath.solve(j,root,g,S,2);
            worst=Math.max(worst,TentacleMath.worstLinkError(j,S)); rootDrift=Math.max(rootDrift,j[0].distanceTo(root));
        }
        check("T7 400 ticks of a moving target: links stay exact and the root stays put", worst<0.05 && rootDrift<1e-9, "worstLink="+String.format("%.4f",worst)+" rootDrift="+rootDrift);
        // T8: smoothness. the largest single-tick move of any joint while the target circles (a snap would show as a jump)
        TentacleMath.lay(j,root,new Vec3(0,1,0),S); Vec3[] prev=new Vec3[N]; double jump=0;
        for(int t=0;t<300;t++){
            double ang=t*0.09; Vec3 g=new Vec3(Math.cos(ang)*4.5, 1.0+Math.sin(t*0.05)*3.0, Math.sin(ang)*4.5);
            TentacleMath.solve(j,root,g,S,2);
            if(t>20) for(int i=0;i<N;i++) jump=Math.max(jump,j[i].distanceTo(prev[i]));
            for(int i=0;i<N;i++) prev[i]=j[i];
        }
        check("T8 no joint jumps more than 1.6 blocks in one tick while the target circles", jump<1.6, "maxJump="+String.format("%.3f",jump));
        // T9: taper runs wide to thin
        check("T9 taper endpoints and monotone", TentacleMath.taper(0,8,1.2F,0.35F)==1.2F && Math.abs(TentacleMath.taper(7,8,1.2F,0.35F)-0.35F)<1e-6 && TentacleMath.taper(3,8,1.2F,0.35F)>TentacleMath.taper(4,8,1.2F,0.35F), "");
        // T10: floor. a chain forced toward a target below the floor must end with every joint on or above it, links intact, root pinned
        TentacleMath.lay(j,root,new Vec3(1,0,0),S); double floor=0.0;
        boolean below=false; double linkErr=0, rootMove=0;
        for(int t=0;t<200;t++){
            Vec3 g=new Vec3(Math.cos(t*0.1)*4.0, -2.0, Math.sin(t*0.1)*4.0);   // target 2 blocks UNDER the floor
            TentacleMath.solve(j,root,g,S,2); TentacleMath.keepAboveFloor(j,root,floor,S,1.25F,0.4F);
            for(int i=1;i<N;i++) if(j[i].y < floor + TentacleMath.taper(i,N,1.25F,0.4F)*0.5 - 1e-9) below=true;
            linkErr=Math.max(linkErr,TentacleMath.worstLinkError(j,S)); rootMove=Math.max(rootMove,j[0].distanceTo(root));
        }
        check("T10 floor: no joint ever below ground, links intact, root pinned", !below && linkErr<0.02 && rootMove<1e-9, "below="+below+" linkErr="+String.format("%.4f",linkErr));
        System.out.println(fails==0?"ALL PASS":"SOME FAIL "+fails);
    }
}
