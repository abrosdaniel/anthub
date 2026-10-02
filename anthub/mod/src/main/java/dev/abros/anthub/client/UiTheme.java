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
 private static final ThreadLocal<Screen> RENDER_SCREEN=new ThreadLocal<>();
 static void rendering(Screen screen,Runnable render){Screen previous=RENDER_SCREEN.get();RENDER_SCREEN.set(screen);try{render.run();}finally{if(previous==null)RENDER_SCREEN.remove();else RENDER_SCREEN.set(previous);}}
 public static boolean stylesButtons(Screen s,net.minecraft.client.gui.components.AbstractButton widget){if(RENDER_SCREEN.get()!=null)s=RENDER_SCREEN.get();return owns(s)||(s instanceof net.minecraft.client.gui.screens.TitleScreen&&widget.getClass().getPackageName().equals("dev.abros.anthub.client"));}
 public static boolean owns(Screen s){return s!=null&&s.getClass().getPackageName().equals("dev.abros.anthub.client");}
 static float hover(AbstractWidget w){float target=w.isHoveredOrFocused()?1:0;if(!AccessibilityScreen.animations())return target;long now=System.nanoTime();var state=motion.get(w);if(state==null){motion.put(w,new HoverState(target,now));return target;}float step=Math.min(1,(now-state.at)/(AccessibilityScreen.motionMillis()*1000000f));state.at=now;state.value+=Math.copySign(Math.min(Math.abs(target-state.value),step),target-state.value);return state.value;}

 static int mix(int a,int b,float t){int out=0;for(int shift=0;shift<=24;shift+=8)out|=((int)(((a>>>shift)&255)*(1-t)+((b>>>shift)&255)*t))<<shift;return out;}
 static void panel(GuiGraphics g,int x,int y,int w,int h,int color){if(w<=0||h<=0)return;int material=AccessibilityScreen.background(color);g.fill(x+2,y+h,x+w+2,y+h+2,UiPalette.color(0x30000000));UiKit.plate(g,x,y,w,h,material);UiKit.detail(g,x,y,w,h);}

 public static void shell(Screen s,GuiGraphics g){if(s instanceof CommunityScreen||s instanceof TaskScreen||s instanceof ReportQueueScreen||s instanceof ServerMenuScreen||s instanceof ServerInfoScreen||s instanceof FeatureListScreen f&&f.kind.equals("players"))UiWorkspace.fit(s.width,s.height).draw(g);}

 public static void button(AbstractButton b,GuiGraphics g){
  var font=Minecraft.getInstance().font;int x=b.getX(),y=b.getY(),w=b.getWidth(),h=b.getHeight();String text=b.getMessage().getString(),raw=text;float t=b.active?hover(b):0;var style=UiActions.style(b);int color=b.active?switch(style.tone()){case PRIMARY->UiPalette.color(0xFF8CBFA2);case DANGER->UiPalette.color(0xFFDB7777);default->UiPalette.color(0xFFE2BE75);}:UiPalette.color(0xFF687580);
  int base=!b.active?UiPalette.color(0xFF20272D):UiPalette.color(0xFF293844);
  int surface=mix(base,style.tone()==UiActions.Tone.PRIMARY?UiPalette.color(0xFF326554):style.tone()==UiActions.Tone.DANGER?UiPalette.color(0xFF6D3D48):UiPalette.color(0xFF3D5261),t);
  UiKit.plate(g,x,y,w,h,surface);
  if(b.isFocused())g.renderOutline(x,y,w,h,color);
  boolean field=text.endsWith(" ▾");if(field){g.fill(x+w-22,y+1,x+w-1,y+h-1,UiPalette.color(0x40202C35));g.fill(x+w-23,y+5,x+w-22,y+h-5,mix(surface,color,0.18f));}
  if(b.active&&style.tone()!=UiActions.Tone.NORMAL)g.fill(x+3,y+5,x+5,y+h-5,color);
  if(text.equals("×")||text.equals("+")||text.equals("↑")||text.equals("↓")){UiIcons.draw(g,text.equals("×")?UiIcons.CLEAR:text.equals("↑")?UiIcons.UP:text.equals("↓")?UiIcons.DOWN:UiIcons.PLUS,x+(w-12)/2,y+(h-12)/2,b.active?UiPalette.color(0xFFE7EDF1):UiPalette.color(0xFF687580));return;}
  boolean dropdown=text.endsWith(" ▾");if(dropdown)text=text.substring(0,text.length()-2);if(text.startsWith("+ "))text=text.substring(2);
  String symbol=dropdown?"":style.icon();int trailing=dropdown?18:0;if(font.width(text)+30+trailing>w)symbol="";int reserve=symbol.isEmpty()?0:18;
  String shown=font.width(text)>w-12-reserve-trailing?font.plainSubstrByWidth(text,Math.max(1,w-12-reserve-trailing-font.width("…")))+"…":text;
  int tx=dropdown?x+8:x+(w-font.width(shown)-reserve)/2,ty=y+(h-8)/2;
  if(!symbol.isEmpty())UiIcons.draw(g,symbol,tx,y+(h-12)/2,color);
  Ui.text(g,font,shown,tx+reserve,ty,b.active?UiKit.text():UiPalette.color(0x82909C),false);
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
