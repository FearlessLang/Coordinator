package mainCoordinator;

import java.nio.file.Path;

import coordinatorMessages.UserExit;
import tools.JavacTool;

/// How a Fearless main learns where it was started from.
/// Every launcher jpackage produces for us passes -Dapp.launcher=.. and -Dapp.dir=$APPDIR
/// (see JavacTool.javaOptions): $APPDIR is the folder jpackage fills from --app-content,
/// so it is inside the app image, at a place that differs per operating system.
/// No main has to guess: a main started without a launcher has no app image around it at
/// all, and says so instead of inventing an answer from its own class location.
public final class LauncherProps{
  private LauncherProps(){}
  public static boolean hasConsoleFlag(){ return JavacTool.consoleKey.equals(System.getProperty(JavacTool.launcherKey)); }
  public static Path reqAppDir(){
    var launcher= System.getProperty(JavacTool.launcherKey);
    var appDir= System.getProperty(JavacTool.appDirKey);
    if (launcher == null || appDir == null){ throw UserExit.mustUseLauncher(); }
    var res= Path.of(appDir);
    if (!res.isAbsolute()){ throw UserExit.mustUseLauncher(); }
    return res;
  }
}
