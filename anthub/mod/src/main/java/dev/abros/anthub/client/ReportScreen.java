package dev.abros.anthub.client;

import com.google.gson.JsonObject;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import dev.abros.anthub.core.*;

final class ReportScreen extends Screen implements CommunityScreen.Receiver {
    private final TextDraft draft;
    private final RequestSession session=new RequestSession();
    private final String initial;
    private final Screen parent;
    private JsonObject pending;
    private MultiLineEditBox message;
    private Button send;
    private boolean busy;
    private String status="";
    ReportScreen(Screen parent){this(parent,"");}
    ReportScreen(Screen parent,String initial){super(initial.startsWith("Игрок: ")?Component.literal("Жалоба на игрока"):Client.tr("server.report"));this.parent=parent;this.initial=initial;draft=new TextDraft("report:"+initial);}
    private int panelHeight(){return Math.min(270,height-20);}
    private int top(){return (height-panelHeight())/2;}
    @Override protected void init(){
        String text=message==null?draft.load(""):message.getValue();
        // Older drafts included the target in the message itself.
        if(!initial.isEmpty()&&text.startsWith(initial))text=text.substring(initial.length());
        int w=Math.min(460,width-32),x=(width-w)/2,y=top();
        message=addRenderableWidget(new MultiLineEditBox(font,x,y+48,w,panelHeight()-110,Component.literal("Опишите, что произошло"),Client.tr("server.message")));
        message.setCharacterLimit(Math.max(1,1500-initial.length()));message.setValue(text);
        message.setValueListener(draft::save);
        send=addRenderableWidget(Button.builder(Client.tr("server.send"),b->sendReport()).bounds(x,y+panelHeight()-26,w/2-3,20).build());
        addRenderableWidget(Button.builder(Client.tr("back"),b->onClose()).bounds(x+w/2+3,y+panelHeight()-26,w/2-3,20).build());
        updateInputs();setInitialFocus(message);
    }
    private void updateInputs(){if(send!=null){send.active=!busy;send.setMessage(Component.literal(busy?"Отправка…":pending!=null?"Повторить отправку":"Отправить"));}if(message!=null)message.active=!busy&&pending==null;}
    private void sendReport(){
        if(busy)return;
        if(pending!=null){busy=true;status="Повторная отправка…";updateInputs();ServerMenuClient.request(session.retry(System.currentTimeMillis()));return;}
        String text=message.getValue().strip();if(text.isBlank()){status=Client.tr("server.reportempty").getString();return;}
        draft.save(message.getValue());busy=true;status="";updateInputs();
        Client.IO.submit(()->{
            var j=new JsonObject();j.addProperty("action","report");j.addProperty("message",initial+text);j.addProperty("coreVersion",dev.abros.anthub.AntHub.VERSION);
            var active=Client.hub==null?null:Client.hub.active();j.addProperty("packVersion",active==null?"":active.version());j.addProperty("repository",active==null?"":active.repository());
            try{String audit=Client.hub==null?"Unavailable":String.join("\n",Client.hub.audit());j.addProperty("audit",audit.substring(0,Math.min(2000,audit.length())));}catch(Exception ex){j.addProperty("audit","Verification failed");}
            minecraft.execute(()->{if(minecraft.screen!=this){busy=false;return;}pending=j.deepCopy();status="Отправка…";ServerMenuClient.request(session.begin(j,true,System.currentTimeMillis()));});
        });
    }
    @Override public void render(GuiGraphics g,int x,int y,float d){super.render(g,x,y,d);int w=Math.min(460,width-32),left=(width-w)/2;g.drawCenteredString(font,title,width/2,top()+6,0xE2BE75);if(!initial.isBlank())g.drawString(font,font.plainSubstrByWidth(initial.strip(),w),left,top()+28,0xEEEEEE);Ui.status(g,font,status,left,top()+panelHeight()-49,w,top()+panelHeight()-28);}
    public void receiveCommunity(JsonObject response){if(!session.receive(response))return;busy=false;status=Json.opt(response,"text","");if(!response.has("error")){pending=null;draft.clear();ServerMenuClient.result="Отправлено администрации";minecraft.setScreen(parent);}else{if(Set.of("INVALID","FORBIDDEN","EXPIRED").contains(Json.opt(response,"code","")))pending=null;updateInputs();}}
    @Override public void tick(){if(session.timeout(System.currentTimeMillis())){busy=false;status="Нет ответа. Повторите отправку.";updateInputs();}if(!ServerMenuClient.available())onClose();}
    @Override public void onClose(){if(message!=null)draft.save(message.getValue());session.cancel();minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
