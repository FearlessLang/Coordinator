package coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.SourceOracle.Ref;
import userMessages.UserError;

final class HelperTest{
  private record FakeRef(String fearPath) implements Ref{
    @Override public byte[] loadBytes(){ return new byte[0]; }
    @Override public long lastModified(){ return 0; }
    @Override public String toString(){ return fearPath; }
  }
  private static Ref ref(String fearPath){ return new FakeRef(fearPath); }

  @Test void aPackageNameStartingWithRankUnderscoreDoesNotMisidentifyItsOtherFiles(){
    var app= ref("fear:/_rank_stuff/_rank_app.fear");
    var hello= ref("fear:/_rank_stuff/hello.fear");
    var result= Helper.okPkgContent(List.of(app,hello), Path.of("unused"));
    assertEquals(app, result);
  }

  @Test void aNonFearFileNamedLikeARankFileIsRejectedWithADedicatedMessage(){
    var app= ref("fear:/_bla/_rank_app.fear");
    var notes= ref("fear:/_bla/_rank_notes.txt");
    var ex= assertThrows(UserError.class, ()->Helper.okPkgContent(List.of(app,notes), Path.of("unused")));
    assertEquals("""
"_rank_" is a reserved file name prefix.
Package:
  "bla"

These names start with "_rank_" but are not the package's rank file:
  "fear:/_bla/_rank_notes.txt"

"_rank_" is reserved for the package's own single rank file, named:
  "_rank_<rankName>.fear" or "_rank_<rankName><NNN>.fear"

Rename them so they do not start with "_rank_".
""", ex.getMessage());
  }

  @Test void basePackageNameIsReserved(){
    var f= ref("fear:/_base/foo.fear");
    var ex= assertThrows(UserError.class, ()->Helper.pkgNameOpt(f));
    assertEquals("""
This folder names a reserved package.
Folder:
  "fear:/_base"

Package name: "base"

"base" is reserved: it is the name of the Fearless standard library package.

Rename the folder to use a different package name.
""", ex.getMessage());
  }

  @Test void rankPackageNameIsReserved(){
    var f= ref("fear:/_rank/foo.fear");
    var ex= assertThrows(UserError.class, ()->Helper.pkgNameOpt(f));
    assertEquals("""
This folder names a reserved package.
Folder:
  "fear:/_rank"

Package name: "rank"

"rank" is reserved: it would create a package folder "_rank", easily
confused with the "_rank_<rankName>.fear" rank-file naming convention used
inside every package (and with the autoloaded names generated from a
package's own folder structure).

Rename the folder to use a different package name.
""", ex.getMessage());
  }
}
