package coordinator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import utils.Join;
import java.util.List;
import java.util.function.Function;
import apiJson.ApiJson;
import userMessages.Violation;
import core.AllLs;
import core.E.Literal;
import core.M;
import core.OtherPackages;
import core.Sig;
import core.TName;
import tools.Fs;
import tools.SourceOracle.Ref;

public interface OutputOracle{
  Path rootDir();
  default long mapStamp(){ return Fs.lastModified(rootDir().resolve("_map.json")); }
  default long pkgApiStamp(String pkg){ return Fs.lastModified(rootDir().resolve(pkg+".json")); }
  private Path builtPath(String pkg){ return rootDir().resolve(pkg+".built"); }
  default boolean stillBuilt(String pkg, List<Ref> files, long minMillis){
    return Fs.lastModified(builtPath(pkg)) >= minMillis && Fs.readUtf8(builtPath(pkg)).equals(OutputHelper.fileList(files));
  }
  default void commitBuilt(String pkg, List<Ref> files, long minExclusiveMillis){
    Fs.writeUtf8(builtPath(pkg), OutputHelper.fileList(files), minExclusiveMillis);
  }
  default OtherPackages addCachedPkgApi(OtherPackages other, String pkg){
    var path= rootDir().resolve(pkg+".json");
    return other.mergeWith(OutputHelper.cachedPkgApi(path), Math.max(other.stamp(), Fs.lastModified(path)));
  }//READS the pkg info and adds to other; Does not update the disk. Just reads info
  default OtherPackages startCachedPkgApi(String pkg,Map<String,Map<String,String>> map,long stamp){
    return OtherPackages.start(map,OutputHelper.cachedPkgApi(rootDir().resolve(pkg+".json")),stamp);
  }
  default long commitPkgApi(String pkg, List<Literal> core, long minExclusiveMillis){
    var path= rootDir().resolve(pkg+".json");
    var res= OutputHelper.pgkApiFromJSon(path);
    if (res.isPresent() && OutputHelper.consistent(res.get(),core)){ return Fs.lastModified(path); }
    return Fs.writeUtf8(path, ApiJson.toJSon(core), res.isEmpty() ? -1 : minExclusiveMillis);
  }
  default void commitMains(String pkg, List<Literal> core){ Fs.writeUtf8(rootDir().resolve(pkg+".mains"), Helper.mainsText(core)); }
  default long commitMap(Map<String,Map<String,String>> map, long minExclusiveMillis){
    var path= rootDir().resolve("_map.json");
    var res= OutputHelper.mapFromJSon(path);
    if (res.filter(map::equals).isPresent()){ return Fs.lastModified(path); }
    return Fs.writeUtf8(path, OutputHelper.toJSon(map), res.isEmpty() ? -1 : minExclusiveMillis);
  }
  //commitMap only write if different from the old, and in that case it will bumps mtime strictly above minExclusiveMillis
}

class OutputHelper{
  static String fileList(List<Ref> files){ return Join.of(files.stream().map(Ref::fearPath).sorted(),"","\n",""); }
  static String toJSon(Map<String,Map<String,String>> map){
    if (map.isEmpty()){ return "{}"; }
    return obj(map, m->obj(m, s->"\""+s+"\""));
  }
  static Optional<Map<TName,Literal>> pgkApiFromJSon(Path p){ return readAllowed(p).map(s->new LimitedJsonParser(s, p).apiJsonToMap()); }
  static Map<TName,Literal> cachedPkgApi(Path p){ return pgkApiFromJSon(p).orElseThrow(()->Violation.cacheMissingPkgApiFile(p)); }
  private static <T> String obj(Map<String,T> m, Function<T,String> v){
    return Join.of(m.entrySet().stream()
      .map(e->"\""+e.getKey()+"\":"+v.apply(e.getValue())),
      "{",",\n","}\n");//correctly throw for empty
  }
  static Optional<Map<String,Map<String,String>>> mapFromJSon(Path p){ return readAllowed(p).map(s->new LimitedJsonParser(s,p).obj2()); }
  private static Optional<String> readAllowed(Path p){
    if (!Files.exists(p)){ return Optional.empty(); }
    var s= Fs.readUtf8(p);
    if (!s.chars().allMatch(c -> Fs.allowed.indexOf(c) >= 0)){ throw Violation.cacheInvalidFile(p, "Non-whitelisted char"); }
    return Optional.of(s);
  }
  static boolean consistent(Map<TName,Literal> map, List<Literal> core){
    var allCore= AllLs.of(core).values();
    //Not filtered to public-only: privates can still be mentioned in meth parameters and ret types.
    return map.size() == allCore.size() && allCore.stream().allMatch(l->map.containsKey(l.name()) && eqApi(l, map.get(l.name())));
  }
  private static boolean eqApi(Literal a, Literal b){
    return a.rc() == b.rc() && a.name().equals(b.name()) && a.thisName().equals(b.thisName())
      && a.bs().equals(b.bs()) && a.cs().equals(b.cs()) && sigs(a).equals(sigs(b));
  }
  private static List<Sig> sigs(Literal l){ return l.ms().stream().map(M::sig).toList(); }
}