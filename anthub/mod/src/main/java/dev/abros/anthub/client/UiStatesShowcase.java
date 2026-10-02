package dev.abros.anthub.client;
import net.minecraft.client.gui.*;import net.minecraft.client.gui.screens.Screen;import net.minecraft.network.chat.Component;
/** Development catalogue of semantic states, displayed through the production components. */
final class UiStatesShowcase extends Screen {
 private final Screen parent;UiStatesShowcase(Screen parent){super(Component.literal("UI Kit · состояния"));this.parent=parent;}
 private UiDialog layout(){return UiDialog.fit(width,height,420,320);}
 @Override protected void init(){var l=layout();UiActions.close(new dev.abros.anthub.core.NativeLayout.Box(l.footer().x(),l.footer().bottom()-20,l.footer().width(),20),this::addRenderableWidget,this::onClose);}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){var l=layout();UiDialog.draw(g,width,l.frame().width(),l.frame().y(),l.frame().bottom());}
 @Override public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);var l=layout();UiHeading.dialog(g,font,title,l.frame().x(),l.frame().y(),l.frame().width());int stride=Math.max(1,l.body().height()/4);int n=0;for(var state:UiState.Kind.values()){int top=l.body().y()+n++*stride;UiState.draw(g,font,state,state.name(),"Общая композиция состояния",l.body().x(),top,l.body().width(),top+stride-4);}});}
 @Override public void onClose(){minecraft.setScreen(parent);}@Override public boolean isPauseScreen(){return false;}
}
