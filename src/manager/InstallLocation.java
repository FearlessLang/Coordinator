package manager;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import userMessages.Violation;
import tools.Fs;
import utils.Bug;

/// The two conventions of the host operating system that the manager needs:
/// - where this system keeps installed programs;
/// - where this system keeps what one program writes for one user.
/// Fearless reads which of the two situations it is in, and never asks the user.
/// A program the user unpacked somewhere carries its own data next to itself, so the
/// whole of it travels together and can be deleted by deleting one folder; a program a
/// system installed sits where the system decided, typically read only, and its data
/// goes to the per-user place that system keeps for exactly this.
final class InstallLocation {
  private InstallLocation(){}
  //An installed program folder is one UNDER a location the system reserves for programs;
  //being one of those locations itself does not happen (they hold programs, they are not one).
  static boolean isInstalled(Path binDir){
    var here= binDir.toAbsolutePath().normalize();
    return installRoots().stream().anyMatch(root -> here.startsWith(root) && !here.equals(root));
  }
  static Path userDataHome(){
    var home= Path.of(System.getProperty("user.home"));
    //Windows: the per-user, per-machine place; roaming would carry the data to other
    //machines, where this exact copy of Fearless is not installed.
    if (Fs.isWindows()){ return fromEnv("LOCALAPPDATA", home.resolve("AppData").resolve("Local")); }
    if (Fs.isMac()){ return home.resolve("Library").resolve("Application Support"); }
    if (Fs.isLinux()){ return fromEnv("XDG_DATA_HOME", home.resolve(".local").resolve("share")); }
    throw Violation.unsupportedOperatingSystem();
  }
  private static List<Path> installRoots(){
    var res= new ArrayList<Path>();
    if (Fs.isWindows()){
      //The three Program Files variables cover 64 bit, 32 bit, and the 64 bit one as seen
      //from a 32 bit process; Programs under the per-user place is where a per-user
      //install goes, and is just as much an installed location as the machine-wide ones.
      addEnv(res, "ProgramFiles");
      addEnv(res, "ProgramFiles(x86)");
      addEnv(res, "ProgramW6432");
      res.add(fromEnv("LOCALAPPDATA", userHome().resolve("AppData").resolve("Local")).resolve("Programs"));
      return res;
    }
    if (Fs.isMac()){
      res.add(Path.of("/Applications"));
      res.add(userHome().resolve("Applications"));
      return res;
    }
    if (Fs.isLinux()){
      res.add(Path.of("/opt"));
      res.add(Path.of("/usr"));//covers /usr/local, /usr/lib, /usr/share, /usr/bin
      res.add(userHome().resolve(".local").resolve("opt"));
      res.add(userHome().resolve(".local").resolve("lib"));
      return res;
    }
    throw Violation.unsupportedOperatingSystem();
  }
  private static Path userHome(){
    var home= System.getProperty("user.home");
    if (home == null){ throw Bug.unreachable(); }//the JVM always sets it
    return Path.of(home);
  }
  private static void addEnv(List<Path> out, String name){
    var dir= envDir(name);
    if (dir != null){ out.add(dir); }
  }
  private static Path fromEnv(String name, Path fallback){
    var dir= envDir(name);
    return dir != null ? dir : fallback;
  }
  //A relative or unusable value is treated as absent: these variables name a location on
  //this machine, and a relative one would move the folder with the working directory.
  private static Path envDir(String name){
    var value= System.getenv(name);
    if (value == null || value.isBlank()){ return null; }
    try {
      var res= Path.of(value);
      return res.isAbsolute() ? res.normalize() : null;
    }
    catch(InvalidPathException e){ return null; }
  }
}
