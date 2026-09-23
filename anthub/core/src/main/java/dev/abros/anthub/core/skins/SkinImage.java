package dev.abros.anthub.core.skins;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
/** Decode only bounded skin dimensions, then re-encode pixels without uploaded metadata. */
public final class SkinImage {
 private SkinImage(){}
 public static byte[] normalize(byte[] input,int limit)throws IOException{
  if(input.length<33||input.length>limit||!Arrays.equals(Arrays.copyOf(input,8),new byte[]{(byte)137,80,78,71,13,10,26,10})||ByteBuffer.wrap(input,8,4).getInt()!=13||ByteBuffer.wrap(input,12,4).getInt()!=0x49484452)throw new IOException("Выберите PNG допустимого размера");
  int w=ByteBuffer.wrap(input,16,4).getInt(),h=ByteBuffer.wrap(input,20,4).getInt();if(w!=64||(h!=64&&h!=32))throw new IOException("Скин должен иметь размер 64×64 или 64×32");
  var decoded=ImageIO.read(new ByteArrayInputStream(input));if(decoded==null||decoded.getWidth()!=w||decoded.getHeight()!=h)throw new IOException("Повреждённый PNG");
  var image=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,64,h,decoded.getRGB(0,0,64,h,null,0,64),0,64);
  if(h==32){int[][] parts={{4,16,16,32,4,4},{8,16,16,32,4,4},{0,20,24,32,4,12},{4,20,16,32,4,12},{8,20,8,32,4,12},{12,20,16,32,4,12},{44,16,-8,32,4,4},{48,16,-8,32,4,4},{40,20,0,32,4,12},{44,20,-8,32,4,12},{48,20,-16,32,4,12},{52,20,-8,32,4,12}};for(var p:parts)for(int y=0;y<p[5];y++)for(int x=0;x<p[4];x++)image.setRGB(p[0]+p[2]+p[4]-1-x,p[1]+p[3]+y,decoded.getRGB(p[0]+x,p[1]+y));
   boolean solid=true;for(int y=0;y<32;y++)for(int x=32;x<64;x++)if((image.getRGB(x,y)>>>24)<128)solid=false;
   if(solid)for(int y=0;y<32;y++)for(int x=32;x<64;x++)image.setRGB(x,y,image.getRGB(x,y)&0xFFFFFF);
  }
  opaque(image,0,0,32,16);opaque(image,0,16,64,32);opaque(image,16,48,48,64);
  var out=new ByteArrayOutputStream();ImageIO.write(image,"PNG",out);return out.toByteArray();
 }
 private static void opaque(BufferedImage i,int left,int top,int right,int bottom){for(int y=top;y<bottom;y++)for(int x=left;x<right;x++)i.setRGB(x,y,i.getRGB(x,y)|0xFF000000);}
}
