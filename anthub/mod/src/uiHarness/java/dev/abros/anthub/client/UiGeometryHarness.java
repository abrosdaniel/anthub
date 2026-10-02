package dev.abros.anthub.client;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
/** Cross-screen contract: repeated semantic actions must not drift back to local sizes. */
final class UiGeometryHarness {
 static void verify(Screen screen){
  boolean framed=screen instanceof HubScreen||screen instanceof RegistryScreen||screen instanceof SkinsScreen||screen instanceof FeatureListScreen||screen instanceof TaskScreen||screen instanceof CommunityScreen||screen instanceof ReportQueueScreen||screen instanceof ServerMenuScreen||screen instanceof ServerInfoScreen||screen instanceof FeatureListScreen list&&list.kind.equals("players");
  if(framed){var frame=UiWorkspace.fit(screen.width,screen.height).frame();for(var child:screen.children())if(child instanceof net.minecraft.client.gui.components.AbstractWidget widget&&widget.visible&&(widget.getX()<frame.x()||widget.getY()<frame.y()||widget.getX()+widget.getWidth()>frame.right()||widget.getY()+widget.getHeight()>frame.bottom()))throw new IllegalStateException(screen.getClass().getSimpleName()+": widget escaped inset frame: "+widget.getMessage().getString());}

  if(screen instanceof ScrollScreen scrolling && scrolling.scrollLayout()!=null)verifyScroll(screen,scrolling.scrollLayout());
  if(screen instanceof TaskScreen task && task.listGeometry()!=null)verifyScroll(screen,task.listGeometry());


  for(var child:screen.children())if(child instanceof Button button){
   var command=UiActions.commandOf(button);String label=button.getMessage().getString();
   if((label.equals("Обновить")||label.equals("Повторить"))&&command==null)throw new IllegalStateException(screen.getClass().getSimpleName()+": local refresh/retry button");
   if(command!=null){
    boolean workspace=screen instanceof TaskScreen||screen instanceof CommunityScreen||screen instanceof ReportQueueScreen||screen instanceof ServerMenuScreen||screen instanceof ServerInfoScreen||screen instanceof FeatureListScreen list&&list.kind.equals("players");
    if(workspace&&button.getY()==UiWorkspace.fit(screen.width,screen.height).footer().y()){var footer=UiPageFooter.workspace(screen.width,screen.height);var slot=command==UiActions.Command.BACK||command==UiActions.Command.CLOSE?footer.end():footer.start();if(button.getX()!=slot.x()||button.getWidth()!=slot.width())throw new IllegalStateException(screen.getClass().getSimpleName()+": footer alignment drift");}
if(button.getHeight()!=UiActions.CONTROL_HEIGHT||button.getWidth()>UiActions.COMMAND_WIDTH)throw new IllegalStateException(screen.getClass().getSimpleName()+": invalid command size");
    if(screen.width>=420&&button.getWidth()!=UiActions.COMMAND_WIDTH)throw new IllegalStateException(screen.getClass().getSimpleName()+": command width drift: "+command+" "+button.getWidth());}
  }
 }
 private static void verifyScroll(Screen screen,dev.abros.anthub.core.ScrollLayout layout){
  var track=layout.track();var owner=layout.viewport();
  if(owner.x()<0||owner.y()<0||owner.right()>screen.width||owner.bottom()>screen.height)throw new IllegalStateException(screen.getClass().getSimpleName()+": viewport outside screen");
  if(track.x()<owner.x()||track.right()>owner.right()||track.y()<owner.y()||track.bottom()>owner.bottom())throw new IllegalStateException("Scrollbar escaped viewport");
  if(track.width()==0||track.height()==0)return;
  for(var child:screen.children())if(child instanceof net.minecraft.client.gui.components.AbstractWidget widget && widget.visible && widget.getY()<track.bottom() && widget.getY()+widget.getHeight()>track.y() && widget.getX()<track.right() && widget.getX()+widget.getWidth()>track.x())throw new IllegalStateException(screen.getClass().getSimpleName()+": widget overlaps scrollbar: "+widget.getMessage().getString());
 }
 static void verifyLayouts(){
  for(var command:UiActions.Command.values()){var button=UiActions.command(command,new dev.abros.anthub.core.NativeLayout.Box(12,14,300,20),widget->{},()->{});button.setMessage(net.minecraft.network.chat.Component.literal("Другая подпись"));if(button.getWidth()!=UiActions.COMMAND_WIDTH||button.getHeight()!=UiActions.CONTROL_HEIGHT||UiActions.commandOf(button)!=command)throw new IllegalStateException("Command geometry depends on caption");}

  for(int width=320;width<=2560;width+=8){var shell=UiWorkspace.fit(width,720);var footer=UiPageFooter.fit(shell.footer());if(shell.footer().width()>=2*UiActions.COMMAND_WIDTH+2*UiActions.GAP&&(footer.start().width()!=UiActions.COMMAND_WIDTH||footer.end().width()!=UiActions.COMMAND_WIDTH))throw new IllegalStateException("Footer commands shrank at "+width);if(footer.start().right()>footer.status().x()||footer.status().right()>footer.end().x()||footer.end().right()!=shell.footer().right())throw new IllegalStateException("Footer overlap");}
 }
 private UiGeometryHarness(){}
}
