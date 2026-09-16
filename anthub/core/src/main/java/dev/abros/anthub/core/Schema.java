package dev.abros.anthub.core;
import com.google.gson.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
/** Executes the subset used by the bundled, closed AntHub 2020-12 schemas. No remote references. */
public final class Schema {
 private static final Map<String,JsonObject> CACHE=new ConcurrentHashMap<>();
 public static void validate(String name,JsonElement value){JsonObject schema=CACHE.computeIfAbsent(name,n->{try(var in=Schema.class.getResourceAsStream("/anthub/schemas/v1/"+n+".schema.json")){if(in==null)throw new IOException("Missing bundled schema "+n);return Json.parse(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}catch(IOException e){throw new IllegalStateException(e);}});check(schema,value,"$");}
 private static void fail(String p,String reason){throw new IllegalArgumentException(p+": "+reason);}
 private static boolean matches(JsonObject s,JsonElement v,String p){try{check(s,v,p);return true;}catch(IllegalArgumentException e){return false;}}
 private static void check(JsonObject s,JsonElement v,String p){
  if(s.has("const")&&!s.get("const").equals(v))fail(p,"unsupported constant");
  if(s.has("enum")){boolean found=false;for(var e:s.getAsJsonArray("enum"))if(e.equals(v))found=true;if(!found)fail(p,"not an allowed value");}
  if(s.has("not")&&matches(s.getAsJsonObject("not"),v,p))fail(p,"forbidden field combination");
  if(s.has("oneOf")){int count=0;for(var option:s.getAsJsonArray("oneOf"))if(matches(option.getAsJsonObject(),v,p))count++;if(count!=1)fail(p,"expected exactly one source");}
  if(s.has("type")){
   boolean correct=switch(s.get("type").getAsString()){
    case "object"->v.isJsonObject();case "array"->v.isJsonArray();case "string"->v.isJsonPrimitive()&&v.getAsJsonPrimitive().isString();case "boolean"->v.isJsonPrimitive()&&v.getAsJsonPrimitive().isBoolean();case "integer"->v.isJsonPrimitive()&&v.getAsJsonPrimitive().isNumber()&&v.getAsBigDecimal().stripTrailingZeros().scale()<=0;case "number"->v.isJsonPrimitive()&&v.getAsJsonPrimitive().isNumber();default->false;};if(!correct)fail(p,"invalid type");
  }
  if(v.isJsonObject()){
   JsonObject o=v.getAsJsonObject();if(s.has("required"))for(var k:s.getAsJsonArray("required"))if(!o.has(k.getAsString()))fail(p,"missing "+k.getAsString());
   JsonObject props=s.has("properties")?s.getAsJsonObject("properties"):new JsonObject();for(var e:o.entrySet()){if(props.has(e.getKey()))check(props.getAsJsonObject(e.getKey()),e.getValue(),p+"."+e.getKey());else if(s.has("additionalProperties")&&!s.get("additionalProperties").getAsBoolean())fail(p,"unknown "+e.getKey());}
  }
  if(v.isJsonArray()){
   JsonArray a=v.getAsJsonArray();if(s.has("minItems")&&a.size()<s.get("minItems").getAsInt())fail(p,"too few items");if(s.has("maxItems")&&a.size()>s.get("maxItems").getAsInt())fail(p,"too many items");Set<JsonElement> seen=new HashSet<>();int i=0;for(var e:a){if(s.has("uniqueItems")&&s.get("uniqueItems").getAsBoolean()&&!seen.add(e))fail(p,"duplicate item");if(s.has("items"))check(s.getAsJsonObject("items"),e,p+"["+(i++)+"]");}
  }
  if(v.isJsonPrimitive()&&v.getAsJsonPrimitive().isString()){
   String text=v.getAsString();int length=text.codePointCount(0,text.length());if(s.has("minLength")&&length<s.get("minLength").getAsInt())fail(p,"too short");if(s.has("maxLength")&&length>s.get("maxLength").getAsInt())fail(p,"too long");if(s.has("pattern")&&!java.util.regex.Pattern.compile(s.get("pattern").getAsString()).matcher(text).find())fail(p,"invalid format");
  }
  if(v.isJsonPrimitive()&&v.getAsJsonPrimitive().isNumber()){
   var n=v.getAsBigDecimal();if(s.has("minimum")&&n.compareTo(s.get("minimum").getAsBigDecimal())<0)fail(p,"below minimum");if(s.has("maximum")&&n.compareTo(s.get("maximum").getAsBigDecimal())>0)fail(p,"above maximum");
  }
 }
}
