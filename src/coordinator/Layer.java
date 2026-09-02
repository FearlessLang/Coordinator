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
import utils.Push;

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
        var stillCached= out.stillBuilt(pkg, files, maxIn);
        if (stillCached){ nextOther = out.addCachedPkgApi(nextOther, pkg); return; }       
        var rich= SourceOracleWithAutoload.of(src, "_"+pkg);
        var srcs= Push.of(files.stream().filter(f->f.fearPath().endsWith(".fear")).toList(),rich.newRefs());
        List<Literal> core= coordinator.frontend(pkg, srcs, rich.oracle(), other,other.virtualizationMap().getOrDefault(pkg,Map.of()));
        coordinator.backend(pkg, core, rich.oracle(), other, out);
        long newStamp= out.commitPkgApi(pkg, core, maxIn); // newStamp will be the old api file mtime if there was no reason to commit.
        out.commitBuilt(pkg, files, maxIn);
        var map= AllLs.of(core).values().stream().collect(Collectors.toUnmodifiableMap (Literal::name, d->d));
        nextOther = nextOther.mergeWith(map,Math.max(nextOther.stamp(),newStamp));
      }};
    pkgs.forEach(res::compilePkg);
    return res.nextOther;
  }  
}
record BaseLayer(Coordinator coordinator, Map<String,Map<String,String>> map, long baseStamp) implements Layer{
  @Override public OtherPackages compile(SourceOracle _ignoreSrc, OutputOracle out){
    var pkgName= "base";
    var cacheDir= coordinator.baseCachePath();
    if (cacheDir.isPresent()){ return deployedBaseApi(cacheDir.get(), pkgName); }
    var other= OtherPackages.empty();
    SourceOracle o= coordinator.sourceOracle(coordinator.stLibPath());
    long maxIn= o.allFiles().stream().mapToLong(Ref::lastModified).max().getAsLong();
    var stillCached= out.stillBuilt(pkgName, o.allFiles(), maxIn);
    if (stillCached){ return out.startCachedPkgApi(pkgName,map,Math.max(baseStamp,out.pkgApiStamp(pkgName))); }
    List<Literal> core= coordinator.frontend(pkgName,o.allFiles(),o,other,Map.of());
    coordinator.backend(pkgName,core,o,other,out);
    long newStamp= out.commitPkgApi(pkgName, core, maxIn);
    out.commitBuilt(pkgName, o.allFiles(), maxIn);
    return OtherPackages.start(map, AllLs.of(core).values(), Math.max(baseStamp,newStamp));
  }
  private OtherPackages deployedBaseApi(Path cacheDir, String pkgName){
    var json= cacheDir.resolve(pkgName+".json");
    var api= new OutputHelper().pgkApiFromJSon(json);
    if (api.isEmpty()){ throw Violation.cacheMissingPkgApiFile(json); }
    return OtherPackages.start(map, api.get(), Math.max(baseStamp, Fs.lastModified(json)));
  }
}