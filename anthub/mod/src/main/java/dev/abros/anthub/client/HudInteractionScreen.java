package dev.abros.anthub.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
/** Cursor mode and position editor share the real renderer, not an imitation preview. */
final class HudInteractionScreen extends Screen {
 private final boolean editing;private final Screen parent;private boolean dragging;private double grabX,grabY;
 HudInteractionScreen(boolean editing,Screen parent){super(Component.literal(editing?"Расположение HUD":"Взаимодействие с HUD"));this.editing=editing;this.parent=parent;}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){if(minecraft.level==null)g.fill(0,0,width,height,0xFF090E14);}
 @Override public void render(GuiGraphics g,int x,int y,float delta){renderBackground(g,x,y,delta);HudRenderer.render(g,true,editing);String help=editing?"Перетащите виджет · R — вернуть расположение · Esc — готово":"Нажмите на блок или плашку · Esc — закрыть";int hintWidth=Math.min(width-16,font.width(help)+16);g.fill((width-hintWidth)/2,height-25,(width+hintWidth)/2,height-7,0xB0090E14);g.drawCenteredString(font,Component.literal(UiKit.fit(font,help,hintWidth-12)),width/2,height-20,0xFFE0E9EE);if(editing)UiHudSurface.outline(g,HudRenderer.lastX-1,HudRenderer.lastY-1,HudRenderer.lastW+2,HudRenderer.lastH+2,HudRenderer.lastRadius+1,UiKit.accent());}
 @Override public boolean mouseClicked(double x,double y,int button){if(button!=0)return false;if(editing&&x>=HudRenderer.lastX&&x<=HudRenderer.lastX+HudRenderer.lastW&&y>=HudRenderer.lastY&&y<=HudRenderer.lastY+HudRenderer.lastH){dragging=true;grabX=x-HudRenderer.lastX;grabY=y-HudRenderer.lastY;return true;}return !editing&&HudRenderer.click(x,y,true);}
 @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(!dragging)return false;var s=HudSettings.INSTANCE;s.anchorX=0;s.anchorY=0;s.offsetX=(int)Math.max(4,Math.min(width-HudRenderer.lastW-4,x-grabX));s.offsetY=(int)Math.max(4,Math.min(height-HudRenderer.lastH-4,y-grabY));return true;}
 @Override public boolean mouseReleased(double x,double y,int button){if(!dragging)return false;dragging=false;var s=HudSettings.INSTANCE;int px=s.offsetX,py=s.offsetY,w=HudRenderer.lastW,h=HudRenderer.lastH;s.anchorX=px+w/2<width/3?0:px+w/2>width*2/3?2:1;s.anchorY=py+h/2<height/3?0:py+h/2>height*2/3?2:1;s.offsetX=s.anchorX==2?width-px-w:s.anchorX==1?px-(width-w)/2:px;s.offsetY=s.anchorY==2?height-py-h:s.anchorY==1?py-(height-h)/2:py;if(Math.abs(s.offsetX)<12)s.offsetX=s.anchorX==1?0:8;if(Math.abs(s.offsetY)<12)s.offsetY=s.anchorY==1?0:8;s.save();return true;}
 @Override public boolean keyPressed(int key,int scan,int mods){if(editing&&key==GLFW.GLFW_KEY_R){var s=HudSettings.INSTANCE;s.anchorX=2;s.anchorY=1;s.offsetX=8;s.offsetY=0;s.save();return true;}return super.keyPressed(key,scan,mods);}
 @Override public void onClose(){HudSettings.INSTANCE.save();minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
