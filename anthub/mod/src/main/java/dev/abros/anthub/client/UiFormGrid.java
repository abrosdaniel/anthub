package dev.abros.anthub.client;
import java.util.*;
import dev.abros.anthub.core.NativeLayout;
/** Compound form grid. Field descriptors control spans; one layout controls labels, inputs and scroll rows. */
final class UiFormGrid {
 record Field(CommunityScreen.Field field,boolean full,boolean multiline,String group,boolean invalid){Field(CommunityScreen.Field field,boolean full,boolean multiline,String group){this(field,full,multiline,group,false);}}
 record Cell(CommunityScreen.Field field,int row,int column,int columns,int units){
  NativeLayout.Box bounds(int x,int y,int width,int firstRow){var tracks=new NativeLayout.Track[columns];Arrays.fill(tracks,NativeLayout.Track.flex(1));return NativeLayout.row(new NativeLayout.Box(x,y+(row-firstRow)*22,width,units*22),8,tracks).get(column);}
  boolean visible(int first,int count){return row>=first&&row+units<=first+count;}
 }
 static List<Cell> layout(List<Field> fields,int width){
  var out=new ArrayList<Cell>();int row=0;
  for(int i=0;i<fields.size();){var f=fields.get(i);int count=1,units=(f.multiline()?3:2)+(f.invalid()?1:0);
   if(!f.group().isEmpty()&&width>=220){while(i+count<fields.size()&&count<3&&fields.get(i+count).group().equals(f.group()))count++;}
   else if(!f.full()&&i+1<fields.size()&&!fields.get(i+1).full()&&fields.get(i+1).group().isEmpty()&&width>=340)count=2;
   for(int c=0;c<count;c++)units=Math.max(units,(fields.get(i+c).multiline()?3:2)+(fields.get(i+c).invalid()?1:0));
   for(int c=0;c<count;c++)out.add(new Cell(fields.get(i+c).field(),row,c,count,units));row+=units;i+=count;
  }return List.copyOf(out);
 }
 static int units(List<Cell> cells){return cells.isEmpty()?0:cells.getLast().row()+cells.getLast().units();}
 private UiFormGrid(){}
}
