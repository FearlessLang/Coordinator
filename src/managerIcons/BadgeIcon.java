package managerIcons;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;

import javax.swing.Icon;

public record BadgeIcon(Image image, int size, Mark mark) implements Icon{
  public enum Mark{ none, attention, invalid, running }
  private static final Color attentionColor= new Color(0xEF6C00);
  private static final Color invalidColor= new Color(0xC62828);
  private static final Color runningColor= new Color(0x2E7D32);
  @Override public int getIconWidth(){ return size; }
  @Override public int getIconHeight(){ return size; }
  @Override public void paintIcon(Component c, Graphics g, int x, int y){
    var g2= (Graphics2D)g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.drawImage(image, x, y, size, size, c);
    switch(mark){
      case none -> {}
      case attention -> badge(g2,x,y,attentionColor,"!");
      case invalid -> badge(g2,x,y,invalidColor,"X");
      case running -> running(g2,x,y);
    }
    g2.dispose();
  }
  //bigger than a plain dot and carries a mark, so it reads as "this needs you" rather than blending into the tile
  private void badge(Graphics2D g, int x, int y, Color color, String glyph){
    var d= Math.max(10,size/3);
    var at= x+size-d;
    var top= y+size-d;
    ring(g,at,top,d);
    g.setColor(color);
    g.fillOval(at,top,d,d);
    g.setColor(Color.white);
    g.setFont(g.getFont().deriveFont(Font.BOLD,d*0.7f));
    var m= g.getFontMetrics();
    g.drawString(glyph,at+(d-m.stringWidth(glyph))/2f,top+(d+m.getAscent()-m.getDescent())/2f);
  }
  //a partial ring redrawn at a time-derived angle, so a list that repaints while this project runs shows it spinning
  private void running(Graphics2D g, int x, int y){
    var d= Math.max(8,size/4);
    var at= x+size-d;
    var top= y+size-d;
    ring(g,at,top,d);
    var angle= (int)(System.currentTimeMillis()/8%360);
    g.setColor(runningColor);
    g.setStroke(new BasicStroke(Math.max(2f,d/4f),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
    g.drawArc(at,top,d,d,angle,270);
  }
  private void ring(Graphics2D g, int at, int top, int d){
    g.setColor(Color.white);
    g.fillOval(at-2,top-2,d+4,d+4);
  }
}
