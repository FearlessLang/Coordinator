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
  static String regFile(Path launcher, String versionId){
    var classes= "HKEY_CURRENT_USER\\Software\\Classes\\";
    var id= progId(versionId);
    return "Windows Registry Editor Version 5.00\r\n"
      + regEntry(classes+id, "Fearless project")
      + regEntry(classes+id+"\\DefaultIcon", launcher+",0")
      + regEntry(classes+id+"\\shell\\open\\command", "\""+launcher+"\" \"%1\"")
      + regEntry(classes+ext, id)
      + regEntry(classes+ext+"\\OpenWithProgids", id, "");
  }
  private static String regEntry(String key, String data){ return regEntry(key,"",data); }
  private static String regEntry(String key, String name, String data){
    var shown= name.isEmpty() ? "@" : "\""+regData(name)+"\"";
    return "\r\n["+key+"]\r\n"+shown+"=\""+regData(data)+"\"\r\n";
  }
  private static String regData(String data){ return data.replace("\\","\\\\").replace("\"","\\\""); }
  static boolean canBecomeDefault(String versionId){
    if (launcher().isEmpty()){ return false; }
    if (Fs.isLinux()){ return !desktopName(versionId).equals(linuxDefault()); }
    if (Fs.isWindows()){ return !progId(versionId).equals(winDefault()); }
    return false;
  }
  static void makeDefault(Path binDir, String versionId, Path icon){
    var launcher= launcher().orElseThrow(Bug::unreachable);
    var tried= "";
    if (Fs.isLinux()){ tried= linuxMakeDefault(binDir, versionId, launcher, icon); }
    if (Fs.isWindows()){ tried= winMakeDefault(launcher, versionId); }
    if (!canBecomeDefault(versionId)){ return; }
    var handPicked= answerGivenByHand();
    if (handPicked.isPresent()){ throw Violation.fearlessProjectsOpenWithYourOwnChoice(handPicked.get()); }
    throw Violation.fearlessProjectsStillOpenWith(shownDefault(), tried);
  }
  private static String shownDefault(){
    var now= Fs.isWindows() ? winDefault() : linuxDefault();
    return now.isEmpty() ? "nothing at all" : now;
  }
  private static String linuxDefault(){
    return exec(List.of("xdg-mime","query","default",mimeType))
      .filter(ran->ran.code() == 0).map(ran->ran.out().strip()).orElse("");
  }
  private static String linuxMakeDefault(Path binDir, String versionId, Path launcher, Path icon){
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
    return req(List.of("xdg-mime", "default", desktopName(versionId), mimeType));
  }
  private static String winMakeDefault(Path launcher, String versionId){
    var file= Fs.of(()->Files.createTempFile("fearless",".reg"));
    Fs.ofV(()->Files.write(file, ("\uFEFF"+regFile(launcher,versionId)).getBytes(StandardCharsets.UTF_16LE)));
    var imported= req(List.of("reg","import",file.toString()));
    Fs.ofV(()->Files.deleteIfExists(file));
    return imported;
  }
  //The answer the desktop remembers is the user's own, given by hand, and only
  //another answer given by hand takes its place: no program writes it, and no
  //program removes it. The most Fearless can do is open the window where it is
  //given, on a file of the type the answer is about.
  static Optional<String> answerGivenByHand(){
    if (!Fs.isWindows()){ return Optional.empty(); }
    return regValue(userChoice,"ProgId");
  }
  static List<String> pickCommand(Path file){
    return List.of("rundll32.exe","shell32.dll,OpenAs_RunDLL",file.toAbsolutePath().toString());
  }
  static void pickByHand(Path file){
    assert Fs.isWindows();
    exec(pickCommand(file));
  }
  static final String userChoice= "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts\\"+ext+"\\UserChoice";
  private static String winDefault(){
    return regValue(userChoice,"ProgId").or(()->regValue("HKCU\\Software\\Classes\\"+ext,"")).orElse("");
  }
  private static String report(List<String> cmd, Optional<Ran> ran){
    var out= ran.map(Ran::out).orElse("The program could not be started.");
    return (String.join(" ",cmd)+"\n"+out).strip();
  }
  private static Optional<String> regValue(String key, String name){
    var cmd= name.isEmpty()
      ? List.of("reg","query",key,"/ve")
      : List.of("reg","query",key,"/v",name);
    return exec(cmd)
      .filter(ran->ran.code() == 0)
      .flatMap(ran->ran.out().lines().map(String::strip).filter(l->l.contains("REG_")).findFirst())
      .map(FileAssociation::regQueried);
  }
  private static String regQueried(String line){
    var end= line.indexOf(' ', line.indexOf("REG_"));
    return end < 0 ? "" : line.substring(end).strip();
  }
  private static String req(List<String> cmd){
    var ran= exec(cmd);
    var reported= report(cmd, ran);
    if (ran.isEmpty() || ran.get().code() != 0){ throw Violation.couldNotOpenFearlessProjects(reported); }
    return reported;
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
