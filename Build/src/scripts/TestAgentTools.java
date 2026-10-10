// java --module-path ../../../Commons/Commons.jar --add-modules Commons scripts/TestAgentTools.java <desk> <agent> [TestClassName] <channelFolder> <filesIOFolder>
// a round of these tests follows scripts/agent_tools_protocol.txt
package scripts;

import java.nio.file.Path;
import java.util.List;

import resources.ResolveResource;
import tools.Fs;
import tools.JavaTool;

public class TestAgentTools{
  public static void main(String[] args) throws InterruptedException{
    assert (args.length==4 || args.length==5) && List.of("true","false").contains(args[1]);
    var channel= Path.of(args[args.length-2]);
    var app= ResolveResource.managedFolderOut.resolve("fearlessManaged"+ResolveResource.versionId);
    if (stale(app)){ DeployManagedFearless.main(args); }
    Fs.copyFresh(app,Path.of(args[args.length-1]).resolve(app.getFileName()));
    ModularBuild.commons();
    ModularBuild.frontendMain();
    ModularBuild.coordinatorMain();
    ModularBuild.controllerTest();
    var classes= ModularBuild.out.resolve("controller-test");
    var select= args.length==5 ? "--select-class=agentTools."+args[2] : "--select-package=agentTools";
    var properties= List.of("-ea","-Ddesk="+args[0],"-Dagent="+args[1],"-DchannelFolder="+channel,"-DfilesIOFolder="+args[args.length-1]);
    Fs.cleanDir(channel);
    try{ JavaTool.runMain(properties, classes, ModularBuild.mods, "org.junit.platform.console.ConsoleLauncher", "execute", "--class-path", classes.toString(), select, "--details=summary", "--disable-ansi-colors"); }
    finally{ Fs.cleanDir(channel); }
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
