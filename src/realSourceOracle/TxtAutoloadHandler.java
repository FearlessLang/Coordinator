package realSourceOracle;

import java.util.List;

import tools.SourceOracle;

public final class TxtAutoloadHandler implements AutoloadHandler{
  @Override public AutoloadedRes generate(SourceOracle.Ref ref, String pkgName){
    if (!ref.fearPath().endsWith(".txt")){ return AutoloadedRes.none(); }
    var type= AutoloadHandler.standardTypeName(pkgName, ref);
    return new AutoloadedRes(
      type+": base.TxtFile{\n"
      +AssetAutoload.descriptorMethods(ref)
      +"}\n",
      List.of(type)
    );
  }
}