package dev.abros.anthub.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;
public final class RegistryScreen extends ScrollScreen {
    private final Screen parent;private final List<String> repositories;private String status="";
    public RegistryScreen(Screen parent,List<String> repositories){super(Client.tr("registry"));this.parent=parent;this.repositories=repositories;}
    @Override protected void init(){scrollArea(repositories.size(),35,height-65,24,width/2+153);for(int i=firstRow;i<Math.min(repositories.size(),firstRow+visibleRows);i++){String repo=repositories.get(i);boolean saved=Client.hub.saved().contains(repo);var button=addRenderableWidget(Button.builder(Component.literal((saved?Client.tr("added").getString()+" · ":"")+repo.substring(19)),b->{try{Client.hub.saveRepository(repo);rebuildWidgets();}catch(Exception e){status=Errors.message(e);}}).bounds(width/2-150,35+(i-firstRow)*24,300,20).build());button.active=!saved;}addRenderableWidget(Button.builder(Client.tr("back"),b->onClose()).bounds(width/2-100,height-26,200,20).build());}
    @Override public void render(GuiGraphics g,int mx,int my,float pt){super.render(g,mx,my,pt);g.drawCenteredString(font,title,width/2,12,0xE2BE75);Ui.status(g,font,status,10,height-60,width-20,height-34);if(repositories.isEmpty())g.drawCenteredString(font,Client.tr("registry.empty"),width/2,50,0xBBBBBB);}
    @Override public void onClose(){minecraft.setScreen(parent);}
}
