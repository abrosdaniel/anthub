package dev.abros.anthub.compat;
import org.spongepowered.asm.mixin.extensibility.*;
import org.objectweb.asm.tree.ClassNode;
import java.util.*;
/** Only probes resources at mixin discovery time: ModList is not initialized yet. */
public final class CompatibilityMixinPlugin implements IMixinConfigPlugin {
 public void onLoad(String p){} public String getRefMapperConfig(){return null;}
 public boolean shouldApplyMixin(String target,String mixin){return getClass().getClassLoader().getResource(target.replace('.','/')+".class")!=null;}
 public void acceptTargets(Set<String>a,Set<String>b){} public List<String>getMixins(){return null;}
 public void preApply(String t,ClassNode n,String m,IMixinInfo i){} public void postApply(String t,ClassNode n,String m,IMixinInfo i){}
}
