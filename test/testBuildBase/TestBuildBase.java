package testBuildBase;


import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import coordinator.Coordinator;
import coordinator.OutputOracle;
import core.OtherPackages;
import core.E.Literal;
import tools.JavaTool;
import tools.SourceOracle;
import utils.ResolveResource;

class TestBuildBase {
  Coordinator c= new Coordinator(){
    @Override public Path rtPath(){    return ResolveResource.stLibRTPath; }  
    @Override public Path stLibPath(){ return ResolveResource.stLibPath; }

    @Override  public void main(Path path){
      OutputOracle out= ()->ResolveResource.stLibDebugOut;
      var pkgName= "base";
      var other= OtherPackages.empty();
      SourceOracle o= sourceOracle(stLibPath());
      List<Literal> core= frontend(pkgName,o.allFiles(),o,other,Map.of());
      backend(pkgName,core,o,other,out);
      var classes= out.rootDir().resolve("gen_java","_classes");
      var runOut= JavaTool.runMain(classes, pkgName+".Main");
      assertEquals("", runOut);
    }
  };
  @Test void test(){ c.main(c.stLibPath()); }
}