package dev.abros.anthub.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import dev.abros.anthub.core.NativeLayout;
/** One adaptive application frame; pages consume named slots rather than screen coordinates. */
record UiWorkspace(NativeLayout.Box frame,NativeLayout.Box header,NativeLayout.Box sidebar,NativeLayout.Box page,NativeLayout.Box footer) {
 static final int OUTER_X=24,OUTER_Y=8;
 static UiWorkspace fit(int width,int height){
  int left=Math.min(146,Math.max(96,width/4))+12+OUTER_X;
  var frame=new NativeLayout.Box(OUTER_X,OUTER_Y,Math.max(0,width-2*OUTER_X),Math.max(0,height-2*OUTER_Y));
  var header=new NativeLayout.Box(OUTER_X+8,12,Math.max(0,width-2*(OUTER_X+8)),20);
  var sidebar=new NativeLayout.Box(OUTER_X+4,42,Math.max(0,left-OUTER_X-20),Math.max(0,height-112));
  var page=new NativeLayout.Box(left,42,Math.max(0,Math.min(880,width-left-OUTER_X-20)),Math.max(0,height-84));
  var footer=new NativeLayout.Box(left,Math.max(42,height-32),Math.max(0,width-left-OUTER_X-20),20);
  return new UiWorkspace(frame,header,sidebar,page,footer);
 }
 void draw(GuiGraphics g){
  UiTheme.panel(g,frame.x(),frame.y(),frame.width(),frame.height(),UiPalette.color(0xBD111A22));
  g.fill(header.x(),header.bottom()+3,header.right(),header.bottom()+4,UiTheme.mix(UiKit.surface(),UiKit.muted(),0.15f));
  g.fill(sidebar.right()+5,sidebar.y(),sidebar.right()+6,sidebar.bottom(),UiTheme.mix(UiKit.surface(),UiKit.muted(),0.12f));
 }
 void heading(GuiGraphics g,Font font,Component title){UiTypography.draw(g,font,title.getString(),header.x()+4,header.y()+5,header.width()-(header.width()<396?88:150),UiTypography.Role.PAGE);}
}
