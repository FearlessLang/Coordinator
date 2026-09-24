package realSourceOracle;

import java.nio.file.Path;
import java.util.List;

import tools.Fs;
import tools.SourceOracle;
import utils.Push;

public record ZipEntry(Path root, Path local, List<String> segments, List<String> zips, String lastZips) implements SourceOracle.Ref{
  @Override public String fearPath(){ return "fear:/"+String.join("/", Push.of(ZipWellFormedness.dropZipExt(PathEntry.localSegments(local)), segments)); }
  @Override public byte[] loadBytes(){ return ZipLocator.entryBytes(root.resolve(local), zips,lastZips); }
  @Override public long lastModified(){ return Fs.lastModified(root.resolve(local)); }
  @Override public String toString(){ return fearPath(); }
}
