package managerData;

import java.nio.file.Path;
import java.util.List;

public interface ManagerData{
  record Entry(Path folder, long compiled, long run){}
  List<Entry> registered();
  void addRegisteredFolder(Path folder);
  void removeRegisteredFolder(Path folder);
  void setCompiled(Path folder, long millis);
  void setRun(Path folder, long millis);
  default boolean isRegistered(Path folder){
    var f= folder.toAbsolutePath().normalize();
    return registered().stream().anyMatch(e->e.folder().equals(f));
  }
}
