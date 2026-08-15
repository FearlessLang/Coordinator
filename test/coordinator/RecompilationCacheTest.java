package coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import core.E.Literal;
import core.OtherPackages;
import core.RC;
import core.Src;
import core.TName;
import tools.SourceOracle;
import tools.SourceOracle.Ref;
import utils.Pos;

final class RecompilationCacheTest{
  private static final SourceOracle emptySrc= List::of;

  private record FakeRef(String fearPath, long lastModified) implements Ref{
    @Override public byte[] loadBytes(){ return new byte[0]; }
    @Override public String toString(){ return fearPath; }
  }
  private static List<Ref> refs(String pkg, long mtime){
    return List.of(new FakeRef(SourceOracle.root+"_"+pkg+"/a.fear", mtime));
  }
  private static Literal literal(String name, RC rc){
    return new Literal(rc, new TName(name, 0, Pos.unknown), List.of(), List.of(), "this", List.of(), Src.syntetic, false);
  }
  private static Layer fixedBase(long stamp){
    return new Layer(){
      @Override public OtherPackages compile(SourceOracle src, OutputOracle out){ return OtherPackages.start(Map.of(), List.<Literal>of(), stamp); }
      @Override public Coordinator coordinator(){ return null; }
    };
  }
  private static final class ScriptedCoordinator implements Coordinator{
    final Map<String,Function<OtherPackages,List<Literal>>> scripts= new LinkedHashMap<>();
    final Map<String,Integer> frontendCalls= new LinkedHashMap<>();
    final Map<String,OtherPackages> lastOtherSeen= new LinkedHashMap<>();
    void fixedOutput(String pkg, List<Literal> core){ scripts.put(pkg, _->core); }
    @Override public Path rtPath(){ return Path.of("unused"); }
    @Override public Path stLibPath(){ return Path.of("unused"); }
    @Override public List<Literal> frontend(String pkgName, List<Ref> files, SourceOracle oracle, OtherPackages other, Map<String,String> vres){
      frontendCalls.merge(pkgName, 1, Integer::sum);
      lastOtherSeen.put(pkgName, other);
      return scripts.get(pkgName).apply(other);
    }
    @Override public void backend(String pkgName, List<Literal> core, SourceOracle oracle, OtherPackages other, OutputOracle out){}
    int calls(String pkg){ return frontendCalls.getOrDefault(pkg, 0); }
  }

  private static final long MARGIN= 30_000;
  private static final long TOUCH= 5_000;

  @Test void fullyCachedRebuildRecomputesNothingAndTouchesNoFiles(@TempDir Path tmp){
    long start= System.currentTimeMillis();
    OutputOracle out= ()->tmp;
    var stub= new ScriptedCoordinator();
    stub.fixedOutput("a", List.of(literal("A1", RC.imm)));
    stub.fixedOutput("b", List.of(literal("B1", RC.imm)));
    var pkgs= new LinkedHashMap<String,List<Ref>>();
    pkgs.put("a", refs("a", start-MARGIN));
    pkgs.put("b", refs("b", start-MARGIN));
    var layer= new MiddleLayer(stub, fixedBase(start-MARGIN), pkgs);

    layer.compile(emptySrc, out);
    assertEquals(1, stub.calls("a"));
    assertEquals(1, stub.calls("b"));
    long stampA= out.pkgApiStamp("a");
    long stampB= out.pkgApiStamp("b");

    layer.compile(emptySrc, out);
    assertEquals(1, stub.calls("a"));
    assertEquals(1, stub.calls("b"));
    assertEquals(stampA, out.pkgApiStamp("a"));
    assertEquals(stampB, out.pkgApiStamp("b"));
  }

