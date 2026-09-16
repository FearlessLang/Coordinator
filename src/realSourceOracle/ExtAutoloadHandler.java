package realSourceOracle;

import java.util.List;
import java.util.function.Predicate;

import tools.SourceOracle;

public record ExtAutoloadHandler(Predicate<String> matches, String baseType) implements AutoloadHandler{
  @Override public AutoloadedRes generate(SourceOracle.Ref ref, String pkgName){
    if (!matches.test(ref.fearPath())){ return AutoloadedRes.none(); }
    var type= AutoloadHandler.standardTypeName(pkgName, ref);
    return new AutoloadedRes(
      type+": "+baseType+"{\n"
      +AssetAutoload.descriptorMethods(ref)
      +"}\n",
      List.of(type)
    );
  }
}
