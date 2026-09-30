package dev.abros.anthub.client;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
/** Original AntHub glyphs on a shared 12-pixel grid; no external textures. */
final class UiIcons {
 private UiIcons(){}
 static final String SEARCH="search",REFRESH="refresh",SAVE="save",CHECK="check",DELETE="delete",PLUS="plus",DOWN="down",UP="up",RIGHT="right",CLEAR="clear";
 private static final Map<String,int[][]> LINES=Map.of(
  PLUS,new int[][]{{5,2,5,9},{2,5,9,5}},
  CHECK,new int[][]{{2,6,4,8},{4,8,10,2}},
  CLEAR,new int[][]{{2,2,9,9},{9,2,2,9}},
  DOWN,new int[][]{{2,4,5,7},{5,7,8,4}},
  UP,new int[][]{{2,7,5,4},{5,4,8,7}},
  RIGHT,new int[][]{{4,2,8,6},{8,6,4,10}},
  DELETE,new int[][]{{2,3,9,3},{4,1,7,1},{3,4,3,10},{8,4,8,10},{3,10,8,10},{5,5,5,8},{6,5,6,8}},
  SEARCH,new int[][]{{3,1,6,1},{1,3,1,6},{3,8,6,8},{8,3,8,6},{2,2,2,2},{7,2,7,2},{2,7,2,7},{7,7,10,10}},
  REFRESH,new int[][]{{3,1,7,1},{7,1,10,4},{10,4,10,7},{10,7,7,10},{7,10,3,10},{3,10,1,8},{1,8,1,5},{8,4,10,4},{10,2,10,4}},
  SAVE,new int[][]{{1,1,9,1},{9,1,10,2},{10,2,10,10},{10,10,1,10},{1,10,1,1},{3,1,3,4},{3,4,7,4},{7,4,7,1},{3,7,8,7},{3,7,3,10},{8,7,8,10}});
 static void draw(GuiGraphics g,String icon,int x,int y,int color){if(icon.equals(PLUS)){g.fill(x+5,y+2,x+7,y+10,color);g.fill(x+2,y+5,x+10,y+7,color);return;}if(icon.equals(REFRESH)){line(g,x+3,y+2,x+7,y+2,color);line(g,x+7,y+2,x+9,y+4,color);g.fill(x+7,y+4,x+11,y+6,color);g.fill(x+9,y+2,x+11,y+6,color);line(g,x+9,y+7,x+7,y+9,color);line(g,x+7,y+9,x+3,y+9,color);line(g,x+3,y+9,x+1,y+7,color);line(g,x+1,y+7,x+1,y+4,color);line(g,x+1,y+4,x+3,y+2,color);return;}var lines=LINES.get(icon);if(lines==null)return;for(int[] p:lines)line(g,x+p[0],y+p[1],x+p[2],y+p[3],color);}
 private static void line(GuiGraphics g,int x,int y,int endX,int endY,int color){int dx=Math.abs(endX-x),dy=-Math.abs(endY-y),sx=x<endX?1:-1,sy=y<endY?1:-1,error=dx+dy;while(true){g.fill(x,y,x+1,y+1,color);if(x==endX&&y==endY)return;int twice=2*error;if(twice>=dy){error+=dy;x+=sx;}if(twice<=dx){error+=dx;y+=sy;}}}
}
