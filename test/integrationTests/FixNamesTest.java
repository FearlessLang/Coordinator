package integrationTests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import realSourceOracle.Fix;
import tools.Fs;

final class FixNamesTest{
  @Test void fixCountsTheNamesOfAFileOnAFatVolumeOnAReFSVolumeAndOnASharedFolder(@TempDir Path tmp) throws Exception{
    if (!Fs.isWindows()){ return; }
    for (var fs : List.of("fat32", "exfat")){
      try (var _= attached(tmp, fs)){ cleans(formatted(tmp, fs)); }
    }
    try (var _= attached(tmp, "refs")){ endsAtASecondName(cleans(formatted(tmp, "refs"))); }
    var name= "fearless_test_"+tmp.getFileName().toString().replace("-", "");
    try (var _= shared(name, tmp.resolve("share"))){ endsAtASecondName(cleans(Path.of("\\\\localhost\\"+name))); }
  }
  private static Path cleans(Path at){
    var root= at.resolve("root");
    Fs.writeUtf8(root.resolve("a.fear"), "A:{}\r\n");
    var sb= new StringBuilder();
    Fix.of(root, line -> sb.append(line).append("\n"));
    assertEquals("Fixed the line endings of a.fear\n", sb.toString());
    assertEquals("A:{}\n", Fs.readUtf8(root.resolve("a.fear")));
    return at;
  }
  private static void endsAtASecondName(Path at) throws IOException{
    var root= at.resolve("root");
    Fs.writeUtf8(root.resolve("b.fear"), "B:{}\r\n");
    Files.createLink(at.resolve("other.fear"), root.resolve("b.fear"));
    var lines= new ArrayList<String>();
    var e= assertThrows(UncheckedIOException.class, () -> Fix.of(root, lines::add));
    utils.Err.strCmp("[###]this file has more than one name, so writing it changes those other names too", e.getCause().getMessage());
    assertEquals(List.of(), lines);
    assertEquals("B:{}\r\n", Fs.readUtf8(at.resolve("other.fear")));
  }
  private static final String needsAdmin= "This test makes volumes with \"diskpart\" and \"format /DevDrv\", and shares a folder with \"net share\": it needs an administrator on a desk that makes a Dev Drive.";
  private static String vdisk(Path tmp, String fs){ return "vdisk file=\""+tmp.resolve(fs+".vhdx")+"\""; }
  private static AutoCloseable attached(Path tmp, String fs){
    var size= fs.equals("refs") ? 51200 : 64;
    diskpart(tmp, "create "+vdisk(tmp, fs)+" maximum="+size+" type=expandable\nselect "+vdisk(tmp, fs)+"\nattach vdisk\n");
    return () -> {
      diskpart(tmp, "select "+vdisk(tmp, fs)+"\ndetach vdisk\n");
      Files.deleteIfExists(tmp.resolve(fs));
    };
  }
  private static Path formatted(Path tmp, String fs){
    var at= tmp.resolve(fs);
    Fs.ensureDir(at);
    var format= fs.equals("refs") ? "" : "format fs="+fs+" quick\n";
    diskpart(tmp, "select "+vdisk(tmp, fs)+"\ncreate partition primary\n"+format+"assign mount=\""+at+"\"\n");
    if (fs.equals("refs")){ run(needsAdmin, "format.com", at.toString(), "/DevDrv", "/Q", "/Y"); }
    return at;
  }
  private static void diskpart(Path tmp, String script){
    var file= tmp.resolve("diskpart.txt");
    Fs.writeUtf8(file, script);
    run(needsAdmin, "cmd", "/c", "diskpart", "/s", file.toString());
  }
  private static AutoCloseable shared(String name, Path folder){
    Fs.ensureDir(folder);
    run(needsAdmin, "net", "share", name+"="+folder, "/grant:"+System.getProperty("user.name")+",FULL");
    return () -> run(needsAdmin, "net", "share", name, "/delete", "/y");
  }
  private static void run(String why, String... cmd){
    var p= Fs.of(() -> new ProcessBuilder(cmd).redirectErrorStream(true).start());
    var out= new String(Fs.of(() -> p.getInputStream().readAllBytes()));
    assertEquals(0, p.onExit().join().exitValue(), why+"\n"+String.join(" ", cmd)+"\n"+out);
  }
}
