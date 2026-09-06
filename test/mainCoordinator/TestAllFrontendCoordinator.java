// java --module-path ../../Commons/Commons.jar --add-modules Commons mainCoordinator/TestAllFrontendCoordinator.java
package mainCoordinator;

public class TestAllFrontendCoordinator{
  public static void main(String[] args) throws InterruptedException{
    ModularBuild.commons();
    ModularBuild.frontendMain();
    ModularBuild.coordinatorTest();
    ModularBuild.runJUnit(ModularBuild.out.resolve("coordinator-test"), "--exclude-package=integrationTests");
  }
}
