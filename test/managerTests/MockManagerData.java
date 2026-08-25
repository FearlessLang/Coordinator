package managerTests;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import managerData.ManagerData;

public final class MockManagerData implements ManagerData{
  private final List<Entry> entries= new ArrayList<>();
  @Override public List<Entry> registered(){ return List.copyOf(entries); }
  @Override public void addRegisteredFolder(Path folder){
    var f= norm(folder);
    if (isRegistered(f)){ return; }
    entries.add(new Entry(f,-1,-1));
  }
  @Override public void removeRegisteredFolder(Path folder){
    var f= norm(folder);
    entries.removeIf(e->e.folder().equals(f));
  }
  @Override public void setCompiled(Path folder, long millis){ update(folder,e->new Entry(e.folder(),millis,e.run())); }
  @Override public void setRun(Path folder, long millis){ update(folder,e->new Entry(e.folder(),e.compiled(),millis)); }
  private void update(Path folder, UnaryOperator<Entry> op){
    var f= norm(folder);
    assert isRegistered(f);
    entries.replaceAll(e->e.folder().equals(f) ? op.apply(e) : e);
  }
  private static Path norm(Path folder){ return folder.toAbsolutePath().normalize(); }
}
