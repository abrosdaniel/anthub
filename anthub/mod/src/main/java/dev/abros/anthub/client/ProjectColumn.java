package dev.abros.anthub.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.*;
final class ProjectColumn extends AbstractWidget {
 private int keyboardIndex;
 private static final int ROW=132;double offset;private boolean dragging;private final List<String> servers;private final Map<String,ServerData> status;private final Consumer<String> choose,join,refresh,settings,remove;private final Supplier<String> selected;private final List<Button> buttons=new ArrayList<>();
 ProjectColumn(int x,int y,int w,int h,List<String> servers,Map<String,ServerData> status,Supplier<String> selected,Consumer<String> choose,Consumer<String> join,Consumer<String> refresh,Consumer<String> settings,Consumer<String> remove){super(x,y,w,h,Client.tr("servers"));this.servers=servers;this.status=status;this.selected=selected;this.choose=choose;this.join=join;this.refresh=refresh;this.settings=settings;this.remove=remove;}
 @Override protected void renderWidget(GuiGraphics g,int mx,int my,float delta){buttons.clear();setTooltip(null);offset=Math.max(0,Math.min(offset,Math.max(0,servers.size()*ROW-height)));var font=Minecraft.getInstance().font;g.enableScissor(getX(),getY(),getX()+width,getY()+height);int y=getY()-(int)offset;
  for(var server:servers){if(y+ROW>=getY()&&y<getY()+height){boolean active=server.equals(selected.get());g.fill(getX(),y,getX()+width-6,y+ROW-4,active?0xD03B382A:0xB0202020);var data=status.get(server);
   if(isMouseOver(mx,my)&&my>=y&&my<y+17&&font.width(Client.hub.projectLabel(server))>width-26)setTooltip(Tooltip.create(Component.literal(Client.hub.projectLabel(server))));
   g.drawString(font,font.plainSubstrByWidth(Client.hub.projectLabel(server),width-26),getX()+5,y+5,active?0xFFE2BE75:0xFFFFFFFF);
   if(data!=null){Branding.serverIcon(data,g,getX()+5,y+19,24);int textX=getX()+34;int line=0;for(var part:font.split(data.motd==null?Component.literal(server):data.motd,Math.max(20,width-44))){if(line++>=2)break;g.drawString(font,part,textX,y+19+(line-1)*11,0xBBBBBB);}
    String players=data.status==null?"…":data.status.getString();String ping=data.ping<0?"…":data.ping+" ms";g.drawString(font,font.plainSubstrByWidth(players+" · "+ping,width-14),getX()+5,y+46,0xBBBBBB);
    g.drawString(font,font.plainSubstrByWidth(data.version==null?server:data.version.getString(),width-26),getX()+5,y+59,0x999999);
   }
   int half=(width-18)/2;
   String[] labels={"connect","refresh","settings","remove"};java.util.List<Consumer<String>> actions=java.util.List.of(join,refresh,settings,remove);
   for(int i=0;i<4;i++){var action=actions.get(i);var button=Button.builder(Client.tr(labels[i]),b->action.accept(server)).bounds(getX()+4+(i%2)*(half+4),y+76+(i/2)*24,half,20).build();button.active=this.active;button.setFocused(isFocused() && keyboardIndex == servers.indexOf(server)*4+i);buttons.add(button);button.render(g,mx,my,delta);}
   if(Client.hub.active()!=null&&Client.hub.active().repository().equals(server))g.drawString(font,"●",getX()+width-15,y+5,0xFF77CC77);

  }y+=ROW;}g.disableScissor();if(servers.size()*ROW>height){int thumb=Math.max(12,height*height/(servers.size()*ROW));int start=getY()+(int)((height-thumb)*offset/(servers.size()*ROW-height));g.fill(getX()+width-4,start,getX()+width,start+thumb,0xFF999999);}}
 @Override public boolean mouseClicked(double x,double y,int button){if(!active||!isMouseOver(x,y))return false;if(button==0&&x>=getX()+width-6&&servers.size()*ROW>height){dragging=true;return true;}for(var b:buttons)if(b.mouseClicked(x,y,button))return true;int index=(int)(y-getY()+offset)/ROW;if(index>=0&&index<servers.size())choose.accept(servers.get(index));return true;}
 @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(!isMouseOver(x,y))return false;offset=Math.max(0,Math.min(Math.max(0,servers.size()*ROW-height),offset-dy*24));return true;}
 @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(!dragging)return false;int content=servers.size()*ROW;int thumb=Math.max(12,height*height/content);offset=Math.max(0,Math.min(content-height,offset+dy*(content-height)/Math.max(1,height-thumb)));return true;}
 @Override public boolean mouseReleased(double x,double y,int button){dragging=false;return super.mouseReleased(x,y,button);}
 @Override public void setFocused(boolean focused){
  boolean entering=focused&&!isFocused();super.setFocused(focused);
  if(entering){keyboardIndex=net.minecraft.client.gui.screens.Screen.hasShiftDown()?Math.max(0,servers.size()*4-1):Math.max(0,servers.indexOf(selected.get()))*4;revealKeyboard();}
 }
 private void revealKeyboard(){
  keyboardIndex=Math.max(0,Math.min(keyboardIndex,Math.max(0,servers.size()*4-1)));
  int top=(keyboardIndex/4)*ROW+76+(keyboardIndex%4/2)*24;
  if(top<offset)offset=top;if(top+20>offset+height)offset=top+20-height;
  offset=Math.max(0,Math.min(offset,Math.max(0,servers.size()*ROW-height)));
 }
 @Override public boolean keyPressed(int key,int scan,int modifiers){
  if(!isFocused()||!active||servers.isEmpty())return false;
  revealKeyboard();int next=keyboardIndex;
  switch(key){
   case org.lwjgl.glfw.GLFW.GLFW_KEY_TAB -> {next+=(net.minecraft.client.gui.screens.Screen.hasShiftDown()?-1:1);if(next<0||next>=servers.size()*4)return false;}
   case org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT -> next++;
   case org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT -> next--;
   case org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN -> next+=2;
   case org.lwjgl.glfw.GLFW.GLFW_KEY_UP -> next-=2;
   case org.lwjgl.glfw.GLFW.GLFW_KEY_HOME -> next=0;
   case org.lwjgl.glfw.GLFW.GLFW_KEY_END -> next=servers.size()*4-1;
   case org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER, org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE -> {
    playDownSound(Minecraft.getInstance().getSoundManager());java.util.List.of(join,refresh,settings,remove).get(keyboardIndex%4).accept(servers.get(keyboardIndex/4));return true;
   }
   default -> {return false;}
  }
  keyboardIndex=Math.max(0,Math.min(next,servers.size()*4-1));revealKeyboard();return true;
 }
 @Override protected void updateWidgetNarration(NarrationElementOutput output){
  if(servers.isEmpty()){defaultButtonNarrationText(output);return;}
  int index=Math.max(0,Math.min(keyboardIndex,servers.size()*4-1));String[] labels={"connect","refresh","settings","remove"};
  output.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,Component.literal(Client.hub.projectLabel(servers.get(index/4))+": ").append(Client.tr(labels[index%4])));
 }
}
