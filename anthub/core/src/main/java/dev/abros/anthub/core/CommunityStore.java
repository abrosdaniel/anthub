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
    public void seen(Actor a)throws Exception{database.transaction(()->{seen(a,a.admin?"Администратор":"Игрок");return null;});}
    public void seen(Actor a,String role)throws Exception{database.transaction(()->{try(var s=connection().prepareStatement("INSERT INTO people VALUES(?,?,?,?) ON CONFLICT(id) DO UPDATE SET name=excluded.name,seen=excluded.seen,role=excluded.role")){s.setString(1,a.id);s.setString(2,a.name);s.setLong(3,System.currentTimeMillis());s.setString(4,role);s.executeUpdate();}return null;});}
    public JsonArray people(String query,int page)throws Exception{return database.transaction(()->{var out=new JsonArray();try(var s=connection().prepareStatement("SELECT id,name,seen,role FROM people WHERE strpos(lower(name),lower(?))>0 ORDER BY name LIMIT 20 OFFSET ?")){s.setString(1,query);s.setInt(2,page*20);try(var rows=s.executeQuery()){while(rows.next()){var j=new JsonObject();j.addProperty("uuid",rows.getString(1));j.addProperty("name",rows.getString(2));j.addProperty("seen",rows.getLong(3));j.addProperty("role",rows.getString(4));out.add(j);}}}return out;});}
    public String personName(String id)throws Exception{return database.transaction(()->{try(var q=connection().prepareStatement("SELECT name FROM people WHERE id=?")){q.setString(1,id);try(var rs=q.executeQuery()){if(!rs.next())throw new IllegalArgumentException("Unknown player");return rs.getString(1);}}});}
    private JsonObject get(String id)throws Exception{try(var s=connection().prepareStatement("SELECT body FROM documents WHERE id=? FOR UPDATE")){s.setString(1,id);try(var r=s.executeQuery()){if(!r.next())throw new IllegalArgumentException("Запись не найдена");return document(r.getString(1));}}}
    private static JsonObject document(String json){var j=JsonParser.parseString(json).getAsJsonObject();if(j.has("participantOrder")){var ordered=new JsonObject();for(var id:j.getAsJsonArray("participantOrder"))ordered.add(id.getAsString(),j.getAsJsonObject("participants").get(id.getAsString()));j.add("participants",ordered);}return j;}
    private void put(JsonObject j)throws Exception{String body=Json.GSON.toJson(j);if(body.length()>200000)throw new IllegalArgumentException("Запись достигла предела размера");try(var s=connection().prepareStatement("INSERT INTO documents(id,section,body) VALUES(?,?,?::jsonb) ON CONFLICT(id) DO UPDATE SET body=excluded.body")){s.setString(1,str(j,"id"));s.setString(2,str(j,"section"));s.setString(3,body);s.executeUpdate();}}
    private boolean enabled(String section){return config.getAsJsonArray("sections").contains(new JsonPrimitive(section));}
    private static boolean owner(JsonObject j,Actor a){return a.id.equals(str(j,"owner"));}
    private static void require(boolean ok){if(!ok)throw new IllegalArgumentException("Действие недоступно или недостаточно прав");}
    private static String str(JsonObject j,String k){return Json.str(j,k);}
    private static String text(JsonObject j,String k,int max){String v=str(j,k).strip();if(v.isEmpty()||v.length()>max||v.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'))throw new IllegalArgumentException("Некорректное поле: "+k);return v;}
    private static int number(JsonObject j,String k,int min,int max){int v=j.get(k).getAsInt();if(v<min||v>max)throw new IllegalArgumentException("Некорректное поле: "+k);return v;}
    private static JsonObject map(JsonObject j,String k){return j.getAsJsonObject(k);}
    private static String value(JsonObject j,String k){return Json.opt(j,k,"");}
    private static boolean contains(JsonObject j,String k,String id){return map(j,k).has(id);}
    private static long time(JsonObject j,String k){return j.get(k).getAsLong();}
    private static void boundedMap(JsonObject j,String k,String id){if(map(j,k).size()>=300&&!map(j,k).has(id))throw new IllegalArgumentException("Достигнут предел участников");}
    private void note(String recipient,String section,String target,String title)throws Exception{
        database.lock("notice:"+recipient);
        var j=new JsonObject();j.addProperty("section",section);j.addProperty("target",target);j.addProperty("title",title);j.addProperty("at",System.currentTimeMillis());
        try(var s=connection().prepareStatement("INSERT INTO notices(recipient,body) VALUES(?,?::jsonb)")){s.setString(1,recipient);s.setString(2,Json.GSON.toJson(j));s.executeUpdate();}
        try(var s=connection().prepareStatement("DELETE FROM notices WHERE recipient=? AND id NOT IN (SELECT id FROM notices WHERE recipient=? ORDER BY id DESC LIMIT 200)")){s.setString(1,recipient);s.setString(2,recipient);s.executeUpdate();}
    }
    public void externalNotice(String recipient,String title)throws Exception{database.transaction(()->{externalNotice(recipient,title,"");return null;});}
    public void externalNotice(String recipient,String title,String target)throws Exception{database.transaction(()->{note(recipient,"help",target,title);return null;});}
    public int unread(String id)throws Exception{return database.transaction(()->{try(var s=connection().prepareStatement("SELECT count(*) FROM notices WHERE recipient=? AND read=0")){s.setString(1,id);try(var r=s.executeQuery()){r.next();return r.getInt(1);}}});}
    public long popupSequence(String id)throws Exception{return database.transaction(()->{var prefs=preferences(id);var muted=prefs.has("muted")?prefs.getAsJsonArray("muted"):new JsonArray();try(var q=connection().prepareStatement("SELECT id,body FROM notices WHERE recipient=? AND read=0 ORDER BY id DESC")){q.setString(1,id);try(var rs=q.executeQuery()){while(rs.next()){var body=JsonParser.parseString(rs.getString(2)).getAsJsonObject();if(!muted.contains(new JsonPrimitive(Json.str(body,"section"))))return rs.getLong(1);}}}return 0L;});}
    public JsonObject preferences(String id)throws Exception{return database.transaction(()->{try(var s=connection().prepareStatement("SELECT body FROM preferences WHERE id=?")){s.setString(1,id);try(var r=s.executeQuery()){return r.next()?JsonParser.parseString(r.getString(1)).getAsJsonObject():new JsonObject();}}});}
    private JsonObject publicView(JsonObject source,Actor a,int page){var j=source.deepCopy();String section=str(j,"section");j.addProperty("manage",owner(j,a)||a.admin);
        if(section.equals("board")){var replies=map(j,"responses");if(!owner(j,a)&&!a.admin)replies.keySet().removeIf(k->!k.equals(a.id));}
        if(section.equals("groups")){boolean manager=manager(j,a);j.addProperty("manage",manager);if(!manager)map(j,"applications").keySet().removeIf(k->!k.equals(a.id));}
        if(section.equals("polls")){var votes=map(j,"votes");j.addProperty("voted",votes.has(a.id));j.add("myVote",votes.has(a.id)?votes.get(a.id).deepCopy():new JsonArray());var counts=new JsonArray();boolean show=j.get("liveResults").getAsBoolean()||System.currentTimeMillis()>=time(j,"endsAt");for(int i=0;i<j.getAsJsonArray("options").size();i++){int count=0;for(var v:votes.asMap().values())if(v.getAsJsonArray().contains(new JsonPrimitive(i)))count++;counts.add(show?count:-1);}j.add("counts",counts);j.remove("votes");}
        if(section.equals("board"))slice(j,"responses",page,5);
        if(section.equals("groups")){j.addProperty("isMember",contains(source,"members",a.id));slice(j,"members",page,20);slice(j,"applications",page,5);map(j,"invitations").keySet().removeIf(k->!k.equals(a.id));}
        if(section.equals("events")){j.remove("reminded");j.remove("participantOrder");j.addProperty("isParticipant",contains(source,"participants",a.id));slice(j,"participants",page,20);}
        return j;
    }
    private static void slice(JsonObject j,String key,int page,int size){var source=map(j,key);j.addProperty(key+"Count",source.size());var limited=new JsonObject();source.entrySet().stream().skip((long)page*size).limit(size).forEach(e->limited.add(e.getKey(),e.getValue()));j.add(key,limited);}
    public JsonObject request(Actor a,JsonObject input)throws Exception{return database.transaction(()->{
        String op=text(input,"op",30),section=text(input,"section",30);require(enabled(section));
        if(section.equals("groups")&&!Set.of("list","detail").contains(op))database.lock("community:groups");
        if(op.equals("create"))database.lock("community:create:"+section);
        JsonObject result=execute(a,input,op,section);if(!Set.of("list","detail","read").contains(op))audit(a.name,"community "+op,section+" "+value(input,"id"));return result;
    });}
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
            }if(Set.of("board","events").contains(section)&&!value(in,"group").isEmpty()){var group=get(value(in,"group"));require(str(group,"section").equals("groups")&&contains(group,"members",a.id));j.addProperty("group",str(group,"id"));j.addProperty("groupName",str(group,"title"));}put(j);
        }else if(!op.equals("list")&&!op.equals("detail")){
            var j=get(id);require(section.equals(str(j,"section")));require(!str(j,"status").equals("hidden")||a.admin);String title=str(j,"title");boolean own=owner(j,a);
            if(op.equals("hide")){require(a.admin);String reason=text(in,"text",500);j.addProperty("status","hidden");j.addProperty("moderationReason",reason);note(str(j,"owner"),section,id,"Скрыто: "+title+" · "+reason);}
            else if(section.equals("board")){
                switch(op){
                    case "respond" -> {require(!own&&str(j,"status").equals("open")&&time(j,"endsAt")>now);require(!contains(j,"responses",a.id));boundedMap(j,"responses",a.id);var reply=new JsonObject();reply.addProperty("name",a.name);reply.addProperty("text",text(in,"text",1000));reply.addProperty("status","pending");reply.addProperty("answer","");map(j,"responses").add(a.id,reply);note(str(j,"owner"),section,id,"Новый отклик: "+title);}
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
                    case "application" -> {require(manager(j,a)&&contains(j,"applications",target));if(in.get("accept").getAsBoolean())addMember(j,target);map(j,"applications").remove(target);note(target,section,id,"Рассмотрена заявка: "+title);}
                    case "invite" -> {require(manager(j,a)&&!members.has(target));UUID.fromString(target);boundedMap(j,"invitations",target);require(!contains(j,"invitations",target));map(j,"invitations").addProperty(target,true);note(target,section,id,"Приглашение: "+title);}
                    case "invitation" -> {require(contains(j,"invitations",a.id));if(in.get("accept").getAsBoolean())addMember(j,a.id);map(j,"invitations").remove(a.id);}
                    case "leave" -> {require(members.has(a.id)&&!own);members.remove(a.id);}
                    case "role" -> {require(own&&members.has(target)&&!target.equals(a.id));String role=text(in,"role",20);require(Set.of("member","assistant","leader").contains(role));members.addProperty(target,role);if(role.equals("leader")){members.addProperty(a.id,"member");j.addProperty("owner",target);}note(target,section,id,"Изменена роль: "+title);}
                    case "recruiting" -> {require(manager(j,a));j.addProperty("recruiting",!j.get("recruiting").getAsBoolean());}
                    default -> throw new IllegalArgumentException("Неизвестное действие");
                }
            }else throw new IllegalArgumentException("Неизвестное действие");
            put(j);
        }
        var out=new JsonObject();out.addProperty("kind","community");out.addProperty("section",section);out.addProperty("request",value(in,"request"));out.add("config",config.deepCopy());out.addProperty("unread",unread(a.id));out.add("preferences",preferences(a.id));var ownGroups=new JsonArray();for(var group:memberGroups(a.id)){var option=new JsonObject();option.addProperty("value",str(group,"id"));option.addProperty("label",str(group,"title"));ownGroups.add(option);}out.add("groups",ownGroups);out.addProperty("canCreate",a.admin||section.equals("events")&&a.events||Set.of("board","groups","ideas").contains(section));
        if(!id.isEmpty()&&!section.equals("notifications")){var j=get(id);require(section.equals(str(j,"section")));require(!str(j,"status").equals("hidden")||owner(j,a)||a.admin);int detailPage=in.has("page")?number(in,"page",0,1000):0;out.add("detail",publicView(j,a,detailPage));var names=new JsonObject();if(section.equals("groups")){try(var q=connection().prepareStatement("SELECT id,name FROM people WHERE id=ANY(?)")){q.setArray(1,connection().createArrayOf("text",map(j,"members").keySet().toArray()));try(var r=q.executeQuery()){while(r.next())names.addProperty(r.getString(1),r.getString(2));}}}out.add("names",names);if(section.equals("groups")){var related=new JsonArray();for(String kind:List.of("board","events")){if(!enabled(kind))continue;for(var child:related(kind,id)){var row=new JsonObject();for(String key:List.of("id","section","title"))row.add(key,child.get(key));related.add(row);if(related.size()>=20)break;}if(related.size()>=20)break;}out.add("related",related);}return out;}
        int page=in.has("page")?number(in,"page",0,1000):0;out.addProperty("page",page);var entries=new JsonArray();
        if(section.equals("notifications")){try(var s=connection().prepareStatement("SELECT id,body,read FROM notices WHERE recipient=? ORDER BY id DESC LIMIT 10 OFFSET ?")){s.setString(1,a.id);s.setInt(2,page*10);try(var rows=s.executeQuery()){while(rows.next()){var j=JsonParser.parseString(rows.getString(2)).getAsJsonObject();j.addProperty("id",rows.getString(1));j.addProperty("read",rows.getBoolean(3));entries.add(j);}}}}
        else entries=listPage(a,in,section,page,now);
        out.add("entries",entries);return out;
    }
    private boolean manager(JsonObject j,Actor a){return a.admin||owner(j,a)||contains(j,"members",a.id)&&map(j,"members").get(a.id).getAsString().equals("assistant");}
    private int memberships(String id)throws Exception{try(var q=connection().prepareStatement("SELECT count(*) FROM documents WHERE section='groups' AND body->>'status'='open' AND jsonb_exists(body->'members',?)")){q.setString(1,id);try(var r=q.executeQuery()){r.next();return r.getInt(1);}}}
    private void addMember(JsonObject j,String id)throws Exception{require(memberships(id)<config.get("maxMemberships").getAsInt());boundedMap(j,"members",id);map(j,"members").addProperty(id,"member");}
    public void reminders(long now)throws Exception{database.transaction(()->{if(lastReminderSweep!=0&&now-lastReminderSweep<30000)return null;try{for(var j:due("events",now)){long start=time(j,"startsAt");if(!str(j,"status").equals("open")||start<now||start-now>300000)continue;boolean changed=false;for(String target:map(j,"participants").keySet())if(!contains(j,"reminded",target)){note(target,"events",str(j,"id"),"Скоро событие: "+str(j,"title"));map(j,"reminded").addProperty(target,true);changed=true;}if(changed)put(j);}for(var j:due("polls",now)){if(time(j,"endsAt")<=now&&!j.has("notified")){for(String target:map(j,"votes").keySet())note(target,"polls",str(j,"id"),"Голосование завершено: "+str(j,"title"));j.addProperty("notified",true);put(j);}}lastReminderSweep=now;}catch(Exception ex){lastReminderSweep=0;throw ex;}return null;});}
    public <T>T inTransaction(PgDatabase.Work<T> work)throws Exception{return database.transaction(work);}
    public interface Transaction {void run()throws Exception;}
    public void transaction(Transaction work)throws Exception{database.transaction(()->{work.run();return null;});}
    public JsonObject record(String namespace,String id)throws Exception{return database.transaction(()->{try(var q=connection().prepareStatement("SELECT body FROM records WHERE namespace=? AND id=? FOR UPDATE")){q.setString(1,namespace);q.setString(2,id);try(var r=q.executeQuery()){return r.next()?JsonParser.parseString(r.getString(1)).getAsJsonObject():null;}}});}
    public void record(String namespace,String id,JsonObject body)throws Exception{database.transaction(()->{try(var q=connection().prepareStatement("INSERT INTO records(namespace,id,body) VALUES(?,?,?::jsonb) ON CONFLICT(namespace,id) DO UPDATE SET body=excluded.body")){q.setString(1,namespace);q.setString(2,id);q.setString(3,Json.GSON.toJson(body));q.executeUpdate();}return null;});}
    public List<JsonObject> records(String namespace)throws Exception{return database.transaction(()->{var out=new ArrayList<JsonObject>();try(var q=connection().prepareStatement("SELECT body FROM records WHERE namespace=? ORDER BY sequence DESC")){q.setString(1,namespace);try(var r=q.executeQuery()){while(r.next())out.add(JsonParser.parseString(r.getString(1)).getAsJsonObject());}}return out;});}
    public void deleteRecord(String namespace,String id)throws Exception{database.transaction(()->{try(var q=connection().prepareStatement("DELETE FROM records WHERE namespace=? AND id=?")){q.setString(1,namespace);q.setString(2,id);q.executeUpdate();}return null;});}
    public void audit(String actor,String action,String detail)throws Exception{database.transaction(()->{var j=new JsonObject();String id=UUID.randomUUID().toString();j.addProperty("id",id);j.addProperty("at",System.currentTimeMillis());j.addProperty("actor",actor);j.addProperty("action",action);j.addProperty("detail",detail.substring(0,Math.min(1000,detail.length())));record("audit",id,j);try(var q=connection().createStatement()){q.executeUpdate("DELETE FROM records WHERE namespace='audit' AND sequence NOT IN (SELECT sequence FROM records WHERE namespace='audit' ORDER BY sequence DESC LIMIT 1000)");}return null;});}

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
        return bodies("SELECT body FROM documents WHERE section=? AND body->>'group'=? AND body->>'status'<>'hidden' ORDER BY sequence DESC LIMIT 20",section,group);
    }
    private List<JsonObject> due(String section,long now)throws Exception{
        String predicate=section.equals("events")?"body->>'status'='open' AND (body->>'startsAt')::bigint BETWEEN ? AND ?":"(body->>'endsAt')::bigint<=? AND NOT jsonb_exists(body,'notified')";
        return section.equals("events")?bodies("SELECT body FROM documents WHERE section='events' AND "+predicate+" ORDER BY sequence FOR UPDATE SKIP LOCKED",now,now+300000):bodies("SELECT body FROM documents WHERE section='polls' AND "+predicate+" ORDER BY sequence FOR UPDATE SKIP LOCKED",now);
    }
    private List<JsonObject> bodies(String sql,Object...args)throws Exception{
        var out=new ArrayList<JsonObject>();try(var q=connection().prepareStatement(sql)){for(int i=0;i<args.length;i++)q.setObject(i+1,args[i]);try(var r=q.executeQuery()){while(r.next())out.add(JsonParser.parseString(r.getString(1)).getAsJsonObject());}}return out;
    }
    private JsonArray listPage(Actor a,JsonObject in,String section,int page,long now)throws Exception{
        var sources=new ArrayList<String>();for(String source:section.equals("home")?List.of("board","groups","events","polls","ideas"):List.of(section))if(enabled(source))sources.add(source);
        String sql="SELECT body FROM documents WHERE section=ANY(?) AND (body->>'status'<>'hidden' OR ? OR body->>'owner'=?) AND (NOT ? OR body->>'owner'=?) AND (?='' OR section<>'groups' OR jsonb_exists(body->'members',?)) AND strpos(lower(body->>'title'),lower(?))>0";
        if(section.equals("home"))sql+=" AND (body->>'owner'=? OR section='board' AND jsonb_exists(body->'responses',?) OR section='groups' AND (jsonb_exists(body->'members',?) OR jsonb_exists(body->'applications',?) OR jsonb_exists(body->'invitations',?)) OR section='events' AND jsonb_exists(body->'participants',?) AND (body->>'startsAt')::bigint>? OR section='polls' AND (body->>'endsAt')::bigint>? AND NOT jsonb_exists(body->'votes',?))";
        sql+=section.equals("events")?" ORDER BY (body->>'startsAt')::bigint ASC, sequence DESC LIMIT 10 OFFSET ?":" ORDER BY sequence DESC LIMIT 10 OFFSET ?";var out=new JsonArray();
        try(var q=connection().prepareStatement(sql)){int i=1;q.setArray(i++,connection().createArrayOf("text",sources.toArray()));q.setBoolean(i++,a.admin);q.setString(i++,a.id);q.setBoolean(i++,in.has("mine")&&in.get("mine").getAsBoolean());q.setString(i++,a.id);q.setString(i++,value(in,"member"));q.setString(i++,value(in,"member"));q.setString(i++,value(in,"query"));if(section.equals("home")){for(int n=0;n<6;n++)q.setString(i++,a.id);q.setLong(i++,now);q.setLong(i++,now);q.setString(i++,a.id);}q.setInt(i,page*10);
            try(var r=q.executeQuery()){while(r.next()){var document=JsonParser.parseString(r.getString(1)).getAsJsonObject();out.add(CommunityPreview.of(document));}}
        }return out;
    }
    public record NoticeSummary(int unread,long sequence){}
    public Map<UUID,NoticeSummary> notificationSummary(Collection<UUID> players)throws Exception{return database.transaction(()->{
        var result=new HashMap<UUID,NoticeSummary>();if(players.isEmpty())return result;
        String sql="SELECT n.recipient,count(*),coalesce(max(n.id) FILTER (WHERE NOT EXISTS (SELECT 1 FROM jsonb_array_elements_text(coalesce(p.body->'muted','[]'::jsonb)) AS muted(section) WHERE muted.section=n.body->>'section')),0) FROM notices n LEFT JOIN preferences p ON p.id=n.recipient WHERE n.read=0 AND n.recipient=ANY(?) GROUP BY n.recipient";
        try(var q=connection().prepareStatement(sql)){q.setArray(1,connection().createArrayOf("text",players.stream().map(UUID::toString).toArray()));try(var r=q.executeQuery()){while(r.next())result.put(UUID.fromString(r.getString(1)),new NoticeSummary(r.getInt(2),r.getLong(3)));}}for(UUID id:players)result.putIfAbsent(id,new NoticeSummary(0,0));return result;
    });}
    public List<JsonObject> recordPage(String namespace,String owner,int page,int size)throws Exception{return database.transaction(()->{
        if(page<0||page>1000||size<1||size>20)throw new IllegalArgumentException("Invalid page");return bodies("SELECT body FROM records WHERE namespace=? AND (?='' OR body->>'uuid'=?) ORDER BY sequence DESC LIMIT ? OFFSET ?",namespace,owner,owner,size,page*size);
    });}
    public void checkReport(String owner,long now)throws Exception{database.lock("report-create");try(var q=connection().prepareStatement("SELECT count(*),count(*) FILTER (WHERE body->>'uuid'=? AND (body->>'createdAt')::bigint>?) FROM records WHERE namespace='reports'")){q.setString(1,owner);q.setLong(2,now-60000);try(var r=q.executeQuery()){r.next();if(r.getInt(1)>=5000)throw new IllegalArgumentException("Хранилище обращений заполнено");if(r.getInt(2)>0)throw new IllegalArgumentException("Подождите минуту перед следующим обращением");}}}
    public void pruneReports(long now)throws Exception{database.transaction(()->{try(var q=connection().prepareStatement("DELETE FROM records WHERE namespace='reports' AND coalesce((body->>'repliedAt')::bigint,(body->>'createdAt')::bigint)<?")){q.setLong(1,now-30L*86400000);q.executeUpdate();}return null;});}

    @Override public void close()throws Exception{}
}
