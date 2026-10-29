package realSourceOracle;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import core.LiteralDeclarations;
import core.TName;
import tools.Fs;
import tools.SourceOracle;
import userMessages.Report;
import utils.Bug;
import utils.Join;

public record AutoloadHandler(Predicate<String> matches, TName baseType){
  String generate(SourceOracleWithAutoload.Triple t, String path, String type, Function<TName,String> names){
    var str= names.apply(LiteralDeclarations.baseStr);
    return type+": "+names.apply(baseType)+"{\n"
      +"  .path: "+str+" -> \""+path+"\";\n"
      +"  .diskPath: "+str+" -> \""+t.diskPath()+"\";\n"
      +"  .zipSteps: "+str+" -> \""+t.zipSteps()+"\";\n"
      +"  .zipEntry: "+str+" -> \""+t.zipEntry()+"\";\n"
      +"  .originalFileName: "+str+" -> \""+components(path).getLast()+"\";\n"
      +"}\n";
  }
  static SourceOracleWithAutoload.Triple triple(SourceOracle.Ref ref){ return switch (ref){
    case PathEntry p -> new SourceOracleWithAutoload.Triple(localPath(p.local()), "", "");
    case ZipEntry z -> new SourceOracleWithAutoload.Triple(localPath(z.local()),
      Join.of(z.zips().stream().map(AutoloadHandler::portableZipPath), "", ";", "", ""),
      portableZipPath(z.lastZips()));
    default -> throw Bug.unreachable();
  };}
  private static String localPath(Path local){
    if (local.isAbsolute() || !local.normalize().equals(local)){ throw Bug.of(""+local); }
    return portableZipPath(Join.of(PathEntry.localSegments(local), "", "/", ""));
  }
  private static String portableZipPath(String path){
    Stream.of(path.split("/", -1))
      .filter(s -> s.isEmpty() || s.startsWith(".") || s.indexOf('\\')>=0 || s.indexOf(';')>=0)
      .findFirst().ifPresent(s -> { throw Bug.of(s); });
    return path;
  }
  static List<String> components(String path){
    assert path.startsWith(SourceOracle.root);
    return Stream.of(path.substring(SourceOracle.root.length()).split("/")).toList();
  }
  public static String dropExt(String name){ var dot= name.lastIndexOf('.'); assert dot > 0; return name.substring(0, dot); }
  static String standardTypeName(String pkgName, SourceOracle.Ref ref, String path){
    var all= components(dropExt(path));
    var i= all.indexOf(pkgName);
    assert i>=0 && i+1<all.size();
    var res= Join.of(all.subList(i + 1, all.size()).stream().map(Fs::capFirst), "", "", "");
    if (!TName.isTypeName(res)){ throw Report.autoloadedNameNotAType(ref, res); }
    return res;
  }
}
