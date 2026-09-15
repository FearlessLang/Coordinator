// java --module-path ../../../Commons/Commons.jar --add-modules Commons scripts/DeployZeroToHeroPages.java
package scripts;

import java.nio.file.Files;
import java.util.List;
import resources.ResolveResource;
import tools.Fs;
import tools.JavaTool;

public class DeployZeroToHeroPages{
  public static void main(String[] args) throws InterruptedException{
    var prefix= ResolveResource.commonsSrc.getParent().getParent();
    var fearlessTourSrc= prefix.resolve("FearlessTour","src");
    var fearlessTourExternalJars= prefix.resolve("FearlessTour","externalJars");
    var zeroToHeroSrc= prefix.resolve("ZeroToHero","src");
    var zeroToHeroRoot= prefix.resolve("ZeroToHero");

    ModularBuild.commons();
    ModularBuild.frontendMain();
    ModularBuild.coordinatorMain();
    Fs.copyTreeFlat(fearlessTourExternalJars, ModularBuild.mods);
    //an automatic module (flexmark has no module-info) pulls every other automatic
    //module into the graph, so this shaded jar's bundled junit classes collide
    //with the real org.junit.jupiter.api module also sitting in mods
    Fs.walk(ModularBuild.mods, s->s
      .filter(p->p.getFileName().toString().startsWith("junit-platform-console-standalone"))
      .toList())
      .forEach(p->Fs.ofV(()->Files.delete(p)));

    var automaticModuleReqs= List.of("-requires-automatic");
    ModularBuild.buildJar("FearlessTour", List.of(fearlessTourSrc, ModularBuild.resources), automaticModuleReqs);
    ModularBuild.buildJar("ZeroToHeroGame", List.of(zeroToHeroSrc), automaticModuleReqs);

    JavaTool.runMain(List.of("-ea"), ModularBuild.out.resolve("FearlessTour"), ModularBuild.mods, "compileHtml.CompileHtml");
    JavaTool.runMain(List.of("-ea","-Duser.dir="+zeroToHeroRoot), ModularBuild.out.resolve("ZeroToHeroGame"), ModularBuild.mods, "mainZeroToHero.Main");
  }
}
