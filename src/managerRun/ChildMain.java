package managerRun;

import java.nio.file.Path;
import java.util.Optional;

import java.util.List;

import coordinator.CapabilityEnvironment;
import coordinator.Coordinator;
import core.OtherPackages;
import core.E.Literal;
import docBuilder.HtmlDocBuilder;
import fileSupport.NativeLocaleForcer;
import managerInfo.ProblemReport;
import naiveBackend.BackendTools;
import tools.ChildJvm;
import tools.JavacTool;
import tools.SourceOracle;
import userMessages.UserError;
import userMessages.Violation;

public class ChildMain{
  public static void main(String[] args){
    NativeLocaleForcer.forceEnglish();
    ChildJvm.watchParent();
    var project= Path.of(args[0]);
    //Clears the previous problem; also puts .out in place before the compile stamps
    //.fearless_out, so the project root's mtime cannot end up newer than that stamp.
    ProblemReport.write(project, "");
    var exitCode= 0;
    var problem= "";
    try{ compile(project); }
    catch(UserError e){ exitCode= 1; problem= e.getMessage(); System.err.print(problem); }
    catch(Throwable t){ exitCode= 2; System.err.print(UserError.crash(t)); }
    ProblemReport.write(project, problem);
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
      @Override public BackendTools backendTools(String pkgName, SourceOracle oracle, OtherPackages other, List<Literal> core, Path rootDir, CapabilityEnvironment capabilities){
        var docs= new HtmlDocBuilder(oracle,other,core,baseCachePath().map(p->p.resolve("base.html")));
        docs.packageLocation(pkgName, rootDir.resolve("gen_java",pkgName+".html"), rootDir.getParent().resolve("auto_tests","_"+pkgName,pkgName+"_test.fear"));
        return BackendTools.of(pkgName, core, rootDir, docs, rt, capabilities);
      }
    };
    c.compile(project, c.sourceOracle(base));
  }
}
