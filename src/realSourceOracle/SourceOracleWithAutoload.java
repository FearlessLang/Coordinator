package realSourceOracle;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import core.TName;
import tools.SourceOracle;
import userMessages.Report;
import utils.Pos;
import utils.Push;

public record SourceOracleWithAutoload(SourceOracle base, Ref autoload, URI autoloadUri, List<Ref> allFiles)
    implements SourceOracle{
  /** A file the compiler recognized as auto-imported: exactly the diskPath/zipSteps/zipEntry
   * triple base.AssetBytesRead.checkAutoloaded validates a runtime asset read against. */
  public record Triple(String diskPath, String zipSteps, String zipEntry){}
  public record Res(SourceOracle oracle, List<Ref> newRefs, Map<String,Triple> autoloadedAssets){
    public List<Ref> sources(List<Ref> files){ return Push.of(files.stream().filter(f -> f.fearPath().endsWith(".fear")).toList(), newRefs); }
  }
  public static final String autoloadFileSuffix= "/autoloaded_assets.fear";
  static final AutoloadHandler image= new AutoloadHandler(p -> p.endsWith(".png") || p.endsWith(".jpg") || p.endsWith(".jpeg") || p.endsWith(".gif") || p.endsWith(".bmp"), new TName("base.ImageFile", 0, Pos.unknown));
  public static final List<AutoloadHandler> handlers= List.of(new AutoloadHandler(p -> p.endsWith(".txt"), new TName("base.TxtFile", 0, Pos.unknown)), image);
  private record Entry(AutoloadHandler h, Triple t, String path, String type){}
  private static final Function<Ref,String> basePath= r -> SourceOracle.root+"_base/"+r.fearPath().substring(SourceOracle.root.length());
  public static Res of(SourceOracle base, String pkgName, Function<TName,String> names){ return of(base, pkgName, Ref::fearPath, names); }
  public static Res ofBase(SourceOracle stLib){ return of(stLib, "_base", basePath, TName::simpleName); }
  public static Optional<Triple> imageAsset(SourceOracle src, SourceOracle stLib, TName type){
    assert type.arity() == 0;
    var pkg= type.pkgName();
    var es= pkg.equals("base") ? entries(stLib, "_base", basePath) : entries(src, "_"+pkg, Ref::fearPath);
    return es.stream().filter(e -> e.type().equals(type.simpleName())).map(Entry::t).findFirst()
      .filter(t -> image.matches().test(t.zipEntry().isEmpty() ? t.diskPath() : t.zipEntry()));
  }
  private static Res of(SourceOracle base, String pkgName, Function<Ref,String> path, Function<TName,String> names){
    var es= entries(base, pkgName, path);
    if (es.isEmpty()){ return new Res(base, List.of(), Map.of()); }
    var text= es.stream().map(e -> e.h().generate(e.t(), e.path(), e.type(), names)).collect(Collectors.joining());
    var auto= new SyntheticRef(SourceOracle.root+pkgName+autoloadFileSuffix, text);
    var all= Push.of(base.allFiles(), auto);
    var assets= new LinkedHashMap<String,Triple>();
    es.forEach(e -> assets.put(e.type(), e.t()));
    return new Res(
      new SourceOracleWithAutoload(base, auto, auto.fearURI().normalize(), all),
      List.of(auto),
      Collections.unmodifiableMap(assets)
    );
  }  @Override public String loadString(URI uri){
    if (uri.normalize().equals(autoloadUri)){ return autoload.loadString(); }
    return base.loadString(uri);
  }
  private static boolean suppressed(SourceOracle base, String pkgName, Function<Ref,String> path){
    return base.allFiles().stream().map(path).anyMatch(n -> n.endsWith(autoloadFileSuffix) && n.contains("/"+pkgName+"/"));
  }
  private static List<Entry> entries(SourceOracle base, String pkgName, Function<Ref,String> path){
    if (suppressed(base, pkgName, path)){ return List.of(); }
    var res= new ArrayList<Entry>();
    var declaredBy= new LinkedHashMap<String,Ref>();
    for (var ref : base.allFiles()){
      var p= path.apply(ref);
      if (!p.contains("/"+pkgName+"/")){ continue; }
      for (var h : handlers){
        if (!h.matches().test(p)){ continue; }
        var type= AutoloadHandler.standardTypeName(pkgName, ref, p);
        var prev= declaredBy.putIfAbsent(type, ref);
        if (prev != null){ throw Report.autoloadedNamesCollide(ref, prev.fearPath(), ref.fearPath(), type); }
        res.add(new Entry(h, AutoloadHandler.triple(ref), p, type));
      }
    }
    return Collections.unmodifiableList(res);
  }  private record SyntheticRef(String fearPath, String text) implements Ref{
    @Override public byte[] loadBytes(){ return text.getBytes(UTF_8); }
    @Override public long lastModified(){ return 0; }
    @Override public String toString(){ return fearPath; }
  }
}
