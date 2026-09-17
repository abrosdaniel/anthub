package dev.abros.anthub.core;
import java.util.*;
import java.util.function.Consumer;
/** Throttled aggregate progress. Verified files are counted only once. */
public final class DownloadProgress {
 private final long total,start=System.nanoTime();private long transferred,last;private final Consumer<String> sink;
 private final Map<String,Long> loaded=new HashMap<>();private final Map<String,Integer> sources=new HashMap<>();
 public DownloadProgress(long total,Consumer<String> sink){this.total=total;this.sink=sink;}
 public synchronized void source(String path,int index){loaded.put(path,0L);sources.put(path,index);emit(path,true);}
 public synchronized void position(String path,long size,long position,long received){transferred+=Math.max(0,received);loaded.put(path,Math.max(0,Math.min(Math.max(0,size-1),position)));emit(path,false);}
 public synchronized void verified(String path,long size){loaded.put(path,size);emit(path,true);}
 private void emit(String path,boolean force){long now=System.nanoTime();if(!force&&now-last<250_000_000L)return;last=now;long done=Math.min(total,loaded.values().stream().mapToLong(Long::longValue).sum());double seconds=Math.max(0.1,(now-start)/1e9);sink.accept("DOWNLOAD|"+done+"|"+total+"|"+(long)(transferred/seconds)+"|"+sources.getOrDefault(path,1)+"|"+path);}
}
