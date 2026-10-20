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
import java.util.stream.IntStream;

import apiJson.ApiJson;
import core.AllLs;
import core.E.Literal;
import core.M;
import core.OtherPackages;
import core.TName;
import metaParser.Message;
import tools.Fs;
import tools.SourceOracle.Ref;
import userMessages.UserError;
import userMessages.Violation;
import utils.Join;

public interface OutputOracle{
  Path rootDir();
  default Path mapPath(){ return rootDir().resolve("_map.json"); }
  default Path mainsPath(){ return rootDir().resolve("mains.info"); }
  private Path mainsPath(String pkg){ return rootDir().resolve(pkg+".mains.info"); }
  private Path pkgApiPath(String pkg){ return rootDir().resolve(pkg+".json"); }
  private Path builtPath(String pkg){ return rootDir().resolve(pkg+".built"); }
  default long mapStamp(){ return Fs.lastModified(mapPath()); }
  default long pkgApiStamp(String pkg){ return Fs.lastModified(pkgApiPath(pkg)); }
  default boolean stillBuilt(String pkg, List<Ref> files, List<String> below, long minMillis){
    return Fs.lastModified(builtPath(pkg))>=minMillis && OutputHelper.readAllowed(builtPath(pkg)).orElseThrow().equals(OutputHelper.built(files, below)) && Fs.exists(mainsPath(pkg));
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
  default OtherPackages startCachedPkgApi(String pkg, Map<String,Map<String,String>> map, long stamp){
    return OtherPackages.start(map, OutputHelper.cachedPkgApi(pkgApiPath(pkg)), stamp);
  }
  default long commitPkgApi(String pkg, List<Literal> core, long minExclusiveMillis){
    var res= OutputHelper.pkgApiFromJson(pkgApiPath(pkg));
    if (res.isPresent() && OutputHelper.consistent(res.get(), core)){ return pkgApiStamp(pkg); }
    return Fs.writeUtf8(pkgApiPath(pkg), ApiJson.toJson(core), res.isEmpty() ? -1 : minExclusiveMillis);
  }
  default Optional<MainsInfo> mainsInfo(){ return Optional.of(mainsPath()).filter(Fs::exists).map(MainsInfo::parse); }
  default long commit(String pkg, List<Literal> core, Map<String,MainsInfo.Main> mains, List<Ref> files, List<String> below, long inputsStamp){
    var newStamp= commitPkgApi(pkg, core, inputsStamp);
    Fs.writeUtf8(mainsPath(pkg), new MainsInfo(mains).print());
    Fs.writeUtf8(builtPath(pkg), OutputHelper.built(files, below));
    Fs.ofV(() -> Fs.setLastModifiedTime(builtPath(pkg), FileTime.fromMillis(inputsStamp)));
    return newStamp;
  }
  default MainsInfo mains(Collection<String> pkgs){
    return new MainsInfo(pkgs.stream().flatMap(p -> MainsInfo.parse(mainsPath(p)).mains().entrySet().stream()).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
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
  static Optional<Map<TName,Literal>> pkgApiFromJson(Path p){ return readAllowed(p).map(s -> new LimitedJsonParser(s, p).apiJsonToMap()); }
  static Map<TName,Literal> cachedPkgApi(Path p){ return pkgApiFromJson(p).orElseThrow(() -> dropCache(p, Violation.cacheMissingPkgApiFile(p))); }
  private static <T> String obj(Map<String,T> m, Function<T,String> v){
    return Join.of(m.entrySet().stream()
      .map(e -> "\""+e.getKey()+"\":"+v.apply(e.getValue())),
      "{", ",\n", "}\n");//correctly throw for empty
  }
  static Optional<Map<String,Map<String,String>>> mapFromJson(Path p){ return readAllowed(p).map(s -> new LimitedJsonParser(s, p).obj2()); }
  static Optional<String> readAllowed(Path p){
    if (!Fs.exists(p)){ return Optional.empty(); }
    var bs= Fs.of(() -> Fs.readAllBytes(p));
    var bad= IntStream.range(0, bs.length).filter(i -> Fs.allowed.indexOf(bs[i]) < 0).findFirst();
    if (bad.isPresent()){ throw dropCache(p, Violation.cacheInvalidFile(p, "Unexpected character "+Message.displayChar((char)(bs[bad.getAsInt()] & 0xff))+" at "+bad.getAsInt())); }
    return Optional.of(new String(bs, StandardCharsets.US_ASCII));
  }
  static UserError dropCache(Path file, UserError err){
    var out= file.getParent();
    assert out.getFileName().toString().equals(Coordinator.outDir);
    Fs.rmTree(out);
    return err;
  }
  static boolean consistent(Map<TName,Literal> map, List<Literal> core){
    //Not filtered to public-only: privates can still be mentioned in meth parameters and ret types.
    return api(map.values()).equals(api(AllLs.of(core).values()));
  }
  private static Map<TName,List<?>> api(Collection<Literal> ls){
    return ls.stream().collect(Collectors.toMap(Literal::name, l -> List.of(l.rc(), l.thisName(), l.bs(), l.cs(), l.ms().stream().map(M::sig).toList())));
  }
}
