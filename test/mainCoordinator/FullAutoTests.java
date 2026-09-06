// Complete check: everything including integrationTests, real JPMS modules, -Werror. See development-guide.txt. Run from Coordinator/test:
//   java --module-path ../../Commons/Commons.jar --add-modules Commons mainCoordinator/FullAutoTests.java
package mainCoordinator;

public class FullAutoTests{
  public static void main(String[] args) throws Exception{
    ModularBuild.commons();
    ModularBuild.frontendMain();
    ModularBuild.frontendTest();
    ModularBuild.runJUnit(ModularBuild.out.resolve("frontend-test"));
    ModularBuild.coordinatorTest();
    ModularBuild.runJUnit(ModularBuild.out.resolve("coordinator-test"));
  }
}
