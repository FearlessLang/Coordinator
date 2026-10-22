// java --module-path ../../../Commons/Commons.jar --add-modules Commons scripts/TestAllFrontend.java
package scripts;

public final class TestAllFrontend{
  public static void main(String[] args) throws InterruptedException{
    TestAllCommons.main(args);
    ModularBuild.frontendMain();
    ModularBuild.runJUnit(args, ModularBuild.frontendTest());
  }
}
