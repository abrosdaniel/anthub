package dev.abros.anthub.core;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@org.junit.jupiter.api.Tag("postgres")
class CommunityStoreTest {
 @TempDir Path world;CommunityStore db;
 CommunityStore.Actor owner=actor("Owner",false),alice=actor("Alice",false),bob=actor("Bob",false),admin=actor("Admin",true);
 static CommunityStore.Actor actor(String name,boolean admin){return new CommunityStore.Actor(UUID.randomUUID().toString(),name,admin,admin);}
 @BeforeEach void start()throws Exception{db=new CommunityStore(TestDatabase.database(world),CommunityStore.defaults());for(var a:List.of(owner,alice,bob,admin))db.seen(a);}
 @AfterEach void close()throws Exception{db.close();}
 JsonObject input(String section,String op,String id){var j=new JsonObject();j.addProperty("section",section);j.addProperty("op",op);j.addProperty("id",id);return j;}
 JsonObject create(String section)throws Exception{var j=input(section,"create","");j.addProperty("title","Example");j.addProperty("description","Description");j.addProperty("days",14);j.addProperty("type","Команда");j.addProperty("startsAt",Instant.now().plusSeconds(120).toString());j.addProperty("endsAt",Instant.now().plusSeconds(60).toString());j.addProperty("capacity",1);var options=new JsonArray();options.add("One");options.add("Two");j.add("options",options);return db.request(Set.of("events","polls").contains(section)?admin:owner,j).getAsJsonObject("detail");}
 String id(JsonObject j){return Json.str(j,"id");}
 JsonObject detail(String section,String id,CommunityStore.Actor a)throws Exception{return db.request(a,input(section,"detail",id)).getAsJsonObject("detail");}
 @Test void groupRefreshWithoutRevisionCanBeUpdatedAndRejectsStaleChanges()throws Exception{
  var group=create("groups");String groupId=id(group);var database=TestDatabase.database(world);
  database.transaction(()->{try(var q=database.connection().prepareStatement("UPDATE documents SET body=body-'revision' WHERE id=?")){q.setString(1,groupId);q.executeUpdate();}return null;});
  var read=input("groups","detail",groupId);read.addProperty("action","community");read.addProperty("cursor","");
  var refreshed=db.request(owner,read).getAsJsonObject("detail");assertEquals(0,refreshed.get("revision").getAsLong());
  assertTrue(refreshed.getAsJsonObject("members").has(owner.id()));
  var update=command(input("groups","recruiting",groupId));update.add("revision",refreshed.get("revision"));
  var changed=db.request(owner,update).getAsJsonObject("detail");assertEquals(1,changed.get("revision").getAsLong());assertFalse(changed.get("recruiting").getAsBoolean());
  var stale=command(input("groups","recruiting",groupId));stale.addProperty("revision",0);
  assertEquals(CommunityFailure.Code.CONFLICT,assertThrows(CommunityFailure.class,()->db.request(owner,stale)).code());
  assertEquals(1,detail("groups",groupId,owner).get("revision").getAsLong());
 }
 @Test void groupRefreshWithNullRevisionReturnsInitialRevision()throws Exception{
  var group=create("groups");var database=TestDatabase.database(world);
  database.transaction(()->{try(var q=database.connection().prepareStatement("UPDATE documents SET body=jsonb_set(body,'{revision}','null'::jsonb) WHERE id=?")){q.setString(1,id(group));q.executeUpdate();}return null;});
  assertEquals(0,detail("groups",id(group),owner).get("revision").getAsLong());
 }
 JsonObject mutate(String section,String op,JsonObject item){var q=command(input(section,op,id(item)));q.add("revision",item.get("revision"));return q;}
 @Test void editingChecksOwnershipAndRevisionAndKeepsParticipants()throws Exception{
  for(String section:List.of("board","groups","events","polls","ideas")){
   var item=create(section);var author=Set.of("events","polls").contains(section)?admin:owner;
   var edit=mutate(section,"edit",item);edit.addProperty("title","Changed");edit.addProperty("description","New description");edit.addProperty("type","Город");
   assertThrows(CommunityFailure.class,()->db.request(alice,edit));
   var result=db.request(author,edit).getAsJsonObject("detail");assertEquals("Changed",Json.str(result,"title"));assertEquals(2,result.get("revision").getAsLong());
   assertEquals(CommunityFailure.Code.CONFLICT,assertThrows(CommunityFailure.class,()->db.request(author,command(edit.deepCopy()))).code());
   if(section.equals("groups")){assertTrue(result.getAsJsonObject("members").has(owner.id()));assertEquals("Город",Json.str(result,"type"));}
  }
 }
 @Test void pollCannotBeEditedAfterFirstVote()throws Exception{
  var poll=create("polls");var vote=input("polls","vote",id(poll));var choices=new JsonArray();choices.add(0);vote.add("choices",choices);db.request(alice,vote);
  var current=detail("polls",id(poll),admin);assertFalse(current.getAsJsonArray("actions").contains(new JsonPrimitive("edit")));
  var edit=mutate("polls","edit",current);edit.addProperty("title","Different question");edit.addProperty("description","Different meaning");assertThrows(CommunityFailure.class,()->db.request(admin,edit));
 }
 @Test void acceptedReplyRequiresConfirmationAndCanOnlyBeWithdrawnByItsAuthor()throws Exception{
  var post=create("board");var reply=input("board","respond",id(post));reply.addProperty("text","I can help");db.request(alice,reply);
  var accept=input("board","respondDecision",id(post));accept.addProperty("target",alice.id());accept.addProperty("decision","accepted");accept.addProperty("text","Come along");db.request(owner,accept);
  var withdraw=mutate("board","withdrawResponse",detail("board",id(post),alice));withdraw.addProperty("target",alice.id());
  assertThrows(CommunityFailure.class,()->db.request(bob,withdraw));assertEquals(CommunityFailure.Code.INVALID,assertThrows(CommunityFailure.class,()->db.request(alice,withdraw)).code());
  withdraw.addProperty("confirmAccepted",true);var result=db.request(alice,withdraw);assertFalse(result.getAsJsonObject("detail").get("hasResponded").getAsBoolean());int notices=db.unread(owner.id());
  db.request(alice,withdraw);assertEquals(notices,db.unread(owner.id()));assertTrue(detail("board",id(post),owner).getAsJsonObject("responses").isEmpty());
 }
 @Test void applicationsAndInvitationsCanBeWithdrawnWithPrivateQueues()throws Exception{
  var group=create("groups");var apply=input("groups","apply",id(group));apply.addProperty("text","Hello");db.request(alice,apply);
  var withdraw=mutate("groups","withdrawApplication",detail("groups",id(group),alice));assertThrows(CommunityFailure.class,()->db.request(bob,withdraw));db.request(alice,withdraw);
  assertTrue(detail("groups",id(group),owner).getAsJsonObject("applications").isEmpty());
  var invite=input("groups","invite",id(group));invite.addProperty("target",alice.id());db.request(owner,invite);
  assertTrue(detail("groups",id(group),owner).getAsJsonObject("invitations").has(alice.id()));assertTrue(detail("groups",id(group),bob).getAsJsonObject("invitations").isEmpty());
  var revoke=mutate("groups","revokeInvitation",detail("groups",id(group),owner));revoke.addProperty("target",alice.id());assertThrows(CommunityFailure.class,()->db.request(bob,revoke));db.request(owner,revoke);
  var accept=input("groups","invitation",id(group));accept.addProperty("accept",true);assertThrows(CommunityFailure.class,()->db.request(alice,accept));
 }
 @Test void assistantCannotEditDeleteOrTransferOwnership()throws Exception{
  var group=create("groups");var invite=input("groups","invite",id(group));invite.addProperty("target",alice.id());db.request(owner,invite);var accept=input("groups","invitation",id(group));accept.addProperty("accept",true);db.request(alice,accept);
  var role=input("groups","role",id(group));role.addProperty("target",alice.id());role.addProperty("role","assistant");db.request(owner,role);
  var current=detail("groups",id(group),alice);for(String op:List.of("edit","delete","role"))assertThrows(CommunityFailure.class,()->db.request(alice,mutate("groups",op,current)));
  assertTrue(current.getAsJsonArray("actions").contains(new JsonPrimitive("invite")));
 }
 @Test void participationFiltersAndHomeShowRelevantEntries()throws Exception{
  var group=create("groups");var post=create("board");create("ideas");
  assertTrue(db.request(owner,input("home","list","")).getAsJsonArray("entries").isEmpty());
  var apply=input("groups","apply",id(group));apply.addProperty("text","Hello");db.request(alice,apply);
  var ownFeed=db.request(owner,input("home","list","")).getAsJsonArray("entries");assertTrue(ownFeed.isEmpty());
  var filter=input("groups","list","");filter.addProperty("participating",true);assertEquals(1,db.request(alice,filter).getAsJsonArray("entries").size());assertTrue(db.request(bob,filter).getAsJsonArray("entries").isEmpty());
  var reply=input("board","respond",id(post));reply.addProperty("text","Help");db.request(alice,reply);assertTrue(db.request(owner,input("home","list","")).getAsJsonArray("entries").isEmpty());
 }
 @Test void boardHasNoCategoriesAndManyPrivateResponses()throws Exception{assertTrue(db.configuration().getAsJsonArray("categories").isEmpty());var post=create("board");for(var a:List.of(alice,bob)){var reply=input("board","respond",id(post));reply.addProperty("text","Private "+a.name());db.request(a,reply);}assertEquals(2,detail("board",id(post),owner).getAsJsonObject("responses").size());var view=detail("board",id(post),alice).getAsJsonObject("responses");assertEquals(1,view.size());assertFalse(view.has(bob.id()));for(var a:List.of(alice,bob)){var accept=input("board","respondDecision",id(post));accept.addProperty("target",a.id());accept.addProperty("decision","accepted");accept.addProperty("text","Welcome");db.request(owner,accept);assertEquals("accepted",Json.str(detail("board",id(post),a).getAsJsonObject("responses").getAsJsonObject(a.id()),"status"));assertEquals(1,db.unread(a.id()));}}
 @Test void unauthorizedChangesRollBack()throws Exception{var p=create("board");assertThrows(IllegalArgumentException.class,()->db.request(alice,input("board","close",id(p))));assertEquals("open",Json.str(detail("board",id(p),owner),"status"));assertThrows(IllegalArgumentException.class,()->db.request(alice,input("ideas","detail",id(p))));assertEquals(0,db.unread(owner.id()));}
 @Test void duplicateResponseDoesNotDuplicateNotice()throws Exception{var p=create("board");var q=input("board","respond",id(p));q.addProperty("text","Hello");db.request(alice,q);assertThrows(IllegalArgumentException.class,()->db.request(alice,q));assertEquals(1,db.unread(owner.id()));}
 @Test void disablingASectionBlocksRequests()throws Exception{var p=create("board");var c=db.configuration();c.getAsJsonArray("sections").remove(new JsonPrimitive("board"));db.configure(c);assertThrows(IllegalArgumentException.class,()->detail("board",id(p),owner));}
 @Test void pollVotesArePrivateAndUnique()throws Exception{var p=create("polls");var q=input("polls","vote",id(p));var choices=new JsonArray();choices.add(0);q.add("choices",choices);var view=db.request(alice,q).getAsJsonObject("detail");assertFalse(view.has("votes"));assertEquals(-1,view.getAsJsonArray("counts").get(0).getAsInt());assertThrows(IllegalArgumentException.class,()->db.request(alice,q));assertFalse(detail("polls",id(p),bob).get("voted").getAsBoolean());}
 @Test void waitlistPromotionAndReminderArePersistent()throws Exception{var p=create("events");db.request(alice,input("events","join",id(p)));db.request(bob,input("events","join",id(p)));db.request(alice,input("events","leave",id(p)));assertEquals(1,db.unread(bob.id()));db.reminders(System.currentTimeMillis());int n=db.unread(bob.id());db.reminders(System.currentTimeMillis());assertEquals(n,db.unread(bob.id()));db.close();db=new CommunityStore(TestDatabase.database(world),CommunityStore.defaults());assertTrue(detail("events",id(p),bob).get("isParticipant").getAsBoolean());}
 @Test void groupMembershipRequiresAcceptanceAndLeaderCannotLeave()throws Exception{var p=create("groups");var q=input("groups","apply",id(p));q.addProperty("text","Join please");db.request(alice,q);assertFalse(detail("groups",id(p),alice).get("isMember").getAsBoolean());var accept=input("groups","application",id(p));accept.addProperty("target",alice.id());accept.addProperty("accept",true);assertThrows(IllegalArgumentException.class,()->db.request(bob,accept));db.request(owner,accept);assertTrue(detail("groups",id(p),alice).get("isMember").getAsBoolean());assertThrows(IllegalArgumentException.class,()->db.request(owner,input("groups","leave",id(p))));}
 @Test void nonAdminCannotCreatePollsOrEvents()throws Exception{for(String section:List.of("polls","events")){var q=input(section,"create","");q.addProperty("title","Bad");q.addProperty("description","Bad");assertThrows(IllegalArgumentException.class,()->db.request(alice,q));}}
 @Test void proposalStatusRequiresAdminAndNotifiesAuthor()throws Exception{var p=create("ideas");var q=input("ideas","status",id(p));q.addProperty("status","planned");q.addProperty("text","Next month");assertThrows(IllegalArgumentException.class,()->db.request(alice,q));db.request(admin,q);assertEquals(1,db.unread(owner.id()));assertEquals("planned",Json.str(detail("ideas",id(p),owner),"status"));}
 @Test void notificationsBelongToRecipient()throws Exception{db.externalNotice(alice.id(),"Private");assertEquals(1,db.request(alice,input("notifications","list","")).getAsJsonArray("entries").size());assertTrue(db.request(bob,input("notifications","list","")).getAsJsonArray("entries").isEmpty());db.request(bob,input("notifications","read",""));assertEquals(1,db.unread(alice.id()));db.request(alice,input("notifications","read",""));assertEquals(0,db.unread(alice.id()));}
 @Test void reportsUseDatabaseAndNotifyOnReply()throws Exception{var reports=new CommunityReports(db);var q=new JsonObject();q.addProperty("message","Help");String ticket=reports.submit(UUID.fromString(alice.id()),"Alice",q,System.currentTimeMillis());reports.reply(ticket,"Admin","Fixed",true);assertEquals(1,db.unread(alice.id()));assertTrue(reports.list(UUID.fromString(bob.id()),0).isEmpty());assertFalse(Files.exists(world.resolve("anthub/reports")));}
 @Test void playerSearchAndAuditAreInDatabase()throws Exception{assertEquals(1,db.people("ali",0).size());db.audit("Admin","test","done");assertEquals(1,db.records("audit").size());assertFalse(Files.exists(world.resolve("anthub/server.db")));assertFalse(Files.exists(world.resolve("anthub/admin-log.json")));}

