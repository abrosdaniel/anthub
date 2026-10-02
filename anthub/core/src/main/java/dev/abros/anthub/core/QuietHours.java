package dev.abros.anthub.core;
/** Client-local hours; equal endpoints mean a whole quiet day. */
public final class QuietHours {
 private QuietHours() {}
 public static boolean active(boolean enabled,int start,int end,int hour) {
  if(start<0||start>23||end<0||end>23||hour<0||hour>23)throw new IllegalArgumentException("Invalid hour");
  return enabled&&(start==end||start<end&&hour>=start&&hour<end||start>end&&(hour>=start||hour<end));
 }
}
