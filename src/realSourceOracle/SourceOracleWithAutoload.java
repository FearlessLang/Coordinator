package realSourceOracle;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import tools.SourceOracle;
import userMessages.Report;
import utils.Push;

public record SourceOracleWithAutoload(SourceOracle base, Ref autoload, URI autoloadUri, List<Ref> allFiles)
    implements SourceOracle{
  public record Res(SourceOracle oracle, List<Ref> newRefs){}
  public static final String autoloadFileSuffix= "/autoloaded_assets.fear";
  public static final List<AutoloadHandler> handlers= List.of(
    new TxtAutoloadHandler(),
    new ImageAutoloadHandler()
  );
  public static Res of(SourceOracle base, String pkgName){
    if (suppressed(base, pkgName)){ return new Res(base, List.of()); }
    var text= generate(base, pkgName, handlers);
    if (text.isEmpty()){ return new Res(base, List.of()); }
    var auto= syntheticRef(pkgName, text);
    var all= Push.of(base.allFiles(), auto);
    return new Res(
      new SourceOracleWithAutoload(base, auto, auto.fearURI().normalize(), all),
      List.of(auto)
    );
  }
  @Override public String loadString(URI uri){
    if (uri.normalize().equals(autoloadUri)){ return autoload.loadString(); }
    return base.loadString(uri);
  }
  private static boolean suppressed(SourceOracle base, String pkgName){
    return base.allFiles().stream().anyMatch(r->suppressFound(r.fearPath(),pkgName));
  }
  private static boolean suppressFound(String rName, String pkgName){
    return rName.endsWith(autoloadFileSuffix) && rName.contains("/"+pkgName+"/");
  }
  private static String generate(SourceOracle base, String pkgName, List<AutoloadHandler> handlers){
    var out= new StringBuilder();
    var declaredBy= new LinkedHashMap<String,Ref>();
    for (var ref: base.allFiles()){
      if (!ref.fearPath().contains("/"+pkgName+"/")){ continue; }
      for (var h: handlers){
        var a= h.generate(ref, pkgName);
        if (a.text().isEmpty()){ continue; }
        a.declaredTypes().forEach(type->checkNotDeclared(declaredBy, ref, type));
        out.append(a.text());
        if (!a.text().endsWith("\n")){ out.append('\n'); }
      }
    }
    return out.toString();
  }
  private static void checkNotDeclared(LinkedHashMap<String,Ref> declaredBy, Ref ref, String type){
    var prev= declaredBy.putIfAbsent(type, ref);
    if (prev != null){ throw Report.autoloadedNamesCollide(ref, prev.fearPath(), ref.fearPath(), type); }
  }
  public static Ref syntheticRef(String pkgName, String text){
    return new SyntheticRef(SourceOracle.root+pkgName+autoloadFileSuffix, text);
  }
  private record SyntheticRef(String fearPath, String text) implements Ref{
    @Override public byte[] loadBytes(){ return text.getBytes(StandardCharsets.UTF_8); }
    @Override public String loadString(){ return text; }
    @Override public long lastModified(){ return 0; }
    @Override public String toString(){ return fearPath; }
  }
}