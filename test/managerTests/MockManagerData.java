package managerTests;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import managerData.EntrySchema;
import managerData.InfoPrinter;
import managerData.Kind;
import managerData.ManagerData;

public final class MockManagerData implements ManagerData{
  private final List<Entry> entries= new ArrayList<>();
  @Override public List<Entry> registered(){ return List.copyOf(entries); }
  @Override public void addRegisteredFolder(String alias, Path folder){
    var f= norm(folder);
    if (isRegistered(f)){ return; }
    assert nestedWith(f).isEmpty();
    entries.add(new Entry(alias,f,Kind.idle,List.of(),Map.of(),Map.of(),-1,-1));
  }
  @Override public void removeRegisteredFolder(Path folder){
    var f= norm(folder);
    entries.removeIf(e->e.path().equals(f));
  }
  @Override public void setCompiled(Path folder, long millis){ update(folder,e->new Entry(e.alias(),e.path(),e.kind(),e.mains(),e.reads(),e.edits(),millis,e.run())); }
  @Override public void setRun(Path folder, long millis){ update(folder,e->new Entry(e.alias(),e.path(),e.kind(),e.mains(),e.reads(),e.edits(),e.compiled(),millis)); }
  @Override public void setSelectedMains(Path folder, List<String> mains){ update(folder,e->new Entry(e.alias(),e.path(),e.kind(),List.copyOf(mains),e.reads(),e.edits(),e.compiled(),e.run())); }
  @Override public String infoText(){ return InfoPrinter.print(EntrySchema.toInfo(registered())); }
  @Override public void commitInfoText(String text){ throw new UnsupportedOperationException(); }
  @Override public void setKind(Path folder, Kind kind){ update(folder,e->new Entry(e.alias(),e.path(),kind,e.mains(),e.reads(),e.edits(),e.compiled(),e.run())); }
  @Override public void setLinks(Path folder, Map<String,List<String>> reads, Map<String,List<String>> edits){
    update(folder,e->new Entry(e.alias(),e.path(),e.kind(),e.mains(),reads,edits,e.compiled(),e.run()));
  }
  private void update(Path folder, UnaryOperator<Entry> op){
    var f= norm(folder);
    assert isRegistered(f);
    entries.replaceAll(e->e.path().equals(f) ? op.apply(e) : e);
  }
  private static Path norm(Path folder){ return folder.toAbsolutePath().normalize(); }
}
