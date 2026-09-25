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
 private static final java.util.concurrent.ExecutorService FILES=java.util.concurrent.Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"AntHub skin file picker");t.setDaemon(true);return t;});
 private static final java.util.concurrent.atomic.AtomicBoolean PICKER=new java.util.concurrent.atomic.AtomicBoolean();
 private boolean secondLayer=true;private boolean choosing;private EditBox nameField;
 private final Screen parent;private RemotePlayer previewPlayer;private byte[] draft;private ResourceLocation draftTexture;private String draftName="",message="";private boolean slim;private float rotation;private int left,listWidth,right,previewBottom;private String selection="";
 SkinsScreen(Screen parent){super(Component.literal("Скины"));this.parent=parent;}
 static void open(Screen parent){var mc=net.minecraft.client.Minecraft.getInstance();mc.setScreen(new SkinsScreen(parent));SkinClient.command("list","",false);}
 boolean isPreview(net.minecraft.world.entity.Entity entity){return entity==previewPlayer;}
 void updated(){if(draft==null&&SkinClient.library.has("profile"))selection=Json.opt(SkinClient.library.getAsJsonObject("profile"),"active","");rebuildWidgets();}
 private Button button(String text,int x,int y,int w,Runnable action){return addRenderableWidget(Button.builder(Component.literal(text),b->action.run()).bounds(x,y,w,20).build());}
 private JsonArray entries(){return SkinClient.library.has("entries")?SkinClient.library.getAsJsonArray("entries"):new JsonArray();}
 @Override protected void init(){left=Math.max(12,(width-600)/2);int total=width-2*left;listWidth=total*3/5-12;right=left+listWidth+18;int bottom=height-74;scrollArea(entries().size()+1,48,bottom,72,left+listWidth+2);
  if(draft==null){
   for(int row=firstRow;row<Math.min(entries().size()+1,firstRow+visibleRows);row++){
    int y=48+(row-firstRow)*72,half=(listWidth-16)/2;
    if(row==0){var reset=button(selection.isEmpty()?"Выбран":"Выбрать",left+6,y+24,listWidth-12,()->SkinClient.command("select","",false));reset.active=!SkinClient.busy&&!selection.isEmpty()&&!choosing;}
    else{var entry=entries().get(row-1).getAsJsonObject();String id=Json.str(entry,"id");
     var select=button(selection.equals(id)?"Выбран":"Выбрать",left+6,y+20,half,()->SkinClient.command("select",id,false));select.active=!SkinClient.busy&&!selection.equals(id)&&!choosing;
     var model=button(entry.get("slim").getAsBoolean()?"Тонкие руки":"Обычные руки",left+10+half,y+20,half,()->SkinClient.command("model",id,!entry.get("slim").getAsBoolean()));model.active=!SkinClient.busy&&!choosing;
     var rename=button("Название",left+6,y+42,half,()->minecraft.setScreen(new NameScreen(this,id,Json.str(entry,"name"))));rename.active=!SkinClient.busy&&!choosing&&dev.abros.anthub.network.Protocol.supportedFeatures.contains("skin-names");if(!rename.active&&!dev.abros.anthub.network.Protocol.supportedFeatures.contains("skin-names"))rename.setTooltip(Tooltip.create(Component.literal("Переименование недоступно на этом сервере")));
     var del=button("Удалить",left+10+half,y+42,half,()->minecraft.setScreen(new ConfirmScreen(ok->{minecraft.setScreen(this);if(ok)SkinClient.command("delete",id,false);},Component.literal("Удалить скин?"),Component.literal(Json.str(entry,"name")))));del.active=!SkinClient.busy&&!choosing;
    }
   }
   var add=button(choosing?"Выбор файла…":"Добавить PNG"+(SkinClient.library.has("limit")?" · "+entries().size()+" / "+SkinClient.library.get("limit").getAsInt():""),left,height-56,listWidth,this::chooseFile);
   if(SkinClient.library.has("maxBytes"))add.setTooltip(Tooltip.create(Component.literal("PNG 64×64 или 64×32, до "+SkinClient.library.get("maxBytes").getAsInt()/1048576+" МиБ")));
   add.active=!choosing&&!PICKER.get()&&SkinClient.available()&&!SkinClient.busy&&SkinClient.library.has("limit")&&entries().size()<SkinClient.library.get("limit").getAsInt();
  }else{
   nameField=addRenderableWidget(new EditBox(font,left+6,60,listWidth-12,20,Component.literal("Название скина")));nameField.setMaxLength(40);nameField.setValue(draftName);nameField.setResponder(v->draftName=v);
   button(slim?"Тонкие руки":"Обычные руки",left+6,86,listWidth-12,()->{slim=!slim;rebuildWidgets();});
   var add=button("Загрузить",left+6,112,listWidth-12,()->{if(draftName.strip().isEmpty()){message="Введите название скина";return;}SkinClient.upload(draftName.strip(),slim,draft);draft=null;draftTexture=null;rebuildWidgets();});add.active=!SkinClient.busy;
   button("Отмена",left+6,138,listWidth-12,()->{draft=null;draftTexture=null;message="";rebuildWidgets();});
  }
  int pw=width-left-right;boolean rowControls=pw>=240;int controlsTop=height-(rowControls?88:112);previewBottom=controlsTop-8;
  button("Второй слой: "+(secondLayer?"Вкл":"Выкл"),right,controlsTop,rowControls?(pw-4)/2:pw,()->{secondLayer=!secondLayer;rebuildWidgets();});
  button("Вернуть вид",rowControls?right+(pw+4)/2:right,rowControls?controlsTop:controlsTop+24,rowControls?(pw-4)/2:pw,()->rotation=0);

  if(SkinClient.retryable)button("Повторить запрос",right,height-56,width-left-right,SkinClient::retry);
  button("Назад",Math.max(12,width/2-70),height-28,140,this::onClose);
  if(minecraft.level!=null&&minecraft.player!=null&&previewPlayer==null)previewPlayer=new RemotePlayer(minecraft.level,minecraft.player.getGameProfile()){@Override public PlayerSkin getSkin(){return previewSkin();}@Override public boolean isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart part){return secondLayer;}};
 }
 private void chooseFile(){
  if(!PICKER.compareAndSet(false,true))return;
  choosing=true;message="";var client=minecraft;var connection=client.getConnection();var level=client.level;int limit=SkinClient.library.get("maxBytes").getAsInt();rebuildWidgets();
  FILES.execute(()->{
   byte[] bytes=null;String label="",failure="";
   try(var stack=MemoryStack.stackPush()){
    var patterns=stack.mallocPointer(1);patterns.put(stack.UTF8("*.png")).flip();
    String chosen=TinyFileDialogs.tinyfd_openFileDialog("Выберите скин PNG",null,patterns,"Minecraft PNG (64×64, 64×32)",false);
    if(chosen!=null){var path=Path.of(chosen);if(!Files.isRegularFile(path)||Files.size(path)>limit)throw new java.io.IOException();try(var in=Files.newInputStream(path)){bytes=SkinImage.normalize(in.readNBytes(limit+1),limit);}label=path.getFileName().toString().replaceFirst("(?i)\\.png$","").replaceAll("[\\p{Cntrl}]","");if(label.length()>40)label=label.substring(0,40);if(label.isBlank())label="Скин";}
   }catch(Exception error){failure="Нужен PNG 64×64 или 64×32 в пределах лимита.";}finally{PICKER.set(false);}
   final byte[] result=bytes;final String name=label,error=failure;
   client.execute(()->{choosing=false;if(client.screen!=this||client.getConnection()!=connection||client.level!=level)return;message=error;if(result!=null)try{draftTexture=SkinClient.preview(result);draft=result;draftName=name;slim=false;}catch(Exception invalid){message="Не удалось подготовить скин";}rebuildWidgets();});
  });
 }
 private static final class NameScreen extends Screen {
  private final SkinsScreen parent;private final String id;private String value;private EditBox field;
  NameScreen(SkinsScreen parent,String id,String value){super(Component.literal("Название скина"));this.parent=parent;this.id=id;this.value=value;}
  @Override public void renderBackground(GuiGraphics g,int x,int y,float d){super.renderBackground(g,x,y,d);DialogPanel.draw(g,width,Math.min(300,width-32),height/2-54,height/2+44);}
 @Override protected void init(){int w=Math.min(300,width-32),x=(width-w)/2,y=height/2-20;field=addRenderableWidget(new EditBox(font,x,y,w,20,title));field.setMaxLength(40);field.setValue(value);var save=addRenderableWidget(Button.builder(Component.literal("Сохранить"),b->{minecraft.setScreen(parent);SkinClient.command("rename",id,false,value.strip());}).bounds(x,y+30,(w-4)/2,20).build());save.active=!value.isBlank();field.setResponder(v->{value=v;save.active=!v.isBlank();});addRenderableWidget(Button.builder(Component.literal("Отмена"),b->onClose()).bounds(x+(w+4)/2,y+30,(w-4)/2,20).build());setInitialFocus(field);}
  @Override public void render(GuiGraphics g,int x,int y,float d){super.render(g,x,y,d);g.drawCenteredString(font,title,width/2,height/2-42,0xE2BE75);}
  @Override public void onClose(){minecraft.setScreen(parent);}
  @Override public boolean isPauseScreen(){return false;}
 }
 private PlayerSkin previewSkin(){if(draftTexture!=null)return new PlayerSkin(draftTexture,null,null,null,slim?PlayerSkin.Model.SLIM:PlayerSkin.Model.WIDE,false);return minecraft.player==null?DefaultPlayerSkin.get(java.util.UUID.randomUUID()):SkinClient.skin(minecraft.player.getUUID());}
 @Override public void renderBackground(GuiGraphics g,int mx,int my,float delta){super.renderBackground(g,mx,my,delta);g.fill(left-6,38,left+listWidth+7,height-64,AccessibilityScreen.background(0xDD1C2731));g.fill(right-4,38,width-left+6,height-64,AccessibilityScreen.background(0xDD1C2731));if(draft==null)for(int row=firstRow;row<Math.min(entries().size()+1,firstRow+visibleRows);row++){int y=48+(row-firstRow)*72;g.fill(left+2,y-3,left+listWidth-2,y+65,AccessibilityScreen.background(0xEF263440));}}
 @Override public void render(GuiGraphics g,int mx,int my,float delta){super.render(g,mx,my,delta);g.drawCenteredString(font,title,width/2,18,0xE2BE75);if(draft!=null)g.drawString(font,"Название",left+6,46,0xFFFFFF);else for(int row=firstRow;row<Math.min(entries().size()+1,firstRow+visibleRows);row++){String id=row==0?"":Json.str(entries().get(row-1).getAsJsonObject(),"id");String label=row==0?"Обычный скин":Json.str(entries().get(row-1).getAsJsonObject(),"name");var face=SkinClient.ordinarySkin();if(row>0){var entry=entries().get(row-1).getAsJsonObject();var texture=SkinClient.texture(Json.str(entry,"hash"));if(texture!=null)face=new PlayerSkin(texture,null,null,null,entry.get("slim").getAsBoolean()?PlayerSkin.Model.SLIM:PlayerSkin.Model.WIDE,false);}PlayerFaceRenderer.draw(g,face,left+7,47+(row-firstRow)*72,16);g.drawString(font,font.plainSubstrByWidth(label,listWidth-36),left+28,50+(row-firstRow)*72,0xFFFFFF);if(id.equals(selection))g.renderOutline(left+1,44+(row-firstRow)*72,listWidth-2,70,0xFFE2BE75);}
  if(previewPlayer!=null){previewPlayer.yBodyRot=180;previewPlayer.setYRot(180);previewPlayer.yHeadRot=180;previewPlayer.yHeadRotO=180;g.enableScissor(right,48,width-left,previewBottom);InventoryScreen.renderEntityInInventory(g,(right+width-left)/2f,(48+previewBottom)/2f,Math.max(18,Math.min(70,(previewBottom-58)/2.2f)),new org.joml.Vector3f(0,previewPlayer.getBbHeight()/2,0),new org.joml.Quaternionf().rotateZ((float)Math.PI).rotateY(rotation),null,previewPlayer);g.disableScissor();}String text=message.isEmpty()?SkinClient.status:message;if(!text.isEmpty())g.drawCenteredString(font,font.plainSubstrByWidth(text,width-24),width/2,height-68,0xE2BE75);
 }
 @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(button==0&&x>=right&&y>=48&&y<height-74){rotation+=(float)dx*0.02f;return true;}return super.mouseDragged(x,y,button,dx,dy);}
 @Override public void onClose(){minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
