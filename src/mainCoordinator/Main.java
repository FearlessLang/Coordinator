package mainCoordinator;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.awt.Desktop;
import java.awt.Font;
import java.awt.Taskbar;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import coordinator.Coordinator;
import coordinatorMessages.CacheCorruptionError;
import coordinatorMessages.UserExit;
import coordinatorMessages.UserTreeError;
import tools.Fs;
import tools.JavacTool;

public final class Main{
  private static final AtomicInteger macSpawnOk= new AtomicInteger(0);
  public static void main(String[] args){
    try{ if (!hasConsoleFlag()){ hookStd(); } run(args); }
    catch(UserExit e){ System.err.print(e.getMessage()); }
    catch(UserTreeError e){ System.err.print(e.getMessage()); }
    catch(CacheCorruptionError e){ System.err.print(e.getMessage()); }
    catch(Throwable t){ 
      System.err.println(t.getClass().getCanonicalName());
      System.err.print(UserExit.crash(t));
    }
  }
  private static void run(String[] args) throws InvocationTargetException, InterruptedException, ExecutionException{
    var appDir= reqAppDir();
    Optional<Path> launch= launchPath(args);
    if (Fs.isMac()){ registerMacSpawnHandler(appDir); }
    if (!launch.isPresent()){
      if (Fs.isMac()){ Thread.sleep(1000); }
      if ( macSpawnOk.get() != 0){ return; }
    }          
    Path l= launch.orElseThrow(UserExit::mustOpenFearlessProjectFile);
    var project= Files.isDirectory(l) ? l : l.getParent();
    var base= appDir.resolve("stdLib").resolve("base");
    var rt= appDir.resolve("stdLib").resolve("rt");
    new Coordinator(){
      @Override public Path stLibPath(){ return base; }
      @Override public Path rtPath(){ return rt; }
    }.main(project);
  }
  private static void registerMacSpawnHandler(Path appDir){
    if (!Desktop.isDesktopSupported()){ throw UserExit.desktopUnsupported(); }
    var d= Desktop.getDesktop();
    if (!d.isSupported(Desktop.Action.APP_OPEN_FILE)){ throw UserExit.openFileUnsupported(); }
    d.setOpenFileHandler(e->e.getFiles().forEach(f->spawnMac(appDir, f.toPath())));
  }
  private static void spawnMac(Path appDir, Path file){
    var bundle= appDir.getParent().getParent();
    try{ new ProcessBuilder("open","-n","-a",bundle.toString(),"--args",file.toString()).start(); }
    catch(Exception e){ throw new RuntimeException(e); }
    macSpawnOk.incrementAndGet();
  }
  private static void hookStd() throws InvocationTargetException, InterruptedException, ExecutionException{
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
      var icon= ImageIO.read(reqAppDir().resolve("icon.png").toFile());
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
  private static boolean hasConsoleFlag(){ return JavacTool.consoleKey.equals(System.getProperty(JavacTool.launcherKey)); }
  private static Optional<Path> launchPath(String[] args){
    return Stream.of(args)
      .filter(a->!a.startsWith("-psn_"))
      .findFirst().map(Main::normalize);
  }
  private static Path normalize(String s){
    if (s.isEmpty()){ throw UserExit.emptyLaunchArg(); }
    Path p; try{ p= s.startsWith("file:") ? Path.of(URI.create(s)) : Path.of(s); }
    catch(RuntimeException ex){ throw UserExit.badLaunchArg(s); }
    if (!p.isAbsolute()){ throw UserExit.nonAbsoluteLaunchArg(s); }
    return p.normalize();
  }
  private static Path reqAppDir(){
    var s= System.getProperty(JavacTool.appDirKey);
    if (s == null){ throw UserExit.mustUseLauncherMissingAppDir(); }
    var p= Path.of(s);
    if (!p.isAbsolute()){ throw UserExit.launcherProvidedNonAbsoluteAppDir(s); }
    return p;
  }
}
class Utf8Sink extends OutputStream{
  private final Consumer<String> out;
  private final CharsetDecoder decoder= StandardCharsets.UTF_8.newDecoder()
    .onMalformedInput(CodingErrorAction.REPLACE)
    .onUnmappableCharacter(CodingErrorAction.REPLACE);
  private ByteBuffer inBuf= ByteBuffer.allocate(8192);
  private final CharBuffer outBuf= CharBuffer.allocate(8192);
  Utf8Sink(Consumer<String> out){ this.out= out; }
  @Override public void write(int b){ write(new byte[]{(byte)b}, 0, 1); }
  @Override public synchronized void write(byte[] b, int off, int len){
    if (inBuf.remaining() < len){ expandBuffer(inBuf.position() + len); }
    inBuf.put(b, off, len);
    inBuf.flip();
    decodeLoop(false);
    inBuf.compact();
  }
  @Override public synchronized void close(){
    inBuf.flip();
    decodeLoop(true);
    while(true){
      outBuf.clear();
      var r= decoder.flush(outBuf);
      drainOutput();
      if (r.isUnderflow()){ break; }
    }
    inBuf.clear();
  }
  private void decodeLoop(boolean end){
    while(true){
      outBuf.clear();
      var r= decoder.decode(inBuf, outBuf, end);
      drainOutput();
      if (r.isUnderflow()){ break; }
    }
  }
  private void drainOutput(){
    outBuf.flip();
    if (outBuf.hasRemaining()){ out.accept(outBuf.toString()); }
  }
  private void expandBuffer(int neededSize){
    int newCap= Math.max(inBuf.capacity() * 2, neededSize);
    ByteBuffer newBuf= ByteBuffer.allocate(newCap);
    inBuf.flip();
    newBuf.put(inBuf);
    inBuf= newBuf;
  }
}