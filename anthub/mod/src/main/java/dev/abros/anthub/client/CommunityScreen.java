package dev.abros.anthub.client;

import com.google.gson.*;
import dev.abros.anthub.core.Json;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.*;

/** Community navigation, paged lists and contextual actions with request-bound responses. */
final class CommunityScreen extends ScrollScreen {
    interface Receiver {void receiveCommunity(JsonObject response);}
    record Row(String label,Runnable click,double progress){Row(String label,Runnable click){this(label,click,-1);}}
    private final Screen parent;
    final String section;
    final String itemId;JsonObject invitationTarget;private String memberFilter="";
    static void groupsFor(Screen parent,String member){var screen=new CommunityScreen(parent,"groups","");screen.memberFilter=member;net.minecraft.client.Minecraft.getInstance().setScreen(screen);}
    static void invite(Screen parent,JsonObject target){var screen=new CommunityScreen(parent,"groups","");screen.invitationTarget=target;net.minecraft.client.Minecraft.getInstance().setScreen(screen);}
    JsonObject data=new JsonObject();
    final List<Row> rows=new ArrayList<>();
    static void clearDrafts(){}
    private final dev.abros.anthub.core.RequestSession session=new dev.abros.anthub.core.RequestSession();private boolean uncertain;private long sentAt,dirtyAt;
    void invalidate(){if(inlineParent!=null&&inlineParent.dirtyAt==0)inlineParent.dirtyAt=System.currentTimeMillis();if(dirtyAt==0)dirtyAt=System.currentTimeMillis();for(var child:expanded.values())child.invalidate();}
    final Set<Integer> choices=new LinkedHashSet<>();
    private String request="",query="";String status="";
    private int page,navOffset,loadedPage,refreshThrough;private boolean appendPage,hasMore;private long nextPageAt;private final java.util.Map<Integer,JsonArray> pages=new java.util.TreeMap<>();private final java.util.NavigableMap<Integer,JsonObject> detailPages=new java.util.TreeMap<>();
    private boolean archive,trash,participating;String groupTab="overview";private boolean detailDragging;private double detailPosition;
    private final List<Row> moreActions=new ArrayList<>();private final Map<Integer,String> cursors=new HashMap<>();
    boolean mine,busy,loaded,quickAction,choicesDirty;private String lastOperation="";
    private EditBox search;private Button notificationButton;
    private CommunityScreen inlineParent;
    private final LinkedHashMap<String,CommunityScreen> expanded=new LinkedHashMap<>();
    Screen surface(){return inlineParent==null?this:inlineParent;}
    void refreshUi(){if(inlineParent!=null){inlineParent.refreshUi();return;}boolean typing=search!=null&&getFocused()==search;int cursor=typing?search.getCursorPosition():0;rebuildWidgets();if(typing&&search!=null){setFocused(search);search.setCursorPosition(cursor);}}
    private void prepareInline(CommunityScreen host){inlineParent=host;minecraft=host.minecraft;font=host.font;width=host.width;height=host.height;}
    private record Anchor(String id,int offset){}
    private Anchor anchor(){if(!data.has("entries")||data.getAsJsonArray("entries").isEmpty())return null;int n=Math.min(firstRow,data.getAsJsonArray("entries").size()-1);return new Anchor(Json.str(data.getAsJsonArray("entries").get(n).getAsJsonObject(),"id"),0);}
    private void restore(Anchor anchor){if(anchor==null||!data.has("entries"))return;int n=0;for(var entry:data.getAsJsonArray("entries")){if(Json.str(entry.getAsJsonObject(),"id").equals(anchor.id)){restoreScroll(n);refreshUi();return;}n++;}}
    private boolean splitLayout(){return inlineParent==null&&itemId.isEmpty()&&!home()&&width-left()-20>=430;}
    private int listWidth(){return Math.min(230,(width-left()-32)*2/5);}
    private int detailLeft(){return left()+listWidth()+12;}
    private CommunityScreen selected(){return expanded.isEmpty()?null:expanded.values().iterator().next();}
    private void inlineWidgets(int top){
        int stride=76;scrollArea(data.getAsJsonArray("entries").size(),top,height-82,stride,left()+contentWidth()+3);
        for(int n=firstRow;n<Math.min(data.getAsJsonArray("entries").size(),firstRow+visibleRows);n++){
            var entry=data.getAsJsonArray("entries").get(n).getAsJsonObject();
            var card=new CommunityCard(left(),top+(n-firstRow)*stride,contentWidth(),70,entry,()->openEntry(entry));card.selected=selected()!=null&&selected().itemId.equals(Json.str(entry,"id"));addRenderableWidget(card);
        }
        if(splitLayout())detailWidgets();
    }
    private void detailWidgets(){
        var child=selected();if(child==null)return;child.prepareInline(this);child.rows.clear();
        if(child.data.has("detail"))child.detail(child.data.getAsJsonObject("detail"));
        int x=detailLeft(),w=width-x-20,top=child.bodyTop();
        child.visibleRows=Math.max(1,(height-82-top)/24);child.firstRow=Math.max(0,Math.min(child.firstRow,Math.max(0,child.rows.size()-child.visibleRows)));
        child.detailControls(this,x,104,w);drawRowWidgets(child,x+8,top,w-16);
        button(child.uncertain?"Повторить":child.hasMore?"Загрузить ещё":"Обновить",x,height-54,Math.max(60,w-68),child::retryOrLoad);
        button("Закрыть",x+w-64,height-54,64,()->{expanded.clear();refreshUi();});
    }
    private void retryOrLoad(){if(busy)return;if(uncertain){var retry=session.retry(System.currentTimeMillis());busy=true;sentAt=System.currentTimeMillis();request=Json.str(retry,"request");ServerMenuClient.request(retry);}else if(hasMore)nextPage();else load();}
    private int contentBottom(){return height-(!itemId.isEmpty()&&inlineParent==null?58:82);}
    private int bodyTop(){return (inlineParent==null?40:104)+(section.equals("groups")?68:42);}
    private void drawRowWidgets(CommunityScreen child,int x,int top,int w){
        for(int n=child.firstRow;n<Math.min(child.rows.size(),child.firstRow+child.visibleRows);n++){
            var row=child.rows.get(n);int y=top+(n-child.firstRow)*24;
            if(row.progress>=0){var bar=new PollOption(x,y,w,row.label,row.progress,()->{if(!child.busy&&row.click!=null)row.click.run();});bar.active=row.click!=null;addRenderableWidget(bar);}
            else if(row.click!=null){var control=button(font.plainSubstrByWidth(row.label,w-12),x,y,w,()->{if(!child.busy)row.click.run();});control.active=!child.busy;}
        }
    }
    private void detailControls(CommunityScreen host,int x,int y,int w){
        if(!moreActions.isEmpty())host.button("⋯",x+w-30,y+4,26,this::showMore);
        if(section.equals("groups")&&data.has("detail")){
            boolean manage=data.getAsJsonObject("detail").get("manage").getAsBoolean();
            var tabs=manage?List.of("overview","members","requests"):List.of("overview","members");int tw=(w-16)/tabs.size();
            for(int i=0;i<tabs.size();i++){String tab=tabs.get(i);String label=switch(tab){case "overview"->"Обзор";case "members"->"Участники";default->"Заявки";};
                var control=host.button(label,x+8+i*tw,y+40,tw-3,()->{groupTab=tab;resetScroll();if(inlineParent!=null)inlineParent.detailPosition=0;refreshUi();});control.active=!groupTab.equals(tab);}
        }
    }
    private void renderDetailPane(GuiGraphics g){
        int x=detailLeft(),w=width-x-20;g.fill(x,104,x+w,height-82,0xD01B252E);var child=selected();
        if(child==null){g.drawString(font,"Выберите запись",x+10,116,0xBAC6D2);return;}
        if(child.data.has("detail")){var j=child.data.getAsJsonObject("detail");g.drawString(font,font.plainSubstrByWidth(Json.str(j,"title"),w-50),x+8,112,0xFFFFFF);g.drawString(font,font.plainSubstrByWidth(status(Json.str(j,"status")),w-16),x+8,128,child.accent());}
        int top=child.bodyTop();g.enableScissor(x,top,x+w,height-82);
        for(int n=child.firstRow;n<Math.min(child.rows.size(),child.firstRow+child.visibleRows);n++){var row=child.rows.get(n);if(row.click==null&&row.progress<0)g.drawString(font,row.label,x+8,top+(n-child.firstRow)*24+6,0xEEEEEE);}
        g.disableScissor();
        if(child.rows.size()>child.visibleRows){int track=height-82-top,thumb=Math.max(12,track*child.visibleRows/child.rows.size());int y=top+(track-thumb)*child.firstRow/(child.rows.size()-child.visibleRows);g.fill(x+w-4,top,x+w,height-82,0x88202020);g.fill(x+w-4,y,x+w,y+thumb,0xFFAAAAAA);}
        if(!child.status.isEmpty())Ui.status(g,font,child.status,x,height-78,w,height-56);
    }
    private void scrollDetail(double delta){var child=selected();if(child==null)return;detailPosition=Math.max(0,Math.min(detailPosition+delta,Math.max(0,child.rows.size()-child.visibleRows)));child.firstRow=(int)detailPosition;refreshUi();if(child.firstRow+child.visibleRows>=child.rows.size())child.nextPage();}
    void refreshPermissions(){refreshUi();}
    private static final Map<String,String> NAMES=Map.ofEntries(Map.entry("home","Главная"),Map.entry("players","Игроки"),Map.entry("board","Доска объявлений"),Map.entry("groups","Объединения"),Map.entry("events","События"),Map.entry("polls","Голосования"),Map.entry("ideas","Предложения"),Map.entry("notifications","Уведомления"),Map.entry("info","Сервер"),Map.entry("help","Помощь"),Map.entry("admin","Администрирование"));
    CommunityScreen(Screen parent,String section,String id){super(Component.literal(name(section)));this.parent=parent;this.section=section;itemId=id;}
    static String name(String section){if(section.equals("groups")&&ServerMenuClient.state.has("communityConfig"))return Json.opt(ServerMenuClient.state.getAsJsonObject("communityConfig"),"groupsTitle",NAMES.get(section));return NAMES.getOrDefault(section,section);}
    static void open(Screen parent,String section){net.minecraft.client.Minecraft.getInstance().setScreen(new CommunityScreen(parent,section,""));}
    static String me(){return Json.opt(ServerMenuClient.state,"uuid","");}
    static boolean can(JsonObject item,String action){return item.has("actions")&&item.getAsJsonArray("actions").contains(new JsonPrimitive(action));}
    boolean owner(JsonObject j){return me().equals(Json.opt(j,"owner",""));}
    private int left(){if(inlineParent!=null)return inlineParent.detailLeft();return Math.min(146,Math.max(96,width/4))+12;}
    private boolean home(){return section.equals("home")&&itemId.isEmpty();}
    private boolean sideProfile(){return home()&&height>=300&&width-left()>=490;}
    private int contentWidth(){if(inlineParent!=null)return width-left()-28;if(splitLayout())return listWidth();return width-left()-20-(sideProfile()?224:0);}
    private Button button(String label,int x,int y,int w,Runnable callback){return addRenderableWidget(Button.builder(Component.literal(label),b->callback.run()).bounds(x,y,w,20).build());}
    @Override protected void init(){
        if(data.has("detail")){rows.clear();detail(data.getAsJsonObject("detail"));}else if(data.has("entries")){rows.clear();list();}
        int navWidth=left()-20;var sections=new ArrayList<String>();var config=ServerMenuClient.state.has("communityConfig")?ServerMenuClient.state.getAsJsonObject("communityConfig"):new JsonObject();
        for(String key:List.of("home","players","board","groups","events","polls","ideas","info","help","admin")){if(key.equals("admin")&&!ServerMenuClient.admin())continue;if(config.has("sections")&&!Set.of("info","help","admin").contains(key)&&!config.getAsJsonArray("sections").contains(new JsonPrimitive(key)))continue;sections.add(key);}
        int shown=Math.max(1,(height-112)/24);navOffset=Math.max(0,Math.min(navOffset,Math.max(0,sections.size()-shown)));
        for(int n=navOffset;n<Math.min(sections.size(),navOffset+shown);n++){String key=sections.get(n);addRenderableWidget(new SidebarButton(8,42+(n-navOffset)*24,navWidth,name(key),section.equals(key),()->navigate(key)));}

        button("Назад",8,height-28,navWidth,()->onClose());
        int unread=ServerMenuClient.state.has("unread")?ServerMenuClient.state.get("unread").getAsInt():0;
        notificationButton=button((width<420?"●":"Уведомления")+(unread>0?" ("+unread+")":""),Math.max(left(),width-(width<420?158:220)),12,width<420?68:130,()->navigate("notifications"));if(!home())button("Главная",width-86,12,78,()->navigate("home"));
        if(itemId.isEmpty()&&!section.equals("home")){
            search=addRenderableWidget(new EditBox(font,left(),76,Math.max(50,width-left()-154),20,Component.literal("Поиск")));search.setMaxLength(100);search.setHint(Component.literal("Поиск"));search.setValue(query);search.setResponder(v->query=v);
            button("Найти",width-164,76,50,this::resetList);button(mine?"Мои":participating?"Участвую":"Все",width-110,76,90,()->{if(!busy)minecraft.setScreen(new ChoicePopup(this,"Показать",List.of("Все","Мои","Участвую"),i->{mine=i==1;participating=i==2;resetList();}));});
        }
        if(home()){
            int px=sideProfile()?width-232:left(),pw=sideProfile()?212:contentWidth(),py=sideProfile()?76:40;
            int by=sideProfile()?py+140:py+44;
            int half=(pw-18)/2;
            button("Объединения",px+6,by,half,()->groupsFor(this,me()));
            if(AuthClient.available())button("Безопасность",px+12+half,by,half,()->AuthAccountScreen.open(this));
        }
        int top=contentTop();
        if(cards()){
            inlineWidgets(top);
        }else{
            scrollArea(rows.size(),top,contentBottom(),24,left()+contentWidth()+5);
            drawRowWidgets(this,left(),top,contentWidth());
            if(!itemId.isEmpty())detailControls(this,left(),40,contentWidth());else if(splitLayout())detailWidgets();
        }
        if(home()&&sideProfile()){
            int half=(contentWidth()-6)/2;
            button("Ответы и приглашения"+(unread>0?" · "+unread:""),left(),76,half,()->navigate("notifications"));
            button("Избранное",left()+half+6,76,half,()->{if(!busy&&data.has("preferences"))minecraft.setScreen(new Favorites());});
        }
        if(itemId.isEmpty()){
            if(!home()&&!section.equals("notifications"))button("⋯",width-48,44,28,()->{if(!busy)minecraft.setScreen(new ChoicePopup(this,"Записи",List.of("Активные","Архив","Удалённые · 30 дней"),i->{archive=i==1;trash=i==2;resetList();}));});
            if(data.has("canCreate")&&data.get("canCreate").getAsBoolean()&&Set.of("board","groups","events","polls","ideas").contains(section))button(createLabel(),left(),height-54,Math.min(170,Math.max(60,contentWidth()-62)),this::create);
            if(section.equals("notifications"))button("Прочитать всё",left(),height-54,125,()->send("read",new JsonObject()));
        }
        button(uncertain?"Повторить":"Обновить",Math.max(left(),width-108),height-28,88,()->{if(uncertain){var retry=session.retry(System.currentTimeMillis());busy=true;sentAt=System.currentTimeMillis();request=Json.str(retry,"request");ServerMenuClient.request(retry);}else load();});
        if(!loaded){loaded=true;load();}
    }
    private final class Favorites extends ScrollScreen {
        private final java.util.List<String> keys=new java.util.ArrayList<>();
        Favorites(){super(Component.literal("Избранные разделы"));var prefs=data.getAsJsonObject("preferences");if(prefs.has("favorites"))for(var key:prefs.getAsJsonArray("favorites"))keys.add(key.getAsString());}
        @Override protected void init(){int w=Math.min(320,width-40),x=(width-w)/2;scrollArea(keys.size(),50,height-64,26,x+w+4);for(int i=firstRow;i<Math.min(keys.size(),firstRow+visibleRows);i++){String key=keys.get(i);addRenderableWidget(Button.builder(Component.literal(name(key)),b->CommunityScreen.this.navigate(key)).bounds(x,50+(i-firstRow)*26,w,22).build());}addRenderableWidget(Button.builder(Component.literal("Настроить"),b->minecraft.setScreen(new CommunityPreferences(CommunityScreen.this))).bounds(x,height-52,w,20).build());addRenderableWidget(Button.builder(Component.literal("Назад"),b->onClose()).bounds(x,height-28,w,20).build());}
        @Override public void render(GuiGraphics g,int x,int y,float d){super.render(g,x,y,d);g.drawCenteredString(font,title,width/2,20,0xE2BE75);if(keys.isEmpty())g.drawCenteredString(font,"Добавьте разделы через «Настроить»",width/2,54,0xBBBBBB);}
        @Override public void onClose(){minecraft.setScreen(CommunityScreen.this);}
        @Override public boolean isPauseScreen(){return false;}
    }
    private int contentTop(){return home()&&!sideProfile()?110:itemId.isEmpty()?104:bodyTop();}
    private boolean cards(){return itemId.isEmpty()&&!section.equals("notifications")&&data.has("entries")&&!data.getAsJsonArray("entries").isEmpty();}
    private void openEntry(JsonObject entry){String id=Json.str(entry,"id");
        if(selected()!=null&&selected().itemId.equals(id))return;
        var child=new CommunityScreen(this,Json.str(entry,"section"),id);child.invitationTarget=invitationTarget;
        if(!splitLayout()){minecraft.setScreen(child);return;}
        expanded.clear();detailPosition=0;child.prepareInline(this);expanded.put(id,child);child.loaded=true;child.load();refreshUi();
    }


