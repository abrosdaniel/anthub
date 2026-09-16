package dev.abros.anthub.client;
import java.util.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
/** Independent scroll state for an embedded Markdown pane. */
final class ContentPane {
 private String text="";private int x,y,width,height,wrappedWidth=-1;private double offset;private boolean dragging;private List<FormattedCharSequence> lines=List.of();
 void text(String text){if(!this.text.equals(text)){this.text=text;wrappedWidth=-1;offset=0;}}
 void bounds(int x,int y,int width,int height){this.x=x;this.y=y;this.width=width;this.height=height;}
 private int rows(){return Math.max(1,(height-12)/12);}private double max(){return Math.max(0,lines.size()-rows());}
 private void move(double value){offset=Math.max(0,Math.min(max(),value));}
 boolean contains(double mx,double my){return mx>=x&&mx<x+width&&my>=y&&my<y+height;}
 boolean scroll(double mx,double my,double delta){if(!contains(mx,my))return false;move(offset-delta*3);return true;}
 private int thumb(){return Math.max(12,(height-8)*rows()/Math.max(1,lines.size()));}
 private void seek(double my){move((my-y-4-thumb()/2.0)/Math.max(1,height-8-thumb())*max());}
 boolean click(double mx,double my){if(max()>0&&contains(mx,my)&&mx>=x+width-9){dragging=true;seek(my);return true;}return false;}
 boolean drag(double my){if(!dragging)return false;seek(my);return true;}void release(){dragging=false;}
 Style link(Font font,double mx,double my){if(!contains(mx,my)||mx<x+6||mx>=x+width-12||my<y+6)return null;int row=(int)offset+(int)(my-y-6)/12;return row>=0&&row<lines.size()?font.getSplitter().componentStyleAtWidth(lines.get(row),(int)mx-x-6):null;}
 void render(GuiGraphics g,Font font){if(wrappedWidth!=width){lines=new ArrayList<>();for(var paragraph:Markdown.parse(text)){if(paragraph.getString().isEmpty())lines.add(FormattedCharSequence.EMPTY);else lines.addAll(font.split(paragraph,Math.max(20,width-20)));}wrappedWidth=width;move(offset);}
  g.fill(x,y,x+width,y+height,0xA0181818);g.enableScissor(x+4,y+4,x+width-10,y+height-4);for(int i=(int)offset;i<Math.min(lines.size(),(int)offset+rows());i++)g.drawString(font,lines.get(i),x+6,y+6+(i-(int)offset)*12,0xEEEEEE);g.disableScissor();
  if(max()>0){int track=x+width-7;g.fill(track,y+4,track+4,y+height-4,0xAA444444);int top=y+4+(int)((height-8-thumb())*offset/max());g.fill(track,top,track+4,top+thumb(),0xFFAAAAAA);}
 }
}
