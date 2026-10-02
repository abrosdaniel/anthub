package dev.abros.anthub.client;
import com.google.gson.JsonObject;
import dev.abros.anthub.core.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.network.chat.Component;
import java.util.*;
/** Mini-profile owns both measurement and drawing; missing metadata takes no space. */
final class HudProfile {
 record Line(Component text,boolean primary){}
 private static boolean shown(String key){return !HudSettings.INSTANCE.profileHidden.contains(key);}
 static List<Line> lines(boolean preview){var mc=Minecraft.getInstance();var state=ServerMenuClient.state;var meta=state.has("profile")?state.getAsJsonObject("profile"):new JsonObject();var rows=new ArrayList<Line>();
  if(shown("name"))rows.add(new Line(Component.literal(Json.opt(state,"name",mc.player==null?"Игрок":mc.player.getGameProfile().getName())),true));
  for(String field:List.of("prefix","suffix")){String value=Json.opt(meta,field,"").strip();if(value.isBlank()&&preview)value=field.equals("prefix")?"Инженер":"Мастер механизмов";if(shown(field)&&!value.isBlank())rows.add(new Line(metadata(value),false));}
  if(shown("session")&&(state.has("sessionSeconds")||preview)){long seconds=state.has("sessionSeconds")?state.get("sessionSeconds").getAsLong():3600;long minutes=Math.max(0,seconds/60);rows.add(new Line(Component.literal("Сессия · "+(minutes>=60?minutes/60+" ч ":"")+minutes%60+" мин"),false));}
  if(shown("ping")){var info=mc.getConnection()==null||mc.player==null?null:mc.getConnection().getPlayerInfo(mc.player.getUUID());if(info!=null||preview)rows.add(new Line(Component.literal("Связь · "+(info==null?42:info.getLatency())+" мс"),false));}return rows;
 }
 static int height(boolean preview){var lines=lines(preview);return lines.isEmpty()&&!shown("head")?0:Math.max(shown("head")?24:0,lines.size()*12)+16;}
 static void draw(GuiGraphics g,int x,int y,int w,boolean preview){var mc=Minecraft.getInstance();var rows=lines(preview);boolean head=shown("head");int content=Math.max(head?24:0,rows.size()*12);if(head){int hy=y+8+(content-24)/2;g.fill(x+7,hy-1,x+33,hy+25,UiPalette.scrollTrack());PlayerFaceRenderer.draw(g,mc.player==null?SkinClient.ordinarySkin():SkinClient.skin(mc.player.getUUID()),x+8,hy,24);g.fill(x+27,hy+19,x+33,hy+25,UiKit.surface());g.fill(x+29,hy+21,x+32,hy+24,UiKit.accent());}
  int tx=x+(head?42:10),ty=y+8+(content-rows.size()*12)/2,limit=w-(head?52:20);for(var line:rows){g.drawString(mc.font,net.minecraft.locale.Language.getInstance().getVisualOrder(mc.font.substrByWidth(line.text(),Math.max(0,limit))),tx,ty,line.primary()?UiKit.text():UiKit.muted(),false);ty+=12;}
 }
 private static Component metadata(String value){var out=Component.empty();PlayerText.text(value).visit((style,text)->{if(style.getColor()!=null)style=style.withColor(readable(style.getColor().getValue()));out.append(Component.literal(text).withStyle(style));return Optional.empty();},net.minecraft.network.chat.Style.EMPTY);return out;}
 private static int readable(int color){int background=UiKit.surface(),target=UiKit.text();if(contrast(color,background)>=4.5)return color;for(int n=1;n<=10;n++){int mixed=0;for(int shift=0;shift<=16;shift+=8)mixed|=(((color>>shift&255)*(10-n)+(target>>shift&255)*n)/10)<<shift;if(contrast(mixed,background)>=4.5)return mixed;}return target&0xFFFFFF;}
 private static double contrast(int a,int b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
 private static double luminance(int color){double sum=0;double[] weights={.0722,.7152,.2126};for(int n=0;n<3;n++){double c=(color>>(n*8)&255)/255d;sum+=weights[n]*(c<=.04045?c/12.92:Math.pow((c+.055)/1.055,2.4));}return sum;}
 private HudProfile(){}
}
