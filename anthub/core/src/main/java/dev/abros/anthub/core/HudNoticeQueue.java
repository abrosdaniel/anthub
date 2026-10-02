package dev.abros.anthub.core;

import java.util.*;

/** Deterministic delivery queue. Expiration is presentation only and never marks notices read. */
public final class HudNoticeQueue {
 public enum Priority { ORDINARY, IMPORTANT, URGENT }
 public record Notice(String id,String section,String target,String event,String title,String body,Priority priority,String group) {
  public Notice(String id,String section,String target,String event,String title,String body,Priority priority){this(id,section,target,event,title,body,priority,"");}
  public String mergeKey(){return section+"|"+target+"|"+event+"|"+group;}
 }
 public static final class Entry {
  private Notice notice;private final LinkedHashSet<String> ids=new LinkedHashSet<>();private long start=-1;private int count=1;
  Entry(Notice notice){this.notice=notice;if(!notice.id().isEmpty())ids.add(notice.id());}
  public Notice notice(){return notice;} public List<String> ids(){return List.copyOf(ids);} public int count(){return count;}
  public long duration(){return notice.priority()==Priority.ORDINARY?6000:9000;}
  public double remaining(long now){return start<0?1:Math.max(0,1-(now-start)/(double)duration());}
  public long age(long now){return start<0?0:Math.max(0,now-start);}
 }
 private final List<Entry> visible=new ArrayList<>();private final Deque<Entry> waiting=new ArrayDeque<>();private final LinkedHashSet<String> seen=new LinkedHashSet<>();private int overflow;private long pausedAt=-1;
 public void clear(){visible.clear();waiting.clear();seen.clear();overflow=0;pausedAt=-1;}
 public void add(Notice notice,long now,int limit){
  if(!notice.id().isEmpty()&&!seen.add(notice.id()))return;while(seen.size()>512)seen.remove(seen.iterator().next());
  for(var entry:all())if(!notice.target().isEmpty()&&entry.notice.mergeKey().equals(notice.mergeKey())){entry.notice=notice;entry.count++;if(!notice.id().isEmpty())entry.ids.add(notice.id());if(notice.priority()==Priority.URGENT&&waiting.remove(entry)){if(visible.size()>=limit){var displaced=visible.remove(visible.size()-1);displaced.start=-1;waiting.addFirst(displaced);}entry.start=now;visible.add(0,entry);}return;}
  var entry=new Entry(notice);if(notice.priority()==Priority.URGENT){if(visible.size()>=limit){var displaced=visible.remove(visible.size()-1);displaced.start=-1;waiting.addFirst(displaced);}entry.start=now;visible.add(0,entry);}else if(waiting.size()<32)waiting.addLast(entry);else overflow++;
  advance(now,limit,pausedAt<0);
 }
 private List<Entry> all(){var all=new ArrayList<>(visible);all.addAll(waiting);return all;}
 public List<Entry> advance(long now,int limit,boolean showing){limit=Math.max(1,Math.min(5,limit));if(!showing){if(pausedAt<0)pausedAt=now;return List.copyOf(visible);}if(pausedAt>=0){for(var e:visible)if(e.start>=0)e.start+=now-Math.max(pausedAt,e.start);pausedAt=-1;}visible.removeIf(e->e.start>=0&&now-e.start>=e.duration());while(visible.size()>limit){var e=visible.remove(visible.size()-1);e.start=-1;waiting.addFirst(e);}if(showing)while(visible.size()<limit&&!waiting.isEmpty()){var e=waiting.removeFirst();e.start=now;visible.add(e);}if(showing&&visible.size()<limit&&waiting.isEmpty()&&overflow>0){var e=new Entry(new Notice("","notifications","","summary","Ещё "+overflow+" уведомлений","Откройте центр уведомлений",Priority.ORDINARY));overflow=0;e.start=now;visible.add(e);}return List.copyOf(visible);}
 public List<String> dismiss(Entry entry){visible.remove(entry);waiting.remove(entry);return entry.ids();}
}
