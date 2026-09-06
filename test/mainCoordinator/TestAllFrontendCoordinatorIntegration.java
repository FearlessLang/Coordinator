// java --module-path ../../Commons/Commons.jar --add-modules Commons mainCoordinator/TestAllFrontendCoordinatorIntegration.java
package mainCoordinator;

public class TestAllFrontendCoordinatorIntegration{
  public static void main(String[] args) throws InterruptedException{
    ModularBuild.commons();
    ModularBuild.frontendMain();
    ModularBuild.frontendTest();
    ModularBuild.runJUnit(ModularBuild.out.resolve("frontend-test"));
    ModularBuild.coordinatorTest();
    ModularBuild.runJUnit(ModularBuild.out.resolve("coordinator-test"));
  }
}
