package dev.abros.anthub.core;
import com.google.gson.*;
import java.util.*;
/** Shared page merging. Stable first occurrence prevents duplicates at moving cursor boundaries. */
public final class MenuPages {
 public static JsonArray merge(Collection<JsonArray> batches){var out=new JsonArray();var ids=new HashSet<String>();for(var batch:batches)for(var e:batch){if(!e.isJsonObject())throw new IllegalArgumentException("Page entry must be an object");var row=e.getAsJsonObject();String id=Json.opt(row,"uuid",Json.opt(row,"id",e.toString()));if(ids.add(id))out.add(row.deepCopy());}return out;}
 private MenuPages(){}
}
