// java --module-path ../../../Commons/Commons.jar --add-modules Commons scripts/TestAgentTools.java desk [TestClassName [agent]]
package scripts;

import java.nio.file.Path;
import java.util.List;

import resources.ResolveResource;
import tools.Fs;
import tools.JavaTool;

public class TestAgentTools{
  public static void main(String[] args) throws InterruptedException{
    var form= args.length==1 || args.length==2 || args.length==3 && args[2].equals("agent");
    if (!form){ System.err.println("Usage: TestAgentTools.java <desk> [<TestClassName> [agent]]\nThe desk names the recordings to use, for example ubuntu-gnome or windows."); System.exit(1); }
    var channel= ModularBuild.out.resolve("pilot");
    Fs.rmTree(channel);
    if (stale(ResolveResource.portableFolderOut.resolve("fearlessBin"+ResolveResource.versionId))){ DeployPortableFearless.main(args); }
    if (stale(ResolveResource.managedFolderOut.resolve("fearlessManaged"+ResolveResource.versionId))){ DeployManagedFearless.main(args); }
    ModularBuild.commons();
    ModularBuild.frontendMain();
    ModularBuild.coordinatorMain();
    ModularBuild.controllerTest();
    var classes= ModularBuild.out.resolve("controller-test");
    var jvm= args.length==3 ? List.of("-ea","-Ddesk="+args[0],"-Dpilot="+channel) : List.of("-ea","-Ddesk="+args[0]);
    var tests= args.length==1 ? ".*" : "agentTools\\."+args[1];
    JavaTool.runMain(jvm, classes, ModularBuild.mods, "org.junit.platform.console.ConsoleLauncher", "execute", "--class-path", classes.toString(),
      "--scan-class-path="+classes, "--include-package=agentTools", "--include-classname="+tests, "--fail-if-no-tests", "--details=summary", "--disable-ansi-colors");
  }
  static boolean stale(Path app){
    var name= app.getFileName().toString();
    var built= Fs.lastModified(Fs.isWindows() ? app.resolve(name+".exe") : app.resolve("bin").resolve(name));
    return sources.stream().anyMatch(s->Fs.walk(s,ps->ps.anyMatch(p->Fs.lastModified(p)>built)));
  }
  static final List<Path> sources= List.of(ResolveResource.commonsSrc, ResolveResource.frontendSrc, ResolveResource.frontendSrcModule,
    ResolveResource.coordinatorSrc, ResolveResource.coordinatorSrcModule, ResolveResource.controllerSrc, ResolveResource.controllerSrcModule,
    ResolveResource.stLibPath, ResolveResource.stLibRTPath);
}
