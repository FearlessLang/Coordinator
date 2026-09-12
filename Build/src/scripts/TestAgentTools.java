// java --module-path ../../../Commons/Commons.jar --add-modules Commons scripts/TestAgentTools.java
package scripts;

public class TestAgentTools{
  public static void main(String[] args) throws InterruptedException{
    ModularBuild.commons();
    ModularBuild.frontendMain();
    ModularBuild.coordinatorMain();
    ModularBuild.controllerTest();
    ModularBuild.runJUnit(ModularBuild.out.resolve("controller-test"), "--include-package=agentTools");
  }
}