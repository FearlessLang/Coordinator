// java --module-path ../../../Commons/Commons.jar --add-modules Commons scripts/DeployZeroToHeroPages.java
package scripts;

import java.util.List;
import tools.JavaTool;

public class DeployZeroToHeroPages{
  public static void main(String[] args) throws InterruptedException{
    var zeroToHero= ModularBuild.root.resolve("ZeroToHero");
    ModularBuild.mainJars();
    ModularBuild.fearlessTour();
    ModularBuild.buildJar("ZeroToHeroGame", List.of(zeroToHero.resolve("src")), List.of("-requires-automatic"));
    JavaTool.runMain(List.of("-ea"), ModularBuild.out.resolve("FearlessTour"), ModularBuild.mods, "compileHtml.CompileHtml");
    JavaTool.runMain(List.of("-ea","-Duser.dir="+zeroToHero), ModularBuild.out.resolve("ZeroToHeroGame"), ModularBuild.mods, "mainZeroToHero.Main");
  }
}
