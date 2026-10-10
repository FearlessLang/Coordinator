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
import utils.Pop;

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
  public static String dropExt(String name){
    int dot= name.lastIndexOf('.');
    assert dot > 0;
    return name.substring(0,dot);
  }
  static String standardTypeName(String pkgName,SourceOracle.Ref ref,String path){
    var all= components(path);
    int i= all.indexOf(pkgName);
    assert i >= 0 && i + 1 < all.size();
    var cs= all.subList(i + 1, all.size());
    var res= Pop.right(cs).stream()
      .map(AutoloadHandler::capFirst)
      .collect(Collectors.joining())
      +capFirst(dropExt(cs.getLast()));
    if (!TName.isTypeName(res)){ throw Report.autoloadedNameNotAType(ref, res); }
    return res;
  }
  static final Pattern leading= Pattern.compile("^_*[a-z]");
  static final Pattern inner= Pattern.compile("_([a-z])");
  public static String capFirst(String s){
    var m= leading.matcher(s);
    var head= m.find() ? m.end() : 0;
    return s.substring(0,head).toUpperCase(Locale.ROOT)+inner.matcher(s.substring(head)).replaceAll(r->r.group(1).toUpperCase(Locale.ROOT));
  }
  static final Pattern upper= Pattern.compile("[A-Z]");
  static final Pattern visibleAtom= Pattern.compile("[a-z_][a-z0-9_]*");
  public static Optional<String> fileName(String type){
    var res= upper.matcher(type).replaceAll(r->"_"+r.group().toLowerCase(Locale.ROOT)).substring(1);
    return Optional.of(res).filter(r->visibleAtom.matcher(r).matches() && capFirst(r).equals(type));
  }
}
