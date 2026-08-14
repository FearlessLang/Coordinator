package sourceOracleTests;

import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.AssertionFailedError;

import realSourceOracle.RealSourceOracleWithZip;
import realSourceOracle.SourceOracleWithAutoload;
import testHelperFs.FsDsl;
import userMessages.UserError;

/// Two assets that map to the same generated type name (same base name, different handled
/// extensions) must not both auto-load into the synthetic autoloaded_assets.fear file: that
/// would silently declare the same type twice. SourceOracleWithAutoload.generate() must reject
/// this itself, with a message that names the two real files, instead of letting it through to
/// become a confusing "duplicate type declaration" error against a file the user never wrote.
final class SourceOracleWithAutoloadTest{
  static{ utils.Err.setUp(AssertionFailedError.class, Assertions::assertEquals, Assertions::assertTrue); }

  @Test void collidingAssetBaseNamesAreRejected(@TempDir Path tmp){
    Path root= tmp.resolve("root");
    UserError.root= root;
    FsDsl.materialize(root, """
_assets/foo.txt
iii
hello
jjj
_assets/foo.png
iii
ignored
""");
    var base= new RealSourceOracleWithZip(root);
    var ex= Assertions.assertThrows(UserError.class, ()->SourceOracleWithAutoload.of(base, "_assets"));
    utils.Err.strCmp("""
Invalid path in this project folder.

Root: [###]
Path: "_assets/foo.txt"

What went wrong
- Auto-loading both of these files would produce two declarations of the same type name.
  Name 1: "fear:/_assets/foo.png"
  Name 2: "fear:/_assets/foo.txt"
  Reason: Both would auto-load as the type "Foo".

How to fix
- Rename one of them so they are clearly distinct.
- Avoid two assets whose folder and file name produce the same auto-loaded type name.

We check this so that you[###]
""", ex.getMessage());
  }
}
