package managerData;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import managerIcons.FolderName;

public interface ManagerData{
  record Entry(String alias, Path path, Kind kind, List<String> mains,
      Map<String,List<String>> reads, Map<String,List<String>> edits,
      long compiled, long run){}
  List<Entry> registered();
  void addRegisteredFolder(String alias, Path folder);
  void removeRegisteredFolder(Path folder);
  void setCompiled(Path folder, long millis);
  void setRun(Path folder, long millis);
  void setSelectedMains(Path folder, List<String> mains);
  void setKind(Path folder, Kind kind);
  void setLinks(Path folder, Map<String,List<String>> reads, Map<String,List<String>> edits);
  String infoText();
  void commitInfoText(String text);
  default boolean isRegistered(Path folder){
    var f= folder.toAbsolutePath().normalize();
    return registered().stream().anyMatch(e->e.path().equals(f));
  }
  default Optional<Path> nestedWith(Path folder){
    var f= folder.toAbsolutePath().normalize();
    return registered().stream().map(Entry::path)
      .filter(o->!o.equals(f) && (f.startsWith(o) || o.startsWith(f)))
      .findFirst();
  }
  default Optional<Entry> entryOf(Path folder){
    var f= folder.toAbsolutePath().normalize();
    return registered().stream().filter(e->e.path().equals(f)).findFirst();
  }
  default Optional<String> linkProblem(Entry e){ return LinkCheck.problem(e,registered()); }
  default Optional<String> markerProblem(Entry e){ return FolderName.markerProblem(e.path(),e.alias()); }
}
