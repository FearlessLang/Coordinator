package managerRun;

import java.nio.file.Path;

import coordinator.Coordinator;
import fileSupport.NativeLocaleForcer;
import tools.ChildJvm;
import tools.JavacTool;
import userMessages.UserError;
import userMessages.Violation;

public class ChildMain{
  public static void main(String[] args){
    NativeLocaleForcer.forceEnglish();
    ChildJvm.watchParent();
    var exitCode= 0;
    try{ compile(Path.of(args[0])); }
    catch(UserError e){ exitCode= 1; System.err.print(e.getMessage()); }
    catch(Throwable t){ exitCode= 2; System.err.print(UserError.crash(t)); }
    System.out.flush();
    System.err.flush();
    System.exit(exitCode);
  }
  private static void compile(Path project){
    var appDir= JavacTool.reqAppDir(Violation::mustUseLauncher);
    UserError.root= project;
    var base= appDir.resolve("stdLib").resolve("base");
    var rt= appDir.resolve("stdLib").resolve("rt");
    new Coordinator(){
      @Override public Path stLibPath(){ return base; }
      @Override public Path rtPath(){ return rt; }
    }.compile(project);
  }
}
