package dev.abros.anthub.core;
import com.google.gson.*;
import java.util.*;
/** Stable connection envelope. New B features are optional names, never required fields. */
public final class ConnectionCompatibility {
    private ConnectionCompatibility(){}
    public static final Set<String> FEATURES=Set.of("menu","auth","pack","home","players","board","groups","events","polls","ideas","notifications","info","help","admin","player-statistics","admin-tools","moderation-votes","moderation-vote-duration","skins","skin-names");
    public static JsonArray features(){var array=new JsonArray();FEATURES.stream().sorted().forEach(array::add);return array;}
    public static String branch(String version){if(!Versions.sameMajor(version,version))throw new IllegalArgumentException("Invalid AntHub version");return version.split("\\.")[0]+".x";}
    public static Set<String> common(JsonElement value){
        if(value==null||!value.isJsonArray()||value.getAsJsonArray().size()>64)throw new IllegalArgumentException("Invalid AntHub features");
        var result=new HashSet<String>();for(var item:value.getAsJsonArray()){if(!item.isJsonPrimitive()||!item.getAsJsonPrimitive().isString()||item.getAsString().length()>64)throw new IllegalArgumentException("Invalid AntHub feature");if(FEATURES.contains(item.getAsString()))result.add(item.getAsString());}return Set.copyOf(result);
    }
    public static String failure(String local,String peer,JsonObject protocols){
        if(!Versions.sameMajor(local,peer))return "Для этого сервера нужна ветка AntHub "+branch(local)+". Выберите версию AntHub.";
        for(String key:List.of("pack","auth","menu"))if(protocols==null||!protocols.has(key)||!WireProtocols.current().get(key).equals(protocols.get(key)))return "Несовместимый выпуск AntHub. Выберите другую версию ветки "+branch(local)+".";
        return "";
    }
}
