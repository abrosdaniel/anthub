package dev.abros.anthub.client;

import dev.abros.anthub.AntHub;
import dev.abros.anthub.core.CoreUpdater;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Version selection is read-only. Only Install starts a download and transaction. */
final class CoreVersionsPopup extends ScrollScreen {
    private final Screen parent;
    private final String branch;
    private List<CoreUpdater.Update> versions=List.of();
    private CoreUpdater.Update selected;
    private boolean started,loading,installing;
    private String status="";
    CoreVersionsPopup(Screen parent){this(parent,"");}
    CoreVersionsPopup(Screen parent,String branch){super(Component.literal("Версия AntHub"));this.parent=parent;this.branch=branch;if(Client.offeredUpdate!=null&&(branch.isEmpty()||dev.abros.anthub.core.Versions.supportsBranch(Client.offeredUpdate.version(),branch))){versions=List.of(Client.offeredUpdate);selected=Client.offeredUpdate;}}
    private int panelWidth(){return Math.min(340,width-16);}
    private int left(){return width-panelWidth()-8;}
    private int bottom(){return Math.min(height-8,270);}
    @Override protected void init(){
        if(parent.width!=width||parent.height!=height)parent.resize(minecraft,width,height);
        int x=left(),w=panelWidth();
        scrollArea(versions.size(),66,bottom()-92,24,x+w-10);
        for(int i=firstRow;i<Math.min(versions.size(),firstRow+visibleRows);i++){
            var update=versions.get(i);
            String label=(update.equals(selected)?"✓ ":"")+update.version();
            var choice=addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(label,w-34)),button->{selected=update;rebuildWidgets();}).bounds(x+8,66+(i-firstRow)*24,w-24,20).build());choice.active=!installing;
        }
        var install=addRenderableWidget(Button.builder(Component.literal(installing?"Подготовка…":"Установить"),button->install()).bounds(x+8,bottom()-58,w-16,20).build());install.active=selected!=null&&!installing&&Client.pending.isEmpty();
        var close=addRenderableWidget(Button.builder(Component.literal("Закрыть"),button->onClose()).bounds(x+8,bottom()-34,w-16,20).build());close.active=!installing;
        if(!started){started=true;load();}
    }
    private void load(){
        if(Client.hub==null){status="AntHub ещё загружается. Откройте список чуть позже.";return;}
        loading=true;
        Client.NETWORK.submit(()->{try{var updates=Client.hub.availableCoreUpdates().stream().filter(u->branch.isEmpty()||dev.abros.anthub.core.Versions.supportsBranch(u.version(),branch)).toList();minecraft.execute(()->{loading=false;versions=updates;if(selected==null||!updates.contains(selected))selected=updates.isEmpty()?null:updates.getFirst();status=updates.isEmpty()?"Других версий нет":"";if(minecraft.screen==this)rebuildWidgets();});}catch(Exception failure){minecraft.execute(()->{loading=false;status="Не удалось проверить версии. Попробуйте позже.";});}});
    }
    private void install(){
        if(selected==null||installing)return;installing=true;status="";var update=selected;rebuildWidgets();
        Client.IO.submit(()->{try{String transaction=Client.hub.prepareCoreUpdate(Client.loadedJar,update);Client.pending=transaction;minecraft.execute(()->minecraft.setScreen(new RestartScreen(parent,transaction)));}catch(Exception failure){minecraft.execute(()->{installing=false;status=Errors.message(failure);rebuildWidgets();});}});
    }
    @Override public void renderBackground(GuiGraphics graphics,int x,int y,float delta){
        graphics.fill(0,0,width,height,0x88090E14);graphics.fill(left(),34,left()+panelWidth(),bottom(),0xFF1B252E);graphics.renderOutline(left(),34,panelWidth(),bottom()-34,0xFF536879);
    }
    @Override public void render(GuiGraphics graphics,int x,int y,float delta){
        parent.render(graphics,-10000,-10000,delta);graphics.flush();graphics.pose().pushPose();
        try{graphics.pose().translate(0,0,400);super.render(graphics,x,y,delta);
            graphics.drawString(font,"Установлена: "+AntHub.VERSION,left()+8,44,0xE2BE75);
            graphics.drawString(font,font.plainSubstrByWidth(loading?"Загрузка…":status,panelWidth()-16),left()+8,bottom()-82,0xBAC7D2);graphics.flush();
        }finally{graphics.pose().popPose();}
    }
    @Override public boolean mouseClicked(double x,double y,int button){if(button==0&&(x<left()||x>left()+panelWidth()||y<34||y>bottom())){onClose();return true;}return super.mouseClicked(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){return x>=left()&&x<=left()+panelWidth()&&super.mouseScrolled(x,y,dx,dy);}
    @Override public void onClose(){if(!installing)minecraft.setScreen(parent);}
    @Override public boolean shouldCloseOnEsc(){return !installing;}
}
