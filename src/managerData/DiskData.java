package managerData;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

import fileSupport.StringFiles;
import userMessages.UserError;
import userMessages.Violation;
import utils.Join;
import utils.Push;

public record DiskData(Path managerDir) implements ManagerData{
  private Path file(){ return managerDir.resolve("registered.txt"); }
  @Override public List<Entry> registered(){
    if (!Files.exists(file())){ return List.of(); }
    return StringFiles.read(file(),UserError.onFileError()).lines().filter(l->!l.isBlank()).map(this::entry).toList();
  }
  @Override public void addRegisteredFolder(Path folder){
    var f= norm(folder);
    var all= registered();
    if (all.stream().anyMatch(e->e.folder().equals(f))){ return; }
    assert nestedWith(f).isEmpty();
    write(Push.of(all,new Entry(f,-1,-1,List.of())));
  }
  @Override public void removeRegisteredFolder(Path folder){
    var f= norm(folder);
    write(registered().stream().filter(e->!e.folder().equals(f)).toList());
  }
  @Override public void setCompiled(Path folder, long millis){ update(folder,e->new Entry(e.folder(),millis,e.run(),e.selectedMains())); }
  @Override public void setRun(Path folder, long millis){ update(folder,e->new Entry(e.folder(),e.compiled(),millis,e.selectedMains())); }
  @Override public void setSelectedMains(Path folder, List<String> mains){ update(folder,e->new Entry(e.folder(),e.compiled(),e.run(),mains)); }
  private void update(Path folder, UnaryOperator<Entry> op){
    var f= norm(folder);
    assert isRegistered(f);
    write(registered().stream().map(e->e.folder().equals(f) ? op.apply(e) : e).toList());
  }
  private static Path norm(Path folder){ return folder.toAbsolutePath().normalize(); }
  private Entry entry(String line){
    var parts= line.split(" ",4);
    if (parts.length != 4){ throw Violation.registeredFoldersUnreadable(managerDir); }
    try { return new Entry(Path.of(new URI(parts[3])),Long.parseLong(parts[0]),Long.parseLong(parts[1]),mainsOf(parts[2])); }
    catch(URISyntaxException|IllegalArgumentException|FileSystemNotFoundException e){
      throw Violation.registeredFoldersUnreadable(managerDir);
    }
  }
  private static String line(Entry e){ return e.compiled()+" "+e.run()+" "+mainsToken(e.selectedMains())+" "+e.folder().toUri(); }
  private static String mainsToken(List<String> mains){ return mains.isEmpty() ? "-" : String.join(",",mains); }
  private static List<String> mainsOf(String token){ return token.equals("-") ? List.of() : List.of(token.split(",")); }
  private void write(List<Entry> all){
    var text= Join.of(all.stream().map(DiskData::line),"","\n","\n","");
    var tmp= managerDir.resolve(UUID.randomUUID()+".tmp");
    StringFiles.writeNew(tmp,text,UserError.onFileError());
    try { Files.move(tmp,file(),ATOMIC_MOVE); }
    catch(IOException e){ throw Violation.couldNotSaveRegisteredFolders(managerDir,e); }
  }
}
