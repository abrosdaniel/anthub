package dev.abros.anthub.core;
import java.util.ArrayDeque;
import java.util.concurrent.*;
/** Bounded ordering per session on a shared worker pool. */
public final class SerialExecutor implements Executor {
 private final Executor executor;private final int limit;private final ArrayDeque<Runnable> queue=new ArrayDeque<>();private boolean running;
 public SerialExecutor(Executor executor,int limit){this.executor=executor;this.limit=limit;}
 public synchronized void execute(Runnable task){
  if(queue.size()>=limit)throw new RejectedExecutionException("Session queue full");
  queue.add(task);if(running)return;running=true;
  try{executor.execute(this::drain);}catch(RejectedExecutionException e){running=false;queue.remove(task);throw e;}
 }
 private void drain(){while(true){Runnable task;synchronized(this){task=queue.poll();if(task==null){running=false;return;}}
  try{task.run();}catch(RuntimeException e){System.getLogger(SerialExecutor.class.getName()).log(System.Logger.Level.WARNING,"Session task failed: "+e.getClass().getSimpleName());}
 }}
}
