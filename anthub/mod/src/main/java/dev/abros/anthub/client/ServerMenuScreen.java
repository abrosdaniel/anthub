package dev.abros.anthub.client;

import com.google.gson.*;
import dev.abros.anthub.core.Json;
import dev.abros.anthub.core.RequestSession;
import dev.abros.anthub.core.UiPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import java.util.*;

final class ServerMenuScreen extends ScrollScreen implements CommunityScreen.Receiver {
    private final MenuSidebar nav=new MenuSidebar(this);private final Screen parent;private String tab="help",adminGroup="overview";private final ContentPane content=new ContentPane();
    
    ServerMenuScreen(Screen parent,String tab){super(Client.tr("server.menu"));this.parent=parent;this.tab=tab;}
    private String stateKey="";
    private final RequestSession overviewSession=new RequestSession();private JsonObject overviewData=new JsonObject();private boolean overviewLoaded,overviewBusy,refreshOnReturn;private String overviewNotice="";private Button overviewRefresh;
    private record Summary(String title,String value,List<String> details,int accent,Runnable action){}
    private final List<Summary> summaries=new ArrayList<>();
    private record DashboardSection(String title,List<Summary> items){}
    private final List<DashboardSection> dashboardSections=new ArrayList<>();
    private void section(String title){if(summaries.isEmpty())dashboardSections.add(new DashboardSection(title,List.of()));else for(int i=0;i<summaries.size();i+=columns())dashboardSections.add(new DashboardSection(title,List.copyOf(summaries.subList(i,Math.min(summaries.size(),i+columns())))));summaries.clear();}

    private String metric(String key){return dev.abros.anthub.core.DisplayCounts.text(overviewData,key,overviewBusy?"…":"—");}
    private void group(String group){adminGroup=group;resetScroll();rebuildWidgets();}
    private void loadOverview(){if(overviewBusy||!ServerMenuClient.admin()||!ServerMenuClient.supports("player-tools"))return;overviewLoaded=true;overviewBusy=true;if(overviewRefresh!=null)overviewRefresh.active=false;overviewNotice="Обновляем сводку…";var q=new JsonObject();q.addProperty("action","adminDashboard");ServerMenuClient.request(overviewSession.begin(q,false,System.currentTimeMillis()));}
    public void receiveCommunity(JsonObject response){if(!overviewSession.receive(response))return;overviewBusy=false;overviewNotice=response.has("error")?Json.opt(response,"text","Сводка недоступна"):"";if(overviewRefresh!=null)overviewRefresh.active=true;if(!response.has("error")&&!UiPayload.same(overviewData,response)){overviewData=response.deepCopy();rebuildWidgets();}}

