package managerData;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

import fileSupport.StringFiles;
import userMessages.UserError;
import userMessages.Violation;
import utils.Join;
import utils.Push;

public record InfoData(Path managerDir) implements ManagerData{
  private Path infoFile(){ return managerDir.resolve("projects.info"); }
  private Path activityFile(){ return managerDir.resolve("activity.txt"); }
  private URI infoUri(){ return infoFile().toUri(); }
  @Override public List<Entry> registered(){
    var activity= readActivity();
    return rawEntries().stream().map(e->withActivity(e,activity)).toList();
  }
  private List<Entry> rawEntries(){
    if (!Files.exists(infoFile())){ return List.of(); }
    var text= StringFiles.read(infoFile(),UserError.onFileError());
    return EntrySchema.fromInfo(text,InfoParser.parse(text,infoUri()));
  }
  private static Entry withActivity(Entry e, Map<Path,long[]> activity){
    var a= activity.get(e.path());
    return a == null ? e : new Entry(e.alias(),e.path(),e.kind(),e.mains(),e.reads(),e.edits(),a[0],a[1]);
  }
  @Override public void addRegisteredFolder(String alias, Path folder){
    var f= norm(folder);
    var current= rawEntries();
    if (current.stream().anyMatch(e->e.path().equals(f))){ return; }
    assert current.stream().noneMatch(e->e.alias().equals(alias));
    assert nestedWith(f).isEmpty();
    writeEntries(Push.of(current,new Entry(alias,f,Kind.idle,List.of(),Map.of(),Map.of(),-1,-1)));
  }
  @Override public void removeRegisteredFolder(Path folder){
    var f= norm(folder);
    writeEntries(rawEntries().stream().filter(e->!e.path().equals(f)).toList());
  }
  @Override public void setSelectedMains(Path folder, List<String> mains){
    updateEntry(folder,e->new Entry(e.alias(),e.path(),e.kind(),List.copyOf(mains),e.reads(),e.edits(),-1,-1));
  }
  @Override public void setKind(Path folder, Kind kind){
    updateEntry(folder,e->new Entry(e.alias(),e.path(),kind,e.mains(),e.reads(),e.edits(),-1,-1));
  }
  @Override public void setLinks(Path folder, Map<String,List<String>> reads, Map<String,List<String>> edits){
    updateEntry(folder,e->new Entry(e.alias(),e.path(),e.kind(),e.mains(),Map.copyOf(reads),Map.copyOf(edits),-1,-1));
  }
  private void updateEntry(Path folder, UnaryOperator<Entry> op){
    var f= norm(folder);
    var current= rawEntries();
    assert current.stream().anyMatch(e->e.path().equals(f));
    writeEntries(current.stream().map(e->e.path().equals(f) ? op.apply(e) : e).toList());
  }
  @Override public void setCompiled(Path folder, long millis){ updateActivity(folder,a->new long[]{millis,a[1]}); }
  @Override public void setRun(Path folder, long millis){ updateActivity(folder,a->new long[]{a[0],millis}); }
  private void updateActivity(Path folder, UnaryOperator<long[]> op){
    var f= norm(folder);
    var activity= readActivity();
    activity.put(f,op.apply(activity.getOrDefault(f,new long[]{-1,-1})));
    writeActivity(activity);
  }
  @Override public String infoText(){
    return Files.exists(infoFile()) ? StringFiles.read(infoFile(),UserError.onFileError()) : InfoPrinter.print(EntrySchema.toInfo(List.of()));
  }
  @Override public void commitInfoText(String text){
    var entries= EntrySchema.fromInfo(text,InfoParser.parse(text,infoUri()));
    writeEntries(entries);
  }
  private void writeEntries(List<Entry> entries){ writeText(infoFile(),InfoPrinter.print(EntrySchema.toInfo(entries))); }
  private static Path norm(Path folder){ return folder.toAbsolutePath().normalize(); }
  private Map<Path,long[]> readActivity(){
    var out= new LinkedHashMap<Path,long[]>();
    if (!Files.exists(activityFile())){ return out; }
    for (var line: StringFiles.read(activityFile(),UserError.onFileError()).lines().toList()){
      if (line.isBlank()){ continue; }
      var parts= line.split(" ",3);
      if (parts.length != 3){ continue; }
      try{ out.put(Path.of(new URI(parts[2])),new long[]{Long.parseLong(parts[0]),Long.parseLong(parts[1])}); }
      catch(URISyntaxException|IllegalArgumentException|FileSystemNotFoundException e){}
    }
    return out;
  }
  private void writeActivity(Map<Path,long[]> activity){
    var lines= activity.entrySet().stream().map(e->e.getValue()[0]+" "+e.getValue()[1]+" "+e.getKey().toUri());
    writeText(activityFile(),Join.of(lines,"","\n","\n",""));
  }
  private void writeText(Path file, String text){
    var tmp= managerDir.resolve(UUID.randomUUID()+".tmp");
    StringFiles.writeNew(tmp,text,UserError.onFileError());
    try{ Files.move(tmp,file,ATOMIC_MOVE); }
    catch(IOException e){ throw Violation.couldNotSaveRegisteredFolders(managerDir,e); }
  }
}
