package dev.abros.anthub.server;
import com.google.gson.JsonObject;
import java.util.UUID;
/** Optional read-only adapter. Never grants permissions when the API is absent. */
public final class LuckPermsAdapter {
    public static JsonObject profile(UUID id){
        JsonObject result=new JsonObject();JsonObject capabilities=new JsonObject();capabilities.addProperty("anthub.admin",false);capabilities.addProperty("anthub.events",false);capabilities.addProperty("anthub.auth.reset",false);capabilities.addProperty("anthub.vote.protected",false);result.add("capabilities",capabilities);
        try{
            Object api=Class.forName("net.luckperms.api.LuckPermsProvider").getMethod("get").invoke(null);
            Object manager=Class.forName("net.luckperms.api.LuckPerms").getMethod("getUserManager").invoke(api);
            Object user=Class.forName("net.luckperms.api.model.user.UserManager").getMethod("getUser",UUID.class).invoke(manager,id);if(user==null)return result;
            Class<?> userType=Class.forName("net.luckperms.api.model.user.User");result.addProperty("primaryGroup",String.valueOf(userType.getMethod("getPrimaryGroup").invoke(user)));
            Object cached=userType.getMethod("getCachedData").invoke(user);Class<?> cachedType=Class.forName("net.luckperms.api.cacheddata.CachedDataManager");
            Object metadata=cachedType.getMethod("getMetaData").invoke(cached);Class<?> metaType=Class.forName("net.luckperms.api.cacheddata.CachedMetaData");
            for(String key:new String[]{"Prefix","Suffix"}){Object v=metaType.getMethod("get"+key).invoke(metadata);if(v!=null)result.addProperty(key.toLowerCase(),v.toString().substring(0,Math.min(512,v.toString().length())));}
            Object permission=cachedType.getMethod("getPermissionData").invoke(cached);Class<?> permissionType=Class.forName("net.luckperms.api.cacheddata.CachedPermissionData");
            for(String key:new String[]{"anthub.admin","anthub.events","anthub.auth.reset","anthub.vote.protected","anthub.stats.edit","anthub.stats.view","anthub.announce","anthub.maintenance","anthub.restart","anthub.reports","anthub.diagnostics"}){Object tristate=permissionType.getMethod("checkPermission",String.class).invoke(permission,key);capabilities.addProperty(key,tristate.toString().equals("TRUE"));}
        result.addProperty("available",true);
        }catch(ReflectiveOperationException|LinkageError|RuntimeException ignored){result.remove("available");for(String key:capabilities.keySet())capabilities.addProperty(key,false); /* Partial API failure must never grant rights or remove vote protection. */ }
        return result;
    }
    public static com.google.gson.JsonArray roles(){
        var rows=new com.google.gson.JsonArray();
        try{
            Object api=Class.forName("net.luckperms.api.LuckPermsProvider").getMethod("get").invoke(null);
            Object manager=Class.forName("net.luckperms.api.LuckPerms").getMethod("getGroupManager").invoke(api);
            var groups=(java.util.Set<?>)Class.forName("net.luckperms.api.model.group.GroupManager").getMethod("getLoadedGroups").invoke(manager);
            var type=Class.forName("net.luckperms.api.model.group.Group");var cachedType=Class.forName("net.luckperms.api.cacheddata.CachedDataManager");var permissionType=Class.forName("net.luckperms.api.cacheddata.CachedPermissionData");
            var sorted=new java.util.TreeMap<String,Object>();for(var group:groups)sorted.put(String.valueOf(type.getMethod("getName").invoke(group)),group);
            for(var entry:sorted.entrySet()){if(rows.size()>=40)break;var row=new JsonObject();row.addProperty("name",entry.getKey());var caps=new JsonObject();var cached=type.getMethod("getCachedData").invoke(entry.getValue());var permission=cachedType.getMethod("getPermissionData").invoke(cached);
                for(String key:new String[]{"anthub.admin","anthub.events","anthub.auth.reset","anthub.vote.protected","anthub.stats.edit","anthub.stats.view","anthub.announce","anthub.maintenance","anthub.restart","anthub.reports","anthub.diagnostics"})caps.addProperty(key,permissionType.getMethod("checkPermission",String.class).invoke(permission,key).toString().equals("TRUE"));if(caps.get("anthub.admin").getAsBoolean())for(String right:ServerCommands.RIGHTS)caps.addProperty(right,true);row.add("capabilities",caps);rows.add(row);
            }
        }catch(ReflectiveOperationException|LinkageError|RuntimeException unavailable){return new com.google.gson.JsonArray();}
        return rows;
    }

}
