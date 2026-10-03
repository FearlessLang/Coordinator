package coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.AssertionFailedError;
import org.junit.jupiter.api.Assertions;

import coordinator.MainsInfo.Claim;
import coordinator.MainsInfo.Main;
import tools.Fs;
import userMessages.UserError;

final class MainsInfoTest{
  static{ utils.Err.setUp(AssertionFailedError.class, Assertions::assertEquals, Assertions::assertTrue); }
  static final String text= """
{
  "hello.Bar": ["_hello/bar.fear", [["hello.IconsBar", "_hello/icons/bar.png", "", "", "bar"]], [["base.IconsConflict", "icons/conflict.png", "", "", ""], ["hello.ZInFoo", "_hello/z.zip", "y.zip", "in/foo.png", "ffile042"]]],
  "hello.Beer": ["_hello/beer.fear", [], []]
}
""";
  @Test void printsSortedAndReadsBack(@TempDir Path project){
    var info= new MainsInfo(Map.of(
      "hello.Beer",new Main("_hello/beer.fear",List.of(),List.of()),
      "hello.Bar",new Main("_hello/bar.fear",
        List.of(new Claim("hello.IconsBar","_hello/icons/bar.png","","","bar")),
        List.of(new Claim("base.IconsConflict","icons/conflict.png","","",""),new Claim("hello.ZInFoo","_hello/z.zip","y.zip","in/foo.png","ffile042")))));
    utils.Err.strCmp(text, info.print());
    Fs.writeUtf8(project.resolve(Coordinator.outDir).resolve("mains.info"), info.print());
    var back= MainsInfo.read(project).orElseThrow();
    assertEquals(info, back);
    assertEquals(List.of("hello.Bar","hello.Beer"), List.copyOf(back.mains().keySet()));
  }
  @Test void aMissingFileReadsAsEmpty(@TempDir Path project){ assertEquals(Optional.empty(), MainsInfo.read(project)); }
  @Test void aClaimWithFourStringsIsRefused(@TempDir Path project){
    Fs.writeUtf8(project.resolve(Coordinator.outDir).resolve("mains.info"), """
{
  "hello.Beer": ["_hello/beer.fear", [["hello.IconsBar", "_hello/icons/bar.png", "", ""]], []]
}
""");
    var ex= assertThrows(UserError.class, ()->MainsInfo.read(project));
    utils.Err.strCmp("""
In file: [###]mains.info

002|   "hello.Beer": ["_hello/beer.fear", [["hello.IconsBar", "_hello/icons/bar.png", "", ""]], []]
   |                                       ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting the file
Expected a claim as [icon, diskPath, zipSteps, zipEntry, extension] here.
""", ex.getMessage());
  }
}
