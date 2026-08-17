package realSourceOracle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import tools.Fs;
import tools.SourceOracle;
import utils.Push;

public record ZipEntry(Path root, Path local, List<String> segments, List<String> zips, String lastZips) implements SourceOracle.Ref{
  @Override public String fearPath(){
    return "fear:/"+Stream.concat(localSegments(local).stream(), segments.stream())
      .collect(Collectors.joining("/"));
  }
  @Override public byte[] loadBytes(){ return ZipLocator.entryBytes(root.resolve(local), zips,lastZips); }
  @Override public long lastModified(){ return Fs.of(()->Files.getLastModifiedTime(root.resolve(local)).toMillis()); }
  static List<String> localSegments(Path local){
    List<String> res= StreamSupport.stream(local.spliterator(),false).map(Path::toString).toList();
    var last= res.getLast();
    assert last.endsWith(".zip");
    return Push.of(res.subList(0, res.size()-1), last.substring(0, last.length()-4));
  }
  @Override public String toString(){ return fearPath(); }
}
