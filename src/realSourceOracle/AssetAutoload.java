package realSourceOracle;

import java.util.Arrays;
import java.util.stream.Collectors;

import tools.SourceOracle;
import utils.Bug;

final class AssetAutoload{
  private AssetAutoload(){}

  static String descriptorMethods(SourceOracle.Ref ref){
    return ""
      +"  .path: base.Str -> `"+ref.fearPath()+"`;\n"
      +"  .diskPath: base.Str -> `"+diskPath(ref)+"`;\n"
      +"  .zipSteps: base.Str -> `"+zipSteps(ref)+"`;\n"
      +"  .zipEntry: base.Str -> `"+zipEntry(ref)+"`;\n";
  }

  static SourceOracleWithAutoload.Triple triple(SourceOracle.Ref ref){
    return new SourceOracleWithAutoload.Triple(diskPath(ref), zipSteps(ref), zipEntry(ref));
  }

  private static String zipSteps(SourceOracle.Ref ref){
    if (ref instanceof PathEntry){ return ""; }
    if (ref instanceof ZipEntry z){
      return z.zips().stream()
        .map(AssetAutoload::portableZipPath)
        .collect(Collectors.joining(";"));
    }
    throw Bug.unreachable();
  }

  private static String zipEntry(SourceOracle.Ref ref){
    if (ref instanceof PathEntry){ return ""; }
    if (ref instanceof ZipEntry z){ return portableZipPath(z.lastZips()); }
    throw Bug.unreachable();
  }

  private static String localPath(java.nio.file.Path local){
    if (local.isAbsolute()){ throw Bug.of(""+local); }
    if (!local.normalize().equals(local)){ throw Bug.of(""+local); }
    var res= PathEntry.localSegments(local).stream()
      .map(AssetAutoload::portableSegment)
      .collect(Collectors.joining("/"));
    if (res.isEmpty()){ throw Bug.of(""+local); }
    return res;
  }

  private static String portableZipPath(String path){
    var res= Arrays.stream(path.split("/", -1))
      .map(AssetAutoload::portableSegment)
      .collect(Collectors.joining("/"));
    if (res.isEmpty()){ throw Bug.of(path); }
    return res;
  }

  private static String portableSegment(String s){
    if (
      s.isEmpty()
      || s.startsWith(".")
      || s.indexOf('/') >= 0
      || s.indexOf('\\') >= 0
      || s.indexOf(';') >= 0
      ){ throw Bug.of(s); }
    return s;
  }

  private static String diskPath(SourceOracle.Ref ref){
    if (ref instanceof PathEntry p){ return localPath(p.local()); }
    if (ref instanceof ZipEntry z){ return localPath(z.local()); }
    throw Bug.unreachable();
  }
}
