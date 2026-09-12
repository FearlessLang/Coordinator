// java --module-path ../../../Commons/Commons.jar --add-modules Commons scripts/TestAllController.java
package scripts;

public class TestAllController{
  public static void main(String[] args) throws InterruptedException{
    ModularBuild.commons();
    ModularBuild.frontendMain();
    ModularBuild.coordinatorMain();
    ModularBuild.controllerTest();
    ModularBuild.runJUnit(ModularBuild.out.resolve("controller-test"), "--exclude-package=agentTools");
  }
}