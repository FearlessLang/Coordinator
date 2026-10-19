// java --module-path ../../../Commons/Commons.jar --add-modules Commons scripts/TestAllFrontendCoordinatorIntegration.java
package scripts;

public final class TestAllFrontendCoordinatorIntegration{
  public static void main(String[] args) throws InterruptedException{
    TestAllFrontend.main(args);
    ModularBuild.runJUnit(args, ModularBuild.coordinatorTest());
  }
}
