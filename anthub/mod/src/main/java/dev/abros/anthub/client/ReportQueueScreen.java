package dev.abros.anthub.client;
import com.google.gson.*;
import dev.abros.anthub.core.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
/** One administrative work queue, with concrete filters and selection for reviewed bulk actions. */
final class ReportQueueScreen extends ScrollScreen implements CommunityScreen.Receiver {
 private final Screen parent;private final RequestSession session=new RequestSession();private final MenuSidebar nav=new MenuSidebar(this);private final Map<String,JsonObject> chosen=new LinkedHashMap<>();
 private JsonArray rows=new JsonArray();private String filter,query="",cursor="",next="",notice="";private boolean loaded,busy,failed;private long searchAt;
 ReportQueueScreen(Screen parent,String filter){super(Component.literal("Очередь обращений"));this.parent=parent;this.filter=filter;}
 private int w(){return nav.right()-nav.left()-24;}
 private void load(){if(busy)return;var j=new JsonObject();j.addProperty("action","reports");j.addProperty("page",0);j.addProperty("filter",filter);j.addProperty("query",query);j.addProperty("cursor",cursor);busy=true;failed=false;ServerMenuClient.request(session.begin(j,false,System.currentTimeMillis()));rebuildWidgets();}
 void invalidate(){cursor="";chosen.clear();load();}
 private void reset(){cursor="";chosen.clear();resetScroll();load();}
 private void toggle(JsonObject row){String id=Json.str(row,"id");if(chosen.remove(id)==null){if(chosen.size()>=20){notice="Выберите не более 20 обращений";return;}chosen.put(id,row);}rebuildWidgets();}
 private void bulk(AbstractWidget anchor){minecraft.setScreen(new ChoicePopup(this,"Для выбранных обращений",List.of("Взять в работу","Закрыть обращения","Высокий приоритет"),n->{var items=new JsonArray();for(var r:chosen.values()){var ref=new JsonObject();ref.add("id",r.get("id"));ref.add("revision",r.get("revision"));items.add(ref);}minecraft.setScreen(new ReportBulkScreen(this,List.of("claim","resolved","high").get(n),items));},anchor));}
 @Override protected void init(){nav.build("admin",this::addRenderableWidget);var labels=List.of("Новые","Мои","Ожидают","Закрытые");var values=List.of("new","mine","waiting","resolved");UiTabs.build(this,font,new NativeLayout.Box(nav.left(),42,w(),20),labels,values.indexOf(filter),this::addRenderableWidget,n->{filter=values.get(n);reset();},!busy);

  var search=addRenderableWidget(UiFields.text(font,nav.left(),72,Math.max(1,w()-110),20,Component.literal("Поиск")));search.setHint(Component.literal("Игрок или текст обращения"));search.setMaxLength(100);search.setValue(query);search.setResponder(v->{query=v;searchAt=System.currentTimeMillis()+500;});search.active=!busy;
  addRenderableWidget(UiActions.button(Component.literal("Фильтр ▾"),UiActions.Tone.NORMAL,"",b->minecraft.setScreen(new ChoicePopup(this,"Показать",List.of("Все","Без ответственного","Высокий приоритет"),n->{filter=List.of("all","unassigned","high").get(n);reset();},b))).bounds(nav.left()+w()-104,72,104,20).build()).active=!busy;
  var bulk=addRenderableWidget(UiActions.button(Component.literal("Выбрано: "+chosen.size()+" ▾"),UiActions.Tone.NORMAL,"",b->bulk(b)).bounds(nav.left(),100,Math.min(150,w()-84),20).build());bulk.active=!busy&&!chosen.isEmpty();addRenderableWidget(UiActions.button(Component.literal("Сбросить"),UiActions.Tone.NORMAL,"",b->{query="";filter="new";reset();}).bounds(nav.left()+Math.min(150,w()-84)+6,100,78,20).build()).active=!busy;
  int stride=Math.min(76,Math.max(30,height-62-130));scrollArea(rows.size(),new dev.abros.anthub.core.NativeLayout.Box(nav.left(),130,Math.max(0,w()),Math.max(0,(height-62)-(130))),stride);for(int n=firstRow;n<Math.min(rows.size(),firstRow+visibleRows);n++){var r=rows.get(n).getAsJsonObject();int y=130+(n-firstRow)*stride;addRenderableWidget(new UiChoiceRow(nav.left(),y,24,"",chosen.containsKey(Json.str(r,"id")),-1,UiKit.ACCENT,()->toggle(r))).active=!busy;var card=r.deepCopy();card.addProperty("section","moderation");card.addProperty("title",Json.str(r,"player"));card.addProperty("attention",Json.opt(r,"status","open").equals("waiting")?"Ожидает ответа":Json.opt(r,"priority","normal").equals("high")?"Высокий приоритет":Json.opt(r,"status","open").equals("resolved")?"Закрыто":"Открыто");card.addProperty("preview",Json.str(r,"message"));card.addProperty("footer",Json.opt(r,"nextStep","").isBlank()?Json.opt(r,"assignedName","").isBlank()?"Назначьте ответственного":"Ответственный: "+Json.opt(r,"assignedName",""):"Далее: "+Json.str(r,"nextStep"));addRenderableWidget(new CommunityCard(nav.left()+30,y,w()-30,stride-6,card,()->minecraft.setScreen(new ReportDetailScreen(this,r,true)))).active=!busy;}
  if(!next.isEmpty())addRenderableWidget(UiActions.button(Component.literal("Следующие"),UiActions.Tone.NORMAL,"",b->{cursor=next;resetScroll();load();}).bounds(nav.left(),height-54,110,20).build()).active=!busy;
  var footer=UiPageFooter.workspace(width,height);footer.start(UiActions.Command.REFRESH,this::addRenderableWidget,this::invalidate).active=!busy;footer.end(UiActions.Command.BACK,this::addRenderableWidget,this::onClose);if(!loaded){loaded=true;load();}}
 public void receiveCommunity(JsonObject j){if(!session.receive(j))return;busy=false;failed=j.has("error");if(j.has("error"))notice=Json.opt(j,"text","Не удалось загрузить очередь");else if(j.has("reports")){rows=j.getAsJsonArray("reports");next=Json.opt(j,"nextCursor","");notice=rows.isEmpty()?"В этой очереди обращений нет":"";}rebuildWidgets();}
 @Override public void tick(){if(searchAt>0&&!busy&&System.currentTimeMillis()>=searchAt){searchAt=0;reset();}if(session.timeout(System.currentTimeMillis())){busy=false;failed=true;notice="Нет ответа. Обновите очередь.";rebuildWidgets();}if(!ServerMenuClient.may("anthub.reports"))onClose();}
 @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(nav.scroll(x,y,dy)){rebuildWidgets();return true;}return super.mouseScrolled(x,y,dx,dy);}
 @Override public void renderBackground(GuiGraphics g,int mx,int my,float d){super.renderBackground(g,mx,my,d);UiTheme.shell(this,g);}
 @Override public void render(GuiGraphics g,int mx,int my,float d){super.render(g,mx,my,d);UiHeading.page(g,font,title,width);nav.drawFrame(g);if(rows.isEmpty())UiState.draw(g,font,failed?UiState.Kind.ERROR:busy?UiState.Kind.LOADING:query.isBlank()?UiState.Kind.EMPTY:UiState.Kind.FILTERED,failed?"Очередь недоступна":busy?"Загружаем обращения…":query.isBlank()?"В этой очереди нет обращений":"Обращения не найдены",failed?notice:busy?"Получаем актуальные данные с сервера.":query.isBlank()?"Новые обращения появятся здесь.":"Измените запрос или фильтр.",nav.left()+8,138,w()-16,height-64);Ui.status(g,font,notice,nav.left(),height-56,w(),height-34);}
 @Override public void onClose(){session.cancel();minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
