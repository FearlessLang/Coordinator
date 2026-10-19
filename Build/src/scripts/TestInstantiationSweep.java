// java --module-path ../../../Commons/Commons.jar --add-modules Commons scripts/TestInstantiationSweep.java
package scripts;

public final class TestInstantiationSweep{
  public static void main(String[] args) throws InterruptedException{
    ModularBuild.commons();
    ModularBuild.frontendMain();
    ModularBuild.runJUnit(args, ModularBuild.frontendTest(), "--include-package=instantiationSweep");
  }
}
