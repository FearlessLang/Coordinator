package realSourceOracle;

import java.util.Arrays;
import java.util.stream.Collectors;

import tools.SourceOracle;
import utils.Bug;

final class AssetAutoload{
  private AssetAutoload(){}

  static String descriptorMethods(SourceOracle.Ref ref, String path){
    var t= triple(ref);
    return ""
      +"  .path: base.Str -> \""+path+"\";\n"
      +"  .diskPath: base.Str -> \""+t.diskPath()+"\";\n"
      +"  .zipSteps: base.Str -> \""+t.zipSteps()+"\";\n"
      +"  .zipEntry: base.Str -> \""+t.zipEntry()+"\";\n"
      +"  .originalFileName: base.Str -> \""+AutoloadHandler.components(path).getLast()+"\";\n";
  }

  static SourceOracleWithAutoload.Triple triple(SourceOracle.Ref ref){ return switch(ref){
    case PathEntry p -> new SourceOracleWithAutoload.Triple(localPath(p.local()), "", "");
    case ZipEntry z -> new SourceOracleWithAutoload.Triple(localPath(z.local()),
      z.zips().stream().map(AssetAutoload::portableZipPath).collect(Collectors.joining(";")),
      portableZipPath(z.lastZips()));
    default -> throw Bug.unreachable();
  };}

  private static String localPath(java.nio.file.Path local){
    if (local.isAbsolute() || !local.normalize().equals(local)){ throw Bug.of(""+local); }
    return portableZipPath(String.join("/", PathEntry.localSegments(local)));
  }

  private static String portableZipPath(String path){
    return Arrays.stream(path.split("/", -1))
      .map(AssetAutoload::portableSegment)
      .collect(Collectors.joining("/"));
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
}
