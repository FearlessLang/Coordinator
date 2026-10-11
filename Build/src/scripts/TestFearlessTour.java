// java --module-path ../../../Commons/Commons.jar --add-modules Commons scripts/TestFearlessTour.java
package scripts;

import java.nio.file.Files;
import java.util.List;

import resources.ResolveResource;
import tools.Fs;
import utils.OneOr;

public class TestFearlessTour{
  public static void main(String[] args) throws InterruptedException{
    var tour= ResolveResource.commonsSrc.getParent().getParent().resolve("FearlessTour");
    DeployPortableFearless.main(args);
    ModularBuild.coordinatorMain();
    Fs.copyTreeFlat(tour.resolve("externalJars"), ModularBuild.mods);
    var standalone= OneOr.of("Expected exactly one junit console jar in "+ModularBuild.mods, Fs.walk(ModularBuild.mods, s->s
      .filter(p->p.getFileName().toString().startsWith("junit-platform-console-standalone"))
      .toList()).stream());
    Fs.ofV(()->Files.delete(standalone));
    ModularBuild.buildJar("FearlessTour", List.of(tour.resolve("src"), ModularBuild.resources), List.of("-requires-automatic"));
    Fs.copyTreeFlat(ResolveResource.coordinatorTestJars, ModularBuild.mods);
    ModularBuild.runJUnit(ModularBuild.out.resolve("FearlessTour"));
  }
}