    static String statusLabel(String value){return status(value);}
    private String createLabel(){return switch(section){case "board"->"Разместить объявление";case "groups"->"Создать объединение";case "events"->"Назначить событие";case "polls"->"Создать голосование";case "ideas"->"Предложить идею";default->"Создать";};}
    private String subtitle(){return switch(section){case "home"->"Ближайшие события и ваши дела";case "board"->"Предложения игроков и отклики";case "groups"->"Найдите команду или соберите свою";case "events"->"Встречи, участие и совместные планы";case "polls"->"Ваш голос в решениях сообщества";case "ideas"->"Идеи игроков и ответы администрации";case "notifications"->"Ответы, приглашения и напоминания";default->name(section);};}
    private String emptyText(){return switch(section){case "home"->"Пока нет новых дел. Загляните в объявления или события.";case "board"->"Объявлений пока нет. Здесь можно найти помощь или предложить свою.";case "groups"->"Объединений пока нет. Здесь появятся команды игроков.";case "events"->"Событий пока нет. Здесь появятся предстоящие встречи.";case "polls"->"Сейчас нет голосований.";case "ideas"->"Пока нет предложений. Поделитесь идеей для сервера.";case "notifications"->"Всё прочитано. Новые ответы и приглашения появятся здесь.";default->"Пока нет записей.";};}
    private int accent(){return switch(section){case "board"->0xFFE2BE75;case "groups"->0xFF79CBA6;case "events"->0xFF82B6F2;case "polls"->0xFFB49AE8;case "ideas"->0xFFF0A77C;default->0xFF8BC7CB;};}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float d){
        super.renderBackground(g,x,y,d);
        if(splitLayout())renderDetailPane(g);
        if(home())ProfilePanel.render(g,font,sideProfile()?width-232:left(),sideProfile()?76:40,sideProfile()?212:contentWidth(),sideProfile());
        if(home()&&!sideProfile())return;
        if(!data.has("detail")){g.fill(left(),40,width-16,70,0xC01C242C);g.fill(left(),40,left()+3,70,accent());
        g.drawString(font,font.plainSubstrByWidth(name(section),contentWidth()-16),left()+9,44,accent());
        g.drawString(font,font.plainSubstrByWidth(subtitle(),contentWidth()-16),left()+9,57,0xBAC6D2);}
        if(data.has("detail")){
            var item=data.getAsJsonObject("detail");g.fill(left(),40,left()+contentWidth(),contentBottom(),0xC51B252E);g.fill(left(),40,left()+3,contentBottom(),accent());
            String heading=Json.str(item,"title");String info=Json.str(item,"author")+" · "+status(Json.str(item,"status"));
            if(section.equals("groups"))info=Json.str(item,"type")+" · "+item.get("membersCount").getAsInt()+" участников";
            if(section.equals("events"))info=local(item.get("startsAt").getAsLong())+" · "+item.get("participantsCount").getAsInt()+" участников";
            if(section.equals("polls"))info="Завершение: "+local(item.get("endsAt").getAsLong());
            if(section.equals("ideas"))info=status(Json.str(item,"status"))+" · "+item.get("supportersCount").getAsInt()+" поддержали";
            g.drawString(font,font.plainSubstrByWidth(heading,contentWidth()-50),left()+10,46,0xFFFFFF);g.drawString(font,font.plainSubstrByWidth(info,contentWidth()-20),left()+10,62,accent());
        }
    }
    private void navigate(String key){if(key.equals("notifications")){minecraft.setScreen(new NotificationPopup(this));return;}if(busy)return;if(key.equals("players")){FeatureListScreen.open(this,"players");return;}if(key.equals("info")){minecraft.setScreen(new ServerInfoScreen(this));return;}if(Set.of("help","admin").contains(key)){minecraft.setScreen(new ServerMenuScreen(this,key));return;}minecraft.setScreen(new CommunityScreen(null,key,""));}
    private void resetList(){if(busy)return;page=loadedPage=refreshThrough=0;pages.clear();cursors.clear();expanded.clear();resetScroll();nextPageAt=0;appendPage=false;load();}
    private void load(){if(busy)return;refreshThrough=loadedPage;page=0;send(itemId.isEmpty()?"list":"detail",new JsonObject());}
    private void nextPage(){if(busy||uncertain||nextPageAt>0||!hasMore||System.currentTimeMillis()-sentAt<600)return;page=loadedPage+1;appendPage=true;send(itemId.isEmpty()?"list":"detail",new JsonObject());}

    void send(String op,JsonObject body){if(busy||uncertain)return;lastOperation=op;if(!Set.of("list","detail").contains(op)){refreshThrough=loadedPage;page=0;}if(data.has("detail"))body.add("revision",data.getAsJsonObject("detail").get("revision"));busy=true;sentAt=System.currentTimeMillis();status="Загрузка…";request=UUID.randomUUID().toString();body.addProperty("action","community");body.addProperty("section",section);body.addProperty("op",op);body.addProperty("id",itemId);body.addProperty("page",page);body.addProperty("trash",trash);body.addProperty("archive",archive);body.addProperty("cursor",page==0?"":cursors.getOrDefault(page,""));body.addProperty("mine",mine);body.addProperty("participating",participating);body.addProperty("member",memberFilter);body.addProperty("query",query);body.addProperty("request",request);body=session.begin(body,!Set.of("list","detail").contains(Json.opt(body,"op","")),sentAt);request=Json.str(body,"request");ServerMenuClient.request(body);}
    void receive(JsonObject response) {
        if(inlineParent==null)for(var child:expanded.values())if(child.request.equals(Json.opt(response,"request",""))){var anchor=anchor();child.receive(response);restore(anchor);return;}
        if (!session.receive(response)) return;uncertain=false;
        busy = false;
        if (response.has("error")) {
            quickAction = false; nextPageAt = 0; appendPage = false;
            if(Json.opt(response,"code","").equals("CONFLICT")&&Set.of("list","detail").contains(lastOperation)){cursors.clear();dirtyAt=System.currentTimeMillis();status="Запись обновилась. Загружаем актуальные данные…";return;}
            status = Json.opt(response, "text", "Ошибка");if(inlineParent!=null){inlineParent.status=status;refreshUi();}return;
        }
        if (quickAction) { quickAction = false; dirtyAt = System.currentTimeMillis(); status = "Сохранено"; return; }
        if (lastOperation.equals("vote")) choicesDirty = false;
        boolean retainChoices = choicesDirty && data.has("detail") && section.equals("polls");
        var anchor=anchor();
        if (itemId.isEmpty() && response.has("entries")) mergeList(response);
        else if (response.has("detail")){cursors.put(page+1,Json.opt(response,"nextCursor",""));mergeDetail(response.getAsJsonObject("detail"));}
        nextPageAt = page < refreshThrough ? System.currentTimeMillis() + 600 : 0;
        if(!itemId.isEmpty()&&response.has("names")&&data.has("names")){var names=data.getAsJsonObject("names").deepCopy();response.getAsJsonObject("names").entrySet().forEach(e->names.add(e.getKey(),e.getValue()));response.add("names",names);}
        data = response; status = "";
        if (!retainChoices) {
            choices.clear();
            if (data.has("detail") && data.getAsJsonObject("detail").has("myVote"))
                for (var choice : data.getAsJsonObject("detail").getAsJsonArray("myVote")) choices.add(choice.getAsInt());
        }
        rows.clear();
        if (data.has("detail")) detail(data.getAsJsonObject("detail")); else list();
        refreshUi();restore(anchor);
    }
    private void mergeList(JsonObject response) {
        var incoming = response.getAsJsonArray("entries"); pages.put(page, incoming.deepCopy());
        String next=Json.opt(response,"nextCursor","");cursors.put(page+1,next);
        if (appendPage) { loadedPage = page; appendPage = false; }
        if (next.isEmpty()) {
            pages.keySet().removeIf(k -> k > page);
            loadedPage = Math.min(loadedPage, page); refreshThrough = Math.min(refreshThrough, page);
        }
        if (page == loadedPage) hasMore = !next.isEmpty();
        var merged = new JsonArray(); var ids = new HashSet<String>();
        for (var batch : pages.values()) for (var entry : batch)
            if (ids.add(Json.str(entry.getAsJsonObject(), "id"))) merged.add(entry);
        response.add("entries", merged);
    }
    private void mergeDetail(JsonObject detail) {
        detailPages.put(page, detail.deepCopy());
        if (appendPage) { loadedPage = page; appendPage = false; }
        List<String> fields = List.of("responses", "members", "applications", "participants", "invitations");
        boolean remaining = !cursors.getOrDefault(page+1,"").isEmpty();
        if (page == loadedPage) hasMore = remaining;
        if (!remaining) {
            detailPages.tailMap(page, false).clear();
            loadedPage = Math.min(loadedPage, page); refreshThrough = Math.min(refreshThrough, page);
        }
        for (String key : fields) if (detail.has(key + "Count")) {
            var merged = new JsonObject();
            for (var batch : detailPages.values()) if (batch.has(key))
                for (var entry : batch.getAsJsonObject(key).entrySet()) merged.add(entry.getKey(), entry.getValue());
            detail.add(key, merged);
        }
    }
    void text(String text){for(String line:text.split("\n",-1)){if(line.isEmpty()){rows.add(new Row("",null));continue;}for(var part:font.getSplitter().splitLines(line,Math.max(40,contentWidth()-24),net.minecraft.network.chat.Style.EMPTY))rows.add(new Row(part.getString(),null));}}
    void action(String name,Runnable action){rows.add(new Row(name,action));}
    private void list(){
        if(section.equals("home")){text("Ваши объявления, заявки и ближайшие дела");if(data.has("preferences")){var prefs=data.getAsJsonObject("preferences");if(prefs.has("favorites"))for(var key:prefs.getAsJsonArray("favorites")){String k=key.getAsString();action("★ "+name(k),()->navigate(k));}}}
        var entries=data.getAsJsonArray("entries");if(entries==null||entries.isEmpty()){text(query.isBlank()?(mine?"У вас пока нет записей в этом разделе.":emptyText()):"Ничего не найдено. Попробуйте другой запрос.");return;}
        for(var e:entries){var j=e.getAsJsonObject();String label=Json.str(j,"title");if(section.equals("notifications")){label=(j.get("read").getAsBoolean()?"":"● ")+label;action(label,()->{var read=new JsonObject();read.addProperty("action","community");read.addProperty("section","notifications");read.addProperty("op","read");read.addProperty("id",Json.str(j,"id"));ServerMenuClient.request(read);String target=Json.opt(j,"target","");if(Json.str(j,"section").equals("help")&&!target.isEmpty()){FeatureListScreen.openReport(this,target);return;}if(target.isEmpty()){minecraft.setScreen(new ServerMenuScreen(this,"help"));return;}minecraft.setScreen(new CommunityScreen(this,Json.str(j,"section"),target));});}
            else action(label+" · "+status(Json.str(j,"status")),()->{var screen=new CommunityScreen(this,Json.str(j,"section"),Json.str(j,"id"));screen.invitationTarget=invitationTarget;minecraft.setScreen(screen);});}
    }
    static String status(String value){return switch(value){case "open"->"Открыто";case "closed"->"Закрыто";case "new"->"Новое";case "discussion"->"Обсуждается";case "planned"->"Запланировано";case "done"->"Выполнено";case "declined"->"Отклонено";case "pending"->"Ожидает ответа";case "accepted"->"Принято";case "cancelled"->"Отменено";case "hidden"->"Скрыто";case "deleted"->"В корзине";default->value;};}
    static String local(long epoch){return DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm z").format(Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()));}
    void detail(JsonObject j){
        moreActions.clear();
        if(Json.str(j,"status").equals("deleted")){text("Удалено. Восстановление доступно до "+local(j.get("deletedAt").getAsLong()+30L*86400000));if(can(j,"restore"))action("Восстановить",()->send("restore",new JsonObject()));return;}
        if(!section.equals("groups")||groupTab.equals("overview")){text(Json.str(j,"description"));LocationActions.render(this,j);}
        boolean manage=j.get("manage").getAsBoolean();
        switch(section){case "board" -> BoardSection.render(this,j);case "ideas" -> IdeasSection.render(this,j);case "polls" -> PollsSection.render(this,j);case "events" -> EventsSection.render(this,j);case "groups" -> GroupsSection.render(this,j);}
        if(data.has("related")&&(!section.equals("groups")||groupTab.equals("overview")))for(var related:data.getAsJsonArray("related")){var child=related.getAsJsonObject();action(name(Json.str(child,"section"))+": "+Json.str(child,"title"),()->minecraft.setScreen(new CommunityScreen(surface(),Json.str(child,"section"),Json.str(child,"id"))));}
        if(can(j,"edit"))secondary("Редактировать",()->edit(j));
        if(!owner(j))secondary("Пожаловаться",()->minecraft.setScreen(new ReportScreen(surface(),name(section)+": "+Json.str(j,"title")+" ["+itemId+"]\n")));
        if(can(j,"location"))secondary("Изменить место…",()->{var preset=new JsonObject();preset.add("location",j.has("location")?j.get("location"):JsonNull.INSTANCE);form("Место","location",List.of(),preset);});
        if(j.has("history"))secondary("История изменений",()->{var labels=new ArrayList<String>();for(var change:j.getAsJsonArray("history")){var h=change.getAsJsonObject();labels.add(local(h.get("at").getAsLong())+" · "+Json.str(h,"author")+" · "+operationLabel(Json.str(h,"operation")));}minecraft.setScreen(new ChoicePopup(surface(),"Последние изменения",labels,i->{}));});
        if(can(j,"delete"))secondary("Удалить…",()->confirm("Удалить? Восстановление доступно 30 дней.","delete",new JsonObject()));
        if(can(j,"hide"))secondary("Скрыть запись",()->form("Модерация","hide",List.of(new Field("text","Причина",500)),new JsonObject()));
    }
    void secondary(String label,Runnable action){moreActions.add(new Row(label,action));}
    private void showMore(){var actions=List.copyOf(moreActions);minecraft.setScreen(new ChoicePopup(surface(),"Действия",actions.stream().map(Row::label).toList(),i->actions.get(i).click.run()));}
    private void edit(JsonObject item){var fields=new ArrayList<Field>();fields.add(new Field("title","Название",100));fields.add(new Field("description","Описание",1500));var preset=new JsonObject();for(String key:List.of("title","description","type","location"))if(item.has(key))preset.add(key,item.get(key).deepCopy());if(section.equals("groups"))fields.add(new Field("type","Тип",40,data.getAsJsonObject("config").getAsJsonArray("groupTypes")));form("Редактировать запись","edit",fields,preset);}
    private static String operationLabel(String op){return switch(op){case "create"->"Создано";case "edit"->"Отредактировано";case "withdrawResponse"->"Отозван отклик";case "withdrawApplication"->"Отозвана заявка";case "revokeInvitation"->"Отменено приглашение";case "delete"->"Удалено";case "restore"->"Восстановлено";case "role"->"Изменено руководство / роль";default->"Обновлено";};}
    String shortName(String uuid){if(uuid.equals(me()))return Json.opt(ServerMenuClient.state,"name","Вы");if(data.has("names")&&data.getAsJsonObject("names").has(uuid))return data.getAsJsonObject("names").get(uuid).getAsString();return uuid.substring(0,8);}
    void confirm(String title,String op,JsonObject body){minecraft.setScreen(new ConfirmScreen(yes->{minecraft.setScreen(surface());if(yes)send(op,body);},Component.literal(title),Component.literal(data.has("detail")?Json.str(data.getAsJsonObject("detail"),"title"):"")));}
    void form(String title,String op,List<Field> fields,JsonObject preset){var initial=preset.deepCopy();if(data.has("detail"))initial.add("revision",data.getAsJsonObject("detail").get("revision"));minecraft.setScreen(new CommunityForm(this,title,fields,initial,body->submitForm(op,body)));}
    private void submitForm(String op,JsonObject body){body.addProperty("action","community");body.addProperty("section",section);body.addProperty("id",itemId);body.addProperty("op",op);ServerMenuClient.request(body);}
    private void create(){var fields=new ArrayList<Field>();fields.add(new Field("title","Название",100));fields.add(new Field("description","Описание",1500));var preset=new JsonObject();switch(section){
        case "board"->{fields.add(new Field("days","Срок (дней, 1–90)",2));preset.addProperty("days","14");var categories=data.getAsJsonObject("config").getAsJsonArray("categories");if(!categories.isEmpty()){var options=new JsonArray();var empty=new JsonObject();empty.addProperty("value","");empty.addProperty("label","Без категории");options.add(empty);options.addAll(categories);fields.add(new Field("category","Категория",40,options));}}
        case "groups"->fields.add(new Field("type","Тип",40,data.getAsJsonObject("config").getAsJsonArray("groupTypes")));
        case "events"->{fields.add(new Field("startsAt","Начало (местное время: yyyy-MM-dd HH:mm)",30));fields.add(new Field("capacity","Места (0 — до 300)",3));preset.addProperty("capacity","0");}
        case "polls"->{fields.add(new Field("endsAt","Завершение (местное время: yyyy-MM-dd HH:mm)",30));fields.add(new Field("options","Варианты через | (2–8)",808));for(String key:List.of("multiple","changeVote","liveResults")){var options=new JsonArray();options.add("Нет");options.add("Да");fields.add(new Field(key,key.equals("multiple")?"Несколько вариантов":key.equals("changeVote")?"Разрешить изменить голос":"Показывать результаты сразу",10,options));}}
    }if(Set.of("board","events").contains(section)&&data.has("groups")&&!data.getAsJsonArray("groups").isEmpty()){var options=new JsonArray();var none=new JsonObject();none.addProperty("value","");none.addProperty("label","Без объединения");options.add(none);options.addAll(data.getAsJsonArray("groups"));fields.add(new Field("group","Объединение",40,options));}form("Создать: "+name(section),"create",fields,preset);}
    static String optionValue(JsonElement option){return option.isJsonObject()?Json.str(option.getAsJsonObject(),"value"):option.getAsString();}
    static String optionLabel(JsonElement option){return option.isJsonObject()?Json.str(option.getAsJsonObject(),"label"):option.getAsString();}
    record Field(String key,String label,int limit,JsonArray options){Field(String key,String label,int limit){this(key,label,limit,null);}}
    @Override protected void onScrollEnd(){nextPage();}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){
        if(x<left()){navOffset=Math.max(0,navOffset+(dy<0?1:-1));refreshUi();return true;}
        if(splitLayout()&&x>=detailLeft()){scrollDetail(-dy*3);return true;}
        return super.mouseScrolled(x,y,dx,dy);
    }
    @Override public boolean mouseClicked(double x,double y,int button){var child=selected();if(splitLayout()&&child!=null&&button==0&&x>=width-24&&x<=width-16&&y>=child.bodyTop()&&y<height-82){detailDragging=true;seekDetail(y);return true;}return super.mouseClicked(x,y,button);}
    private void seekDetail(double y){var child=selected();if(child==null)return;int track=height-82-child.bodyTop(),thumb=Math.max(12,track*child.visibleRows/Math.max(1,child.rows.size()));detailPosition=0;scrollDetail((y-child.bodyTop()-thumb/2.0)/Math.max(1,track-thumb)*Math.max(0,child.rows.size()-child.visibleRows));}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(detailDragging&&button==0){seekDetail(y);return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){detailDragging=false;return super.mouseReleased(x,y,button);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==256&&selected()!=null){expanded.clear();refreshUi();return true;}if(splitLayout()&&selected()!=null&&getFocused() instanceof AbstractWidget widget&&widget.getX()>=detailLeft()){if(key==266||key==267){scrollDetail((key==266?-1:1)*selected().visibleRows);return true;}}return super.keyPressed(key,scan,modifiers);}
    @Override public void render(GuiGraphics g,int x,int y,float d){super.render(g,x,y,d);g.drawString(font,font.plainSubstrByWidth(name(section),Math.max(40,width-240)),8,18,0xE2BE75);int top=contentTop();if(!cards())for(int n=firstRow;n<Math.min(rows.size(),firstRow+visibleRows);n++)if(rows.get(n).click==null&&rows.get(n).progress<0)g.drawString(font,rows.get(n).label,left()+4,top+(n-firstRow)*24+6,0xEEEEEE);Ui.status(g,font,status,left(),contentBottom()+4,contentWidth(),height-30);}
    @Override public void tick(){if(inlineParent==null)for(var child:expanded.values())child.tick();if(notificationButton!=null){int unread=ServerMenuClient.state.has("unread")?ServerMenuClient.state.get("unread").getAsInt():0;notificationButton.setMessage(Component.literal((width<420?"●":"Уведомления")+(unread>0?" ("+unread+")":"")));}if(!ServerMenuClient.available())minecraft.setScreen(null);else if(session.timeout(System.currentTimeMillis())){busy=false;uncertain=true;nextPageAt=0;appendPage=false;status="Нет ответа. Нажмите «Повторить».";refreshUi();}else if(!busy&&nextPageAt>0&&System.currentTimeMillis()>=nextPageAt){nextPageAt=0;page++;send(itemId.isEmpty()?"list":"detail",new JsonObject());}else if(!busy&&!uncertain&&dirtyAt>0&&System.currentTimeMillis()-sentAt>750){dirtyAt=0;load();}}
    @Override public void onClose(){if(parent instanceof CommunityScreen screen)screen.invalidate();minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
