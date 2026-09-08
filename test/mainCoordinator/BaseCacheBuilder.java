package mainCoordinator;

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
import docBuilder.DocBuilder;
import docBuilder.HtmlDocBuilder;
import tools.Fs;
import tools.SourceOracle;
import utils.OneOr;

public final class BaseCacheBuilder{
  public static void main(String[] a){ deployInto(Path.of(a[0])); }
  public static void deployInto(Path appRoot){
    var modsDir= singleDirNamed(appRoot, "mods");
    buildInto(modsDir, modsDir.getParent().resolve("stdLib"), Optional.empty());
  }
  private static Path singleDirNamed(Path root, String name){
    var found= Fs.walk(root, s->s.filter(Files::isDirectory).filter(p->p.getFileName().toString().equals(name)).toList());
    return OneOr.of("Expected exactly one '"+name+"' dir under "+root, found.stream());
  }
  public static void buildInto(Path modsDir, Path stdLibDir, Optional<Path> testFileDest){
    var pkgName= "base";
    var scratch= stdLibDir.resolve("_baseScratch");
    Fs.ensureDir(scratch);
    try{
      var c= new Coordinator(){
        @Override public Path rtPath(){ return ResolveResource.stLibRTPath; }
        @Override public Path stLibPath(){ return ResolveResource.stLibPath; }
        @Override public Path modsPath(){ return modsDir; }
        @Override public DocBuilder docBuilder(String pkgName, SourceOracle oracle, OtherPackages other, List<Literal> core, Path rootDir){
          var docs= new HtmlDocBuilder(oracle,other,core,baseCachePath().map(p->p.resolve("base.html")));
          var htmlPath= rootDir.resolve("gen_java",pkgName+".html");
          docs.packageLocation(pkgName,htmlPath,testFileDest.orElseGet(()->scratch.resolve("_discardedTest.fear")));
          return docs;
        }
      };
      OutputOracle out= ()->scratch;
      var other= OtherPackages.empty();
      SourceOracle o= c.sourceOracle(c.stLibPath());
      List<Literal> core= c.frontend(pkgName, o.allFiles(), o, other, Map.of());
      c.backend(pkgName, core, o, other, out, new CapabilityEnvironment(List.of()));
      out.commitPkgApi(pkgName, core, -1);
      var baseCache= stdLibDir.resolve("baseCache");
      Fs.copyFresh(scratch.resolve("base.json"), baseCache.resolve("base.json"));
      Fs.copyFresh(scratch.resolve("gen_java").resolve("base.jar"), baseCache.resolve("base.jar"));
      Fs.copyFresh(scratch.resolve("gen_java").resolve("base.html"), baseCache.resolve("base.html"));
      Fs.copyFresh(scratch.resolve("gen_java").resolve("base.txt"), baseCache.resolve("base.txt"));
    } finally{ Fs.rmTree(scratch); }
  }
}
