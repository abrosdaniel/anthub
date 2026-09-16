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
    private record Row(String label,Runnable click,double progress){Row(String label,Runnable click){this(label,click,-1);}}
    private final Screen parent;
    private final String section;
    private final String itemId;private JsonObject invitationTarget;private String memberFilter="";
    static void groupsFor(Screen parent,String member){var screen=new CommunityScreen(parent,"groups","");screen.memberFilter=member;net.minecraft.client.Minecraft.getInstance().setScreen(screen);}
    static void invite(Screen parent,JsonObject target){var screen=new CommunityScreen(parent,"groups","");screen.invitationTarget=target;net.minecraft.client.Minecraft.getInstance().setScreen(screen);}
    private JsonObject data=new JsonObject();
    private final List<Row> rows=new ArrayList<>();
    private static final Map<String,JsonObject> drafts=new LinkedHashMap<>(){protected boolean removeEldestEntry(Map.Entry<String,JsonObject> entry){return size()>100;}};
    static void clearDrafts(){drafts.clear();}
    private long sentAt;
    private final Set<Integer> choices=new LinkedHashSet<>();
    private String request="",status="",query="";
    private int page,navOffset;
    private boolean mine,busy,loaded,openSettingsOnLoad;
    static void openSettings(Screen parent){var screen=new CommunityScreen(parent,"home","");screen.openSettingsOnLoad=true;net.minecraft.client.Minecraft.getInstance().setScreen(screen);}
    private EditBox search;private Button notificationButton;
    void refreshPermissions(){rebuildWidgets();}
    private static final Map<String,String> NAMES=Map.ofEntries(Map.entry("home","Главная"),Map.entry("players","Игроки"),Map.entry("board","Доска объявлений"),Map.entry("groups","Объединения"),Map.entry("events","События"),Map.entry("polls","Голосования"),Map.entry("ideas","Предложения"),Map.entry("notifications","Уведомления"),Map.entry("info","Сервер"),Map.entry("help","Помощь"),Map.entry("admin","Администрирование"));
    CommunityScreen(Screen parent,String section,String id){super(Component.literal(name(section)));this.parent=parent;this.section=section;itemId=id;}
    static String name(String section){if(section.equals("groups")&&ServerMenuClient.state.has("communityConfig"))return Json.opt(ServerMenuClient.state.getAsJsonObject("communityConfig"),"groupsTitle",NAMES.get(section));return NAMES.getOrDefault(section,section);}
    static void open(Screen parent,String section){net.minecraft.client.Minecraft.getInstance().setScreen(new CommunityScreen(parent,section,""));}
    private static String me(){return Json.opt(ServerMenuClient.state,"uuid","");}
    private boolean owner(JsonObject j){return me().equals(Json.opt(j,"owner",""));}
    private int left(){return Math.min(146,Math.max(96,width/4))+12;}
    private boolean home(){return section.equals("home")&&itemId.isEmpty();}
    private boolean sideProfile(){return home()&&height>=300&&width-left()>=490;}
    private int contentWidth(){return width-left()-20-(sideProfile()?224:0);}
    private Button button(String label,int x,int y,int w,Runnable callback){return addRenderableWidget(Button.builder(Component.literal(label),b->callback.run()).bounds(x,y,w,20).build());}
    @Override protected void init(){
        if(data.has("detail")){rows.clear();detail(data.getAsJsonObject("detail"));}else if(data.has("entries")){rows.clear();list();}
        int navWidth=left()-20;var sections=new ArrayList<String>();var config=ServerMenuClient.state.has("communityConfig")?ServerMenuClient.state.getAsJsonObject("communityConfig"):new JsonObject();
        for(String key:List.of("home","players","board","groups","events","polls","ideas","info","help","admin")){if(key.equals("admin")&&!ServerMenuClient.admin())continue;if(config.has("sections")&&!Set.of("info","help","admin").contains(key)&&!config.getAsJsonArray("sections").contains(new JsonPrimitive(key)))continue;sections.add(key);}
        int shown=Math.max(1,(height-112)/24);navOffset=Math.max(0,Math.min(navOffset,Math.max(0,sections.size()-shown)));
        for(int n=navOffset;n<Math.min(sections.size(),navOffset+shown);n++){String key=sections.get(n);addRenderableWidget(new SidebarButton(8,42+(n-navOffset)*24,navWidth,name(key),section.equals(key),()->navigate(key)));}
        button("Настройки",8,height-52,navWidth,()->{if(!busy&&data.has("preferences"))minecraft.setScreen(new Preferences(this));});
        button("Назад",8,height-28,navWidth,()->onClose());
        int unread=ServerMenuClient.state.has("unread")?ServerMenuClient.state.get("unread").getAsInt():0;
        notificationButton=button((width<420?"●":"Уведомления")+(unread>0?" ("+unread+")":""),Math.max(left(),width-(width<420?158:220)),12,width<420?68:130,()->navigate("notifications"));if(!home())button("Главная",width-86,12,78,()->navigate("home"));
        if(itemId.isEmpty()&&!section.equals("home")){
            search=addRenderableWidget(new EditBox(font,left(),76,Math.max(50,contentWidth()-116),20,Component.literal("Поиск")));search.setMaxLength(100);search.setHint(Component.literal("Поиск"));search.setValue(query);search.setResponder(v->query=v);
            button("Найти",width-128,76,50,()->{page=0;load();});button(mine?"Мои ✓":"Все",width-74,76,54,()->{mine=!mine;page=0;load();});
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
            var entries=data.getAsJsonArray("entries");int columns=columns(),stride=cardStride();
            scrollArea((entries.size()+columns-1)/columns,top,height-82,stride,left()+contentWidth()+5);
            int cardWidth=(contentWidth()-(columns-1)*8)/columns;
            for(int row=firstRow;row<Math.min((entries.size()+columns-1)/columns,firstRow+visibleRows);row++)for(int column=0;column<columns;column++){
                int index=row*columns+column;if(index>=entries.size())break;
                var entry=entries.get(index).getAsJsonObject();
                addRenderableWidget(new CommunityCard(left()+column*(cardWidth+8),top+(row-firstRow)*stride,cardWidth,stride-6,entry,()->{if(!busy)openEntry(entry);}));
            }
        }else{
            scrollArea(rows.size(),top,height-82,24,left()+contentWidth()+5);
            for(int i=firstRow;i<Math.min(rows.size(),firstRow+visibleRows);i++){
                Row row=rows.get(i);int y=top+(i-firstRow)*24;
                if(row.progress>=0){var bar=new PollOption(left(),y,contentWidth(),row.label,row.progress,()->{if(!busy&&row.click!=null)row.click.run();});bar.active=row.click!=null;addRenderableWidget(bar);}
                else if(row.click!=null)button(font.plainSubstrByWidth(row.label,contentWidth()-12),left(),y,contentWidth(),()->{if(!busy)row.click.run();});
            }
        }
        if(home()&&sideProfile()){
            int half=(contentWidth()-6)/2;
            button("Ответы и приглашения"+(unread>0?" · "+unread:""),left(),76,half,()->navigate("notifications"));
            button("Избранное",left()+half+6,76,half,()->{if(!busy&&data.has("preferences"))minecraft.setScreen(new Favorites());});
        }
        if(itemId.isEmpty()){
            button("‹",left(),height-54,24,()->{if(page>0){page--;load();}});button("›",left()+28,height-54,24,()->{if(data.has("entries")&&data.getAsJsonArray("entries").size()==10){page++;load();}});
            if(data.has("canCreate")&&data.get("canCreate").getAsBoolean()&&Set.of("board","groups","events","polls","ideas").contains(section))button(createLabel(),left()+60,height-54,Math.min(170,Math.max(60,contentWidth()-62)),this::create);
            if(section.equals("notifications"))button("Прочитать всё",left()+60,height-54,125,()->send("read",new JsonObject()));
        }
        if(!itemId.isEmpty()){button("‹",left(),height-54,24,()->{if(page>0){page--;load();}});button("›",left()+28,height-54,24,()->{page++;load();});}
        button("Обновить",Math.max(left(),width-108),height-28,88,this::load);
        if(!loaded){loaded=true;load();}
    }
    private final class Favorites extends ScrollScreen {
        private final java.util.List<String> keys=new java.util.ArrayList<>();
        Favorites(){super(Component.literal("Избранные разделы"));var prefs=data.getAsJsonObject("preferences");if(prefs.has("favorites"))for(var key:prefs.getAsJsonArray("favorites"))keys.add(key.getAsString());}
        @Override protected void init(){int w=Math.min(320,width-40),x=(width-w)/2;scrollArea(keys.size(),50,height-64,26,x+w+4);for(int i=firstRow;i<Math.min(keys.size(),firstRow+visibleRows);i++){String key=keys.get(i);addRenderableWidget(Button.builder(Component.literal(name(key)),b->CommunityScreen.this.navigate(key)).bounds(x,50+(i-firstRow)*26,w,22).build());}addRenderableWidget(Button.builder(Component.literal("Настроить"),b->minecraft.setScreen(new Preferences(CommunityScreen.this))).bounds(x,height-52,w,20).build());addRenderableWidget(Button.builder(Component.literal("Назад"),b->onClose()).bounds(x,height-28,w,20).build());}
        @Override public void render(GuiGraphics g,int x,int y,float d){super.render(g,x,y,d);g.drawCenteredString(font,title,width/2,20,0xE2BE75);if(keys.isEmpty())g.drawCenteredString(font,"Добавьте разделы через «Настроить»",width/2,54,0xBBBBBB);}
        @Override public void onClose(){minecraft.setScreen(CommunityScreen.this);}
        @Override public boolean isPauseScreen(){return false;}
    }
    private int contentTop(){return home()&&!sideProfile()?110:itemId.isEmpty()?104:76;}
    private boolean cards(){return itemId.isEmpty()&&!section.equals("notifications")&&data.has("entries")&&!data.getAsJsonArray("entries").isEmpty();}
    private int columns(){return (section.equals("groups")||section.equals("home"))&&contentWidth()>=460?2:1;}
    private int cardStride(){return Math.min(80,Math.max(42,height-82-contentTop()));}
    private void openEntry(JsonObject entry){var screen=new CommunityScreen(this,Json.str(entry,"section"),Json.str(entry,"id"));screen.invitationTarget=invitationTarget;minecraft.setScreen(screen);}
    static String statusLabel(String value){return status(value);}
    private String createLabel(){return switch(section){case "board"->"Разместить объявление";case "groups"->"Создать объединение";case "events"->"Назначить событие";case "polls"->"Создать голосование";case "ideas"->"Предложить идею";default->"Создать";};}
    private String subtitle(){return switch(section){case "home"->"Ваши дела, избранное и ближайшие планы";case "board"->"Предложения игроков и отклики";case "groups"->"Найдите команду или соберите свою";case "events"->"Встречи, участие и совместные планы";case "polls"->"Ваш голос в решениях сообщества";case "ideas"->"Идеи игроков и ответы администрации";case "notifications"->"Ответы, приглашения и напоминания";default->name(section);};}
    private String emptyText(){return switch(section){case "home"->"Пока нет новых дел. Загляните в объявления или события.";case "board"->"Объявлений пока нет. Здесь можно найти помощь или предложить свою.";case "groups"->"Объединений пока нет. Здесь появятся команды игроков.";case "events"->"Событий пока нет. Здесь появятся предстоящие встречи.";case "polls"->"Сейчас нет голосований.";case "ideas"->"Пока нет предложений. Поделитесь идеей для сервера.";case "notifications"->"Всё прочитано. Новые ответы и приглашения появятся здесь.";default->"Пока нет записей.";};}
    private int accent(){return switch(section){case "board"->0xFFE2BE75;case "groups"->0xFF79CBA6;case "events"->0xFF82B6F2;case "polls"->0xFFB49AE8;case "ideas"->0xFFF0A77C;default->0xFF8BC7CB;};}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float d){super.renderBackground(g,x,y,d);if(home())ProfilePanel.render(g,font,sideProfile()?width-232:left(),sideProfile()?76:40,sideProfile()?212:contentWidth(),sideProfile());if(home()&&!sideProfile())return;g.fill(left(),40,width-16,70,0xC01C242C);g.fill(left(),40,left()+3,70,accent());g.drawString(font,font.plainSubstrByWidth(name(section),contentWidth()-16),left()+9,44,accent());g.drawString(font,font.plainSubstrByWidth(subtitle(),contentWidth()-16),left()+9,57,0xBAC6D2);}
    private void navigate(String key){if(busy)return;if(key.equals("players")){FeatureListScreen.open(this,"players");return;}if(key.equals("info")){minecraft.setScreen(new ServerInfoScreen(this));return;}if(Set.of("help","admin").contains(key)){minecraft.setScreen(new ServerMenuScreen(this,key));return;}minecraft.setScreen(new CommunityScreen(null,key,""));}
    private void load(){send(itemId.isEmpty()?"list":"detail",new JsonObject());}
    private void send(String op,JsonObject body){if(busy)return;busy=true;sentAt=System.currentTimeMillis();status="Загрузка…";request=UUID.randomUUID().toString();body.addProperty("action","community");body.addProperty("section",section);body.addProperty("op",op);body.addProperty("id",itemId);body.addProperty("page",page);body.addProperty("mine",mine);body.addProperty("member",memberFilter);body.addProperty("query",query);body.addProperty("request",request);ServerMenuClient.request(body);}
    void receive(JsonObject response){if(!request.equals(Json.opt(response,"request","")))return;busy=false;if(response.has("error")){status=Json.opt(response,"text","Ошибка");return;}data=response;status="";choices.clear();if(data.has("detail")&&data.getAsJsonObject("detail").has("myVote"))for(var choice:data.getAsJsonObject("detail").getAsJsonArray("myVote"))choices.add(choice.getAsInt());rows.clear();if(data.has("detail"))detail(data.getAsJsonObject("detail"));else list();rebuildWidgets();if(openSettingsOnLoad){openSettingsOnLoad=false;minecraft.setScreen(new Preferences(this));}}
    private void text(String text){for(String line:text.split("\n",-1)){if(line.isEmpty()){rows.add(new Row("",null));continue;}String rest=line;while(!rest.isEmpty()){String part=font.plainSubstrByWidth(rest,Math.max(40,contentWidth()-8));if(part.isEmpty())break;rows.add(new Row(part,null));rest=rest.substring(part.length());}}}
    private void action(String name,Runnable action){rows.add(new Row(name,action));}
    private void list(){
        if(section.equals("home")){text("Ваши объявления, заявки и ближайшие дела");if(data.has("preferences")){var prefs=data.getAsJsonObject("preferences");if(prefs.has("favorites"))for(var key:prefs.getAsJsonArray("favorites")){String k=key.getAsString();action("★ "+name(k),()->navigate(k));}}}
        var entries=data.getAsJsonArray("entries");if(entries==null||entries.isEmpty()){text(query.isBlank()?(mine?"У вас пока нет записей в этом разделе.":emptyText()):"Ничего не найдено. Попробуйте другой запрос.");return;}
        for(var e:entries){var j=e.getAsJsonObject();String label=Json.str(j,"title");if(section.equals("notifications")){label=(j.get("read").getAsBoolean()?"":"● ")+label;action(label,()->{var read=new JsonObject();read.addProperty("action","community");read.addProperty("section","notifications");read.addProperty("op","read");read.addProperty("id",Json.str(j,"id"));ServerMenuClient.request(read);String target=Json.opt(j,"target","");if(Json.str(j,"section").equals("help")&&!target.isEmpty()){FeatureListScreen.openReport(this,target);return;}if(target.isEmpty()){minecraft.setScreen(new ServerMenuScreen(this,"help"));return;}minecraft.setScreen(new CommunityScreen(this,Json.str(j,"section"),target));});}
            else action(label+" · "+status(Json.str(j,"status")),()->{var screen=new CommunityScreen(this,Json.str(j,"section"),Json.str(j,"id"));screen.invitationTarget=invitationTarget;minecraft.setScreen(screen);});}
    }
    private static String status(String value){return switch(value){case "open"->"Открыто";case "closed"->"Закрыто";case "new"->"Новое";case "discussion"->"Обсуждается";case "planned"->"Запланировано";case "done"->"Выполнено";case "declined"->"Отклонено";case "pending"->"Ожидает ответа";case "accepted"->"Принято";case "cancelled"->"Отменено";case "hidden"->"Скрыто";default->value;};}
    private static String local(long epoch){return DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm z").format(Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()));}
    private void detail(JsonObject j){
        text(Json.str(j,"title")+"\n"+Json.str(j,"author")+" · "+status(Json.str(j,"status"))+"\n\n"+Json.str(j,"description"));
        boolean manage=j.get("manage").getAsBoolean();
        switch(section){
            case "board" -> {
                text("Действует до: "+local(j.get("endsAt").getAsLong()));if(!Json.opt(j,"category","").isEmpty())text("Категория: "+Json.str(j,"category"));
                var responses=j.getAsJsonObject("responses");if(!owner(j)&&!responses.has(me())&&Json.str(j,"status").equals("open"))action("Откликнуться",()->form("Отклик","respond",List.of(new Field("text","Сообщение",1000)),new JsonObject()));
                for(var entry:responses.entrySet()){String target=entry.getKey();var reply=entry.getValue().getAsJsonObject();text("\n"+Json.str(reply,"name")+" · "+status(Json.str(reply,"status"))+"\n"+Json.str(reply,"text"));if(!Json.opt(reply,"answer","").isEmpty())text("Ответ: "+Json.str(reply,"answer"));if(owner(j))for(String decision:List.of("accepted","declined","pending"))action(status(decision)+": "+Json.str(reply,"name"),()->{var body=new JsonObject();body.addProperty("target",target);body.addProperty("decision",decision);form("Ответ на отклик","respondDecision",List.of(new Field("text","Ответ",1000)),body);});}
                if(owner(j)){action("Закрыть объявление",()->confirm("Закрыть объявление?","close",new JsonObject()));action("Отметить выполненным",()->confirm("Отметить выполненным?","complete",new JsonObject()));}
            }
            case "ideas" -> {text("Поддержали: "+j.getAsJsonObject("supporters").size());action(j.getAsJsonObject("supporters").has(me())?"Убрать поддержку":"Поддержать",()->send("support",new JsonObject()));text(Json.opt(j,"answer",""));if(ServerMenuClient.admin())for(String state:List.of("new","discussion","planned","done","declined"))action("Статус: "+status(state),()->{var body=new JsonObject();body.addProperty("status",state);form("Официальный ответ","status",List.of(new Field("text","Объяснение",1000)),body);});}
            case "polls" -> {
                text("До: "+local(j.get("endsAt").getAsLong()));var options=j.getAsJsonArray("options");var counts=j.getAsJsonArray("counts");int maxCount=1;for(var count:counts)maxCount=Math.max(maxCount,count.getAsInt());
                for(int i=0;i<options.size();i++){int index=i;String label=(choices.contains(i)?"☑ ":"☐ ")+options.get(i).getAsString();if(counts.get(i).getAsInt()>=0)label+=" · "+counts.get(i).getAsInt();if(j.get("endsAt").getAsLong()<=System.currentTimeMillis()||j.get("voted").getAsBoolean()&&!j.get("changeVote").getAsBoolean()){rows.add(new Row(label,null,counts.get(i).getAsInt()<0?-1:(double)counts.get(i).getAsInt()/maxCount));continue;}int rowIndex=rows.size();action(label,()->{if(!j.get("multiple").getAsBoolean())choices.clear();if(!choices.add(index))choices.remove(index);rows.clear();detail(j);rebuildWidgets();});if(counts.get(i).getAsInt()>=0){var option=rows.get(rowIndex);rows.set(rowIndex,new Row(option.label,option.click,(double)counts.get(i).getAsInt()/maxCount));}}
                if(j.get("endsAt").getAsLong()>System.currentTimeMillis()&&(!j.get("voted").getAsBoolean()||j.get("changeVote").getAsBoolean()))action("Голосовать",()->{var body=new JsonObject();var selected=new JsonArray();choices.forEach(selected::add);body.add("choices",selected);send("vote",body);});
            }
            case "events" -> {
                text("Начало: "+local(j.get("startsAt").getAsLong()));int capacity=j.get("capacity").getAsInt();var participants=j.getAsJsonObject("participants");text("Участников: "+j.get("participantsCount").getAsInt()+(capacity>0?" / "+capacity:""));int n=0;for(var entry:participants.entrySet()){text((capacity>0&&n++>=capacity?"Очередь: ":"✓ ")+entry.getValue().getAsString());}
                if(Json.str(j,"status").equals("open")&&j.get("startsAt").getAsLong()>System.currentTimeMillis())action(j.get("isParticipant").getAsBoolean()?"Отменить участие":"Участвовать",()->send(j.get("isParticipant").getAsBoolean()?"leave":"join",new JsonObject()));
                if(manage){action("Перенести",()->form("Перенос события","reschedule",List.of(new Field("startsAt","Начало (местное время: yyyy-MM-dd HH:mm)",30)),new JsonObject()));action("Отменить событие",()->confirm("Отменить событие?","cancel",new JsonObject()));}
            }
            case "groups" -> {
                text(Json.str(j,"type")+" · "+(j.get("recruiting").getAsBoolean()?"Набор открыт":"Набор закрыт"));var members=j.getAsJsonObject("members");for(var entry:members.entrySet()){String target=entry.getKey(),role=entry.getValue().getAsString();text(shortName(target)+" · "+switch(role){case "leader"->"Руководитель";case "assistant"->"Помощник";default->"Участник";});if(owner(j)&&!target.equals(me()))for(String next:List.of("member","assistant","leader"))action("Роль "+(next.equals("leader")?"Руководитель":next.equals("assistant")?"Помощник":"Участник")+": "+shortName(target),()->{var body=new JsonObject();body.addProperty("target",target);body.addProperty("role",next);confirm("Изменить роль участника?","role",body);});}
                if(!j.get("isMember").getAsBoolean()&&!j.getAsJsonObject("applications").has(me())&&j.get("recruiting").getAsBoolean())action("Подать заявку",()->form("Заявка","apply",List.of(new Field("text","О себе",500)),new JsonObject()));
                if(j.get("isMember").getAsBoolean()&&!owner(j))action("Выйти",()->confirm("Выйти из объединения?","leave",new JsonObject()));
                if(j.getAsJsonObject("invitations").has(me()))for(boolean accept:List.of(true,false))action(accept?"Принять приглашение":"Отклонить приглашение",()->{var b=new JsonObject();b.addProperty("accept",accept);send("invitation",b);});
                if(manage){if(invitationTarget!=null)action("Пригласить "+Json.str(invitationTarget,"name"),()->{var invite=new JsonObject();invite.addProperty("target",Json.str(invitationTarget,"uuid"));send("invite",invite);});action("Изменить доступность набора",()->send("recruiting",new JsonObject()));action("Пригласить игрока",()->FeatureListScreen.pick(this,player->{var body=new JsonObject();body.addProperty("target",Json.str(player,"uuid"));send("invite",body);}));for(var entry:j.getAsJsonObject("applications").entrySet()){String target=entry.getKey();var application=entry.getValue().getAsJsonObject();text(Json.str(application,"name")+": "+Json.str(application,"text"));for(boolean accept:List.of(true,false))action((accept?"Принять ":"Отклонить ")+Json.str(application,"name"),()->{var b=new JsonObject();b.addProperty("target",target);b.addProperty("accept",accept);send("application",b);});}}
            }
        }
        if(data.has("related"))for(var related:data.getAsJsonArray("related")){var child=related.getAsJsonObject();action(name(Json.str(child,"section"))+": "+Json.str(child,"title"),()->minecraft.setScreen(new CommunityScreen(this,Json.str(child,"section"),Json.str(child,"id"))));}
        if(!owner(j))action("Пожаловаться",()->minecraft.setScreen(new ReportScreen(this,name(section)+": "+Json.str(j,"title")+" ["+itemId+"]\n")));
        if(ServerMenuClient.admin())action("Скрыть запись",()->form("Модерация","hide",List.of(new Field("text","Причина",500)),new JsonObject()));
    }
    private String shortName(String uuid){if(uuid.equals(me()))return Json.opt(ServerMenuClient.state,"name","Вы");if(data.has("names")&&data.getAsJsonObject("names").has(uuid))return data.getAsJsonObject("names").get(uuid).getAsString();return uuid.substring(0,8);}
    private void confirm(String title,String op,JsonObject body){minecraft.setScreen(new ConfirmScreen(yes->{minecraft.setScreen(this);if(yes)send(op,body);},Component.literal(title),Component.literal(data.has("detail")?Json.str(data.getAsJsonObject("detail"),"title"):"")));}
    private void form(String title,String op,List<Field> fields,JsonObject preset){minecraft.setScreen(new Form(this,title,fields,preset,body->submitForm(op,body)));}
    private void submitForm(String op,JsonObject body){body.addProperty("action","community");body.addProperty("section",section);body.addProperty("id",itemId);body.addProperty("op",op);ServerMenuClient.request(body);}
    private void create(){var fields=new ArrayList<Field>();fields.add(new Field("title","Название",100));fields.add(new Field("description","Описание",1500));var preset=new JsonObject();switch(section){
        case "board"->{fields.add(new Field("days","Срок (дней, 1–90)",2));preset.addProperty("days","14");var categories=data.getAsJsonObject("config").getAsJsonArray("categories");if(!categories.isEmpty()){var options=new JsonArray();var empty=new JsonObject();empty.addProperty("value","");empty.addProperty("label","Без категории");options.add(empty);options.addAll(categories);fields.add(new Field("category","Категория",40,options));}}
        case "groups"->fields.add(new Field("type","Тип",40,data.getAsJsonObject("config").getAsJsonArray("groupTypes")));
        case "events"->{fields.add(new Field("startsAt","Начало (местное время: yyyy-MM-dd HH:mm)",30));fields.add(new Field("capacity","Места (0 — до 300)",3));preset.addProperty("capacity","0");}
        case "polls"->{fields.add(new Field("endsAt","Завершение (местное время: yyyy-MM-dd HH:mm)",30));fields.add(new Field("options","Варианты через | (2–8)",808));for(String key:List.of("multiple","changeVote","liveResults")){var options=new JsonArray();options.add("Нет");options.add("Да");fields.add(new Field(key,key.equals("multiple")?"Несколько вариантов":key.equals("changeVote")?"Разрешить изменить голос":"Показывать результаты сразу",10,options));}}
    }if(Set.of("board","events").contains(section)&&data.has("groups")&&!data.getAsJsonArray("groups").isEmpty()){var options=new JsonArray();var none=new JsonObject();none.addProperty("value","");none.addProperty("label","Без объединения");options.add(none);options.addAll(data.getAsJsonArray("groups"));fields.add(new Field("group","Объединение",40,options));}form("Создать: "+name(section),"create",fields,preset);}
    private static String optionValue(JsonElement option){return option.isJsonObject()?Json.str(option.getAsJsonObject(),"value"):option.getAsString();}
    private static String optionLabel(JsonElement option){return option.isJsonObject()?Json.str(option.getAsJsonObject(),"label"):option.getAsString();}
    record Field(String key,String label,int limit,JsonArray options){Field(String key,String label,int limit){this(key,label,limit,null);}}
    static final class Form extends ScrollScreen implements Receiver {
        private final CommunityScreen parent;private final List<Field> fields;private final JsonObject values;private final Consumer<JsonObject> submit;private boolean busy;private String request="",error="";private long sentAt;private final String draftKey;
        Form(CommunityScreen parent,String title,List<Field> fields,JsonObject preset,Consumer<JsonObject> submit){super(Component.literal(title));this.parent=parent;this.fields=fields;draftKey=parent.section+"|"+parent.itemId+"|"+title;values=drafts.getOrDefault(draftKey,preset).deepCopy();this.submit=submit;}
        @Override protected void init(){int w=Math.min(600,width-40),x=(width-w)/2;scrollArea(fields.size(),38,height-64,66,x+w+4);for(int n=firstRow;n<Math.min(fields.size(),firstRow+visibleRows);n++){Field f=fields.get(n);int y=38+(n-firstRow)*66;if(f.options!=null){if(!values.has(f.key))values.addProperty(f.key,optionValue(f.options.get(0)));addRenderableWidget(Button.builder(Component.literal(f.options.asList().stream().filter(o->optionValue(o).equals(Json.str(values,f.key))).map(CommunityScreen::optionLabel).findFirst().orElse("")),b->{int i=0;for(;i<f.options.size();i++)if(optionValue(f.options.get(i)).equals(Json.str(values,f.key)))break;values.addProperty(f.key,optionValue(f.options.get((i+1)%f.options.size())));b.setMessage(Component.literal(optionLabel(f.options.get((i+1)%f.options.size()))));}).bounds(x,y+15,w,20).build());}else if(Set.of("description","text","options").contains(f.key)){var edit=addRenderableWidget(new MultiLineEditBox(font,x,y+15,w,40,Component.literal(f.label),Component.literal(f.label)));edit.setCharacterLimit(f.limit);edit.setValue(Json.opt(values,f.key,""));edit.setValueListener(v->{values.addProperty(f.key,v);drafts.put(draftKey,values.deepCopy());});}else{var edit=addRenderableWidget(new EditBox(font,x,y+15,w,20,Component.literal(f.label)));edit.setMaxLength(f.limit);edit.setValue(Json.opt(values,f.key,""));edit.setResponder(v->{values.addProperty(f.key,v);drafts.put(draftKey,values.deepCopy());});}}
            var save=addRenderableWidget(Button.builder(Component.literal(busy?"Отправка…":"Отправить"),b->send()).bounds(x,height-28,w/2-3,20).build());save.active=!busy;addRenderableWidget(Button.builder(Component.literal("Назад"),b->onClose()).bounds(x+w/2+3,height-28,w/2-3,20).build());}
        private void send(){if(busy)return;var body=values.deepCopy();try{for(var f:fields){String value=Json.opt(values,f.key,"").strip();if(value.isBlank()&&!Set.of("group","category").contains(f.key))throw new IllegalArgumentException("Заполните: "+f.label);if(Set.of("days","capacity").contains(f.key))body.addProperty(f.key,Integer.parseInt(value));if(Set.of("startsAt","endsAt").contains(f.key))body.addProperty(f.key,LocalDateTime.parse(value,DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(java.time.format.ResolverStyle.STRICT)).atZone(ZoneId.systemDefault()).toInstant().toString());if(f.key.equals("options")){var options=new JsonArray();for(String s:value.split("\\|"))options.add(s.strip());body.add("options",options);}if(Set.of("multiple","changeVote","liveResults").contains(f.key))body.addProperty(f.key,value.equals("Да"));}request=UUID.randomUUID().toString();body.addProperty("request",request);busy=true;sentAt=System.currentTimeMillis();error="";submit.accept(body);rebuildWidgets();}catch(Exception ex){error="Проверьте поля: "+ex.getMessage();}}
        public void receiveCommunity(JsonObject j){if(!request.equals(Json.opt(j,"request","")))return;busy=false;if(j.has("error")){error=Json.opt(j,"text","Ошибка");rebuildWidgets();return;}drafts.remove(draftKey);minecraft.setScreen(parent);parent.busy=false;parent.load();}
        @Override public boolean mouseScrolled(double x,double y,double dx,double dy){for(var child:children())if(child instanceof MultiLineEditBox edit&&edit.isMouseOver(x,y)&&edit.mouseScrolled(x,y,dx,dy))return true;return super.mouseScrolled(x,y,dx,dy);}
        @Override public void render(GuiGraphics g,int x,int y,float d){super.render(g,x,y,d);g.drawCenteredString(font,title,width/2,14,0xE2BE75);int left=(width-Math.min(600,width-40))/2;for(int n=firstRow;n<Math.min(fields.size(),firstRow+visibleRows);n++)g.drawString(font,fields.get(n).label,left,38+(n-firstRow)*66,0xEEEEEE);Ui.status(g,font,error,left,height-60,width-40,height-30);}
        @Override public void onClose(){if(!busy){drafts.put(draftKey,values.deepCopy());minecraft.setScreen(parent);}}
        @Override public void tick(){if(!ServerMenuClient.available())minecraft.setScreen(null);else if(busy&&System.currentTimeMillis()-sentAt>15000){busy=false;error="Ответ не получен. Обновите список перед повторной отправкой.";rebuildWidgets();}}
        @Override public boolean isPauseScreen(){return false;}
    }
    private static final class Preferences extends ScrollScreen implements Receiver {
        private final CommunityScreen parent;private final JsonArray favorites=new JsonArray(),muted=new JsonArray();private String error="",request="";private boolean busy;
        Preferences(CommunityScreen parent){super(Component.literal("Настройки меню"));this.parent=parent;var prefs=parent.data.has("preferences")?parent.data.getAsJsonObject("preferences"):new JsonObject();if(prefs.has("favorites"))prefs.getAsJsonArray("favorites").forEach(favorites::add);if(prefs.has("muted"))prefs.getAsJsonArray("muted").forEach(muted::add);}
        private void toggle(JsonArray a,String k){var v=new JsonPrimitive(k);if(a.contains(v))a.remove(v);else a.add(v);rebuildWidgets();}
        @Override protected void init(){var keys=List.of("board","groups","events","polls","ideas","notices","sound","restartNotices");scrollArea(keys.size(),42,height-94,46,width-16);for(int n=firstRow;n<Math.min(keys.size(),firstRow+visibleRows);n++){String k=keys.get(n);int y=42+(n-firstRow)*46;if(n>=5){int setting=n==6?1:n==7?2:0;addRenderableWidget(Button.builder(Client.tr("server."+k).copy().append(": ").append(Client.tr(ServerMenuClient.enabled(setting)?"server.on":"server.off")),b->{ServerMenuClient.toggle(setting);rebuildWidgets();}).bounds(20,y,width-40,20).build());continue;}addRenderableWidget(Button.builder(Component.literal((favorites.contains(new JsonPrimitive(k))?"★ ":"☆ ")+name(k)),b->toggle(favorites,k)).bounds(20,y,width/2-25,20).build());addRenderableWidget(Button.builder(Component.literal(muted.contains(new JsonPrimitive(k))?"Без плашек":"Плашки включены"),b->toggle(muted,k)).bounds(width/2+5,y,width/2-25,20).build());}addRenderableWidget(Button.builder(Component.literal("Сохранить"),b->{if(busy)return;busy=true;request=UUID.randomUUID().toString();var j=new JsonObject();j.addProperty("action","community");j.addProperty("op","preferences");j.addProperty("section","home");j.addProperty("request",request);var p=new JsonObject();p.add("favorites",favorites);p.add("muted",muted);j.add("preferences",p);ServerMenuClient.request(j);}).bounds(20,height-28,110,20).build());addRenderableWidget(Button.builder(Component.literal("Клавиша меню"),b->minecraft.setScreen(new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(this,minecraft.options))).bounds(20,height-54,140,20).build());addRenderableWidget(Button.builder(Component.literal("Назад"),b->onClose()).bounds(width-100,height-28,80,20).build());}
        public void receiveCommunity(JsonObject j){if(!request.equals(Json.opt(j,"request","")))return;busy=false;if(j.has("error")){error=Json.opt(j,"text","");return;}minecraft.setScreen(parent);parent.load();}
        @Override public void render(GuiGraphics g,int x,int y,float d){super.render(g,x,y,d);g.drawCenteredString(font,title,width/2,14,0xE2BE75);g.drawString(font,error,20,height-55,0xEEEEEE);}
        @Override public void onClose(){minecraft.setScreen(parent);}
        @Override public boolean isPauseScreen(){return false;}
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(x<left()){navOffset=Math.max(0,navOffset+(dy<0?1:-1));rebuildWidgets();return true;}return super.mouseScrolled(x,y,dx,dy);}
    @Override public void render(GuiGraphics g,int x,int y,float d){super.render(g,x,y,d);g.drawString(font,font.plainSubstrByWidth(name(section),Math.max(40,width-240)),8,18,0xE2BE75);int top=contentTop();if(!cards())for(int n=firstRow;n<Math.min(rows.size(),firstRow+visibleRows);n++)if(rows.get(n).click==null&&rows.get(n).progress<0)g.drawString(font,rows.get(n).label,left()+4,top+(n-firstRow)*24+6,0xEEEEEE);Ui.status(g,font,status,left(),height-78,contentWidth(),height-56);}
    @Override public void tick(){if(notificationButton!=null){int unread=ServerMenuClient.state.has("unread")?ServerMenuClient.state.get("unread").getAsInt():0;notificationButton.setMessage(Component.literal((width<420?"●":"Уведомления")+(unread>0?" ("+unread+")":"")));}if(!ServerMenuClient.available())minecraft.setScreen(null);else if(busy&&System.currentTimeMillis()-sentAt>15000){busy=false;status="Ответ не получен. Можно обновить список.";}}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
