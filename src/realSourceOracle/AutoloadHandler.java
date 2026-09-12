package realSourceOracle;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import core.TName;
import tools.SourceOracle;
import userMessages.Report;
import utils.Pop;

public interface AutoloadHandler{
  AutoloadedRes generate(SourceOracle.Ref ref, String pkgName);
  static List<String> componentsAfterPackage(String pkgName,SourceOracle.Ref ref){
    var cs= components(ref);
    int i= cs.indexOf(pkgName);
    assert i >= 0 && i + 1 < cs.size();
    return cs.subList(i + 1, cs.size());
  } 
  static List<String> components(SourceOracle.Ref ref){
    assert ref.fearPath().startsWith(SourceOracle.root);
    return Stream.of(ref.fearPath().substring(SourceOracle.root.length()).split("/")).toList();
  }
  static String dropExt(String name){
    int dot= name.lastIndexOf('.');
    assert dot > 0;
    name= name.substring(0,dot);
    assert name.indexOf('.') == -1;
    return name;
  }
  static String standardTypeName(String pkgName,SourceOracle.Ref ref){
    var cs= componentsAfterPackage(pkgName,ref);
    var res= Pop.right(cs).stream()
      .map(AutoloadHandler::capFirst)
      .collect(Collectors.joining())
      +capFirst(dropExt(cs.getLast()));
    if (!TName.isTypeName(res)){ throw Report.autoloadedNameNotAType(ref, res); }
    return res;
  }
  Pattern lowerToCap= Pattern.compile("(^|_)([a-z])");
  static String capFirst(String s){ return lowerToCap.matcher(s).replaceAll(m->m.group(2).toUpperCase(Locale.ROOT)); }
}