package dev.abros.anthub.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
public final class RestartScreen extends Screen {
    private final Screen parent;private final String transaction;private String error="";
    public RestartScreen(Screen parent,String transaction){super(Client.tr("restart.title"));this.parent=parent;this.transaction=transaction;}
    @Override protected void init(){
        addRenderableWidget(Button.builder(Client.tr("restart.close"),b->{b.active=false;Client.IO.submit(()->{try{Client.hub.startHelper(transaction);minecraft.execute(()->{if(minecraft.level!=null){minecraft.level.disconnect();minecraft.disconnect();}minecraft.stop();});}catch(Exception e){error=Errors.message(e);minecraft.execute(()->b.active=true);}});}).bounds(width/2-100,height/2+20,200,20).build());
        addRenderableWidget(Button.builder(Component.literal("Продолжить до выхода"),b->onClose()).bounds(width/2-100,height/2+46,200,20).build());
    }
    @Override public void render(GuiGraphics g,int mx,int my,float pt){super.render(g,mx,my,pt);g.drawCenteredString(font,title,width/2,height/2-65,0xE2BE75);g.drawWordWrap(font,Client.tr("restart.message"),width/2-150,height/2-35,300,0xFFFFFF);if(!error.isEmpty())g.drawWordWrap(font,Component.literal(error),10,height-40,width-20,0xFF9999);}
    @Override public void onClose(){minecraft.setScreen(parent);}
}
