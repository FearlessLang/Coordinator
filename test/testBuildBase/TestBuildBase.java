package testBuildBase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.AssertionFailedError;

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
import utils.Push;

final class TestBuildBase{
  static{ utils.Err.setUp(AssertionFailedError.class, Assertions::assertEquals, Assertions::assertTrue); }
  static Coordinator coordinator(Path base, Path debugOut){ return new Coordinator(){
    @Override public Path modsPath(){  return ResolveResource.coordinatorJars; }
    @Override public BackendTools backendTools(String pkgName, SourceOracle oracle, OtherPackages other, List<Literal> core, CapabilityEnvironment capabilities){
      return BackendTools.of(pkgName, oracle, other, core, debugOut, baseCachePath(), true, ResolveResource.stLibRTPath, capabilities);
    }
    @Override public String main(Path path, SourceOracle stLib) throws InterruptedException{
      OutputOracle out= () -> debugOut;
      var pkgName= "base";
      var other= OtherPackages.empty();
      var rich= SourceOracleWithAutoload.ofBase(stLib);
      var capabilities= new CapabilityEnvironment(rich.autoloadedAssets());
      var core= frontend(pkgName, rich.sources(stLib.allFiles()), rich.oracle(), other, Map.of(), capabilities);
      backend(pkgName, core, rich.oracle(), other, capabilities);
      var jars= Push.of(Coordinator.genJava(out.rootDir()), sharedClasspath());
      return Coordinator.runMains(Coordinator.runData(out.rootDir().getParent(), base), jars, "_"+pkgName+".Main");
    }
  };}
  static String run(Path base, Path debugOut){
    var c= coordinator(base, debugOut);
    try{ return c.main(base, c.sourceOracle(base)); }
    catch (InterruptedException e){ return fail(e); }
  }
  @Test void test(){ assertEquals("", run(ResolveResource.stLibPath, ResolveResource.stLibDebugOut)); }
  @Test void aFailingExampleOfBaseInAFileOfTheErrorMachineryIsReportedAtItsLine(@TempDir Path tmp){
    var base= tmp.resolve("base");
    Fs.copyTree(ResolveResource.stLibPath, base);
    var action= base.resolve("action.fear");
    var text= Fs.readUtf8(action)+"///A probe.\n//> .check{1 .assertEq 2}\nMachineryProbe:{}\n";
    Fs.writeUtf8(action, text);
    var line= text.lines().toList().indexOf("//> .check{1 .assertEq 2}")+1;
    utils.Err.strCmp("""
Test failure _MachineryProbe_Examples at line: %d in file: action.fear
[###]""".formatted(line), run(base, tmp.resolve("dbgOut")));
  }
}
