package mainCoordinator;
import tools.Fs;
import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MakeDemo{
  public static void of(Path projectDir){
    Fs.ensureDir(projectDir);
    var demoDir= projectDir.resolve("_demo");
    Fs.ensureDir(demoDir);
    var fearThere= Files.exists(demoDir.resolve("_rank_app.fear")); 
    if (!fearThere){ Fs.writeUtf8(demoDir.resolve("_rank_app.fear"), rankAppFear); }
    var startThere= Files.exists(projectDir.resolve("start.fearless"));
    if (!startThere){ Fs.writeUtf8(projectDir.resolve("start.fearless"), ""); }
    Fs.ofV(()->Desktop.getDesktop().open(projectDir.toFile()));
  }

  private static final String rankAppFear="""
use base.Main as Main;
use base.Lists as List;
use base.Num as Num;
use base.Void as Void;
use base.Str as Str;

Hello: Main { sys -> sys.out.println(`Hello World!`) }
""";
}