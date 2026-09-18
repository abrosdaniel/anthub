package dev.abros.anthub.client;

import dev.abros.anthub.AntHub;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class CoreVersionButton extends Button {
    CoreVersionButton(int x,int y,Screen parent){
        super(x,y,80,20,Component.literal(AntHub.VERSION+" ▾"),button->
                net.minecraft.client.Minecraft.getInstance().setScreen(new CoreVersionsPopup(parent)),DEFAULT_NARRATION);
    }
    @Override protected void renderWidget(GuiGraphics graphics,int x,int y,float delta){
        super.renderWidget(graphics,x,y,delta);
        if(Client.offeredUpdate!=null){
            int left=getX()+5,top=getY()+(getHeight()-7)/2;
            int border=0xFF29033F,red=0xFFFF3333;
            // Seven-pixel rounded indicator with a one-pixel outline.
            graphics.fill(left+2,top,left+5,top+7,border);
            graphics.fill(left+1,top+1,left+6,top+6,border);
            graphics.fill(left,top+2,left+7,top+5,border);
            graphics.fill(left+2,top+1,left+5,top+6,red);
            graphics.fill(left+1,top+2,left+6,top+5,red);
        }
    }
}
