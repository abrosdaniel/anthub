package dev.abros.anthub.client;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
/** Shared graphite surfaces, brass focus and restrained motion for AntHub only. */
public final class UiTheme {
 private UiTheme(){} private static final class HoverState {float value;long at;HoverState(float value,long at){this.value=value;this.at=at;}}
 private static final Map<AbstractWidget,HoverState> motion=new WeakHashMap<>();
 public static boolean stylesButtons(Screen s,net.minecraft.client.gui.components.AbstractButton widget){return owns(s)||(s instanceof net.minecraft.client.gui.screens.TitleScreen&&widget.getClass().getPackageName().equals("dev.abros.anthub.client"));}
 public static boolean owns(Screen s){return s!=null&&s.getClass().getPackageName().equals("dev.abros.anthub.client");}
 static float hover(AbstractWidget w){float target=w.isHoveredOrFocused()?1:0;if(!AccessibilityScreen.animations())return target;long now=System.nanoTime();var state=motion.get(w);if(state==null){motion.put(w,new HoverState(target,now));return target;}float step=Math.min(1,(now-state.at)/120000000f);state.at=now;state.value+=Math.copySign(Math.min(Math.abs(target-state.value),step),target-state.value);return state.value;}

 static int mix(int a,int b,float t){int out=0;for(int shift=0;shift<=24;shift+=8)out|=((int)(((a>>>shift)&255)*(1-t)+((b>>>shift)&255)*t))<<shift;return out;}
 static void panel(GuiGraphics g,int x,int y,int w,int h,int color){if(w<=0||h<=0)return;int material=AccessibilityScreen.background(color);g.fill(x+2,y+h,x+w+2,y+h+2,UiPalette.color(0x30000000));UiKit.surface(g,x,y,w,h,material);g.fill(x+2,y,x+w-2,y+1,UiPalette.color(0xFF4A5B67));}

