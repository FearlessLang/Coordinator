package mainCoordinator;

import java.nio.file.Path;
import java.util.Locale;

import tools.Fs;
//This file only exists because 
//Desktop.getDesktop().open(projectDir.toFile())
//has a bug connected with JPackage (shell poisoning)
final class OpenDir{
  static void open(Path dir){ Fs.ofV(()->cleanPb(command(dir)).start()); }

  private static ProcessBuilder cleanPb(String... cmd){
    var pb= new ProcessBuilder(cmd);
    pb.environment().remove("_JPACKAGE_LAUNCHER");
    return pb;
  }
  private static String[] command(Path dir){
    var path= dir.toAbsolutePath().toString();
    var os= System.getProperty("os.name").toLowerCase(Locale.ROOT);
    if (os.contains("win")){ return new String[]{"explorer.exe", path}; }
    if (os.contains("mac")){ return new String[]{"open", path}; }
    return new String[]{"xdg-open", path};
  }
}