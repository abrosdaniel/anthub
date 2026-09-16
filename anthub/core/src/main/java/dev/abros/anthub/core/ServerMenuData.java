package dev.abros.anthub.core;

import com.google.gson.*;
import java.net.URI;

/** Validates the closed, public server menu document before it can be sent to clients. */
public final class ServerMenuData {
    private ServerMenuData() {}
    public static JsonObject validate(JsonObject input) {
        Json.keys(input,"links");
        JsonArray links=array(input,"links",12);
        for(var item:links){
            JsonObject link=item.getAsJsonObject();Json.keys(link,"name","url");text(link,"name",60);
            URI uri=URI.create(text(link,"url",512));
            if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)throw new IllegalArgumentException("Links must use HTTPS");
        }
        // Reserve room for the response envelope and indentation added by its parent.
        if(Json.GSON.toJson(input).length()>24000)throw new IllegalArgumentException("Menu data exceeds network limit");
        return input.deepCopy();
    }
    private static JsonArray array(JsonObject input,String key,int maximum){
        if(!input.has(key)||!input.get(key).isJsonArray())throw new IllegalArgumentException("Expected array: "+key);
        var array=input.getAsJsonArray(key);if(array.size()>maximum)throw new IllegalArgumentException("Too many "+key);
        for(var item:array)if(!item.isJsonObject())throw new IllegalArgumentException("Expected object in "+key);
        return array;
    }
    private static String text(JsonObject input,String key,int maximum){
        String value=Json.str(input,key);if(value.isBlank()||value.length()>maximum)throw new IllegalArgumentException("Invalid "+key);return value;
    }
}
