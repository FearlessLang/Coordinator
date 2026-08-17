package realSourceOracle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import tools.Fs;
import tools.SourceOracle;

public record PathEntry(Path root, Path local) implements SourceOracle.Ref{
  @Override public String fearPath(){ return "fear:/"+localSegments(local).stream().collect(Collectors.joining("/")); }
  @Override public byte[] loadBytes(){ return Fs.of(()->Files.readAllBytes(root.resolve(local))); }
  @Override public long lastModified(){ return Fs.lastModified(root.resolve(local)); }
  @Override public String toString(){ return fearPath(); }
  static List<String> localSegments(Path local){
    return StreamSupport.stream(local.spliterator(),false).map(Path::toString).toList();
  }
}
