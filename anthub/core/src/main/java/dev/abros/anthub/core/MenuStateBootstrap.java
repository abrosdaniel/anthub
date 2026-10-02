package dev.abros.anthub.core;
/** Initial menu handshake is connection-owned and retried until the server confirms readiness. */
public final class MenuStateBootstrap {
 private boolean attempted,confirmed;private long nextAttempt;
 public boolean requestDue(long now,boolean available,boolean playerReady){if(!available||!playerReady||confirmed||now<nextAttempt)return false;attempted=true;nextAttempt=now+5000;return true;}
 public void confirm(){if(attempted)confirmed=true;}
 public void reset(){attempted=false;confirmed=false;nextAttempt=0;}
}
