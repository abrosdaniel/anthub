package dev.abros.anthub.client;

import dev.abros.anthub.core.CommunityLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.Entity;
import java.lang.reflect.*;
import net.neoforged.fml.ModList;

/** Isolated World Map camera adapter. Probe before use; restore the user's camera setting on exit. */
final class XaeroMapBridge {
 private static Screen opened;private static Field attached;private static boolean previousAttached;
 private static Boolean supported;
 static boolean available(){
  if(supported!=null)return supported;
  if(!ModList.get().isLoaded("xaeroworldmap"))return supported=false;
  try{
   Class<?> gui=Class.forName("xaero.map.gui.GuiMap"),processor=Class.forName("xaero.map.MapProcessor"),session=Class.forName("xaero.map.WorldMapSession");
   session.getMethod("getCurrentSession");session.getMethod("getMapProcessor");processor.getMethod("isMapWorldUsable");processor.getMethod("getMapWorld").getReturnType().getMethod("getCurrentDimensionId");
   gui.getConstructor(Screen.class,Screen.class,processor,Entity.class);
   field(gui,"cameraX",double.class);field(gui,"cameraZ",double.class);field(gui,"shouldResetCameraPos",boolean.class);
   if(!Modifier.isStatic(field(gui,"attachedCamera",boolean.class).getModifiers()))return supported=false;
   return supported=true;
  }catch(ReflectiveOperationException|RuntimeException|LinkageError unsupported){return supported=false;}
 }
 static void open(Screen parent,CommunityLocation place)throws ReflectiveOperationException{
  var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)throw new IllegalStateException("Мир ещё не загружен.");
  if(!mc.level.dimension().location().toString().equals(place.dimension()))throw new IllegalStateException("Это место в другом измерении. Перейдите в него или сохраните метку через Xaero Minimap.");
  Class<?> sessionType=Class.forName("xaero.map.WorldMapSession"),processorType=Class.forName("xaero.map.MapProcessor"),guiType=Class.forName("xaero.map.gui.GuiMap");
  Object session=sessionType.getMethod("getCurrentSession").invoke(null);if(session==null)throw new IllegalStateException("Карта ещё загружается.");
  Object processor=sessionType.getMethod("getMapProcessor").invoke(session);
  if(!(boolean)processorType.getMethod("isMapWorldUsable").invoke(processor))throw new IllegalStateException("Карта ещё загружается.");
  Object world=processorType.getMethod("getMapWorld").invoke(processor);
  Object dimension=world.getClass().getMethod("getCurrentDimensionId").invoke(world);
  if(!mc.level.dimension().equals(dimension))throw new IllegalStateException("Выберите текущее измерение в Xaero World Map и повторите.");
  // Xaero exposes the screen constructor but no public camera-position method.
  Field cameraX=field(guiType,"cameraX",double.class),cameraZ=field(guiType,"cameraZ",double.class),reset=field(guiType,"shouldResetCameraPos",boolean.class),follow=field(guiType,"attachedCamera",boolean.class);
  var screen=(Screen)guiType.getConstructor(Screen.class,Screen.class,processorType,Entity.class).newInstance(parent,parent,processor,mc.player);
  boolean wasAttached=follow.getBoolean(null);mc.setScreen(screen);
  try{cameraX.setDouble(screen,place.x()+0.5);cameraZ.setDouble(screen,place.z()+0.5);reset.setBoolean(screen,false);follow.setBoolean(null,false);opened=screen;attached=follow;previousAttached=wasAttached;}
  catch(ReflectiveOperationException ex){follow.setBoolean(null,wasAttached);mc.setScreen(parent);throw ex;}
 }
 private static Field field(Class<?> owner,String name,Class<?> expected)throws ReflectiveOperationException{var field=owner.getDeclaredField(name);if(field.getType()!=expected)throw new NoSuchFieldException(name);field.setAccessible(true);return field;}
 static void tick(){if(opened!=null&&Minecraft.getInstance().screen!=opened){try{attached.setBoolean(null,previousAttached);}catch(ReflectiveOperationException ignored){}finally{opened=null;attached=null;}}}
}
