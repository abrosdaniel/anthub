package dev.abros.anthub.core;
public final class Versions {
    private Versions(){}
    /** Public AntHub releases are A.B.C; A is the compatibility boundary. */
    public static boolean sameMajor(String installed,String required){
        String pattern="(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)";
        return installed.matches(pattern)&&required.matches(pattern)&&installed.split("\\.")[0].equals(required.split("\\.")[0]);
    }
    /** Project requirements name a release line, not a concrete mod version. */
    public static boolean supportsBranch(String installed,String branch){
        return installed!=null&&branch!=null&&branch.matches("(0|[1-9][0-9]*)\\.x")
            &&sameMajor(installed,branch.substring(0,branch.length()-2)+".0.0");
    }
    public static int compare(String a,String b){
        String[] aa=a.split("\\+",2)[0].split("-",2),bb=b.split("\\+",2)[0].split("-",2);
        String[] av=aa[0].split("\\."),bv=bb[0].split("\\.");if(av.length!=3||bv.length!=3)throw new IllegalArgumentException("SemVer required");
        for(int i=0;i<3;i++){int c=new java.math.BigInteger(av[i]).compareTo(new java.math.BigInteger(bv[i]));if(c!=0)return c;}
        if(aa.length!=bb.length)return aa.length==1?1:-1;if(aa.length==1)return 0;
        String[] ap=aa[1].split("\\."),bp=bb[1].split("\\.");for(int i=0;i<Math.min(ap.length,bp.length);i++){boolean an=ap[i].matches("[0-9]+"),bn=bp[i].matches("[0-9]+");int c=an&&bn?new java.math.BigInteger(ap[i]).compareTo(new java.math.BigInteger(bp[i])):an!=bn?(an?-1:1):ap[i].compareTo(bp[i]);if(c!=0)return c;}return Integer.compare(ap.length,bp.length);
    }
}
