package dev.abros.anthub.core;
import java.util.*;
/** Immutable layout values. Fixed tracks and weighted tracks share one bounded axis. */
public final class NativeLayout {
 public record Box(int x,int y,int width,int height) {
  public Box {if(width<0||height<0)throw new IllegalArgumentException("Negative layout size");}
  public int right(){return x+width;}public int bottom(){return y+height;}
  public boolean contains(double px,double py){return px>=x&&px<right()&&py>=y&&py<bottom();}
  public Box inset(int amount){if(amount<0)throw new IllegalArgumentException("Negative inset");int dx=Math.min(amount,width/2),dy=Math.min(amount,height/2);return new Box(x+dx,y+dy,width-2*dx,height-2*dy);}
 }
 public record Track(int fixed,int weight){public static Track fixed(int size){return new Track(size,0);}public static Track flex(int weight){return new Track(0,weight);}public Track{if(fixed<0||weight<0||fixed>0&&weight>0)throw new IllegalArgumentException("Negative track");}}
 public static List<Box> row(Box area,int gap,Track...tracks){return divide(area,gap,true,tracks);}
 public static List<Box> column(Box area,int gap,Track...tracks){return divide(area,gap,false,tracks);}
 private static List<Box> divide(Box area,int gap,boolean horizontal,Track[] tracks){
  if(tracks.length==0)return List.of();if(gap<0)throw new IllegalArgumentException("Negative gap");
  int length=horizontal?area.width():area.height();gap=Math.min(gap,length/Math.max(1,tracks.length-1));int room=Math.max(0,length-gap*(tracks.length-1));
  int fixed=0,weights=0;for(var t:tracks){fixed+=t.fixed();weights+=t.weight();}
  int fixedBudget=Math.min(fixed,room),flexBudget=room-fixedBudget,cursor=horizontal?area.x():area.y(),fixedUsed=0,flexUsed=0,fixedSum=0,weightSum=0;
  var out=new ArrayList<Box>();for(var t:tracks){int size;if(t.weight()>0){weightSum+=t.weight();int end=weights==0?0:(int)((long)flexBudget*weightSum/weights);size=end-flexUsed;flexUsed=end;}else{fixedSum+=t.fixed();int end=fixed==0?0:(int)((long)fixedBudget*fixedSum/fixed);size=end-fixedUsed;fixedUsed=end;}
   out.add(horizontal?new Box(cursor,area.y(),size,area.height()):new Box(area.x(),cursor,area.width(),size));cursor+=size+gap;
  }return List.copyOf(out);
 }
 private NativeLayout(){}
}