 @Test void cancelledWaiterDoesNotPromoteAnAlreadyAdmittedPlayer()throws Exception{var p=create("events");db.request(alice,input("events","join",id(p)));db.request(bob,input("events","join",id(p)));db.request(bob,input("events","leave",id(p)));assertEquals(0,db.unread(alice.id()));}
 @Test void popupPreferencesDoNotDeleteNotifications()throws Exception{var q=input("home","preferences","");var prefs=new JsonObject();prefs.add("favorites",new JsonArray());var muted=new JsonArray();muted.add("ideas");prefs.add("muted",muted);q.add("preferences",prefs);db.request(owner,q);var p=create("ideas");var status=input("ideas","status",id(p));status.addProperty("status","planned");status.addProperty("text","Soon");db.request(admin,status);assertEquals(1,db.unread(owner.id()));assertEquals(0,db.popupSequence(owner.id()));var summary=db.notificationSummary(List.of(UUID.fromString(owner.id()))).get(UUID.fromString(owner.id()));assertEquals(1,summary.unread());assertEquals(0,summary.sequence());}
 @Test void disabledSectionCannotBeReadThroughAnotherSection()throws Exception{var p=create("board");var config=db.configuration();config.getAsJsonArray("sections").remove(new JsonPrimitive("board"));db.configure(config);assertThrows(IllegalArgumentException.class,()->db.request(owner,input("ideas","detail",id(p))));}
 @Test void repliesArePagedAndCannotExceedPacketBudget()throws Exception{var p=create("board");for(int i=0;i<15;i++){var a=actor("User"+i,false);var q=input("board","respond",id(p));q.addProperty("text","x".repeat(1000));db.request(a,q);}var response=db.request(owner,input("board","detail",id(p)));assertEquals(5,response.getAsJsonObject("detail").getAsJsonObject("responses").size());assertTrue(Json.GSON.toJson(response).length()<32767);}
 @Test void groupMembershipLimitIsEnforcedOnInvitation()throws Exception{var config=db.configuration();config.addProperty("maxMemberships",1);db.configure(config);var first=create("groups");var secondInput=input("groups","create","");secondInput.addProperty("title","Second");secondInput.addProperty("description","Different");secondInput.addProperty("type","Команда");var second=db.request(bob,secondInput).getAsJsonObject("detail");for(var group:List.of(first,second)){var who=id(group).equals(id(first))?owner:bob;var invite=input("groups","invite",id(group));invite.addProperty("target",alice.id());db.request(who,invite);}var accept=input("groups","invitation",id(first));accept.addProperty("accept",true);db.request(alice,accept);accept.addProperty("id",id(second));assertThrows(IllegalArgumentException.class,()->db.request(alice,accept));assertTrue(detail("groups",id(second),alice).getAsJsonObject("invitations").has(alice.id()));}
 @Test void failedTransactionRollsBackAllWrites()throws Exception{assertThrows(Exception.class,()->db.transaction(()->{db.record("test","one",new JsonObject());throw new Exception("stop");}));assertNull(db.record("test","one"));}

