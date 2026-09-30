package dev.abros.anthub.core;
/** Short codes never contain ambiguous I/O/0/1 and are allocated from a durable server sequence. */
public final class TaskCodes {
 private static final String ALPHABET="23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
 private TaskCodes(){}
 public static String encode(long number){if(number<0)throw new IllegalArgumentException("Negative task code");var out=new StringBuilder();do{out.append(ALPHABET.charAt((int)(number%32)));number/=32;}while(number>0);while(out.length()<4)out.append('2');return out.reverse().toString();}
 public static boolean isMarker(String text){return text!=null&&text.strip().equalsIgnoreCase("&anthub");}
 public static String normalize(String code){return code.strip().toUpperCase(java.util.Locale.ROOT);}
}
