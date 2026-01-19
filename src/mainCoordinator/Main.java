package mainCoordinator;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import coordinator.Coordinator;
import coordinatorMessages.CacheCorruptionError;
import coordinatorMessages.UserExit;
import coordinatorMessages.UserTreeError;

public final class Main{
  public static void main(String[] args){
    initIoMode();
    try{ run(args); }
    catch(UserExit ue){ UserIO.fatal(ue.getMessage()); }
    catch(UserTreeError ute){ UserIO.fatal(ute.getMessage()); }
    catch(CacheCorruptionError cce){ UserIO.fatal(cce.getMessage()); }
    catch(UncheckedIOException uio){
      var m= userMsg(uio);
      if (m != null){ UserIO.fatal(m); }
      else { UserIO.fatal(UserExit.crash(uio)); }
    }
    catch(Throwable t){ UserIO.fatal(UserExit.crash(t)); }
  }
  static void run(String[] args){
    var launch= LaunchPath.fetchCalledOn(args);
    var project= Files.isDirectory(launch) ? launch : launch.getParent();
    if (project == null){ throw UserExit.cannotFindProjectFolder(launch); }
    var appDirS= System.getProperty("fearless.appDir");
    if (appDirS == null){ throw UserExit.mustUseLauncherMissingAppDir(); }
    var appDir= Path.of(appDirS);
    if (!appDir.isAbsolute()){ throw UserExit.launcherProvidedNonAbsoluteAppDir(appDirS); }
    var base= appDir.resolve("stdLib").resolve("base");
    var rt= appDir.resolve("stdLib").resolve("rt");
    new Coordinator(){
      @Override public Path stLibPath(){ return base; }
      @Override public Path rtPath(){ return rt; }
    }.main(project);
  }
  private static void initIoMode(){
    var hasAppDir= System.getProperty("fearless.appDir") != null;
    boolean defConsole= System.console() != null && !hasAppDir;
    var s= System.getProperty("fearless.console");
    if (s != null){ defConsole = Boolean.parseBoolean(s); }
    UserIO.setConsole(defConsole);
    UserIO.hookStd();
  }
  private static String userMsg(UncheckedIOException uio){
    var c= uio.getCause();
    return c == null ? uio.getMessage() : c.getMessage();
  }
}