package mainCoordinator;

import java.awt.*;
import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.function.Consumer;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.filechooser.FileSystemView;
import javax.swing.plaf.FontUIResource;

import tools.Fs;
import utils.Bug;

public final class InitialSupportGuiMain{
  public static void main(String[] args){
    try{ UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
    catch(Exception _){ throw Bug.unreachable(); }
    setUiFont(uiFont);
    SwingUtilities.invokeLater(InitialSupportGuiMain::new);
  }

  private final JFrame frame= new JFrame("Welcome to Fearless");
  private final JPanel actions= vbox();
  private final JPanel advanced= vbox();
  public InitialSupportGuiMain(){
    addBtn(actions, "Create demo project", "Copy a demo project into a folder you choose.", this::selectFile);
    addBtn(actions, "Create project with tests");
    addBtn(actions, "Create project with GUI");
    addBtn(actions, "More...", "More options!", this::toggleMore);

    advanced.setVisible(false);
    addBtn(advanced, "Create project with API connection");
    addBtn(advanced, "Create empty project");
    addBtn(advanced, "Import template...");
    actions.add(advanced);

    var root= new JPanel(new BorderLayout(12,12));
    root.setBorder(new EmptyBorder(12,12,12,12));
    frame.setContentPane(root);

    root.add(header(), BorderLayout.NORTH);
    root.add(center(), BorderLayout.CENTER);
    root.add(footer(), BorderLayout.SOUTH);

    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    frame.setMinimumSize(new Dimension(300,300));
    frame.setSize(new Dimension(950,700));
    frame.setLocationRelativeTo(null);
    frame.setVisible(true);
  }

  private JComponent header(){
    var p= new JPanel(new BorderLayout());
    var title= new JLabel("Welcome to Fearless");
    title.setFont(uiFont.deriveFont(Font.BOLD, uiFont.getSize2D() + 6f));
    p.add(title, BorderLayout.NORTH);

    var sub= new JLabel(startText);
    sub.setBorder(new EmptyBorder(6,0,0,0));
    p.add(sub, BorderLayout.SOUTH);
    return p;
  }

  private JComponent center(){
    var left= textPaneHtml(welcomeHtml);
    var leftScroll= new JScrollPane(left);
    leftScroll.setBorder(border("About"));
    var rightScroll= new JScrollPane(actions);
    rightScroll.setBorder(border("Actions"));
    rightScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    rightScroll.setPreferredSize(new Dimension(240, 0));
    var split= new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScroll, rightScroll);
    split.setResizeWeight(0.70);
    split.setBorder(null);
    split.setContinuousLayout(true);
    rightScroll.getViewport().setViewPosition(new Point(0,0));
    leftScroll.getViewport().setViewPosition(new Point(0,0));
    return split;
  }

  private JComponent footer(){
    var p= new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    var info= addBtn(p, "Info", "About Fearless", this::showInfo);
    info.setFont(uiFont.deriveFont(uiFont.getSize2D() - 2f));
    info.setMargin(new Insets(2,8,2,8));
    return p;
  }

