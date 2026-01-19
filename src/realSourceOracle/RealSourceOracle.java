package realSourceOracle;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import coordinatorMessages.UserExit;

import static offensiveUtils.Require.*;

import tools.Fs;
import tools.SourceOracle;
import utils.IoErr;

public final class RealSourceOracle implements SourceOracle{
  private final List<Ref> allFiles;
  private final Map<URI,Ref> byUri;
  public RealSourceOracle(Path root){
    if (!Files.isDirectory(root)){ throw UserExit.rootNotDirectory(); }
    var files= new Build(root).build();
    allFiles= files.entrySet().stream()
      .<Ref>map(e-> new RealRef(e.getKey(), e.getValue()))
      .toList();
    byUri= allFiles.stream().collect(Collectors.toUnmodifiableMap(r->r.fearURI().normalize(), r->r));
  }
  @Override public List<Ref> allFiles(){ return allFiles; }

  @Override public String loadString(URI uri){
    var r= byUri.get(uri);
    assert r != null : "No such file: "+uri;
    return r.loadString();
  }
  private record RealRef(String fearPath, Path diskPath) implements Ref{
    RealRef(URI uri, Path diskPath){ this(uri.normalize().toString(), diskPath); }
    RealRef{ assert nonNull(fearPath, diskPath); }
    @Override public byte[] loadBytes(){ return IoErr.of(()->Files.readAllBytes(diskPath)); }
    @Override public String loadString(){ return IoErr.of(()->Files.readString(diskPath)); }
    @Override public long lastModified(){ return Fs.lastModified(diskPath); }
    @Override public String toString(){ return fearPath; }
  }
}