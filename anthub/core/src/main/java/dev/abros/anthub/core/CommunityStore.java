package dev.abros.anthub.core;

import com.google.gson.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Persistent community commands. All mutations and their notifications share one transaction. */
public final class CommunityStore implements AutoCloseable {
    public record Actor(String id,String name,boolean admin,boolean events) {}
    public static final List<String> SECTIONS=List.of("home","players","board","groups","events","polls","ideas","notifications");
    private final PgDatabase database;
    private volatile JsonObject config;private volatile long lastReminderSweep;
    public CommunityStore(PgDatabase database,JsonObject settings){this.database=database;config=validateConfig(settings);}
    private Connection connection(){return database.connection();}
    public static JsonObject defaults(){var j=new JsonObject();j.addProperty("groupsTitle","Объединения");j.addProperty("maxMemberships",3);j.add("categories",new JsonArray());var types=new JsonArray();for(String t:List.of("Город","Команда","Гильдия","Поселение"))types.add(t);j.add("groupTypes",types);var sections=new JsonArray();SECTIONS.forEach(sections::add);j.add("sections",sections);return j;}
    public static JsonObject validateConfig(JsonObject j){Json.keys(j,"groupsTitle","maxMemberships","categories","groupTypes","sections");text(j,"groupsTitle",40);int max=number(j,"maxMemberships",1,20);for(String key:List.of("categories","groupTypes","sections")){var a=j.getAsJsonArray(key);if(a==null||a.size()>20)throw new IllegalArgumentException("Invalid "+key);var seen=new HashSet<String>();for(var e:a){String v=e.getAsString();if(v.isBlank()||v.length()>40||!seen.add(v)||key.equals("sections")&&!SECTIONS.contains(v))throw new IllegalArgumentException("Invalid "+key);}}if(!j.getAsJsonArray("sections").contains(new JsonPrimitive("home"))||!j.getAsJsonArray("sections").contains(new JsonPrimitive("notifications")))throw new IllegalArgumentException("Home and notifications must remain enabled");if(j.getAsJsonArray("groupTypes").isEmpty())throw new IllegalArgumentException("Specify a group type");return j.deepCopy();}
    public void configure(JsonObject j){config=validateConfig(j);}
    public JsonObject configuration(){return config.deepCopy();}
    public void seen(Actor a)throws Exception{database.communityTransaction(()->{seen(a,a.admin?"Администратор":"Игрок");return null;});}
    public void seen(Actor a,String role)throws Exception{database.communityTransaction(()->{try(var s=connection().prepareStatement("INSERT INTO people VALUES(?,?,?,?) ON CONFLICT(id) DO UPDATE SET name=excluded.name,seen=excluded.seen,role=excluded.role")){s.setString(1,a.id);s.setString(2,a.name);s.setLong(3,System.currentTimeMillis());s.setString(4,role);s.executeUpdate();}return null;});}
    public JsonArray people(String query,int page)throws Exception{return database.communityTransaction(()->{var out=new JsonArray();try(var s=connection().prepareStatement("SELECT id,name,seen,role FROM people WHERE strpos(lower(name),lower(?))>0 ORDER BY name LIMIT 20 OFFSET ?")){s.setString(1,query);s.setInt(2,page*20);try(var rows=s.executeQuery()){while(rows.next()){var j=new JsonObject();j.addProperty("uuid",rows.getString(1));j.addProperty("name",rows.getString(2));j.addProperty("seen",rows.getLong(3));j.addProperty("role",rows.getString(4));out.add(j);}}}return out;});}
    public JsonObject peopleCursor(String query,String cursor)throws Exception{return database.communityTransaction(()->{String scope="people:"+query,after=PlayerCursor.after(cursor,scope);var rows=new JsonArray();try(var q=connection().prepareStatement("SELECT id,name,seen FROM people WHERE strpos(lower(name),lower(?))>0 AND id>? ORDER BY id COLLATE \"C\" LIMIT 21")){q.setString(1,query);q.setString(2,after);try(var rs=q.executeQuery()){while(rs.next()){var j=new JsonObject();j.addProperty("uuid",rs.getString(1));j.addProperty("name",rs.getString(2));j.addProperty("seen",rs.getLong(3));rows.add(j);}}}return PlayerCursor.page(rows,scope);});}
    public String personName(String id)throws Exception{return database.communityTransaction(()->{try(var q=connection().prepareStatement("SELECT name FROM people WHERE id=?")){q.setString(1,id);try(var rs=q.executeQuery()){if(!rs.next())throw new IllegalArgumentException("Unknown player");return rs.getString(1);}}});}
    private JsonObject get(String id)throws Exception{try(var s=connection().prepareStatement("SELECT body FROM documents WHERE id=? FOR UPDATE")){s.setString(1,id);try(var r=s.executeQuery()){if(!r.next())throw new CommunityFailure(CommunityFailure.Code.NOT_FOUND,"Запись не найдена. Обновите список");return document(r.getString(1));}}}
    private static JsonObject document(String json){var j=JsonParser.parseString(json).getAsJsonObject();if(!j.has("revision")||j.get("revision").isJsonNull())j.addProperty("revision",0);if(j.has("participantOrder")){var ordered=new JsonObject();for(var id:j.getAsJsonArray("participantOrder"))ordered.add(id.getAsString(),j.getAsJsonObject("participants").get(id.getAsString()));j.add("participants",ordered);}return j;}
    private void put(JsonObject j)throws Exception{put(j,true);}
    private void put(JsonObject j,boolean visible)throws Exception{if(visible)j.addProperty("revision",j.has("revision")?j.get("revision").getAsLong()+1:1);String body=Json.GSON.toJson(j);if(body.length()>200000)throw new IllegalArgumentException("Запись достигла предела размера");try(var s=connection().prepareStatement("INSERT INTO documents(id,section,body) VALUES(?,?,?::jsonb) ON CONFLICT(id) DO UPDATE SET body=excluded.body")){s.setString(1,str(j,"id"));s.setString(2,str(j,"section"));s.setString(3,body);s.executeUpdate();}if(visible)CommunityOutbox.add(database,str(j,"section"),str(j,"id"),"");}
    private boolean enabled(String section){return config.getAsJsonArray("sections").contains(new JsonPrimitive(section));}
    private static boolean owner(JsonObject j,Actor a){return a.id.equals(str(j,"owner"));}
    private static void require(boolean ok){if(!ok)throw new CommunityFailure(CommunityFailure.Code.FORBIDDEN,"Действие недоступно или недостаточно прав");}
    private static String str(JsonObject j,String k){return Json.str(j,k);}
    private static String text(JsonObject j,String k,int max){String v=str(j,k).strip();if(v.isEmpty()||v.length()>max||v.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'))throw new IllegalArgumentException("Некорректное поле: "+k);return v;}
    private static int number(JsonObject j,String k,int min,int max){int v=j.get(k).getAsInt();if(v<min||v>max)throw new IllegalArgumentException("Некорректное поле: "+k);return v;}
    private static JsonObject map(JsonObject j,String k){return j.getAsJsonObject(k);}
    private static String value(JsonObject j,String k){return Json.opt(j,k,"");}
    private static boolean contains(JsonObject j,String k,String id){return map(j,k).has(id);}
    private static long time(JsonObject j,String k){return j.get(k).getAsLong();}
    private static void boundedMap(JsonObject j,String k,String id){if(map(j,k).size()>=300&&!map(j,k).has(id))throw new IllegalArgumentException("Достигнут предел участников");}
    private void note(String recipient,String section,String target,String title)throws Exception{
        database.lock("notice:"+recipient);CommunityOutbox.add(database,"notifications",target,recipient);
        var j=new JsonObject();j.addProperty("section",section);j.addProperty("target",target);j.addProperty("title",title);j.addProperty("at",System.currentTimeMillis());
        try(var s=connection().prepareStatement("INSERT INTO notices(recipient,body) VALUES(?,?::jsonb)")){s.setString(1,recipient);s.setString(2,Json.GSON.toJson(j));s.executeUpdate();}
        try(var s=connection().prepareStatement("DELETE FROM notices WHERE recipient=? AND id NOT IN (SELECT id FROM notices WHERE recipient=? ORDER BY id DESC LIMIT 200)")){s.setString(1,recipient);s.setString(2,recipient);s.executeUpdate();}
    }
    public void externalNotice(String recipient,String title)throws Exception{database.communityTransaction(()->{externalNotice(recipient,title,"");return null;});}
    public void externalNotice(String recipient,String title,String target)throws Exception{database.communityTransaction(()->{note(recipient,"help",target,title);return null;});}
    public int unread(String id)throws Exception{return database.communityTransaction(()->{try(var s=connection().prepareStatement("SELECT count(*) FROM notices WHERE recipient=? AND read=0")){s.setString(1,id);try(var r=s.executeQuery()){r.next();return r.getInt(1);}}});}
    public long popupSequence(String id)throws Exception{return database.communityTransaction(()->{var prefs=preferences(id);var muted=prefs.has("muted")?prefs.getAsJsonArray("muted"):new JsonArray();try(var q=connection().prepareStatement("SELECT id,body FROM notices WHERE recipient=? AND read=0 ORDER BY id DESC")){q.setString(1,id);try(var rs=q.executeQuery()){while(rs.next()){var body=JsonParser.parseString(rs.getString(2)).getAsJsonObject();if(!muted.contains(new JsonPrimitive(Json.str(body,"section"))))return rs.getLong(1);}}}return 0L;});}
    public JsonObject preferences(String id)throws Exception{return database.communityTransaction(()->{try(var s=connection().prepareStatement("SELECT body FROM preferences WHERE id=?")){s.setString(1,id);try(var r=s.executeQuery()){return r.next()?JsonParser.parseString(r.getString(1)).getAsJsonObject():new JsonObject();}}});}
    private JsonObject publicView(JsonObject source,Actor a,int page)throws Exception{var j=source.deepCopy();j.add("actions",CommunityPolicy.actions(source,a,System.currentTimeMillis()));String section=str(j,"section");j.addProperty("manage",owner(j,a)||a.admin);
        if(section.equals("board")){var replies=map(j,"responses");if(!owner(j,a)&&!a.admin)replies.keySet().removeIf(k->!k.equals(a.id));}
        if(section.equals("groups")){boolean manager=manager(j,a);j.addProperty("manage",manager);if(!manager)map(j,"applications").keySet().removeIf(k->!k.equals(a.id));}
        if(section.equals("polls")){var votes=map(j,"votes");j.addProperty("voted",votes.has(a.id));j.add("myVote",votes.has(a.id)?votes.get(a.id).deepCopy():new JsonArray());var counts=new JsonArray();boolean show=j.get("liveResults").getAsBoolean()||System.currentTimeMillis()>=time(j,"endsAt");for(int i=0;i<j.getAsJsonArray("options").size();i++){int count=0;for(var v:votes.asMap().values())if(v.getAsJsonArray().contains(new JsonPrimitive(i)))count++;counts.add(show?count:-1);}j.add("counts",counts);j.remove("votes");}
        if(section.equals("board")){j.addProperty("hasResponded",contains(source,"responses",a.id));slice(j,"responses",page,5);}
        if(section.equals("groups")){j.addProperty("isMember",contains(source,"members",a.id));j.addProperty("hasApplication",contains(source,"applications",a.id));slice(j,"members",page,20);slice(j,"applications",page,5);if(!manager(source,a))map(j,"invitations").keySet().removeIf(k->!k.equals(a.id));slice(j,"invitations",page,20);}
        if(section.equals("ideas")){j.addProperty("supportersCount",map(j,"supporters").size());map(j,"supporters").keySet().removeIf(key->!key.equals(a.id));}
        if(section.equals("events")){int position=new java.util.ArrayList<>(map(source,"participants").keySet()).indexOf(a.id);int capacity=source.get("capacity").getAsInt();j.addProperty("participantOffset",page*20);j.addProperty("waitlistPosition",position<0||capacity==0||position<capacity?0:position-capacity+1);j.remove("reminded");j.remove("participantOrder");j.addProperty("isParticipant",contains(source,"participants",a.id));slice(j,"participants",page,20);}
        if(!owner(source,a)&&!a.admin)j.remove("moderationReason");
        if(j.has("history")){var history=j.getAsJsonArray("history");var recent=new JsonArray();for(int i=Math.max(0,history.size()-5);i<history.size();i++)recent.add(history.get(i));j.add("history",recent);}
        if(j.has("location")&&j.getAsJsonObject("location").has("membersOnly")&&j.getAsJsonObject("location").get("membersOnly").getAsBoolean()){
            boolean allowed=a.admin||owner(source,a)||str(source,"section").equals("groups")&&contains(source,"members",a.id);
            if(!allowed&&!value(source,"group").isEmpty()){var group=get(value(source,"group"));allowed=str(group,"status").equals("open")&&contains(group,"members",a.id);}
            if(!allowed)j.remove("location");
        }return j;
    }
    private static void slice(JsonObject j,String key,int page,int size){var source=map(j,key);j.addProperty(key+"Count",source.size());var limited=new JsonObject();source.entrySet().stream().skip((long)page*size).limit(size).forEach(e->limited.add(e.getKey(),e.getValue()));j.add(key,limited);}
    public JsonObject request(Actor a,JsonObject input)throws Exception{return database.communityTransaction(()->{
        String op=text(input,"op",30),section=text(input,"section",30);require(enabled(section));
        if(section.equals("groups")&&!Set.of("list","detail").contains(op))database.lock("community:groups");
        if(op.equals("create"))database.lock("community:create:"+section);
        var receipt=new RequestJournal(database).execute(a.id,receiptInput(a,input),()->{JsonObject result=execute(a,input,op,section);if(!Set.of("list","detail","read").contains(op))audit(a.name,"community "+op,section+" "+value(input,"id"));return result;});
        if(receipt.has("replayed")){var read=input.deepCopy();read.addProperty("op",receipt.has("detail")?"detail":"list");if(receipt.has("detail"))read.addProperty("id",Json.str(receipt.getAsJsonObject("detail"),"id"));var current=execute(a,read,Json.str(read,"op"),section);current.addProperty("replayed",true);return current;}return receipt;
    });}
    private JsonObject receiptInput(Actor a,JsonObject input){var copy=input.deepCopy();copy.addProperty("permissions",a.admin+":"+a.events);return copy;}
    private JsonObject execute(Actor a,JsonObject in,String op,String section)throws Exception{
        long now=System.currentTimeMillis();String id=value(in,"id");
        if(op.equals("preferences")){var p=in.getAsJsonObject("preferences");Json.keys(p,"favorites","muted");for(String k:List.of("favorites","muted")){if(p.getAsJsonArray(k).size()>10)throw new IllegalArgumentException("Too many settings");for(var v:p.getAsJsonArray(k))require(SECTIONS.contains(v.getAsString()));}try(var s=connection().prepareStatement("INSERT INTO preferences VALUES(?,?::jsonb) ON CONFLICT(id) DO UPDATE SET body=excluded.body")){s.setString(1,a.id);s.setString(2,Json.GSON.toJson(p));s.executeUpdate();}}
        else if(op.equals("read")){try(var s=connection().prepareStatement("UPDATE notices SET read=1 WHERE recipient=? AND (?='' OR id=?)")){s.setString(1,a.id);s.setString(2,id);s.setLong(3,id.isEmpty()?0:Long.parseLong(id));s.executeUpdate();}}
        else if(op.equals("create")){
            require(Set.of("board","groups","events","polls","ideas").contains(section));require(!section.equals("events")||a.events||a.admin);require(!section.equals("polls")||a.admin);
            checkCreate(section,a,now);
            var j=new JsonObject();id=UUID.randomUUID().toString();j.addProperty("id",id);j.addProperty("section",section);j.addProperty("owner",a.id);j.addProperty("author",a.name);j.addProperty("title",text(in,"title",100));j.addProperty("description",text(in,"description",1500));j.addProperty("createdAt",now);j.addProperty("status","open");
            switch(section){
                case "board" -> {j.addProperty("endsAt",now+number(in,"days",1,90)*86400000L);String category=value(in,"category");require(category.isEmpty()||config.getAsJsonArray("categories").contains(new JsonPrimitive(category)));j.addProperty("category",category);j.add("responses",new JsonObject());}
                case "groups" -> {require(memberships(a.id)<config.get("maxMemberships").getAsInt());String type=text(in,"type",40);require(config.getAsJsonArray("groupTypes").contains(new JsonPrimitive(type)));j.addProperty("type",type);j.addProperty("recruiting",true);var members=new JsonObject();members.addProperty(a.id,"leader");j.add("members",members);j.add("applications",new JsonObject());j.add("invitations",new JsonObject());}
                case "events" -> {long start=Instant.parse(text(in,"startsAt",40)).toEpochMilli();require(start>now);j.addProperty("startsAt",start);j.addProperty("capacity",number(in,"capacity",0,300));j.add("participants",new JsonObject());j.add("participantOrder",new JsonArray());j.add("reminded",new JsonObject());}
                case "polls" -> {long end=Instant.parse(text(in,"endsAt",40)).toEpochMilli();require(end>now);j.addProperty("endsAt",end);var options=in.getAsJsonArray("options");require(options.size()>=2&&options.size()<=8);var unique=new HashSet<String>();for(var o:options)require(!o.getAsString().isBlank()&&o.getAsString().length()<=100&&unique.add(o.getAsString()));j.add("options",options.deepCopy());for(String k:List.of("multiple","changeVote","liveResults"))j.addProperty(k,in.has(k)&&in.get(k).getAsBoolean());j.add("votes",new JsonObject());}
                case "ideas" -> {j.add("supporters",new JsonObject());j.addProperty("status","new");j.addProperty("answer","");}
            }if(Set.of("board","events").contains(section)&&!value(in,"group").isEmpty()){var group=get(value(in,"group"));require(str(group,"section").equals("groups")&&str(group,"status").equals("open")&&contains(group,"members",a.id));j.addProperty("group",str(group,"id"));j.addProperty("groupName",str(group,"title"));}if(in.has("location")&&!in.get("location").isJsonNull())setLocation(j,in);EntryHistory.append(j,op,a.name,now);put(j);
        }else if(!op.equals("list")&&!op.equals("detail")){
            var j=get(id);require(section.equals(str(j,"section")));require(!str(j,"status").equals("hidden")||a.admin||owner(j,a));String title=str(j,"title");boolean own=owner(j,a);CommunityPolicy.check(j,a,op,now);
            if(Set.of("respondDecision","close","complete","status","reschedule","cancel","role","application","recruiting","hide","delete","restore","location","edit","withdrawResponse","withdrawApplication","revokeInvitation").contains(op)&&(in.has("operationId")&&!in.has("revision")||in.has("revision")&&in.get("revision").getAsLong()!=(j.has("revision")?j.get("revision").getAsLong():0)))throw new CommunityFailure(CommunityFailure.Code.CONFLICT,"Запись уже изменена. Обновите её; ваш текст сохранён.");
            if(op.equals("delete")){j.addProperty("previousStatus",str(j,"status"));j.addProperty("status","deleted");j.addProperty("deletedAt",now);j.addProperty("deletedBy",a.id);
                try(var q=connection().prepareStatement("DELETE FROM notices WHERE body->>'target'=?")){q.setString(1,id);q.executeUpdate();}
                if(section.equals("groups"))for(var child:bodies("SELECT body FROM documents WHERE body->>'group'=? ORDER BY sequence FOR UPDATE",id)){child.remove("group");child.remove("groupName");put(child);}
            }else if(op.equals("restore")){if(section.equals("groups"))for(String member:map(j,"members").keySet())if(memberships(member)>=config.get("maxMemberships").getAsInt())throw new CommunityFailure(CommunityFailure.Code.CONFLICT,"Участник достиг лимита объединений. Сначала освободите место для восстановления.");j.addProperty("status",str(j,"previousStatus"));j.remove("previousStatus");j.remove("deletedAt");j.remove("deletedBy");
            }else if(op.equals("edit")){
                j.addProperty("title",text(in,"title",100));j.addProperty("description",text(in,"description",1500));
                if(section.equals("groups")){String type=text(in,"type",40);require(config.getAsJsonArray("groupTypes").contains(new JsonPrimitive(type)));j.addProperty("type",type);
                    for(var child:bodies("SELECT body FROM documents WHERE body->>'group'=? ORDER BY sequence FOR UPDATE",id)){child.addProperty("groupName",str(j,"title"));put(child);}}
                if(in.has("location"))setLocation(j,in);
                if(section.equals("events"))for(String target:map(j,"participants").keySet())if(!target.equals(a.id))note(target,section,id,"Обновлено событие: "+str(j,"title"));
            }else if(op.equals("location")){setLocation(j,in);
            }else if(op.equals("hide")){require(a.admin);String reason=text(in,"text",500);j.addProperty("status","hidden");j.addProperty("moderationReason",reason);note(str(j,"owner"),section,id,"Скрыто: "+title+" · "+reason);}
            else if(section.equals("board")){
                switch(op){
                    case "respond" -> {require(!own&&str(j,"status").equals("open")&&time(j,"endsAt")>now);require(!contains(j,"responses",a.id));boundedMap(j,"responses",a.id);var reply=new JsonObject();reply.addProperty("name",a.name);reply.addProperty("text",text(in,"text",1000));reply.addProperty("status","pending");reply.addProperty("answer","");map(j,"responses").add(a.id,reply);note(str(j,"owner"),section,id,"Новый отклик: "+title);}
                    case "withdrawResponse" -> {var reply=map(j,"responses").getAsJsonObject(a.id);require(reply!=null);
                        if(str(reply,"status").equals("accepted")&&(!in.has("confirmAccepted")||!in.get("confirmAccepted").getAsBoolean()))throw new CommunityFailure(CommunityFailure.Code.INVALID,"Отклик уже принят. Подтвердите его отмену.");
                        map(j,"responses").remove(a.id);note(str(j,"owner"),section,id,a.name+" отозвал отклик: "+title);}
                    case "respondDecision" -> {require(own);String target=text(in,"target",36),decision=text(in,"decision",20);require(Set.of("accepted","declined","pending").contains(decision)&&contains(j,"responses",target));var reply=map(j,"responses").getAsJsonObject(target);reply.addProperty("status",decision);reply.addProperty("answer",text(in,"text",1000));note(target,section,id,"Ответ на отклик: "+title);}
                    case "close","complete" -> {require(own);j.addProperty("status",op.equals("complete")?"done":"closed");}
                    default -> throw new IllegalArgumentException("Неизвестное действие");
                }
            }else if(section.equals("ideas")){
                if(op.equals("support")){require(!str(j,"status").equals("hidden"));var supporters=map(j,"supporters");if(supporters.has(a.id))supporters.remove(a.id);else{boundedMap(j,"supporters",a.id);supporters.addProperty(a.id,a.name);}}
                else if(op.equals("status")){require(a.admin);String status=text(in,"status",20);require(Set.of("new","discussion","planned","done","declined").contains(status));j.addProperty("status",status);j.addProperty("answer",text(in,"text",1000));note(str(j,"owner"),section,id,"Обновлено предложение: "+title);}else throw new IllegalArgumentException("Неизвестное действие");
            }else if(section.equals("polls")){
                require(op.equals("vote")&&time(j,"endsAt")>now&&str(j,"status").equals("open"));var votes=map(j,"votes");require(!votes.has(a.id)||j.get("changeVote").getAsBoolean());var chosen=in.getAsJsonArray("choices");require(!chosen.isEmpty()&&(j.get("multiple").getAsBoolean()||chosen.size()==1));var unique=new HashSet<Integer>();for(var c:chosen){int n=c.getAsInt();require(n>=0&&n<j.getAsJsonArray("options").size()&&unique.add(n));}boundedMap(j,"votes",a.id);votes.add(a.id,chosen.deepCopy());
            }else if(section.equals("events")){
                switch(op){
                    case "join" -> {require(str(j,"status").equals("open")&&time(j,"startsAt")>now);boundedMap(j,"participants",a.id);if(!contains(j,"participants",a.id)){map(j,"participants").addProperty(a.id,a.name);j.getAsJsonArray("participantOrder").add(a.id);}}
                    case "leave" -> {require(contains(j,"participants",a.id));int priorIndex=new ArrayList<>(map(j,"participants").keySet()).indexOf(a.id);map(j,"participants").remove(a.id);j.getAsJsonArray("participantOrder").remove(new JsonPrimitive(a.id));int capacity=j.get("capacity").getAsInt();if(capacity>0&&priorIndex<capacity&&map(j,"participants").size()>=capacity){String promoted=new ArrayList<>(map(j,"participants").keySet()).get(capacity-1);note(promoted,section,id,"Освободилось место: "+title);}}
                    case "reschedule" -> {require(own||a.admin);long start=Instant.parse(text(in,"startsAt",40)).toEpochMilli();require(start>now);j.addProperty("startsAt",start);j.add("reminded",new JsonObject());for(String target:map(j,"participants").keySet())note(target,section,id,"Событие перенесено: "+title);}
                    case "cancel" -> {require(own||a.admin);j.addProperty("status","cancelled");for(String target:map(j,"participants").keySet())note(target,section,id,"Событие отменено: "+title);}
                    default -> throw new IllegalArgumentException("Неизвестное действие");
                }
            }else if(section.equals("groups")){
                var members=map(j,"members");String target=value(in,"target");
                switch(op){
                    case "apply" -> {require(j.get("recruiting").getAsBoolean()&&!members.has(a.id)&&str(j,"status").equals("open"));boundedMap(j,"applications",a.id);require(!contains(j,"applications",a.id));var application=new JsonObject();application.addProperty("name",a.name);application.addProperty("text",text(in,"text",500));map(j,"applications").add(a.id,application);note(str(j,"owner"),section,id,"Заявка в "+title);}
                    case "withdrawApplication" -> {require(contains(j,"applications",a.id));map(j,"applications").remove(a.id);note(str(j,"owner"),section,id,a.name+" отозвал заявку: "+title);}
                    case "revokeInvitation" -> {require(manager(j,a)&&contains(j,"invitations",target));map(j,"invitations").remove(target);note(target,section,id,"Приглашение отменено: "+title);}
                    case "application" -> {require(manager(j,a)&&contains(j,"applications",target));if(in.get("accept").getAsBoolean()){addMember(j,target);map(j,"invitations").remove(target);}map(j,"applications").remove(target);note(target,section,id,"Рассмотрена заявка: "+title);}
                    case "invite" -> {require(manager(j,a)&&!members.has(target));UUID.fromString(target);boundedMap(j,"invitations",target);require(!contains(j,"invitations",target));map(j,"invitations").addProperty(target,true);note(target,section,id,"Приглашение: "+title);}
                    case "invitation" -> {require(contains(j,"invitations",a.id));if(in.get("accept").getAsBoolean()){addMember(j,a.id);map(j,"applications").remove(a.id);}map(j,"invitations").remove(a.id);}
                    case "leave" -> {require(members.has(a.id)&&!own);members.remove(a.id);}
                    case "role" -> {require(own&&members.has(target)&&!target.equals(a.id));String role=text(in,"role",20);require(Set.of("member","assistant","leader").contains(role));members.addProperty(target,role);if(role.equals("leader")){members.addProperty(a.id,"member");j.addProperty("owner",target);}note(target,section,id,"Изменена роль: "+title);}
                    case "recruiting" -> {require(manager(j,a));j.addProperty("recruiting",!j.get("recruiting").getAsBoolean());}
                    default -> throw new IllegalArgumentException("Неизвестное действие");
                }
            }else throw new IllegalArgumentException("Неизвестное действие");
            EntryHistory.append(j,op,a.name,now);put(j);
        }
        var out=new JsonObject();out.addProperty("kind","community");out.addProperty("section",section);out.addProperty("request",value(in,"request"));out.add("config",config.deepCopy());out.addProperty("unread",unread(a.id));out.add("preferences",preferences(a.id));if(section.equals("home")){var pinned=record("state","pinned-announcement");if(pinned!=null&&pinned.get("until").getAsLong()>now&&!Json.opt(pinned,"text","").isBlank())out.add("pinnedAnnouncement",pinned);}var ownGroups=new JsonArray();for(var group:memberGroups(a.id)){var option=new JsonObject();option.addProperty("value",str(group,"id"));option.addProperty("label",str(group,"title"));ownGroups.add(option);}out.add("groups",ownGroups);out.addProperty("canCreate",a.admin||section.equals("events")&&a.events||Set.of("board","groups","ideas").contains(section));
        if(!id.isEmpty()&&!section.equals("notifications")){var j=get(id);require(section.equals(str(j,"section")));require(!Set.of("hidden","deleted").contains(str(j,"status"))||owner(j,a)||a.admin);int detailPage=0;String token=value(in,"cursor"),scope=a.id+":"+id+":"+section;long revision=j.get("revision").getAsLong();if(op.equals("detail")&&!token.isEmpty()){var cursor=CommunityCursor.decode(token,scope);if(cursor.time()!=revision)throw new CommunityFailure(CommunityFailure.Code.CONFLICT,"Состав записи изменился. Обновляем карточку.");if(cursor.sequence()>60)throw new IllegalArgumentException("Invalid detail cursor");detailPage=(int)cursor.sequence();if(detailPage>60)throw new IllegalArgumentException("Invalid detail cursor");}else if(!in.has("action")&&in.has("page"))detailPage=number(in,"page",0,1000);
        var detail=publicView(j,a,detailPage);out.add("detail",detail);boolean more=false;for(String field:List.of("responses","applications","members","participants","invitations"))if(detail.has(field+"Count")){int size=Set.of("responses","applications").contains(field)?5:20;more|=(detailPage+1)*size<detail.get(field+"Count").getAsInt();}out.addProperty("nextCursor",more?new CommunityCursor(detailPage+1,revision,1000,scope).encode():"");var names=new JsonObject();if(section.equals("groups")){try(var q=connection().prepareStatement("SELECT id,name FROM people WHERE id=ANY(?)")){q.setArray(1,connection().createArrayOf("text",groupPeople(detail).toArray()));try(var r=q.executeQuery()){while(r.next())names.addProperty(r.getString(1),r.getString(2));}}}out.add("names",names);if(section.equals("groups")){var related=new JsonArray();for(String kind:List.of("board","events")){if(!enabled(kind))continue;for(var child:related(kind,id)){var row=new JsonObject();for(String key:List.of("id","section","title"))row.add(key,child.get(key));related.add(row);if(related.size()>=20)break;}if(related.size()>=20)break;}out.add("related",related);}return out;}
        int page=in.has("page")?number(in,"page",0,1000):0;out.addProperty("page",page);var entries=new JsonArray();
        if(section.equals("notifications"))entries=new CommunityQueries(database).notices(a,in);
        else entries=listPage(a,in,section,page,now);
        String next="";for(var entry:entries){var row=entry.getAsJsonObject();if(row.has("_nextCursor")){next=Json.str(row,"_nextCursor");row.remove("_nextCursor");}}out.addProperty("nextCursor",next);out.add("entries",entries);return out;
    }
    private static Set<String> groupPeople(JsonObject detail){var ids=new HashSet<String>();for(String key:List.of("members","applications","invitations"))ids.addAll(map(detail,key).keySet());return ids;}
    private boolean manager(JsonObject j,Actor a){return a.admin||owner(j,a)||contains(j,"members",a.id)&&map(j,"members").get(a.id).getAsString().equals("assistant");}
    private int memberships(String id)throws Exception{try(var q=connection().prepareStatement("SELECT count(*) FROM documents WHERE section='groups' AND body->>'status'='open' AND jsonb_exists(body->'members',?)")){q.setString(1,id);try(var r=q.executeQuery()){r.next();return r.getInt(1);}}}
    private void addMember(JsonObject j,String id)throws Exception{require(memberships(id)<config.get("maxMemberships").getAsInt());boundedMap(j,"members",id);map(j,"members").addProperty(id,"member");}
    public JsonArray pendingChanges()throws Exception{return database.communityTransaction(()->CommunityOutbox.pending(database));}
    public void acknowledgeChanges(JsonArray events)throws Exception{database.communityTransaction(()->{CommunityOutbox.acknowledge(database,events);return null;});}
    private void setLocation(JsonObject document,JsonObject input){
        if(!Set.of("board","groups","events","polls","ideas").contains(str(document,"section")))throw new IllegalArgumentException("Место недоступно в этом разделе");
        if(!input.has("location")||input.get("location").isJsonNull()){document.remove("location");return;}
        var location=CommunityLocation.read(input.getAsJsonObject("location"));
        if(location.membersOnly()&&!str(document,"section").equals("groups")&&value(document,"group").isEmpty())throw new IllegalArgumentException("Закрытое место доступно только для объединения");
        document.add("location",location.json());
    }
    public void purgeDeleted(long now)throws Exception{database.communityTransaction(()->{
        database.lock("community:groups");
        try(var q=connection().prepareStatement("DELETE FROM notices WHERE body->>'target' IN (SELECT id FROM documents WHERE body->>'status'='deleted' AND (body->>'deletedAt')::bigint<=?)")){q.setLong(1,now-30L*86400000);q.executeUpdate();}
        try(var q=connection().prepareStatement("DELETE FROM documents WHERE body->>'status'='deleted' AND (body->>'deletedAt')::bigint<=?")){q.setLong(1,now-30L*86400000);q.executeUpdate();}return null;
    });}
    public void reminders(long now)throws Exception{database.communityTransaction(()->{if(lastReminderSweep!=0&&now-lastReminderSweep<30000)return null;try{for(var j:due("events",now)){long start=time(j,"startsAt");if(!str(j,"status").equals("open")||start<now||start-now>300000)continue;boolean changed=false;for(String target:map(j,"participants").keySet())if(!contains(j,"reminded",target)){note(target,"events",str(j,"id"),"Скоро событие: "+str(j,"title"));map(j,"reminded").addProperty(target,true);changed=true;}if(changed)put(j,false);}for(var j:due("polls",now)){if(time(j,"endsAt")<=now&&!j.has("notified")){for(String target:map(j,"votes").keySet())note(target,"polls",str(j,"id"),"Голосование завершено: "+str(j,"title"));j.addProperty("notified",true);put(j,false);}}lastReminderSweep=now;}catch(Exception ex){lastReminderSweep=0;throw ex;}return null;});}
    public <T>T inTransaction(PgDatabase.Work<T> work)throws Exception{return database.communityTransaction(work);}
    public interface Transaction {void run()throws Exception;}
    public void transaction(Transaction work)throws Exception{database.communityTransaction(()->{work.run();return null;});}
    public JsonObject record(String namespace,String id)throws Exception{return database.communityTransaction(()->{try(var q=connection().prepareStatement("SELECT body FROM records WHERE namespace=? AND id=? FOR UPDATE")){q.setString(1,namespace);q.setString(2,id);try(var r=q.executeQuery()){return r.next()?JsonParser.parseString(r.getString(1)).getAsJsonObject():null;}}});}
    public void record(String namespace,String id,JsonObject body)throws Exception{database.communityTransaction(()->{try(var q=connection().prepareStatement("INSERT INTO records(namespace,id,body) VALUES(?,?,?::jsonb) ON CONFLICT(namespace,id) DO UPDATE SET body=excluded.body")){q.setString(1,namespace);q.setString(2,id);q.setString(3,Json.GSON.toJson(body));q.executeUpdate();}return null;});}
    public List<JsonObject> records(String namespace)throws Exception{return database.communityTransaction(()->{var out=new ArrayList<JsonObject>();try(var q=connection().prepareStatement("SELECT body FROM records WHERE namespace=? ORDER BY sequence DESC")){q.setString(1,namespace);try(var r=q.executeQuery()){while(r.next())out.add(JsonParser.parseString(r.getString(1)).getAsJsonObject());}}return out;});}
    public void deleteRecord(String namespace,String id)throws Exception{database.communityTransaction(()->{try(var q=connection().prepareStatement("DELETE FROM records WHERE namespace=? AND id=?")){q.setString(1,namespace);q.setString(2,id);q.executeUpdate();}return null;});}
    public void audit(String actor,String action,String detail)throws Exception{database.communityTransaction(()->{var j=new JsonObject();String id=UUID.randomUUID().toString();j.addProperty("id",id);j.addProperty("at",System.currentTimeMillis());j.addProperty("actor",actor);j.addProperty("action",action);j.addProperty("detail",detail.substring(0,Math.min(1000,detail.length())));record("audit",id,j);try(var q=connection().createStatement()){q.executeUpdate("DELETE FROM records WHERE namespace='audit' AND sequence NOT IN (SELECT sequence FROM records WHERE namespace='audit' ORDER BY sequence DESC LIMIT 1000)");}return null;});}

    private void checkCreate(String section,Actor actor,long now)throws Exception{
        try(var q=connection().prepareStatement("SELECT count(*),count(*) FILTER (WHERE body->>'owner'=? AND (body->>'createdAt')::bigint>?) FROM documents WHERE section=?")){
            q.setString(1,actor.id);q.setLong(2,now-60000);q.setString(3,section);
            try(var r=q.executeQuery()){r.next();if(r.getInt(1)>=2000)throw new IllegalArgumentException("Раздел заполнен");if(!actor.admin&&r.getInt(2)>0)throw new IllegalArgumentException("Подождите минуту перед новой публикацией");}
        }
    }
    private List<JsonObject> memberGroups(String id)throws Exception{
        return bodies("SELECT body FROM documents WHERE section='groups' AND body->>'status'='open' AND jsonb_exists(body->'members',?) ORDER BY sequence DESC",id);
    }
    private List<JsonObject> related(String section,String group)throws Exception{
        return bodies("SELECT body FROM documents WHERE section=? AND body->>'group'=? AND body->>'status' NOT IN ('hidden','deleted') ORDER BY sequence DESC LIMIT 20",section,group);
    }
    private List<JsonObject> due(String section,long now)throws Exception{
        String predicate=section.equals("events")?"body->>'status'='open' AND (body->>'startsAt')::bigint BETWEEN ? AND ?":"body->>'status'='open' AND (body->>'endsAt')::bigint<=? AND NOT jsonb_exists(body,'notified')";
        return section.equals("events")?bodies("SELECT body FROM documents WHERE section='events' AND "+predicate+" ORDER BY sequence FOR UPDATE SKIP LOCKED",now,now+300000):bodies("SELECT body FROM documents WHERE section='polls' AND "+predicate+" ORDER BY sequence FOR UPDATE SKIP LOCKED",now);
    }
    private List<JsonObject> bodies(String sql,Object...args)throws Exception{
        var out=new ArrayList<JsonObject>();try(var q=connection().prepareStatement(sql)){for(int i=0;i<args.length;i++)q.setObject(i+1,args[i]);try(var r=q.executeQuery()){while(r.next())out.add(JsonParser.parseString(r.getString(1)).getAsJsonObject());}}return out;
    }
    private JsonArray listPage(Actor a,JsonObject in,String section,int page,long now)throws Exception {
        return new CommunityQueries(database).list(a,in,section,now,configuration());
    }
    public record NoticeSummary(int unread,long sequence){}
    public Map<UUID,NoticeSummary> notificationSummary(Collection<UUID> players)throws Exception{return database.communityTransaction(()->{
        var result=new HashMap<UUID,NoticeSummary>();if(players.isEmpty())return result;
        String sql="SELECT n.recipient,count(*),coalesce(max(n.id) FILTER (WHERE NOT EXISTS (SELECT 1 FROM jsonb_array_elements_text(coalesce(p.body->'muted','[]'::jsonb)) AS muted(section) WHERE muted.section=n.body->>'section')),0) FROM notices n LEFT JOIN preferences p ON p.id=n.recipient WHERE n.read=0 AND n.recipient=ANY(?) GROUP BY n.recipient";
        try(var q=connection().prepareStatement(sql)){q.setArray(1,connection().createArrayOf("text",players.stream().map(UUID::toString).toArray()));try(var r=q.executeQuery()){while(r.next())result.put(UUID.fromString(r.getString(1)),new NoticeSummary(r.getInt(2),r.getLong(3)));}}for(UUID id:players)result.putIfAbsent(id,new NoticeSummary(0,0));return result;
    });}
    public List<JsonObject> recordPage(String namespace,String owner,int page,int size)throws Exception{return database.communityTransaction(()->{
        if(page<0||page>1000||size<1||size>20)throw new IllegalArgumentException("Invalid page");return bodies("SELECT body FROM records WHERE namespace=? AND (?='' OR body->>'uuid'=?) ORDER BY sequence DESC LIMIT ? OFFSET ?",namespace,owner,owner,size,page*size);
    });}
    public JsonObject recordCursor(String namespace,String owner,String token,int size)throws Exception{return database.communityTransaction(()->{
        String scope="records:"+namespace+":"+owner;var cursor=token.isEmpty()?null:CommunityCursor.decode(token,scope);long ceiling=cursor==null?Long.MAX_VALUE:cursor.ceiling();var result=new JsonObject();var entries=new JsonArray();long last=0;boolean more=false;
        try(var q=connection().prepareStatement("SELECT sequence,body FROM records WHERE namespace=? AND (?='' OR body->>'uuid'=?) AND sequence<=? AND sequence<? ORDER BY sequence DESC LIMIT ?")){q.setString(1,namespace);q.setString(2,owner);q.setString(3,owner);q.setLong(4,ceiling);q.setLong(5,cursor==null?Long.MAX_VALUE:cursor.sequence());q.setInt(6,size+1);try(var rs=q.executeQuery()){while(rs.next()){if(entries.size()==size){more=true;break;}last=rs.getLong(1);if(entries.isEmpty()&&cursor==null)ceiling=last;entries.add(Json.parse(rs.getString(2)));}}}
        result.add("entries",entries);result.addProperty("nextCursor",more?new CommunityCursor(last,0,ceiling,scope).encode():"");return result;
    });}
    public JsonObject receipt(String actor,JsonObject input,PgDatabase.Work<JsonObject> work)throws Exception{return database.communityTransaction(()->new RequestJournal(database).execute(actor,input,work));}
    public void checkReport(String owner,long now)throws Exception{database.lock("report-create");try(var q=connection().prepareStatement("SELECT count(*),count(*) FILTER (WHERE body->>'uuid'=? AND (body->>'createdAt')::bigint>?) FROM records WHERE namespace='reports'")){q.setString(1,owner);q.setLong(2,now-60000);try(var r=q.executeQuery()){r.next();if(r.getInt(1)>=5000)throw new IllegalArgumentException("Хранилище обращений заполнено");if(r.getInt(2)>0)throw new IllegalArgumentException("Подождите минуту перед следующим обращением");}}}
    public void pruneReports(long now)throws Exception{database.communityTransaction(()->{try(var q=connection().prepareStatement("DELETE FROM records WHERE namespace='reports' AND body->>'status'='resolved' AND coalesce((body->>'repliedAt')::bigint,(body->>'createdAt')::bigint)<?")){q.setLong(1,now-30L*86400000);q.executeUpdate();}return null;});}

    @Override public void close()throws Exception{}
}