 @Test void responseAndApplicationFlagsSurvivePaging()throws Exception{
  var board=create("board");var reply=input("board","respond",id(board));reply.addProperty("text","Hello");db.request(alice,reply);var later=input("board","detail",id(board));later.addProperty("page",1);var view=db.request(alice,later).getAsJsonObject("detail");assertTrue(view.get("hasResponded").getAsBoolean());assertTrue(view.getAsJsonObject("responses").isEmpty());
  var group=create("groups");var apply=input("groups","apply",id(group));apply.addProperty("text","Hello");db.request(alice,apply);later=input("groups","detail",id(group));later.addProperty("page",1);view=db.request(alice,later).getAsJsonObject("detail");assertTrue(view.get("hasApplication").getAsBoolean());assertTrue(view.getAsJsonObject("applications").isEmpty());
 }
 @Test void cardActionsExposeOnlyOwnParticipation()throws Exception{
  var event=create("events");db.request(alice,input("events","join",id(event)));var a=db.request(alice,input("events","list","")).getAsJsonArray("entries").get(0).getAsJsonObject();var b=db.request(bob,input("events","list","")).getAsJsonArray("entries").get(0).getAsJsonObject();assertTrue(a.get("isParticipant").getAsBoolean());assertFalse(b.get("isParticipant").getAsBoolean());assertFalse(a.has("participants"));
  var idea=create("ideas");db.request(alice,input("ideas","support",id(idea)));a=db.request(alice,input("ideas","list","")).getAsJsonArray("entries").get(0).getAsJsonObject();b=db.request(bob,input("ideas","list","")).getAsJsonArray("entries").get(0).getAsJsonObject();assertTrue(a.get("supported").getAsBoolean());assertFalse(b.get("supported").getAsBoolean());assertFalse(a.has("supporters"));
 }
 @Test void homeShowsOnlyThreeNearestJoinedOrOwnedEvents()throws Exception{var first=create("events");assertTrue(db.request(alice,input("home","list","")).getAsJsonArray("entries").isEmpty());db.request(alice,input("events","join",id(first)));assertEquals(1,db.request(alice,input("home","list","")).getAsJsonArray("entries").size());for(int i=0;i<4;i++){var event=create("events");db.request(alice,input("events","join",id(event)));}var home=db.request(alice,input("home","list","")).getAsJsonArray("entries");assertEquals(3,home.size());assertEquals(id(first),id(home.get(0).getAsJsonObject()));assertEquals(3,db.request(admin,input("home","list","")).getAsJsonArray("entries").size());db.request(alice,input("events","leave",id(first)));assertFalse(db.request(alice,input("home","list","")).getAsJsonArray("entries").asList().stream().anyMatch(e->id(e.getAsJsonObject()).equals(id(first))));}


