// java --module-path ../../Commons/Commons.jar --add-modules Commons mainCoordinator/TestAllFrontend.java
package mainCoordinator;

public class TestAllFrontend{
  public static void main(String[] args) throws InterruptedException{
    ModularBuild.commons();
    ModularBuild.frontendMain();
    ModularBuild.frontendTest();
    ModularBuild.runJUnit(ModularBuild.out.resolve("frontend-test"));
  }
}
