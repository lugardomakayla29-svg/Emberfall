// TEST-ONLY stand-in for net.minecraft.world.phys.Vec3 so TentacleMath can run headless. Never compiled into the mod: only tools/testbot/relic_math checks use it.
package net.minecraft.world.phys;
public class Vec3 { public final double x,y,z; public Vec3(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
 public Vec3 add(Vec3 o){return new Vec3(x+o.x,y+o.y,z+o.z);} public Vec3 add(double a,double b,double c){return new Vec3(x+a,y+b,z+c);}
 public Vec3 subtract(Vec3 o){return new Vec3(x-o.x,y-o.y,z-o.z);} public Vec3 scale(double s){return new Vec3(x*s,y*s,z*s);}
 public double length(){return Math.sqrt(x*x+y*y+z*z);} public double lengthSqr(){return x*x+y*y+z*z;}
 public Vec3 normalize(){double l=length();return l<1e-9?new Vec3(0,0,0):new Vec3(x/l,y/l,z/l);}
 public double dot(Vec3 o){return x*o.x+y*o.y+z*o.z;} public double distanceTo(Vec3 o){return subtract(o).length();} public double horizontalDistance(){return Math.sqrt(x*x+z*z);}
 public Vec3 lerp(Vec3 o,double t){return new Vec3(x+(o.x-x)*t,y+(o.y-y)*t,z+(o.z-z)*t);} public Vec3 cross(Vec3 o){return new Vec3(y*o.z-z*o.y,z*o.x-x*o.z,x*o.y-y*o.x);} }
