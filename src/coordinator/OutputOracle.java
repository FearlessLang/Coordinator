package coordinator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import apiJson.ApiJson;
import core.AllLs;
import core.E.Literal;
import core.M;
import core.OtherPackages;
import core.TName;
import tools.Fs;
import tools.SourceOracle;
import tools.SourceOracle.Ref;
import utils.Join;

public interface OutputOracle{
  Path rootDir();
  default Path mapPath(){ return rootDir().resolve("_map.json"); }
  private Path mainsPath(String pkg){ return rootDir().resolve(pkg+MainsInfo.suffix); }
  private Path pkgApiPath(String pkg){ return rootDir().resolve(pkg+".json"); }
  private Path builtPath(String pkg){ return rootDir().resolve(pkg+".built"); }
  default long mapStamp(){ return Fs.lastModified(mapPath()); }
  default long pkgApiStamp(String pkg){ return Fs.lastModified(pkgApiPath(pkg)); }
  default boolean stillBuilt(String pkg, List<Ref> files, List<String> below, long minMillis){
    return Fs.lastModified(builtPath(pkg))>=minMillis && OutputHelper.read(builtPath(pkg)).orElseThrow().equals(OutputHelper.built(files, below)) && Fs.exists(mainsPath(pkg)) && Fs.exists(pkgApiPath(pkg)) && Fs.exists(Coordinator.genJava(rootDir()).resolve(pkg+".jar"));
  }
  default void forgetAllBut(List<String> pkgs){
    if (!Fs.isDirectory(rootDir())){ return; }
    var gone= Fs.walk(rootDir(), s -> s.map(Path::getFileName).map(Path::toString)
      .filter(n -> n.endsWith(".built")).map(n -> n.substring(0, n.length()-6)).filter(p -> !pkgs.contains(p)).toList());
    gone.forEach(this::forget);
  }
  private void forget(String pkg){
    var gen= Coordinator.genJava(rootDir());
    List.of(builtPath(pkg), pkgApiPath(pkg), mainsPath(pkg), gen.resolve(pkg+".jar"), gen.resolve(pkg+".html"), gen.resolve(pkg+".txt")).forEach(Fs::rmTree);
  }
  default OtherPackages addCachedPkgApi(OtherPackages other, String pkg){
    return other.mergeWith(OutputHelper.cachedPkgApi(pkgApiPath(pkg)), Math.max(other.stamp(), pkgApiStamp(pkg)));
  }//READS the pkg info and adds to other; Does not update the disk. Just reads info
  default OtherPackages startCachedPkgApi(String pkg, Map<String,Map<String,String>> map, Map<String,Ref> ranks, long stamp){
    return OtherPackages.start(map, ranks, OutputHelper.cachedPkgApi(pkgApiPath(pkg)), stamp);
  }
  default long commitPkgApi(String pkg, List<Literal> core, long minExclusiveMillis){
    var res= OutputHelper.pkgApiFromJson(pkgApiPath(pkg));
    if (res.isPresent() && OutputHelper.consistent(res.get(), core)){ return pkgApiStamp(pkg); }
    return Fs.writeUtf8(pkgApiPath(pkg), ApiJson.toJson(core), res.isEmpty() ? -1 : minExclusiveMillis);
  }
  default void relocate(String pkg, SourceOracle src, SourceOracle stLib){
    var m= MainsInfo.parse(mainsPath(pkg));
    var located= m.located(src, stLib);
    if (!located.equals(m)){ Fs.writeUtf8(mainsPath(pkg), located.print()); }
  }
  default long commit(String pkg, List<Literal> core, MainsInfo mains, List<Ref> files, List<String> below, long inputsStamp){
    var newStamp= commitPkgApi(pkg, core, inputsStamp);
    Fs.writeUtf8(mainsPath(pkg), mains.print());
    Fs.writeUtf8(builtPath(pkg), OutputHelper.built(files, below));
    Fs.ofV(() -> Fs.setLastModifiedTime(builtPath(pkg), FileTime.fromMillis(inputsStamp)));
    return newStamp;
  }
  default long commitMap(Map<String,Map<String,String>> map, long minExclusiveMillis){
    var res= OutputHelper.mapFromJson(mapPath());
    if (res.filter(map::equals).isPresent()){ return mapStamp(); }
    return Fs.writeUtf8(mapPath(), OutputHelper.toJson(map), res.isEmpty() ? -1 : minExclusiveMillis);
  }
  //commitMap only write if different from the old, and in that case it will bumps mtime strictly above minExclusiveMillis
}
final class OutputHelper{
  static String built(List<Ref> files, List<String> below){
    return Join.of(files.stream().map(Ref::fearPath).sorted(), "", "\n", "")+Join.of(below.stream().sorted(), "\n---\n", "\n", "", "\n---");
  }
  static String toJson(Map<String,Map<String,String>> map){
    if (map.isEmpty()){ return "{}"; }
    return obj(map, m -> obj(m, s -> "\""+s+"\""));
  }
  static Optional<Map<TName,Literal>> pkgApiFromJson(Path p){ return read(p).map(s -> new LimitedJsonParser(s, p).apiJsonToMap()); }
  static Map<TName,Literal> cachedPkgApi(Path p){ return pkgApiFromJson(p).orElseThrow(); }
  private static <T> String obj(Map<String,T> m, Function<T,String> v){
    return Join.of(m.entrySet().stream()
      .map(e -> "\""+e.getKey()+"\":"+v.apply(e.getValue())),
      "{", ",\n", "}\n");//correctly throw for empty
  }
  static Optional<Map<String,Map<String,String>>> mapFromJson(Path p){ return read(p).map(s -> new LimitedJsonParser(s, p).obj2()); }
  static Optional<String> read(Path p){
    if (!Fs.exists(p)){ return Optional.empty(); }
    return Optional.of(new String(Fs.of(() -> Fs.readAllBytes(p)), StandardCharsets.US_ASCII));
  }
  static boolean consistent(Map<TName,Literal> map, List<Literal> core){
    //Not filtered to public-only: privates can still be mentioned in meth parameters and ret types.
    return api(map.values()).equals(api(AllLs.of(core).values()));
  }
  private static Map<TName,List<?>> api(Collection<Literal> ls){
    return ls.stream().collect(Collectors.toMap(Literal::name, l -> List.of(l.rc(), l.thisName(), l.bs(), l.cs(), l.ms().stream().map(M::sig).toList())));
  }
}
