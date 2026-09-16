package dev.abros.anthub.core;
import com.google.gson.*;
import java.util.*;
/** Support tickets share the server PostgreSQL database. */
public final class CommunityReports {
 private final CommunityStore db;
 public CommunityReports(CommunityStore db){this.db=db;}
 public String submit(UUID player,String name,JsonObject in,long now)throws Exception{return db.inTransaction(()->{db.checkReport(player.toString(),now);String message=Json.str(in,"message").strip();if(message.isEmpty()||message.length()>1500)throw new IllegalArgumentException("Текст обращения: 1–1500 символов");var j=new JsonObject();String id=UUID.randomUUID().toString();j.addProperty("id",id);j.addProperty("uuid",player.toString());j.addProperty("player",name);j.addProperty("createdAt",now);j.addProperty("message",message);j.addProperty("status","open");for(String k:List.of("coreVersion","packVersion","repository","audit")){String v=Json.opt(in,k,"");if(v.length()>(k.equals("audit")?2000:512))throw new IllegalArgumentException("Поле слишком длинное");j.addProperty(k,v);}db.record("reports",id,j);return id;});}
 public JsonArray list(UUID owner,int page)throws Exception{if(page<0||page>1000)throw new IllegalArgumentException("Invalid page");var out=new JsonArray();db.recordPage("reports",owner==null?"":owner.toString(),page,5).forEach(out::add);return out;}
 public JsonObject get(UUID owner,String id)throws Exception{UUID.fromString(id);var j=db.record("reports",id);if(j==null||!owner.toString().equals(Json.str(j,"uuid")))throw new IllegalArgumentException("Обращение не найдено");return j;}
 public JsonArray list(int page)throws Exception{return list(null,page);}
 public void reply(String id,String author,String text,boolean resolved)throws Exception{db.transaction(()->{UUID.fromString(id);var j=db.record("reports",id);if(j==null)throw new IllegalArgumentException("Обращение не найдено");if(text.isBlank()||text.length()>1500)throw new IllegalArgumentException("Ответ: 1–1500 символов");j.addProperty("reply",text);j.addProperty("replyAuthor",author);j.addProperty("repliedAt",System.currentTimeMillis());j.addProperty("status",resolved?"resolved":"open");db.transaction(()->{db.record("reports",id,j);db.externalNotice(Json.str(j,"uuid"),"Ответ администрации на обращение",id);db.audit(author,"report reply",id);});});}
 public void prune(long now)throws Exception{db.pruneReports(now);}
}
