public class JointStrike { public static void main(String[] a){
  int N=8; double S=0.95; Vec3 root=new Vec3(1.5,1.3,0);
  for(int windup : new int[]{18,24,36,50}){
    Vec3[] j=new Vec3[N]; TentacleMath.lay(j,root,new Vec3(1,0,0),S);
    Vec3 idle=new Vec3(5,0.6,0), rear=new Vec3(1.5,6.3,0), strike=new Vec3(6,0,3);
    for(int t=0;t<30;t++) TentacleMath.solve(j,root,idle,S,2);          // settle at idle
    Vec3[] prev=j.clone(); double worstJoint=0, tipGap=0; int tickAtWorst=-1;
    for(int t=1;t<=windup+10;t++){
      Vec3 tg=TentaclePose.blend(idle,rear,strike,TentaclePose.progress(t,windup),t>=windup,0.25);
      TentacleMath.solve(j,root,tg,S,2); TentacleMath.keepAboveFloor(j,root,0.0,S,1.25F,0.4F);
      for(int i=0;i<N;i++){ double d=j[i].distanceTo(prev[i]); if(d>worstJoint){worstJoint=d;tickAtWorst=t;} }
      if(t==windup) tipGap=j[N-1].distanceTo(strike);
      prev=j.clone(); }
    System.out.println("windup "+windup+": worst joint move per tick="+String.format("%.2f",worstJoint)+" (tick "+tickAtWorst+"), tip is "+String.format("%.2f",tipGap)+" blocks from the strike on the resolve tick"); } } }
