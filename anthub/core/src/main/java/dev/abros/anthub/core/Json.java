package dev.abros.anthub.core;
import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.util.*;
public final class Json {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private Json() {}
    public static JsonObject read(Path path) throws IOException { return parse(Files.readString(path)); }
    public static JsonObject parse(String value) throws IOException {
        if (value.length()>8*1024*1024) throw new IOException("Metadata too large");
        try (JsonReader r=new JsonReader(new StringReader(value))) {
            r.setLenient(false);
            JsonElement e=readValue(r,0);
            if (r.peek()!=JsonToken.END_DOCUMENT || !e.isJsonObject()) throw new IOException("Expected one JSON object");
            return e.getAsJsonObject();
        } catch (IllegalStateException|NumberFormatException e) { throw new IOException("Invalid JSON",e); }
    }
    private static JsonElement readValue(JsonReader r,int depth) throws IOException {
        if(depth>32) throw new IOException("JSON nesting limit");
        return switch(r.peek()) {
            case BEGIN_OBJECT -> {
                JsonObject o=new JsonObject();Set<String> seen=new HashSet<>();r.beginObject();
                while(r.hasNext()){String k=r.nextName();if(!seen.add(k))throw new IOException("Duplicate key: "+k);o.add(k,readValue(r,depth+1));}r.endObject();yield o;
            }
            case BEGIN_ARRAY -> {JsonArray a=new JsonArray();r.beginArray();while(r.hasNext()){if(a.size()>20000)throw new IOException("Array limit");a.add(readValue(r,depth+1));}r.endArray();yield a;}
            case STRING -> new JsonPrimitive(r.nextString());
            case BOOLEAN -> new JsonPrimitive(r.nextBoolean());
            case NUMBER -> new JsonPrimitive(new java.math.BigDecimal(r.nextString()));
            case NULL -> {r.nextNull();yield JsonNull.INSTANCE;}
            default -> throw new IOException("Unexpected JSON token");
        };
    }
    public static void write(Path path,Object value) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path tmp=path.resolveSibling(path.getFileName()+".tmp-"+UUID.randomUUID());
        try {Files.writeString(tmp,GSON.toJson(value));try(FileChannel c=FileChannel.open(tmp,StandardOpenOption.WRITE)){c.force(true);}move(tmp,path);}
        finally {Files.deleteIfExists(tmp);}
    }
    public static void move(Path from,Path to)throws IOException {
        Files.move(from,to,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    public static String str(JsonObject o,String key){if(!o.has(key)||!o.get(key).isJsonPrimitive()||!o.getAsJsonPrimitive(key).isString())throw new IllegalArgumentException("Missing string: "+key);return o.get(key).getAsString();}
    public static String opt(JsonObject o,String key,String fallback){return o.has(key)?str(o,key):fallback;}
    public static void keys(JsonObject o,String... allowed){Set<String> set=Set.of(allowed);for(String k:o.keySet())if(!set.contains(k))throw new IllegalArgumentException("Unknown field: "+k);}
}