    private boolean stateFlag(String key){return ServerMenuClient.state.has(key)&&ServerMenuClient.state.get(key).getAsBoolean();}
    private boolean scheduled(){return ServerMenuClient.state.has("restartAt")&&ServerMenuClient.state.get("restartAt").getAsLong()>0;}
    private String modeKey(){return stateFlag("maintenance")+":"+scheduled()+":"+stateFlag("pinned")+":"+dev.abros.anthub.core.DisplayCounts.text(ServerMenuClient.state,"online","");}
    private int columns(){return width-nav.left()-14>=440?2:1;}
    private void card(String title,String value,String first,String second,int accent,Runnable action){summaries.add(new Summary(title,value,List.of(first,second),accent,action));}
    private void export(String section){var q=new JsonObject();q.addProperty("action","exportCommunity");q.addProperty("section",section);ServerMenuClient.request(q);}
    private String sectionTitle(){return switch(adminGroup){case "server"->"Управление сервером";case "messages"->"Объявления";case "data"->"Экспорт данных";default->"Администрирование";};}
    private void button(String key,int x,int y,int w,Runnable action){addRenderableWidget(Button.builder(Client.tr(key),b->action.run()).bounds(x,y,w,20).build());}
    @Override protected void init(){
        nav.build(tab,this::addRenderableWidget);
        if(tab.equals("admin")&&!ServerMenuClient.staff()){CommunityScreen.open(parent,"home");return;}
        content.bounds(nav.left(),70,width-nav.left()-14,Math.max(30,height-145));
        if(tab.equals("help")){if(System.getenv("ANTHUB_PILOT_UI")!=null)addRenderableWidget(Button.builder(Component.literal("UI Kit"),b->minecraft.setScreen(new UiKitShowcaseScreen(this))).bounds(width-94,42,80,20).build());addRenderableWidget(Button.builder(Component.literal("Интерфейс и голос…"),b->minecraft.setScreen(new ChoicePopup(this,"Настройки клиента",List.of("Доступность интерфейса","Диагностика Plasmo Voice"),i->minecraft.setScreen(i==0?new AccessibilityScreen(this):new VoiceDiagnosticsScreen(this))))).bounds(nav.left(),42,Math.min(180,width-nav.left()-14),20).build());button("server.report",nav.left(),height-72,Math.min(180,(width-nav.left()-22)/2),()->minecraft.setScreen(new ReportScreen(this)));button("server.myReports",nav.left()+(width-nav.left())/2,height-72,Math.min(180,(width-nav.left()-22)/2),()->FeatureListScreen.open(this,"myReports"));}
        if(tab.equals("admin")){
            int left=nav.left(),available=width-left-14;
            boolean server=ServerMenuClient.may("anthub.announce")||ServerMenuClient.may("anthub.maintenance")||ServerMenuClient.may("anthub.restart");
            if(!adminGroup.equals("overview"))addRenderableWidget(Button.builder(Component.literal("К обзору"),b->group("overview")).bounds(left,42,100,20).build());
            summaries.clear();stateKey=modeKey();
            dashboardSections.clear();
            if(adminGroup.equals("overview")){
                section("Сводка на сейчас");
                if(ServerMenuClient.may("anthub.reports")){
                    if(overviewData.has("attentionReports"))for(var entry:overviewData.getAsJsonArray("attentionReports")){var report=entry.getAsJsonObject();card(Json.opt(report,"player","Игрок"),Json.opt(report,"priority","normal").equals("high")?"Высокий приоритет":"Ожидает ответа",Json.opt(report,"message",""),Json.opt(report,"assignedName","").isBlank()?"Ответственный не назначен":"Ответственный: "+Json.opt(report,"assignedName",""),0xFFEF7777,()->minecraft.setScreen(new ReportDetailScreen(this,report,true)));}
                    if(summaries.isEmpty())card("Обращения игроков",overviewBusy?"Загрузка…":metric("openReports").equals("0")?"Все обращения обработаны":"Открыть очередь","Назначение ответственного и ответы","Открытые и закрытые обращения",0xFFEF7777,()->FeatureListScreen.open(this,"reports"));
                    section("Требуют внимания · приоритетные и самые старые");
                }
                if(server)card("Сервер",stateFlag("maintenance")?"Обслуживание":"Вход открыт",scheduled()?"Остановка запланирована":"Остановка не запланирована",stateFlag("pinned")?"Объявление закреплено":"Нет закреплённого объявления",0xFF79CBA6,()->group("server"));
                if(ServerMenuClient.admin()&&ServerMenuClient.supports("player-tools"))card("Состояние AntHub",overviewData.has("recentErrors")?overviewData.getAsJsonArray("recentErrors").isEmpty()?"Ошибок не зафиксировано":overviewData.getAsJsonArray("recentErrors").size()+" последних ошибок":"Нет данных об ошибках","Очередь записи: "+metric("storageQueue"),"Очередь чтения: "+metric("readQueue"),0xFFB49AE8,()->minecraft.setScreen(new AdminDashboardScreen(this,true)));
                else if(ServerMenuClient.may("anthub.diagnostics"))card("Состояние AntHub","Проверка подключений","Состояние мода и интеграций","Получить текущий отчёт",0xFFB49AE8,()->ServerMenuClient.request("diagnostics"));
                if(!summaries.isEmpty())section("Сервер и обслуживание");
                if(ServerMenuClient.admin()&&ServerMenuClient.supports("community-extensions"))card("Права ролей","Предпросмотр","Какие разделы и действия доступны","Без смены вашей роли",0xFFB49AE8,()->minecraft.setScreen(new RolePreviewScreen(this)));
                if(ServerMenuClient.admin())card("Журнал действий","История администрации","Кто и что изменил","Открыть записи и подробности",0xFF82B6F2,()->FeatureListScreen.open(this,"history"));
                if(ServerMenuClient.admin()&&ServerMenuClient.supports("admin-tools"))card("Данные сообщества","Экспорт","Выберите нужный раздел","Выгрузка данных с сервера",0xFFE2BE75,()->group("data"));
                if(!summaries.isEmpty())section("Инструменты администрации");
            }else if(adminGroup.equals("server")){
                if(ServerMenuClient.may("anthub.maintenance"))card("Вход игроков",stateFlag("maintenance")?"Обслуживание включено":"Открыт",stateFlag("maintenance")?Json.opt(ServerMenuClient.state,"maintenanceReason",""):"Игроки могут подключаться",stateFlag("maintenance")?"Нажмите, чтобы завершить":"Включить обслуживание с причиной",0xFF79CBA6,()->action(stateFlag("maintenance")?"maintenanceOff":"maintenance"));
                if(ServerMenuClient.may("anthub.restart"))card("Остановка сервера",scheduled()?"Запланирована":"Не запланирована","Перезапуск выполняется панелью",scheduled()?"Отменить запланированную остановку":"Указать время и причину остановки",0xFFEF7777,()->action(scheduled()?"restartOff":"restart"));
                if(ServerMenuClient.may("anthub.announce"))card("Объявления",stateFlag("pinned")?"Есть закреплённое":"Нет закреплённого","Разовое сообщение всем игрокам","Сообщение на главной AntHub",0xFFE2BE75,()->group("messages"));
                if(ServerMenuClient.admin()&&ServerMenuClient.supports("player-tools"))card("Настройки сервера","Просмотр и изменения","Проверка значений перед применением","Изменения с подтверждением",0xFF82B6F2,()->minecraft.setScreen(new ServerSettingsReviewScreen(this)));
            }else if(adminGroup.equals("messages")&&ServerMenuClient.may("anthub.announce")){
                card("Разовое объявление","Отправить игрокам","Текст появится у всех игроков","Сообщение и подтверждение отправки",0xFFE2BE75,()->action("announce"));
                if(ServerMenuClient.supports("admin-tools")){
                    card("Сообщение на главной",stateFlag("pinned")?"Изменить закреплённое":"Закрепить объявление",stateFlag("pinned")?Json.opt(ServerMenuClient.state,"pinnedText",""):"Показывается на главной AntHub","Укажите текст и срок показа",0xFF79CBA6,()->action("pinAnnouncement"));
                    if(stateFlag("pinned"))card("Снять объявление","Убрать с главной","Текущее сообщение перестанет показываться","Действие потребует подтверждения",0xFFEF7777,()->minecraft.setScreen(new ConfirmScreen(yes->{minecraft.setScreen(this);if(yes){var q=new JsonObject();q.addProperty("action","pinAnnouncement");q.addProperty("text","");q.addProperty("minutes",1);ServerMenuClient.request(q);}},Component.literal("Снять объявление?"),Component.literal("Сообщение исчезнет с главной AntHub."))));
                }
            }else if(adminGroup.equals("data")&&ServerMenuClient.admin()&&ServerMenuClient.supports("admin-tools")){
                String[] sections={"","board","groups","events","polls","ideas"},labels={"Все разделы","Доска объявлений","Объединения","События","Голосования","Предложения"};
                for(int i=0;i<sections.length;i++){String section=sections[i];card(labels[i],"Выгрузить данные","Экспорт выбранного раздела","Файл создаётся на сервере",0xFFE2BE75,()->export(section));}
            }
            int columns=columns(),tileWidth=(available-8*(columns-1))/columns;
            if(adminGroup.equals("overview")){
                scrollArea(dashboardSections.size(),42,height-64,112,width-10);
                for(int row=firstRow;row<Math.min(dashboardSections.size(),firstRow+visibleRows);row++){
                    int y=42+(row-firstRow)*112;var section=dashboardSections.get(row);
                    if(row==0){String[] keys={"openReports","unassignedReports","highReports"},labels={"Открытые обращения","Без ответственного","Высокий приоритет"};int statWidth=(available-8)/3;for(int i=0;i<3;i++){String key=keys[i];if(ServerMenuClient.may("anthub.reports"))addRenderableWidget(new UiMetricCard(left+i*(statWidth+4),y+18,statWidth,labels[i],metric(key),()->minecraft.setScreen(ServerMenuClient.supports("community-extensions")?new ReportQueueScreen(this,key.equals("unassignedReports")?"unassigned":key.equals("highReports")?"high":"all"):new FeatureListScreen(this,"reports"))));}if(ServerMenuClient.may("anthub.reports"))addRenderableWidget(Button.builder(Component.literal("Все обращения"),b->FeatureListScreen.open(this,"reports")).bounds(left,y+80,110,20).build());continue;}
                    int count=section.items.size(),cardWidth=(available-8*(Math.min(count,columns)-1))/Math.max(1,Math.min(count,columns));
                    for(int n=0;n<Math.min(count,columns);n++){var item=section.items.get(n);addRenderableWidget(new UiSummaryCard(left+n*(cardWidth+8),y+18,cardWidth,item.title,item.value,item.details,item.accent,item.action));}
                }
                if(ServerMenuClient.admin()&&ServerMenuClient.supports("player-tools")){overviewRefresh=addRenderableWidget(Button.builder(Component.literal("Обновить"),b->loadOverview()).bounds(left,height-28,90,20).build());overviewRefresh.active=!overviewBusy;}
            }else{
                scrollArea((summaries.size()+columns-1)/columns,76,height-64,96,width-10);
                for(int n=firstRow*columns;n<Math.min(summaries.size(),(firstRow+visibleRows)*columns);n++){var item=summaries.get(n);addRenderableWidget(new UiSummaryCard(left+n%columns*(tileWidth+8),76+(n/columns-firstRow)*96,tileWidth,item.title,item.value,item.details,item.accent,item.action));}
            }

        }
        button("back",width-114,height-28,100,()->onClose());
        if(tab.equals("admin")&&adminGroup.equals("overview")&&(!overviewLoaded||refreshOnReturn)){refreshOnReturn=false;loadOverview();}
    }
    private void action(String name){
        if(name.equals("maintenanceOff")||name.equals("restartOff")){
            var j=new JsonObject();j.addProperty("text","");
            if(name.equals("maintenanceOff")){j.addProperty("action","maintenance");j.addProperty("enabled",false);j.addProperty("minutes",0);}
            else {j.addProperty("action","restart");j.addProperty("seconds",0);}
            minecraft.setScreen(new ConfirmScreen(yes->{minecraft.setScreen(this);if(yes)ServerMenuClient.request(j);},Client.tr("server.confirm"),Client.tr("server."+name)));
        }else minecraft.setScreen(new ActionForm(this,name));
    }
    @Override public void removed(){refreshOnReturn=true;}
    void refreshPermissions(){if(!ServerMenuClient.admin()){overviewSession.cancel();overviewBusy=false;overviewData=new JsonObject();}rebuildWidgets();}
    @Override public void tick(){if(tab.equals("admin")&&!ServerMenuClient.staff()){CommunityScreen.open(parent,"home");}else if(tab.equals("admin")&&!stateKey.equals(modeKey()))rebuildWidgets();if(overviewSession.timeout(System.currentTimeMillis())){overviewBusy=false;overviewNotice="Нет ответа. Обновите сводку.";if(overviewRefresh!=null)overviewRefresh.active=true;}}
    private String body(){var j=ServerMenuClient.state;
        if(tab.equals("help"))return Json.opt(j,"help","")+"\n\n"+Client.tr("server.helptext").getString();
        return Client.tr("server.admintext").getString();
    }
    @Override public void render(GuiGraphics g,int x,int y,float d){super.render(g,x,y,d);UiHeading.page(g,font,Component.literal(tab.equals("admin")?sectionTitle():ServerMenuClient.header()),width);content.text(body());if(!tab.equals("admin"))content.render(g,font);
        if(tab.equals("admin")&&adminGroup.equals("overview"))for(int row=firstRow;row<Math.min(dashboardSections.size(),firstRow+visibleRows);row++)Ui.text(g,font,UiKit.fit(font,dashboardSections.get(row).title,width-nav.left()-20),nav.left(),42+(row-firstRow)*112,UiKit.muted(),false);
        Ui.status(g,font,adminGroup.equals("overview")&&!overviewNotice.isEmpty()?overviewNotice:ServerMenuClient.result,nav.left(),height-48,width-28,height-32);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(!tab.equals("admin")&&(key==266||key==267)){content.scroll(nav.left()+8,106,key==266?6:-6);return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(nav.scroll(x,dy)){rebuildWidgets();return true;}return !tab.equals("admin")&&content.scroll(x,y,dy)||super.mouseScrolled(x,y,dx,dy);}
    @Override public boolean mouseClicked(double x,double y,int b){if(b==0){if(content.click(x,y))return true;var style=content.link(font,x,y);if(style!=null&&style.getClickEvent()!=null)return handleComponentClicked(style);}return super.mouseClicked(x,y,b);}
    @Override public boolean mouseDragged(double x,double y,int b,double dx,double dy){return b==0&&content.drag(y)||super.mouseDragged(x,y,b,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int b){content.release();return super.mouseReleased(x,y,b);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){if(tab.equals("admin")&&!adminGroup.equals("overview")){group(adminGroup.equals("messages")?"server":"overview");return;}overviewSession.cancel();minecraft.setScreen(parent);}

    private static final class ActionForm extends Screen {
        private final Screen parent;private final String action;private EditBox message,duration;private String error="";
        ActionForm(Screen parent,String action){super(action.equals("pinAnnouncement")?Component.literal("Закреплённое объявление"):Client.tr("server."+action));this.parent=parent;this.action=action;}
        private int top(){return DialogPanel.top(height,240);}
        private int bottom(){return height-top();}
        private Component durationLabel(){return action.equals("pinAnnouncement")?Component.literal("Минуты (1–10080)"):Client.tr(action.equals("restart")?"server.seconds":"server.minutes");}
        private boolean timed(){return !action.equals("announce");}
        @Override public void renderBackground(GuiGraphics g,int x,int y,float d){super.renderBackground(g,x,y,d);DialogPanel.draw(g,width,Math.min(420,width-40),top(),bottom());}
 @Override protected void init(){
            int w=Math.min(420,width-40),x=(width-w)/2;
            String draft=message==null?(action.equals("pinAnnouncement")?Json.opt(ServerMenuClient.state,"pinnedText",""):""):message.getValue(),value=duration==null?(action.equals("maintenance")?"15":"60"):duration.getValue();
            message=addRenderableWidget(new UiEditBox(font,x,top()+54,w,20,Client.tr("server.message")));message.setMaxLength(500);message.setValue(draft);
            if(timed()){duration=addRenderableWidget(new UiEditBox(font,x,top()+98,100,20,durationLabel()));duration.setMaxLength(5);duration.setFilter(v->v.matches("[0-9]*"));duration.setValue(value);}
            addRenderableWidget(Button.builder(Client.tr("server.confirm"),b->submit()).bounds(x,bottom()-28,w/2-3,20).build());
            addRenderableWidget(Button.builder(Client.tr("back"),b->onClose()).bounds(x+w/2+3,bottom()-28,w/2-3,20).build());
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
            UiHeading.dialog(g,font,title,left,top(),Math.min(420,width-40));Ui.text(g,font,Client.tr("server.message"),left,top()+40,AccessibilityScreen.foreground(UiPalette.color(0xEEEEEE)));
            if(timed())Ui.text(g,font,durationLabel(),left,top()+84,AccessibilityScreen.foreground(UiPalette.color(0xEEEEEE)));
            Ui.status(g,font,error.isEmpty()?(action.equals("pinAnnouncement")?"Сообщение появится на главной до окончания указанного срока.":Client.tr("server.form."+action).getString()):error,left,top()+(timed()?130:90),Math.min(420,width-40),bottom()-36);
        }
        @Override public void tick(){if(!ServerMenuClient.available())minecraft.setScreen(null);else if(!ServerMenuClient.may("anthub."+(action.equals("pinAnnouncement")?"announce":action)))minecraft.setScreen(parent);}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void onClose(){minecraft.setScreen(parent);}
    }

}
