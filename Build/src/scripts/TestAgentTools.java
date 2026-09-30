// java --module-path ../../../Commons/Commons.jar --add-modules Commons scripts/TestAgentTools.java [TestClassName [agent]]
package scripts;

import java.nio.file.Path;
import java.util.List;

import resources.ResolveResource;
import tools.Fs;
import tools.JavaTool;

public class TestAgentTools{
  public static void main(String[] args) throws InterruptedException{
    assert args.length<2 || args.length==2 && args[1].equals("agent");
    var channel= ModularBuild.out.resolve("pilot");
    Fs.rmTree(channel);
    if (stale(ResolveResource.portableFolderOut.resolve("fearlessBin"+ResolveResource.versionId))){ DeployPortableFearless.main(args); }
    if (stale(ResolveResource.managedFolderOut.resolve("fearlessManaged"+ResolveResource.versionId))){ DeployManagedFearless.main(args); }
    ModularBuild.commons();
    ModularBuild.frontendMain();
    ModularBuild.coordinatorMain();
    ModularBuild.controllerTest();
    var classes= ModularBuild.out.resolve("controller-test");
    if (args.length==0){ ModularBuild.runJUnit(classes, "--include-package=agentTools"); return; }
    JavaTool.runMain(args.length==1 ? List.of("-ea") : List.of("-ea","-Dpilot="+channel), classes, ModularBuild.mods, "org.junit.platform.console.ConsoleLauncher",
      "execute", "--class-path", classes.toString(), "--select-class=agentTools."+args[0], "--details=summary", "--disable-ansi-colors");
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
