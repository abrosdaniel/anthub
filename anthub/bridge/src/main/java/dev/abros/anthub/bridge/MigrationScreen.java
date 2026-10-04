package dev.abros.anthub.bridge;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;

final class MigrationScreen extends Screen {
    private Button retry,close;
    MigrationScreen(){super(Component.literal("AntHub → Rivet"));}
    @Override protected void init(){
        int w=Math.min(360,Math.max(180,width-32)),x=(width-w)/2;
        retry=addRenderableWidget(Button.builder(Component.literal("Повторить"),b->BridgeClient.start()).bounds(x,height-44,(w-8)/2,20).build());
        close=addRenderableWidget(Button.builder(Component.literal("Закрыть Minecraft"),b->Minecraft.getInstance().stop()).bounds(x+(w+8)/2,height-44,(w-8)/2,20).build());
    }
    @Override public void tick(){retry.active=BridgeClient.failed;}
    @Override public boolean shouldCloseOnEsc(){return false;}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float delta){
        g.fill(0,0,width,height,0xff11171c);
        int w=Math.min(360,Math.max(180,width-32)),x=(width-w)/2;
        g.drawCenteredString(font,title,width/2,26,0xffe0b376);
        int y=60;
        for(var line:font.split(Component.literal(BridgeClient.message),w)){g.drawString(font,line,x,y,0xffe2e6e9,false);y+=font.lineHeight+3;}
        y+=16;
        String detail="Личные настройки и список серверов сохраняются. Прежний JAR и папка данных остаются в резервной копии. После запуска Rivet обновите сборку сервера, если это потребуется.";
        for(var line:font.split(Component.literal(detail),w)){if(y<height-58)g.drawString(font,line,x,y,0xffa4b3bd,false);y+=font.lineHeight+3;}
        super.render(g,mouseX,mouseY,delta);
    }
}
