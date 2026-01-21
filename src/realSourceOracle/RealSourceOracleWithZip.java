package realSourceOracle;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import coordinatorMessages.UserExit;
import tools.SourceOracle;

public final class RealSourceOracleWithZip implements SourceOracle{
  private final List<Ref> allFiles;
  private final Map<URI,Ref> byUri;
  public RealSourceOracleWithZip(Path root){
    if (!Files.isDirectory(root)){ throw UserExit.rootNotDirectory(); }
    allFiles= new BuildWithZip(root).build();
    byUri= allFiles.stream().collect(Collectors.toUnmodifiableMap(r->r.fearURI().normalize(), r->r));
  }
  @Override public List<Ref> allFiles(){ return allFiles; }
  @Override public String loadString(URI uri){
    var r= byUri.get(uri);
    assert r != null : "No such file: "+uri;
    return r.loadString();
  }
}