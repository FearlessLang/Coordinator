package coordinator;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.List;

import core.E.Literal;
import core.OtherPackages;
import main.FrontendLogicMain;
import naiveBackend.Backend;
import realSourceOracle.RealSourceOracle;
import tools.Fs;
import tools.JavaTool;
import tools.JavacTool;
import tools.SourceOracle;
import utils.IoErr;
import utils.ResolveResource;

public interface Coordinator {
  SourceOracle oracle();
  OtherPackages other();
  OutputOracle out();
  default List<Literal> frontend(String pkgName, List<URI> files, SourceOracle oracle, OtherPackages other){
    return new FrontendLogicMain().of(List.of(), files, oracle, other);
    }
  default void backend(String pkgName, List<Literal> core, SourceOracle oracle, OtherPackages other, OutputOracle out){
    var outPath= out.pathOf(List.of("gen_java",pkgName));
    new Backend(outPath, pkgName, core).produceJavaCode();
    assert Files.exists(ResolveResource.stLibRTPath): "Missing extra folder: "+ResolveResource.stLibRTPath;
    if (pkgName.equals("base")){ Fs.copyTree(ResolveResource.stLibRTPath, outPath); }
    var classes= out.pathOf(List.of("gen_java","_classes"));
    Fs.ensureDir(classes);
    Fs.cleanDirContents(classes);
    var javacOut= IoErr.of(()->JavacTool.compileTree(outPath, classes));
    assert javacOut.isEmpty();
    }
  default void runAllMains(String pkgName,OutputOracle out){
    var classes= out.pathOf(List.of("gen_java","_classes"));
    var runOut= JavaTool.runMain(classes, pkgName+".Main");
    assertEquals("", runOut);
  }
  void main();
  default void compileBase(OutputOracle out){
    var pkgName= "base";
    var other= OtherPackages.empty();
    SourceOracle o; try{ o= new RealSourceOracle(ResolveResource.stLibPath); }
    catch(UncheckedIOException ioe){
      System.err.println(ioe.getCause().getMessage());
      throw ioe;
      }
    List<Literal> core= frontend(pkgName,o.allFiles(),o,other);
    backend(pkgName,core,o,other,out);
  }
}