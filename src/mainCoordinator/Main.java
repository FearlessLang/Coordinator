package mainCoordinator;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Taskbar;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import javax.imageio.ImageIO;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
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
    var ok= run(Fs.of((Files.isDirectory(l) ? l : l.getParent())::toRealPath), stdLib.resolve("base"), stdLib.resolve("rt"));
    if (!ok && hasConsoleFlag()){ System.exit(1); }
  }
  public static boolean run(Path project, Path base, Path rt) throws InterruptedException{
    UserError.root = project;
    Fs.onSymbolicLink = Report::symlinkForbidden;
    var failed= new AtomicBoolean();
    var c= new Coordinator(){
      @Override public Optional<Path> baseCachePath(){ return Optional.of(base.getParent().resolve("baseCache")); }
      @Override public BackendTools backendTools(String pkgName, SourceOracle oracle, OtherPackages other, List<Literal> core, CapabilityEnvironment capabilities){
        return BackendTools.of(pkgName, oracle, other, core, project.resolve(Coordinator.outDir), baseCachePath(), rt, capabilities);
      }
      @Override public void mainEnded(int exitCode){ if (exitCode != 0){ failed.set(true); } }
    };
    c.main(project, base);
    return !failed.get();
  }
  public static final Predicate<String> belongsToFamily= s -> s.contains("earless");
  ///The launcher names us: one identity per launcher, and the manager's own is not the portable one.
  public static String identity(Path launcher){ return launcher.getFileName().toString().replaceFirst("\\.[^.]*$", ""); }
  //Write to a .tmp name, then atomically rename it to .msg: the owner drains
  //only *.msg, so it can never observe a half written file.
  //A name already taken gets another id: the rename never replaces a file.
  public static void leave(Path msgDir, String message) throws IOException{
    var name= String.format(Locale.ROOT, "%020d-%s", System.currentTimeMillis(), UUID.randomUUID());
    var tmp= msgDir.resolve(name+".tmp");
    try{
      Fs.writeString(tmp, message, StandardOpenOption.CREATE_NEW);
      try{ Fs.move(tmp, msgDir.resolve(name+".msg")); }
      catch (FileAlreadyExistsException e){ Fs.deleteIfExists(tmp); throw e; }
    }
    catch (FileAlreadyExistsException e){ leave(msgDir, message); }
  }
  /// The manager of that folder never sees the folder deleted in part: the folder is renamed in its folder, then deleted.
  private static void removeAtOnce(Path dir){
    if (!Fs.exists(dir)){ return; }
    try (var ch= Fs.openChannel(dir.resolve("instance.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)){
      if (ch.tryLock() == null){ quitted(dir, ch); }
      var gone= dir.resolveSibling(dir.getFileName()+"_"+UUID.randomUUID().toString().replace("-", ""));
      Fs.move(dir, gone, StandardCopyOption.ATOMIC_MOVE);
      Fs.rmTree(gone);
    }
    catch (IOException e){ throw new UncheckedIOException(e); }
  }
  private static void quitted(Path dir, FileChannel ch) throws IOException{
    try (var server= new ServerSocket(0, 1, InetAddress.getLoopbackAddress())){
      leave(dir.resolve("messages"), "Quit\nPort:"+server.getLocalPort());
      Thread.startVirtualThread(() -> ended(server));
      ch.lock();
    }
  }
  private static void ended(ServerSocket server){
    try (var s= server.accept(); var in= new BufferedReader(new InputStreamReader(s.getInputStream(), UTF_8))){
      s.setSoTimeout(stalled);
      var process= ProcessHandle.of(Long.parseLong(in.readLine()));
      if (!progressing(in)){ process.ifPresent(ProcessHandle::destroyForcibly); }
    }
    catch (IOException _){}
  }
  private static final int stalled= 10_000;
  private static boolean progressing(BufferedReader in) throws IOException{
    var last= "";
    var since= System.currentTimeMillis();
    try{
      for (var line= in.readLine(); line != null; line = in.readLine()){
        if (!line.equals(last)){ last = line; since = System.currentTimeMillis(); }
        if (System.currentTimeMillis()-since >= stalled){ return false; }
      }
      return true;
    }
    catch (SocketTimeoutException _){ return false; }
  }
  public static void replaced(Path launcher, Path command){
    JavacTool.managerFolderOf(command).filter(d -> !JavacTool.managerFolderOf(launcher).equals(Optional.of(d))).ifPresent(Main::removeAtOnce);
  }
  public record Answer(List<String> taken, List<String> givenUp){}
  @SuppressWarnings("serial") public static final class Refusal extends RuntimeException{
    private final String todo;
    private final List<String> held;
    public Refusal(String error, String todo, List<String> held){ super(error); this.todo= todo; this.held= held; }
    public RuntimeException error(){ return new UserError(getMessage()); }
  }
  public static Optional<Refusal> reconcile(Path launcher, String name, List<Icon> extensions, List<String> taken, List<WindowIcon> windows, Path icon){
    try{
      FileAssociations.reconcile(identity(launcher), name, belongsToFamily, launcher, extensions, taken, windows, icon, c -> replaced(launcher, c),
        s -> new Refusal(Violation.associationsAmbiguous(s), Violation.removeAllButOne, List.of()),
        h -> new Refusal(Violation.associationNotOurs(h), Violation.tickToHold, List.copyOf(h.keySet())),
        h -> new Refusal(Violation.associationNotTaken(h), Violation.tickToTake, List.copyOf(h.keySet())),
        s -> new Refusal(Violation.associationNotWritable(s), Violation.givePermission, List.of()),
        Violation::associationLeftHalfDone);
      return Optional.empty();
    }
    catch (Refusal r){ return Optional.of(r); }
  }
  public static final int asks= 3;
  public static void hold(Function<List<String>,Optional<Refusal>> reconcile, Function<Refusal,Answer> ask, BiConsumer<Refusal,List<String>> givenUp){
    var taken= new LinkedHashSet<String>();
    var asked= 0;
    for (var r= reconcile.apply(List.of()); r.isPresent(); r = reconcile.apply(List.copyOf(taken))){
      if (asked == asks){ throw r.get().error(); }
      asked += 1;
      var a= ask.apply(r.get());
      taken.addAll(a.taken());
      a.givenUp().forEach(taken::remove);
      if (!a.givenUp().isEmpty()){ givenUp.accept(r.get(), a.givenUp()); }
    }
  }
  public static Answer ask(Refusal r){
    var text= new JTextArea(r.getMessage()+"\n\n"+r.todo);
    text.setEditable(false);
    text.setRows(Math.min(20, text.getLineCount()));
    var boxes= r.held.stream().map(e -> new JCheckBox(e, true)).toList();
    var column= new JPanel(new GridLayout(0, 1));
    boxes.forEach(column::add);
    var ticks= new JScrollPane(column);
    ticks.getViewport().setPreferredSize(new Dimension(column.getPreferredSize().width, Math.min(300, column.getPreferredSize().height)));
    var shown= boxes.isEmpty() ? new Object[]{new JScrollPane(text)} : new Object[]{new JScrollPane(text), ticks};
    var ok= JOptionPane.showConfirmDialog(null, shown, "Fearless", boxes.isEmpty() ? JOptionPane.DEFAULT_OPTION : JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION;
    return new Answer(boxes.stream().filter(b -> ok && b.isSelected()).map(JCheckBox::getText).toList(), boxes.stream().filter(b -> !ok || !b.isSelected()).map(JCheckBox::getText).toList());
  }
  public static void eradicateAll(){ FileAssociations.eradicateAll(belongsToFamily, Violation::associationLeftHalfDone); }
  private static void offerAssociation(Path appDir){
    var launcher= ProcessHandle.current().info().command().map(Path::of);
    if (launcher.isEmpty() || GraphicsEnvironment.isHeadless()){ return; }
    var l= launcher.get();
    if (!belongsToFamily.test(identity(l))){ return; }
    var icon= appDir.resolve("icon.png");
    hold(taken -> reconcile(l, "Fearless Portable", List.of(new Icon(".fearless", l, icon)), taken, List.of(), icon), Main::askOnDesktop, (r, _) -> { throw r.error(); });
  }
  private static Answer askOnDesktop(Refusal r){
    var asked= new FutureTask<>(() -> ask(r));
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
