package mainCoordinator;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.awt.Desktop;
import java.awt.Font;
import java.awt.Taskbar;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import coordinator.Coordinator;
import fileAssociations.FileAssociations;
import fileAssociations.Icon;
import userMessages.UserError;
import userMessages.Violation;
import tools.Fs;
import tools.Utf8Sink;
import tools.JavacTool;

public final class Main{
  private static final AtomicInteger macSpawnOk= new AtomicInteger(0);
  public static void main(String[] args){
    JavacTool.reqAppDir(Violation::mustUseLauncher);
    try{ if (!hasConsoleFlag()){ hookStd(); } run(args); }
    catch(UserError e){ System.err.print(e.getMessage()); }
    catch(Throwable t){ 
      System.err.println(t.getClass().getCanonicalName());
      System.err.print(UserError.crash(t));
    }
  }
  private static void run(String[] args) throws InvocationTargetException, InterruptedException, ExecutionException{
    var appDir= JavacTool.reqAppDir(Violation::mustUseLauncher);
    offerAssociation(appDir);
    Optional<Path> launch= launchPath(args);
    if (Fs.isMac() && !hasConsoleFlag()){ registerMacSpawnHandler(appDir); }
    if (!launch.isPresent()){
      if (Fs.isMac()){ Thread.sleep(1000); }
      if ( macSpawnOk.get() != 0){ return; }
    }          
    if (launch.isEmpty()){ InitialSupportGuiMain.main(new String[]{}); return; }
    Path l= launch.get();
    var project= Files.isDirectory(l) ? l : l.getParent();
    var base= appDir.resolve("stdLib").resolve("base");
    var rt= appDir.resolve("stdLib").resolve("rt");
    run(project,base,rt);
  }
  public static void run(Path project, Path base, Path rt) throws InvocationTargetException, InterruptedException, ExecutionException{
    new Coordinator(){
      @Override public Path stLibPath(){ return base; }
      @Override public Path rtPath(){ return rt; }
    }.main(project);
  }
  private static final Predicate<String> belongsToFamily= s->s.contains("earless");
  private static void offerAssociation(Path appDir){
    if (Fs.isMac()){ return; }
    var launcher= ProcessHandle.current().info().command().map(Path::of);
    if (launcher.isEmpty()){ return; }
    var l= launcher.get();
    var identity= identity(l);
    if (!belongsToFamily.test(identity)){ return; }
    var icon= appDir.resolve("icon.png");
    FileAssociations.reconcile(identity, belongsToFamily, l, List.of(new Icon(".fearless", l, icon)), l, icon,
      reported->Violation.associationsAmbiguous(reported).withRecovery(
        "Remove all Fearless registrations", ()->FileAssociations.eradicateAll(belongsToFamily, Violation::associationLeftHalfDone)),
      Violation::associationUserLocked,
      Violation::associationNotOurs,
      Violation::associationNotWritable,
      Violation::associationLeftHalfDone);
  }
  private static String identity(Path launcher){
    var file= launcher.getFileName().toString();
    var dot= file.lastIndexOf('.');
    return dot < 0 ? file : file.substring(0, dot);
  }
  private static void registerMacSpawnHandler(Path appDir){
    Desktop.getDesktop().setOpenFileHandler(e->e.getFiles().forEach(f->spawnMac(appDir, f.toPath())));
  }
  private static void spawnMac(Path appDir, Path file){
    var bundle= appDir.getParent().getParent();
    try{ 
      //new ProcessBuilder("open","-n","-a",bundle.toString(),"--args",file.toString()).start();
      var pb= new ProcessBuilder("open","-n","-a",bundle.toString(),"--args",file.toString());
      pb.environment().remove("_JPACKAGE_LAUNCHER");
      pb.start();
      }
    catch(Exception e){ throw new RuntimeException(e); }
    macSpawnOk.incrementAndGet();
  }
  private static void hookStd() throws InvocationTargetException, InterruptedException, ExecutionException{
    if (!Desktop.isDesktopSupported()){ throw Violation.desktopUnsupported(); }
    assert !SwingUtilities.isEventDispatchThread();
    FutureTask<Consumer<String>> task= new FutureTask<>(Main::initGui);
    SwingUtilities.invokeAndWait(task);
    Consumer<String> frame= task.get();
    System.setOut(new PrintStream(new Utf8Sink(frame), true, UTF_8));
    System.setErr(new PrintStream(new Utf8Sink(frame), true, UTF_8));
  }
  private static Consumer<String> initGui(){
    var area= new JTextArea(25, 120);
    area.setEditable(false);
    area.setFont(new Font(Font.MONOSPACED, Font.BOLD, 18));
    var frame= new JFrame("Fearless output");
    Fs.ofV(()->{
      var icon= ImageIO.read(JavacTool.reqAppDir(Violation::mustUseLauncher).resolve("icon.png").toFile());
      frame.setIconImage(icon);
      if (Taskbar.isTaskbarSupported()){
        var tb= Taskbar.getTaskbar();
        if (tb.isSupported(Taskbar.Feature.ICON_IMAGE)){ tb.setIconImage(icon); }
      }
    });
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.add(new JScrollPane(area));
    frame.pack();
    frame.setLocationByPlatform(true);
    return s->SwingUtilities.invokeLater(() -> {
      if (!frame.isVisible()){ frame.setVisible(true); }
      area.append(s);
    });
  }  
  public static boolean hasConsoleFlag(){ return JavacTool.consoleKey.equals(System.getProperty(JavacTool.launcherKey)); }
  private static Optional<Path> launchPath(String[] args){
    return Stream.of(args)
      .filter(a->!a.startsWith("-psn_"))
      .findFirst().map(Main::normalize);
  }
  private static Path normalize(String s){
    assert !s.isEmpty();
    Path p; try{ p= s.startsWith("file:")?Path.of(URI.create(s)):Path.of(s); }
    catch(RuntimeException ex){ throw Violation.badLaunchArg(s, hasConsoleFlag()); }
    return p.toAbsolutePath().normalize();
  }
}
