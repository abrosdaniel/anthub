package dev.abros.anthub.client;
import dev.abros.anthub.core.Json;
import net.minecraft.client.Minecraft;
import java.nio.file.*;
import java.util.*;
/** Client-only palette. Player colours, item textures and success/error semantics stay intact. */
final class UiPalette {
 private record Palette(String id,String name,int surface,int accent,int secondary){}
 private static final List<Palette> THEMES=List.of(
  new Palette("golden-dark","Golden Dark",0x24282B,0xE2BE75,0x83C6C4),
  new Palette("love-pink","Love Pink",0xF5DEE8,0xA63959,0x965369),
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
 static boolean light(){return selected()==1;}
 static int inputSurface(){return light()?0xFFF9EBF1:color(0xFF13202A);}
 static int insetSurface(){return light()?0xFFE2B7C8:color(0xFF102338);}
 static int scrollTrack(){var p=THEMES.get(selected());return 0xFF000000|(light()?0xE2B7C8:blend(p.surface,0x030507,0.3f));}
 static int scrollThumb(){var p=THEMES.get(selected());return 0xFF000000|p.accent;}
 static String id(){return THEMES.get(selected()).id;}
 static int color(int original){load();return colors.computeIfAbsent(original,UiPalette::tint);}
 private static int tint(int original){int rgb=original&0xFFFFFF,alpha=original&0xFF000000;var theme=THEMES.get(index);
  if(Set.of(0xE2BE75,0xFFD166,0xE7C77B,0xE9C578).contains(rgb))return alpha|theme.accent;
  if(Set.of(0x83C6C4,0x85CFBD,0xD5F4EB,0x253E45,0x324B59,0x283E4A).contains(rgb)){int value=rgb==0xD5F4EB?blend(theme.secondary,0xFFFFFF,0.6f):rgb==0x253E45||rgb==0x324B59||rgb==0x283E4A?blend(theme.surface,theme.secondary,0.24f):theme.secondary;return alpha|value;}
  if(index==1){
   // Text may pass through both a component and the shared renderer. Keep resolved colours stable.
   if(Set.of(0x246041,0x295D86,0x97452F,0x714D91,0x382431,0x684252,0xA12C42,0xA63959,0x965369).contains(rgb))return original;
   if(rgb==0xD5F4EB)return alpha|0x382431;
   if(rgb==0xF0A77C)return alpha|0x97452F;
   if(rgb==0x82B6F2)return alpha|0x295D86;
   if(rgb==0xB49AE8)return alpha|0x714D91;
   if(Set.of(0xFFFFFF,0xE0E9EE,0xF2F6F8,0xE7EDF1,0xCCD4DE,0xD7E2EC,0xE0E8F0,0xD8E9F2,0xD8E8F0,0xEEEEEE).contains(rgb))return alpha|0x382431;
   if(Set.of(0xA4B5C0,0xBAC7D2,0x99ADB9,0x8FA6B5,0x82909C,0x687580,0xB6C2CC,0x96A6B5,0xAFBFCD,0xBBBBBB,0xBAC6D2,0xBAC9D3,0xCCCCCC,0xC1CED8,0x8DA7B8,0x879BAD,0x91A7B7,0xA9B9C8,0xAEBBC8,0x999999,0xAAAAAA,0xABB5BE).contains(rgb))return alpha|0x684252;
   if(Set.of(0x79CBA6,0x8CBFA2).contains(rgb))return alpha|0x246041;
   if(Set.of(0xEF7777,0xDB7777).contains(rgb))return alpha|0xA12C42;
  }
  int red=(rgb>>16)&255,green=(rgb>>8)&255,blue=rgb&255;int max=Math.max(red,Math.max(green,blue)),min=Math.min(red,Math.min(green,blue));
  // Graphite surfaces only: leave red warnings, green success and text colours unchanged.
  if(max>=12&&max<=125&&(blue>=red&&green>=red||max-min<14)){
   float brightness=(red+green+blue)/3f;float base=(((theme.surface>>16)&255)+((theme.surface>>8)&255)+(theme.surface&255))/3f;
   if(index==1)return alpha|blend(theme.surface,0xBA839C,Math.min(0.38f,brightness/230f));
   int result=brightness>=base?blend(theme.surface,0xAAB6C1,Math.min(0.55f,(brightness-base)/150f)):blend(theme.surface,0x030507,Math.min(0.9f,(base-brightness)/base));return alpha|result;
  }return original;
 }
 private static int blend(int a,int b,float t){int out=0;for(int shift=0;shift<=16;shift+=8)out|=((int)(((a>>shift)&255)*(1-t)+((b>>shift)&255)*t))<<shift;return out;}
}
