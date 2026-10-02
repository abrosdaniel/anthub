package dev.abros.anthub.client;
import dev.abros.anthub.core.HudNoticeQueue;
import net.minecraft.client.gui.GuiGraphics;
/** Semantic symbols drawn natively: warning triangle, information, clock and check. */
final class UiNoticeIcon {
 static void draw(GuiGraphics g,HudNoticeQueue.Notice notice,int x,int y,int color){
  if(notice.priority()!=HudNoticeQueue.Priority.ORDINARY){for(int row=0;row<11;row++){int half=row/2;g.fill(x+6-half,y+row,x+7-half,y+row+1,color);g.fill(x+6+half,y+row,x+7+half,y+row+1,color);}g.fill(x+1,y+10,x+12,y+11,color);g.fill(x+6,y+4,x+7,y+7,color);g.fill(x+6,y+8,x+7,y+9,color);return;}
  circle(g,x,y,color);
  if(notice.section().equals("events")){g.fill(x+6,y+3,x+7,y+7,color);g.fill(x+6,y+6,x+9,y+7,color);}
  else if(notice.section().equals("tasks"))UiIcons.draw(g,UiIcons.CHECK,x,y,color);
  else{g.fill(x+6,y+3,x+7,y+4,color);g.fill(x+6,y+5,x+7,y+9,color);}
 }
 private static void circle(GuiGraphics g,int x,int y,int color){for(int row=0;row<12;row++)for(int col=0;col<12;col++){double distance=Math.hypot(col-5.5,row-5.5);if(distance>=4.4&&distance<=5.5)g.fill(x+col,y+row,x+col+1,y+row+1,color);}}
 private UiNoticeIcon(){}
}
