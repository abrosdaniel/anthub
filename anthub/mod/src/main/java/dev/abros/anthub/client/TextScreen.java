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
    protected int panelTop(){return UiDialog.top(height,340);}
    protected int panelBottom(){return height-panelTop();}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.draw(g,width,Math.min(520,width-40),panelTop(),panelBottom());}
 @Override protected void init(){lines=new ArrayList<>();for(var paragraph:literal?text.lines().map(Component::literal).toList():Markdown.parse(text)){lines.addAll(font.split(paragraph,Math.min(520,width-40)));if(paragraph.getString().isEmpty())lines.add(FormattedCharSequence.EMPTY);}scrollArea(lines.size(),new dev.abros.anthub.core.NativeLayout.Box((width-Math.min(520,width-40))/2,panelTop()+32,Math.max(0,Math.min(520,width-40)),Math.max(0,(panelBottom()-footer)-(panelTop()+32))),12);UiActions.close(new dev.abros.anthub.core.NativeLayout.Box((width-Math.min(520,width-40))/2,panelBottom()-26,Math.min(520,width-40),20),this::addRenderableWidget,this::onClose);}
    @Override protected void rowsChanged(){}
    @Override public void render(GuiGraphics g,int mx,int my,float pt){UiDialog.render(parent,this,g,pt,()->{super.render(g,mx,my,pt);UiHeading.dialog(g,font,title,(width-(Math.min(520,width-40)))/2,panelTop(),Math.min(520,width-40));int x=(width-Math.min(520,width-40))/2;for(int i=firstRow;i<lines.size()&&panelTop()+32+(i-firstRow)*12<panelBottom()-footer;i++)Ui.text(g,font,lines.get(i),x,panelTop()+32+(i-firstRow)*12,UiPalette.color(0xEEEEEE));});}
    @Override public boolean mouseClicked(double x,double y,int button){int left=(width-Math.min(520,width-40))/2;int row=(int)((y-panelTop()-32)/12)+firstRow;if(button==0&&x>=left&&x<width-left&&y>=panelTop()+32&&y<panelBottom()-footer&&row>=0&&row<lines.size()){var style=font.getSplitter().componentStyleAtWidth(lines.get(row),(int)x-left);if(style!=null&&style.getClickEvent()!=null)return handleComponentClicked(style);}return super.mouseClicked(x,y,button);}
    @Override public void onClose(){minecraft.setScreen(parent);}
}
