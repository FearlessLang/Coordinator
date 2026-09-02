package mainCoordinator;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import coordinator.Coordinator;
import coordinator.OutputOracle;
import core.E.Literal;
import core.OtherPackages;
import tools.Fs;
import tools.SourceOracle;

public final class BaseCacheBuilder{
  public static void buildInto(Path modsDir, Path stdLibDir){
    var pkgName= "base";
    var scratch= stdLibDir.resolve("_baseScratch");
    Fs.ensureDir(scratch);
    try{
      var c= new Coordinator(){
        @Override public Path rtPath(){ return ResolveResource.stLibRTPath; }
        @Override public Path stLibPath(){ return ResolveResource.stLibPath; }
        @Override public Path modsPath(){ return modsDir; }
      };
      OutputOracle out= ()->scratch;
      var other= OtherPackages.empty();
      SourceOracle o= c.sourceOracle(c.stLibPath());
      List<Literal> core= c.frontend(pkgName, o.allFiles(), o, other, Map.of());
      Fs.copyTreeFlat(c.modsPath(), scratch.resolve("gen_java"));
      c.backend(pkgName, core, o, other, out);
      out.commitPkgApi(pkgName, core, -1);
      var baseCache= stdLibDir.resolve("baseCache");
      Fs.copyFresh(scratch.resolve("base.json"), baseCache.resolve("base.json"));
      Fs.copyFresh(scratch.resolve("gen_java").resolve("base.jar"), baseCache.resolve("base.jar"));
    } finally{ Fs.rmTree(scratch); }
  }
}
