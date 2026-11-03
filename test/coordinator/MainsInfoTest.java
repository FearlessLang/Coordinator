package coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import coordinator.MainsInfo.Claim;
import coordinator.MainsInfo.Main;

final class MainsInfoTest{
  static{ utils.Err.setUp(AssertionFailedError.class, Assertions::assertEquals, Assertions::assertTrue); }
  static final String text= """
{
  "mains": {
    "hello.Bar": [[["hello.IconsBar", "_hello/icons/bar.png", "", "", "bar", "bar"]], [["base.IconsConflict", "icons/conflict.png", "", "", "", "ffile001"], ["hello.ZInFoo", "_hello/z.zip", "y.zip", "in/foo.png", "", "ffile042"]], [["hello.IconsBar", "_hello/icons/bar.png", "", "", "", ""], ["base.IconsFearless", "icons/fearless.png", "", "", "", ""]]],
    "hello.Beer": [[], [], [["base.IconsFearless", "icons/fearless.png", "", "", "", ""]]]
  },
  "attenuators": ["hello.NoDownload", "hello.NoGui"],
  "dataRead": ["hello.Archive", "hello.Pub"],
  "dataEdit": ["hello.Diary"]
}
""";
  static final MainsInfo info= new MainsInfo(Map.of(
    "hello.Beer", new Main(List.of(), List.of(), List.of(new Claim("base.IconsFearless", "icons/fearless.png", "", "", "", ""))),
    "hello.Bar", new Main(
      List.of(new Claim("hello.IconsBar", "_hello/icons/bar.png", "", "", "bar", "bar")),
      List.of(new Claim("base.IconsConflict", "icons/conflict.png", "", "", "", "ffile001"), new Claim("hello.ZInFoo", "_hello/z.zip", "y.zip", "in/foo.png", "", "ffile042")),
      List.of(new Claim("hello.IconsBar", "_hello/icons/bar.png", "", "", "", ""), new Claim("base.IconsFearless", "icons/fearless.png", "", "", "", "")))),
    List.of("hello.NoGui", "hello.NoDownload"), List.of("hello.Pub", "hello.Archive"), List.of("hello.Diary"));
  @Test void printsSortedAndReadsBack(){
    utils.Err.strCmp(text, info.print());
    var back= MainsInfo.parse(info.print(), Path.of("hello.json"));
    assertEquals(info, back);
    assertEquals(List.of("hello.Bar", "hello.Beer"), List.copyOf(back.mains().keySet()));
  }
  @Test void withExtensionsKeepsTheNamedExtensionsAndAsksForTheOthers(){
    var source= new MainsInfo(Map.of("hello.Bar", new Main(
      List.of(new Claim("hello.IconsBar", "_hello/icons/bar.png", "", "", "fapp123", "fapp123"), new Claim("hello.IconsBaz", "_hello/icons/baz.png", "", "", "", "")),
      List.of(new Claim("hello.IconsFoo", "_hello/icons/foo.png", "", "", "", "")), List.of())));
    var asked= new StringBuilder();
    var chosen= source.withExtensions((main, shortcut, c) -> { asked.append(main+" "+shortcut+" "+c.icon()+"\n"); return shortcut ? "fapp777" : "ffile777"; });
    utils.Err.strCmp("""
hello.Bar true hello.IconsBaz
hello.Bar false hello.IconsFoo
""", asked.toString());
    var m= chosen.mains().get("hello.Bar");
    assertEquals(List.of("fapp123", "fapp777"), m.shortcuts().stream().map(Claim::extension).toList());
    assertEquals(List.of("fapp123", ""), m.shortcuts().stream().map(Claim::named).toList());
    assertEquals(List.of("ffile777"), m.openWiths().stream().map(Claim::extension).toList());
  }
}
