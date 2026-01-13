package coordinator;

import java.net.URI;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import core.E.Literal;
import core.FearlessException;
import core.OtherPackages;
import main.FrontendLogicMain;
import naiveBackend.Backend;
import tools.Fs;
import tools.JavacTool;
import tools.SourceOracle;
import utils.Bug;
import utils.IoErr;
import utils.ResolveResource;

public interface Layer{
  default OtherPackages compile(SourceOracle src, OutputOracle out){ throw Bug.unreachable(); }
  default List<Literal> frontend(String pkgName, List<URI> files, SourceOracle oracle, OtherPackages other,Map<String,String> vres){
    try{ return new FrontendLogicMain().of(pkgName,vres, files, oracle, other); }
    catch(FearlessException fe){ System.err.println(fe.render(oracle)); throw fe; }
  }
  default void backend(String pkgName, List<Literal> core, SourceOracle oracle, OtherPackages other, OutputOracle out){
    var outPath= out.rootDir().resolve("gen_java",pkgName);
    new Backend(outPath, pkgName, core).produceJavaCode();
    assert Files.exists(ResolveResource.stLibRTPath): "Missing extra folder: "+ResolveResource.stLibRTPath;
    if (pkgName.equals("base")){ Fs.copyTree(ResolveResource.stLibRTPath, outPath); }
    var classes= out.rootDir().resolve("gen_java","_classes");
    Fs.ensureDir(classes);
    Fs.cleanDirContents(classes);
    var javacOut= IoErr.of(()->JavacTool.compileTree(outPath, classes));
    assert javacOut.isEmpty();
    //if you add stuff to address this comment, it should be just a single call to a separate method
    //here we need to use out to produce the following: pkgName.jar, pkgName.html, pkgName.json
    //pkgName.html will contain docs for the types and methods in pkgName.
    //pkgName.json is the signatures of all the types saved in a json format, so that we can read them for separate compilation.      
  }
}
record MiddleLayer(Layer next, LinkedHashMap<String,List<URI>> pkgs) implements Layer{   
  @Override public OtherPackages compile(SourceOracle src, OutputOracle out){
    OtherPackages other= next.compile(src, out);
    var res= new Object(){
      OtherPackages nextOther= next.compile(src, out);
      private void compilePkg(String pkg, List<URI> files){
        long maxSrc= files.stream().mapToLong(src::lastModified).max().getAsLong();
        long maxIn= Math.max(maxSrc, other.stamp());//out.mapStamp() must be <= then other.watermark() since it comes from next
        var stillCached= out.pkgApiStamp(pkg) >= maxIn;
        if (stillCached){ nextOther = out.addCachedPkgApi(nextOther, pkg); return; }
        List<Literal> core= frontend(pkg, files, src, other,other.map().getOrDefault(pkg,Map.of()));
        backend(pkg, core, src, other, out);      
        long newStamp= out.commitPkgApi(pkg, core, maxIn); // newStamp will be maxIn if there was no reason to commit. 
        nextOther = nextOther.mergeWith(core,newStamp);
      }};
    pkgs.forEach(res::compilePkg);
    return res.nextOther;
  }  
}
record BaseLayer(Map<String,Map<String,String>> map, long baseStamp) implements Layer{
  @Override public OtherPackages compile(SourceOracle _ignoreSrc, OutputOracle out){
    var pkgName= "base";
    var other= OtherPackages.empty();
    SourceOracle o= Coordinator.sourceOracle(ResolveResource.stLibPath);
    long maxIn= o.allFiles().stream().mapToLong(o::lastModified).max().getAsLong();
    var stillCached= out.pkgApiStamp(pkgName)>= maxIn;
    if (stillCached){ return out.addCachedPkgApi(other, pkgName); }
    List<Literal> core= frontend(pkgName,o.allFiles(),o,other,Map.of());
    backend(pkgName,core,o,other,out);
    long newStamp= out.commitPkgApi(pkgName, core, baseStamp);
    return other.mergeWith(core,newStamp);
  }
}