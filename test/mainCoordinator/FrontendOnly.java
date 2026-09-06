// Fast modular check: Commons + Frontend (main and test), as real JPMS modules
// with -Xlint:all,-auxiliaryclass,-missing-explicit-ctor -Werror.
//
// Run from Coordinator/test (Commons.jar is checked into the Commons repo,
// ready right after align-branch; --add-modules names its module-info.java):
//   java --module-path ../../Commons/Commons.jar --add-modules Commons mainCoordinator/FrontendOnly.java <extraJarsDir>
// <extraJarsDir> is a directory of jars needed on top of Coordinator/externalJars:
// junit-jupiter-api, junit-platform-commons, junit-platform-console-standalone,
// opentest4j, apiguardian-api, jspecify.
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
