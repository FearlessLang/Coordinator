// java --module-path ../../../Commons/Commons.jar --add-modules Commons scripts/TestAllFrontendCoordinatorIntegration.java
package scripts;

public class TestAllFrontendCoordinatorIntegration{
  public static void main(String[] args) throws InterruptedException{
    TestAllFrontend.main(args);
    ModularBuild.coordinatorTest();
    ModularBuild.runJUnit(ModularBuild.out.resolve("coordinator-test"));
  }
}
