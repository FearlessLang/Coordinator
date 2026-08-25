package manager;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import tools.Fs;
import tools.JavacTool;
import userMessages.Violation;
import utils.Bug;

final class FileAssociation{
  private FileAssociation(){}
  static final String mimeType= "application/x-fearless";
  private record Ran(int code, String out){}
  static String desktopName(String versionId){ return JavacTool.dataDirNameFor(versionId)+".desktop"; }
  static Path launcher(Path binDir, String versionId){
    return binDir.resolve("bin").resolve(JavacTool.appNameFor(versionId)+"w");
  }
  static String desktopEntry(Path launcher, Path icon){
    return """
      [Desktop Entry]
      Type=Application
      Name=Fearless
      Comment=Open a Fearless project
      Exec=%s %%f
      Icon=%s
      Terminal=false
      Categories=Development;
      MimeType=%s;
      """.formatted(launcher, icon, mimeType);
  }
  static boolean canBecomeDefault(String versionId){
    if (!Fs.isLinux()){ return false; }
    return exec("xdg-mime","query","default",mimeType)
      .filter(ran->ran.code() == 0)
      .map(ran->!desktopName(versionId).equals(ran.out().strip()))
      .orElse(false);
  }
  static void makeDefault(Path binDir, String versionId, Path icon){
    var dataHome= InstallLocation.userDataHome();
    var mimeHome= dataHome.resolve("mime");
    var apps= dataHome.resolve("applications");
    Fs.ensureDir(mimeHome.resolve("packages"));
    Fs.ofV(()->Files.copy(
      binDir.resolve("bin").resolve("fearless-mime.xml"),
      mimeHome.resolve("packages").resolve("fearless-mime.xml"), REPLACE_EXISTING));
    Fs.writeUtf8(apps.resolve(desktopName(versionId)), desktopEntry(launcher(binDir,versionId), icon));
    req("update-mime-database", mimeHome.toString());
    req("update-desktop-database", apps.toString());
    req("xdg-mime", "default", desktopName(versionId), mimeType);
  }
  private static void req(String... cmd){
    var ran= exec(cmd);
    if (ran.isEmpty()){ throw Violation.couldNotOpenFearlessProjects(List.of(cmd), "The program could not be started."); }
    if (ran.get().code() != 0){ throw Violation.couldNotOpenFearlessProjects(List.of(cmd), ran.get().out()); }
  }
  private static Optional<Ran> exec(String... cmd){
    var pb= new ProcessBuilder(cmd).redirectErrorStream(true);
    pb.environment().remove("_JPACKAGE_LAUNCHER");
    Process p;
    try { p= pb.start(); }
    catch(IOException e){ return Optional.empty(); }
    var out= Fs.of(()->new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    return Optional.of(new Ran(waitFor(p), out));
  }
  private static int waitFor(Process p){
    try { return p.waitFor(); }
    catch(InterruptedException e){ Thread.currentThread().interrupt(); throw Bug.of(e); }
  }
}
