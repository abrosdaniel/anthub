package dev.abros.anthub.core;
import com.google.gson.*;import java.util.ArrayDeque;
/** Process-local bounded diagnostic series. No player or connection credentials. */
public final class LatencyHistory {
 private record Sample(long at,long total,long database){}private final ArrayDeque<Sample> samples=new ArrayDeque<>();
 public synchronized void add(long at,long total,long database){if(at<0||total<0||database<0)throw new IllegalArgumentException("Negative sample");if(!samples.isEmpty()&&at-samples.peekLast().at()<1000)return;samples.addLast(new Sample(at,total,database));while(samples.size()>120)samples.removeFirst();}
 public synchronized JsonArray snapshot(){var out=new JsonArray();for(var s:samples){var row=new JsonObject();row.addProperty("at",s.at());row.addProperty("totalMs",s.total());row.addProperty("databaseMs",s.database());out.add(row);}return out;}
 public synchronized void clear(){samples.clear();}
}
