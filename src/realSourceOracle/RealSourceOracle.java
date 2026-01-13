package realSourceOracle;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import tools.SourceOracle;
import utils.Bug;
import utils.IoErr;

public final class RealSourceOracle implements SourceOracle{
  private final Map<URI,Path> files;
  public RealSourceOracle(Path root){
    if (Files.isDirectory(root)){ files= new Build(root).build(); return; }
    throw Policy.fail(Path.of("."), "Problem: root is not a directory."," Start Fearless on the Fearless project directory.");
  }
  @Override public CharSequence load(URI uri){
    if (!SourceOracle.isFile(uri)){ throw new IllegalArgumentException("Only file: URIs are supported: "+uri); }
    var u= uri.normalize();
    var p= files.get(u);
    if (p != null){ return IoErr.of(()->Files.readString(p)); }
    throw new IllegalArgumentException("No such file: "+u);
  }
  @Override public boolean exists(URI uri){
    if (!SourceOracle.isFile(uri)){ return false; }
    return files.containsKey(uri.normalize());
  }
  @Override public long lastModified(URI u){
    throw Bug.todo(); 
  }

  @Override public List<URI> allFiles(){ return files.keySet().stream().toList(); }
}