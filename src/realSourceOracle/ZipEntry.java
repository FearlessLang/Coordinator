package realSourceOracle;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import tools.Fs;
import tools.SourceOracle;
import utils.Pop;
import utils.Push;

public record ZipEntry(Path root, Path local, List<String> segments, List<String> zips, String lastZips) implements SourceOracle.Ref{
  @Override public String fearPath(){
    return "fear:/"+Stream.concat(localSegments(local).stream(), segments.stream())
      .collect(Collectors.joining("/"));
  }
  @Override public byte[] loadBytes(){ return ZipLocator.entryBytes(root.resolve(local), zips,lastZips); }
  @Override public long lastModified(){ return Fs.lastModified(root.resolve(local)); }
  static List<String> localSegments(Path local){
    var res= PathEntry.localSegments(local);
    return Push.of(Pop.right(res), ZipWellFormedness.lastSegmentOf(res.getLast()));
  }
  @Override public String toString(){ return fearPath(); }
}
