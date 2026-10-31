package coordinator;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import apiJson.ApiJson;
import core.OtherPackages;
import realSourceOracle.SourceOracleWithAutoload;
import tools.Fs;
import tools.SourceOracle;
import tools.SourceOracle.Ref;
import userMessages.Violation;

public interface Layer{
  default LinkedHashMap<String,List<Ref>> pkgs(){ return new LinkedHashMap<>(); }
  default List<String> pkgNames(){ return List.of("base"); }
  OtherPackages compile(SourceOracle src, OutputOracle out);
  SourceOracle stLib();
}
record MiddleLayer(Coordinator coordinator, Layer next, LinkedHashMap<String,List<Ref>> pkgs) implements Layer{
  MiddleLayer{ assert !pkgs.isEmpty(); }
  @Override public SourceOracle stLib(){ return next.stLib(); }
  @Override public List<String> pkgNames(){ return Stream.concat(next.pkgNames().stream(), pkgs.keySet().stream()).toList(); }
  @Override public OtherPackages compile(SourceOracle src, OutputOracle out){
    var other= next.compile(src, out);
    var nextOther= other;
    for (var pkg : pkgs.keySet()){
      var files= pkgs.get(pkg);
      var maxSrc= files.stream().mapToLong(Ref::lastModified).max().getAsLong();
      var maxIn= Math.max(maxSrc, other.stamp());//out.mapStamp() must be <= other.stamp() since it comes from next
      if (out.stillBuilt(pkg, files, next.pkgNames(), maxIn)){ nextOther = out.addCachedPkgApi(nextOther, pkg); continue; }
      var vres= other.virtualizationMap().getOrDefault(pkg, Map.of());
      var rich= SourceOracleWithAutoload.of(src, "_"+pkg, coordinator.names(pkg, files, src, other, vres));
      var capabilities= new CapabilityEnvironment(rich.autoloadedAssets());
      var core= coordinator.frontend(pkg, rich.sources(files), rich.oracle(), other, vres, capabilities);
      var mains= MainsInfo.of(core, src, stLib());
      coordinator.backend(pkg, core, rich.oracle(), other, capabilities);
      var newStamp= out.commit(pkg, core, mains, files, next.pkgNames(), maxIn); // newStamp will be the old api file mtime if there was no reason to commit.
      nextOther = nextOther.mergeWith(ApiJson.api(core), Math.max(nextOther.stamp(), newStamp));
    }
    return nextOther;
  }
}
record BaseLayer(Coordinator coordinator, Map<String,Map<String,String>> map, Map<String,Ref> ranks, long baseStamp, SourceOracle stLib) implements Layer{
  @Override public OtherPackages compile(SourceOracle _ignoreSrc, OutputOracle out){
    var pkgName= "base";
    var cacheDir= coordinator.baseCachePath();
    if (cacheDir.isPresent()){ return deployedBaseApi(cacheDir.get(), pkgName); }
    var other= OtherPackages.empty();
    var maxIn= stLib.allFiles().stream().mapToLong(Ref::lastModified).max().getAsLong();
    if (out.stillBuilt(pkgName, stLib.allFiles(), List.of(), maxIn)){ return out.startCachedPkgApi(pkgName, map, ranks, Math.max(baseStamp, out.pkgApiStamp(pkgName))); }
    var rich= SourceOracleWithAutoload.ofBase(stLib);
    var capabilities= new CapabilityEnvironment(rich.autoloadedAssets());
    var core= coordinator.frontend(pkgName, rich.sources(stLib.allFiles()), rich.oracle(), other, Map.of(), capabilities);
    coordinator.backend(pkgName, core, rich.oracle(), other, capabilities);
    var newStamp= out.commit(pkgName, core, new MainsInfo(Map.of(), MainsInfo.attenuators(core), List.of(), List.of()), stLib.allFiles(), List.of(), maxIn);
    return OtherPackages.start(map, ranks, ApiJson.api(core), Math.max(baseStamp, newStamp));
  }
  private OtherPackages deployedBaseApi(Path cacheDir, String pkgName){
    var json= cacheDir.resolve(pkgName+".json");
    var api= OutputHelper.pkgApiFromJson(json).orElseThrow(() -> Violation.cacheMissingBaseApiFile(json));
    return OtherPackages.start(map, ranks, api, Math.max(baseStamp, Fs.lastModified(json)));
  }
}
