// java --module-path ../../../Commons/Commons.jar --add-modules Commons scripts/TestFearlessTour.java
package scripts;

import resources.ResolveResource;
import tools.Fs;

public final class TestFearlessTour{
  public static void main(String[] args) throws InterruptedException{
    DeployPortableFearless.main(args);
    ModularBuild.coordinatorMain();
    ModularBuild.fearlessTour();
    Fs.copyTreeFlat(ResolveResource.coordinatorTestJars, ModularBuild.mods);
    ModularBuild.runJUnit(ModularBuild.out.resolve("FearlessTour"));
  }
}
