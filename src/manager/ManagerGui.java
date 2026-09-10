package manager;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Taskbar;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileFilter;

import managerData.ManagerData;
import managerIcons.FolderName;
import managerInfo.FolderFacts;
import managerInfo.FolderInfo;
import managerList.FolderList;
import tools.JavacTool;
import tools.OpenPath;
import userMessages.UserError;
import userMessages.Violation;

final class ManagerGui {
  private static final Duration tickDelay= Duration.ofSeconds(1);//the label shows seconds, so tick every second
  private static final Duration freshnessPeriod= Duration.ofMillis(2500);
  final JFrame frame;
  final JLabel elapsed;
  final FolderList folders;
  final JSplitPane split;
  private final JPanel body= new JPanel();
  private final JMenu running= new JMenu("Running");
  private final JMenu project= new JMenu("Project");
  private final ManagerData data;
  private final Executor worker;
  private final Consumer<ManagerGui> onForget;
  private final Runnable onQuit;
  private final Map<Path,FolderInfo> open= new LinkedHashMap<>();
  private final AtomicBoolean rotationChecking= new AtomicBoolean();
  private boolean windowVisible;
  private FolderInfo shown;
  private Instant lastShownCheck= Instant.now();
  private Instant lastRotationCheck= Instant.now().minusMillis(1250);
  private int rotationIndex= 0;
  private ManagerGui(Runnable onQuit, ManagerData data, Executor worker, Consumer<ManagerGui> onForget){
    frame= new JFrame("Fearless Manager");
    this.data= data;
    this.worker= worker;
    this.onForget= onForget;
    this.onQuit= onQuit;
    elapsed= new JLabel("Running for 00:00:00");
    elapsed.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
    folders= new FolderList(data, this::isRunning, this::showFolder);
    folders.setBorder(BorderFactory.createTitledBorder("Registered project folders"));
    split= new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, folders, new JPanel());
    split.setResizeWeight(0.3);
    split.setDividerLocation(320);
    body.setLayout(new BorderLayout());
    body.add(folders, BorderLayout.CENTER);
    frame.setJMenuBar(menuBar());
    frame.setLayout(new BorderLayout());
    frame.add(body, BorderLayout.CENTER);
    frame.add(elapsed, BorderLayout.SOUTH);
    frame.getRootPane().setTransferHandler(dropHandler());
    frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    frame.addWindowListener(new WindowAdapter(){//closing the window only hides the manager; the Quit button terminates it
      @Override public void windowClosing(WindowEvent e){ frame.setVisible(false); setWindowVisible(false); }
      @Override public void windowIconified(WindowEvent e){ setWindowVisible(false); }
      @Override public void windowDeiconified(WindowEvent e){ setWindowVisible(true); }
    });
    setIcons();
    frame.setLocationByPlatform(true);
    frame.setSize(preferredSize());//the size a restore (un-maximize) falls back to
    frame.setExtendedState(Frame.MAXIMIZED_BOTH);
  }
  //760x900 is the restore size if un-maximized, capped against the usable
  //screen so it never lands partly off-screen on a shorter display.
  private static Dimension preferredSize(){
    var avail= GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
    return new Dimension(Math.min(760, avail.width), Math.min(900, avail.height));
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
  String nameFolder(ManagerData data, Path folder){
    var taken= data.registered().stream().map(ManagerData.Entry::alias).collect(Collectors.toSet());
    return FolderName.makeUnique(folder, taken, suggested -> askName(folder, suggested, taken));
  }
  private String askName(Path folder, String suggested, Set<String> taken){
    var result= new AtomicReference<String>();
    try { SwingUtilities.invokeAndWait(() -> result.set(prompt(folder, suggested, taken))); }
    catch(InterruptedException|InvocationTargetException e){ throw Violation.couldNotStartGui(e); }
    return result.get();
  }
  private String prompt(Path folder, String suggested, Set<String> taken){
    var question= """
      Another registered project is already called "%s".
      Choose the name to show for
      %s
      Names use lowercase letters, digits and single underscores.""".formatted(FolderName.compactName(folder), folder);
    while(true){
      var answer= JOptionPane.showInputDialog(frame, question, suggested);
      if (answer == null){ return suggested; }
      var name= answer.strip();
      if (FolderName.isName(folder,name) && FolderName.isFree(folder,name,taken)){ return name; }
    }
  }
  boolean askForget(){
    return ask("""
      Remove Fearless as the program registered to open Fearless projects?

      What your desktop already remembers by hand is left exactly as it is:
      this only removes what Fearless itself registered.""");
  }
  //Asked from the event thread by the button, and from the manager's own thread
  //by the offer: invokeAndWait is a deadlock when it is already the event thread.
  private boolean ask(String question){
    if (SwingUtilities.isEventDispatchThread()){ return confirm(question); }
    var result= new AtomicReference<Boolean>();
    try { SwingUtilities.invokeAndWait(() -> result.set(confirm(question))); }
    catch(InterruptedException|InvocationTargetException e){ throw Violation.couldNotStartGui(e); }
    return result.get();
  }
  private boolean confirm(String question){
    return JOptionPane.showConfirmDialog(frame, question, "Fearless", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
  }
  void explain(UserError problem){
    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, problem.getMessage(), "Fearless", JOptionPane.WARNING_MESSAGE));
  }
  void select(Path folder){ SwingUtilities.invokeLater(()->folders.select(folder)); }
  private boolean isRunning(Path folder){
    var info= open.get(folder);
    return info != null && info.session().running().isPresent();
  }
  //A divider dragged to the icon-grid-only edge (see FolderInfo's zero minimum
  //size) would otherwise restore to that same edge position on every future
  //selection, silently reopening each panel too narrow to see.
  private static final int minPanelWidth= 300;
  private FolderInfo info(Path folder){
    return open.computeIfAbsent(folder, f->new FolderInfo(data, f, EclipseConnect.reports(data.entryOf(f).orElseThrow().alias()), worker, this::foldersChangedHere));
  }
  void run(Path folder){ SwingUtilities.invokeLater(()->info(folder).compileOrRun()); }
  void terminate(Path folder){ SwingUtilities.invokeLater(()->info(folder).session().terminate()); }
  private void showFolder(Optional<Path> folder){
    if (folder.isEmpty()){ hidePanel(); return; }
    shown= info(folder.get());
    shown.reload();
    var where= split.getDividerLocation();
    if (split.getWidth() > 0){ where= Math.min(where, split.getWidth()-minPanelWidth); }
    split.setLeftComponent(folders);
    split.setRightComponent(shown.panel());
    body.removeAll();
    body.add(split, BorderLayout.CENTER);
    split.setDividerLocation(where);
    fillProjectMenu();
    body.revalidate();
    body.repaint();
  }
  private void hidePanel(){
    shown= null;
    split.setRightComponent(new JPanel());
    body.removeAll();
    body.add(folders, BorderLayout.CENTER);
    fillProjectMenu();
    body.revalidate();
    body.repaint();
  }
  private JMenuBar menuBar(){
    var res= new JMenuBar();
    var manager= new JMenu("Manager");
    manager.setMnemonic('M');
    manager.add(item("Edit project metadata...",true,this::editMetadata));
    manager.add(item("Show raw project state...",true,this::showRawState));
    manager.add(item("Connect Eclipse...",true,this::connectEclipse));
    manager.addSeparator();
    manager.add(item("Forget association",true,()->onForget.accept(this)));
    manager.add(item("Quit manager",true,onQuit));
    project.setMnemonic('P');
    running.setMnemonic('R');
    res.add(manager);
    res.add(project);
    res.add(running);
    fillProjectMenu();
    fillRunningMenu();
    return res;
  }
  private static JMenuItem item(String text, boolean enabled, Runnable action){
    var res= new JMenuItem(text);
    res.setEnabled(enabled);
    res.addActionListener(_->action.run());
    return res;
  }
  private void fillProjectMenu(){
    project.removeAll();
    project.add(item("Add folder...",true,this::addFolder));
    project.addSeparator();
    var on= shown;
    project.add(item("Clear cache",on != null,()->on.clearCache()));
    project.add(item("Browse files",on != null,()->on.browse()));
    project.add(item("View documentation",on != null,()->on.openDocs()));
    project.add(item("View base documentation",true,ManagerGui::openBaseDocs));
    project.add(item("Error report",on != null && on.hasProblem(),()->on.report()));
    project.addSeparator();
    project.add(item("Forget project",on != null,()->on.forget()));
  }
  private static void openBaseDocs(){
    var appDir= JavacTool.reqAppDir(Violation::mustUseLauncher);
    OpenPath.open(appDir.resolve("stdLib").resolve("baseCache").resolve("base.html"));
  }
  private void fillRunningMenu(){
    running.removeAll();
    var live= open.values().stream().filter(i->i.session().running().isPresent()).toList();
    if (live.isEmpty()){ running.add(item("<nothing running>",false,()->{})); return; }
    live.forEach(i->running.add(item(describe(i),true,()->folders.select(i.folder()))));
  }
  private String describe(FolderInfo info){
    var alias= data.entryOf(info.folder()).map(ManagerData.Entry::alias).orElseGet(()->FolderName.compactName(info.folder()));
    return alias+" - "+info.session().running().orElseThrow();
  }
  List<String> runningPrograms(){
    return open.values().stream().filter(i->i.session().running().isPresent()).map(this::describe).toList();
  }
  private void addFolder(){
    var chooser= new JFileChooser();
    chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
    chooser.setFileFilter(projectFilter());
    chooser.setAcceptAllFileFilterUsed(false);
    chooser.setDialogTitle("Add a Fearless project folder");
    if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION){ return; }
    worker.execute(()->Manager.register(this, data, chooser.getSelectedFile().toString()));
  }
  private static FileFilter projectFilter(){
    return new FileFilter(){
      @Override public boolean accept(File f){ return f.isDirectory() || f.getName().endsWith(".fearless"); }
      @Override public String getDescription(){ return "Fearless project folder or *.fearless file"; }
    };
  }
  private TransferHandler dropHandler(){
    return new TransferHandler(){
      @Override public boolean canImport(TransferSupport support){
        if (!FolderDrop.hasFileFlavor(support.getTransferable())){ return false; }
        support.setDropAction(COPY);
        return true;
      }
      @Override public boolean importData(TransferSupport support){
        var paths= FolderDrop.pathsOf(support.getTransferable());
        if (paths.isEmpty()){ return false; }
        worker.execute(()->paths.forEach(p->Manager.register(ManagerGui.this,data,p.toString())));
        return true;
      }
    };
  }
  private void connectEclipse(){
    var chooser= new JFileChooser();
    chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
    chooser.setDialogTitle("Select the Eclipse executable");
    if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION){ return; }
    worker.execute(()->tryConnect(chooser.getSelectedFile().toPath()));
  }
  private void tryConnect(Path chosen){
    String done;
    try{ done= EclipseConnect.connect(chosen); }
    catch(UserError e){ explain(e); return; }
    SwingUtilities.invokeLater(()->JOptionPane.showMessageDialog(frame,done,"Fearless",JOptionPane.INFORMATION_MESSAGE));
  }
  private void editMetadata(){
    var area= new JTextArea(data.infoText(),30,100);
    area.setFont(new Font(Font.MONOSPACED,Font.PLAIN,13));
    var dialog= new JDialog(frame,"Edit project metadata",true);
    var commit= new JButton("Commit");
    var close= new JButton("Close");
    commit.addActionListener(_->worker.execute(()->tryCommit(area.getText(),dialog)));
    close.addActionListener(_->dialog.dispose());
    var buttons= new JPanel(new FlowLayout(FlowLayout.RIGHT));
    buttons.add(commit);
    buttons.add(close);
    dialog.setLayout(new BorderLayout());
    dialog.add(new JScrollPane(area),BorderLayout.CENTER);
    dialog.add(buttons,BorderLayout.SOUTH);
    dialog.pack();
    dialog.setLocationRelativeTo(frame);
    dialog.setVisible(true);
  }
  private void tryCommit(String text, JDialog dialog){
    try{ data.commitInfoText(text); }
    catch(UserError e){ explain(e); return; }
    SwingUtilities.invokeLater(()->{
      dialog.dispose();
      open.values().forEach(FolderInfo::reload);
      foldersChangedHere();
    });
  }
  private void showRawState(){
    var text= new JTextArea(rawStateDump(),30,100);
    text.setEditable(false);
    text.setFont(new Font(Font.MONOSPACED,Font.PLAIN,13));
    JOptionPane.showMessageDialog(frame,new JScrollPane(text),"Raw project state (everything the manager tracks that is not in the metadata file)",JOptionPane.PLAIN_MESSAGE);
  }
  private String rawStateDump(){
    var sb= new StringBuilder();
    for (var e: data.registered()){
      var facts= FolderFacts.of(e.path(),e.kind());
      sb.append(e.alias()).append("  (").append(e.path()).append(")\n");
      sb.append("  files: ").append(facts.files()).append(", bytes: ").append(facts.bytes()).append('\n');
      sb.append("  last modified: ").append(facts.modified()).append('\n');
      sb.append("  package-data stamp: ").append(facts.jsonStamp()).append(", cache stamp: ").append(facts.cacheStamp()).append('\n');
      sb.append("  cache up to date: ").append(facts.cacheUpToDate()).append('\n');
      sb.append("  structurally valid: ").append(facts.valid());
      facts.problem().ifPresent(p->sb.append(" (").append(p.lines().findFirst().orElse(p)).append(')'));
      sb.append('\n');
      sb.append("  marker file: ").append(data.markerProblem(e).map(p->p.lines().findFirst().orElse(p)).orElse("ok")).append('\n');
      sb.append("  last compiled: ").append(e.compiled()).append(", last run: ").append(e.run()).append('\n');
      var info= open.get(e.path());
      if (info != null){
        sb.append("  session busy: ").append(info.session().busy()).append('\n');
        sb.append("  session running: ").append(info.session().running().orElse("<no>")).append('\n');
        sb.append("  known mains from last compile: ").append(info.session().mains().orElse(List.of())).append('\n');
      }
      sb.append('\n');
    }
    return sb.isEmpty() ? "<nothing registered>" : sb.toString();
  }
  private void foldersChangedHere(){
    folders.refresh();
    var known= data.registered();
    EclipseConnect.publish(known);
    var live= known.stream().map(ManagerData.Entry::path).toList();
    open.values().stream().filter(i->!live.contains(i.folder())).forEach(i->i.session().terminate());
    open.keySet().removeIf(f->!live.contains(f));
    fillRunningMenu();
    if (shown != null && !live.contains(shown.folder())){ hidePanel(); return; }
    fillProjectMenu();
  }
  void foldersChanged(){ SwingUtilities.invokeLater(this::foldersChangedHere); }
  static ManagerGui create(Runnable onQuit, ManagerData data, Executor worker, Consumer<ManagerGui> onForget){
    var result= new AtomicReference<ManagerGui>();
    try {
      SwingUtilities.invokeAndWait(() -> result.set(new ManagerGui(onQuit, data, worker, onForget)));
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
      setWindowVisible(true);
    });
  }
  private synchronized void setWindowVisible(boolean value){ windowVisible= value; notifyAll(); }
  private synchronized void awaitWindowVisible() throws InterruptedException { while(!windowVisible){ wait(); } }
  void tickLoop(){
    var start= Instant.now();
    while(tick(start)){}
  }
  private boolean tick(Instant start){
    try { awaitWindowVisible(); }
    catch(InterruptedException e){ return false; }
    var up= Duration.between(start, Instant.now());
    SwingUtilities.invokeLater(()->tickShown(up));
    try { Thread.sleep(tickDelay); return true; }
    catch(InterruptedException e){ return false; }//interruption is the designed stop signal for this worker
  }
  private void tickShown(Duration up){
    var status= shown == null ? "" : "   -   "+shown.status();
    elapsed.setText("Running for "+formatDuration(up)+status);
    var now= Instant.now();
    if (shown != null && due(lastShownCheck,now)){ lastShownCheck= now; shown.recheckFreshness(); }
    if (due(lastRotationCheck,now)){ lastRotationCheck= now; recheckNextInRotation(); }
  }
  private static boolean due(Instant last, Instant now){ return Duration.between(last,now).compareTo(freshnessPeriod) >= 0; }
  private void recheckNextInRotation(){
    var registered= data.registered().stream().map(ManagerData.Entry::path).toList();
    var candidates= shown == null ? registered : registered.stream().filter(f->!f.equals(shown.folder())).toList();
    if (candidates.isEmpty() || !rotationChecking.compareAndSet(false,true)){ return; }
    var folder= candidates.get(rotationIndex % candidates.size());
    rotationIndex+= 1;
    worker.execute(()->{
      var modified= FolderFacts.modified(folder);
      var upToDate= FolderFacts.cacheUpToDate(folder,modified);
      SwingUtilities.invokeLater(()->{ rotationChecking.set(false); folders.updateFreshness(folder,modified,upToDate); });
    });
  }
  static String formatDuration(Duration duration){
    var seconds= duration.toSeconds();
    return "%02d:%02d:%02d".formatted(seconds/3600, (seconds/60)%60, seconds%60);
  }
}
