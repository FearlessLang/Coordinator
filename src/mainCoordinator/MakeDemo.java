package mainCoordinator;
import tools.Fs;
import tools.OpenPath;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MakeDemo{
  public static final String markerContent= "Fearless project: open this file to work on the folder it is in.\n";
  public static void of(Path projectDir){
    hello(projectDir,"demo","Hello");
    var startThere= Files.exists(projectDir.resolve("start.fearless"));
    if (!startThere){ Fs.writeUtf8(projectDir.resolve("start.fearless"), markerContent); }
    OpenPath.open(projectDir);
  }
  public static void hello(Path projectDir, String pkg, String type){
    var pkgDir= projectDir.resolve("_"+pkg);
    Fs.ensureDir(pkgDir);
    var fearThere= Files.exists(pkgDir.resolve("_rank_app.fear"));
    if (!fearThere){ Fs.writeUtf8(pkgDir.resolve("_rank_app.fear"), rankAppFear.formatted(type)); }
  }

  private static final String rankAppFear="""
use base.Main as Main;
use base.Lists as List;
use base.Num as Num;
use base.Void as Void;
use base.Str as Str;

%s: Main { sys -> sys.out.println(`Hello World!`) }
""";
}