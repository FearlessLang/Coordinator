package coordinator;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
  Coordinator coordinator();
}
record MiddleLayer(Coordinator coordinator, Layer next, LinkedHashMap<String,List<Ref>> pkgs) implements Layer{
  MiddleLayer{ assert !pkgs.isEmpty(); }
  @Override public OtherPackages compile(SourceOracle src, OutputOracle out){
    OtherPackages other= next.compile(src, out);
    var res= new Object(){
      OtherPackages nextOther= other;
      private void compilePkg(String pkg, List<Ref> files){
        long maxSrc= files.stream().mapToLong(Ref::lastModified).max().getAsLong();
        long maxIn= Math.max(maxSrc, other.stamp());//out.mapStamp() must be <= then other.watermark() since it comes from next
        if (out.stillBuilt(pkg, files, maxIn)){ nextOther = out.addCachedPkgApi(nextOther, pkg); return; }
        var rich= SourceOracleWithAutoload.of(src, "_"+pkg);
        List<Literal> core= coordinator.frontend(pkg, rich.sources(files), rich.oracle(), other,other.virtualizationMap().getOrDefault(pkg,Map.of()));
        coordinator.backend(pkg, core, rich.oracle(), other, new CapabilityEnvironment(rich.autoloadedAssets()));
        long newStamp= out.commitPkgApi(pkg, core, maxIn); // newStamp will be the old api file mtime if there was no reason to commit.
        out.commitMains(pkg, core);
        out.commitBuilt(pkg, files, maxIn);
        var map= AllLs.of(core).values().stream().collect(Collectors.toUnmodifiableMap (Literal::name, d->d));
        nextOther = nextOther.mergeWith(map,Math.max(nextOther.stamp(),newStamp));
      }};
    pkgs.forEach(res::compilePkg);
    return res.nextOther;
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
    long newStamp= out.commitPkgApi(pkgName, core, maxIn);
    out.commitBuilt(pkgName, stLib.allFiles(), maxIn);
    return OtherPackages.start(map, AllLs.of(core).values(), Math.max(baseStamp,newStamp));
  }
  private OtherPackages deployedBaseApi(Path cacheDir, String pkgName){
    var json= cacheDir.resolve(pkgName+".json");
    var api= OutputHelper.pgkApiFromJSon(json).orElseThrow(()->Violation.cacheMissingBaseApiFile(json));
    return OtherPackages.start(map, api, Math.max(baseStamp, Fs.lastModified(json)));
  }
}