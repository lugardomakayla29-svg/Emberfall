public class Probe { public static void main(String[] a){
  int N=8; double S=0.95; Vec3 root=new Vec3(0,1.3,0); Vec3[] j=new Vec3[N]; TentacleMath.lay(j,root,new Vec3(1,0,0),S);
  double worst=0; int wt=-1,wi=-1;
  for(int t=0;t<200;t++){ Vec3 g=new Vec3(Math.cos(t*0.1)*4.0,-2.0,Math.sin(t*0.1)*4.0);
    TentacleMath.solve(j,root,g,S,2); TentacleMath.keepAboveFloor(j,root,0.0,S,1.25F,0.4F);
    for(int i=1;i<N;i++){ double need=TentacleMath.taper(i,N,1.25F,0.4F)*0.5; double d=need-j[i].y; if(d>worst){worst=d;wt=t;wi=i;} } }
  System.out.println("worst shortfall below rest height = "+String.format("%.3f",worst)+" at tick "+wt+" joint "+wi); } }
