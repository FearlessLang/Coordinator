package integrationTests;

import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.AssertionFailedError;

import coordinator.Coordinator;
import mainCoordinator.ResolveResource;
import testHelperFs.FsDsl;
import tools.JavacTool;
import userMessages.UserError;

/// Two assets with the same base name (here "foo.txt" and "foo.png") both map to the
/// auto-loaded type name "Foo". realSourceOracle.SourceOracleWithAutoload now rejects this
/// itself, before the frontend ever sees the synthetic autoloaded_assets.fear file, with a
/// message that names the two real files that collide instead of blaming a file the user
/// never wrote. Exercised end-to-end through Coordinator.main, like RunIntegration's tests.
public class AssetAutoloadCollisionTest {
  static{ utils.Err.setUp(AssertionFailedError.class, Assertions::assertEquals, Assertions::assertTrue); }

  @Test void collidingAssetBaseNamesAreRejected(@TempDir Path tmp) throws InterruptedException{
    Path root= tmp.resolve("root");
    UserError.root= root;
    FsDsl.materialize(root, """
_col/_rank_app.fear
iii

jjj
_col/foo.txt
iii
hello
jjj
_col/foo.png
iii
ignored
""");
    System.setProperty(JavacTool.appDirKey,ResolveResource.stLibPath.getParent()
      .resolve("fearlessArtefact","fearless","app").toString());
    var c= new Coordinator(){
      public Path rtPath(){    return ResolveResource.stLibRTPath; }
      public Path stLibPath(){ return ResolveResource.stLibPath; }
      public Path modsPath(){  return ResolveResource.coordinatorJars; }
    };
    var ex= Assertions.assertThrows(UserError.class, ()->c.main(root));
    utils.Err.strCmp("""
Invalid path in this project folder.

Root: [###]
Path: "_col/foo.txt"

What went wrong
- Auto-loading both of these files would produce two declarations of the same type name.
  Name 1: "fear:/_col/foo.png"
  Name 2: "fear:/_col/foo.txt"
  Reason: Both would auto-load as the type "Foo".

How to fix
- Rename one of them so they are clearly distinct.
- Avoid two assets whose folder and file name produce the same auto-loaded type name.

We check this so that you[###]
""", ex.getMessage());
  }
}
