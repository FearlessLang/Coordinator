package testBuildBase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import coordinator.CapabilityEnvironment;
import coordinator.Coordinator;
import coordinator.OutputOracle;
import core.E.Literal;
import core.OtherPackages;
import naiveBackend.BackendTools;
import realSourceOracle.SourceOracleWithAutoload;
import resources.ResolveResource;
import tools.Fs;
import tools.SourceOracle;
import utils.OneOr;

public final class BaseCacheBuilder{
  public static void main(String[] a){ deployInto(Path.of(a[0])); }
  public static void deployInto(Path appRoot){
    var modsDir= singleDirNamed(appRoot, "mods");
    var stdLib= modsDir.getParent().resolve("stdLib");
    Fs.rmTree(stdLib.resolve("base", "auto_tests"));
    buildInto(modsDir, stdLib, Optional.empty());
  }
  private static Path singleDirNamed(Path root, String name){
    var found= Fs.walk(root, s -> s.filter(Files::isDirectory).filter(p -> p.getFileName().toString().equals(name)).toList());
    return OneOr.of("Expected exactly one '"+name+"' dir under "+root, found.stream());
  }
  public static void buildInto(Path modsDir, Path stdLibDir, Optional<Path> testsRoot){
    var pkgName= "base";
    var scratch= stdLibDir.resolve("_baseScratch");
    Fs.ensureDir(scratch);
    try{
      var c= new Coordinator(){
        @Override public Path modsPath(){ return modsDir; }
        @Override public BackendTools backendTools(String pkgName, SourceOracle oracle, OtherPackages other, List<Literal> core, CapabilityEnvironment capabilities){
          return BackendTools.of(pkgName, oracle, other, core, scratch, baseCachePath(), testsRoot, ResolveResource.stLibRTPath, capabilities);
        }
      };
      OutputOracle out= () -> scratch;
      var other= OtherPackages.empty();
      SourceOracle o= c.sourceOracle(ResolveResource.stLibPath);
      var rich= SourceOracleWithAutoload.ofBase(o);
      var capabilities= new CapabilityEnvironment(rich.autoloadedAssets());
      List<Literal> core= c.frontend(pkgName, rich.sources(o.allFiles()), rich.oracle(), other, Map.of(), capabilities);
      c.backend(pkgName, core, rich.oracle(), other, capabilities);
      out.commitPkgApi(pkgName, core, -1);
      var baseCache= stdLibDir.resolve("baseCache");
      Fs.copyFresh(scratch.resolve("base.json"), baseCache.resolve("base.json"));
      Fs.copyFresh(scratch.resolve("gen_java").resolve("base.jar"), baseCache.resolve("base.jar"));
      Fs.copyFresh(scratch.resolve("gen_java").resolve("base.html"), baseCache.resolve("base.html"));
      Fs.copyFresh(scratch.resolve("gen_java").resolve("base.txt"), baseCache.resolve("base.txt"));
    } finally{ Fs.rmTree(scratch); }
  }
}
