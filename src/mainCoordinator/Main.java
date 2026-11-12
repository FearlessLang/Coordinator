package mainCoordinator;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.awt.Desktop;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Taskbar;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import coordinator.CapabilityEnvironment;
import coordinator.Coordinator;
import core.E.Literal;
import core.OtherPackages;
import fileAssociations.FileAssociations;
import fileAssociations.Icon;
import fileAssociations.WindowIcon;
import fileSupport.NativeLocaleForcer;
import naiveBackend.BackendTools;
import realSourceOracle.RealSourceOracleWithZip;
import tools.Fs;
import tools.JavacTool;
import tools.PathBytes;
import tools.SourceOracle;
import tools.Utf8Sink;
import userMessages.Report;
import userMessages.UserError;
import userMessages.Violation;
import utils.Bug;

public final class Main{
  private static final AtomicInteger macSpawnOk= new AtomicInteger(0);
  public static void main(String[] args){
    NativeLocaleForcer.forceEnglish();
    JavacTool.reqAppDir(Violation::mustUseLauncher);
    try{ if (!hasConsoleFlag()){ hookStd(); } NativeLocaleForcer.requireUtf8FileNames(); run(args); }
    catch (UserError e){ failed(e.getMessage()); }
    catch (Throwable e){ failed(UserError.crash(e)); }
  }
  private static void failed(String error){
    System.err.print(error+"\n");
    if (hasConsoleFlag()){ System.exit(1); }
  }
  private static void run(String[] args) throws InterruptedException{
    var appDir= JavacTool.reqAppDir(Violation::mustUseLauncher);
    var launch= IntStream.range(0, args.length).filter(i -> !args[i].startsWith("-psn_")).boxed().findFirst().map(i -> normalize(args, i));
    if (Fs.isMac() && (launch.isEmpty() || !hasConsoleFlag())){ Desktop.getDesktop().setOpenFileHandler(e -> e.getFiles().forEach(f -> spawnMac(f.toPath()))); }
    if (Fs.isMac() && launch.isEmpty()){ Thread.sleep(1000); }
    if (launch.isEmpty() && macSpawnOk.get() != 0){ return; }
    try{ offerAssociation(appDir); }
    catch (UserError e){ e.display(); System.exit(1); }
    if (launch.isEmpty()){ InitialSupportGuiMain.main(new String[]{}); return; }
    var l= launch.get();
    if (!Files.exists(l)){ throw Report.launchPathNotFound(l); }
    var stdLib= appDir.resolve("stdLib");
    run(Fs.of((Files.isDirectory(l) ? l : l.getParent())::toRealPath), stdLib.resolve("base"), stdLib.resolve("rt"));
  }
  public static void run(Path project, Path base, Path rt) throws InterruptedException{
    UserError.root = project;
    Fs.onSymbolicLink = Report::symlinkForbidden;
    var c= new Coordinator(){
      @Override public Optional<Path> baseCachePath(){ return Optional.of(base.getParent().resolve("baseCache")); }
      @Override public BackendTools backendTools(String pkgName, SourceOracle oracle, OtherPackages other, List<Literal> core, CapabilityEnvironment capabilities){
        return BackendTools.of(pkgName, oracle, other, core, project.resolve(Coordinator.outDir), baseCachePath(), rt, capabilities);
      }
    };
    c.main(project, base);
  }
  public static final Predicate<String> belongsToFamily= s -> s.contains("earless");
  ///The launcher names us: one identity per launcher, and the manager's own is not the portable one.
  public static String identity(Path launcher){ return launcher.getFileName().toString().replaceFirst("\\.[^.]*$", ""); }
  /// The manager of that folder never sees the folder deleted in part: the folder is renamed in its folder, then deleted.
  private static void removeAtOnce(Path dir){
    if (!Fs.exists(dir)){ return; }
    var gone= dir.resolveSibling(dir.getFileName()+"_"+UUID.randomUUID().toString().replace("-", ""));
    Fs.ofV(() -> Fs.move(dir, gone, StandardCopyOption.ATOMIC_MOVE));
    Fs.rmTree(gone);
  }
  public static void replaced(Path launcher, Path command){
    JavacTool.managerFolderOf(command).filter(d -> !JavacTool.managerFolderOf(launcher).equals(Optional.of(d))).ifPresent(Main::removeAtOnce);
  }
  public static void reconcile(Path launcher, String name, List<Icon> extensions, List<String> taken, List<WindowIcon> windows, Path icon, Function<String,RuntimeException> ambiguous){
    FileAssociations.reconcile(identity(launcher), name, belongsToFamily, launcher, extensions, taken, windows, icon, c -> replaced(launcher, c),
      ambiguous, Held::new, Violation::associationNotWritable, Violation::associationLeftHalfDone);
  }
  public static void eradicateAll(){ FileAssociations.eradicateAll(belongsToFamily, Violation::associationLeftHalfDone); }
  private static void offerAssociation(Path appDir){
    var launcher= ProcessHandle.current().info().command().map(Path::of);
    if (launcher.isEmpty() || GraphicsEnvironment.isHeadless()){ return; }
    var l= launcher.get();
    if (!belongsToFamily.test(identity(l))){ return; }
    var icon= appDir.resolve("icon.png");
    hold(taken -> reconcile(l, "Fearless Portable", List.of(new Icon(".fearless", l, icon)), taken, List.of(), icon,
      reported -> Violation.associationsAmbiguous(reported).withRecovery("Remove all Fearless registrations", Main::eradicateAll)),
      h -> askOnDesktop(Violation.associationQuestion(h, e -> "", "ends Fearless")), h -> Violation.associationNotOurs(h, e -> ""));
  }
  public static void hold(Consumer<List<String>> reconcile, Predicate<Map<String,String>> ask, Function<Map<String,String>,RuntimeException> rejected){
    try{ reconcile.accept(List.of()); }
    catch (Held h){
      if (!ask.test(h.held)){ throw rejected.apply(h.held); }
      try{ reconcile.accept(List.copyOf(h.held.keySet())); }
      catch (Held again){ throw rejected.apply(again.held); }
    }
  }
  /// A refusal of the reconcile: each extension, with its dot, mapped to the name of the program holding it.
  @SuppressWarnings("serial") public static final class Held extends RuntimeException{
    final Map<String,String> held;
    public Held(Map<String,String> held){ this.held= held; }
  }
  private static boolean askOnDesktop(String question){
    var asked= new FutureTask<>(() -> JOptionPane.showConfirmDialog(null, question, "Fearless", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION);
    try{ SwingUtilities.invokeAndWait(asked); return asked.get(); }
    catch (InterruptedException|InvocationTargetException|ExecutionException e){ throw Bug.of(e); }
  }
  private static void spawnMac(Path file){
    var launcher= Path.of(ProcessHandle.current().info().command().orElseThrow());
    var windowed= hasConsoleFlag() ? launcher.resolveSibling(launcher.getFileName()+"w") : launcher;
    Fs.ofV(() -> Fs.processBuilder(List.of(windowed.toString(), file.toString())).inheritIO().start());
    macSpawnOk.incrementAndGet();
  }
  private static void hookStd() throws InvocationTargetException, InterruptedException, ExecutionException{
    if (!Desktop.isDesktopSupported()){ throw Violation.desktopUnsupported(); }
    assert !SwingUtilities.isEventDispatchThread();
    var task= new FutureTask<>(Main::initGui);
    SwingUtilities.invokeAndWait(task);
    var frame= task.get();
    System.setOut(new PrintStream(new Utf8Sink(frame), true, UTF_8));
    System.setErr(new PrintStream(new Utf8Sink(frame), true, UTF_8));
  }
  private static Consumer<String> initGui(){
    var area= new JTextArea(25, 120);
    area.setEditable(false);
    area.setFont(new Font(Font.MONOSPACED, Font.BOLD, 18));
    var frame= new JFrame("Fearless output");
    Fs.ofV(() -> {
      var icon= ImageIO.read(JavacTool.reqAppDir(Violation::mustUseLauncher).resolve("icon.png").toFile());
      frame.setIconImage(icon);
      if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)){ Taskbar.getTaskbar().setIconImage(icon); }
    });
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.add(new JScrollPane(area));
    frame.pack();
    var screen= GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
    frame.setSize(Math.min(frame.getWidth(), screen.width), Math.min(frame.getHeight(), screen.height));
    frame.setLocationRelativeTo(null);
    return s -> SwingUtilities.invokeLater(() -> {
      if (!frame.isVisible()){ frame.setVisible(true); }
      area.append(s);
    });
  }
  private static boolean hasConsoleFlag(){ return JavacTool.consoleKey.equals(System.getProperty(JavacTool.launcherKey)); }
  //The JVM hands main the arguments decoded as text, and bytes not valid in the file name encoding
  //are lost: PathBytes.arg reads the bytes the process received. A "file:" URI writes every byte as
  //an escape. From here on the compiler holds the project as a Path, which keeps every byte.
  private static Path normalize(String[] args, int i){
    var s= args[i];
    assert !s.isEmpty();
    Path p;
    try{ p= s.startsWith("file:") ? Path.of(URI.create(s)) : PathBytes.arg(args, i); }
    catch (RuntimeException _){ throw Violation.badLaunchArg(s, hasConsoleFlag()); }
    return p.toAbsolutePath().normalize();
  }
}
