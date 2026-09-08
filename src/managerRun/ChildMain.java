package managerRun;

import java.nio.file.Path;
import java.util.Optional;

import java.util.List;

import coordinator.CapabilityEnvironment;
import coordinator.Coordinator;
import core.OtherPackages;
import core.E.Literal;
import fileSupport.NativeLocaleForcer;
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
    var c= new Coordinator(){
      @Override public Optional<Path> baseCachePath(){ return Optional.of(appDir.resolve("stdLib").resolve("baseCache")); }
      @Override public BackendTools backendTools(String pkgName, SourceOracle oracle, OtherPackages other, List<Literal> core, Path rootDir, CapabilityEnvironment capabilities){
        return BackendTools.of(pkgName, core, rootDir, docBuilder(pkgName,oracle,other,core,rootDir), rt, capabilities);
      }
    };
    c.compile(project, c.sourceOracle(base));
  }
}
