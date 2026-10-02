package dev.abros.anthub.client;

import dev.abros.anthub.core.NativeLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/** Compound dialog: parent layer, scrim, surface, border, body and footer slots. */
record UiDialog(NativeLayout.Box frame, NativeLayout.Box header, NativeLayout.Box body, NativeLayout.Box footer) {
 static UiDialog fit(int width,int height,int contentWidth,int desiredHeight){
  int top=top(height,desiredHeight),bottom=height-top;
  int w=Math.max(0,Math.min(contentWidth,width-40));
  int left=(width-w)/2;
  return new UiDialog(new NativeLayout.Box(left-10,top,w+20,bottom-top),
   new NativeLayout.Box(left,top+8,w,24),
   new NativeLayout.Box(left,top+38,w,Math.max(0,bottom-top-98)),
   new NativeLayout.Box(left,bottom-52,w,44));
 }
 static int top(int height,int desired){return Math.max(8,(height-Math.min(desired,height-16))/2);}
 static int bottom(int height,int desired){return height-top(height,desired);}
 static void render(Screen parent,Screen modal,GuiGraphics g,float delta,Runnable contents){ModalLayer.render(parent,modal,g,delta,contents);}
 static void draw(GuiGraphics g,int width,int contentWidth,int top,int bottom){int left=(width-contentWidth)/2-10;surface(g,left,top,width-left*2,bottom-top);}
 static void surface(GuiGraphics g,int left,int top,int width,int height){
  g.fill(0,0,net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth(),net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight(),0xAA090E14);
  UiKit.material(g,left,top,width,height);
  g.renderOutline(left,top,width,height,UiPalette.outline());
 }
}
