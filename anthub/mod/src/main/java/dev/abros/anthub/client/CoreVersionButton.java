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
        boolean update=Client.offeredUpdate!=null;
        setMessage(Component.literal(AntHub.VERSION+" ").append(Component.literal(update?"↑":"▾").withStyle(s->s.withColor(update?0xFF3333:0xFFFFFF))));
        setTooltip(update?net.minecraft.client.gui.components.Tooltip.create(Component.literal("Доступна новая версия AntHub")):null);
        super.renderWidget(graphics,x,y,delta);
        if(update)graphics.renderOutline(getX(),getY(),getWidth(),getHeight(),0xFF29033F);
    }
}