  private void toggleMore(JButton more){
    boolean now= !advanced.isVisible();
    advanced.setVisible(now);
    more.setText(now ? "Less..." : "More...");
    actions.revalidate();
    actions.repaint();
  }
  private void showInfo(JButton b){
    var p= textPaneHtml(aboutHtml);
    var s= new JScrollPane(p);
    s.setPreferredSize(new Dimension(780,560));
    s.getViewport().setViewPosition(new Point(0,0));
    JOptionPane.showMessageDialog(frame, s, "About Fearless", JOptionPane.INFORMATION_MESSAGE);
  }
  private static void openUri(String s){
    try{
      if (!Desktop.isDesktopSupported()){ Toolkit.getDefaultToolkit().beep(); return; }
      var d= Desktop.getDesktop();
      if (!d.isSupported(Desktop.Action.BROWSE)){ Toolkit.getDefaultToolkit().beep(); return; }
      d.browse(URI.create(s));
    }catch(Exception _){ Toolkit.getDefaultToolkit().beep(); }
  }
  private void selectFile(JButton b){
    var fc= new JFileChooser();
    fc.setDialogTitle("Choose your project location");
    fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
    fc.setCurrentDirectory(FileSystemView.getFileSystemView().getHomeDirectory()); // often Desktop on Windows
    fc.setSelectedFile(new File("demo")); 
    if(fc.showSaveDialog(b) != JFileChooser.APPROVE_OPTION){ return; }
    var filePath=fc.getSelectedFile().toPath();
    MakeDemo.of(filePath);
    afterCreate(filePath);
  }
  private static void setUiFont(Font f){
    var d= UIManager.getDefaults();
    for (var k: d.keySet()){
      var v= d.get(k);
      if (v instanceof Font){ d.put(k, new FontUIResource(f)); }
    }
  }
  private static TitledBorder border(String title){
    return BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), title);
  }
  private static JEditorPane textPaneHtml(String html){
    var p= new JEditorPane("text/html", html);
    p.setEditable(false);
    p.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
    p.setFont(uiFont);
    p.setBorder(new EmptyBorder(10,10,10,10));
    p.addHyperlinkListener(e->{
      if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED){ return; }
      if (e.getURL() != null) openUri(e.getURL().toString());
      else openUri(e.getDescription());
    });
    return p;
  }
  private static JPanel vbox(){
    var p= new JPanel();
    p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
    p.setBorder(new EmptyBorder(8,8,8,8));
    return p;
  }
  private static JButton addBtn(Container parent, String text, String tooltip, Consumer<JButton> c){
    var b= new JButton(text);
    b.setAlignmentX(Component.CENTER_ALIGNMENT);
    b.setMaximumSize(new Dimension(Integer.MAX_VALUE, b.getPreferredSize().height + 6));
    if (tooltip != null && !tooltip.isEmpty()){ b.setToolTipText(tooltip); }
    b.addActionListener(_->c.accept(b));
    parent.add(b);
    parent.add(Box.createVerticalStrut(8));
    return b;
  }
  private static JButton addBtn(Container parent, String text){
    var b= new JButton(text);
    b.setAlignmentX(Component.CENTER_ALIGNMENT);
    b.setMaximumSize(new Dimension(Integer.MAX_VALUE, b.getPreferredSize().height + 6));
    b.setEnabled(false);
    b.setToolTipText(comingSoon);
    parent.add(b);
    parent.add(Box.createVerticalStrut(8));
    return b;
  }
  private void afterCreate(Path projectDir){
    var html= Fs.isWindows() ? afterCreateWinHtml : Fs.isMac() ? afterCreateMacHtml : afterCreateLinuxHtml;
    var p= textPaneHtml(html);
    var s= new JScrollPane(p);
    s.setPreferredSize(new Dimension(780,560));
    s.getViewport().setViewPosition(new Point(0,0));
    JOptionPane.showMessageDialog(frame, s, "Next steps", JOptionPane.INFORMATION_MESSAGE);
  }
  private static final Font uiFont= new Font(Font.SANS_SERIF, Font.PLAIN, 16);
  public static final String startText= "Predictable programming at scale";
  public static final String comingSoon= "Coming soon";
  public static final String welcomeHtml= """
<html>
  <body>
    <h2 style='margin-top:0;'>Getting started</h2>

    <p>
      A Fearless project is just a folder with a few files inside.
    </p>

    <p>
      <b>Create demo project</b> will copy a demo project into a folder you choose.
    </p>

    <p>
      The other templates will appear here as Fearless matures.
    </p>

    <h3>Quick links</h3>
    <p style='margin-top:0;'>
      <a href='https://fearlang.org/'>Website</a><br/>
      <a href='https://github.com/FearlessLang'>Source</a><br/>
      <a href='https://marcoservetto.github.io/ZeroToHero/src/assetsGuide/01_01.html'>Guide to learn Fearless</a><br/>
      <a href='https://marcoservetto.github.io/ZeroToHero/src/assetsDest/Level101/Level101.html'>Game to learn fearless</a>
    </p>
  </body>
</html>
""";
  public static final String aboutHtml= """
<html>
  <body>
    <h2 style='margin-top:0;'>Fearless - About</h2>

    <h3>Creators</h3>
    <ul style='margin-top:0;'>
      <li>Marco Servetto - <a href='mailto:marco.servetto@vuw.ac.nz'>marco.servetto@vuw.ac.nz</a></li>
      <li>Nick Webster - <a href='mailto:nick@nick.geek.nz'>nick@nick.geek.nz</a></li>
    </ul>

    <h3>Developed at</h3>
    <p style='margin-top:0;'>
      Victoria University of Wellington (VUW), School of Engineering and Computer Science (ECS)
    </p>

    <h3>Hall of fame / thanks</h3>
    <ul style='margin-top:0;'>
      <li>Name Surname - <a href='mailto:email@example.com'>email@example.com</a></li>
      <li>Name Surname - <a href='mailto:email@example.com'>email@example.com</a></li>
    </ul>
  </body>
</html>
""";
public static final String afterCreateWinHtml= """
<html>
  <body>
    <h2 style='margin-top:0;'>Demo created</h2>

    <p>
      Your demo folder should now be open.
      Inside it, you will find <b>start.fearless</b>.
    </p>

    <h3>Run it</h3>
    <ol>
      <li>Double-click <b>start.fearless</b>.</li>
      <li>You will see "select an app to open this .fearless file":
        <ol>
          <li>Scroll down</li>
          <li>Click <b>Chose an app on your pc</b></li>
          <li>Navigate to the fearless installation and select <b>fearless/fearlessw.exe</b></li>
          <li>Chose <b>Always</b></li>
          <li>The first time you run fearless on a new project, it may take a while</li>
        </ol>
      </li>
    </ol>
    <p>
      Tip: if you don't see ".fearless", enable "File name extensions" in Explorer's View menu.
    </p>
  </body>
</html>
""";
  public static final String afterCreateMacHtml= """
<html>
  <body>
    <h2 style='margin-top:0;'>Demo created</h2>

    <p>
      Your demo folder should now be open.
      Inside it, you will find <b>start.fearless</b>.
    </p>

    <h3>Run it</h3>
    <ol>
      <li>Double-click <b>start.fearless</b>.</li>
      <li>You will see a dialog like "There is no application set to open the document":
        <ol>
          <li>Click <b>Choose Application...</b></li>
          <li>Select the Fearless app (usually <b>Fearless.app</b>)</li>
          <li>Tick <b>Always Open With</b></li>
          <li>Click <b>Open</b></li>
          <li>The first time you run fearless on a new project, it may take a while</li>
        </ol>
      </li>
    </ol>

    <p>
      Tip: if you don't see ".fearless", Finder -> Settings (Preferences) -> Advanced -> enable
      <b>Show all filename extensions</b>.
    </p>
  </body>
</html>
""";
  public static final String afterCreateLinuxHtml= """
<html>
  <body>
    <h2 style='margin-top:0;'>Demo created</h2>

    <p>
      Your demo folder should now be open.
      Inside it, you will find <b>start.fearless</b>.
    </p>

    <h3>One-time setup: register the .fearless file type</h3>
    <ol>
      <li>In the Fearless installation folder, find the file <b>fearless-mime.xml</b>.</li>
      <li>Copy/drag-and-drop it to:
        <pre style='margin-top:0;'>~/.local/share/mime/packages/</pre>
      </li>
      <li>Click on Open a terminal here and run this single command:
        <pre style='margin-top:0;'>update-mime-database ~/.local/share/mime</pre>
      </li>
    </ol>

    <h3>Associate .fearless files with Fearless</h3>
    <ol>
      <li>Right-click <b>start.fearless</b>.</li>
      <li>Choose <b>Open With...</b> (or <b>Properties</b> -> <b>Open With</b>).</li>
      <li>Select <b>.../fearless/bin/fearlessw</b>.</li>
      <li>Choose <b>Always</b> / <b>Remember</b> / <b>Set as default</b>.</li>
      <li>The first time you run fearless on a new project, it may take a while.</li>
    </ol>
    <p>
      Tip: if after the MIME step the file still looks like plain text, close and reopen the file manager (or log out/in)
      and then do the <b>Open With</b> step again.
    </p>
  </body>
</html>
""";
}