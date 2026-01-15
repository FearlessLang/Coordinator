package testBuildBase;


import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import coordinator.Coordinator;
import coordinator.Layer;
import coordinator.OutputOracle;
import core.OtherPackages;
import core.E.Literal;
import tools.JavaTool;
import tools.SourceOracle;
import utils.Bug;
import utils.ResolveResource;

class TestBuildBase {
  Coordinator c= new Coordinator(){
    @Override  public void main(Path path){
      OutputOracle out= ()->ResolveResource.stLibDebugOut;
      Layer l= new Layer(){ public OtherPackages compile(SourceOracle src, OutputOracle out){ throw Bug.unreachable(); }};
      var pkgName= "base";
      var other= OtherPackages.empty();
      SourceOracle o= Coordinator.sourceOracle(ResolveResource.stLibPath);
      List<Literal> core= l.frontend(pkgName,o.allFiles(),o,other,Map.of());
      l.backend(pkgName,core,o,other,out);
      var classes= out.rootDir().resolve("gen_java","_classes");
      var runOut= JavaTool.runMain(classes, pkgName+".Main");
      assertEquals("", runOut);
    }
  };
  @Test void test(){ c.main(ResolveResource.stLibPath); }
}