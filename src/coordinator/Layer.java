package coordinator;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import core.AllLs;
import core.E.Literal;
import realSourceOracle.SourceOracleWithAutoload;
import core.OtherPackages;
import tools.Fs;
import tools.SourceOracle;
import tools.SourceOracle.Ref;
import userMessages.Violation;

public interface Layer{
  default LinkedHashMap<String,List<Ref>> pkgs(){ return new LinkedHashMap<>();}
  OtherPackages compile(SourceOracle src, OutputOracle out);
  SourceOracle stLib();
}
record MiddleLayer(Coordinator coordinator, Layer next, LinkedHashMap<String,List<Ref>> pkgs) implements Layer{
  MiddleLayer{ assert !pkgs.isEmpty(); }
  @Override public SourceOracle stLib(){ return next.stLib(); }
  @Override public OtherPackages compile(SourceOracle src, OutputOracle out){
    OtherPackages other= next.compile(src, out);
    var nextOther= other;
    for (var pkg: pkgs.keySet()){
      var files= pkgs.get(pkg);
      long maxSrc= files.stream().mapToLong(Ref::lastModified).max().getAsLong();
      long maxIn= Math.max(maxSrc, other.stamp());//out.mapStamp() must be <= then other.watermark() since it comes from next
      if (out.stillBuilt(pkg, files, maxIn)){ nextOther= out.addCachedPkgApi(nextOther, pkg); continue; }
      var rich= SourceOracleWithAutoload.of(src, "_"+pkg);
      List<Literal> core= coordinator.frontend(pkg, rich.sources(files), rich.oracle(), other,other.virtualizationMap().getOrDefault(pkg,Map.of()));
      var mains= MainsInfo.of(core, src, stLib());
      coordinator.backend(pkg, core, rich.oracle(), other, new CapabilityEnvironment(rich.autoloadedAssets()));
      long newStamp= out.commit(pkg, core, mains, files, maxIn); // newStamp will be the old api file mtime if there was no reason to commit.
      nextOther = nextOther.mergeWith(AllLs.of(core),Math.max(nextOther.stamp(),newStamp));
    }
    return nextOther;
  }
}
record BaseLayer(Coordinator coordinator, Map<String,Map<String,String>> map, long baseStamp, SourceOracle stLib) implements Layer{
  @Override public OtherPackages compile(SourceOracle _ignoreSrc, OutputOracle out){
    var pkgName= "base";
    var cacheDir= coordinator.baseCachePath();
    if (cacheDir.isPresent()){ return deployedBaseApi(cacheDir.get(), pkgName); }
    var other= OtherPackages.empty();
    long maxIn= stLib.allFiles().stream().mapToLong(Ref::lastModified).max().getAsLong();
    if (out.stillBuilt(pkgName, stLib.allFiles(), maxIn)){ return out.startCachedPkgApi(pkgName,map,Math.max(baseStamp,out.pkgApiStamp(pkgName))); }
    var rich= SourceOracleWithAutoload.ofBase(stLib);
    List<Literal> core= coordinator.frontend(pkgName,rich.sources(stLib.allFiles()),rich.oracle(),other,Map.of());
    coordinator.backend(pkgName,core,rich.oracle(),other,new CapabilityEnvironment(rich.autoloadedAssets()));
    long newStamp= out.commit(pkgName, core, Map.of(), stLib.allFiles(), maxIn);
    return OtherPackages.start(map, AllLs.of(core).values(), Math.max(baseStamp,newStamp));
  }
  private OtherPackages deployedBaseApi(Path cacheDir, String pkgName){
    var json= cacheDir.resolve(pkgName+".json");
    var api= OutputHelper.pkgApiFromJSon(json).orElseThrow(()->Violation.cacheMissingBaseApiFile(json));
    return OtherPackages.start(map, api, Math.max(baseStamp, Fs.lastModified(json)));
  }
}