 JsonObject command(JsonObject j){j.addProperty("operationId",UUID.randomUUID().toString());j.addProperty("issuedAt",System.currentTimeMillis());return j;}
 @Test void duplicateMutationKeepsResultAndDoesNotToggleTwice()throws Exception{var p=create("ideas");var q=command(input("ideas","support",id(p)));var first=db.request(alice,q);q.addProperty("request","retry");var second=db.request(alice,q);assertEquals(first.get("detail"),second.get("detail"));assertTrue(second.get("replayed").getAsBoolean());assertTrue(detail("ideas",id(p),alice).getAsJsonObject("supporters").has(alice.id()));q.addProperty("id","different");assertThrows(CommunityFailure.class,()->db.request(alice,q));}
 @Test void replayDoesNotLeakAfterPermissionChange()throws Exception{var p=create("ideas");var q=command(input("ideas","support",id(p)));db.request(alice,q);var changed=new CommunityStore.Actor(alice.id(),alice.name(),true,true);assertThrows(CommunityFailure.class,()->db.request(changed,q));assertTrue(detail("ideas",id(p),alice).getAsJsonObject("supporters").has(alice.id()));}
 @Test void staleRevisionCannotOverwriteAndHistoryIsPublicOnly()throws Exception{var p=create("ideas");var q=command(input("ideas","status",id(p)));q.addProperty("revision",p.get("revision").getAsLong());q.addProperty("status","planned");q.addProperty("text","Public answer");var updated=db.request(admin,q).getAsJsonObject("detail");var stale=command(q.deepCopy());stale.addProperty("text","Overwrite");assertEquals(CommunityFailure.Code.CONFLICT,assertThrows(CommunityFailure.class,()->db.request(admin,stale)).code());assertEquals("Public answer",Json.str(detail("ideas",id(p),alice),"answer"));assertEquals(2,updated.getAsJsonArray("history").size());var board=create("board");var reply=input("board","respond",id(board));reply.addProperty("text","PRIVATE");db.request(alice,reply);assertFalse(detail("board",id(board),bob).getAsJsonArray("history").toString().contains("PRIVATE"));}
 @Test void archiveIsSeparateAndCursorSurvivesInsert()throws Exception{
  for(int n=0;n<12;n++){var j=input("ideas","create","");j.addProperty("title","Idea "+n);j.addProperty("description","description");db.request(admin,j);}
  var first=db.request(alice,input("ideas","list",""));assertEquals(10,first.getAsJsonArray("entries").size());String cursor=Json.str(first,"nextCursor");assertFalse(cursor.isEmpty());
  var extra=input("ideas","create","");extra.addProperty("title","New arrival");extra.addProperty("description","description");db.request(admin,extra);
  var next=input("ideas","list","");next.addProperty("cursor",cursor);var second=db.request(alice,next);assertEquals(2,second.getAsJsonArray("entries").size());var ids=new HashSet<String>();first.getAsJsonArray("entries").forEach(e->ids.add(id(e.getAsJsonObject())));second.getAsJsonArray("entries").forEach(e->assertTrue(ids.add(id(e.getAsJsonObject()))));assertThrows(CommunityFailure.class,()->db.request(bob,next));
  var board=create("board");db.request(owner,input("board","close",id(board)));assertTrue(db.request(alice,input("board","list","")).getAsJsonArray("entries").isEmpty());var archive=input("board","list","");archive.addProperty("archive",true);assertEquals(1,db.request(alice,archive).getAsJsonArray("entries").size());
 }
 @Test void failedReceiptRollsBackAndCanRetry()throws Exception{var p=create("ideas");var q=command(input("ideas","status",id(p)));q.addProperty("revision",p.get("revision").getAsLong());q.addProperty("status","planned");q.addProperty("text","Answer");assertThrows(CommunityFailure.class,()->db.request(alice,q));assertNotNull(db.request(admin,q));}
 @Test void trashHidesRecordsAndRestoresDependents()throws Exception{
  for(String section:List.of("board","groups","events","polls","ideas")){
   var item=create(section);var who=Set.of("events","polls").contains(section)?admin:owner;String key=id(item);
   var mutation=input(section,switch(section){case "board"->"respond";case "groups"->"apply";case "events"->"join";case "polls"->"vote";default->"support";},key);
   mutation.addProperty("text","Keep this response");var choices=new JsonArray();choices.add(0);mutation.add("choices",choices);db.request(alice,mutation);
   db.request(who,input(section,"delete",key));assertEquals("deleted",Json.str(detail(section,key,who),"status"));
   assertTrue(db.request(alice,input(section,"list","")).getAsJsonArray("entries").isEmpty());
   assertThrows(IllegalArgumentException.class,()->detail(section,key,alice));assertThrows(CommunityFailure.class,()->db.request(alice,input(section,"restore",key)));
   var trash=input(section,"list","");trash.addProperty("trash",true);assertEquals(1,db.request(who,trash).getAsJsonArray("entries").size());assertTrue(db.request(alice,trash).getAsJsonArray("entries").isEmpty());
   db.request(who,input(section,"restore",key));var restored=detail(section,key,alice);
   switch(section){case "board"->assertTrue(restored.get("hasResponded").getAsBoolean());case "groups"->assertTrue(restored.get("hasApplication").getAsBoolean());case "events"->assertTrue(restored.get("isParticipant").getAsBoolean());case "polls"->assertTrue(restored.get("voted").getAsBoolean());case "ideas"->assertTrue(restored.getAsJsonObject("supporters").has(alice.id()));}
  }
 }
 @Test void deletingGroupDetachesPostsAndEventsWithoutDeletingThem()throws Exception{
  var group=create("groups");String groupId=id(group);
  for(String section:List.of("board","events")){
   var create=input(section,"create","");create.addProperty("title","Related");create.addProperty("description","Keep");create.addProperty("days",2);create.addProperty("startsAt",Instant.now().plusSeconds(900).toString());create.addProperty("capacity",10);create.addProperty("group",groupId);
   var privilegedOwner=new CommunityStore.Actor(owner.id(),owner.name(),true,true);db.request(privilegedOwner,create);
  }
  db.request(owner,input("groups","delete",groupId));
  for(String section:List.of("board","events")){var entries=db.request(alice,input(section,"list","")).getAsJsonArray("entries");assertEquals(1,entries.size());assertFalse(detail(section,id(entries.get(0).getAsJsonObject()),alice).has("group"));}
  db.request(owner,input("groups","restore",groupId));assertTrue(detail("groups",groupId,owner).get("isMember").getAsBoolean());
 }
 @Test void trashExpiresOnlyAfterThirtyDaysAndCannotSendReminders()throws Exception{
  var poll=create("polls");var vote=input("polls","vote",id(poll));var choices=new JsonArray();choices.add(0);vote.add("choices",choices);db.request(alice,vote);db.request(admin,input("polls","delete",id(poll)));
  db.reminders(System.currentTimeMillis()+120000);assertEquals(0,db.unread(alice.id()));db.purgeDeleted(System.currentTimeMillis()+29L*86400000);assertEquals("deleted",Json.str(detail("polls",id(poll),admin),"status"));
  db.purgeDeleted(System.currentTimeMillis()+31L*86400000);assertThrows(IllegalArgumentException.class,()->detail("polls",id(poll),admin));
 }
 @Test void transactionalEventsRollbackAndAcknowledgeExactlyTheBatch()throws Exception{
  var before=db.pendingChanges();db.acknowledgeChanges(before);assertTrue(db.pendingChanges().isEmpty());
  assertThrows(Exception.class,()->db.transaction(()->{create("board");throw new Exception("rollback");}));assertTrue(db.pendingChanges().isEmpty());
  create("board");var batch=db.pendingChanges();assertFalse(batch.isEmpty());create("ideas");db.acknowledgeChanges(batch);var remaining=db.pendingChanges();assertFalse(remaining.isEmpty());for(var event:remaining)assertEquals("ideas",Json.str(event.getAsJsonObject(),"topic"));
 }

