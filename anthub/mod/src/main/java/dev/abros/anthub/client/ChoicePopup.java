package dev.abros.anthub.client;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
/** Short contextual choice without losing the current list or selection. */
final class ChoicePopup extends ScrollScreen implements CommunityScreen.Receiver {
 private final Screen parent;private final List<String> labels;private final Consumer<Integer> choose;
 ChoicePopup(Screen parent,String title,List<String> labels,Consumer<Integer> choose){super(Component.literal(title));this.parent=parent;this.labels=List.copyOf(labels);this.choose=choose;}
 private int panelWidth(){return Math.min(310,width-24);}private int panelHeight(){return Math.min(height-12,labels.size()*24+64);}private int panelTop(){return (height-panelHeight())/2;}
 @Override protected void init(){int w=panelWidth(),x=(width-w)/2,y=panelTop()+28;scrollArea(labels.size(),y,panelTop()+panelHeight()-34,24,x+w-7);for(int i=firstRow;i<Math.min(labels.size(),firstRow+visibleRows);i++){int index=i;addRenderableWidget(Button.builder(Component.literal(labels.get(i)),b->{minecraft.setScreen(parent);choose.accept(index);}).bounds(x+10,y+(i-firstRow)*24,w-24,20).build());}addRenderableWidget(Button.builder(Component.literal("Отмена"),b->onClose()).bounds(x+10,panelTop()+panelHeight()-28,w-20,20).build());}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){g.fill(0,0,width,height,AccessibilityScreen.background(0xAA090E14));int w=panelWidth(),left=(width-w)/2;g.fill(left,panelTop(),left+w,panelTop()+panelHeight(),AccessibilityScreen.background(0xF51B252E));}
 @Override public void render(GuiGraphics g,int x,int y,float d){ModalLayer.render(parent,this,g,d,()->{super.render(g,x,y,d);g.drawCenteredString(font,font.plainSubstrByWidth(title.getString(),panelWidth()-20),width/2,panelTop()+10,0xE2BE75);});}
 Screen parentScreen(){return parent;}
 void invalidate(){if(parent instanceof CommunityScreen screen)screen.invalidate();}
 @Override public void receiveCommunity(com.google.gson.JsonObject response){if(parent instanceof CommunityScreen screen)screen.receive(response);else if(parent instanceof CommunityScreen.Receiver receiver)receiver.receiveCommunity(response);}
 @Override public void tick(){if(!ServerMenuClient.available())minecraft.setScreen(null);else if(parent instanceof CommunityScreen)parent.tick();}
 @Override public void onClose(){minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
