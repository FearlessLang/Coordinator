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
    write(Push.of(all,new Entry(f,-1,-1)));
  }
  @Override public void removeRegisteredFolder(Path folder){
    var f= norm(folder);
    write(registered().stream().filter(e->!e.folder().equals(f)).toList());
  }
  @Override public void setCompiled(Path folder, long millis){ update(folder,e->new Entry(e.folder(),millis,e.run())); }
  @Override public void setRun(Path folder, long millis){ update(folder,e->new Entry(e.folder(),e.compiled(),millis)); }
  private void update(Path folder, UnaryOperator<Entry> op){
    var f= norm(folder);
    assert isRegistered(f);
    write(registered().stream().map(e->e.folder().equals(f) ? op.apply(e) : e).toList());
  }
  private static Path norm(Path folder){ return folder.toAbsolutePath().normalize(); }
  private Entry entry(String line){
    var parts= line.split(" ",3);
    if (parts.length != 3){ throw Violation.registeredFoldersUnreadable(managerDir); }
    try { return new Entry(Path.of(new URI(parts[2])),Long.parseLong(parts[0]),Long.parseLong(parts[1])); }
    catch(URISyntaxException|IllegalArgumentException|FileSystemNotFoundException e){
      throw Violation.registeredFoldersUnreadable(managerDir);
    }
  }
  private static String line(Entry e){ return e.compiled()+" "+e.run()+" "+e.folder().toUri(); }
  private void write(List<Entry> all){
    var text= Join.of(all.stream().map(DiskData::line),"","\n","\n","");
    var tmp= managerDir.resolve(UUID.randomUUID()+".tmp");
    StringFiles.writeNew(tmp,text,UserError.onFileError());
    try { Files.move(tmp,file(),ATOMIC_MOVE); }
    catch(IOException e){ throw Violation.couldNotSaveRegisteredFolders(managerDir,e); }
  }
}
