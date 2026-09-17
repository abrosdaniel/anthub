package dev.abros.anthub.client;

import com.google.gson.JsonObject;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import dev.abros.anthub.core.*;

final class ReportScreen extends Screen implements CommunityScreen.Receiver {
    private final TextDraft draft;private final RequestSession session=new RequestSession();private JsonObject pending;private final String initial;private final Screen parent;private EditBox message;private boolean busy;private String status="";
    ReportScreen(Screen parent){this(parent,"");}
    ReportScreen(Screen parent,String initial){super(Client.tr("server.report"));this.parent=parent;this.initial=initial;draft=new TextDraft("report:"+initial);}
    @Override protected void init(){String draft=message==null?this.draft.load(initial):message.getValue();message=addRenderableWidget(new EditBox(font,20,70,width-40,20,Client.tr("server.message")));message.setMaxLength(1500);message.setValue(draft);message.setHint(Client.tr("server.message"));
        addRenderableWidget(Button.builder(Client.tr("server.preview"),b->{if(busy)return;if(pending!=null){busy=true;ServerMenuClient.request(session.retry(System.currentTimeMillis()));status="Повторная отправка…";return;}String text=message.getValue();this.draft.save(text);if(text.isBlank()){status=Client.tr("server.reportempty").getString();return;}busy=true;Client.IO.submit(()->{
            var j=new JsonObject();j.addProperty("action","report");j.addProperty("message",text);j.addProperty("coreVersion",dev.abros.anthub.AntHub.VERSION);var active=Client.hub==null?null:Client.hub.active();j.addProperty("packVersion",active==null?"":active.version());j.addProperty("repository",active==null?"":active.repository());
            try{String audit=Client.hub==null?"Unavailable":String.join("\n",Client.hub.audit());j.addProperty("audit",audit.substring(0,Math.min(2000,audit.length())));}catch(Exception ex){j.addProperty("audit","Verification failed");}
            minecraft.execute(()->{busy=false;if(minecraft.screen!=this)return;String preview=Client.tr("server.reportprivacy").getString()+"\n\n"+Json.GSON.toJson(j);
                minecraft.setScreen(new ReviewScreen(this,title,preview,Client.tr("server.send"),()->{pending=j.deepCopy();busy=true;status=Client.tr("server.sending").getString();ServerMenuClient.request(session.begin(j,true,System.currentTimeMillis()));minecraft.setScreen(this);}));});
        });}).bounds(width/2-130,height-54,260,20).build());
        addRenderableWidget(Button.builder(Client.tr("back"),b->onClose()).bounds(width/2-100,height-28,200,20).build());}
    @Override public void render(GuiGraphics g,int x,int y,float d){super.render(g,x,y,d);g.drawCenteredString(font,title,width/2,20,0xE2BE75);Ui.status(g,font,status,20,100,width-40,height-60);}
    public void receiveCommunity(JsonObject response){if(!session.receive(response))return;busy=false;status=Json.opt(response,"text","");if(!response.has("error")){pending=null;draft.clear();minecraft.setScreen(parent);}else if(Set.of("INVALID","FORBIDDEN","EXPIRED").contains(Json.opt(response,"code","")))pending=null;}
    @Override public void tick(){if(session.timeout(System.currentTimeMillis())){busy=false;status="Нет ответа. Повторите отправку тем же запросом.";}if(!ServerMenuClient.available())onClose();}
    @Override public void onClose(){if(message!=null)draft.save(message.getValue());session.cancel();minecraft.setScreen(parent);}

}
