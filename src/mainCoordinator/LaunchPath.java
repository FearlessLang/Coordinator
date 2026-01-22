package mainCoordinator;
import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.Consumer;

import coordinatorMessages.CacheCorruptionError;
import coordinatorMessages.UserExit;

public final class LaunchPath{
  public static Path fetchCalledOn(String[] args){
    if (args.length == 0){ return forMac(); }
    if (args[0].startsWith("-psn_")){ return forMac(); }
    return normalizeArg0(args[0]);
  }
  private static final ArrayBlockingQueue<Path> opened= new ArrayBlockingQueue<>(16);
  private static Path forMac(){
    if (!Desktop.isDesktopSupported()){ throw UserExit.desktopUnsupported(); }
    var d= Desktop.getDesktop();
    if (!d.isSupported(Desktop.Action.APP_OPEN_FILE)){ throw UserExit.openFileUnsupported(); }
    var c= new Consumer<File>(){
      boolean noOp= false;
      @Override public void accept(File f){
        if (noOp){ return; }
        noOp = true;
        opened.offer(f.toPath().toAbsolutePath().normalize());
      }};
    d.setOpenFileHandler(e -> e.getFiles().forEach(c));
    try{ return opened.take(); }
    catch(InterruptedException ie){
      Thread.currentThread().interrupt();
      throw CacheCorruptionError.interruptedWhileWaitingForProject();
    }
  }
  private static Path normalizeArg0(String s){
    if (s.isEmpty()){ throw UserExit.emptyLaunchArg(); }
    Path p; try{ p= s.startsWith("file:") ? Path.of(URI.create(s)) : Path.of(s); }
    catch(RuntimeException ex){ throw UserExit.badLaunchArg(s); }
    if (!p.isAbsolute()){ throw UserExit.nonAbsoluteLaunchArg(s); }
    return p.normalize();
  }
}