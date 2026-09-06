// Fast check: Commons + Frontend, real JPMS modules, -Werror. See development-guide.txt. Run from Coordinator/test:
//   java --module-path ../../Commons/Commons.jar --add-modules Commons mainCoordinator/FrontendOnly.java <extraJarsDir>
package mainCoordinator;

import java.nio.file.Path;

public class FrontendOnly{
  public static void main(String[] args) throws Exception{
    var extraJars= Path.of(args[0]);
    ModularBuild.commons();
    ModularBuild.frontendMain();
    ModularBuild.frontendTest(extraJars);
    ModularBuild.runJUnit(ModularBuild.out.resolve("frontend-test"));
  }
}
