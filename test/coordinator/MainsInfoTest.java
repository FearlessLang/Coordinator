package coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.AssertionFailedError;

import coordinator.MainsInfo.Claim;
import coordinator.MainsInfo.Main;
import tools.Fs;
import userMessages.UserError;

final class MainsInfoTest{
  static{ utils.Err.setUp(AssertionFailedError.class, Assertions::assertEquals, Assertions::assertTrue); }
  static final String text= """
{
  "mains": {
    "hello.Bar": ["_hello/bar.fear", [["hello.IconsBar", "_hello/icons/bar.png", "", "", "bar"]], [["base.IconsConflict", "icons/conflict.png", "", "", ""], ["hello.ZInFoo", "_hello/z.zip", "y.zip", "in/foo.png", "ffile042"]], [["hello.IconsBar", "_hello/icons/bar.png", "", "", ""], ["base.IconsFearless", "icons/fearless.png", "", "", ""]]],
    "hello.Beer": ["_hello/beer.fear", [], [], [["base.IconsFearless", "icons/fearless.png", "", "", ""]]]
  },
  "attenuators": ["hello.NoDownload", "hello.NoGui"],
  "dataRead": ["hello.Archive", "hello.Pub"],
  "dataEdit": ["hello.Diary"]
}
""";
  static Path manifest(Path project, String pkg){ return project.resolve(Coordinator.outDir).resolve(pkg+"_manifest.info"); }
  @Test void printsSortedAndReadsBack(@TempDir Path project){
    var info= new MainsInfo(Map.of(
      "hello.Beer", new Main("_hello/beer.fear", List.of(), List.of(), List.of(new Claim("base.IconsFearless", "icons/fearless.png", "", "", ""))),
      "hello.Bar", new Main("_hello/bar.fear",
        List.of(new Claim("hello.IconsBar", "_hello/icons/bar.png", "", "", "bar")),
        List.of(new Claim("base.IconsConflict", "icons/conflict.png", "", "", ""), new Claim("hello.ZInFoo", "_hello/z.zip", "y.zip", "in/foo.png", "ffile042")),
        List.of(new Claim("hello.IconsBar", "_hello/icons/bar.png", "", "", ""), new Claim("base.IconsFearless", "icons/fearless.png", "", "", "")))),
      List.of("hello.NoGui", "hello.NoDownload"), List.of("hello.Pub", "hello.Archive"), List.of("hello.Diary"));
    utils.Err.strCmp(text, info.print());
    Fs.writeUtf8(manifest(project, "hello"), info.print());
    var back= MainsInfo.read(project).orElseThrow();
    assertEquals(info, back);
    assertEquals(List.of("hello.Bar", "hello.Beer"), List.copyOf(back.mains().keySet()));
  }
  @Test void readsEveryManifestOfTheProjectAndWritesBackEachOne(@TempDir Path project){
    var beer= new Main("_hello/beer.fear", List.of(), List.of(), List.of());
    var lib= new Main("_lib/lib.fear", List.of(new Claim("lib.IconsLib", "_lib/lib.png", "", "", "")), List.of(), List.of());
    Fs.writeUtf8(manifest(project, "hello"), new MainsInfo(Map.of("hello.Beer", beer), List.of("hello.NoGui"), List.of("hello.Pub"), List.of()).print());
    Fs.writeUtf8(manifest(project, "lib"), new MainsInfo(Map.of("lib.Lib", lib), List.of(), List.of(), List.of("lib.Diary")).print());
    var all= MainsInfo.read(project).orElseThrow();
    assertEquals(new MainsInfo(Map.of("hello.Beer", beer, "lib.Lib", lib), List.of("hello.NoGui"), List.of("hello.Pub"), List.of("lib.Diary")), all);
    var filled= new Main("_lib/lib.fear", List.of(new Claim("lib.IconsLib", "_lib/lib.png", "", "", "fapp007")), List.of(), List.of());
    MainsInfo.write(project, new MainsInfo(Map.of("hello.Beer", beer, "lib.Lib", filled), List.of("hello.NoGui"), List.of("hello.Pub"), List.of("lib.Diary")));
    assertEquals(new MainsInfo(Map.of("hello.Beer", beer), List.of("hello.NoGui"), List.of("hello.Pub"), List.of()), MainsInfo.parse(manifest(project, "hello")));
    assertEquals(new MainsInfo(Map.of("lib.Lib", filled), List.of(), List.of(), List.of("lib.Diary")), MainsInfo.parse(manifest(project, "lib")));
  }
  @Test void aMissingFileReadsAsEmpty(@TempDir Path project){ assertEquals(Optional.empty(), MainsInfo.read(project)); }
  @Test void aClaimWithFourStringsIsRefused(@TempDir Path project){
    Fs.writeUtf8(manifest(project, "hello"), """
{
  "mains": {
    "hello.Beer": ["_hello/beer.fear", [["hello.IconsBar", "_hello/icons/bar.png", "", ""]], [], []]
  },
  "attenuators": [],
  "dataRead": [],
  "dataEdit": []
}
""");
    var ex= assertThrows(UserError.class, () -> MainsInfo.read(project));
    utils.Err.strCmp("""
Error: Fearless crashed: either this project's compilation cache was corrupted, or this is a Fearless bug.
  Fearless deleted this project's .fearless_out folder: compile again to rebuild it.
  If Fearless crashes again, please report that error.

Details:
utils.Bug: userMessages.UserError: In file: [###]hello_manifest.info

003|     "hello.Beer": ["_hello/beer.fear", [["hello.IconsBar", "_hello/icons/bar.png", "", ""]], [], []]
   |                                         ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting the file
Expected a claim as [icon, diskPath, zipSteps, zipEntry, extension] here.
[###]""", ex.getMessage());
    assertFalse(Fs.exists(project.resolve(Coordinator.outDir)));
  }
}
