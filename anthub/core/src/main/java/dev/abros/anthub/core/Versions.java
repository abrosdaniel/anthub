package dev.abros.anthub.core;
public final class Versions {
    private Versions(){}
    /** Public AntHub releases are A.B.C; A is the compatibility boundary. */
    public static boolean sameMajor(String installed,String required){
        String pattern="(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)";
        return installed.matches(pattern)&&required.matches(pattern)&&installed.split("\\.")[0].equals(required.split("\\.")[0]);
    }
    /** A.x.x accepts a compatible major; A.B.x and A.B.C set a minimum within it. */
    public static boolean supportsRequirement(String installed,String requirement){
        String number="(0|[1-9][0-9]*)";
        if(installed==null||requirement==null||!installed.matches(number+"\\."+number+"\\."+number))return false;
        // Keep the original A.x seed format readable.
        if(requirement.matches(number+"\\.x(\\.x)?"))return sameMajor(installed,requirement.split("\\.")[0]+".0.0");
        if(!requirement.matches(number+"\\."+number+"\\.(x|"+number+")"))return false;
        String minimum=requirement.endsWith(".x")?requirement.substring(0,requirement.length()-1)+"0":requirement;
        return sameMajor(installed,minimum)&&compare(installed,minimum)>=0;
    }
    /** Retained for callers compiled against the earlier API. */
    public static boolean supportsBranch(String installed,String requirement){return supportsRequirement(installed,requirement);}
    public static int compare(String a,String b){
        String[] aa=a.split("\\+",2)[0].split("-",2),bb=b.split("\\+",2)[0].split("-",2);
        String[] av=aa[0].split("\\."),bv=bb[0].split("\\.");if(av.length!=3||bv.length!=3)throw new IllegalArgumentException("SemVer required");
        for(int i=0;i<3;i++){int c=new java.math.BigInteger(av[i]).compareTo(new java.math.BigInteger(bv[i]));if(c!=0)return c;}
        if(aa.length!=bb.length)return aa.length==1?1:-1;if(aa.length==1)return 0;
        String[] ap=aa[1].split("\\."),bp=bb[1].split("\\.");for(int i=0;i<Math.min(ap.length,bp.length);i++){boolean an=ap[i].matches("[0-9]+"),bn=bp[i].matches("[0-9]+");int c=an&&bn?new java.math.BigInteger(ap[i]).compareTo(new java.math.BigInteger(bp[i])):an!=bn?(an?-1:1):ap[i].compareTo(bp[i]);if(c!=0)return c;}return Integer.compare(ap.length,bp.length);
    }
}
