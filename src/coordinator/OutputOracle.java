package coordinator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import utils.IoErr;
import utils.Join;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import apiJson.ApiJson;
import coordinatorMessages.CacheCorruptionError;
import core.E.Literal;
import core.M;
import core.OtherPackages;
import core.TName;
import tools.Fs;

public interface OutputOracle{
  Path rootDir();
  default long baseApiStamp(){ return Fs.lastModified(rootDir().resolve("base.json")); }
  default long mapStamp(){ return Fs.lastModified(rootDir().resolve("_map.json")); }
  default long pkgApiStamp(String pkg){ return Fs.lastModified(rootDir().resolve(pkg+".json")); }
  
  default void write(String path, Consumer<Consumer<String>> dataProducer){
    try (BufferedWriter writer = Files.newBufferedWriter(rootDir().resolve(path))){
      dataProducer.accept(content -> {
        try { writer.write(content); }
        catch (IOException e){ throw new UncheckedIOException(e); }
      });
    }
    catch (IOException e){ throw new UncheckedIOException(e); }
  }
  default OtherPackages addCachedPkgApi(OtherPackages other, String pkg){
    var path= rootDir().resolve(pkg+".json");
    var api= new OutputHelper().pgkApiFromJSon(path);
    if (api.isEmpty()){ throw CacheCorruptionError.startRepair_missingPkgApiFile(path); }
    return other.mergeWith(api.get(), other.stamp());
  }//READS the pkg info and adds to other; Does not update the disk. Just reads info
  default long commitPkgApi(String pkg, List<Literal> core, long minExclusiveMillis){
    var path= rootDir().resolve(pkg+".json");
    var res= new OutputHelper().pgkApiFromJSon(path);
    if (res.isEmpty()){ return Fs.writeUtf8(path, ApiJson.toJSon(core),-1); }
    if (new OutputHelper().consistent(res.get(),core)){ return minExclusiveMillis; }
    return Fs.writeUtf8(path, ApiJson.toJSon(core),minExclusiveMillis);
    }
  default long commitMap(Map<String,Map<String,String>> map, long minExclusiveMillis){
    var path= rootDir().resolve("_map.json");
    var res= new OutputHelper().mapFromJSon(path);
    if (res.isEmpty()){ return Fs.writeUtf8(path, new OutputHelper().toJSon(map),-1); }
    if (res.get().equals(map)){ return minExclusiveMillis; }
    return Fs.writeUtf8(path, new OutputHelper().toJSon(map),minExclusiveMillis);
  }
  //commitMap only write if different from the old, and in that case it will bumps mtime strictly above minExclusiveMillis
}

class OutputHelper{
  private static final String asciiWhitelist=
      "0123456789" +
      "abcdefghijklmnopqrstuvwxyz" +
      "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
      "+-*/=<>,.;:()[]{}" +
      "`'\"!?@#$%^&_|~\\" +
      " \n";
  String toJSon(Map<String,Map<String,String>> map){
    if (map.isEmpty()){ return "{}"; }
    return obj(map, m->obj(m, s->"\""+s+"\""));
  }
  Optional<Map<TName,Literal>> pgkApiFromJSon(Path p){
    if (!IoErr.of(()->Files.exists(p))){ return Optional.empty(); }
    var s= IoErr.of(() -> Files.readString(p));
    assert s.chars().allMatch(c -> asciiWhitelist.indexOf(c) >= 0) : "Non-whitelisted char in "+p;//should cause Cachecorruption instead
    var out= new LimitedJsonParser(s, p).apiJsonToMap();
    return Optional.of(out);
  }
  private <T> String obj(Map<String,T> m, Function<T,String> v){
    return Join.of(m.entrySet().stream()
      .map(e->"\""+e.getKey()+"\":"+v.apply(e.getValue())),
      "{",",\n","}\n");//correctly throw for empty
  }
  Optional<Map<String,Map<String,String>>> mapFromJSon(Path p){
    if (!IoErr.of(()->Files.exists(p))){ return Optional.empty(); }
    var s= IoErr.of(() -> Files.readString(p));
    assert s.chars().allMatch(c -> asciiWhitelist.indexOf(c) >= 0) : "Non-whitelisted char in "+p;
    var out= new LimitedJsonParser(s,p).obj2();
    return Optional.of(out);
  }
  boolean consistent(Map<TName,Literal> map, List<Literal> core){
    for (var l: core){
      if (!l.name().isPublic()){ continue; }
      var cached= map.get(l.name());
      if (cached == null){ return false; }
      if (!eqApi(l, cached)){ return false; }
    }
    return map.size() == (int)core.stream().filter(l->l.name().isPublic()).count();
  }
  private static boolean eqApi(Literal a, Literal b){
    if (a.rc() != b.rc()){ return false; }
    if (!a.name().equals(b.name())){ return false; }
    if (!a.bs().equals(b.bs())){ return false; }
    if (!a.cs().equals(b.cs())){ return false; }
    return eqMs(a.ms(), b.ms());
  }
  private static boolean eqMs(List<M> a, List<M> b){
    if (a.size() != b.size()){ return false; }
    for (int i= 0; i < a.size(); i += 1){
      if (!a.get(i).sig().equals(b.get(i).sig())){ return false; }
    }
    return true;
  }
}