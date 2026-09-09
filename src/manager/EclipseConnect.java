package manager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import managerData.ManagerData;
import tools.Fs;
import tools.JavacTool;
import userMessages.Report;
import userMessages.Violation;

final class EclipseConnect{
  private EclipseConnect(){}
  static String connect(Path chosen, List<ManagerData.Entry> known){
    var eclipse= chosen.getParent();
    if (!Files.isRegularFile(eclipse.resolve(".eclipseproduct"))){ throw Report.notAnEclipseInstall(eclipse); }
    var plugin= JavacTool.reqAppDir(Violation::mustUseLauncher).resolve("eclipsePlugin");
    Fs.copyFresh(plugin, eclipse.resolve("dropins").resolve("fearless").resolve("plugins"));
    known.forEach(e->writeDescription(e.path(), e.alias()));
    return """
Eclipse is now connected:
%s

Restart Eclipse, then use File > Open Projects from File System on any project
folder this manager knows.
""".formatted(eclipse);
  }
  static void writeDescription(Path folder, String alias){
    var file= folder.resolve(".project");
    if (Files.exists(file)){ return; }
    Fs.writeUtf8(file, """
<?xml version="1.0" encoding="UTF-8"?>
<projectDescription>
  <name>%s</name>
  <comment></comment>
  <projects></projects>
  <buildSpec></buildSpec>
  <natures></natures>
</projectDescription>
""".formatted(alias));
  }
}
