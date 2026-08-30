package managerIcons;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;

import javax.swing.Icon;

public record BadgeIcon(Image image, int size, Color badge) implements Icon{
  public static final Color running= new Color(0x2E7D32);
  public static final Color upToDate= new Color(0x1565C0);
  public static final Color stale= new Color(0xB0BEC5);
  @Override public int getIconWidth(){ return size; }
  @Override public int getIconHeight(){ return size; }
  @Override public void paintIcon(Component c, Graphics g, int x, int y){
    var g2= (Graphics2D)g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.drawImage(image, x, y, size, size, c);
    var dot= Math.max(8, size/4);
    var at= x+size-dot;
    g2.setColor(Color.white);
    g2.fillOval(at-2, y+size-dot-2, dot+4, dot+4);
    g2.setColor(badge);
    g2.fillOval(at, y+size-dot, dot, dot);
    g2.dispose();
  }
}
