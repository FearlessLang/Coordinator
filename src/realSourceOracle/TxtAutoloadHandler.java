package realSourceOracle;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import tools.SourceOracle;
//TODO: move many of those methods into AutoloadHandler
public final class TxtAutoloadHandler implements AutoloadHandler{
  @Override public AutoloadedRes generate(SourceOracle.Ref ref, String pkgName, List<String> alreadyDeclaredTypes){
    if (!ref.fearPath().endsWith(".txt")){ return AutoloadedRes.none(); }
    var type= AutoloadHandler.standardTypeName(pkgName, ref);
    return new AutoloadedRes(
      type+": base.TxtFile{\n"
      +"  .path: base.Str -> `"+ref.fearPath()+"`;\n"
      +"  .diskPath: base.Str -> `"+diskPath(ref)+"`;\n"
      +"  .zipSteps: base.Str -> `"+zipSteps(ref)+"`;\n"
      +"  .zipEntry: base.Str -> `"+zipEntry(ref)+"`;\n"
      +"}\n",
      List.of(type)
    );
  }
  private static String diskPath(SourceOracle.Ref ref){
    if (ref instanceof PathEntry p){ return localPath(p.local()); }
    if (ref instanceof ZipEntry z){ return localPath(z.local()); }
    throw new AssertionError(ref.getClass());
  }
  private static String zipSteps(SourceOracle.Ref ref){
    if (ref instanceof PathEntry){ return ""; }
    if (ref instanceof ZipEntry z){ return String.join(";", z.zips()); }
    throw new AssertionError(ref.getClass());
  }
  private static String zipEntry(SourceOracle.Ref ref){
    if (ref instanceof PathEntry){ return ""; }
    if (ref instanceof ZipEntry z){ return z.lastZips(); }
    throw new AssertionError(ref.getClass());
  }
  private static String localPath(java.nio.file.Path local){
    return StreamSupport.stream(local.spliterator(), false)
      .map(java.nio.file.Path::toString)
      .collect(Collectors.joining("/"));
  }
}