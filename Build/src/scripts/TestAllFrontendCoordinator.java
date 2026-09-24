// java --module-path ../../../Commons/Commons.jar --add-modules Commons scripts/TestAllFrontendCoordinator.java
package scripts;

public class TestAllFrontendCoordinator{
  public static void main(String[] args) throws InterruptedException{
    TestAllFrontend.main(args);
    ModularBuild.coordinatorTest();
    ModularBuild.runJUnit(ModularBuild.out.resolve("coordinator-test"), "--exclude-package=integrationTests");
  }
}
