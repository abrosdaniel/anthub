package dev.abros.anthub.client;
import net.minecraft.client.gui.GuiGraphics;
/** Rounded native HUD geometry; each translucent pixel is painted only once. */
final class UiHudSurface {
 static final int RADIUS=5;
 static void panel(GuiGraphics g,int w,int h,float opacity){fill(g,1,2,w,h,RADIUS,0x25000000);fill(g,0,0,w,h,RADIUS,(UiKit.surface()&0xFFFFFF)|((int)(255*opacity)<<24));outline(g,0,0,w,h,RADIUS,(UiKit.muted()&0xFFFFFF)|0x40000000);g.fill(10,0,w-10,1,(UiKit.accent()&0xFFFFFF)|0x60000000);}
 static void fill(GuiGraphics g,int x,int y,int w,int h,int radius,int color){int r=Math.min(radius,Math.min(w,h)/2);for(int row=0;row<h;row++){int inset=inset(row,h,r);g.fill(x+inset,y+row,x+w-inset,y+row+1,color);}}
 static void outline(GuiGraphics g,int x,int y,int w,int h,int radius,int color){int r=Math.min(radius,Math.min(w,h)/2);for(int row=0;row<h;row++){int outer=inset(row,h,r);int inner=row==0||row==h-1?w/2:1+inset(row-1,h-2,Math.max(0,r-1));if(row==0||row==h-1)g.fill(x+outer,y+row,x+w-outer,y+row+1,color);else{g.fill(x+outer,y+row,x+inner,y+row+1,color);g.fill(x+w-inner,y+row,x+w-outer,y+row+1,color);}}}
 private static int inset(int row,int h,int r){if(r==0)return 0;double d=row<r?r-row-.5:row>=h-r?row-(h-r)+.5:0;return d==0?0:Math.max(0,(int)Math.ceil(r-Math.sqrt(r*r-d*d)-.5));}
 private UiHudSurface(){}
}
