// Fast modular check: Commons + Frontend main + Coordinator (main and test),
// excluding the slow integrationTests package. Real JPMS modules, -Werror.
//
// Does NOT build StandardLibrary's base: BaseCacheBuilder.buildInto is only
// ever called from integrationTests.RunIntegration's @BeforeAll.
// testBuildBase.TestBuildBase (which this does run) independently compiles and
// runs base itself, as a one-off unrelated to the shared baseCache the
// integration tests reuse.
//
// Run from Coordinator/test (Commons.jar is checked into the Commons repo,
// ready right after align-branch; --add-modules names its module-info.java):
//   java --module-path ../../Commons/Commons.jar --add-modules Commons mainCoordinator/CoordinatorMinusIntegration.java <extraJarsDir>
// <extraJarsDir> is a directory of jars needed on top of Coordinator/externalJars:
// junit-jupiter-api, junit-platform-commons, junit-platform-console-standalone,
// opentest4j, apiguardian-api, jspecify.
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
