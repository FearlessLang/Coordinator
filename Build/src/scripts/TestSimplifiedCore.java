// java --module-path ../../../Commons/Commons.jar --add-modules Commons scripts/TestSimplifiedCore.java
package scripts;

import resources.ResolveResource;

public final class TestSimplifiedCore{
  public static void main(String[] args) throws InterruptedException{
    ModularBuild.commons();
    ModularBuild.runJUnit(args, ModularBuild.test(ResolveResource.frontendSrc.getParent().resolveSibling("simplified_core").resolve("src"), "simplified-core-test"));
  }
}
