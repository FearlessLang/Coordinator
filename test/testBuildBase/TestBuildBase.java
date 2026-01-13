package testBuildBase;


import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import coordinator.Coordinator;
import coordinator.Layer;
import coordinator.OutputOracle;
import core.OtherPackages;
import core.E.Literal;
import tools.SourceOracle;
import utils.ResolveResource;

class TestBuildBase {
  Coordinator c= new Coordinator(){
    @Override  public void main(Path path){
      OutputOracle out= ps->  ps.stream().map(Path::of).reduce(ResolveResource.stLibDebugOut, (acc,e)->acc.resolve(e)); 
      var pkgName= "base";
      var other= OtherPackages.empty();
      SourceOracle o= Coordinator.sourceOracle(ResolveResource.stLibPath);
      List<Literal> core= new Layer(){}.frontend(pkgName,o.allFiles(),o,other,Map.of());
      new Layer(){}.backend(pkgName,core,o,other,out);
      runAllMains("base",out);
    }
  };
  @Test void test(){ c.main(ResolveResource.stLibPath); }
}