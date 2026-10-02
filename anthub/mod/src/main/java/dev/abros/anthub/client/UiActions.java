package dev.abros.anthub.client;
import dev.abros.anthub.core.NativeLayout;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;
/** Compound action footer. Widths and gaps are computed once for every host. */
final class UiActions {
 static final int CONTROL_HEIGHT=20, COMMAND_WIDTH=96, GAP=6;
 enum Command {
  REFRESH("server.refresh",UiIcons.REFRESH), RETRY("retry",UiIcons.REFRESH), BACK("back",""), CLOSE("close",""), LIST("backToList","");
  final String label,icon;Command(String label,String icon){this.label=label;this.icon=icon;}
 }
 private static final Map<Button,Command> commands=new WeakHashMap<>();
 static Button command(Command command,NativeLayout.Box slot,Consumer<AbstractWidget> add,Runnable run){
  var b=button(Client.tr(command.label),Tone.NORMAL,command.icon,ignored->run.run()).bounds(slot.x(),slot.y(),Math.min(COMMAND_WIDTH,slot.width()),CONTROL_HEIGHT).build();
  commands.put(b,command);add.accept(b);return b;
 }
 static Command commandOf(Button button){return commands.get(button);}
 enum Tone { NORMAL, PRIMARY, DANGER }
 record Style(Tone tone,String icon){}
 private static final Map<Button,Style> styles=new WeakHashMap<>();
 record Action(Component label,Runnable run,boolean enabled,Tone tone,String icon,String reason){Action withIcon(String icon){return new Action(label,run,enabled,tone,icon,reason);}Action because(String reason){return new Action(label,run,enabled,tone,icon,reason);}}
 static Action action(String label,Runnable run,boolean enabled){return new Action(Component.literal(label),run,enabled,Tone.NORMAL,"","");}
 static Button.Builder button(Component label,Tone tone,String icon,Button.OnPress press){return new Button.Builder(label,press){@Override public Button build(){return style(super.build(),tone,icon);}};}
 static Action primary(String label,Runnable run,boolean enabled){return new Action(Component.literal(label),run,enabled,Tone.PRIMARY,UiIcons.CHECK,"");}
 static Action danger(String label,Runnable run,boolean enabled){return new Action(Component.literal(label),run,enabled,Tone.DANGER,UiIcons.DELETE,"");}
 static <T extends Button> T style(T button,Tone tone,String icon){styles.put(button,new Style(tone,icon));return button;}
 static Style style(net.minecraft.client.gui.components.AbstractButton button){return styles.getOrDefault(button,new Style(Tone.NORMAL,""));}
 static List<Button> row(NativeLayout.Box area,Consumer<AbstractWidget> add,Action... actions){
  return row(area,add,Arrays.stream(actions).map(a->NativeLayout.Track.flex(1)).toArray(NativeLayout.Track[]::new),actions);
 }
 static List<Button> row(NativeLayout.Box area,Consumer<AbstractWidget> add,NativeLayout.Track[] tracks,Action... actions){
  if(tracks.length!=actions.length)throw new IllegalArgumentException("Action tracks must match actions");
  var boxes=NativeLayout.row(area,6,tracks);
  var widgets=new ArrayList<Button>();
  for(int i=0;i<actions.length;i++){var a=actions[i];var b=boxes.get(i);var widget=Button.builder(a.label(),ignored->a.run().run()).bounds(b.x(),b.y(),b.width(),b.height()).build();style(widget,a.tone(),a.icon());widget.active=a.enabled();if(!a.enabled()&&!a.reason().isBlank())widget.setTooltip(Tooltip.create(Component.literal(a.reason())));add.accept(widget);widgets.add(widget);}
  return List.copyOf(widgets);
 }
 static Button close(NativeLayout.Box area,Consumer<AbstractWidget> add,Runnable close){int w=Math.min(COMMAND_WIDTH,area.width());return command(Command.CLOSE,new NativeLayout.Box(area.right()-w,area.y(),w,CONTROL_HEIGHT),add,close);}
 private UiActions(){}
}
