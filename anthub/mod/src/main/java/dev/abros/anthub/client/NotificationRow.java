package dev.abros.anthub.client;
import com.google.gson.JsonObject;
import dev.abros.anthub.core.Json;
import java.time.*;
import java.time.format.DateTimeFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
/** Inbox entries remain readable rows rather than centered action buttons. */
final class NotificationRow extends Button {
 private final JsonObject notice;
 NotificationRow(int x,int y,int w,JsonObject notice,Runnable action){super(x,y,w,36,Component.literal(Json.str(notice,"title")),b->action.run(),DEFAULT_NARRATION);this.notice=notice;}
 @Override protected void renderWidget(GuiGraphics g,int mx,int my,float dt){int x=getX(),y=getY();var font=Minecraft.getInstance().font;boolean unread=!notice.get("read").getAsBoolean();g.fill(x,y,x+width,y+height,UiTheme.mix(unread?UiPalette.color(0xFF263B48):UiPalette.color(0xFF1D2B35),UiPalette.color(0xFF344F5D),UiTheme.hover(this)));if(unread)g.fill(x,y,x+2,y+height,UiPalette.color(0xFF85CFBD));
 String title=Json.str(notice,"title")+(notice.has("groupCount")?" ("+notice.get("groupCount").getAsInt()+")":"");int limit=width-34;if(font.width(title)>limit)title=font.plainSubstrByWidth(title,Math.max(1,limit-font.width("…")))+"…";Ui.text(g,font,title,x+9,y+6,AccessibilityScreen.foreground(unread?UiPalette.color(0xF2F6F8):UiPalette.color(0xC1CED8)),false);
 String category=CommunityScreen.name(Json.opt(notice,"section","notifications"));if(category.equals("Уведомления"))category="Сообщество";String when=notice.has("at")?Instant.ofEpochMilli(notice.get("at").getAsLong()).atZone(AccessibilityScreen.zone()).format(DateTimeFormatter.ofPattern("dd.MM HH:mm")):"";String meta=category+(when.isBlank()?"":" · "+when)+(unread?" · новое":"");Ui.text(g,font,font.plainSubstrByWidth(meta,width-20),x+9,y+22,UiPalette.color(0x91A7B7),false);if(isHoveredOrFocused())UiIcons.draw(g,UiIcons.RIGHT,x+width-20,y+5,UiPalette.color(0xFF85CFBD));if(isFocused())g.renderOutline(x,y,width,height,UiPalette.color(0xFF85CFBD));
 }
}
