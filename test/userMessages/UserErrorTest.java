package userMessages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.AssertionFailedError;

import mainCoordinator.Main;
import metaParser.PrettyFileName;
import resources.ResolveResource;
import testHelperFs.FsDsl;
import tools.Fs;

final class UserErrorTest{
  static{ utils.Err.setUp(AssertionFailedError.class, Assertions::assertEquals, Assertions::assertTrue); }
  @Test void pathIsUnchangedForPlainAscii(){
    assertEquals("  C:\\data\\proj", UserError.path("C:\\data\\proj"));
  }
  @Test void pathStaysWithinWhitelistForNonAscii(){
    assertWhitelisted(UserError.path("C:\\Users\\caf\u00e9\\proj"));
  }
  @Test void displayFileNameStaysWithinWhitelist(@TempDir Path tmp){
    var nonAscii= tmp.resolve("caf\u00e9_\u4e2d\u6587");
    assertWhitelisted(PrettyFileName.displayFileName(nonAscii.toUri()));
  }
  @Test void aRunShowsTheRootOfItsOwnProject(@TempDir Path tmp){
    UserError.root = tmp.resolve("elsewhere");
    var project= tmp.resolve("project");
    FsDsl.materialize(project, """
_pkg/my.dir/a.fear
iii
X
""");
    var ex= assertThrows(UserError.class, () -> Main.run(project, ResolveResource.stLibPath, ResolveResource.stLibRTPath));
    utils.Err.strCmp("""
Invalid path in this project folder.

Root: %s
Path: "_pkg/my.dir"

What went wrong
- A visible folder/file name contains an unsupported character: ".".
  Visible names may use only lowercase letters (a-z), digits (0-9), and underscore (_).

How to fix
- Rename it to use only lowercase letters, digits, and underscores.
  Examples: "foo_bar2", "src1", "_cache".

We check this so that you[###]
""".formatted(PrettyFileName.displayFileName(project.toUri())), ex.getMessage());
  }
  private static void assertWhitelisted(String s){
    assertTrue(s.codePoints().allMatch(cp -> cp < 128 && Fs.allowed.indexOf((char)cp) >= 0), s);
  }
}
