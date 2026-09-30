// java --module-path ../../../Commons/Commons.jar --add-modules Commons scripts/TestAgentTools.java [TestClassName]
package scripts;

import java.util.List;

import tools.Fs;
import tools.JavaTool;

public class TestAgentTools{
  public static void main(String[] args) throws InterruptedException{
    assert args.length<=1;
    var channel= ModularBuild.out.resolve("pilot");
    Fs.rmTree(channel);
    ModularBuild.commons();
    ModularBuild.frontendMain();
    ModularBuild.coordinatorMain();
    ModularBuild.controllerTest();
    var classes= ModularBuild.out.resolve("controller-test");
    if (args.length==0){ ModularBuild.runJUnit(classes, "--include-package=agentTools"); return; }
    JavaTool.runMain(List.of("-ea","-Dpilot="+channel), classes, ModularBuild.mods, "org.junit.platform.console.ConsoleLauncher",
      "execute", "--class-path", classes.toString(), "--select-class=agentTools."+args[0], "--details=summary", "--disable-ansi-colors");
  }
}
