package dev.abros.anthub.client;

import com.google.gson.*;
import dev.abros.anthub.core.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;

final class AuthAccountScreen extends ScrollScreen {
    private final Screen parent;private JsonArray devices=new JsonArray();private String status="",type="",currentDevice="";private JsonObject invite;private EditBox oldPassword,password,repeat;private boolean changing,linked;private String mode="",linkAction="";private long refreshAfter;
    AuthAccountScreen(Screen parent){super(Component.literal("Безопасность аккаунта"));this.parent=parent;}
    static Screen invitation(Screen parent,JsonObject invitation){var screen=new AuthAccountScreen(parent);screen.invite=invitation;return screen;}
    static void open(Screen parent){Minecraft.getInstance().setScreen(new AuthAccountScreen(parent));requestDevices();}
    private static void requestDevices(){var j=new JsonObject();j.addProperty("action","devices");AuthClient.send(j);}
    static void invite(Screen parent,String target){Minecraft.getInstance().setScreen(new UiConfirmDialog(yes->{Minecraft.getInstance().setScreen(parent);if(yes){var j=new JsonObject();j.addProperty("action","invite");j.addProperty("target",target);AuthClient.send(j);}},Component.literal("Создать приглашение для "+target+"?"),Component.literal("Передавайте приглашение только после проверки владельца аккаунта. Оно позволяет установить новый пароль.")));}
    private PasswordBox secret(int x,int y,int w,String label){return UiFields.password(font,x,y,w,Component.literal(label),this::addRenderableWidget);}
    private int panelTop(){return UiDialog.top(height,300);}
 private int panelBottom(){return height-panelTop();}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.draw(g,width,Math.min(460,width-40),panelTop(),panelBottom());}
 @Override protected void init(){int w=Math.min(460,width-40),x=(width-w)/2;
        if(invite!=null){var code=addRenderableWidget(UiFields.text(font,x,panelTop()+90,w,20,Component.literal("Код восстановления")));code.setMaxLength(128);code.setValue(Json.str(invite,"token"));code.setEditable(false);addRenderableWidget(UiActions.button(Component.literal("Скопировать код"),UiActions.Tone.NORMAL,"",b->{if(System.currentTimeMillis()<invite.get("expires").getAsLong()){minecraft.keyboardHandler.setClipboard(Json.str(invite,"token"));status="Скопировано. Передайте владельцу лично";}else status="Приглашение истекло";}).bounds(x,panelTop()+118,w,20).build());}
        else if(!linkAction.isEmpty()){
            password=secret(x,panelTop()+70,w,"Текущий пароль сервера");password.setHint(Component.literal("Текущий пароль сервера"));
            addRenderableWidget(UiActions.button(Component.literal(linkAction.equals("link")?"Подтвердить привязку":"Подтвердить отвязку"),UiActions.Tone.PRIMARY,UiIcons.CHECK,b->{
                if(password.getValue().isEmpty()){status="Введите текущий пароль сервера";return;}String value=password.getValue();password.setValue("");
                if(linkAction.equals("link"))AuthClient.link(value);else{var request=new JsonObject();request.addProperty("action","unlink");request.addProperty("password",value);AuthClient.send(request);}status="Проверка…";
            }).bounds(x,panelTop()+100,w,20).build());setInitialFocus(password);
        }
        else if(changing){oldPassword=secret(x,panelTop()+40,w,"Текущий пароль");oldPassword.setHint(Component.literal("Текущий пароль"));password=secret(x,panelTop()+74,w,"Новый пароль");password.setHint(Component.literal("Новый пароль · от "+AuthClient.minimumPasswordLength()+" символов"));repeat=secret(x,panelTop()+108,w,"Повтор нового пароля");repeat.setHint(Component.literal("Повтор нового пароля"));addRenderableWidget(UiActions.button(Component.literal("Изменить пароль и отозвать все сеансы"),UiActions.Tone.NORMAL,"",b->{if(password.getValue().length()<AuthClient.minimumPasswordLength()){status="Нужно не менее "+AuthClient.minimumPasswordLength()+" символов";return;}if(!password.getValue().equals(repeat.getValue())){status="Пароли не совпадают";return;}var j=new JsonObject();j.addProperty("action","change");j.addProperty("oldPassword",oldPassword.getValue());j.addProperty("password",password.getValue());oldPassword.setValue("");password.setValue("");repeat.setValue("");AuthClient.send(j);status="Проверка…";}).bounds(x,panelTop()+145,w,20).build());setInitialFocus(oldPassword);}
        else {scrollArea(devices.size(),new dev.abros.anthub.core.NativeLayout.Box(x,panelTop()+50,Math.max(0,w),Math.max(0,(panelBottom()-140)-(panelTop()+50))),36);for(int i=firstRow;i<Math.min(devices.size(),firstRow+visibleRows);i++){var device=devices.get(i).getAsJsonObject();addRenderableWidget(UiActions.button(Component.literal(Json.str(device,"label")+(Json.str(device,"id").equals(currentDevice)?" (это устройство)":"")+" · Отозвать"),UiActions.Tone.NORMAL,"",b->revoke(Json.str(device,"id"))).bounds(x,panelTop()+50+(i-firstRow)*36,w,20).build());}
            if(mode.equals("hybrid")){var link=addRenderableWidget(UiActions.button(Component.literal(linked?"Отвязать Minecraft-аккаунт":"Привязать Minecraft-аккаунт"),UiActions.Tone.NORMAL,"",b->{linkAction=linked?"unlink":"link";status="Подтвердите действие паролем сервера";rebuildWidgets();}).bounds(x,panelBottom()-128,w,20).build());link.active=linked||AuthClient.officialLauncher();}
            var change=addRenderableWidget(UiActions.button(Component.literal("Сменить пароль"),UiActions.Tone.NORMAL,"",b->{changing=true;rebuildWidgets();}).bounds(x,panelBottom()-100,w,20).build());change.active=type.equals("local");addRenderableWidget(UiActions.button(Component.literal("Отозвать все устройства и сеансы"),UiActions.Tone.NORMAL,"",b->revoke("all")).bounds(x,panelBottom()-76,w,20).build());}
        UiActions.close(new dev.abros.anthub.core.NativeLayout.Box(x,panelBottom()-26,w,20),this::addRenderableWidget,this::onClose);
    }
    private void revoke(String id){minecraft.setScreen(new UiConfirmDialog(yes->{minecraft.setScreen(this);if(yes){var j=new JsonObject();j.addProperty("action","revoke");j.addProperty("device",id);AuthClient.send(j);}},Component.literal("Отозвать доступ?"),Component.literal("Сеансы выбранного устройства будут завершены.")));}
    void receive(JsonObject j){status=Json.opt(j,"text","");if(Json.opt(j,"kind","").equals("devices")){devices=j.getAsJsonArray("devices");linked=j.has("linked")&&j.get("linked").getAsBoolean();mode=Json.opt(j,"mode","");type=Json.str(j,"type");currentDevice=Json.opt(j,"current","");rebuildWidgets();}else if(Json.opt(j,"kind","").equals("updated")){linkAction="";refreshAfter=System.currentTimeMillis()+800;rebuildWidgets();}}
    @Override public void tick(){if(!AuthClient.available()){minecraft.setScreen(null);return;}if(refreshAfter>0&&System.currentTimeMillis()>=refreshAfter){refreshAfter=0;requestDevices();}}
    @Override public void onClose(){if(!linkAction.isEmpty()){if(password!=null)password.setValue("");linkAction="";status="";rebuildWidgets();}else if(changing){changing=false;rebuildWidgets();}else {if(invite!=null)invite.remove("token");minecraft.setScreen(parent);}}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int x,int y,float delta){UiDialog.render(parent,this,g,delta,()->{super.render(g,x,y,delta);UiHeading.dialog(g,font,title,(width-(Math.min(460,width-40)))/2,panelTop(),Math.min(460,width-40));if(invite!=null){Ui.centered(g,font,"Сброс доступа: "+Json.str(invite,"target"),width/2,panelTop()+50,0xFFFFFF);Ui.centered(g,font,"Осталось секунд: "+Math.max(0,(invite.get("expires").getAsLong()-System.currentTimeMillis())/1000),width/2,panelTop()+70,UiPalette.color(0xAAAAAA));}Ui.status(g,font,status,(width-Math.min(460,width-40))/2,panelBottom()-52,Math.min(460,width-40),panelBottom()-28);});}
}
