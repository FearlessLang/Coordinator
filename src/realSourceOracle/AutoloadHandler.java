package realSourceOracle;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import tools.SourceOracle;
import utils.Pop;

public interface AutoloadHandler{
  AutoloadedRes generate(SourceOracle.Ref ref, String pkgName, List<String> alreadyDeclaredTypes);
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
    return Pop.right(cs).stream()
      .map(AutoloadHandler::capFirst)
      .collect(Collectors.joining())
      +capFirst(dropExt(cs.getLast()));
  }
  static String capFirst(String s){
    char c= s.charAt(0);
    if ('a' > c || c > 'z'){ return s; }
    return ""+(char)(c - 'a' + 'A')+s.substring(1);
  }
}