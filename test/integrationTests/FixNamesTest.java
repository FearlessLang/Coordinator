package integrationTests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import realSourceOracle.Fix;
import resources.LocalResources;
import tools.Fs;

final class FixNamesTest{
  @Test void fixCleansAFileOnEachKindOfVolume(@TempDir Path tmp) throws Exception{
    if (Fs.isMac()){
      for (var fs : List.of("fat32", "exfat")){
        try (var _= image(tmp, fs)){ cleans(tmp.resolve(fs)); }
      }
      try (var _= nfs(tmp)){ changesTheFileAtASecondName(cleans(tmp.resolve("share").resolve(tmp.getFileName().toString()))); }
      return;
    }
    if (Fs.isLinux()){
      for (var fs : List.of("vfat", "exfat")){
        try (var _= loop(tmp, fs)){ cleans(tmp.resolve(fs)); }
      }
      return;
    }
    for (var fs : List.of("fat32", "exfat")){
      try (var _= attached(tmp, fs)){ cleans(formatted(tmp, fs)); }
    }
    try (var _= attached(tmp, "refs")){ changesTheFileAtASecondName(cleans(formatted(tmp, "refs"))); }
    var name= "fearless_test_"+tmp.getFileName().toString().replace("-", "");
    try (var _= shared(name, tmp.resolve("share"))){ changesTheFileAtASecondName(cleans(Path.of("\\\\localhost\\"+name))); }
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
  private static void changesTheFileAtASecondName(Path at) throws IOException{
    var root= at.resolve("root");
    Fs.writeUtf8(root.resolve("b.fear"), "B:{}\r\n");
    Files.createLink(at.resolve("other.fear"), root.resolve("b.fear"));
    var lines= new ArrayList<String>();
    Fix.of(root, lines::add);
    assertEquals(List.of("Fixed the line endings of b.fear"), lines);
    assertEquals("B:{}\n", Fs.readUtf8(at.resolve("other.fear")));
  }
  private static final String needsRoot= "This test makes a FAT32 and an exFAT volume with \"sudo -n mkfs.vfat\", \"sudo -n mkfs.exfat\" and \"sudo -n mount\": it needs a user that runs sudo with no password on this desk, and the programs mkfs.vfat and mkfs.exfat.";
  private static AutoCloseable loop(Path tmp, String fs) throws IOException{
    var image= tmp.resolve(fs+".img").toString();
    var at= tmp.resolve(fs);
    Fs.ensureDir(at);
    try (var f= new RandomAccessFile(image, "rw")){ f.setLength(64L<<20); }
    var make= fs.equals("vfat") ? List.of("sudo", "-n", "mkfs.vfat", "-F", "32", image) : List.of("sudo", "-n", "mkfs.exfat", image);
    run(needsRoot, make.toArray(String[]::new));
    var owner= "uid="+Files.getAttribute(tmp, "unix:uid")+",gid="+Files.getAttribute(tmp, "unix:gid");
    run(needsRoot, "sudo", "-n", "mount", "-o", "loop,"+owner, image, at.toString());
    return () -> run(needsRoot, "sudo", "-n", "umount", at.toString());
  }
  private static final String needsMac= "This test makes a FAT32 and an exFAT volume with \"hdiutil\", and mounts the NFS share LocalResources.nfsShare with \"sudo -n mount\": a folder that a Linux machine exports with NFS version 3.";
  private static AutoCloseable image(Path tmp, String fs){
    var image= tmp.resolve(fs+".dmg").toString();
    var at= tmp.resolve(fs);
    Fs.ensureDir(at);
    run(needsMac, "hdiutil", "create", "-size", "64m", "-fs", fs.equals("fat32") ? "MS-DOS FAT32" : "ExFAT", "-volname", "FIX", image);
    run(needsMac, "hdiutil", "attach", "-nobrowse", "-mountpoint", at.toString(), image);
    return () -> run(needsMac, "hdiutil", "detach", at.toString());
  }
  private static AutoCloseable nfs(Path tmp){
    var at= tmp.resolve("share");
    Fs.ensureDir(at);
    run(needsMac, "sudo", "-n", "mount", "-t", "nfs", "-o", "vers=3,nolocks,resvport,nobrowse", LocalResources.nfsShare, at.toString());
    var mine= at.resolve(tmp.getFileName().toString());
    return () -> {
      run(needsMac, "rm", "-rf", mine.toString());
      run(needsMac, "sudo", "-n", "umount", at.toString());
    };
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
