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
    record Row(String label,Runnable click,double progress,List<Row> buttons,Boolean checked){Row(String label,Runnable click,double progress,List<Row> buttons){this(label,click,progress,buttons,null);}Row(String label,Runnable click){this(label,click,-1,List.of());}Row(String label,Runnable click,double progress){this(label,click,progress,List.of());}}
    private final Screen parent;
    final String section;
    final String itemId;JsonObject invitationTarget;private String memberFilter="",sort="default";
    static void groupsFor(Screen parent,String member){var screen=new CommunityScreen(parent,"groups","");screen.memberFilter=member;net.minecraft.client.Minecraft.getInstance().setScreen(screen);}
    static void invite(Screen parent,JsonObject target){var screen=new CommunityScreen(parent,"groups","");screen.invitationTarget=target;net.minecraft.client.Minecraft.getInstance().setScreen(screen);}
    JsonObject data=new JsonObject();
    final List<Row> rows=new ArrayList<>();
    private final List<int[]> rowCards=new ArrayList<>();
    int beginCard(){return rows.size();}
    void endCard(int start){rowCards.add(new int[]{start,rows.size()});text("");}
    private void renderRowCards(GuiGraphics g,int x,int top,int w,int bottom){g.enableScissor(x-4,top,x+w+4,bottom);for(var card:rowCards){int y=top+(card[0]-firstRow)*24,end=top+(card[1]-firstRow)*24;if(end<=top||y>=bottom)continue;g.fill(x-4,y-2,x+w+4,end,AccessibilityScreen.background(UiPalette.color(0xB02D3B46)));g.renderOutline(x-4,y-2,w+8,end-y+2,UiPalette.color(0xFF526674));}g.disableScissor();}
    static void clearDrafts(){}
    private final dev.abros.anthub.core.CommunityWorkspace model;
    void invalidate(){if(inlineParent!=null)inlineParent.model.invalidate();model.invalidate();for(var child:expanded.values())child.invalidate();}
    void invalidate(String topic,String id){if(!home()&&!topic.isEmpty()&&!topic.equals(section))return;if(!id.isEmpty()&&!itemId.isEmpty()&&!itemId.equals(id)&&!topic.equals("home"))return;model.invalidate();for(var child:expanded.values())if(id.isEmpty()||child.itemId.equals(id))child.invalidate(topic,id);}
    void cancelRead(){model.leave();ServerMenuClient.cancelReads(surface());}
    final Set<Integer> choices=new LinkedHashSet<>();
    private String query="";String status="";
    private boolean archive,trash,participating;String groupTab="overview";private boolean detailDragging;private double detailPosition;
    private final List<Row> moreActions=new ArrayList<>();private final Set<Integer> dangerousActions=new HashSet<>();
    boolean mine,loaded,quickAction,choicesDirty;private boolean controlsBusy;
    private EditBox search;private Button notificationButton;private final MenuSidebar navigation=new MenuSidebar(this);
    private CommunityScreen inlineParent;
    private final LinkedHashMap<String,CommunityScreen> expanded=new LinkedHashMap<>();
    Screen surface(){return inlineParent==null?this:inlineParent;}
    void refreshUi(){if(inlineParent!=null){inlineParent.refreshUi();return;}boolean typing=search!=null&&getFocused()==search;int cursor=typing?search.getCursorPosition():0;rebuildWidgets();if(typing&&search!=null){setFocused(search);search.setCursorPosition(cursor);}}
    private void prepareInline(CommunityScreen host){inlineParent=host;minecraft=host.minecraft;font=host.font;width=host.width;height=host.height;}
    private record Anchor(String id,int offset){}
    private Anchor anchor(){if(!data.has("entries"))return null;var entries=displayEntries();if(entries.isEmpty())return null;int n=Math.min(firstRow,entries.size()-1);return new Anchor(Json.str(entries.get(n).getAsJsonObject(),"id"),0);}
    private void restore(Anchor anchor){if(anchor==null||!data.has("entries"))return;int n=0;for(var entry:displayEntries()){if(Json.str(entry.getAsJsonObject(),"id").equals(anchor.id)){if(firstRow!=n){restoreScroll(n);refreshUi();}return;}n++;}}
    private UiListDetail masterDetail(){return UiListDetail.fit(new dev.abros.anthub.core.NativeLayout.Box(left(),bodyTop(),Math.min(880,navigation.right()-left()-20),Math.max(0,height-bodyTop()-82)),230,height>=300);}
    private boolean splitLayout(){return inlineParent==null&&itemId.isEmpty()&&!home()&&masterDetail().split();}
    private int listWidth(){return masterDetail().list().width();}
    private int detailLeft(){return left()+listWidth()+12;}
    private CommunityScreen selected(){return expanded.isEmpty()?null:expanded.values().iterator().next();}
    private void inlineWidgets(int top){
        var entries=displayEntries();int bottom=height-(home()?40:splitLayout()?82:60),stride=Math.min(home()&&height<320?56:AccessibilityScreen.cardStride(),Math.max(24,bottom-top));scrollArea(entries.size(),new dev.abros.anthub.core.NativeLayout.Box(left(),top,Math.max(0,contentWidth()),Math.max(0,(bottom)-(top))),stride);
        for(int n=firstRow;n<Math.min(entries.size(),firstRow+visibleRows);n++){
            var entry=entries.get(n).getAsJsonObject();
            var card=new CommunityCard(left(),top+(n-firstRow)*stride,contentWidth(),stride-6,entry,()->openEntry(entry));card.selected=selected()!=null&&selected().itemId.equals(Json.str(entry,"id"));addRenderableWidget(card);
        }
        if(splitLayout())detailWidgets();
    }
    private void detailWidgets(){
        var child=selected();if(child==null)return;child.prepareInline(this);child.rows.clear();
        if(child.data.has("detail"))child.detail(child.data.getAsJsonObject("detail"));
        int x=detailLeft(),w=masterDetail().detail().width(),top=child.bodyTop();
        child.visibleRows=Math.max(1,(height-82-top)/24);child.firstRow=Math.max(0,Math.min(child.firstRow,Math.max(0,child.rows.size()-child.visibleRows)));
        child.detailControls(this,x,contentTop(),w);drawRowWidgets(child,x+8,top,w-16);
        var footer=UiPageFooter.fit(new dev.abros.anthub.core.NativeLayout.Box(x,height-54,w,20));
        if(child.model.uncertain())footer.start(UiActions.Command.RETRY,this::addRenderableWidget,child::retryOrLoad);else if(child.model.more())button("Загрузить ещё",footer.start().x(),footer.start().y(),footer.start().width(),child::retryOrLoad);
        footer.end(UiActions.Command.CLOSE,this::addRenderableWidget,()->{expanded.clear();refreshUi();});
    }
    private void retryOrLoad(){if(model.busy())return;if(model.uncertain())model.retry();else if(model.more())nextPage();else load();status=model.status();}

    private int contentBottom(){return height-(!itemId.isEmpty()&&inlineParent==null?58:82);}
    private int bodyTop(){return (inlineParent==null?40:inlineParent.contentTop())+(section.equals("groups")?76:48);}
    private void drawRowWidgets(CommunityScreen child,int x,int top,int w){
        for(int n=child.firstRow;n<Math.min(child.rows.size(),child.firstRow+child.visibleRows);n++){
            var row=child.rows.get(n);int y=top+(n-child.firstRow)*24;
            if(!row.buttons.isEmpty()){int bw=Math.min((w-4*(row.buttons.size()-1))/row.buttons.size(),row.buttons.stream().mapToInt(a->font.width(a.label)+20).max().orElse(100));for(int i=0;i<row.buttons.size();i++){var action=row.buttons.get(i);var control=button(font.plainSubstrByWidth(action.label,bw-12),x+i*(bw+4),y,bw,()->{if(!child.model.busy())action.click.run();});control.setTooltip(control.getMessage().getString().equals(action.label)?null:Tooltip.create(Component.literal(action.label)));child.controlsBusy=child.model.busy();control.active=!child.model.busy();}}
            else if(row.checked!=null){var bar=new UiChoiceRow(x,y,w,row.label,row.checked,row.progress,0xFFB49AE8,()->{if(!child.model.busy()&&row.click!=null)row.click.run();});bar.active=!child.model.busy()&&row.click!=null;child.controlsBusy=child.model.busy();addRenderableWidget(bar);}
            else if(row.click!=null){int buttonWidth=child.itemId.isEmpty()?w:Math.min(w,Math.max(88,Math.min(240,font.width(row.label)+24)));var control=button(font.plainSubstrByWidth(row.label,buttonWidth-12),x,y,buttonWidth,()->{if(!child.model.busy())row.click.run();});control.setTooltip(control.getMessage().getString().equals(row.label)?null:Tooltip.create(Component.literal(row.label)));child.controlsBusy=child.model.busy();control.active=!child.model.busy();}
        }
    }
    private void detailControls(CommunityScreen host,int x,int y,int w){
        if(!moreActions.isEmpty())host.button("Действия ▾",x+w-90,y+4,82,this::showMore);
        if(section.equals("groups")&&data.has("detail")){
            boolean manage=data.getAsJsonObject("detail").get("manage").getAsBoolean();
            var tabs=plus()?manage?List.of("overview","members","tasks","places","requests"):List.of("overview","members","tasks","places"):manage?List.of("overview","members","requests"):List.of("overview","members");
            var labels=tabs.stream().map(CommunityScreen::groupTabLabel).toList();
            UiTabs.build(host,font,new dev.abros.anthub.core.NativeLayout.Box(x+8,y+40,w-16,20),labels,tabs.indexOf(groupTab),host::addRenderableWidget,n->{groupTab=tabs.get(n);resetScroll();if(inlineParent!=null)inlineParent.detailPosition=0;refreshUi();},!model.busy());

        }
    }
    private static String groupTabLabel(String tab){return switch(tab){case "overview"->"Обзор";case "members"->"Участники";case "tasks"->"Задачи";case "places"->"Места";default->"Заявки";};}
    private void renderDetailPane(GuiGraphics g){
        int x=detailLeft(),w=masterDetail().detail().width();g.fill(x,contentTop(),x+w,height-82,AccessibilityScreen.background(UiPalette.color(0xD01B252E)));var child=selected();
        if(child==null){Ui.text(g,font,"Выберите запись",x+10,contentTop()+12,UiPalette.color(0xBAC6D2));return;}
        if(child.data.has("detail")){var j=child.data.getAsJsonObject("detail");Ui.text(g,font,font.plainSubstrByWidth(Json.str(j,"title"),w-104),x+8,contentTop()+8,UiKit.text(),false);Ui.text(g,font,font.plainSubstrByWidth(child.detailStatus(j),w-16),x+8,contentTop()+24,child.accent());}
        int top=child.bodyTop();child.renderRowCards(g,x+8,top,w-16,height-82);g.enableScissor(x,top,x+w,height-82);
        for(int n=child.firstRow;n<Math.min(child.rows.size(),child.firstRow+child.visibleRows);n++){var row=child.rows.get(n);if(row.click==null&&row.progress<0&&row.buttons.isEmpty()&&row.checked==null)Ui.text(g,font,row.label,x+8,top+(n-child.firstRow)*24+6,AccessibilityScreen.foreground(UiPalette.color(0xEEEEEE)));}
        g.disableScissor();
        UiScrollbar.draw(g,dev.abros.anthub.core.ScrollLayout.fit(new dev.abros.anthub.core.NativeLayout.Box(x+8,top,Math.max(0,w-16),Math.max(0,height-82-top))).track(),child.visibleRows,child.rows.size(),child.firstRow);
        if(!child.status.isEmpty())Ui.status(g,font,child.status,x,height-78,w,height-56);
    }
    private String detailStatus(JsonObject j){return section.equals("groups")&&Json.str(j,"status").equals("open")?(j.has("recruiting")&&j.get("recruiting").getAsBoolean()?"Набор открыт":"Набор закрыт"):status(Json.str(j,"status"));}
    private void scrollDetail(double delta){var child=selected();if(child==null)return;detailPosition=Math.max(0,Math.min(detailPosition+delta,Math.max(0,child.rows.size()-child.visibleRows)));child.firstRow=(int)detailPosition;refreshUi();if(child.firstRow+child.visibleRows>=child.rows.size())child.nextPage();}
    boolean leavePage(){if(!model.leave())return false;for(var child:expanded.values())if(!child.leavePage())return false;ServerMenuClient.cancelReads(surface());return true;}

    void refreshPermissions(){refreshUi();}
    private static final Map<String,String> NAMES=Map.ofEntries(Map.entry("home","Главная"),Map.entry("tasks","Мои задачи"),Map.entry("players","Игроки"),Map.entry("board","Доска объявлений"),Map.entry("groups","Объединения"),Map.entry("events","События"),Map.entry("polls","Голосования"),Map.entry("ideas","Предложения"),Map.entry("notifications","Уведомления"),Map.entry("info","Сервер"),Map.entry("help","Помощь"),Map.entry("admin","Администрирование"));
    CommunityScreen(Screen parent,String section,String id){super(Component.literal(name(section)));this.parent=parent;this.section=section;itemId=id;model=new dev.abros.anthub.core.CommunityWorkspace(section,id,ServerMenuClient::request,System::currentTimeMillis);}
    static String name(String section){if(section.equals("groups")&&ServerMenuClient.state.has("communityConfig"))return Json.opt(ServerMenuClient.state.getAsJsonObject("communityConfig"),"groupsTitle",NAMES.get(section));return NAMES.getOrDefault(section,section);}
    static void open(Screen parent,String section){net.minecraft.client.Minecraft.getInstance().setScreen(new CommunityScreen(parent,section,""));}
    static boolean plus(){return ServerMenuClient.supports("community-plus");}
    static String me(){return Json.opt(ServerMenuClient.state,"uuid","");}
    static boolean can(JsonObject item,String action){return item.has("actions")&&item.getAsJsonArray("actions").contains(new JsonPrimitive(action));}
    boolean owner(JsonObject j){return me().equals(Json.opt(j,"owner",""));}
    private int navigationLeft(){return navigation.left();}
    private int left(){if(inlineParent!=null)return inlineParent.detailLeft();int base=navigationLeft();return home()?base+Math.max(0,(navigation.right()-base-20-864)/2):base;}
    private int profileLeft(){return left()+contentWidth()+12;}
    private boolean home(){return section.equals("home")&&itemId.isEmpty();}
    private int groupScroll;
    private boolean sideProfile(){return home()&&height>=360&&navigation.right()-left()>=490;}
    private int contentWidth(){if(inlineParent!=null)return inlineParent.masterDetail().detail().width()-8;if(splitLayout())return listWidth();return home()?Math.min(640,navigation.right()-left()-20-(sideProfile()?224:0)):Math.min(680,navigation.right()-left()-20);}
    private Button button(String label,int x,int y,int w,Runnable callback){return button(label,UiActions.Tone.NORMAL,"",x,y,w,callback);}
    private Button button(String label,UiActions.Tone tone,String icon,int x,int y,int w,Runnable callback){return addRenderableWidget(UiActions.button(Component.literal(label),tone,icon,b->callback.run()).bounds(x,y,w,20).build());}
    @Override protected void init(){controlsBusy=model.busy();
        if(data.has("detail")){rows.clear();detail(data.getAsJsonObject("detail"));}else if(data.has("entries")){rows.clear();list();}
        int navWidth=navigationLeft()-20;
        notificationButton=null;
        navigation.build(section,control->{addRenderableWidget(control);if(control.getMessage().getString().equals(MenuSidebar.inboxLabel(width)))notificationButton=control;});
        UiPageFooter.workspace(width,height).end(UiActions.Command.BACK,this::addRenderableWidget,this::onClose);
        if(itemId.isEmpty()&&!section.equals("home")){
            search=UiSearchToolbar.build(this,font,left(),76,contentWidth(),query,mine?1:participating?2:0,sort,archive||trash,this::addRenderableWidget,v->query=v,this::resetList,n->{mine=n==1;participating=n==2;resetList();},v->{sort=v;resetList();},()->{query="";mine=false;participating=false;archive=false;trash=false;sort="default";resetList();refreshUi();},!model.busy());

        }
        if(home()){
            if(TaskScreen.available()){
                button("Поиск по серверу",UiActions.Tone.NORMAL,UiIcons.SEARCH,left(),76,Math.min(140,contentWidth()),()->minecraft.setScreen(new GlobalSearchScreen(this)));if(contentWidth()>=190)button("⋯",left()+148,76,24,()->minecraft.setScreen(new HomeWidgetsScreen(this)));
            }
            if(sideProfile()){
                int px=profileLeft(),by=184;
                if(SkinClient.available())button("Скины",px+9,by,58,()->minecraft.setScreen(new QuickSkinsScreen(this)));
                if(plus())button("О себе",px+71,by,62,()->minecraft.setScreen(new PersonalProfileScreen(this)));
                button("Интерфейс",px+137,by,66,()->minecraft.setScreen(new AccessibilityScreen(this)));
                if(AuthClient.available())button("Безопасность",px+9,by+24,194,()->AuthAccountScreen.open(this));
                groupWidgets();
            }
        }
        int top=contentTop();
        if(cards()){
            inlineWidgets(top);
        }else{
            scrollArea(rows.size(),new dev.abros.anthub.core.NativeLayout.Box(left(),top,Math.max(0,contentWidth()),Math.max(0,(contentBottom())-(top))),24);
            drawRowWidgets(this,left()+12,top,contentWidth()-24);
            if(!itemId.isEmpty())detailControls(this,left(),40,contentWidth());else if(splitLayout())detailWidgets();
        }
        if(itemId.isEmpty()){
            if(!home()&&!section.equals("notifications"))button((trash?"Удалённые":archive?"Архив":"Активные")+" ▾",navigation.right()-120,44,100,()->{if(!model.busy())minecraft.setScreen(new ChoicePopup(this,"Записи",List.of("Активные","Архив","Удалённые · 30 дней"),i->{archive=i==1;trash=i==2;resetList();}).anchorLabel((trash?"Удалённые":archive?"Архив":"Активные")+" ▾").current(trash?2:archive?1:0));});
            if(data.has("canCreate")&&data.get("canCreate").getAsBoolean()&&Set.of("board","groups","events","polls","ideas").contains(section))button(createLabel(),UiActions.Tone.PRIMARY,UiIcons.PLUS,left(),height-54,Math.min(170,Math.max(60,contentWidth()-62)),this::create);
            if(section.equals("notifications"))button("Прочитать всё",left(),height-54,125,()->send("read",new JsonObject()));
        }
        UiPageFooter.workspace(width,height).start(model.uncertain()?UiActions.Command.RETRY:UiActions.Command.REFRESH,this::addRenderableWidget,()->{if(model.uncertain()){model.retry();status=model.status();}else{load();if(selected()!=null)selected().load();}});
        if(!loaded){loaded=true;load();}
    }
    private int contentTop(){return home()?(TaskScreen.available()?104:76):itemId.isEmpty()?132:bodyTop();}
    private JsonArray displayEntries(){
        var result=new JsonArray();
        if(home()&&data.has("pinnedAnnouncement")){var pin=data.getAsJsonObject("pinnedAnnouncement");if(pin.get("until").getAsLong()>System.currentTimeMillis()&&!Json.str(pin,"text").isBlank()){
            var row=new JsonObject();row.addProperty("id","local:pinned");row.addProperty("section","home");row.addProperty("title","Объявление администрации");row.addProperty("preview",Json.str(pin,"text"));row.addProperty("attention","Закреплено");row.addProperty("status","Открыть целиком");result.add(row);
        }}
        if(itemId.isEmpty()&&ModerationVoteScreen.enabled()&&(section.equals("polls")||home()&&Json.opt(ServerMenuClient.moderationVote,"status","").equals("open"))){var row=new JsonObject();row.addProperty("id","local:moderation-vote");row.addProperty("section","moderation");row.addProperty("title",Json.opt(ServerMenuClient.moderationVote,"status","").equals("open")?"Наказание: "+Json.opt(ServerMenuClient.moderationVote,"name",""):"Нарушение правил");row.addProperty("preview",Json.opt(ServerMenuClient.moderationVote,"status","").equals("open")?Json.opt(ServerMenuClient.moderationVote,"reason",""):"Кик, временный бан или голосовой мут");row.addProperty("attention","Нарушения правил");row.addProperty("footer",Json.opt(ServerMenuClient.moderationVote,"status","").equals("open")?"Идёт голосование · открыть":"Открыть голосование");result.add(row);}
        if(data.has("entries")){if(home()){int count=0;for(var entry:data.getAsJsonArray("entries")){var e=entry.getAsJsonObject();if(Json.opt(e,"section","").equals("events")&&(e.has("isParticipant")&&e.get("isParticipant").getAsBoolean()||e.has("isOwner")&&e.get("isOwner").getAsBoolean()||e.has("isSubscribed")&&e.get("isSubscribed").getAsBoolean())&&count++<3)result.add(entry);}}else{for(var entry:data.getAsJsonArray("entries")){if(section.equals("polls")){var poll=entry.getAsJsonObject().deepCopy();poll.addProperty("attention","Сообщество · голосование");result.add(poll);}else result.add(entry);}}}
        if(home()){
            if(data.has("tasks"))for(var task:data.getAsJsonArray("tasks")){var t=task.getAsJsonObject();var row=t.deepCopy();row.addProperty("id","local:task:"+Json.str(t,"id"));row.addProperty("section","task");row.addProperty("preview",Json.opt(t,"groupName","")+" · "+Json.opt(t,"description",""));long due=t.get("dueAt").getAsLong();row.addProperty("attention",due>0?(due<System.currentTimeMillis()?"Просрочено · ":"Срок · ")+local(due):"Моя задача");result.add(row);}
            if(!sideProfile()){
                var profile=new JsonObject();profile.addProperty("id","local:profile");profile.addProperty("section","profile");profile.addProperty("title",Json.opt(ServerMenuClient.state,"name","Мой профиль"));profile.addProperty("attention","Мой профиль");profile.addProperty("preview","Сессия: "+dev.abros.anthub.core.DisplayCounts.text(ServerMenuClient.state,"sessionSeconds","0")+" с");result.add(profile);

            }
        }if(home()){if(data.has("invitations"))for(var invitation:data.getAsJsonArray("invitations"))result.add(invitation);if(data.has("groups"))for(var group:data.getAsJsonArray("groups"))result.add(groupCard(group.getAsJsonObject()));return HomeLayout.arrange(result);}return result;
    }
    private boolean cards(){return itemId.isEmpty()&&!section.equals("notifications")&&data.has("entries");}
    private void openEntry(JsonObject entry){String id=Json.str(entry,"id");
        if(id.equals("local:profile")){profileActions();return;}
        if(id.startsWith("local:task:")&&TaskScreen.available()){minecraft.setScreen(new TaskScreen(this,Json.opt(entry,"group",""),id.substring("local:task:".length())));return;}if(id.startsWith("local:task:")){var group=new CommunityScreen(this,"groups",Json.str(entry,"group"));group.groupTab="tasks";minecraft.setScreen(group);return;}
        if(id.equals("local:pinned")){minecraft.setScreen(new TextScreen(this,Component.literal("Объявление администрации"),Json.str(entry,"preview")+"\n\n"+Json.str(entry,"attention")));return;}
        if(id.equals("local:moderation-vote")){minecraft.setScreen(new ModerationVoteScreen(this,null));return;}
        if(selected()!=null&&selected().itemId.equals(id))return;
        var child=new CommunityScreen(this,Json.str(entry,"section"),id);child.invitationTarget=invitationTarget;
        if(!splitLayout()){minecraft.setScreen(child);return;}
        expanded.clear();detailPosition=0;child.prepareInline(this);expanded.put(id,child);child.loaded=true;child.load();refreshUi();
    }


    static String statusLabel(String value){return status(value);}
    private String createLabel(){return switch(section){case "board"->"Разместить объявление";case "groups"->"Создать объединение";case "events"->"Назначить событие";case "polls"->"Создать голосование";case "ideas"->"Предложить идею";default->"Создать";};}
    private String subtitle(){return switch(section){case "home"->"Ваши события, задачи и объединения";case "board"->"Предложения игроков";case "groups"->"Найдите объединение или соберите свое";case "events"->"Встречи, участие и совместные планы";case "polls"->"Ваш голос в решениях сообщества";case "ideas"->"Идеи игроков";case "notifications"->"Ответы, приглашения и напоминания";default->name(section);};}
    private String emptyText(){return switch(section){case "home"->"Вы пока не записаны на ближайшие события.";case "board"->"Объявлений пока нет. Здесь можно найти помощь или предложить свою.";case "groups"->"Объединений пока нет. Здесь появятся команды игроков.";case "events"->"Событий пока нет. Здесь появятся предстоящие встречи.";case "polls"->"Сейчас нет голосований.";case "ideas"->"Пока нет предложений. Поделитесь идеей для сервера.";case "notifications"->"Всё прочитано. Новые ответы и приглашения появятся здесь.";default->"Пока нет записей.";};}
    private int accent(){return switch(section){case "board"->UiPalette.color(0xFFE2BE75);case "groups"->UiPalette.color(0xFF79CBA6);case "events"->UiPalette.color(0xFF82B6F2);case "polls"->UiPalette.color(0xFFB49AE8);case "ideas"->UiPalette.color(0xFFF0A77C);default->UiPalette.color(0xFF8BC7CB);};}
    private void profileActions(){var labels=new ArrayList<String>();var actions=new ArrayList<Runnable>();if(TaskScreen.available()){labels.add("Мои задачи");actions.add(()->minecraft.setScreen(new TaskScreen(this,"","")));}if(ServerMenuClient.supports("player-tools")){labels.add("Организаторы событий");actions.add(()->minecraft.setScreen(new FollowingScreen(this)));labels.add("Общедоступные места");actions.add(()->minecraft.setScreen(new PlacesScreen(this)));labels.add("Карта объединения");actions.add(()->minecraft.setScreen(new MapSharingScreen(this)));}if(plus()){labels.add("О себе");actions.add(()->minecraft.setScreen(new PersonalProfileScreen(this)));}if(SkinClient.available()){labels.add("Скины");actions.add(()->SkinsScreen.open(this));}labels.add("Виджеты главной");actions.add(()->minecraft.setScreen(new HomeWidgetsScreen(this)));labels.add("Интерфейс");actions.add(()->minecraft.setScreen(new AccessibilityScreen(this)));if(AuthClient.available()){labels.add("Безопасность");actions.add(()->AuthAccountScreen.open(this));}minecraft.setScreen(new ChoicePopup(this,"Мой профиль",labels,i->actions.get(i).run()));}
    private int groupTop(){return AuthClient.available()?256:228;}
    private int groupRows(){return Math.max(1,(height-40-groupTop())/76);}
    private JsonObject groupCard(JsonObject option){var row=new JsonObject();row.addProperty("id",Json.str(option,"value"));row.addProperty("section","groups");row.addProperty("title",Json.str(option,"label"));row.addProperty("attention","В сети: "+dev.abros.anthub.core.DisplayCounts.text(option,"onlineCount","—")+" / "+dev.abros.anthub.core.DisplayCounts.text(option,"membersCount","—"));row.addProperty("preview",option.has("nextEvent")?Json.str(option,"nextEvent"):"Нет ближайших встреч");row.addProperty("footer",option.has("applicationsCount")&&option.get("applicationsCount").getAsInt()>0?"Заявок: "+option.get("applicationsCount").getAsInt():option.has("nextEventAt")?local(option.get("nextEventAt").getAsLong()):"Открыть объединение");return row;}
    private void groupWidgets(){if(home()||!data.has("groups"))return;var groups=data.getAsJsonArray("groups");groupScroll=Math.max(0,Math.min(groupScroll,Math.max(0,groups.size()-groupRows())));for(int n=groupScroll;n<Math.min(groups.size(),groupScroll+groupRows());n++){var row=groupCard(groups.get(n).getAsJsonObject());addRenderableWidget(new CommunityCard(navigation.right()-232,groupTop()+(n-groupScroll)*76,212,70,row,()->openEntry(row)));}}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float d){
        super.renderBackground(g,x,y,d);
        if(splitLayout())renderDetailPane(g);
        if(home()&&sideProfile()){ProfilePanel.render(g,font,profileLeft(),76,212,true,data);}
        if(!data.has("detail")){g.fill(left(),40,home()?left()+contentWidth()+(sideProfile()?224:0):navigation.right()-16,70,AccessibilityScreen.background(UiPalette.color(0x901C2B36)));g.fill(left(),40,left()+2,70,accent());
        Ui.text(g,font,font.plainSubstrByWidth(subtitle(),contentWidth()-124),left()+9,51,UiPalette.color(0xBAC6D2),false);}
        if(data.has("detail")){
            var item=data.getAsJsonObject("detail");g.fill(left(),40,left()+contentWidth(),rowCards.isEmpty()?contentBottom():bodyTop()-8,AccessibilityScreen.background(UiPalette.color(0xC51B252E)));g.fill(left(),40,left()+3,bodyTop()-8,accent());renderRowCards(g,left()+12,contentTop(),contentWidth()-24,contentBottom());
            String heading=name(section)+" › "+Json.str(item,"title");String info=Json.str(item,"author")+" · "+status(Json.str(item,"status"));
            if(section.equals("groups"))info=Json.str(item,"type")+" · "+item.get("membersCount").getAsInt()+" участников";
            if(section.equals("events"))info=local(item.get("startsAt").getAsLong())+" · "+item.get("participantsCount").getAsInt()+" участников";
            if(section.equals("polls"))info="Завершение: "+local(item.get("endsAt").getAsLong());
            if(section.equals("ideas"))info=status(Json.str(item,"status"))+" · "+item.get("supportersCount").getAsInt()+" поддержали";
            Ui.text(g,font,font.plainSubstrByWidth(heading,contentWidth()-104),left()+10,46,UiKit.text(),false);Ui.text(g,font,font.plainSubstrByWidth(info,contentWidth()-20),left()+10,62,accent());
        }
    }
    private void navigate(String key){UiNavigation.open(this,key);}
    private dev.abros.anthub.core.CommunityWorkspace.Filters filters(){return new dev.abros.anthub.core.CommunityWorkspace.Filters(query,memberFilter,sort,trash,archive,mine,participating);}
    private void resetList(){if(model.busy())return;model.reset();expanded.clear();resetScroll();load();}
    private void load(){model.load(filters());status=model.status();}
    private void nextPage(){model.next(filters());status=model.status();}
    void send(String op,JsonObject body){model.send(op,body,filters());status=model.status();}
    void receive(JsonObject response){
        if(inlineParent==null)for(var child:expanded.values())if(child.model.matches(response)){var anchor=anchor();child.receive(response);restore(anchor);return;}
        var result=model.receive(response);if(!result.accepted())return;status=model.status();
        if(result.failed()){quickAction=false;if(inlineParent!=null){inlineParent.status=status;refreshUi();}else if(!result.conflict())refreshUi();return;}
        if(quickAction){quickAction=false;model.invalidate();status="Сохранено";return;}
        if(model.operation().equals("vote"))choicesDirty=false;
        boolean retainChoices=choicesDirty&&data.has("detail")&&section.equals("polls");
        var anchor=anchor();data=model.data();if(!result.changed()&&!controlsBusy)return;
        if(!retainChoices){choices.clear();if(data.has("detail")&&data.getAsJsonObject("detail").has("myVote"))for(var choice:data.getAsJsonObject("detail").getAsJsonArray("myVote"))choices.add(choice.getAsInt());}
        rows.clear();if(data.has("detail"))detail(data.getAsJsonObject("detail"));else list();refreshUi();restore(anchor);
    }
    void text(String text){for(String line:text.split("\n",-1)){if(line.isEmpty()){rows.add(new Row("",null));continue;}for(var part:font.getSplitter().splitLines(line,Math.max(40,contentWidth()-40),net.minecraft.network.chat.Style.EMPTY))rows.add(new Row(part.getString(),null));}}
    void action(String name,Runnable action){rows.add(new Row(name,action));}
    void actionRow(List<Row> actions){
        if(actions.isEmpty())return;
        int available=contentWidth()-16;var group=new ArrayList<Row>();int used=0;
        for(var action:actions){int needed=font.width(action.label)+16;if(!group.isEmpty()&&(group.size()==3||(Math.max(used,needed)+4)*(group.size()+1)-4>available)){rows.add(new Row("",null,-1,List.copyOf(group)));group.clear();used=0;}group.add(action);used=Math.max(used,needed);}
        if(!group.isEmpty())rows.add(new Row("",null,-1,List.copyOf(group)));
    }

    private void list(){
        if(section.equals("polls")&&ModerationVoteScreen.enabled())action("Нарушение правил · голосование игроков",()->minecraft.setScreen(new ModerationVoteScreen(this,null)));
        var entries=data.getAsJsonArray("entries");if(entries==null||entries.isEmpty()){text(query.isBlank()?(mine?"У вас пока нет записей в этом разделе.":emptyText()):"Ничего не найдено. Попробуйте другой запрос.");return;}
        for(var e:entries){var j=e.getAsJsonObject();String label=Json.str(j,"title");if(section.equals("notifications")){label=(j.get("read").getAsBoolean()?"":"● ")+label;action(label,()->{model.readNotice(Json.str(j,"id"));String target=Json.opt(j,"target","");if(Json.str(j,"section").equals("help")&&!target.isEmpty()){FeatureListScreen.openReport(this,target);return;}if(target.isEmpty()){minecraft.setScreen(new ServerMenuScreen(this,"help"));return;}minecraft.setScreen(new CommunityScreen(this,Json.str(j,"section"),target));});}
            else action(label+" · "+status(Json.str(j,"status")),()->{var screen=new CommunityScreen(this,Json.str(j,"section"),Json.str(j,"id"));screen.invitationTarget=invitationTarget;minecraft.setScreen(screen);});}
    }
    static String status(String value){return switch(value){case "open"->"Открыто";case "closed"->"Закрыто";case "new"->"Новое";case "discussion"->"Обсуждается";case "planned"->"Запланировано";case "working"->"В работе";case "done"->"Выполнено";case "declined"->"Отклонено";case "pending"->"Ожидает ответа";case "accepted"->"Принято";case "cancelled"->"Отменено";case "hidden"->"Скрыто";case "deleted"->"В корзине";default->value;};}
    static String local(long epoch){return DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm z").format(Instant.ofEpochMilli(epoch).atZone(AccessibilityScreen.zone()));}
    void detail(JsonObject j){
        moreActions.clear();dangerousActions.clear();rowCards.clear();
        if(Json.str(j,"status").equals("deleted")){text("Удалено. Восстановление доступно до "+local(j.get("deletedAt").getAsLong()+30L*86400000));if(can(j,"restore"))action("Восстановить",()->send("restore",new JsonObject()));return;}
        if(!section.equals("groups")||groupTab.equals("overview")){int intro=beginCard();text(Json.str(j,"description"));if(Set.of("board","events","groups").contains(section))LocationActions.render(this,j);endCard(intro);}
        boolean manage=j.get("manage").getAsBoolean();
        switch(section){case "board" -> BoardSection.render(this,j);case "ideas" -> IdeasSection.render(this,j);case "polls" -> PollsSection.render(this,j);case "events" -> EventsSection.render(this,j);case "groups" -> GroupsSection.render(this,j);}
        if(data.has("related")&&(!section.equals("groups")||groupTab.equals("overview")))for(var related:data.getAsJsonArray("related")){var child=related.getAsJsonObject();action(name(Json.str(child,"section"))+": "+Json.str(child,"title"),()->minecraft.setScreen(new CommunityScreen(surface(),Json.str(child,"section"),Json.str(child,"id"))));}
        if(plus())secondary("Поделиться в чате…",()->minecraft.setScreen(new ChatScreen("/ah share "+section+" "+itemId)));
        if(can(j,"edit"))secondary("Редактировать",()->edit(j));
        else if(Set.of("board","events","groups").contains(section)&&can(j,"location"))secondary("Место…",()->{var preset=new JsonObject();preset.add("location",j.has("location")?j.get("location"):JsonNull.INSTANCE);form("Место","location",List.of(),preset);});
        if(!owner(j))secondary("Пожаловаться",()->minecraft.setScreen(new ReportScreen(surface(),name(section)+": "+Json.str(j,"title")+" ["+itemId+"]\n")));
        if(j.has("history"))secondary("История изменений",()->{var labels=new ArrayList<String>();for(var change:j.getAsJsonArray("history")){var h=change.getAsJsonObject();labels.add(local(h.get("at").getAsLong())+" · "+Json.str(h,"author")+" · "+operationLabel(Json.str(h,"operation")));}minecraft.setScreen(new ChoicePopup(surface(),"Последние изменения",labels,i->{}));});
        if(can(j,"delete"))secondaryDanger("Удалить…",()->confirm("Удалить? Восстановление доступно 30 дней.","delete",new JsonObject()));
        if(can(j,"hide"))secondary("Скрыть запись",()->form("Модерация","hide",List.of(new Field("text","Причина",500)),new JsonObject()));
    }
    void secondary(String label,Runnable action){moreActions.add(new Row(label,action));}
    private void secondaryDanger(String label,Runnable action){dangerousActions.add(moreActions.size());secondary(label,action);}
    private void showMore(){var actions=List.copyOf(moreActions);var menu=new ChoicePopup(surface(),"Действия",actions.stream().map(Row::label).toList(),i->actions.get(i).click.run()).anchorLabel("Действия ▾");for(int index:dangerousActions)menu.danger(index);minecraft.setScreen(menu);}
    private void edit(JsonObject item){var fields=new ArrayList<Field>();fields.add(new Field("title","Название",100));fields.add(new Field("description","Описание",1500));var preset=new JsonObject();for(String key:List.of("title","description","type","location"))if(item.has(key))preset.add(key,item.get(key).deepCopy());if(section.equals("groups"))fields.add(new Field("type","Тип",40,data.getAsJsonObject("config").getAsJsonArray("groupTypes")));if(plus()&&section.equals("board")){CommunityTools.creation(section,fields,preset);if(item.has("trade")){var offer=item.getAsJsonObject("trade");preset.addProperty("trade",Json.str(offer,"type"));for(String key:List.of("item","quantity","terms"))preset.addProperty(key,offer.get(key).getAsString());}}form("Редактировать запись","edit",fields,preset);}
    private static String operationLabel(String op){return switch(op){case "create"->"Создано";case "edit"->"Отредактировано";case "withdrawResponse"->"Отозван отклик";case "withdrawApplication"->"Отозвана заявка";case "revokeInvitation"->"Отменено приглашение";case "delete"->"Удалено";case "restore"->"Восстановлено";case "role"->"Изменено руководство / роль";default->"Обновлено";};}
    String shortName(String uuid){if(uuid.equals(me()))return Json.opt(ServerMenuClient.state,"name","Вы");if(data.has("names")&&data.getAsJsonObject("names").has(uuid))return data.getAsJsonObject("names").get(uuid).getAsString();return uuid.substring(0,8);}
    void confirm(String title,String op,JsonObject body){var dialog=new UiConfirmDialog(yes->{minecraft.setScreen(surface());if(yes)send(op,body);},Component.literal(title),Component.literal(data.has("detail")?Json.str(data.getAsJsonObject("detail"),"title"):""));if(op.equals("delete"))dialog.dangerous();minecraft.setScreen(dialog);}
    void form(String title,String op,List<Field> fields,JsonObject preset){var initial=preset.deepCopy();if(data.has("detail"))initial.add("revision",data.getAsJsonObject("detail").get("revision"));minecraft.setScreen(new CommunityForm(this,title,fields,initial,body->submitForm(op,body)));}
    private void submitForm(String op,JsonObject body){model.submitForm(op,body);}
    private void create(){var fields=new ArrayList<Field>();fields.add(new Field("title","Название",100));fields.add(new Field("description","Описание",1500));var preset=new JsonObject();switch(section){
        case "board"->{fields.add(new Field("days","Срок (дней, 1–90)",2));preset.addProperty("days","14");var categories=data.getAsJsonObject("config").getAsJsonArray("categories");if(!categories.isEmpty()){var options=new JsonArray();var empty=new JsonObject();empty.addProperty("value","");empty.addProperty("label","Без категории");options.add(empty);options.addAll(categories);fields.add(new Field("category","Категория",40,options));}}
        case "groups"->fields.add(new Field("type","Тип",40,data.getAsJsonObject("config").getAsJsonArray("groupTypes")));
        case "events"->{fields.add(new Field("startsAt","Начало · местное время",30));fields.add(new Field("capacity","Места (0 — до 300)",3));preset.addProperty("capacity","0");}
        case "polls"->{fields.add(new Field("endsAt","Завершение · местное время",30));fields.add(new Field("options","Варианты через | (2–8)",808));for(String key:List.of("multiple","changeVote","liveResults")){var options=new JsonArray();options.add("Нет");options.add("Да");fields.add(new Field(key,key.equals("multiple")?"Несколько вариантов":key.equals("changeVote")?"Разрешить изменить голос":"Показывать результаты сразу",10,options));}}
    }if(plus())CommunityTools.creation(section,fields,preset);if(Set.of("board","events").contains(section)&&data.has("groups")&&!data.getAsJsonArray("groups").isEmpty()){var options=new JsonArray();var none=new JsonObject();none.addProperty("value","");none.addProperty("label","Без объединения");options.add(none);options.addAll(data.getAsJsonArray("groups"));fields.add(new Field("group","Объединение",40,options));}form(switch(section){case "board"->"Новое объявление";case "groups"->"Новое объединение";case "events"->"Новое событие";case "polls"->"Новое голосование";case "ideas"->"Новое предложение";default->"Новая запись";},"create",fields,preset);}
    static String optionValue(JsonElement option){return option.isJsonObject()?Json.str(option.getAsJsonObject(),"value"):option.getAsString();}
    static String optionLabel(JsonElement option){return option.isJsonObject()?Json.str(option.getAsJsonObject(),"label"):option.getAsString();}
    record Field(String key,String label,int limit,JsonArray options){Field(String key,String label,int limit){this(key,label,limit,null);}}
    @Override protected void onScrollEnd(){nextPage();}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){
        if(!home()&&sideProfile()&&data.has("groups")&&x>=navigation.right()-232&&y>=groupTop()&&y<groupTop()+groupRows()*76){int max=Math.max(0,data.getAsJsonArray("groups").size()-groupRows());groupScroll=Math.max(0,Math.min(max,groupScroll+(dy<0?1:dy>0?-1:0)));refreshUi();return true;}
        if(navigation.scroll(x,y,dy)){refreshUi();return true;}
        if(splitLayout()&&x>=detailLeft()&&y>=contentTop()&&y<height-64){scrollDetail(-dy*3);return true;}
        return super.mouseScrolled(x,y,dx,dy);
    }
    @Override public boolean mouseClicked(double x,double y,int button){var child=selected();if(splitLayout()&&child!=null&&button==0&&x>=detailLeft()+masterDetail().detail().width()-14&&x<=detailLeft()+masterDetail().detail().width()-8&&y>=child.bodyTop()&&y<height-82){detailDragging=true;seekDetail(y);return true;}return super.mouseClicked(x,y,button);}
    private void seekDetail(double y){var child=selected();if(child==null)return;int track=height-82-child.bodyTop(),thumb=Math.max(12,track*child.visibleRows/Math.max(1,child.rows.size()));detailPosition=0;scrollDetail((y-child.bodyTop()-thumb/2.0)/Math.max(1,track-thumb)*Math.max(0,child.rows.size()-child.visibleRows));}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(detailDragging&&button==0){seekDetail(y);return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){detailDragging=false;return super.mouseReleased(x,y,button);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if((key==264||key==265)&&getFocused() instanceof CommunityCard focused&&focused.getX()==left()){var entries=displayEntries();int at=-1;for(int n=0;n<entries.size();n++)if(Json.str(entries.get(n).getAsJsonObject(),"id").equals(focused.targetId())){at=n;break;}if(at>=0){int next=Math.max(0,Math.min(entries.size()-1,at+(key==264?1:-1)));if(next<firstRow)restoreScroll(next);else if(next>=firstRow+visibleRows)restoreScroll(next-visibleRows+1);String target=Json.str(entries.get(next).getAsJsonObject(),"id");refreshUi();for(var c:children())if(c instanceof CommunityCard card&&card.targetId().equals(target)){setFocused(card);break;}return true;}}if((key==257||key==335)&&getFocused()==search){resetList();return true;}if(key==256&&selected()!=null){expanded.clear();refreshUi();return true;}if(splitLayout()&&selected()!=null&&getFocused() instanceof AbstractWidget widget&&widget.getX()>=detailLeft()){if(key==266||key==267){scrollDetail((key==266?-1:1)*selected().visibleRows);return true;}}return super.keyPressed(key,scan,modifiers);}
    @Override public void render(GuiGraphics g,int x,int y,float d){super.render(g,x,y,d);UiHeading.page(g,font,Component.literal(name(section)),width);navigation.drawFrame(g);int top=contentTop();if(cards()&&displayEntries().isEmpty())UiState.draw(g,font,model.failed()||model.uncertain()?UiState.Kind.ERROR:model.busy()?UiState.Kind.LOADING:query.isBlank()&&!mine&&!participating?UiState.Kind.EMPTY:UiState.Kind.FILTERED,model.failed()||model.uncertain()?"Не удалось загрузить записи":model.busy()?"Загружаем записи…":query.isBlank()?"Здесь пока нет записей":"Ничего не найдено",model.failed()||model.uncertain()?status:model.busy()?"Получаем актуальные данные с сервера.":query.isBlank()?(mine?"У вас пока нет записей в этом разделе.":emptyText()):"Сбросьте поиск или измените запрос.",left()+8,top+8,contentWidth()-16,contentBottom());if(!cards())for(int n=firstRow;n<Math.min(rows.size(),firstRow+visibleRows);n++)if(rows.get(n).click==null&&rows.get(n).progress<0&&rows.get(n).buttons.isEmpty()&&rows.get(n).checked==null)Ui.text(g,font,rows.get(n).label,left()+12,top+(n-firstRow)*24+6,AccessibilityScreen.foreground(UiPalette.color(0xEEEEEE)));Ui.status(g,font,status,left(),contentBottom()+4,contentWidth(),height-30);}
    @Override public void tick(){if(inlineParent==null)for(var child:expanded.values())child.tick();if(notificationButton!=null){int unread=ServerMenuClient.state.has("unread")?ServerMenuClient.state.get("unread").getAsInt():0;String caption=(width<420?"Входящие":"Уведомления")+(unread>0?" ("+unread+")":"");if(!notificationButton.getMessage().getString().equals(caption))notificationButton.setMessage(Component.literal(caption));}if(!ServerMenuClient.available())minecraft.setScreen(null);else{boolean changed=model.tick(filters());if(changed){status=model.status();refreshUi();}}}
    @Override public void onClose(){model.close();ServerMenuClient.cancelReads(surface());if(parent instanceof CommunityScreen screen)screen.invalidate();minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
