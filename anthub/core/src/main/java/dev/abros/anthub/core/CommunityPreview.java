package dev.abros.anthub.core;
import com.google.gson.*;
import java.util.List;
/** Bounded public list summaries. Never include votes, applications or reply contents. */
public final class CommunityPreview {
 private CommunityPreview(){}
 public static JsonObject of(JsonObject document){
  var row=new JsonObject();for(String key:List.of("id","section","title","author","status","createdAt","startsAt","endsAt","type","recruiting","capacity","revision","visibility","rescheduledAt","series"))if(document.has(key))row.add(key,document.get(key).deepCopy());
  String description=Json.opt(document,"description","").replaceAll("\\s+"," ");row.addProperty("preview",description.substring(0,Math.min(200,description.length())));
  for(String field:List.of("responses","members","participants","supporters"))if(document.has(field))row.addProperty(field+"Count",document.getAsJsonObject(field).size());
  if(document.has("answer")){String answer=Json.opt(document,"answer","");row.addProperty("answerPreview",answer.substring(0,Math.min(140,answer.length())));}
  return row;
 }
}
