package dev.abros.anthub.client;

import dev.abros.anthub.core.CommunityLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.neoforged.fml.ModList;
import java.lang.reflect.*;
import java.util.*;

/** Optional adapter: only public waypoint interfaces, no direct edits of Xaero files. */
final class XaeroBridge {
 private static Class<?> type(String name)throws ClassNotFoundException{return Class.forName(name);}
 private static Boolean supported;
 static boolean available(){
  if(supported!=null)return supported;
  if(!ModList.get().isLoaded("xaerominimap"))return supported=false;
  try{
   type("xaero.common.XaeroMinimapSession").getMethod("getCurrentSession");
   type("xaero.common.HudMod").getField("INSTANCE");
   var point=type("xaero.common.minimap.waypoints.Waypoint");
   point.getConstructor(int.class,int.class,int.class,String.class,String.class,int.class);
   point.getMethod("setTemporary",boolean.class);point.getMethod("setOneoffDestination",boolean.class);
   for(String name:List.of("getX","getY","getZ","getPurpose","isThirdParty","isServerWaypoint"))point.getMethod(name);
   type("xaero.common.gui.GuiAddWaypoint").getConstructor(type("xaero.common.HudMod"),type("xaero.hud.minimap.module.MinimapSession"),Screen.class,Screen.class,ArrayList.class,type("xaero.hud.path.XaeroPath"),type("xaero.hud.minimap.world.MinimapWorld"),String.class,boolean.class);
   return supported=true;
  }catch(ReflectiveOperationException|RuntimeException|LinkageError unsupported){return supported=false;}
 }
 private static Object call(Object target,String name,Object...args)throws ReflectiveOperationException{
  if(target==null)throw new IllegalStateException("Xaero ещё не подготовил карту этого сервера.");
  for(var method:target.getClass().getMethods())if(method.getName().equals(name)&&method.getParameterCount()==args.length){boolean matches=true;var types=method.getParameterTypes();for(int i=0;i<types.length;i++)if(args[i]!=null&&!types[i].isInstance(args[i])&&!(types[i]==boolean.class&&args[i] instanceof Boolean))matches=false;if(matches)return method.invoke(target,args);}
  throw new NoSuchMethodException(name);
 }
 static void edit(Screen parent,CommunityLocation place,int mode)throws ReflectiveOperationException{
  var current=type("xaero.common.XaeroMinimapSession").getMethod("getCurrentSession").invoke(null);
  Object session=call(current,"getWaypointsManager"),manager=call(session,"getWorldManager"),world=call(manager,"getAutoWorld");
  var key=ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(place.dimension()));
  if(!key.equals(call(world,"getDimId"))){
   Object root=call(call(world,"getContainer"),"getRoot"),path=call(root,"getPath"),helper=call(session,"getDimensionHelper");
   String directory=(String)call(helper,"getDimensionDirectoryName",key);Object destination=call(path,"resolve",directory);
   Object container=call(manager,"getWorldContainerNullable",destination);
   if(container==null)throw new IllegalStateException("Сначала посетите это измерение, чтобы Xaero определил его карту.");
   Object linked=call(container,"getFirstWorldConnectedTo",world);
   if(linked==null)throw new IllegalStateException("В Xaero не определена карта этого измерения. Выберите её после посещения измерения.");world=linked;
  }
  if(!key.equals(call(world,"getDimId")))throw new IllegalStateException("Измерение карты не совпадает с местом.");
  String set=(String)call(world,"getCurrentWaypointSetId");Object waypoint=null;
  // Reopen an existing point at this position, preserving the player's name/type/color.
  for(Object candidateSet:(Iterable<?>)call(world,"getIterableWaypointSets"))for(Object candidate:(Iterable<?>)call(candidateSet,"getWaypoints")){
   if((int)call(candidate,"getX")==place.x()&&(int)call(candidate,"getY")==place.y()&&(int)call(candidate,"getZ")==place.z()&&!(boolean)call(call(candidate,"getPurpose"),"isDeath")&&!(boolean)call(candidate,"isThirdParty")&&!(boolean)call(candidate,"isServerWaypoint")){waypoint=candidate;set=(String)call(candidateSet,"getName");break;}
  }
  boolean adding=waypoint==null;
  if(adding){String name=place.name().substring(0,Math.min(32,place.name().length()));waypoint=type("xaero.common.minimap.waypoints.Waypoint").getConstructor(int.class,int.class,int.class,String.class,String.class,int.class).newInstance(place.x(),place.y(),place.z(),name,"A",14);call(waypoint,"setTemporary",mode==1);call(waypoint,"setOneoffDestination",mode==0);}
  Object mod=type("xaero.common.HudMod").getField("INSTANCE").get(null),rootPath=call(call(call(world,"getContainer"),"getRoot"),"getPath");
  var points=new ArrayList<>();points.add(waypoint);
  var constructor=type("xaero.common.gui.GuiAddWaypoint").getConstructor(type("xaero.common.HudMod"),type("xaero.hud.minimap.module.MinimapSession"),Screen.class,Screen.class,ArrayList.class,type("xaero.hud.path.XaeroPath"),type("xaero.hud.minimap.world.MinimapWorld"),String.class,boolean.class);
  Minecraft.getInstance().setScreen((Screen)constructor.newInstance(mod,session,parent,parent,points,rootPath,world,set,adding));
 }
}
