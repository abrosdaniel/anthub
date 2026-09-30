package dev.abros.anthub.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
/** Nested foreground layers share a render pass, but never share a depth plane. */
final class ModalLayer {
 private static final ThreadLocal<Pass> PASS=new ThreadLocal<>();
 private static final class Pass{int level;}
 static void prepare(Screen parent,Screen modal){if(parent==null)return;if(parent.width==0||parent.height==0)parent.init(Minecraft.getInstance(),modal.width,modal.height);else if(parent.width!=modal.width||parent.height!=modal.height)parent.resize(Minecraft.getInstance(),modal.width,modal.height);}
 static void render(Screen parent,Screen modal,GuiGraphics graphics,float delta,Runnable contents){
  boolean root=PASS.get()==null;if(root)PASS.set(new Pass());
  try{
   if(parent!=null){prepare(parent,modal);parent.render(graphics,-10000,-10000,delta);}
   // Item rendering adds up to 200 depth units; tooltips also raise their plane.
   // Keep the complete foreground window above all geometry from its parent.
   int layer=++PASS.get().level*600;
   graphics.flush();graphics.pose().pushPose();
   try{graphics.pose().translate(0,0,layer);contents.run();graphics.flush();}finally{graphics.pose().popPose();}
  }finally{if(root)PASS.remove();}
 }
}
