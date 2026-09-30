package dev.abros.anthub.core;
import com.google.gson.JsonObject;
import java.util.Set;
/** Request semantics shared by queueing and mutation identity assignment. */
public final class MenuRequests {
 private MenuRequests(){}
 private static final Set<String> COMMUNITY_READS=Set.of("list","detail","workList","workGet","workMembers","globalSearch","toolsPrivacy","toolsPlaces","toolsFollowing","toolsMapSettings","toolsMapPeers","plusProfile","plusIgnores","plusItemRead");
 private static final Set<String> PAGE_READS=Set.of("players","reports","myReports","myReport","history","menuData","playerAdministration","adminDashboard");
 private static final Set<String> MUTATIONS=Set.of("report","reply","moderate","reportManage");
 public static boolean read(JsonObject request){String action=Json.opt(request,"action","");return PAGE_READS.contains(action)||action.equals("community")&&COMMUNITY_READS.contains(Json.opt(request,"op",""))||action.equals("moderationVote")&&Json.opt(request,"op","").equals("view");}
 public static boolean mutation(JsonObject request){String action=Json.opt(request,"action","");return MUTATIONS.contains(action)||action.equals("community")&&!COMMUNITY_READS.contains(Json.opt(request,"op",""));}
 public static boolean sameRead(JsonObject first,JsonObject second){return read(first)&&read(second)&&UiPayload.same(first,second);}
}
