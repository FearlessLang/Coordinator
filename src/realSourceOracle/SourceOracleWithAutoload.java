package realSourceOracle;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import core.TName;
import tools.SourceOracle;
import userMessages.Report;
import utils.Push;

public record SourceOracleWithAutoload(SourceOracle base, Ref autoload, URI autoloadUri, List<Ref> allFiles)
    implements SourceOracle{
  /** A file the compiler recognized as auto-imported: exactly the diskPath/zipSteps/zipEntry
   * triple base.AssetBytesRead.checkAutoloaded validates a runtime asset read against. */
  public record Triple(String diskPath, String zipSteps, String zipEntry){}
  public record Res(SourceOracle oracle, List<Ref> newRefs, Map<String,Triple> autoloadedAssets){
    public List<Ref> sources(List<Ref> files){ return Push.of(files.stream().filter(f->f.fearPath().endsWith(".fear")).toList(), newRefs); }
  }
  public static final String autoloadFileSuffix= "/autoloaded_assets.fear";
  static final AutoloadHandler image= new AutoloadHandler(p->p.endsWith(".png") || p.endsWith(".jpg") || p.endsWith(".jpeg") || p.endsWith(".gif") || p.endsWith(".bmp"), "base.ImageFile");
  public static final List<AutoloadHandler> handlers= List.of(new AutoloadHandler(p->p.endsWith(".txt"), "base.TxtFile"), image);
  private record Generated(String text, Map<String,Triple> autoloadedAssets){}
  public static Res of(SourceOracle base, String pkgName){ return of(base, pkgName, Ref::fearPath); }
  public static Res ofBase(SourceOracle stLib){
    return of(stLib, "_base", r->SourceOracle.root+"_base/"+r.fearPath().substring(SourceOracle.root.length()));
  }
  public static Optional<Triple> imageAsset(SourceOracle src, SourceOracle stLib, TName type){
    assert type.arity() == 0;
    var pkg= type.pkgName();
    var res= pkg.equals("base") ? ofBase(stLib) : of(src, "_"+pkg);
    return Optional.ofNullable(res.autoloadedAssets().get(type.simpleName()))
      .filter(t->image.matches().test(t.zipEntry().isEmpty() ? t.diskPath() : t.zipEntry()));
  }
  private static Res of(SourceOracle base, String pkgName, Function<Ref,String> path){
    if (suppressed(base, pkgName, path)){ return new Res(base, List.of(), Map.of()); }
    var gen= generate(base, pkgName, path);
    if (gen.text().isEmpty()){ return new Res(base, List.of(), Map.of()); }
    var auto= syntheticRef(pkgName, gen.text());
    var all= Push.of(base.allFiles(), auto);
    return new Res(
      new SourceOracleWithAutoload(base, auto, auto.fearURI().normalize(), all),
      List.of(auto),
      gen.autoloadedAssets()
    );
  }
  @Override public String loadString(URI uri){
    if (uri.normalize().equals(autoloadUri)){ return autoload.loadString(); }
    return base.loadString(uri);
  }
  private static boolean suppressed(SourceOracle base, String pkgName, Function<Ref,String> path){
    return base.allFiles().stream().map(path).anyMatch(n->n.endsWith(autoloadFileSuffix) && n.contains("/"+pkgName+"/"));
  }
  private static Generated generate(SourceOracle base, String pkgName, Function<Ref,String> path){
    var out= new StringBuilder();
    var declaredBy= new LinkedHashMap<String,Ref>();
    var assets= new LinkedHashMap<String,Triple>();
    for (var ref: base.allFiles()){
      var p= path.apply(ref);
      if (!p.contains("/"+pkgName+"/")){ continue; }
      for (var h: handlers){
        if (!h.matches().test(p)){ continue; }
        var type= AutoloadHandler.standardTypeName(pkgName, ref, p);
        var prev= declaredBy.putIfAbsent(type, ref);
        if (prev != null){ throw Report.autoloadedNamesCollide(ref, prev.fearPath(), ref.fearPath(), type); }
        var t= AssetAutoload.triple(ref);
        out.append(h.generate(t, p, type));
        assets.put(type, t);
      }
    }
    return new Generated(out.toString(), Collections.unmodifiableMap(assets));
  }
  public static Ref syntheticRef(String pkgName, String text){
    return new SyntheticRef(SourceOracle.root+pkgName+autoloadFileSuffix, text);
  }
  private record SyntheticRef(String fearPath, String text) implements Ref{
    @Override public byte[] loadBytes(){ return text.getBytes(UTF_8); }
    @Override public String loadString(){ return text; }
    @Override public long lastModified(){ return 0; }
    @Override public String toString(){ return fearPath; }
  }
}
