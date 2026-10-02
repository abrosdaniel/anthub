package dev.abros.anthub.client;

import com.google.gson.*;
import dev.abros.anthub.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import java.util.*;

/** Shared compound HUD surfaces: layout, clipped text, marks, progress and hit regions. */
final class HudRenderer {
 record Block(String title,String text,String section,String target,String group,double progress){}
 record Hit(double x,double y,double w,double h,Runnable action){boolean contains(double px,double py){return px>=x&&py>=y&&px<x+w&&py<y+h;}}
 private static final List<Hit> hits=new ArrayList<>();static int lastX,lastY,lastW,lastH,lastRadius;private static long drawn;private static int widgetHits;
 static List<Block> blocks(boolean preview){var out=new ArrayList<Block>();var data=AntHubHud.snapshot;var state=ServerMenuClient.state;var settings=HudSettings.INSTANCE;
  for(String key:settings.order){if(!settings.enabled&&!preview||settings.hidden.contains(key))continue;switch(key){
   case "profile"->{if(HudProfile.height(preview)>0)out.add(new Block("","","profile","","",-1));}
   case "server"->{String title=Minecraft.getInstance().getCurrentServer()==null?"AntHub":Minecraft.getInstance().getCurrentServer().name;out.add(new Block(title,state.has("online")?state.get("online")+" / "+state.get("maximum"):"Подключение…","server","","",-1));}
   case "task"->{if(data.has("tasks")&&!data.getAsJsonArray("tasks").isEmpty()){var task=data.getAsJsonArray("tasks").get(0).getAsJsonObject();long due=task.has("dueAt")?task.get("dueAt").getAsLong():0;int total=task.get("total").getAsInt(),done=task.get("done").getAsInt();String text=(total>0?done+" / "+total+" этапов":"Без этапов")+(due>0?" · "+AntHubHud.relative(due):"");out.add(new Block(Json.str(task,"title"),text,"tasks",Json.str(task,"id"),Json.opt(task,"group",""),total>0?done/(double)total:-1));}else if(preview)out.add(new Block("Задача появится здесь","Срок и прогресс этапов","tasks","","",.4));}
   case "event"->{if(data.has("events")&&!data.getAsJsonArray("events").isEmpty()){var event=data.getAsJsonArray("events").get(0).getAsJsonObject();out.add(new Block(Json.str(event,"title"),AntHubHud.relative(event.get("startsAt").getAsLong()),"events",Json.str(event,"id"),"",-1));}else if(preview)out.add(new Block("Ближайшее событие","Через 30 мин","events","","",-1));}
   case "groups"->{if(data.has("groups")&&!data.getAsJsonArray("groups").isEmpty()){var labels=new ArrayList<String>();for(var e:data.getAsJsonArray("groups"))labels.add(Json.str(e.getAsJsonObject(),"title"));out.add(new Block("Объединения",String.join(" · ",labels),"groups","","",-1));}else if(preview)out.add(new Block("Объединения","Названия ваших объединений","groups","","",-1));}
   case "unread"->{int count=state.has("unread")?state.get("unread").getAsInt():0;if(count>0||preview)out.add(new Block("Уведомления",count+" непрочитанных","notifications","","",-1));}
  }}
  long restart=state.has("restartAt")?state.get("restartAt").getAsLong():0;if(ServerMenuClient.enabled(2)&&restart>System.currentTimeMillis())out.add(0,new Block("Перезапуск сервера",AntHubHud.relative(restart),"server","","",-1));
  if((settings.hidden.contains("task")||!settings.enabled)&&data.has("tasks")&&!data.getAsJsonArray("tasks").isEmpty()){var task=data.getAsJsonArray("tasks").get(0).getAsJsonObject();long due=task.has("dueAt")?task.get("dueAt").getAsLong():0;if(due>0&&due<System.currentTimeMillis())out.add(0,new Block("Просрочено: "+Json.str(task,"title"),AntHubHud.relative(due),"tasks",Json.str(task,"id"),Json.opt(task,"group",""),-1));}
  if(preview&&out.isEmpty())out.add(new Block("Блоки выключены","Включите их в настройках HUD","notifications","","",-1));return out;
 }
 static void render(GuiGraphics g,boolean widget,boolean preview){hits.clear();widgetHits=0;drawn=net.minecraft.Util.getMillis();var mc=Minecraft.getInstance();var settings=HudSettings.INSTANCE;float density=(float)Math.min(1,1.5/mc.getWindow().getGuiScale());
  boolean showing=ServerMenuClient.enabled(0)&&!mc.options.hideGui;float toastScale=density;int capacity=Math.max(1,(int)((g.guiHeight()-16)/(67*toastScale)));var notices=AntHubHud.QUEUE.advance(net.minecraft.Util.getMillis(),Math.min(settings.toastCount,capacity),showing);int toastWidth=Math.min(260,(int)((g.guiWidth()-16)/toastScale));
  if(widget){var blocks=blocks(preview);if(!blocks.isEmpty()){int h=12;for(var block:blocks)h+=blockHeight(block,preview);float scale=Math.min(settings.scale*density,Math.max(.3f,(g.guiHeight()-16f)/h));int w=Math.min(214,Math.max(80,(int)((g.guiWidth()-16)/scale)));int pw=(int)Math.ceil(w*scale),ph=(int)Math.ceil(h*scale);int x=settings.anchorX==0?settings.offsetX:settings.anchorX==2?g.guiWidth()-pw-settings.offsetX:(g.guiWidth()-pw)/2+settings.offsetX;int y=settings.anchorY==0?settings.offsetY:settings.anchorY==2?g.guiHeight()-ph-settings.offsetY:(g.guiHeight()-ph)/2+settings.offsetY;x=Math.max(4,Math.min(g.guiWidth()-pw-4,x));y=Math.max(4,Math.min(g.guiHeight()-ph-4,y));if(!notices.isEmpty()){int toastLeft=(int)(g.guiWidth()-toastWidth*toastScale-8),toastBottom=(int)Math.ceil(8+notices.size()*67*toastScale);if(x+pw>toastLeft&&y<toastBottom&&y+ph>8){if(toastBottom+ph+8<=g.guiHeight())y=toastBottom+4;else if(toastLeft-pw-8>=4)x=toastLeft-pw-8;}}lastX=x;lastY=y;lastW=pw;lastH=ph;lastRadius=Math.max(2,Math.round(UiHudSurface.RADIUS*scale));
   g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(scale,scale,1);
   UiHudSurface.panel(g,w,h,settings.opacity);
   int row=6;for(var block:blocks){int rh=blockHeight(block,preview);
    if(block.section().equals("profile"))HudProfile.draw(g,0,row,w,preview);
    else if(block.section().equals("server")&&!block.title().equals("Перезапуск сервера")){String online=UiKit.fit(mc.font,block.text(),Math.max(30,w/2-12));int ow=mc.font.width(online);g.fill(9,row+7,12,row+10,UiKit.accent());text(g,block.title(),17,row+5,Math.max(0,w-ow-35),UiKit.text());text(g,online,w-ow-10,row+5,ow,UiKit.muted());}
    else{String icon=block.section().equals("tasks")?UiIcons.CHECK:block.section().equals("events")?UiIcons.REFRESH:UiIcons.RIGHT;UiIcons.draw(g,icon,9,row+6,UiKit.accent());text(g,block.title(),26,row+4,w-38,UiKit.text());text(g,block.text(),26,row+17,w-38,UiKit.muted());if(block.progress()>=0){g.fill(26,row+29,w-10,row+31,UiPalette.scrollTrack());g.fill(26,row+29,26+(int)((w-36)*block.progress()),row+31,UiKit.accent());}}
    if(row+rh<h-6)g.fill(10,row+rh-1,w-10,row+rh,alpha(UiKit.muted(),.15));
    int index=row;int px=x,py=y;float factor=scale;hits.add(new Hit(px,py+index*factor,pw,rh*factor,()->{if(!block.section().equals("profile"))AntHubHud.open(block.section(),block.target(),block.group());}));row+=rh;}g.pose().popPose();
  }}
  widgetHits=hits.size();
  if(!showing)return;int width=toastWidth;float y=8;for(var entry:notices){if(y+62*toastScale>g.guiHeight()-8)break;long now=net.minecraft.Util.getMillis();double fade=AccessibilityScreen.animations()?Math.min(1,Math.min(entry.age(now)/140d,entry.remaining(now)*entry.duration()/220d)):1;float x=g.guiWidth()-width*toastScale-8+(float)((1-fade)*12);int h=62;var n=entry.notice();g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(toastScale,toastScale,1);surface(g,0,0,width,h,.94f*(float)fade);int accent=n.priority()==HudNoticeQueue.Priority.URGENT?UiPalette.color(0xFFDB7777):UiKit.accent();g.fill(0,4,2,h-4,alpha(accent,fade));UiNoticeIcon.draw(g,n,7,7,alpha(accent,fade));text(g,n.title()+(entry.count()>1?" ×"+entry.count():""),22,8,width-48,alpha(UiKit.text(),fade));var wrapped=mc.font.getSplitter().splitLines(n.body(),width-24,net.minecraft.network.chat.Style.EMPTY);for(int line=0;line<Math.min(2,wrapped.size());line++)text(g,wrapped.get(line).getString(),9,24+line*11,width-24,alpha(UiKit.muted(),fade));UiIcons.draw(g,UiIcons.CLEAR,width-18,7,alpha(UiKit.muted(),fade));g.fill(6,h-4,6+(int)((width-12)*entry.remaining(now)),h-3,alpha(UiKit.accent(),fade));g.pose().popPose();
   hits.add(new Hit(x+(width-24)*toastScale,y,24*toastScale,22*toastScale,()->AntHubHud.acknowledge(AntHubHud.QUEUE.dismiss(entry))));hits.add(new Hit(x,y,width*toastScale,h*toastScale,()->{AntHubHud.acknowledge(AntHubHud.QUEUE.dismiss(entry));AntHubHud.open(n.section(),n.target(),n.group());}));y+=(h+5)*toastScale;
  }
 }
 private static int blockHeight(Block b,boolean preview){return b.section().equals("profile")?HudProfile.height(preview):b.section().equals("server")&&!b.title().equals("Перезапуск сервера")?24:38;}
 private static int alpha(int color,double fade){return color&0xFFFFFF|Math.max(5,(int)(255*fade))<<24;}
 private static void surface(GuiGraphics g,int x,int y,int w,int h,float opacity){int color=UiKit.surface()&0xFFFFFF|((int)(255*opacity)<<24);UiKit.surface(g,x,y,w,h,color);g.fill(x+6,y,x+w-6,y+1,UiPalette.color(0x40526674));UiKit.detail(g,x,y,w,h);}
 private static void text(GuiGraphics g,String value,int x,int y,int w,int color){g.drawString(Minecraft.getInstance().font,UiKit.fit(Minecraft.getInstance().font,value,w),x,y,color,false);}
 static boolean click(double x,double y,boolean widget){if(Minecraft.getInstance().options.hideGui||net.minecraft.Util.getMillis()-drawn>250)return false;for(int n=widgetHits;n<hits.size();n++){var hit=hits.get(n);if(hit.contains(x,y)){hit.action().run();return true;}}if(widget)for(int n=0;n<widgetHits;n++){var hit=hits.get(n);if(hit.contains(x,y)){hit.action().run();return true;}}return false;}
}
