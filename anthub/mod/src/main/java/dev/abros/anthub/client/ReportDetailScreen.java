package dev.abros.anthub.client;
import com.google.gson.*;
import dev.abros.anthub.core.Json;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
final class ReportDetailScreen extends Screen implements CommunityScreen.Receiver {
    private final TextDraft draft;private final Screen parent;private final JsonObject report;private final boolean editable;private final ContentPane pane=new ContentPane();private MultiLineEditBox reply;private Button sendButton,statusButton;private boolean resolved,busy;private String status="";private final dev.abros.anthub.core.RequestSession session=new dev.abros.anthub.core.RequestSession();private JsonObject attempted;
    ReportDetailScreen(Screen parent,JsonObject report,boolean editable){super(net.minecraft.network.chat.Component.literal("Обращение игрока"));this.parent=parent;this.report=report;this.draft=new TextDraft("report-reply:"+Json.str(report,"id"));this.editable=editable;resolved=Json.opt(report,"status","open").equals("resolved");}
    void updateReport(JsonObject value){for(var entry:value.entrySet())report.add(entry.getKey(),entry.getValue().deepCopy());}
    private int panelTop(){return UiDialog.top(height,370);}
    private int panelBottom(){return height-panelTop();}
    private int left(){return (width-Math.min(520,width-40))/2;}
    private int contentWidth(){return Math.min(520,width-40);}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.draw(g,width,contentWidth(),panelTop(),panelBottom());}
 @Override protected void init(){pane.bounds(left(),panelTop()+(editable?64:36),contentWidth(),Math.max(24,panelBottom()-panelTop()-(editable?232:76)));pane.text("Обращение: "+Json.str(report,"player")+" · "+Client.tr("server.status."+Json.opt(report,"status","open")).getString()+"\n"+Json.str(report,"message")+(Json.opt(report,"reply","").isBlank()?"":"\n\nОтвет администрации · "+Json.opt(report,"replyAuthor","")+"\n"+Json.opt(report,"reply",""))+"\n\n"+Json.opt(report,"audit",""));
        if(editable){if(ServerMenuClient.supports("player-tools"))addRenderableWidget(UiActions.button(net.minecraft.network.chat.Component.literal("Управление обращением…"),UiActions.Tone.NORMAL,"",b->minecraft.setScreen(new ReportManagementScreen(this,report))).bounds(left(),panelTop()+34,Math.min(180,contentWidth()),20).build());String draftText=reply==null?draft.load(Json.opt(report,"reply","")):reply.getValue();reply=addRenderableWidget(UiFields.multiline(font,left(),panelBottom()-152,contentWidth(),62,Client.tr("server.reply"),Client.tr("server.reply")));reply.setCharacterLimit(1500);reply.setValue(draftText);
            statusButton=addRenderableWidget(new UiChoiceRow(left(),panelBottom()-72,contentWidth()-114,"Закрыть после ответа",resolved,-1,UiKit.ACCENT,()->{resolved=!resolved;rebuildWidgets();}));
            sendButton=addRenderableWidget(UiActions.button(net.minecraft.network.chat.Component.literal("Ответить"),UiActions.Tone.NORMAL,"",b->{if(busy||reply.getValue().isBlank())return;var j=new JsonObject();j.addProperty("action","reply");j.addProperty("id",Json.str(report,"id"));j.addProperty("text",reply.getValue());j.addProperty("resolved",resolved);j.add("revision",report.get("revision"));JsonObject packet;if(attempted!=null&&attempted.equals(j))packet=session.retry(System.currentTimeMillis());else{attempted=j.deepCopy();packet=session.begin(j,true,System.currentTimeMillis());}draft.save(reply.getValue());busy=true;status="Отправка…";ServerMenuClient.request(packet);}).bounds(left()+contentWidth()-100,panelBottom()-72,100,20).build());}
        UiActions.close(new dev.abros.anthub.core.NativeLayout.Box(left(),panelBottom()-26,contentWidth(),20),this::addRenderableWidget,this::onClose);}
    @Override public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);UiHeading.dialog(g,font,title,(width-(contentWidth()))/2,panelTop(),contentWidth());pane.render(g,font);if(editable)Ui.text(g,font,"Ответ игроку",left(),panelBottom()-166,UiKit.muted(),false);Ui.status(g,font,status,left(),panelBottom()-48,contentWidth(),panelBottom()-28);});}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){return pane.scroll(x,y,dy)||super.mouseScrolled(x,y,dx,dy);}
    @Override public boolean mouseClicked(double x,double y,int b){return b==0&&pane.click(x,y)||super.mouseClicked(x,y,b);}
    @Override public boolean mouseDragged(double x,double y,int b,double dx,double dy){return b==0&&pane.drag(y)||super.mouseDragged(x,y,b,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int b){pane.release();return super.mouseReleased(x,y,b);}
    public void receiveCommunity(JsonObject response){if(!session.receive(response))return;busy=false;status=Json.opt(response,"text","");if(!response.has("error")){draft.clear();attempted=null;if(response.has("report")){updateReport(response.getAsJsonObject("report"));resolved=Json.opt(report,"status","open").equals("resolved");rebuildWidgets();}}}
    @Override public void tick(){if(reply!=null){reply.active=!busy;sendButton.active=!busy&&!reply.getValue().isBlank();statusButton.active=!busy;sendButton.setMessage(busy?net.minecraft.network.chat.Component.literal("Отправка…"):net.minecraft.network.chat.Component.literal("Ответить"));}if(session.timeout(System.currentTimeMillis())){busy=false;status="Нет ответа. Повторите отправку: ответ не продублируется.";}if(!ServerMenuClient.available())minecraft.setScreen(null);else if(editable&&!ServerMenuClient.may("anthub.reports")){session.cancel();minecraft.setScreen(parent);}}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){if(reply!=null)draft.save(reply.getValue());session.cancel();minecraft.setScreen(parent);}
}
