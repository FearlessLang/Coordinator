package mainCoordinator;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

final class UserIO{
  private static volatile boolean console= System.console() != null;
  private static final Object lock= new Object();
  private static StringBuilder buf= new StringBuilder();
  private static JFrame frame;
  private static JTextArea area;

  static void setConsole(boolean v){ console = v; }
  static boolean isConsole(){ return console || GraphicsEnvironment.isHeadless(); }

  static void hookStd(){
    if (isConsole()){ return; }
    System.setOut(new PrintStream(new Sink(false), true, StandardCharsets.UTF_8));
    System.setErr(new PrintStream(new Sink(true), true, StandardCharsets.UTF_8));
  }

  static void out(String s){ append(s, false); }
  static void err(String s){ append(s, true); }

  static void fatal(String s){
    if (isConsole()){ System.err.print(s); return; }
    append(s, true);
    ensureGuiSync();
  }

  private static void append(String s, boolean isErr){
    if (isConsole()){
      (isErr ? System.err : System.out).print(s);
      return;
    }
    synchronized(lock){
      buf.append(s);
      if (frame == null){ SwingUtilities.invokeLater(UserIO::ensureGui); return; }
      SwingUtilities.invokeLater(() -> area.append(s));
    }
  }

  private static void ensureGuiSync(){
    if (frame != null){ return; }
    try{
      if (SwingUtilities.isEventDispatchThread()){ ensureGui(); }
      else { SwingUtilities.invokeAndWait(UserIO::ensureGui); }
    }catch(Exception ex){
      System.err.print(buf.toString());
    }
  }

  private static void ensureGui(){
    if (frame != null){ return; }
    frame= new JFrame("Fearless output");
    area= new JTextArea(25, 125);
    area.setEditable(false);
    area.setFont(new Font(Font.MONOSPACED, Font.BOLD, 18));
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.add(new JScrollPane(area));
    synchronized(lock){ area.setText(buf.toString()); }
    area.setCaretPosition(area.getDocument().getLength());
    frame.pack();
    frame.setLocationByPlatform(true);
    frame.setVisible(true);
  }

  private static final class Sink extends OutputStream{
    private final boolean isErr;
    Sink(boolean isErr){ this.isErr = isErr; }
    @Override public void write(int b){ write(new byte[]{(byte)b}, 0, 1); }
    @Override public void write(byte[] b, int off, int len){
      var s= new String(b, off, len, StandardCharsets.UTF_8);
      append(s, isErr);
    }
  }
}