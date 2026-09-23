package dev.abros.anthub.client;
import com.google.gson.*;
import dev.abros.anthub.core.Json;
import dev.abros.anthub.core.skins.SkinImage;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.*;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.lwjgl.system.MemoryStack;
import java.nio.file.*;

final class SkinsScreen extends ScrollScreen {
 private final Screen parent;private RemotePlayer previewPlayer;private byte[] draft;private ResourceLocation draftTexture;private String draftName="",message="";private boolean slim;private float rotation;private int left,listWidth,right;private String selection="";
 SkinsScreen(Screen parent){super(Component.literal("Скины"));this.parent=parent;}
 static void open(Screen parent){var mc=net.minecraft.client.Minecraft.getInstance();mc.setScreen(new SkinsScreen(parent));SkinClient.command("list","",false);}
 boolean isPreview(net.minecraft.world.entity.Entity entity){return entity==previewPlayer;}
 void updated(){if(draft==null&&SkinClient.library.has("profile"))selection=Json.opt(SkinClient.library.getAsJsonObject("profile"),"active","");rebuildWidgets();}
 private Button button(String text,int x,int y,int w,Runnable action){return addRenderableWidget(Button.builder(Component.literal(text),b->action.run()).bounds(x,y,w,20).build());}
 private JsonArray entries(){return SkinClient.library.has("entries")?SkinClient.library.getAsJsonArray("entries"):new JsonArray();}
 @Override protected void init(){left=Math.max(12,(width-600)/2);int total=width-2*left;listWidth=total*3/5-12;right=left+listWidth+18;int bottom=height-74;scrollArea(entries().size()+1,48,bottom,48,left+listWidth+2);
  if(draft==null){for(int row=firstRow;row<Math.min(entries().size()+1,firstRow+visibleRows);row++){int y=48+(row-firstRow)*48;if(row==0){var b=button("Обычный",left,y,listWidth,()->{selection="";SkinClient.command("select","",false);});b.active=!SkinClient.busy&&!selection.isEmpty();}else{var entry=entries().get(row-1).getAsJsonObject();String id=Json.str(entry,"id");var b=button(font.plainSubstrByWidth(Json.str(entry,"name"),listWidth-16),left,y,listWidth,()->{selection=id;SkinClient.command("select",id,false);});b.active=!SkinClient.busy&&!selection.equals(id);int half=(listWidth-4)/2;var model=button(entry.get("slim").getAsBoolean()?"Тонкие руки":"Обычные руки",left,y+24,half,()->SkinClient.command("model",id,!entry.get("slim").getAsBoolean()));model.active=!SkinClient.busy;var del=button("Удалить",left+half+4,y+24,half,()->minecraft.setScreen(new ConfirmScreen(ok->{minecraft.setScreen(this);if(ok)SkinClient.command("delete",id,false);},Component.literal("Удалить скин?"),Component.literal(Json.str(entry,"name")))));del.active=!SkinClient.busy;}}var add=button("Добавить PNG"+(SkinClient.library.has("limit")?" · "+entries().size()+" / "+SkinClient.library.get("limit").getAsInt():""),left,height-56,listWidth,this::chooseFile);if(SkinClient.library.has("maxBytes"))add.setTooltip(Tooltip.create(Component.literal("PNG 64×64 или 64×32, до "+SkinClient.library.get("maxBytes").getAsInt()/1048576+" МиБ")));add.active=SkinClient.available()&&!SkinClient.busy&&SkinClient.library.has("limit")&&entries().size()<SkinClient.library.get("limit").getAsInt();}
  else{button(slim?"Модель: тонкие руки":"Модель: обычные руки",left,76,listWidth,()->{slim=!slim;rebuildWidgets();});var add=button("Загрузить и применить",left,104,listWidth,()->{SkinClient.upload(draftName,slim,draft);draft=null;draftTexture=null;rebuildWidgets();});add.active=!SkinClient.busy;button("Отменить загрузку",left,132,listWidth,()->{draft=null;draftTexture=null;message="";rebuildWidgets();});}
  button("Назад",Math.max(12,width/2-70),height-28,140,this::onClose);
  if(minecraft.level!=null&&minecraft.player!=null&&previewPlayer==null)previewPlayer=new RemotePlayer(minecraft.level,minecraft.player.getGameProfile()){@Override public PlayerSkin getSkin(){return previewSkin();}};
 }
 private void chooseFile(){try(var stack=MemoryStack.stackPush()){var patterns=stack.mallocPointer(1);patterns.put(stack.UTF8("*.png")).flip();String chosen=TinyFileDialogs.tinyfd_openFileDialog("Выберите скин PNG",null,patterns,"Minecraft PNG (64×64, 64×32)",false);if(chosen==null)return;var path=Path.of(chosen);long limit=SkinClient.library.get("maxBytes").getAsLong();try(var in=Files.newInputStream(path)){byte[] bytes=in.readNBytes((int)limit+1);draft=SkinImage.normalize(bytes,(int)limit);}draftName=path.getFileName().toString().replaceFirst("(?i)\\.png$","");if(draftName.length()>40)draftName=draftName.substring(0,40);if(draftName.isBlank())draftName="Скин";slim=false;draftTexture=SkinClient.preview(draft);message="";rebuildWidgets();}catch(Exception error){draft=null;message="Не удалось открыть PNG: нужен скин 64×64 или 64×32 в пределах лимита.";}}
 private PlayerSkin previewSkin(){if(draftTexture!=null)return new PlayerSkin(draftTexture,null,null,null,slim?PlayerSkin.Model.SLIM:PlayerSkin.Model.WIDE,false);return minecraft.player==null?DefaultPlayerSkin.get(java.util.UUID.randomUUID()):SkinClient.skin(minecraft.player.getUUID());}
 @Override public void renderBackground(GuiGraphics g,int mx,int my,float delta){super.renderBackground(g,mx,my,delta);g.fill(left-6,38,left+listWidth+7,height-64,AccessibilityScreen.background(0xDD1C2731));g.fill(right-4,38,width-left+6,height-64,AccessibilityScreen.background(0xDD1C2731));}
 @Override public void render(GuiGraphics g,int mx,int my,float delta){super.render(g,mx,my,delta);g.drawCenteredString(font,title,width/2,18,0xE2BE75);if(draft!=null)g.drawString(font,font.plainSubstrByWidth(draftName,listWidth-8),left+4,52,0xFFFFFF);else for(int row=firstRow;row<Math.min(entries().size()+1,firstRow+visibleRows);row++){String id=row==0?"":Json.str(entries().get(row-1).getAsJsonObject(),"id");if(id.equals(selection))g.renderOutline(left-2,46+(row-firstRow)*48,listWidth+4,row==0?24:46,0xFFE2BE75);}
  if(previewPlayer!=null){rotation+=delta*0.006f;previewPlayer.yBodyRot=180;previewPlayer.setYRot(180);previewPlayer.yHeadRot=180;previewPlayer.yHeadRotO=180;g.enableScissor(right,48,width-left,height-74);InventoryScreen.renderEntityInInventory(g,(right+width-left)/2f,(48+height-74)/2f,Math.max(18,Math.min(70,(height-136)/2.2f)),new org.joml.Vector3f(0,previewPlayer.getBbHeight()/2,0),new org.joml.Quaternionf().rotateZ((float)Math.PI).rotateY(rotation),null,previewPlayer);g.disableScissor();}String text=message.isEmpty()?SkinClient.status:message;if(!text.isEmpty())g.drawCenteredString(font,font.plainSubstrByWidth(text,width-24),width/2,height-68,0xE2BE75);
 }
 @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(button==0&&x>=right&&y>=48&&y<height-74){rotation+=(float)dx*0.02f;return true;}return super.mouseDragged(x,y,button,dx,dy);}
 @Override public void onClose(){minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
