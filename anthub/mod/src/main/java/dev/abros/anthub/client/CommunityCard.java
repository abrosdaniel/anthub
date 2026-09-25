package dev.abros.anthub.client;
import com.google.gson.JsonObject;
import dev.abros.anthub.core.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import java.time.*;
import java.time.format.DateTimeFormatter;
/** One focusable card, with section-specific hierarchy rather than a wall of buttons. */
final class CommunityCard extends Button {
 boolean selected;private final JsonObject item;private final String section;
 CommunityCard(int x,int y,int w,int h,JsonObject item,Runnable action){super(x,y,w,h,Component.literal(Json.opt(item,"title","")+". "+Json.opt(item,"preview","")),b->action.run(),DEFAULT_NARRATION);this.item=item;section=Json.opt(item,"section","home");}
 private String value(String key){return Json.opt(item,key,"");}
 private String count(String key){return dev.abros.anthub.core.DisplayCounts.text(item,key,"0");}
 private String eventTime(){if(!item.has("startsAt"))return "Событие";long minutes=Math.max(0,(item.get("startsAt").getAsLong()-System.currentTimeMillis())/60000);return (item.has("rescheduledAt")?"Перенесено · ":"")+(minutes<60?"Через "+minutes+" мин":minutes<1440?"Через "+minutes/60+" ч":"Через "+minutes/1440+" дн");}
 private int color(){return switch(section){case "moderation"->0xFFEF7777;case "groups"->0xFF79CBA6;case "events"->0xFF82B6F2;case "ideas","task"->0xFFF0A77C;case "polls"->0xFFB49AE8;default->0xFFE2BE75;};}
 private void line(GuiGraphics g,String text,int x,int y,int w,int color){g.drawString(Minecraft.getInstance().font,Minecraft.getInstance().font.plainSubstrByWidth(text,Math.max(4,w)),x,y,color);}
 @Override protected void renderWidget(GuiGraphics g,int mx,int my,float delta){
  int x=getX(),y=getY(),w=getWidth(),h=getHeight(),left=x+10;boolean focus=selected||isHoveredOrFocused();
  g.fill(x,y,x+w,y+h,AccessibilityScreen.background(focus?0xEF303D49:0xD01B252E));g.fill(x,y,x+3,y+h,color());if(focus)g.renderOutline(x,y,w,h,color());
  g.enableScissor(x+4,y+3,x+w-4,y+h-3);
  if(section.equals("events")){g.fill(x+7,y+7,x+63,y+h-7,0xAA102338);if(item.has("startsAt")){var time=Instant.ofEpochMilli(item.get("startsAt").getAsLong()).atZone(ZoneId.systemDefault());line(g,time.format(DateTimeFormatter.ofPattern("dd.MM")),x+12,y+13,48,0xFFFFFF);line(g,time.format(DateTimeFormatter.ofPattern("HH:mm")),x+12,y+29,48,color());}left=x+72;}
  else if(section.equals("ideas")){g.fill(x+7,y+7,x+51,y+h-7,0xAA392B22);line(g,count("supportersCount"),x+15,y+13,30,color());line(g,"голосов",x+9,y+29,40,0xBBBBBB);left=x+60;}
  String eyebrow=switch(section){case "groups"->value("type")+" · "+(item.has("recruiting")&&item.get("recruiting").getAsBoolean()?"Набор открыт":"Набор закрыт");case "polls"->"Голосование · открыть варианты";case "board"->value("author")+" · "+count("responsesCount")+" откликов";case "events"->eventTime();case "ideas"->CommunityScreen.statusLabel(value("status"));default->CommunityScreen.name(section);};
  if(h>=54)line(g,section.equals("events")?eventTime():item.has("attention")?value("attention"):eyebrow,left,y+8,x+w-left-12,color());line(g,value("title"),left,y+(h>=54?23:8),x+w-left-12,0xFFFFFF);
  String preview=section.equals("ideas")&&!value("answerPreview").isBlank()?"Ответ: "+value("answerPreview"):value("preview");
  line(g,preview,left,y+(h>=54?38:23),x+w-left-12,AccessibilityScreen.foreground(0xB6C2CC));
  if(h>=68)line(g,item.has("footer")?value("footer"):section.equals("groups")?count("membersCount")+" участников · Смотреть команду":section.equals("events")?(item.has("waitlistPosition")&&item.get("waitlistPosition").getAsInt()>0?"Очередь: "+item.get("waitlistPosition").getAsInt():item.has("isParticipant")&&item.get("isParticipant").getAsBoolean()?"Вы участвуете":item.has("isOwner")&&item.get("isOwner").getAsBoolean()?"Вы организатор":"Вы подписаны"):CommunityScreen.statusLabel(value("status")),left,y+54,x+w-left-12,AccessibilityScreen.foreground(0x96A6B5));
  g.disableScissor();
 }
}
