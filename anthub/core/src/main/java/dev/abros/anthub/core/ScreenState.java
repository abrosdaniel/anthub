package dev.abros.anthub.core;
/** Framework-free read state, independent from screen geometry and rendering. */
public final class ScreenState<T> {
 public enum Phase { INITIAL, LOADING, READY, EMPTY, ERROR }
 private boolean emptyValue;private Phase phase=Phase.INITIAL;private T value;private String error="";private long generation;
 public long begin(){phase=Phase.LOADING;error="";return ++generation;}
 public boolean resolve(long token,T next,boolean empty){if(token!=generation||phase!=Phase.LOADING)return false;value=next;emptyValue=empty;phase=empty?Phase.EMPTY:Phase.READY;error="";return true;}
 public boolean fail(long token,String message){if(token!=generation||phase!=Phase.LOADING)return false;error=message;phase=Phase.ERROR;return true;}
 public void cancel(){generation++;phase=value==null?Phase.INITIAL:emptyValue?Phase.EMPTY:Phase.READY;}
 public Phase phase(){return phase;}public T value(){return value;}public String error(){return error;}
}
