package testBuildBase;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import coordinator.CapabilityEnvironment;
import coordinator.Coordinator;
import coordinator.OutputOracle;
import core.OtherPackages;
import core.E.Literal;
import mainCoordinator.ResolveResource;
import tools.JavaTool;
import tools.SourceOracle;
import utils.Push;

class TestBuildBase {
  Coordinator c= new Coordinator(){
    @Override public Path rtPath(){    return ResolveResource.stLibRTPath; }
    @Override public Path stLibPath(){ return ResolveResource.stLibPath; }
    @Override public Path modsPath(){  return ResolveResource.coordinatorJars; }

    @Override public String main(Path path) throws InterruptedException{
      OutputOracle out= ()->ResolveResource.stLibDebugOut;
      var pkgName= "base";
      var other= OtherPackages.empty();
      SourceOracle o= sourceOracle(stLibPath());
      List<Literal> core= frontend(pkgName,o.allFiles(),o,other,Map.of());
      backend(pkgName,core,o,other,out,new CapabilityEnvironment(List.of()));
      var jars= Push.of(out.rootDir().resolve("gen_java"),sharedClasspath());
      var runOut= JavaTool.runMainFromJars(List.of("-DfearlessUser.dir="+out.rootDir().getParent()),jars,pkgName+".Main");
      assertEquals("", runOut);
      return runOut;
    }
  };
  @Test void test(){
    try { c.main(c.stLibPath());}
    catch (InterruptedException e){ Assertions.fail(e);}
  }
}