 @Test void privateLocationsAreNotSentToOutsidersAndSurviveTrash()throws Exception{
  var group=create("groups");var place=new CommunityLocation("Private base","minecraft:overworld",123,64,456,true).json();var change=input("groups","location",id(group));change.add("location",place);db.request(owner,change);
  assertEquals(place,detail("groups",id(group),owner).get("location"));assertFalse(detail("groups",id(group),alice).has("location"));assertFalse(db.request(alice,input("groups","list","")).toString().contains("Private base"));
  assertThrows(CommunityFailure.class,()->db.request(alice,change));var apply=input("groups","apply",id(group));apply.addProperty("text","Join");db.request(alice,apply);var accept=input("groups","application",id(group));accept.addProperty("target",alice.id());accept.addProperty("accept",true);db.request(owner,accept);assertEquals(place,detail("groups",id(group),alice).get("location"));
  var board=input("board","create","");board.addProperty("title","Meet");board.addProperty("description","Private meeting");board.addProperty("days",3);board.addProperty("group",id(group));board.add("location",place);var post=db.request(owner,board).getAsJsonObject("detail");assertEquals(place,detail("board",id(post),alice).get("location"));assertFalse(detail("board",id(post),bob).has("location"));
  db.request(owner,input("groups","delete",id(group)));assertFalse(detail("board",id(post),alice).has("location"));db.request(owner,input("groups","restore",id(group)));assertEquals(place,detail("groups",id(group),alice).get("location"));assertFalse(detail("board",id(post),alice).has("location"));
  change.add("location",JsonNull.INSTANCE);db.request(owner,change);assertFalse(detail("groups",id(group),owner).has("location"));
 }
 @Test void locationUpdateHonorsRevisionAndCannotInjectPrivateUngroupedLocation()throws Exception{
  var post=create("ideas");var q=command(input("ideas","location",id(post)));q.add("revision",post.get("revision"));q.add("location",new CommunityLocation("Build here","minecraft:overworld",1,65,2,false).json());db.request(owner,q);
  var stale=command(q.deepCopy());stale.getAsJsonObject("location").addProperty("x",77);assertEquals(CommunityFailure.Code.CONFLICT,assertThrows(CommunityFailure.class,()->db.request(owner,stale)).code());assertEquals(1,detail("ideas",id(post),owner).getAsJsonObject("location").get("x").getAsInt());
  var privateLocation=input("ideas","location",id(post));privateLocation.add("location",new CommunityLocation("Secret","minecraft:overworld",0,70,0,true).json());assertThrows(IllegalArgumentException.class,()->db.request(owner,privateLocation));
 }

