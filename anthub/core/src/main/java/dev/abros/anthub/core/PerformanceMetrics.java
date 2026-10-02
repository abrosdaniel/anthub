package dev.abros.anthub.core;
import java.util.*;
/** Bounded aggregate timings; contains operation names only, never account or payload data. */
public final class PerformanceMetrics {
 public record Sample(long count,long totalNanos,long maxNanos,long failures){public double meanMillis(){return count==0?0:totalNanos/1_000_000.0/count;}}
 private static final Map<String,Sample> values=new LinkedHashMap<>();
 public static synchronized void record(String operation,long nanos,boolean failed){if(!values.containsKey(operation)&&values.size()>=64)return;var old=values.getOrDefault(operation,new Sample(0,0,0,0));values.put(operation,new Sample(old.count+1,old.totalNanos+Math.max(0,nanos),Math.max(old.maxNanos,nanos),old.failures+(failed?1:0)));}
 public static synchronized Map<String,Sample> snapshot(){return Map.copyOf(values);}
 public static synchronized void clear(){values.clear();}
 private PerformanceMetrics(){}
}
