package integrationTests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import realSourceOracle.Fix;
import testHelperFs.FsDsl;
import tools.Fs;
import userMessages.UserError;

final class FixShareTest{
  @Test void fixDoesNotDeleteAFolderThatAShareOfThisMachineShows(@TempDir Path tmp) throws Exception{
    if (!Fs.isWindows()){ return; }
    var root= tmp.resolve("root").toAbsolutePath().normalize();
    UserError.root = root;
    Fs.ensureDir(root);
    FsDsl.materialize(root, "a.txt\niii\na\njjj\n__MACOSX/b/c.txt\niii\nc");
    var b= root.resolve("__MACOSX/b");
    var drive= Stream.of("ZYXWVUTSRQPONMLKJIHGFED".split("")).filter(l -> Stream.of(File.listRoots()).noneMatch(r -> r.toString().startsWith(l))).findFirst().orElseThrow()+":";
    var share= "fearless_shown_"+ProcessHandle.current().pid();
    var needsAdmin= "This test shares a folder with \"net share\": it needs administrator rights on this desk.";
    run(needsAdmin, "net", "share", share+"="+b, "/GRANT:"+System.getProperty("user.name")+",READ");
    try (var _= (AutoCloseable)() -> run(needsAdmin, "net", "share", share, "/DELETE", "/Y")){
      run(needsAdmin, "net", "use", drive, "\\\\localhost\\"+share);
      try (var _= (AutoCloseable)() -> run(needsAdmin, "net", "use", drive, "/DELETE", "/Y")){
        var lines= new ArrayList<String>();
        var end= assertThrows(UncheckedIOException.class, () -> Fix.of(root, lines::add));
        utils.Err.strCmp("[###]this entry, or an entry inside it, is a mount point or is also mounted at another place", end.getCause().getMessage());
        assertEquals(List.of(), lines);
        assertEquals("c", Fs.readUtf8(Path.of(drive+"\\").resolve("c.txt")));
      }
    }
  }
  private static void run(String why, String... cmd){
    var p= Fs.of(() -> new ProcessBuilder(cmd).redirectErrorStream(true).start());
    var out= new String(Fs.of(() -> p.getInputStream().readAllBytes()));
    assertEquals(0, p.onExit().join().exitValue(), why+"\n"+String.join(" ", cmd)+"\n"+out);
  }
}
