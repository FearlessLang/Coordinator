package manager;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import managerData.ManagerData;
import tools.Fs;
import tools.JavacTool;
import userMessages.Report;
import userMessages.Violation;

/// The manager's side of the Eclipse plugin (FearlessLang/EclipsePlugin). Eclipse writes
/// nothing into a project folder: it reads projects.txt and each alias's reports from
/// dir(), and asks for work through the messages folder named in manager.txt.
final class EclipseConnect{
  private EclipseConnect(){}
  static Path dir(){ return ManagerMain.managerDir.resolve("eclipse"); }
  static Path reports(String alias){ return dir().resolve(alias); }
  static String connect(Path chosen){
    var eclipse= chosen.getParent();
    if (!Files.isRegularFile(eclipse.resolve(".eclipseproduct"))){ throw Report.notAnEclipseInstall(eclipse); }
    var plugin= JavacTool.reqAppDir(Violation::mustUseLauncher).resolve("eclipsePlugin");
    var fearless= eclipse.resolve("dropins").resolve("fearless");
    Fs.copyFresh(plugin, fearless.resolve("plugins"));
    Fs.writeUtf8(fearless.resolve("manager.txt"), ManagerMain.msgDir()+"\n"+dir()+"\n");
    return """
Eclipse is now connected:
%s

Restart Eclipse: every project this manager knows appears in its workspace,
and the Fearless menu offers New project, Run and Terminate.
""".formatted(eclipse);
  }
  static void publish(List<ManagerData.Entry> known){
    var lines= known.stream().map(e->e.alias()+" "+e.path()+"\n").toList();
    var tmp= dir().resolve("projects.tmp");
    Fs.writeUtf8(tmp, String.join("",lines));
    Fs.ofV(()->Files.move(tmp, dir().resolve("projects.txt"), ATOMIC_MOVE));
  }
}
