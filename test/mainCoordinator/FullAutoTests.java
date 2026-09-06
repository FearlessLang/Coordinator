// Slow, complete modular check: all Frontend tests, all Coordinator tests
// (including integrationTests). Real JPMS modules, -Werror.
//
// Does not build or publish the shippable app-images (fearlessBin,
// fearlessManaged) - that's deploy-fearless-pages's job, run separately
// afterward if this passes.
//
// Run from Coordinator/test (Commons.jar is checked into the Commons repo,
// ready right after align-branch; --add-modules names its module-info.java):
//   java --module-path ../../Commons/Commons.jar --add-modules Commons mainCoordinator/FullAutoTests.java <extraJarsDir>
// <extraJarsDir> is a directory of jars needed on top of Coordinator/externalJars:
// junit-jupiter-api, junit-platform-commons, junit-platform-console-standalone,
// opentest4j, apiguardian-api, jspecify.
package mainCoordinator;

import java.nio.file.Path;

public class FullAutoTests{
  public static void main(String[] args) throws Exception{
    var extraJars= Path.of(args[0]);
    ModularBuild.commons();
    ModularBuild.frontendMain();
    ModularBuild.frontendTest(extraJars);
    ModularBuild.runJUnit(ModularBuild.out.resolve("frontend-test"));
    ModularBuild.coordinatorTest(extraJars);
    ModularBuild.runJUnit(ModularBuild.out.resolve("coordinator-test"));
  }
}
