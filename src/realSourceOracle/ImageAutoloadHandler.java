package realSourceOracle;

import java.util.List;

import tools.SourceOracle;

public final class ImageAutoloadHandler implements AutoloadHandler{
  @Override public AutoloadedRes generate(SourceOracle.Ref ref, String pkgName){
    if (!isImage(ref.fearPath())){ return AutoloadedRes.none(); }
    var type= AutoloadHandler.standardTypeName(pkgName, ref);
    return new AutoloadedRes(
      type+": base.ImageFile{\n"
      +AssetAutoload.descriptorMethods(ref)
      +"}\n",
      List.of(type)
    );
  }

  private static boolean isImage(String path){
    return path.endsWith(".png")
      || path.endsWith(".jpg")
      || path.endsWith(".jpeg")
      || path.endsWith(".gif")
      || path.endsWith(".bmp");
  }
}