// java --module-path ../../Commons/Commons.jar --add-modules Commons mainCoordinator/TestAllController.java
package mainCoordinator;

public class TestAllController{
  public static void main(String[] args) throws InterruptedException{
    ModularBuild.commons();
    ModularBuild.frontendMain();
    ModularBuild.coordinatorMain();
    ModularBuild.controllerTest();
    ModularBuild.runJUnit(ModularBuild.out.resolve("controller-test"));
  }
}