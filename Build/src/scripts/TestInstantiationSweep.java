// java --module-path ../../../Commons/Commons.jar --add-modules Commons scripts/TestInstantiationSweep.java
package scripts;

public class TestInstantiationSweep{
  public static void main(String[] args) throws InterruptedException{
    ModularBuild.commons();
    ModularBuild.frontendMain();
    ModularBuild.frontendTest();
    ModularBuild.runJUnit(ModularBuild.out.resolve("frontend-test"), "--include-package=instantiationSweep");
  }
}
