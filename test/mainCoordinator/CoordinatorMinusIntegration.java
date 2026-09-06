// Fast check: + Coordinator minus integrationTests, real JPMS modules, -Werror. See development-guide.txt. Run from Coordinator/test:
//   java --module-path ../../Commons/Commons.jar --add-modules Commons mainCoordinator/CoordinatorMinusIntegration.java <extraJarsDir>
package mainCoordinator;

import java.nio.file.Path;

public class CoordinatorMinusIntegration{
  public static void main(String[] args) throws Exception{
    var extraJars= Path.of(args[0]);
    ModularBuild.commons();
    ModularBuild.frontendMain();
    ModularBuild.coordinatorTest(extraJars);
    ModularBuild.runJUnit(ModularBuild.out.resolve("coordinator-test"), "--exclude-package=integrationTests");
  }
}
