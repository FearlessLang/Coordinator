// java --module-path ../../../Commons/Commons.jar --add-modules Commons scripts/TestFuzz.java
package scripts;

public final class TestFuzz{
  public static void main(String[] args) throws InterruptedException{
    ModularBuild.commons();
    ModularBuild.frontendMain();
    ModularBuild.runJUnit(args, ModularBuild.frontendTest(), "--include-package=fuzz");
    if (args.length != 0){ return; }
    ModularBuild.mainJars();
    ModularBuild.runJUnit(args, ModularBuild.controllerTest(), "--include-package=fuzz");
  }
}
