package integrationTests;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import realSourceOracle.Fix;
import realSourceOracle.RealSourceOracleWithZip;
import resources.LocalResources;
import testHelperFs.FsDsl;
import tools.Fs;
import userMessages.Report;
import userMessages.UserError;

final class FixMountTest{
  private static Path root(Path tmp, String spec){
    var root= tmp.resolve("root").toAbsolutePath().normalize();
    UserError.root = root;
    Fs.ensureDir(root);
    FsDsl.materialize(root, spec);
    return root;
  }
  @Test void aFolderWhereAFileSystemIsMountedMakesTheProjectInvalidAndEndsFix(@TempDir Path tmp) throws Exception{
    var root= root(tmp, "a.txt\niii\na");
    Fs.onSymbolicLink = Report::symlinkForbidden;
    var at= root.resolve("sub");
    Fs.ensureDir(at);
    try (var _= fileSystemAt(tmp, at)){
      Files.writeString(at.resolve("c.txt"), "c");
      var e= assertThrows(UserError.class, () -> new RealSourceOracleWithZip(root));
      utils.Err.strCmp("[###]This path is a symbolic link/junction/mount point.[###]", e.getMessage());
      var lines= new ArrayList<String>();
      var end= assertThrows(UserError.class, () -> Fix.of(root, lines::add));
      assertEquals(at+" is a drive: remove the drive, then run Fix again.", end.getMessage());
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
      return bound(other, at);
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
  @Test void fixDoesNotDeleteAFolderThatIsAlsoMountedAtAnotherPlace(@TempDir Path tmp) throws Exception{
    var share= tmp.resolve("share");
    try (var _= share(share)){
      var root= root(share.resolve(tmp.getFileName().toString()), "a.txt\niii\na\njjj\n__MACOSX/b/c.txt\niii\nc");
      var b= root.resolve("__MACOSX/b");
      if (!Fs.isWindows()){
        var elsewhere= tmp.resolve("elsewhere");
        Fs.ensureDir(elsewhere);
        try (var _= Fs.isMac() ? mounted(LocalResources.nfsShare+"/"+share.relativize(b), elsewhere) : bound(b, elsewhere)){ refusedAt(root, elsewhere, elsewhere); }
        return;
      }
      var drive= Stream.of("ZYXWVUTSRQPONMLKJIHGFED".split("")).filter(l -> Stream.of(File.listRoots()).noneMatch(r -> r.toString().startsWith(l))).findFirst().orElseThrow()+":";
      var why= "This test shows a folder at a second place with \"subst\", and with \"net share\" and \"net use\": \"net share\" needs administrator rights on this desk.";
      run(why, "subst", drive, b.toString());
      try (var _= (AutoCloseable)() -> run(why, "subst", drive, "/D")){ refusedAt(root, Path.of(drive+"\\"), Path.of(drive+"\\")); }
      var name= "fearless_shown_"+ProcessHandle.current().pid();
      run(why, "net", "share", name+"="+b, "/GRANT:"+System.getProperty("user.name")+",READ");
      try (var _= (AutoCloseable)() -> run(why, "net", "share", name, "/DELETE", "/Y")){
        run(why, "net", "use", drive, "\\\\localhost\\"+name);
        try (var _= (AutoCloseable)() -> run(why, "net", "use", drive, "/DELETE", "/Y")){ refusedAt(root, Path.of(drive+"\\"), Path.of("\\\\localhost\\"+name+"\\")); }
      }
    }
  }
  private static void refusedAt(Path root, Path elsewhere, Path drive){
    var lines= new ArrayList<String>();
    assertEquals(drive+" is a drive: remove the drive, then run Fix again.", assertThrows(UserError.class, () -> Fix.of(root, lines::add)).getMessage());
    assertEquals(List.of(), lines);
    assertEquals("c", Fs.readUtf8(elsewhere.resolve("c.txt")));
  }
  private static final String needsShare= "This test mounts the NFS share LocalResources.nfsShare with \"sudo -n mount\": a folder that a Linux machine exports with NFS version 3.";
  private static AutoCloseable share(Path at){
    Fs.ensureDir(at);
    if (!Fs.isMac()){ return () -> {}; }
    var mount= mounted(LocalResources.nfsShare, at);
    var mine= at.resolve(at.getParent().getFileName().toString());
    return () -> { run(needsShare, "rm", "-rf", mine.toString()); mount.close(); };
  }
  private static AutoCloseable mounted(String source, Path at){
    run(needsShare, "sudo", "-n", "mount", "-t", "nfs", "-o", "vers=3,nolocks,resvport,nobrowse", source, at.toString());
    return () -> run(needsShare, "sudo", "-n", "umount", at.toString());
  }
  private static final String needsRoot= "This test mounts a file system with \"sudo -n mount\": it needs a user that runs sudo with no password on this desk.";
  private static AutoCloseable bound(Path folder, Path at){
    run(needsRoot, "sudo", "-n", "mount", "--bind", folder.toString(), at.toString());
    return () -> run(needsRoot, "sudo", "-n", "umount", at.toString());
  }
  private static void run(String why, String... cmd){
    var p= assertDoesNotThrow(() -> new ProcessBuilder(cmd).redirectErrorStream(true).start(), why+"\n"+String.join(" ", cmd));
    var out= new String(Fs.of(() -> p.getInputStream().readAllBytes()));
    assertEquals(0, p.onExit().join().exitValue(), why+"\n"+String.join(" ", cmd)+"\n"+out);
  }
}
