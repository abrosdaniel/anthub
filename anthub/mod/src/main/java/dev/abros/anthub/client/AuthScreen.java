package dev.abros.anthub.client;

import com.google.gson.*;
import dev.abros.anthub.core.Json;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class AuthScreen extends Screen {
    private EditBox password,repeat,invitation;
    private int panelTop,panelBottom,statusTop;
    private PasswordBox secret(int x,int y,int w,Component label){var field=addRenderableWidget(new PasswordBox(font,x,y,w-26,label));addRenderableWidget(new PasswordVisibilityButton(x+w-22,y,field));return field;}
    private boolean resetting,remember=true,error;
    private String status="";
    private long sent;
    private Button submit;
    AuthScreen(){super(Component.literal("AntHub · Вход"));}
    private boolean registering(){return !resetting&&Json.opt(AuthClient.offer,"type","").equals("new");}
    private String heading(){return resetting?"Восстановление доступа":registering()?"Добро пожаловать":"С возвращением";}
    private String action(){return resetting?"Сохранить новый пароль":registering()?"Создать аккаунт и войти":"Войти на сервер";}
    private Button button(String text,int x,int y,int w,Runnable run){return addRenderableWidget(Button.builder(Component.literal(text),b->run.run()).bounds(x,y,w,20).build());}
    @Override protected void init(){
        int w=Math.min(320,width-32),x=(width-w)/2;panelTop=Math.max(6,(height-254)/2);int y=panelTop+46;
        String first=password==null?"":password.getValue(),second=repeat==null?"":repeat.getValue(),code=invitation==null?"":invitation.getValue();
        repeat=null;invitation=null;
        password=secret(x,y,w,Component.literal(resetting||registering()?"Новый пароль, "+AuthClient.minimumPasswordLength()+"–128 символов":"Ваш пароль"));
        password.setHint(Component.literal(resetting||registering()?"Придумайте пароль · от "+AuthClient.minimumPasswordLength()+" символов":"Введите пароль"));password.setValue(first);y+=22;
        if(resetting||registering()){repeat=secret(x,y,w,Component.literal("Повторите пароль"));repeat.setHint(Component.literal("Повторите новый пароль"));repeat.setValue(second);y+=22;}
        if(resetting){invitation=secret(x,y,w,Component.literal("Код от администратора"));invitation.setHint(Component.literal("Код приглашения от администратора"));invitation.setValue(code);y+=22;}
        else{button((remember?"☑ ":"☐ ")+"Запомнить меня · 30 дней",x,y,w,()->{remember=!remember;rebuildWidgets();});y+=22;}
        submit=button(action(),x,y,w,this::submit);y+=22;
        if(!registering()){button(resetting?"Вернуться ко входу":"Не помню пароль",x,y,w,()->{resetting=!resetting;clearSecrets();error=false;status=resetting?"Попросите администратора выдать приглашение для восстановления.":"";rebuildWidgets();});y+=22;}
        if(!resetting&&AuthClient.officialLauncher()&&Json.opt(AuthClient.offer,"mode","").equals("hybrid")&&AuthClient.offer.has("linked")&&AuthClient.offer.get("linked").getAsBoolean()){button("Войти через аккаунт Minecraft",x,y,w,()->{waiting("Проверяем аккаунт Minecraft…");AuthClient.official();});y+=22;}
        statusTop=y+4;panelBottom=y+68;button("Отключиться",x,panelBottom-26,w,this::onClose);
        setInitialFocus(password);setBusy(sent>0);
    }
    private void clearSecrets(){if(password!=null)password.setValue("");if(repeat!=null)repeat.setValue("");if(invitation!=null)invitation.setValue("");}
    private void setBusy(boolean value){for(var child:children())if(child instanceof AbstractWidget widget)widget.active=!value;for(var child:children())if(child instanceof Button b&&b.getMessage().getString().equals("Отключиться"))b.active=true;if(submit!=null)submit.setMessage(Component.literal(value?"Проверяем…":action()));}
    private void waiting(String text){sent=System.currentTimeMillis();error=false;status=text;setBusy(true);}
    private void fail(String text,EditBox field){error=true;status=text;setFocused(field);}
    private void submit(){
        if(submit==null||!submit.active)return;
        String value=password.getValue();
        if(value.isEmpty()){fail("Введите пароль, чтобы продолжить.",password);return;}
        if((resetting||registering())&&value.length()<AuthClient.minimumPasswordLength()){fail("Нужно не менее "+AuthClient.minimumPasswordLength()+" символов.",password);return;}
        if((resetting||registering())&&!value.equals(repeat.getValue())){fail("Пароли не совпадают. Проверьте повторный ввод.",repeat);return;}
        if(resetting&&invitation.getValue().isBlank()){fail("Введите приглашение, полученное от администратора.",invitation);return;}
        var j=new JsonObject();j.addProperty("action",resetting?"reset":registering()?"register":"login");j.addProperty("password",value);j.addProperty("remember",remember);j.addProperty("label",System.getProperty("os.name","Компьютер"));if(resetting)j.addProperty("invitation",invitation.getValue());
        clearSecrets();waiting("Проверяем данные…");AuthClient.send(j);
    }
    void receive(JsonObject j){sent=0;error=Json.opt(j,"kind","").equals("error");status=Json.opt(j,"text","");if(Json.opt(j,"kind","").equals("resetDone")){resetting=false;AuthClient.offer.addProperty("type","local");AuthClient.forget();clearSecrets();status="Пароль изменён. Войдите с новым паролем.";rebuildWidgets();}setBusy(false);setFocused(password);}
    @Override public void tick(){if(sent>0&&System.currentTimeMillis()-sent>15000){sent=0;setBusy(false);error=true;status="Ответ задерживается. Повторите попытку или отключитесь.";}}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if((key==257||key==335)&&getFocused() instanceof EditBox){if(getFocused()==password&&repeat!=null)setFocused(repeat);else if(getFocused()==repeat&&invitation!=null)setFocused(invitation);else submit();return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public void onClose(){clearSecrets();AuthClient.cancel();}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float delta){super.renderBackground(g,x,y,delta);int w=Math.min(344,width-12),left=(width-w)/2;g.fill(left,panelTop,left+w,panelBottom,0xE5171C22);g.fill(left,panelTop,left+w,panelTop+2,0xFFE2BE75);}
    @Override public void render(GuiGraphics g,int x,int y,float delta){super.render(g,x,y,delta);int w=Math.min(320,width-32),left=(width-w)/2;g.drawCenteredString(font,heading(),width/2,panelTop+12,0xE2BE75);String name=Json.opt(AuthClient.offer,"name","");g.drawCenteredString(font,font.plainSubstrByWidth(name+" · "+(resetting?"Новый пароль":registering()?"Создание аккаунта":"Вход в аккаунт"),w),width/2,panelTop+29,0xCCD4DE);if(error)g.fill(left-4,statusTop,left-2,panelBottom-30,0xFFFF8A80);Ui.status(g,font,status.isEmpty()?"Защищённое подключение · вход до загрузки мира":status,left,statusTop,w,panelBottom-30);}
}
