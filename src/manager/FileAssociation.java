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
  static final String ext= ".fearless";
  private record Ran(int code, String out){}
  static String desktopName(String versionId){ return JavacTool.dataDirNameFor(versionId)+".desktop"; }
  static String progId(String versionId){ return "Fearless.Project."+versionId; }
  static Optional<Path> launcher(){
    return ProcessHandle.current().info().command().map(Path::of);
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
  static List<List<String>> winCommands(Path launcher, String versionId){
    var id= "HKCU\\Software\\Classes\\"+progId(versionId);
    return List.of(
      List.of("reg","add",id,"/ve","/d","Fearless project","/f"),
      List.of("reg","add",id+"\\DefaultIcon","/ve","/d",launcher+",0","/f"),
      List.of("reg","add",id+"\\shell\\open\\command","/ve","/d","\""+launcher+"\" \"%1\"","/f"),
      List.of("reg","add","HKCU\\Software\\Classes\\"+ext,"/ve","/d",progId(versionId),"/f"));
  }
  static boolean canBecomeDefault(String versionId){
    if (launcher().isEmpty()){ return false; }
    if (Fs.isLinux()){ return !desktopName(versionId).equals(linuxDefault()); }
    if (Fs.isWindows()){ return !progId(versionId).equals(winDefault()); }
    return false;
  }
  static void makeDefault(Path binDir, String versionId, Path icon){
    var launcher= launcher().orElseThrow(Bug::unreachable);
    if (Fs.isLinux()){ linuxMakeDefault(binDir, versionId, launcher, icon); }
    if (Fs.isWindows()){ winCommands(launcher, versionId).forEach(FileAssociation::req); }
    if (canBecomeDefault(versionId)){ throw Violation.couldNotOpenFearlessProjects("Your system kept the program it was opening them with before."); }
  }
  private static String linuxDefault(){
    return exec(List.of("xdg-mime","query","default",mimeType))
      .filter(ran->ran.code() == 0).map(ran->ran.out().strip()).orElse("");
  }
  private static void linuxMakeDefault(Path binDir, String versionId, Path launcher, Path icon){
    var dataHome= InstallLocation.userDataHome();
    var mimeHome= dataHome.resolve("mime");
    var apps= dataHome.resolve("applications");
    Fs.ensureDir(mimeHome.resolve("packages"));
    Fs.ofV(()->Files.copy(
      binDir.resolve("bin").resolve("fearless-mime.xml"),
      mimeHome.resolve("packages").resolve("fearless-mime.xml"), REPLACE_EXISTING));
    Fs.writeUtf8(apps.resolve(desktopName(versionId)), desktopEntry(launcher, icon));
    req(List.of("update-mime-database", mimeHome.toString()));
    req(List.of("update-desktop-database", apps.toString()));
    req(List.of("xdg-mime", "default", desktopName(versionId), mimeType));
  }
  private static String winDefault(){
    var choice= regValue("HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts\\"+ext+"\\UserChoice","ProgId");
    return choice.or(()->regValue("HKCU\\Software\\Classes\\"+ext,"")).orElse("");
  }
  private static Optional<String> regValue(String key, String name){
    var cmd= name.isEmpty()
      ? List.of("reg","query",key,"/ve")
      : List.of("reg","query",key,"/v",name);
    var shown= name.isEmpty() ? "(Default)" : name;
    return exec(cmd)
      .filter(ran->ran.code() == 0)
      .flatMap(ran->ran.out().lines().map(String::strip).filter(l->l.startsWith(shown)).findFirst())
      .map(FileAssociation::regData);
  }
  private static String regData(String line){
    var type= line.indexOf("REG_");
    var end= line.indexOf(' ', type);
    return end < 0 ? "" : line.substring(end).strip();
  }
  private static void req(List<String> cmd){
    var ran= exec(cmd);
    var reported= String.join(" ",cmd)+"\n"+ran.map(Ran::out).orElse("The program could not be started.");
    if (ran.isEmpty() || ran.get().code() != 0){ throw Violation.couldNotOpenFearlessProjects(reported); }
  }
  private static Optional<Ran> exec(List<String> cmd){
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
