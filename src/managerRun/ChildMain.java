package managerRun;

import java.nio.file.Path;
import java.util.Optional;

import coordinator.Coordinator;
import fileSupport.NativeLocaleForcer;
import managerInfo.FolderFacts;
import managerInfo.ProblemReport;
import tools.ChildJvm;
import tools.Fs;
import tools.JavacTool;
import userMessages.UserError;
import userMessages.Violation;

public class ChildMain{
  public static void main(String[] args){
    NativeLocaleForcer.forceEnglish();
    ChildJvm.watchParent();
    var project= Path.of(args[0]);
    var reports= Path.of(args[1]);
    ProblemReport.write(reports, "");
    //Puts .out in place before the compile stamps .fearless_out, so the project
    //root's mtime cannot end up newer than that stamp.
    Fs.ensureDir(project.resolve(FolderFacts.runDir));
    var exitCode= 0;
    var problem= "";
    try{ compile(project); }
    catch(UserError e){ exitCode= 1; problem= e.getMessage(); System.err.print(problem); }
    catch(Throwable t){ exitCode= 2; System.err.print(UserError.crash(t)); }
    ProblemReport.write(reports, problem);
    System.out.flush();
    System.err.flush();
    System.exit(exitCode);
  }
  private static void compile(Path project){
    var appDir= JavacTool.reqAppDir(Violation::mustUseLauncher);
    UserError.root= project;
    var base= appDir.resolve("stdLib").resolve("base");
    var rt= appDir.resolve("stdLib").resolve("rt");
    var c= new Coordinator(){
      @Override public Optional<Path> baseCachePath(){ return Optional.of(appDir.resolve("stdLib").resolve("baseCache")); }
      @Override public Path rtPath(){ return rt; }
    };
    c.compile(project, c.sourceOracle(base));
  }
}
