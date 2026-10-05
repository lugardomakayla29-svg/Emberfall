import com.solme.emberfall.relic.*;
public class Sat { public static void main(String[] a){ int first=-1; for(int i=0;i<200;i++){ if(RelicMath.chestPrice(i)==Integer.MAX_VALUE){first=i;break;} } System.out.println("first saturated opening = "+first+"  price(first-1)="+RelicMath.chestPrice(first-1)); for(int n: new int[]{40,50,60,70}) System.out.println(n+" -> "+RelicMath.chestPrice(n)); } }
