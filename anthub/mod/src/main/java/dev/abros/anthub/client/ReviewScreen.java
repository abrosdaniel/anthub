package dev.abros.anthub.client;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
/** Scrollable installation summary with one explicit action and a cancel button. */
final class ReviewScreen extends TextScreen {
 private final Component action;private final Runnable confirm;
 ReviewScreen(Screen parent,Component title,String text,Component action,Runnable confirm){super(parent,title,text);this.action=action;this.confirm=confirm;}
 ReviewScreen(Screen parent,Component title,String text,Component action,Runnable confirm,boolean literal){super(parent,title,text,40,literal);this.action=action;this.confirm=confirm;}
 @Override protected void init(){super.init();clearWidgets();addRenderableWidget(Button.builder(Client.tr("cancel"),b->onClose()).bounds(width/2-150,panelBottom()-26,100,20).build());addRenderableWidget(Button.builder(action,b->confirm.run()).bounds(width/2-44,panelBottom()-26,194,20).build());}
}
