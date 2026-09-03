package managerIcons;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.stream.IntStream;

final class GeneratedIcon{
  private GeneratedIcon(){}
  static BufferedImage of(String name, int size){
    var colors= colors(new Random(name.hashCode()));
    var res= new BufferedImage(size,size,BufferedImage.TYPE_INT_RGB);
    var g= res.createGraphics();
    quarters(g,colors,size);
    g.dispose();
    initials(res,colors,name,size);
    return res;
  }
  private static List<Color> colors(Random rnd){
    var base= rnd.nextFloat();
    return IntStream.range(0,4).mapToObj(i->color(rnd,base+i*0.25f)).toList();
  }
  private static Color color(Random rnd, float hue){
    return Color.getHSBColor(hue+rnd.nextFloat()*0.1f-0.05f,0.5f+rnd.nextFloat()*0.45f,0.45f+rnd.nextFloat()*0.45f);
  }
  private static void quarters(Graphics2D g, List<Color> colors, int size){
    var half= size/2;
    int[] at= {0,half};
    int[] len= {half,size-half};
    var bevel= Math.max(1,size/16);
    for (int i= 0; i < 4; i++){
      tile(g,colors.get(i),at[i%2],at[i/2],len[i%2],len[i/2],bevel);
    }
  }
  //flat fill plus a light top/left edge and dark bottom/right edge, to hint at a raised tile instead of a dead flat square
  private static void tile(Graphics2D g, Color base, int x, int y, int w, int h, int bevel){
    g.setColor(base);
    g.fillRect(x,y,w,h);
    g.setColor(shade(base,0.16f));
    g.fillRect(x,y,w,bevel);
    g.fillRect(x,y,bevel,h);
    g.setColor(shade(base,-0.16f));
    g.fillRect(x,y+h-bevel,w,bevel);
    g.fillRect(x+w-bevel,y,bevel,h);
  }
  private static Color shade(Color c, float deltaBrightness){
    var hsb= Color.RGBtoHSB(c.getRed(),c.getGreen(),c.getBlue(),null);
    var brightness= Math.max(0f,Math.min(1f,hsb[2]+deltaBrightness));
    return Color.getHSBColor(hsb[0],hsb[1],brightness);
  }
  private static void initials(BufferedImage img, List<Color> colors, String name, int size){
    var ink= readableOn(colors);
    var mask= glyphMask(first2(name),ink,size);
    if (ink.equals(Color.black)){ outline(img,mask); }
    var g= img.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
    g.drawImage(mask,0,0,null);
    g.dispose();
  }
  //renders just the initials, alone on a transparent canvas, so their coverage (this image's alpha) can drive the outline independently of whatever colour sits underneath them
  private static BufferedImage glyphMask(String text, Color ink, int size){
    var mask= new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
    var g= mask.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,size/2));
    g.setColor(ink);
    var m= g.getFontMetrics();
    g.drawString(text,(size-m.stringWidth(text))/2,(size-m.getHeight())/2+m.getAscent());
    g.dispose();
    return mask;
  }
  //a two-step halo around the glyph's opaque pixels: pure white right next to the letters, then a white/background blend one pixel further out, so the edge softens instead of cutting straight from white to a saturated quarter colour
  private static void outline(BufferedImage img, BufferedImage mask){
    var w= img.getWidth();
    var h= img.getHeight();
    var core= new boolean[w][h];
    for (int x= 0; x < w; x++){
      for (int y= 0; y < h; y++){ core[x][y]= alpha(mask,x,y) >= 128; }
    }
    var ring1= dilate(core,w,h);
    var ring2= dilate(ring1,w,h);
    for (int x= 0; x < w; x++){
      for (int y= 0; y < h; y++){
        if (core[x][y]){ continue; }
        if (ring1[x][y]){ img.setRGB(x,y,Color.white.getRGB()); }
        else if (ring2[x][y]){ img.setRGB(x,y,blend(new Color(img.getRGB(x,y)),Color.white).getRGB()); }
      }
    }
  }
  private static int alpha(BufferedImage img, int x, int y){ return (img.getRGB(x,y) >>> 24)&0xFF; }
  private static Color blend(Color a, Color b){
    return new Color((a.getRed()+b.getRed())/2,(a.getGreen()+b.getGreen())/2,(a.getBlue()+b.getBlue())/2);
  }
  private static boolean[][] dilate(boolean[][] mask, int w, int h){
    var res= new boolean[w][h];
    for (int x= 0; x < w; x++){
      for (int y= 0; y < h; y++){ res[x][y]= mask[x][y] || hasSetNeighbour(mask,x,y,w,h); }
    }
    return res;
  }
  private static boolean hasSetNeighbour(boolean[][] mask, int x, int y, int w, int h){
    for (int dx= -1; dx <= 1; dx++){
      for (int dy= -1; dy <= 1; dy++){
        var nx= x+dx;
        var ny= y+dy;
        if (nx >= 0 && nx < w && ny >= 0 && ny < h && mask[nx][ny]){ return true; }
      }
    }
    return false;
  }
  private static String first2(String name){
    var s= name.strip();
    return s.substring(0,Math.min(2,s.length())).toUpperCase(Locale.ROOT);
  }
  private static Color readableOn(List<Color> colors){
    var lum= colors.stream().mapToDouble(GeneratedIcon::luminance).average().getAsDouble();
    return contrast(lum,1) >= contrast(lum,0) ? Color.white : Color.black;
  }
  private static double contrast(double a, double b){ return (Math.max(a,b)+0.05)/(Math.min(a,b)+0.05); }
  private static double luminance(Color c){
    return 0.2126*channel(c.getRed())+0.7152*channel(c.getGreen())+0.0722*channel(c.getBlue());
  }
  private static double channel(int v){
    var s= v/255.0;
    return s <= 0.03928 ? s/12.92 : Math.pow((s+0.055)/1.055,2.4);
  }
}
