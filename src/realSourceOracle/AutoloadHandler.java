package realSourceOracle;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import core.TName;
import tools.SourceOracle;
import userMessages.Report;
import utils.Pop;

public record AutoloadHandler(Predicate<String> matches, String baseType){
  String generate(SourceOracleWithAutoload.Triple t, String path, String type){
    return type+": "+baseType+"{\n"+AssetAutoload.descriptorMethods(t, path)+"}\n";
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
}
