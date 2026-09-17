package dev.abros.anthub.core;

import java.util.*;
import java.util.concurrent.*;

/** Subscribers share one request; the last cancellation also interrupts its worker. */
public final class SharedRequests {
 private final Map<String,Flight<?>> flights=new HashMap<>();
 private static final class Flight<T>{final CompletableFuture<T> result=new CompletableFuture<>();FutureTask<Void> worker;int users;}
 @SuppressWarnings("unchecked")
 public synchronized <T> CompletableFuture<T> submit(String key,Executor executor,Callable<T> work){
  var flight=(Flight<T>)flights.get(key);boolean fresh=flight==null;
  if(fresh){flight=new Flight<>();flights.put(key,flight);}
  final var current=flight;current.users++;
  var client=new CompletableFuture<T>();
  client.whenComplete((v,e)->{synchronized(this){if(--current.users==0){flights.remove(key,current);if(!current.result.isDone()&&current.worker!=null)current.worker.cancel(true);}}});
  current.result.whenComplete((v,e)->{if(e==null)client.complete(v);else client.completeExceptionally(e);});
  if(fresh){current.worker=new FutureTask<>(()->{try{current.result.complete(work.call());}catch(Exception e){current.result.completeExceptionally(e);}return null;});try{executor.execute(current.worker);}catch(RejectedExecutionException e){current.result.completeExceptionally(e);}}
  return client;
 }
}
