package dev.abros.anthub.core;
/** Bounded line comparison: '-' is local, '+' is the project version. */
public final class ConfigDiff {
 private ConfigDiff(){}
 public static String compare(String local,String desired){
  if(local.indexOf('\0')>=0||desired.indexOf('\0')>=0)throw new IllegalArgumentException("Binary configuration cannot be compared");
  String[] a=local.split("\\R",-1),b=desired.split("\\R",-1);StringBuilder result=new StringBuilder();int shown=0;
  for(int i=0;i<Math.max(a.length,b.length);i++){
   String left=i<a.length?a[i]:null,right=i<b.length?b[i]:null;
   if(java.util.Objects.equals(left,right))continue;
   if(++shown>200){result.append("…\n");break;}
   result.append("@@ ").append(i+1).append(" @@\n");if(left!=null)result.append("- ").append(left).append('\n');if(right!=null)result.append("+ ").append(right).append('\n');
  }
  return result.toString();
 }
}
