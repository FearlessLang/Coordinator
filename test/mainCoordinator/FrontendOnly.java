// Fast check: Commons + Frontend, real JPMS modules, -Werror. See development-guide.txt. Run from Coordinator/test:
//   java --module-path ../../Commons/Commons.jar --add-modules Commons mainCoordinator/FrontendOnly.java
package mainCoordinator;

public class FrontendOnly{
  public static void main(String[] args) throws Exception{
    ModularBuild.commons();
    ModularBuild.frontendMain();
    ModularBuild.frontendTest();
    ModularBuild.runJUnit(ModularBuild.out.resolve("frontend-test"));
  }
}
