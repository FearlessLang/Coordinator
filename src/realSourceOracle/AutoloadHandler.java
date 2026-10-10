package realSourceOracle;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import core.TName;
import tools.SourceOracle;
import userMessages.Report;
import utils.Bug;

public record AutoloadHandler(Predicate<String> matches, String baseType){
  String generate(SourceOracleWithAutoload.Triple t, String path, String type){
    return type+": "+baseType+"{\n"
      +"  .path: base.Str -> \""+path+"\";\n"
      +"  .diskPath: base.Str -> \""+t.diskPath()+"\";\n"
      +"  .zipSteps: base.Str -> \""+t.zipSteps()+"\";\n"
      +"  .zipEntry: base.Str -> \""+t.zipEntry()+"\";\n"
      +"  .originalFileName: base.Str -> \""+components(path).getLast()+"\";\n"
      +"}\n";
  }
  static SourceOracleWithAutoload.Triple triple(SourceOracle.Ref ref){ return switch(ref){
    case PathEntry p -> new SourceOracleWithAutoload.Triple(localPath(p.local()), "", "");
    case ZipEntry z -> new SourceOracleWithAutoload.Triple(localPath(z.local()),
      z.zips().stream().map(AutoloadHandler::portableZipPath).collect(Collectors.joining(";")),
      portableZipPath(z.lastZips()));
    default -> throw Bug.unreachable();
  };}
  private static String localPath(Path local){
    if (local.isAbsolute() || !local.normalize().equals(local)){ throw Bug.of(""+local); }
    return portableZipPath(String.join("/", PathEntry.localSegments(local)));
  }
  private static String portableZipPath(String path){
    Stream.of(path.split("/", -1))
      .filter(s->s.isEmpty() || s.startsWith(".") || s.indexOf('\\') >= 0 || s.indexOf(';') >= 0)
      .findFirst().ifPresent(s->{ throw Bug.of(s); });
    return path;
  }
  static List<String> components(String path){
    assert path.startsWith(SourceOracle.root);
    return Stream.of(path.substring(SourceOracle.root.length()).split("/")).toList();
  }
  public static String dropExt(String name){ int dot= name.lastIndexOf('.'); assert dot > 0; return name.substring(0,dot); }
  static String standardTypeName(String pkgName,SourceOracle.Ref ref,String path){
    var all= components(dropExt(path));
    int i= all.indexOf(pkgName);
    assert i >= 0 && i + 1 < all.size();
    var res= all.subList(i + 1, all.size()).stream().map(AutoloadHandler::capFirst).collect(Collectors.joining());
    if (!TName.isTypeName(res)){ throw Report.autoloadedNameNotAType(ref, res); }
    return res;
  }
  static final Pattern capitals= Pattern.compile("^(_*[a-z])|_([a-z])");
  public static String capFirst(String s){
    return capitals.matcher(s).replaceAll(r->(r.group(1) == null ? r.group(2) : r.group(1)).toUpperCase(Locale.ROOT));
  }
  static final Pattern upper= Pattern.compile("[A-Z]");
  public static Optional<String> fileName(String type){
    var res= upper.matcher(type).replaceAll(r->"_"+r.group().toLowerCase(Locale.ROOT)).substring(1);
    return Optional.of(res).filter(r->r.matches("[a-z_][a-z0-9_]*") && capFirst(r).equals(type));
  }
}
