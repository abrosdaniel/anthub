package dev.abros.anthub.client;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
/** Short contextual choice without losing the current list or selection. */
final class ChoicePopup extends ScrollScreen implements CommunityScreen.Receiver {
 private int selected=-1;private int anchorX=-1,anchorY,anchorWidth,anchorTop;private final Screen parent;private final List<String> labels;private final Consumer<Integer> choose;
 ChoicePopup(Screen parent,String title,List<String> labels,Consumer<Integer> choose){super(Component.literal(title));this.parent=parent;this.labels=List.copyOf(labels);this.choose=choose;if(parent!=null)for(var child:parent.children())if(child instanceof net.minecraft.client.gui.components.AbstractWidget widget&&widget.getMessage().getString().endsWith(" ▾")&&(widget.isFocused()||widget.isHovered())){anchor(widget);break;}}
 ChoicePopup(Screen parent,String title,List<String> labels,Consumer<Integer> choose,net.minecraft.client.gui.components.AbstractWidget anchor){this(parent,title,labels,choose);anchor(anchor);}
 private void anchor(net.minecraft.client.gui.components.AbstractWidget widget){anchorX=widget.getX();anchorTop=widget.getY();anchorY=widget.getY()+widget.getHeight()+2;anchorWidth=widget.getWidth();}
 ChoicePopup anchorLabel(String label){if(parent!=null)for(var child:parent.children())if(child instanceof net.minecraft.client.gui.components.AbstractWidget widget&&widget.getMessage().getString().equals(label)){anchor(widget);break;}return this;}
 ChoicePopup attach(net.minecraft.client.gui.components.AbstractWidget widget){anchor(widget);rebuildWidgets();return this;}
 ChoicePopup current(int index){selected=index;return this;}
 private int panelLeft(){return anchorX<0?(width-panelWidth())/2:Math.max(8,Math.min(anchorX,width-panelWidth()-8));}
 private int panelWidth(){if(anchorX>=0)return Math.min(width-16,Math.max(anchorWidth,180));return Math.min(310,width-24);}private int panelHeight(){return Math.min(height-12,labels.size()*24+(anchorX<0?64:8));}private int panelTop(){return anchorX<0?(height-panelHeight())/2:Math.max(8,Math.min(anchorY+panelHeight()<=height-8?anchorY:anchorTop-panelHeight()-2,height-panelHeight()-8));}
 @Override protected void init(){int w=panelWidth(),x=panelLeft(),y=panelTop()+(anchorX<0?28:4);scrollArea(labels.size(),y,panelTop()+panelHeight()-(anchorX<0?34:4),24,x+w-7);for(int i=firstRow;i<Math.min(labels.size(),firstRow+visibleRows);i++){int index=i;addRenderableWidget(new UiMenuItem(labels.get(i),x+10,y+(i-firstRow)*24,w-24,i==selected,()->{minecraft.setScreen(parent);if(index!=selected)choose.accept(index);}));}if(anchorX<0)addRenderableWidget(Button.builder(Component.literal("Отмена"),b->onClose()).bounds(x+10,panelTop()+panelHeight()-28,w-20,20).build());}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){if(anchorX<0)g.fill(0,0,width,height,UiPalette.color(0xAA090E14));int w=panelWidth(),left=panelLeft();UiTheme.panel(g,left,panelTop(),w,panelHeight(),UiPalette.color(0xFF101820));}
 @Override public void render(GuiGraphics g,int x,int y,float d){ModalLayer.render(parent,this,g,d,()->{super.render(g,x,y,d);if(anchorX<0)UiHeading.dialog(g,font,title,panelLeft()+10,panelTop(),panelWidth()-20);});}
 @Override public boolean mouseClicked(double x,double y,int button){if(anchorX>=0&&(x<panelLeft()||x>panelLeft()+panelWidth()||y<panelTop()||y>panelTop()+panelHeight())){onClose();return true;}return super.mouseClicked(x,y,button);}
 Screen parentScreen(){return parent;}
 void invalidate(){if(parent instanceof CommunityScreen screen)screen.invalidate();}
 @Override public void receiveCommunity(com.google.gson.JsonObject response){if(parent instanceof CommunityScreen screen)screen.receive(response);else if(parent instanceof CommunityScreen.Receiver receiver)receiver.receiveCommunity(response);}
 @Override public void tick(){if(parent!=null)parent.tick();}
 @Override public void onClose(){minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
