package realSourceOracle;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.StreamSupport;

import tools.Fs;
import tools.SourceOracle;
import userMessages.Report;
import utils.Join;

public record PathEntry(Path root, Path local, Since since) implements SourceOracle.Ref{
  public PathEntry(Path root, Path local){ this(root, local, Since.anyTime); }
  @Override public String fearPath(){ return Join.of(localSegments(local), SourceOracle.root, "/", ""); }
  @Override public byte[] loadBytes(){ return since.read(root.resolve(local), () -> Fs.of(() -> Fs.readAllBytes(root.resolve(local)))); }
  @Override public long lastModified(){ return Fs.lastModified(root.resolve(local)); }
  @Override public RuntimeException notUtf8(long line, int offset){ return Report.notUtf8(this, line, offset); }
  @Override public String toString(){ return fearPath(); }
  static List<String> localSegments(Path local){
    return StreamSupport.stream(local.spliterator(), false).map(Path::toString).toList();
  }
}
