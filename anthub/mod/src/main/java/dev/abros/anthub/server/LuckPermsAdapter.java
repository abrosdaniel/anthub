package dev.abros.anthub.server;
import com.google.gson.JsonObject;
import java.util.UUID;
/** Optional read-only adapter. Never grants permissions when the API is absent. */
public final class LuckPermsAdapter {
    public static JsonObject profile(UUID id){
        JsonObject result=new JsonObject();JsonObject capabilities=new JsonObject();capabilities.addProperty("anthub.admin",false);capabilities.addProperty("anthub.events",false);capabilities.addProperty("anthub.auth.reset",false);result.add("capabilities",capabilities);
        try{
            Object api=Class.forName("net.luckperms.api.LuckPermsProvider").getMethod("get").invoke(null);
            Object manager=Class.forName("net.luckperms.api.LuckPerms").getMethod("getUserManager").invoke(api);
            Object user=Class.forName("net.luckperms.api.model.user.UserManager").getMethod("getUser",UUID.class).invoke(manager,id);if(user==null)return result;
            Class<?> userType=Class.forName("net.luckperms.api.model.user.User");result.addProperty("primaryGroup",String.valueOf(userType.getMethod("getPrimaryGroup").invoke(user)));
            Object cached=userType.getMethod("getCachedData").invoke(user);Class<?> cachedType=Class.forName("net.luckperms.api.cacheddata.CachedDataManager");
            Object metadata=cachedType.getMethod("getMetaData").invoke(cached);Class<?> metaType=Class.forName("net.luckperms.api.cacheddata.CachedMetaData");
            for(String key:new String[]{"Prefix","Suffix"}){Object v=metaType.getMethod("get"+key).invoke(metadata);if(v!=null)result.addProperty(key.toLowerCase(),v.toString().substring(0,Math.min(512,v.toString().length())));}
            Object permission=cachedType.getMethod("getPermissionData").invoke(cached);Class<?> permissionType=Class.forName("net.luckperms.api.cacheddata.CachedPermissionData");
            for(String key:new String[]{"anthub.admin","anthub.events","anthub.auth.reset"}){Object tristate=permissionType.getMethod("checkPermission",String.class).invoke(permission,key);capabilities.addProperty(key,tristate.toString().equals("TRUE"));}
        }catch(ReflectiveOperationException|LinkageError ignored){ /* Missing or incompatible optional API leaves conservative defaults. */ }
        return result;
    }
}
