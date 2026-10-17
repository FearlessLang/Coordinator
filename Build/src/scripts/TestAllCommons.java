// java --module-path ../../../Commons/Commons.jar --add-modules Commons scripts/TestAllCommons.java
package scripts;

public final class TestAllCommons{
  public static void main(String[] args) throws InterruptedException{
    ModularBuild.commons();
    ModularBuild.runJUnit(ModularBuild.commonsTest());
  }
}