 @Test void threeHundredConcurrentActorsDoNotLoseOrDuplicateSupport()throws Exception{
  String key=id(create("ideas"));var workers=java.util.concurrent.Executors.newFixedThreadPool(12);long start=System.nanoTime();
  try{var jobs=new ArrayList<java.util.concurrent.Callable<Void>>();for(int i=0;i<300;i++){var actor=actor("Concurrent"+i,false);jobs.add(()->{var q=command(input("ideas","support",key));db.request(actor,q);var replay=db.request(actor,q);assertTrue(replay.get("replayed").getAsBoolean());assertTrue(replay.getAsJsonObject("detail").getAsJsonObject("supporters").has(actor.id()));return null;});}for(var result:workers.invokeAll(jobs,90,java.util.concurrent.TimeUnit.SECONDS)){assertFalse(result.isCancelled());result.get();}}
  finally{workers.shutdownNow();}
  var result=detail("ideas",key,owner);assertEquals(300,result.get("supportersCount").getAsInt());assertTrue(result.getAsJsonObject("supporters").isEmpty());assertTrue(result.toString().length()<32767);System.out.println("300 actors / 600 idempotent requests: "+((System.nanoTime()-start)/1000000)+" ms");
 }

 @Test void editClearsLocationThroughSerializedCommandAndRetry()throws Exception{
  var group=create("groups");var set=input("groups","location",id(group));set.add("location",new CommunityLocation("Base","minecraft:overworld",1,64,2,false).json());db.request(owner,set);var current=detail("groups",id(group),owner);
  var edit=command(input("groups","edit",id(group)));edit.add("revision",current.get("revision"));edit.addProperty("title","Renamed group");edit.addProperty("description","Description");edit.addProperty("type",Json.str(current,"type"));edit.add("location",JsonNull.INSTANCE);edit.addProperty("clearLocation",true);
  var wire=Json.parse(Json.GSON.toJson(edit));assertFalse(wire.has("location"));var changed=db.request(owner,wire).getAsJsonObject("detail");assertFalse(changed.has("location"));assertEquals("Renamed group",Json.str(changed,"title"));db.request(owner,wire);assertFalse(detail("groups",id(group),owner).has("location"));
 }
}
