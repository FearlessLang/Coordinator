package realSourceOracle;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import tools.Fs;
import tools.SourceOracle;
import userMessages.Report;

public final class RealSourceOracleWithZip implements SourceOracle{
  private final List<Ref> allFiles;
  private final Map<URI,Ref> byUri;
  public RealSourceOracleWithZip(Path root){ this(root, true, Since.anyTime); }
  public RealSourceOracleWithZip(Path root, Since since){ this(root, true, since); }
  private RealSourceOracleWithZip(Path root, boolean dated, Since since){
    if (!Fs.isDirectory(root)){ throw Report.rootNotDirectory(); }
    allFiles= new BuildWithZip(root, dated, since).build();
    byUri= allFiles.stream().collect(Collectors.toUnmodifiableMap(r -> r.fearURI().normalize(), r -> r));
  }
  public static RealSourceOracleWithZip stdLib(Path root){ return new RealSourceOracleWithZip(root, false, Since.anyTime); }
  @Override public List<Ref> allFiles(){ return allFiles; }
  @Override public String loadString(URI uri){ return byUri.get(uri).loadString(); }
}
