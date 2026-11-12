package integrationTests;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import realSourceOracle.Fix;
import realSourceOracle.RealSourceOracleWithZip;
import testHelperFs.FsDsl;
import tools.Fs;
import userMessages.Report;
import userMessages.UserError;

final class FixMountTest{
  @Test void aFolderWhereAFileSystemIsMountedMakesTheProjectInvalidAndEndsFix(@TempDir Path tmp) throws Exception{
    var root= tmp.resolve("root").toAbsolutePath().normalize();
    UserError.root = root;
    Fs.onSymbolicLink = Report::symlinkForbidden;
    Fs.ensureDir(root);
    FsDsl.materialize(root, "a.txt\niii\na");
    var at= root.resolve("sub");
    Fs.ensureDir(at);
    try (var _= fileSystemAt(tmp, at)){
      Files.writeString(at.resolve("c.txt"), "c");
      var e= assertThrows(UserError.class, () -> new RealSourceOracleWithZip(root));
      utils.Err.strCmp("[###]This path is a symbolic link/junction/mount point.[###]", e.getMessage());
      var lines= new ArrayList<String>();
      var end= assertThrows(UncheckedIOException.class, () -> Fix.of(root, lines::add));
      utils.Err.strCmp("[###]this entry, or an entry inside it, is a mount point or is also mounted at another place", end.getCause().getMessage());
      assertEquals(List.of(), lines);
      assertEquals("c", Fs.readUtf8(at.resolve("c.txt")));
    }
  }
  private static AutoCloseable fileSystemAt(Path tmp, Path at){
    if (Fs.isWindows()){
      var disk= tmp.resolve("fearless_mount.vhdx");
      diskpart(tmp, """
create vdisk file="%s" maximum=16
attach vdisk
create partition primary
format fs=ntfs quick
assign mount="%s"
""".formatted(disk, at));
      return () -> diskpart(tmp, """
select volume="%s"
remove mount="%s"
select vdisk file="%s"
detach vdisk
""".formatted(at, at, disk));
    }
    if (Fs.isLinux()){
      var other= tmp.resolve("other");
      Fs.ensureDir(other);
      var why= "This test mounts a file system with \"sudo -n mount\": it needs a user that runs sudo with no password on this desk.";
      run(why, "sudo", "-n", "mount", "--bind", other.toString(), at.toString());
      return () -> run(why, "sudo", "-n", "umount", at.toString());
    }
    var image= tmp.resolve("disk.dmg").toString();
    var why= "This test mounts a disk image with hdiutil.";
    run(why, "hdiutil", "create", "-size", "1m", "-fs", "HFS+", "-volname", "fearless_test", image);
    run(why, "hdiutil", "attach", "-nobrowse", "-mountpoint", at.toString(), image);
    return () -> run(why, "hdiutil", "detach", at.toString());
  }
  private static void diskpart(Path tmp, String script){
    var file= tmp.resolve("fearless_mount.txt");
    Fs.writeUtf8(file, script);
    run("This test mounts a file system with diskpart: it needs a process with administrator rights on this desk.", "diskpart", "/s", file.toString());
  }
  private static void run(String why, String... cmd){
    var p= assertDoesNotThrow(() -> new ProcessBuilder(cmd).redirectErrorStream(true).start(), why+"\n"+String.join(" ", cmd));
    var out= new String(Fs.of(() -> p.getInputStream().readAllBytes()));
    assertEquals(0, p.onExit().join().exitValue(), why+"\n"+String.join(" ", cmd)+"\n"+out);
  }
}
