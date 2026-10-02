package dev.abros.anthub.client;
import net.minecraft.client.gui.*;
/** Semantic typography: hierarchy through scale and spacing, not louder colours. */
final class UiTypography {
 enum Role { PAGE, SECTION, TITLE, BODY, CAPTION }
 static void draw(GuiGraphics g,Font font,String text,int x,int y,int width,Role role){float scale=role==Role.PAGE?1.15f:role==Role.SECTION?1.05f:1f;g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(scale,scale,1);Ui.text(g,font,UiKit.fit(font,text,(int)(width/scale)),0,0,role==Role.CAPTION?UiKit.muted():UiKit.text(),false);g.pose().popPose();}
 private UiTypography(){}
}
