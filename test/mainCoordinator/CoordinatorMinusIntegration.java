// Fast check: + Coordinator minus integrationTests, real JPMS modules, -Werror. See development-guide.txt. Run from Coordinator/test:
//   java --module-path ../../Commons/Commons.jar --add-modules Commons mainCoordinator/CoordinatorMinusIntegration.java
package mainCoordinator;

public class CoordinatorMinusIntegration{
  public static void main(String[] args) throws Exception{
    ModularBuild.commons();
    ModularBuild.frontendMain();
    ModularBuild.coordinatorTest();
    ModularBuild.runJUnit(ModularBuild.out.resolve("coordinator-test"), "--exclude-package=integrationTests");
  }
}
