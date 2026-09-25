package dev.abros.anthub.core;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@Tag("postgres")
class CommunityPlusTest {
 @TempDir Path temp;CommunityStore store;PgDatabase db;
 CommunityStore.Actor owner=actor("Owner",true),member=actor("Member",false),outsider=actor("Outside",false);
 static CommunityStore.Actor actor(String name,boolean admin){return new CommunityStore.Actor(UUID.randomUUID().toString(),name,admin,admin);}
 @BeforeEach void start()throws Exception{db=TestDatabase.database(temp);store=new CommunityStore(db,CommunityStore.defaults());for(var a:List.of(owner,member,outsider))store.seen(a);}
 JsonObject input(String section,String op,String id){var q=new JsonObject();q.addProperty("section",section);q.addProperty("op",op);q.addProperty("id",id);return q;}
 JsonObject create(String section){var q=input(section,"create","");q.addProperty("title","Example");q.addProperty("description","Description");q.addProperty("type","Команда");q.addProperty("days",7);q.addProperty("capacity",1);q.addProperty("startsAt",Instant.now().plusSeconds(7200).toString());return q;}
 String group()throws Exception{return Json.str(store.request(owner,create("groups")).getAsJsonObject("detail"),"id");}
 void join(String id)throws Exception{var q=input("groups","invite",id);q.addProperty("target",member.id());store.request(owner,q);q=input("groups","invitation",id);q.addProperty("accept",true);store.request(member,q);}
 JsonObject detail(CommunityStore.Actor actor,String section,String id)throws Exception{return store.request(actor,input(section,"detail",id)).getAsJsonObject("detail");}
 @Test void privateEventIsFilteredFromListsDetailsSharingAndJoining()throws Exception{
  String group=group();join(group);var q=create("events");q.addProperty("group",group);q.addProperty("visibility","group");String id=Json.str(store.request(owner,q).getAsJsonObject("detail"),"id");
  assertEquals(0,store.request(outsider,input("events","list","")).getAsJsonArray("entries").size());assertEquals(1,store.request(member,input("events","list","")).getAsJsonArray("entries").size());
  assertThrows(CommunityFailure.class,()->detail(outsider,"events",id));assertThrows(CommunityFailure.class,()->store.sharedEntry(outsider,"events",id));assertThrows(CommunityFailure.class,()->store.request(outsider,input("events","join",id)));
  store.request(member,input("events","join",id));store.request(member,input("groups","leave",group));assertThrows(CommunityFailure.class,()->detail(member,"events",id));
 }
 @Test void repeatsHaveIndependentParticipantsAndSingleCancellation()throws Exception{
  var q=create("events");q.addProperty("repeat","weekly");q.addProperty("occurrences",3);q.addProperty("timezone","Europe/Warsaw");q.addProperty("startsAt","2027-03-21T15:00:00Z");store.request(owner,q);
  var entries=store.request(member,input("events","list","")).getAsJsonArray("entries");assertEquals(3,entries.size());String first=Json.str(entries.get(0).getAsJsonObject(),"id"),second=Json.str(entries.get(1).getAsJsonObject(),"id");
  store.request(member,input("events","join",first));assertTrue(detail(member,"events",second).getAsJsonObject("participants").isEmpty());store.request(owner,input("events","cancel",first));assertEquals("open",Json.str(detail(owner,"events",second),"status"));
  long next=entries.get(1).getAsJsonObject().get("startsAt").getAsLong();assertEquals(16,Instant.ofEpochMilli(next).atZone(ZoneId.of("Europe/Warsaw")).getHour());
 }
 @Test void ignoreDoesNotSuppressSystemNoticesAndProfileWritesAreOwnOnly()throws Exception{
  var ignore=input("home","plusIgnore","");ignore.addProperty("target",owner.name());store.request(member,ignore);String group=group();var invite=input("groups","invite",group);invite.addProperty("target",member.id());assertThrows(CommunityFailure.class,()->store.request(owner,invite));store.externalNotice(member.id(),"System");assertEquals(1,store.unread(member.id()));
  var profile=input("home","plusProfileSave",owner.id());profile.addProperty("about","Hello");assertThrows(CommunityFailure.class,()->store.request(member,profile));profile.addProperty("id",member.id());store.request(member,profile);assertEquals("Hello",Json.str(store.request(owner,input("home","plusProfile",member.id())).getAsJsonObject("profile"),"about"));
 }
 @Test void tasksArePrivateAndAssigneeCanChangeOnlyTheirStatus()throws Exception{
  String group=group();join(group);var create=input("groups","plusItemSave",group);create.addProperty("kind","task");create.addProperty("title","Build road");create.addProperty("assignee",member.name());var detail=store.request(owner,create).getAsJsonObject("detail");var task=detail.getAsJsonArray("groupItems").get(0).getAsJsonObject();
  assertTrue(detail(outsider,"groups",group).getAsJsonArray("groupItems").isEmpty());var status=input("groups","plusTaskStatus",group);status.addProperty("itemId",Json.str(task,"id"));status.add("itemRevision",task.get("revision"));status.addProperty("status","done");assertThrows(CommunityFailure.class,()->store.request(outsider,status));store.request(member,status);
  assertEquals(CommunityFailure.Code.CONFLICT,assertThrows(CommunityFailure.class,()->store.request(member,status)).code());
  var home=store.request(member,input("home","list",""));assertTrue(home.getAsJsonArray("tasks").isEmpty());
 }
 @Test void groupRemovalProtectsLeaderAndNormalizedMembersSurviveEdits()throws Exception{
  String group=group();join(group);var remove=input("groups","plusRemoveMember",group);remove.addProperty("target",owner.id());remove.addProperty("reason","Reason");assertThrows(CommunityFailure.class,()->store.request(owner,remove));remove.addProperty("target",member.id());assertThrows(CommunityFailure.class,()->store.request(member,remove));store.request(owner,remove);assertFalse(detail(owner,"groups",group).getAsJsonObject("members").has(member.id()));
  db.transaction(()->{try(var q=db.connection().createStatement();var rows=q.executeQuery("SELECT body ? 'members' FROM documents WHERE section='groups'")){assertTrue(rows.next());assertFalse(rows.getBoolean(1));}return null;});
 }
 @Test void retryKeepsTaskIdentityAndResponseMetadata()throws Exception{
  String group=group();var q=input("groups","plusItemSave",group);q.addProperty("kind","task");q.addProperty("title","Once");q.addProperty("operationId",UUID.randomUUID().toString());q.addProperty("issuedAt",System.currentTimeMillis());var first=store.request(owner,q);var second=store.request(owner,q);assertTrue(second.get("replayed").getAsBoolean());assertTrue(second.has("config"));assertEquals(first.getAsJsonObject("detail").getAsJsonArray("groupItems"),second.getAsJsonObject("detail").getAsJsonArray("groupItems"));
 }
 @Test void existingJsonCollectionsMigrateWithoutLosingData()throws Exception{
  class Rollback extends RuntimeException{}
  assertThrows(Rollback.class,()->db.transaction(()->{
   try(var q=db.connection().createStatement()){q.execute("DROP VIEW community_documents");q.execute("DROP TABLE community_relations,community_profile,community_ignores,community_event_preferences,community_group_items");q.execute("DELETE FROM schema_versions WHERE version=4");}
   var old=new JsonObject();old.addProperty("id","old-record");old.addProperty("section","events");var participants=new JsonObject();participants.addProperty(member.id(),member.name());participants.addProperty(owner.id(),owner.name());old.add("participants",participants);var order=new JsonArray();order.add(owner.id());order.add(member.id());old.add("participantOrder",order);var votes=new JsonObject();var choices=new JsonArray();choices.add(1);votes.add(member.id(),choices);old.add("votes",votes);
   try(var q=db.connection().prepareStatement("INSERT INTO documents(id,section,body) VALUES('old-record','events',?::jsonb)")){q.setString(1,old.toString());q.executeUpdate();}DatabaseMigrations.apply(db);
   var restored=store.get("old-record");assertEquals(List.of(owner.id(),member.id()),new ArrayList<>(restored.getAsJsonObject("participants").keySet()));assertEquals(votes,restored.getAsJsonObject("votes"));throw new Rollback();
  }));
 }
 @Test void subscribedEventAppearsOnHomeAndReminderCanBeDisabled()throws Exception{
  var q=create("events");String id=Json.str(store.request(owner,q).getAsJsonObject("detail"),"id");assertTrue(store.request(member,input("home","list","")).getAsJsonArray("entries").isEmpty());
  var reminder=input("events","plusReminder",id);reminder.addProperty("minutes",15);store.request(member,reminder);assertEquals(1,store.request(member,input("home","list","")).getAsJsonArray("entries").size());long start=detail(member,"events",id).get("startsAt").getAsLong();store.reminders(start-14*60000);assertEquals(1,store.unread(member.id()));store.reminders(start-13*60000);assertEquals(1,store.unread(member.id()));reminder.addProperty("minutes",-1);store.request(member,reminder);assertTrue(store.request(member,input("home","list","")).getAsJsonArray("entries").isEmpty());
 }
 @Test void manyGroupTasksUseBoundedPagesAndCursorDetectsEdits()throws Exception{
  String group=group();for(int i=0;i<12;i++){var q=input("groups","plusItemSave",group);q.addProperty("kind","task");q.addProperty("title","Task "+i);q.addProperty("description","x".repeat(1000));store.request(owner,q);}
  var q=input("groups","detail",group);var first=store.request(owner,q);assertEquals(5,first.getAsJsonObject("detail").getAsJsonArray("groupItems").size());assertTrue(Json.GSON.toJson(first).length()<32767);q.add("cursor",first.get("nextCursor"));assertEquals(5,store.request(owner,q).getAsJsonObject("detail").getAsJsonArray("groupItems").size());var task=first.getAsJsonObject("detail").getAsJsonArray("groupItems").get(0).getAsJsonObject();var update=input("groups","plusTaskStatus",group);update.addProperty("itemId",Json.str(task,"id"));update.add("itemRevision",task.get("revision"));update.addProperty("status","done");store.request(owner,update);assertEquals(CommunityFailure.Code.CONFLICT,assertThrows(CommunityFailure.class,()->store.request(owner,q)).code());
 }
 @Test void structuredOfferAcceptsNumericQuantity()throws Exception{
  var q=create("board");q.addProperty("trade","sell");q.addProperty("item","Камень");q.addProperty("quantity",64);q.addProperty("terms","1 алмаз");var post=store.request(owner,q).getAsJsonObject("detail");assertEquals(64,post.getAsJsonObject("trade").get("quantity").getAsInt());
 }
}
