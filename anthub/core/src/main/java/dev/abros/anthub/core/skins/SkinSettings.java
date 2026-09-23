package dev.abros.anthub.core.skins;
public record SkinSettings(boolean enabled,int maxFileSizeMiB,int maxSkinsPerPlayer,String mojangFallback){
 public SkinSettings{if(maxFileSizeMiB<1||maxFileSizeMiB>16||maxSkinsPerPlayer<1||maxSkinsPerPlayer>5||!java.util.Set.of("false","UUID","nickname").contains(mojangFallback))throw new IllegalArgumentException("Invalid skins settings");}
 public int maxBytes(){return maxFileSizeMiB*1024*1024;}
}
