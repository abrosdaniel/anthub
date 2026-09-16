package dev.abros.anthub.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.*;
public class TextScreen extends ScrollScreen {
    private final boolean literal;private final int footer;private final Screen parent;private final String text;private List<FormattedCharSequence> lines=List.of();
    public TextScreen(Screen parent,Component title,String text){this(parent,title,text,40);}
    public TextScreen(Screen parent,Component title,String text,boolean literal){this(parent,title,text,40,literal);}
    protected TextScreen(Screen parent,Component title,String text,int footer){this(parent,title,text,footer,false);}
    protected TextScreen(Screen parent,Component title,String text,int footer,boolean literal){super(title);this.parent=parent;this.text=text;this.footer=footer;this.literal=literal;}
    @Override protected void init(){lines=new ArrayList<>();for(var paragraph:literal?text.lines().map(Component::literal).toList():Markdown.parse(text)){lines.addAll(font.split(paragraph,Math.min(600,width-40)));if(paragraph.getString().isEmpty())lines.add(FormattedCharSequence.EMPTY);}scrollArea(lines.size(),32,height-footer,12,(width+Math.min(600,width-40))/2+5);addRenderableWidget(Button.builder(Client.tr("back"),b->onClose()).bounds(width/2-100,height-26,200,20).build());}
    @Override protected void rowsChanged(){}
    @Override public void render(GuiGraphics g,int mx,int my,float pt){super.render(g,mx,my,pt);g.drawCenteredString(font,title,width/2,12,0xE2BE75);int x=(width-Math.min(600,width-40))/2;for(int i=firstRow;i<lines.size()&&32+(i-firstRow)*12<height-footer;i++)g.drawString(font,lines.get(i),x,32+(i-firstRow)*12,0xEEEEEE);}
    @Override public boolean mouseClicked(double x,double y,int button){int left=(width-Math.min(600,width-40))/2;int row=(int)((y-32)/12)+firstRow;if(button==0&&x>=left&&x<width-left&&y>=32&&y<height-footer&&row>=0&&row<lines.size()){var style=font.getSplitter().componentStyleAtWidth(lines.get(row),(int)x-left);if(style!=null&&style.getClickEvent()!=null)return handleComponentClicked(style);}return super.mouseClicked(x,y,button);}
    @Override public void onClose(){minecraft.setScreen(parent);}
}
