// java --module-path ../../../Commons/Commons.jar --add-modules Commons scripts/TestAllFrontendCoordinator.java
package scripts;

public class TestAllFrontendCoordinator{
  public static void main(String[] args) throws InterruptedException{
    TestAllFrontend.main(args);
    ModularBuild.runJUnit(ModularBuild.coordinatorTest(), "--exclude-package=integrationTests");
  }
}
