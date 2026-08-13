package manager;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Taskbar;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import userMessages.Violation;

final class ManagerGui {
  private static final Duration tickDelay= Duration.ofSeconds(1);//the label shows seconds, so tick every second
  private static final DateTimeFormatter received= DateTimeFormatter.ofPattern("HH:mm:ss");
  final JFrame frame;
  final JLabel elapsed;
  final JTextArea messages;
  private ManagerGui(Runnable onQuit){
    frame= new JFrame("Fearless Manager");
    elapsed= new JLabel("Running for 00:00:00");
    elapsed.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
    messages= new JTextArea(16, 60);
    messages.setEditable(false);
    var scroll= new JScrollPane(messages);
    scroll.setBorder(BorderFactory.createTitledBorder("Start messages received"));
    var quit= new JButton("Quit manager");
    quit.addActionListener(_ -> onQuit.run());
    var bottom= new JPanel(new FlowLayout(FlowLayout.RIGHT));
    bottom.add(quit);
    frame.setLayout(new BorderLayout());
    frame.add(elapsed, BorderLayout.NORTH);
    frame.add(scroll, BorderLayout.CENTER);
    frame.add(bottom, BorderLayout.SOUTH);
    frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    frame.addWindowListener(new WindowAdapter(){//closing the window only hides the manager; the Quit button terminates it
      @Override public void windowClosing(WindowEvent e){ frame.setVisible(false); }
    });
    setIcons();
    frame.setLocationByPlatform(true);
    frame.pack();
  }
  //Same icon in the window, in the task bar and in the tray: one Fearless, whichever of
  //the three the desktop chooses to show. Whether a desktop shows a task bar icon at all
  //is its own business, so an unsupported task bar is not a failure (same policy as the tray).
  private void setIcons(){
    frame.setIconImage(FearlessIcon.image());
    if (!Taskbar.isTaskbarSupported()){ return; }
    var bar= Taskbar.getTaskbar();
    if (bar.isSupported(Taskbar.Feature.ICON_IMAGE)){ bar.setIconImage(FearlessIcon.image()); }
  }
  static ManagerGui create(Runnable onQuit){
    var result= new AtomicReference<ManagerGui>();
    try {
      SwingUtilities.invokeAndWait(() -> result.set(new ManagerGui(onQuit)));
      return result.get();
    }
    catch(InterruptedException|InvocationTargetException e){ throw Violation.couldNotStartGui(e); }
  }
  void showManager(){
    SwingUtilities.invokeLater(() -> {
      frame.setVisible(true);
      frame.setExtendedState(frame.getExtendedState() & ~Frame.ICONIFIED);
      frame.toFront();
      frame.requestFocus();//best effort
    });
  }
  void addMessages(List<String> texts){
    var stamp= LocalTime.now().format(received);
    SwingUtilities.invokeLater(() -> {
      for(var text: texts){ messages.append(stamp+"  "+oneLine(text)+"\n"); }
    });
  }
  void tickLoop(){
    var start= Instant.now();
    while(tick(start)){}
  }
  private boolean tick(Instant start){
    setElapsed(Duration.between(start, Instant.now()));
    try { Thread.sleep(tickDelay); return true; }
    catch(InterruptedException e){ return false; }//interruption is the designed stop signal for this worker
  }
  private void setElapsed(Duration duration){
    SwingUtilities.invokeLater(() -> elapsed.setText("Running for "+formatDuration(duration)));
  }
  private static String oneLine(String text){ return text.replace('\r',' ').replace('\n',' '); }
  static String formatDuration(Duration duration){
    var seconds= duration.toSeconds();
    return "%02d:%02d:%02d".formatted(seconds/3600, (seconds/60)%60, seconds%60);
  }
}