  @Test void sourceTouchedWithUnchangedApiRecomputesButDoesNotRewriteAndLeavesSiblingAlone(@TempDir Path tmp){
    long start= System.currentTimeMillis();
    OutputOracle out= ()->tmp;
    var stub= new ScriptedCoordinator();
    stub.fixedOutput("a", List.of(literal("A1", RC.imm), literal("_Hidden", RC.imm)));
    stub.fixedOutput("b", List.of(literal("B1", RC.imm)));
    var pkgs= new LinkedHashMap<String,List<Ref>>();
    pkgs.put("a", refs("a", start-MARGIN));
    pkgs.put("b", refs("b", start-MARGIN));
    var layer= new MiddleLayer(stub, fixedBase(start-MARGIN), pkgs);

    layer.compile(emptySrc, out);
    long stampA= out.pkgApiStamp("a");
    long stampB= out.pkgApiStamp("b");

    long afterBuild1= System.currentTimeMillis();
    pkgs.put("a", refs("a", afterBuild1+TOUCH));
    layer.compile(emptySrc, out);

    assertEquals(2, stub.calls("a"));
    assertEquals(1, stub.calls("b"));
    assertEquals(stampA, out.pkgApiStamp("a"));
    assertEquals(stampB, out.pkgApiStamp("b"));
  }

  @Test void apiChangeRewritesAndCascadesToDependentLayerWithUpdatedData(@TempDir Path tmp){
    long start= System.currentTimeMillis();
    OutputOracle out= ()->tmp;
    var stub= new ScriptedCoordinator();
    var aName= new TName("A1", 0, Pos.unknown);
    stub.fixedOutput("a", List.of(literal("A1", RC.imm)));
    stub.fixedOutput("b", List.of(literal("B1", RC.imm)));
    stub.scripts.put("c", other-> List.of(literal("C1", other.__of(aName).rc())));
    var pkgs1= new LinkedHashMap<String,List<Ref>>();
    pkgs1.put("a", refs("a", start-MARGIN));
    pkgs1.put("b", refs("b", start-MARGIN));
    var layer1= new MiddleLayer(stub, fixedBase(start-MARGIN), pkgs1);
    var pkgs2= new LinkedHashMap<String,List<Ref>>();
    pkgs2.put("c", refs("c", start-MARGIN));
    var layer2= new MiddleLayer(stub, layer1, pkgs2);

    layer2.compile(emptySrc, out);
    assertEquals(1, stub.calls("a"));
    assertEquals(1, stub.calls("c"));
    long stampA= out.pkgApiStamp("a");
    long stampC= out.pkgApiStamp("c");
    assertEquals(RC.imm, stub.lastOtherSeen.get("c").__of(aName).rc());

    long afterBuild1= System.currentTimeMillis();
    pkgs1.put("a", refs("a", afterBuild1+TOUCH));
    stub.fixedOutput("a", List.of(literal("A1", RC.mut)));
    layer2.compile(emptySrc, out);

    assertEquals(2, stub.calls("a"));
    assertEquals(1, stub.calls("b"));
    assertEquals(2, stub.calls("c"));
    assertNotEquals(stampA, out.pkgApiStamp("a"));
    assertNotEquals(stampC, out.pkgApiStamp("c"));
    assertEquals(RC.mut, stub.lastOtherSeen.get("c").__of(aName).rc());
  }

  @Test void layerStampMustTrackTheFreshestSiblingNotJustTheLastOneMerged(@TempDir Path tmp){
    long start= System.currentTimeMillis();
    OutputOracle out= ()->tmp;
    var stub= new ScriptedCoordinator();
    stub.fixedOutput("a", List.of(literal("A1", RC.imm)));
    stub.fixedOutput("b", List.of(literal("B1", RC.imm)));
    var pkgs= new LinkedHashMap<String,List<Ref>>();
    pkgs.put("a", refs("a", start-MARGIN));
    pkgs.put("b", refs("b", start-MARGIN));
    var layer= new MiddleLayer(stub, fixedBase(start-MARGIN), pkgs);
    layer.compile(emptySrc, out);

    long afterBuild1= System.currentTimeMillis();
    pkgs.put("a", refs("a", afterBuild1+TOUCH));
    pkgs.put("b", refs("b", afterBuild1+TOUCH/2));
    var result= layer.compile(emptySrc, out);

    assertEquals(2, stub.calls("a"));
    assertEquals(2, stub.calls("b"));
    long newStampA= afterBuild1+TOUCH;
    long newStampB= afterBuild1+TOUCH/2;
    assertEquals(Math.max(newStampA, newStampB), result.stamp());
  }
}
