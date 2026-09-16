package dev.abros.anthub.core;

import java.util.ArrayList;
import java.util.List;

/** Display-only legacy formatting: no click events, commands or external resources. */
public final class LegacyText {
    private LegacyText() {}
    public record Span(String text,Integer color,boolean bold,boolean italic,boolean underlined,boolean strike,boolean obfuscated) {}
    private static final int[] COLORS={0x000000,0x0000AA,0x00AA00,0x00AAAA,0xAA0000,0xAA00AA,0xFFAA00,0xAAAAAA,0x555555,0x5555FF,0x55FF55,0x55FFFF,0xFF5555,0xFF55FF,0xFFFF55,0xFFFFFF};
    public static List<Span> parse(String source){
        // LuckPerms may contain MiniMessage-style hexadecimal color tags.
        source=source.replaceAll("(?i)<#([0-9a-f]{6})>", "&#$1").replaceAll("(?i)</#[0-9a-f]{6}>", "&r");
        var result=new ArrayList<Span>();var text=new StringBuilder();Integer color=null;boolean bold=false,italic=false,underlined=false,strike=false,obfuscated=false;
        for(int i=0;i<source.length();){
            char marker=source.charAt(i);int consumed=0;Integer rgb=null;char code=0;
            if((marker=='&'||marker=='§')&&i+1<source.length()){
                code=Character.toLowerCase(source.charAt(i+1));int digit="0123456789abcdef".indexOf(code);
                if(digit>=0){rgb=COLORS[digit];consumed=2;}
                else if("klmnor".indexOf(code)>=0)consumed=2;
                else if(code=='#'&&i+8<=source.length()&&source.substring(i+2,i+8).matches("[a-fA-F0-9]{6}")){rgb=Integer.parseInt(source.substring(i+2,i+8),16);consumed=8;}
                else if(code=='x'&&i+14<=source.length()){
                    var hex=new StringBuilder();for(int n=0;n<6;n++){int at=i+2+n*2;if(source.charAt(at)!=marker||Character.digit(source.charAt(at+1),16)<0)break;hex.append(source.charAt(at+1));}
                    if(hex.length()==6){rgb=Integer.parseInt(hex.toString(),16);consumed=14;}
                }
            }
            if(consumed==0){text.append(marker);i++;continue;}
            if(!text.isEmpty()){result.add(new Span(text.toString(),color,bold,italic,underlined,strike,obfuscated));text.setLength(0);}
            if(rgb!=null||code=='r'){color=rgb;bold=italic=underlined=strike=obfuscated=false;}
            else switch(code){case 'k'->obfuscated=true;case 'l'->bold=true;case 'm'->strike=true;case 'n'->underlined=true;case 'o'->italic=true;default->{}}
            i+=consumed;
        }
        if(!text.isEmpty())result.add(new Span(text.toString(),color,bold,italic,underlined,strike,obfuscated));return List.copyOf(result);
    }
}
