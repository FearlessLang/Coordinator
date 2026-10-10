// java --module-path ../../../Commons/Commons.jar --add-modules Commons scripts/TestAllController.java
package scripts;

public final class TestAllController{
  public static void main(String[] args) throws InterruptedException{
    ModularBuild.mainJars();
    ModularBuild.runJUnit(ModularBuild.controllerTest(), "--exclude-package=agentTools");
  }
}