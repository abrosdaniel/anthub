package dev.abros.anthub.core;
import dev.abros.anthub.core.skins.*;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
class SkinImageTest {
 static byte[] png(int width,int height)throws Exception{var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);image.setRGB(8,8,height==32?0xffff0000:0xff00ff00);var out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);return out.toByteArray();}
 @Test void normalizesLegacyAndKeepsFace()throws Exception{var bytes=SkinImage.normalize(png(64,32),1048576);var image=ImageIO.read(new ByteArrayInputStream(bytes));assertEquals(64,image.getHeight());assertEquals(0xffff0000,image.getRGB(8,8));assertEquals(255,image.getRGB(0,16)>>>24);}
 @Test void rejectsInvalidSizesBeforeDecode()throws Exception{assertThrows(Exception.class,()->SkinImage.normalize(png(128,128),1048576));assertThrows(Exception.class,()->SkinImage.normalize(png(64,64),32));assertThrows(Exception.class,()->SkinImage.normalize(new byte[100],1048576));}
 @Test void stableEncodingAndLimits()throws Exception{var bytes=SkinImage.normalize(png(64,64),1048576);assertArrayEquals(bytes,SkinImage.normalize(bytes,1048576));assertThrows(IllegalArgumentException.class,()->new SkinSettings(true,1,0,"false"));assertThrows(IllegalArgumentException.class,()->new SkinSettings(true,1,6,"false"));assertThrows(IllegalArgumentException.class,()->new SkinSettings(true,1,2,"true"));assertEquals(1048576,new SkinSettings(true,1,2,"UUID").maxBytes());}
}
