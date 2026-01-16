package coordinator;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import core.E.Literal;
import core.OtherPackages;
import tools.SourceOracle;

public interface Layer{
  default LinkedHashMap<String,List<URI>> pkgs(){ return new LinkedHashMap<>();}
  OtherPackages compile(SourceOracle src, OutputOracle out);
  Coordinator coordinator();
}
record MiddleLayer(Coordinator coordinator, Layer next, LinkedHashMap<String,List<URI>> pkgs) implements Layer{
  MiddleLayer{ assert !pkgs.isEmpty(); }
  @Override public OtherPackages compile(SourceOracle src, OutputOracle out){
    OtherPackages other= next.compile(src, out);
    var res= new Object(){
      OtherPackages nextOther= next.compile(src, out);
      private void compilePkg(String pkg, List<URI> files){
        long maxSrc= files.stream().mapToLong(src::lastModified).max().getAsLong();
        long maxIn= Math.max(maxSrc, other.stamp());//out.mapStamp() must be <= then other.watermark() since it comes from next
        var stillCached= out.pkgApiStamp(pkg) >= maxIn;
        if (stillCached){ nextOther = out.addCachedPkgApi(nextOther, pkg); return; }
        List<Literal> core= coordinator.frontend(pkg, files, src, other,other.virtualizationMap().getOrDefault(pkg,Map.of()));
        coordinator.backend(pkg, core, src, other, out);      
        long newStamp= out.commitPkgApi(pkg, core, maxIn); // newStamp will be maxIn if there was no reason to commit. 
        var map= core.stream().collect(Collectors.toUnmodifiableMap (Literal::name, d->d));
        nextOther = nextOther.mergeWith(map,newStamp);
      }};
    pkgs.forEach(res::compilePkg);
    return res.nextOther;
  }  
}
record BaseLayer(Coordinator coordinator, Map<String,Map<String,String>> map, long baseStamp) implements Layer{
  @Override public OtherPackages compile(SourceOracle _ignoreSrc, OutputOracle out){
    var pkgName= "base";
    var other= OtherPackages.empty();
    SourceOracle o= coordinator.sourceOracle(coordinator.stLibPath());
    long maxIn= o.allFiles().stream().mapToLong(o::lastModified).max().getAsLong();
    var stillCached= out.pkgApiStamp(pkgName)>= maxIn;
    if (stillCached){ return out.startCachedPkgApi(pkgName,map,baseStamp); }
    List<Literal> core= coordinator.frontend(pkgName,o.allFiles(),o,other,Map.of());
    coordinator.backend(pkgName,core,o,other,out);
    long newStamp= out.commitPkgApi(pkgName, core, baseStamp);
    return OtherPackages.start(map, core, newStamp);
  }
}