 public static void shell(Screen s,GuiGraphics g){if(!(s instanceof CommunityScreen||s instanceof TaskScreen||s instanceof ServerMenuScreen||s instanceof ServerInfoScreen||s instanceof FeatureListScreen f&&f.kind.equals("players")))return;g.fill(4,6,s.width-4,s.height-6,AccessibilityScreen.background(UiPalette.color(0xBD111A22)));g.fill(4,6,s.width-4,34,UiPalette.color(0xD51B2832));int left=Math.min(146,Math.max(96,s.width/4))+8;g.fill(left,38,left+1,s.height-34,UiPalette.color(0xFF46535E));}
 private static int accent(String label){if(label.startsWith("Удалить")||label.startsWith("Забанить")||label.startsWith("Кикнуть")||label.startsWith("Отключить голос"))return UiPalette.color(0xFFDB7777);if(label.startsWith("Сохранить")||label.startsWith("Принять")||label.startsWith("Создать")||label.equals("Новая задача")||label.startsWith("Участвовать"))return UiPalette.color(0xFF8CBFA2);return UiPalette.color(0xFFE2BE75);}
 private static String icon(String text){if(text.startsWith("Поиск")||text.equals("Найти"))return UiIcons.SEARCH;if(text.startsWith("Обновить")||text.equals("Повторить"))return UiIcons.REFRESH;if(text.startsWith("Удалить"))return UiIcons.DELETE;if(text.startsWith("Принять")||text.startsWith("Сохранить"))return text.startsWith("Сохранить")?UiIcons.SAVE:UiIcons.CHECK;if(text.startsWith("+ ")||text.startsWith("Создать")||text.startsWith("Добавить")||text.equals("Новая задача"))return UiIcons.PLUS;return "";}
 public static void button(AbstractButton b,GuiGraphics g){
  var font=Minecraft.getInstance().font;int x=b.getX(),y=b.getY(),w=b.getWidth(),h=b.getHeight();String text=b.getMessage().getString(),raw=text;float t=b.active?hover(b):0;int color=b.active?accent(text):UiPalette.color(0xFF687580);
  int base=!b.active?UiPalette.color(0xFF20272D):color==UiPalette.color(0xFF8CBFA2)?UiPalette.color(0xFF254B40):color==UiPalette.color(0xFFDB7777)?UiPalette.color(0xFF503039):UiPalette.color(0xFF293844);
  int surface=mix(base,color==UiPalette.color(0xFF8CBFA2)?UiPalette.color(0xFF326554):color==UiPalette.color(0xFFDB7777)?UiPalette.color(0xFF6D3D48):UiPalette.color(0xFF3D5261),t);
  g.fillGradient(x,y,x+w,y+h,mix(surface,UiPalette.color(0xFF526572),0.10f),surface);
  if(b.isFocused())g.renderOutline(x,y,w,h,color);else g.fill(x,y+h-1,x+w,y+h,b.active?mix(UiPalette.color(0xFF536672),color,t*0.45f):UiPalette.color(0xFF36424B));
  if(text.startsWith("☑ ")||text.startsWith("☐ ")){boolean checked=text.startsWith("☑ ");UiKit.checkbox(g,x+UiKit.INSET,y+(h-UiKit.CHECK_SIZE)/2,checked,b.active?UiKit.accent():UiKit.muted());g.drawString(font,UiKit.fit(font,text.substring(2),w-32),x+26,y+(h-8)/2,b.active?UiKit.text():UiKit.muted(),false);return;}
  boolean field=text.endsWith(" ▾");if(field){g.fill(x+w-22,y+1,x+w-1,y+h-1,UiPalette.color(0x40202C35));g.fill(x+w-23,y+4,x+w-22,y+h-4,UiPalette.color(0xFF536672));}
  if(b.active&&(color==UiPalette.color(0xFF8CBFA2)||color==UiPalette.color(0xFFDB7777)))g.fill(x+1,y+h-2,x+w-1,y+h-1,color);
  if(text.equals("×")||text.equals("+")){UiIcons.draw(g,text.equals("×")?UiIcons.CLEAR:UiIcons.PLUS,x+(w-12)/2,y+(h-12)/2,b.active?UiPalette.color(0xFFE7EDF1):UiPalette.color(0xFF687580));return;}
  boolean dropdown=text.endsWith(" ▾");if(dropdown)text=text.substring(0,text.length()-2);if(text.startsWith("+ "))text=text.substring(2);
  String symbol=dropdown?"":icon(raw);int trailing=dropdown?18:0;if(font.width(text)+30+trailing>w)symbol="";int reserve=symbol.isEmpty()?0:18;
  String shown=font.width(text)>w-12-reserve-trailing?font.plainSubstrByWidth(text,Math.max(1,w-12-reserve-trailing-font.width("…")))+"…":text;
  int tx=dropdown?x+8:x+(w-font.width(shown)-reserve)/2,ty=y+(h-8)/2;
  if(!symbol.isEmpty())UiIcons.draw(g,symbol,tx,y+(h-12)/2,color);
  g.drawString(font,shown,tx+reserve,ty,b.active?AccessibilityScreen.foreground(UiPalette.color(0xF2F6F8)):UiPalette.color(0x82909C),false);
  if(dropdown)UiIcons.draw(g,UiIcons.DOWN,x+w-16,y+(h-12)/2,b.active?UiPalette.color(0xFFE2BE75):UiPalette.color(0xFF687580));
 }
 public static boolean keyboard(Screen screen,int key){
  if(!owns(screen)||screen.getFocused() instanceof EditBox||screen.getFocused() instanceof MultiLineEditBox||key!=264&&key!=265)return false;
  var cards=screen.children().stream().filter(e->e instanceof AbstractWidget w&&w.active&&w.visible&&(e instanceof CommunityCard||e instanceof PlayerRow||e instanceof UiSummaryCard)).map(e->(AbstractWidget)e).toList();
  if(cards.isEmpty()||screen.getFocused()!=null&&!cards.contains(screen.getFocused()))return false;
  if(screen.getFocused()==null){screen.setFocused(cards.getFirst());return true;}
  var current=(AbstractWidget)screen.getFocused();AbstractWidget next=current;long distance=Long.MAX_VALUE;
  for(var card:cards){int dy=card.getY()-current.getY();if(key==264?dy<=0:dy>=0)continue;long score=Math.abs(dy)*1000L+Math.abs(card.getX()-current.getX());if(score<distance){distance=score;next=card;}}
  screen.setFocused(next);return true;
 }
}
