package dev.abros.anthub.client;
import net.minecraft.network.chat.*;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.regex.*;
public final class Markdown {
 private static final Pattern INLINE=Pattern.compile("(\\*\\*(.+?)\\*\\*|\\*(.+?)\\*|`([^`]+)`|\\[([^\\]]+)\\]\\((https://[^\\s)]+)\\))");
 public static List<Component> parse(String source){
  List<Component> lines=new ArrayList<>();boolean code=false;boolean front=source.startsWith("---\n");int index=0;
  for(String line:source.split("\\R",-1)){
   if(front){if(index++>0&&line.equals("---"))front=false;continue;}
   if(line.startsWith("```")){code=!code;continue;}
   if(code){lines.add(Component.literal(line).withStyle(Style.EMPTY.withColor(0xD7D7B9).withFont(ResourceLocation.withDefaultNamespace("uniform"))));continue;}
   if(line.matches("#{1,6} .*")){lines.add(inline(line.replaceFirst("^#+ ","")).withStyle(ChatFormatting.GOLD,ChatFormatting.BOLD));continue;}
   if(line.startsWith("> ")){lines.add(inline("│ "+line.substring(2)).withStyle(ChatFormatting.GRAY,ChatFormatting.ITALIC));continue;}
   lines.add(inline(line.replaceFirst("^[-*] ","• ")));
  }return lines;
 }
 private static MutableComponent inline(String source){
  MutableComponent text=Component.empty();Matcher m=INLINE.matcher(source);int offset=0;
  while(m.find()){text.append(Component.literal(source.substring(offset,m.start())));MutableComponent part;
   if(m.group(2)!=null)part=Component.literal(m.group(2)).withStyle(ChatFormatting.BOLD);
   else if(m.group(3)!=null)part=Component.literal(m.group(3)).withStyle(ChatFormatting.ITALIC);
   else if(m.group(4)!=null)part=Component.literal(m.group(4)).withStyle(ChatFormatting.YELLOW);
   else part=Component.literal(m.group(5)).withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA).withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL,m.group(6))).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,Component.literal(m.group(6)))));
   text.append(part);offset=m.end();
  }return text.append(Component.literal(source.substring(offset)));
 }
}
