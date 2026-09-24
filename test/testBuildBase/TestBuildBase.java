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
import resources.ResolveResource;
import naiveBackend.BackendTools;
import realSourceOracle.SourceOracleWithAutoload;
import tools.JavaTool;
import tools.SourceOracle;
import utils.Push;

class TestBuildBase {
  Coordinator c= new Coordinator(){
    @Override public Path modsPath(){  return ResolveResource.coordinatorJars; }
    @Override public BackendTools backendTools(String pkgName, SourceOracle oracle, OtherPackages other, List<Literal> core, CapabilityEnvironment capabilities){
      var testFileDest= ResolveResource.stLibDebugOut.resolve("_baseTestOut","base_test.fear");
      return BackendTools.of(pkgName, oracle, other, core, ResolveResource.stLibDebugOut, baseCachePath(), testFileDest, ResolveResource.stLibRTPath, capabilities);
    }

    @Override public String main(Path path, SourceOracle stLib) throws InterruptedException{
      OutputOracle out= ()->ResolveResource.stLibDebugOut;
      var pkgName= "base";
      var other= OtherPackages.empty();
      var rich= SourceOracleWithAutoload.ofBase(stLib);
      List<Literal> core= frontend(pkgName,rich.sources(stLib.allFiles()),rich.oracle(),other,Map.of());
      backend(pkgName,core,rich.oracle(),other,new CapabilityEnvironment(rich.autoloadedAssets()));
      var jars= Push.of(out.rootDir().resolve("gen_java"),sharedClasspath());
      var runOut= JavaTool.runMainFromJars(Coordinator.runData(out.rootDir().getParent(),ResolveResource.stLibPath),jars,"_"+pkgName+".Main");
      assertEquals("", runOut);
      return runOut;
    }
  };
  @Test void test(){
    try { c.main(ResolveResource.stLibPath, c.sourceOracle(ResolveResource.stLibPath));}
    catch (InterruptedException e){ Assertions.fail(e);}
  }
}