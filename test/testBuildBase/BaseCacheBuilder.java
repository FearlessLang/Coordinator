package testBuildBase;

import java.nio.file.Path;
import java.util.List;

import coordinator.CapabilityEnvironment;
import coordinator.Coordinator;
import core.E.Literal;
import core.OtherPackages;
import naiveBackend.BackendTools;
import resources.ResolveResource;
import tools.Fs;
import tools.SourceOracle;
import utils.OneOr;

public final class BaseCacheBuilder{
  public static void main(String[] args){ deployInto(Path.of(args[0])); }
  public static void deployInto(Path appRoot){
    var modsDir= singleDirNamed(appRoot, "mods");
    var stdLib= modsDir.getParent().resolve("stdLib");
    buildInto(modsDir, stdLib, false);
  }
  private static Path singleDirNamed(Path root, String name){
    var found= Fs.walk(root, s -> s.filter(Fs::isDirectory).filter(p -> p.getFileName().toString().equals(name)).toList());
    return OneOr.of("Expected exactly one '"+name+"' dir under "+root, found.stream());
  }
  public static void buildInto(Path modsDir, Path stdLibDir, boolean generatesTests){
    var pkgName= "base";
    var scratch= stdLibDir.resolve("_baseScratch");
    Fs.ensureDir(scratch);
    try{
      var c= new Coordinator(){
        @Override public Path modsPath(){ return modsDir; }
        @Override public BackendTools backendTools(String pkgName, SourceOracle oracle, OtherPackages other, List<Literal> core, CapabilityEnvironment capabilities){
          return BackendTools.of(pkgName, oracle, other, core, scratch, baseCachePath(), generatesTests, ResolveResource.stLibRTPath, capabilities);
        }
      };
      c.buildBaseCache(ResolveResource.stLibPath, scratch, stdLibDir.resolve("baseCache"));
    }
    finally{ Fs.rmTree(scratch); }
  }
}
