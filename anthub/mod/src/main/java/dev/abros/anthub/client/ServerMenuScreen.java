package dev.abros.anthub.client;

import com.google.gson.*;
import dev.abros.anthub.core.Json;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import java.util.*;

final class ServerMenuScreen extends ScrollScreen {
    private final MenuSidebar nav=new MenuSidebar(this);private final Screen parent;private String tab="help",adminGroup="reports";private final ContentPane content=new ContentPane();
    
    ServerMenuScreen(Screen parent,String tab){super(Client.tr("server.menu"));this.parent=parent;this.tab=tab;}
    private record AdminRow(String label,List<String> captions,List<Runnable> actions){}
    private final List<AdminRow> adminRows=new ArrayList<>();
    private String stateKey="";
    private boolean stateFlag(String key){return ServerMenuClient.state.has(key)&&ServerMenuClient.state.get(key).getAsBoolean();}
    private boolean scheduled(){return ServerMenuClient.state.has("restartAt")&&ServerMenuClient.state.get("restartAt").getAsLong()>0;}
    private String modeKey(){return stateFlag("maintenance")+":"+scheduled()+":"+stateFlag("pinned");}
    private void row(String label,String caption,Runnable action){adminRows.add(new AdminRow(label,List.of(caption),List.of(action)));}
    private void button(String key,int x,int y,int w,Runnable action){addRenderableWidget(Button.builder(Client.tr(key),b->action.run()).bounds(x,y,w,20).build());}
    @Override protected void init(){
        nav.build(tab,this::addRenderableWidget);
        if(tab.equals("admin")&&!ServerMenuClient.staff()){CommunityScreen.open(parent,"home");return;}
        content.bounds(nav.left(),70,width-nav.left()-14,Math.max(30,height-145));
        if(tab.equals("help")){addRenderableWidget(Button.builder(Component.literal("Интерфейс и голос…"),b->minecraft.setScreen(new ChoicePopup(this,"Настройки клиента",List.of("Доступность интерфейса","Диагностика Plasmo Voice"),i->minecraft.setScreen(i==0?new AccessibilityScreen(this):new VoiceDiagnosticsScreen(this))))).bounds(nav.left(),42,Math.min(180,width-nav.left()-14),20).build());button("server.report",nav.left(),height-72,Math.min(180,(width-nav.left()-22)/2),()->minecraft.setScreen(new ReportScreen(this)));button("server.myReports",nav.left()+(width-nav.left())/2,height-72,Math.min(180,(width-nav.left()-22)/2),()->FeatureListScreen.open(this,"myReports"));}
        if(tab.equals("admin")){
            int left=nav.left(),available=width-left-14,third=(available-8)/3;
            var groups=new ArrayList<String>();if(ServerMenuClient.may("anthub.reports"))groups.add("reports");if(ServerMenuClient.may("anthub.announce")||ServerMenuClient.may("anthub.maintenance")||ServerMenuClient.may("anthub.restart"))groups.add("server");if(ServerMenuClient.admin()||ServerMenuClient.may("anthub.diagnostics"))groups.add("journal");
            if(!groups.contains(adminGroup)&&!groups.isEmpty())adminGroup=groups.getFirst();
            for(int i=0;i<groups.size();i++){String group=groups.get(i);String label=switch(group){case "reports"->"Обращения";case "server"->"Сервер";default->"Журнал";};var control=addRenderableWidget(Button.builder(Component.literal(label),b->{adminGroup=group;resetScroll();rebuildWidgets();}).bounds(left+i*(third+4),42,third,20).build());control.active=!group.equals(adminGroup);}
            adminRows.clear();stateKey=modeKey();
            if(adminGroup.equals("reports"))row("Обращения игроков","Открыть очередь",()->FeatureListScreen.open(this,"reports"));
            if(adminGroup.equals("journal")){
                if(ServerMenuClient.admin())row("Действия администрации","Открыть журнал",()->FeatureListScreen.open(this,"history"));
                if(ServerMenuClient.may("anthub.diagnostics"))row("Состояние AntHub и подключений","Диагностика",()->ServerMenuClient.request("diagnostics"));
                if(ServerMenuClient.admin()&&ServerMenuClient.supports("admin-tools"))row("Данные сообщества","Экспорт сообщества",()->ServerMenuClient.request("exportCommunity"));
            }
            if(adminGroup.equals("server")){
                if(ServerMenuClient.may("anthub.announce"))row("Разовое сообщение всем игрокам","Отправить объявление",()->action("announce"));
                if(ServerMenuClient.may("anthub.maintenance"))row(stateFlag("maintenance")?"Обслуживание: "+Json.opt(ServerMenuClient.state,"maintenanceReason",""):"Вход игроков открыт",stateFlag("maintenance")?"Завершить обслуживание":"Включить обслуживание",()->action(stateFlag("maintenance")?"maintenanceOff":"maintenance"));
                if(ServerMenuClient.may("anthub.restart"))row(scheduled()?"Остановка запланирована":"Перезапуск выполняется панелью",scheduled()?"Отменить остановку":"Запланировать остановку",()->action(scheduled()?"restartOff":"restart"));
                if(ServerMenuClient.may("anthub.announce")&&ServerMenuClient.supports("admin-tools")){
                    if(stateFlag("pinned"))adminRows.add(new AdminRow("Закреплённое сообщение",List.of("Изменить","Снять"),List.of(()->action("pinAnnouncement"),()->{var j=new JsonObject();j.addProperty("action","pinAnnouncement");j.addProperty("text","");j.addProperty("minutes",1);ServerMenuClient.request(j);})));
                    else row("Сообщение на главной","Закрепить сообщение",()->action("pinAnnouncement"));
                }
            }
            scrollArea(adminRows.size(),76,height-64,64,width-10);
            for(int row=firstRow;row<Math.min(adminRows.size(),firstRow+visibleRows);row++){
                var item=adminRows.get(row);int y=76+(row-firstRow)*64+24;int bw=Math.min(210,(available-16-4*(item.actions.size()-1))/item.actions.size());
                for(int i=0;i<item.actions.size();i++){var callback=item.actions.get(i);String caption=item.captions.get(i);var control=addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(caption,bw-12)),b->callback.run()).bounds(left+8+i*(bw+4),y,bw,20).build());control.setTooltip(Tooltip.create(Component.literal(caption)));}
            }
        }
        button("back",width-114,height-28,100,()->onClose());
    }
    private void action(String name){
        if(name.equals("maintenanceOff")||name.equals("restartOff")){
            var j=new JsonObject();j.addProperty("text","");
            if(name.equals("maintenanceOff")){j.addProperty("action","maintenance");j.addProperty("enabled",false);j.addProperty("minutes",0);}
            else {j.addProperty("action","restart");j.addProperty("seconds",0);}
            minecraft.setScreen(new ConfirmScreen(yes->{minecraft.setScreen(this);if(yes)ServerMenuClient.request(j);},Client.tr("server.confirm"),Client.tr("server."+name)));
        }else minecraft.setScreen(new ActionForm(this,name));
    }
    void refreshPermissions(){rebuildWidgets();}
    @Override public void tick(){if(tab.equals("admin")&&!ServerMenuClient.staff()){CommunityScreen.open(parent,"home");}else if(tab.equals("admin")&&!stateKey.equals(modeKey()))rebuildWidgets();}
    private String body(){var j=ServerMenuClient.state;
        if(tab.equals("help"))return Json.opt(j,"help","")+"\n\n"+Client.tr("server.helptext").getString();
        return Client.tr("server.admintext").getString();
    }
    @Override public void renderBackground(GuiGraphics g,int x,int y,float d){super.renderBackground(g,x,y,d);if(tab.equals("admin")){
        for(int row=firstRow;row<Math.min(adminRows.size(),firstRow+visibleRows);row++){int top=76+(row-firstRow)*64;g.fill(nav.left(),top,width-12,top+58,AccessibilityScreen.background(0xCE25232A));g.fill(nav.left(),top,nav.left()+3,top+58,AccessibilityScreen.background(0xFFE2BE75));String label=adminRows.get(row).label;
            if(adminGroup.equals("server")&&label.equals("Остановка запланирована"))label+=" · "+Math.max(0,(ServerMenuClient.state.get("restartAt").getAsLong()-System.currentTimeMillis())/1000)+" с";
            if(adminGroup.equals("server")&&label.startsWith("Обслуживание:")){long until=ServerMenuClient.state.has("maintenanceUntil")?ServerMenuClient.state.get("maintenanceUntil").getAsLong():0;label=(until>0?"Ещё "+Math.max(0,(until-System.currentTimeMillis()+59999)/60000)+" мин · ":"Бессрочно · ")+label;}
            g.drawString(font,font.plainSubstrByWidth(label,width-nav.left()-26),nav.left()+8,top+7,0xE2BE75);
        }
    }}
    @Override public void render(GuiGraphics g,int x,int y,float d){super.render(g,x,y,d);g.drawCenteredString(font,font.plainSubstrByWidth(ServerMenuClient.header(),width-20),width/2,18,0xE2BE75);content.text(body());if(!tab.equals("admin"))content.render(g,font);
        Ui.status(g,font,ServerMenuClient.result,nav.left(),height-48,width-28,height-32);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(!tab.equals("admin")&&(key==266||key==267)){content.scroll(nav.left()+8,106,key==266?6:-6);return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(nav.scroll(x,dy)){rebuildWidgets();return true;}return !tab.equals("admin")&&content.scroll(x,y,dy)||super.mouseScrolled(x,y,dx,dy);}
    @Override public boolean mouseClicked(double x,double y,int b){if(b==0){if(content.click(x,y))return true;var style=content.link(font,x,y);if(style!=null&&style.getClickEvent()!=null)return handleComponentClicked(style);}return super.mouseClicked(x,y,b);}
    @Override public boolean mouseDragged(double x,double y,int b,double dx,double dy){return b==0&&content.drag(y)||super.mouseDragged(x,y,b,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int b){content.release();return super.mouseReleased(x,y,b);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(parent);}

    private static final class ActionForm extends Screen {
        private final Screen parent;private final String action;private EditBox message,duration;private String error="";
        ActionForm(Screen parent,String action){super(action.equals("pinAnnouncement")?Component.literal("Закреплённое объявление"):Client.tr("server."+action));this.parent=parent;this.action=action;}
        private Component durationLabel(){return action.equals("pinAnnouncement")?Component.literal("Минуты (1–10080)"):Client.tr(action.equals("restart")?"server.seconds":"server.minutes");}
        private boolean timed(){return !action.equals("announce");}
        @Override protected void init(){
            int w=Math.min(420,width-40),x=(width-w)/2;
            String draft=message==null?(action.equals("pinAnnouncement")?Json.opt(ServerMenuClient.state,"pinnedText",""):""):message.getValue(),value=duration==null?(action.equals("maintenance")?"15":"60"):duration.getValue();
            message=addRenderableWidget(new EditBox(font,x,64,w,20,Client.tr("server.message")));message.setMaxLength(500);message.setValue(draft);
            if(timed()){duration=addRenderableWidget(new EditBox(font,x,108,100,20,durationLabel()));duration.setMaxLength(5);duration.setFilter(v->v.matches("[0-9]*"));duration.setValue(value);}
            addRenderableWidget(Button.builder(Client.tr("server.confirm"),b->submit()).bounds(x,height-28,w/2-3,20).build());
            addRenderableWidget(Button.builder(Client.tr("back"),b->onClose()).bounds(x+w/2+3,height-28,w/2-3,20).build());
            setInitialFocus(message);
        }
        private void submit(){
            int amount=0;if(timed())try{amount=Integer.parseInt(duration.getValue());if(amount<(action.equals("maintenance")?0:1)||amount>(action.equals("maintenance")?1440:action.equals("pinAnnouncement")?10080:86400))throw new NumberFormatException();}catch(NumberFormatException ex){error=Client.tr("server.invalidnumber").getString();return;}
            if(message.getValue().isBlank()){error=Client.tr("server.reportempty").getString();return;}
            var j=new JsonObject();j.addProperty("action",action);j.addProperty("text",message.getValue().strip());
            if(action.equals("pinAnnouncement"))j.addProperty("minutes",amount);else if(action.equals("maintenance")){j.addProperty("enabled",true);j.addProperty("minutes",amount);}else if(action.equals("restart"))j.addProperty("seconds",amount);
            Component summary=Component.literal(message.getValue());if(timed())summary=summary.copy().append(" · "+amount+(action.equals("restart")?" с":" мин"));
            minecraft.setScreen(new ConfirmScreen(yes->{minecraft.setScreen(yes?parent:this);if(yes)ServerMenuClient.request(j);},title,summary));
        }
        @Override public void render(GuiGraphics g,int x,int y,float d){super.render(g,x,y,d);int left=(width-Math.min(420,width-40))/2;
            g.drawCenteredString(font,title,width/2,20,0xE2BE75);g.drawString(font,Client.tr("server.message"),left,50,AccessibilityScreen.foreground(0xEEEEEE));
            if(timed())g.drawString(font,durationLabel(),left,94,AccessibilityScreen.foreground(0xEEEEEE));
            Ui.status(g,font,error.isEmpty()?(action.equals("pinAnnouncement")?"Сообщение появится на главной до окончания указанного срока.":Client.tr("server.form."+action).getString()):error,left,timed()?140:100,Math.min(420,width-40),height-36);
        }
        @Override public void tick(){if(!ServerMenuClient.available())minecraft.setScreen(null);else if(!ServerMenuClient.may("anthub."+(action.equals("pinAnnouncement")?"announce":action)))minecraft.setScreen(parent);}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void onClose(){minecraft.setScreen(parent);}
    }

}
