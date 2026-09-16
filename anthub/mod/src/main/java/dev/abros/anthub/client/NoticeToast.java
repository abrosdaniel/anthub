package dev.abros.anthub.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.*;
import net.minecraft.network.chat.Component;
/** A vanilla toast with an explicit close target when a screen exposes the cursor. */
final class NoticeToast extends SystemToast {
 private static final SystemToastId ID=new SystemToastId();private float x,y;private long rendered;private boolean dismissed;
 private NoticeToast(Component title,Component body){super(ID,title,body);}
 static void show(Component title,Component body){var toasts=Minecraft.getInstance().getToasts();var old=toasts.getToast(NoticeToast.class,ID);if(old==null)toasts.addToast(new NoticeToast(title,body));else{old.dismissed=false;old.reset(title,body);}}
 static boolean dismiss(double mouseX,double mouseY){var toast=Minecraft.getInstance().getToasts().getToast(NoticeToast.class,ID);if(toast==null||toast.dismissed||net.minecraft.Util.getMillis()-toast.rendered>200)return false;if(mouseX>=toast.x+toast.width()-16&&mouseX<toast.x+toast.width()&&mouseY>=toast.y&&mouseY<toast.y+16){toast.dismissed=true;toast.forceHide();return true;}return false;}
 @Override public Toast.Visibility render(GuiGraphics g,ToastComponent component,long time){x=g.pose().last().pose().m30();y=g.pose().last().pose().m31();rendered=net.minecraft.Util.getMillis();var visibility=super.render(g,component,time);g.drawString(Minecraft.getInstance().font,"×",width()-11,4,0xFFFFFF);return visibility;}
}
