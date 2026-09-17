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
    private void button(String key,int x,int y,int w,Runnable action){addRenderableWidget(Button.builder(Client.tr(key),b->action.run()).bounds(x,y,w,20).build());}
    @Override protected void init(){
        nav.build(tab,this::addRenderableWidget);
        if(tab.equals("admin")&&!ServerMenuClient.admin()){CommunityScreen.open(parent,"home");return;}
        content.bounds(nav.left(),70,width-nav.left()-14,Math.max(30,height-145));
        if(tab.equals("help")){button("server.report",nav.left(),height-72,Math.min(180,(width-nav.left()-22)/2),()->minecraft.setScreen(new ReportScreen(this)));button("server.myReports",nav.left()+(width-nav.left())/2,height-72,Math.min(180,(width-nav.left()-22)/2),()->FeatureListScreen.open(this,"myReports"));}
        if(tab.equals("admin")){
            int left=nav.left(),available=width-left-14,third=(available-8)/3;
            String[] groups={"reports","server","journal"},labels={"Обращения","Сервер","Журнал"};
            for(int i=0;i<3;i++){String group=groups[i];var tabButton=addRenderableWidget(Button.builder(Component.literal(labels[i]),b->{adminGroup=group;resetScroll();rebuildWidgets();}).bounds(left+i*(third+4),42,third,20).build());tabButton.active=!group.equals(adminGroup);}
            int count=adminGroup.equals("server")?4:1;scrollArea(count,76,height-64,56,width-10);
            for(int row=firstRow;row<Math.min(count,firstRow+visibleRows);row++){
                int y=76+(row-firstRow)*56+22,half=(available-12)/2;
                if(adminGroup.equals("reports")){button("server.reports",left+6,y,half,()->FeatureListScreen.open(this,"reports"));continue;}
                if(adminGroup.equals("journal")){button("server.history",left+6,y,half,()->FeatureListScreen.open(this,"history"));button("server.diagnostics",left+half+10,y,half,()->ServerMenuClient.request("diagnostics"));continue;}
                switch(row){
                    case 0 -> button("server.announce",left+6,y,half,()->action("announce"));
                    case 1 -> {button("server.maintenance",left+6,y,half,()->action("maintenance"));button("server.endmaintenance",left+half+10,y,half,()->action("maintenanceOff"));}
                    case 2 -> {button("server.restart",left+6,y,half,()->action("restart"));button("server.cancelrestart",left+half+10,y,half,()->action("restartOff"));}
                    case 3 -> button("server.reloadMenu",left+6,y,half,()->ServerMenuClient.request("reloadMenu"));
                }
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
    @Override public void tick(){if(tab.equals("admin")&&!ServerMenuClient.admin()){CommunityScreen.open(parent,"home");}}
    private String body(){var j=ServerMenuClient.state;
        if(tab.equals("help"))return Json.opt(j,"help","")+"\n\n"+Client.tr("server.helptext").getString();
        return Client.tr("server.admintext").getString();
    }
    @Override public void renderBackground(GuiGraphics g,int x,int y,float d){super.renderBackground(g,x,y,d);if(tab.equals("admin")){
        String[] labels=adminGroup.equals("reports")?new String[]{"Очередь обращений и ответы игрокам"}:adminGroup.equals("journal")?new String[]{"История действий и состояние сервера"}:new String[]{"Объявления для игроков","Техническое обслуживание","Перезапуск с обратным отсчётом","Настройки меню"};
        for(int row=firstRow;row<Math.min(labels.length,firstRow+visibleRows);row++){int top=76+(row-firstRow)*56;g.fill(nav.left(),top,width-12,top+50,0xCE25232A);g.fill(nav.left(),top,nav.left()+3,top+50,0xFFE2BE75);g.drawString(font,font.plainSubstrByWidth(labels[row],width-nav.left()-26),nav.left()+8,top+7,0xE2BE75);}
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
        ActionForm(Screen parent,String action){super(Client.tr("server."+action));this.parent=parent;this.action=action;}
        private boolean timed(){return !action.equals("announce");}
        @Override protected void init(){
            int w=Math.min(420,width-40),x=(width-w)/2;
            String draft=message==null?"":message.getValue(),value=duration==null?(action.equals("maintenance")?"15":"60"):duration.getValue();
            message=addRenderableWidget(new EditBox(font,x,64,w,20,Client.tr("server.message")));message.setMaxLength(500);message.setValue(draft);
            if(timed()){duration=addRenderableWidget(new EditBox(font,x,108,100,20,Client.tr(action.equals("maintenance")?"server.minutes":"server.seconds")));duration.setMaxLength(5);duration.setFilter(v->v.matches("[0-9]*"));duration.setValue(value);}
            addRenderableWidget(Button.builder(Client.tr("server.confirm"),b->submit()).bounds(x,height-28,w/2-3,20).build());
            addRenderableWidget(Button.builder(Client.tr("back"),b->onClose()).bounds(x+w/2+3,height-28,w/2-3,20).build());
            setInitialFocus(message);
        }
        private void submit(){
            int amount=0;if(timed())try{amount=Integer.parseInt(duration.getValue());if(amount<(action.equals("restart")?1:0)||amount>(action.equals("maintenance")?1440:86400))throw new NumberFormatException();}catch(NumberFormatException ex){error=Client.tr("server.invalidnumber").getString();return;}
            if(message.getValue().isBlank()){error=Client.tr("server.reportempty").getString();return;}
            var j=new JsonObject();j.addProperty("action",action);j.addProperty("text",message.getValue().strip());
            if(action.equals("maintenance")){j.addProperty("enabled",true);j.addProperty("minutes",amount);}else if(action.equals("restart"))j.addProperty("seconds",amount);
            Component summary=Component.literal(message.getValue());if(timed())summary=summary.copy().append(" · "+amount+" ").append(Client.tr(action.equals("maintenance")?"server.minutes":"server.seconds"));
            minecraft.setScreen(new ConfirmScreen(yes->{minecraft.setScreen(yes?parent:this);if(yes)ServerMenuClient.request(j);},title,summary));
        }
        @Override public void render(GuiGraphics g,int x,int y,float d){super.render(g,x,y,d);int left=(width-Math.min(420,width-40))/2;
            g.drawCenteredString(font,title,width/2,20,0xE2BE75);g.drawString(font,Client.tr("server.message"),left,50,0xEEEEEE);
            if(timed())g.drawString(font,Client.tr(action.equals("maintenance")?"server.minutes":"server.seconds"),left,94,0xEEEEEE);
            Ui.status(g,font,error.isEmpty()?Client.tr("server.form."+action).getString():error,left,timed()?140:100,Math.min(420,width-40),height-36);
        }
        @Override public void tick(){if(!ServerMenuClient.available())minecraft.setScreen(null);else if(!ServerMenuClient.admin())minecraft.setScreen(parent);}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void onClose(){minecraft.setScreen(parent);}
    }

}
