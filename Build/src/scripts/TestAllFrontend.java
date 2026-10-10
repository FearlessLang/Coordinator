// java --module-path ../../../Commons/Commons.jar --add-modules Commons scripts/TestAllFrontend.java
package scripts;

public class TestAllFrontend{
  public static void main(String[] args) throws InterruptedException{
    ModularBuild.commons();
    ModularBuild.frontendMain();
    ModularBuild.runJUnit(ModularBuild.frontendTest(), "--exclude-package=instantiationSweep");
  }
}
