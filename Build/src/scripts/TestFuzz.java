// java --module-path ../../../Commons/Commons.jar --add-modules Commons scripts/TestFuzz.java
package scripts;

public final class TestFuzz{
  public static void main(String[] args) throws InterruptedException{
    ModularBuild.commons();
    ModularBuild.frontendMain();
    ModularBuild.runJUnit(args, ModularBuild.frontendTest(), "--include-package=fuzz");
  }
}
