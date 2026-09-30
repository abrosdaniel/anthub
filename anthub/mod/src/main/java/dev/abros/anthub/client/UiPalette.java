package dev.abros.anthub.client;
import dev.abros.anthub.core.Json;
import net.minecraft.client.Minecraft;
import java.nio.file.*;
import java.util.*;
/** Client-only palette. Player colours, item textures and success/error semantics stay intact. */
final class UiPalette {
 private record Palette(String id,String name,int surface,int accent,int secondary){}
 private static final List<Palette> THEMES=List.of(
  new Palette("golden-dark","Golden Dark",0x1B2832,0xE2BE75,0x83C6C4),
  new Palette("love-pink","Love Pink",0x35212E,0xFFADC8,0xE58CA4),
  new Palette("obsidian","Obsidian",0x211D32,0xB9A3F2,0x978BDD),
  new Palette("create-stuff","Create Stuff",0x302922,0xE4B57A,0x88BBD9),
  new Palette("mine-main","Mine Main",0x282B25,0xC6DAA3,0x83B06A));
 private static final List<String> NAMES=THEMES.stream().map(Palette::name).toList();
 private static boolean loaded;private static int index;private static final Map<Integer,Integer> colors=new HashMap<>();
 private UiPalette(){}
 private static Path file(){return Minecraft.getInstance().gameDirectory.toPath().resolve("anthub/ui-theme.json");}
 private static void load(){if(loaded)return;loaded=true;try{if(Files.exists(file())){String id=Json.opt(Json.read(file()),"theme","golden-dark");for(int n=0;n<THEMES.size();n++)if(THEMES.get(n).id.equals(id))index=n;}}catch(Exception ignored){}}
 static List<String> names(){return NAMES;}
 static void reload(){loaded=false;index=0;colors.clear();load();}
 static int selected(){load();return index;}
 static String name(){return names().get(selected());}
 static void select(int next)throws java.io.IOException {if(next<0||next>=THEMES.size())throw new IllegalArgumentException("Unknown theme");Json.write(file(),Map.of("theme",THEMES.get(next).id));preview(next);}
 static void preview(int next){if(next<0||next>=THEMES.size())throw new IllegalArgumentException("Unknown theme");loaded=true;index=next;colors.clear();}
 static int color(int original){load();if(index==0)return original;return colors.computeIfAbsent(original,UiPalette::tint);}
 private static int tint(int original){int rgb=original&0xFFFFFF,alpha=original&0xFF000000;var theme=THEMES.get(index);
  if(Set.of(0xE2BE75,0xFFD166,0xE7C77B,0xE9C578).contains(rgb))return alpha|theme.accent;
  if(Set.of(0x83C6C4,0x85CFBD,0xD5F4EB,0x253E45,0x324B59,0x283E4A).contains(rgb)){int value=rgb==0xD5F4EB?blend(theme.secondary,0xFFFFFF,0.6f):rgb==0x253E45||rgb==0x324B59||rgb==0x283E4A?blend(theme.surface,theme.secondary,0.24f):theme.secondary;return alpha|value;}
  int red=(rgb>>16)&255,green=(rgb>>8)&255,blue=rgb&255;int max=Math.max(red,Math.max(green,blue)),min=Math.min(red,Math.min(green,blue));
  // Graphite surfaces only: leave red warnings, green success and text colours unchanged.
  if(max>=12&&max<=125&&(blue>=red&&green>=red||max-min<14)){
   float brightness=(red+green+blue)/3f;float base=(((theme.surface>>16)&255)+((theme.surface>>8)&255)+(theme.surface&255))/3f;
   int result=brightness>=base?blend(theme.surface,0xAAB6C1,Math.min(0.55f,(brightness-base)/150f)):blend(theme.surface,0x030507,Math.min(0.9f,(base-brightness)/base));return alpha|result;
  }return original;
 }
 private static int blend(int a,int b,float t){int out=0;for(int shift=0;shift<=16;shift+=8)out|=((int)(((a>>shift)&255)*(1-t)+((b>>shift)&255)*t))<<shift;return out;}
}
