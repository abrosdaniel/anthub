package dev.abros.anthub.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import java.util.List;
@EventBusSubscriber(modid="anthub",value=Dist.CLIENT)
public final class ToolInsetsHarness {
 private static int step=-1;private static long at;
 @SubscribeEvent public static void tick(ScreenEvent.Render.Post event){String output=System.getenv("ANTHUB_TOOL_INSETS");boolean preview=System.getenv("ANTHUB_TOOL_PREVIEW")!=null;if(output==null&&!preview||step>144)return;var mc=Minecraft.getInstance();if(preview){if(step<0&&mc.screen instanceof TitleScreen){step=145;mc.setScreen(new HubScreen(null));System.out.println("ANTHUB_TOOL_PREVIEW_READY");}return;}if(step<0){if(!(mc.screen instanceof TitleScreen))return;CommunityUiHarness.open("normal");new java.io.File(output).mkdirs();step=0;at=System.currentTimeMillis()+500;}if(System.currentTimeMillis()<at)return;at=System.currentTimeMillis()+400;
 try{if(step>0){UiGeometryHarness.verify(mc.screen);var widgets=mc.screen.children().stream().filter(c->c instanceof AbstractWidget).map(c->(AbstractWidget)c).filter(w->w.visible).toList();for(int a=0;a<widgets.size();a++)for(int b=a+1;b<widgets.size();b++){var x=widgets.get(a);var y=widgets.get(b);if(x.getX()<y.getX()+y.getWidth()&&y.getX()<x.getX()+x.getWidth()&&x.getY()<y.getY()+y.getHeight()&&y.getY()<x.getY()+x.getHeight())throw new IllegalStateException(mc.screen.getClass().getSimpleName()+": overlap "+x.getMessage().getString()+" / "+y.getMessage().getString());}net.minecraft.client.Screenshot.grab(new java.io.File(output),String.format("tools-%03d.png",step),mc.getMainRenderTarget(),m->{});}
 if(step==144){step++;System.out.println("ANTHUB_TOOL_INSETS_OK: 144 states, four tools, twelve themes, three scales");mc.stop();return;}
 int group=step/4;UiPalette.preview(group%12);if(step%4==0){mc.options.guiScale().set(1+group/12);mc.resizeDisplay();}
 switch(step%4){case 0->mc.setScreen(new HubScreen(null));case 1->mc.setScreen(new RegistryScreen(null,List.of()));case 2->mc.setScreen(new FeatureListScreen(null,"links"));case 3->mc.setScreen(new SkinsScreen(null));}step++;
 }catch(Throwable failure){System.out.println("ANTHUB_TOOL_INSETS_FAILED step="+step);failure.printStackTrace();step=144;mc.stop();}}
}
