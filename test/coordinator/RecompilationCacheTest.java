package coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
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
import tools.Fs;
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
  private static final long TOUCH= 500;

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

  @Test void apiPreservingEditMustNotCascadeToTheDependentLayer(@TempDir Path tmp){
    long start= System.currentTimeMillis();
    OutputOracle out= ()->tmp;
    var stub= new ScriptedCoordinator();
    stub.fixedOutput("a", List.of(literal("A1", RC.imm)));
    stub.fixedOutput("c", List.of(literal("C1", RC.imm)));
    var pkgs1= new LinkedHashMap<String,List<Ref>>();
    pkgs1.put("a", refs("a", start-MARGIN));
    var layer1= new MiddleLayer(stub, fixedBase(start-MARGIN), pkgs1);
    var pkgs2= new LinkedHashMap<String,List<Ref>>();
    pkgs2.put("c", refs("c", start-MARGIN));
    var layer2= new MiddleLayer(stub, layer1, pkgs2);

    layer2.compile(emptySrc, out);
    assertEquals(1, stub.calls("a"));
    assertEquals(1, stub.calls("c"));

    long afterBuild1= System.currentTimeMillis();
    pkgs1.put("a", refs("a", afterBuild1+TOUCH));
    layer2.compile(emptySrc, out);

    assertEquals(2, stub.calls("a"));
    assertEquals(1, stub.calls("c"));
  }

  @Test void rebuildAfterAnApiPreservingEditMustNotRecompileAgain(@TempDir Path tmp){
    long start= System.currentTimeMillis();
    OutputOracle out= ()->tmp;
    var stub= new ScriptedCoordinator();
    stub.fixedOutput("a", List.of(literal("A1", RC.imm)));
    var pkgs= new LinkedHashMap<String,List<Ref>>();
    pkgs.put("a", refs("a", start-MARGIN));
    var layer= new MiddleLayer(stub, fixedBase(start-MARGIN), pkgs);

    layer.compile(emptySrc, out);
    assertEquals(1, stub.calls("a"));

    long afterBuild1= System.currentTimeMillis();
    pkgs.put("a", refs("a", afterBuild1+TOUCH));
    layer.compile(emptySrc, out);
    assertEquals(2, stub.calls("a"));

    layer.compile(emptySrc, out);
    assertEquals(2, stub.calls("a"));
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
    long stampB= out.pkgApiStamp("b");

    long afterBuild1= System.currentTimeMillis();
    pkgs.put("a", refs("a", afterBuild1+TOUCH));
    pkgs.put("b", refs("b", afterBuild1+TOUCH/2));
    stub.fixedOutput("a", List.of(literal("A1", RC.mut)));
    var result= layer.compile(emptySrc, out);

    assertEquals(2, stub.calls("a"));
    assertEquals(2, stub.calls("b"));
    assertEquals(stampB, out.pkgApiStamp("b"));
    assertEquals(out.pkgApiStamp("a"), result.stamp());
    assertNotEquals(stampB, result.stamp());
  }

  private static final TName aName= new TName("A1", 0, Pos.unknown);
  private static final TName bName= new TName("B1", 0, Pos.unknown);

  private record Stack(ScriptedCoordinator stub, LinkedHashMap<String,List<Ref>> a, Layer top){}

  private static Stack stack(long start){
    var stub= new ScriptedCoordinator();
    var pkgsA= new LinkedHashMap<String,List<Ref>>();
    pkgsA.put("a", refs("a", start-MARGIN));
    var pkgsB= new LinkedHashMap<String,List<Ref>>();
    pkgsB.put("b", refs("b", start-MARGIN));
    var pkgsC= new LinkedHashMap<String,List<Ref>>();
    pkgsC.put("c", refs("c", start-MARGIN));
    var l1= new MiddleLayer(stub, fixedBase(start-MARGIN), pkgsA);
    var l2= new MiddleLayer(stub, l1, pkgsB);
    return new Stack(stub, pkgsA, new MiddleLayer(stub, l2, pkgsC));
  }

  @Test void apiChangeInTheBottomLayerCascadesThroughEveryLayerAbove(@TempDir Path tmp){
    long start= System.currentTimeMillis();
    OutputOracle out= ()->tmp;
    var s= stack(start);
    s.stub().fixedOutput("a", List.of(literal("A1", RC.imm)));
    s.stub().scripts.put("b", other-> List.of(literal("B1", other.__of(aName).rc())));
    s.stub().scripts.put("c", other-> List.of(literal("C1", other.__of(bName).rc())));

    s.top().compile(emptySrc, out);
    assertEquals(1, s.stub().calls("a"));
    assertEquals(1, s.stub().calls("b"));
    assertEquals(1, s.stub().calls("c"));

    long afterBuild1= System.currentTimeMillis();
    s.a().put("a", refs("a", afterBuild1+TOUCH));
    s.stub().fixedOutput("a", List.of(literal("A1", RC.mut)));
    s.top().compile(emptySrc, out);

    assertEquals(2, s.stub().calls("a"));
    assertEquals(2, s.stub().calls("b"));
    assertEquals(2, s.stub().calls("c"));
    assertEquals(RC.mut, s.stub().lastOtherSeen.get("c").__of(bName).rc());
  }

  @Test void apiPreservingEditInTheBottomLayerMustNotReachAnyLayerAbove(@TempDir Path tmp){
    long start= System.currentTimeMillis();
    OutputOracle out= ()->tmp;
    var s= stack(start);
    s.stub().fixedOutput("a", List.of(literal("A1", RC.imm)));
    s.stub().fixedOutput("b", List.of(literal("B1", RC.imm)));
    s.stub().fixedOutput("c", List.of(literal("C1", RC.imm)));

    s.top().compile(emptySrc, out);
    long stampC= out.pkgApiStamp("c");

    long afterBuild1= System.currentTimeMillis();
    s.a().put("a", refs("a", afterBuild1+TOUCH));
    s.top().compile(emptySrc, out);

    assertEquals(2, s.stub().calls("a"));
    assertEquals(1, s.stub().calls("b"));
    assertEquals(1, s.stub().calls("c"));
    assertEquals(stampC, out.pkgApiStamp("c"));
  }

  @Test void aBottomLayerApiChangeReachesTheTopEvenWhenTheMiddleApiIsUnchanged(@TempDir Path tmp){
    long start= System.currentTimeMillis();
    OutputOracle out= ()->tmp;
    var s= stack(start);
    s.stub().fixedOutput("a", List.of(literal("A1", RC.imm)));
    s.stub().fixedOutput("b", List.of(literal("B1", RC.imm)));
    s.stub().fixedOutput("c", List.of(literal("C1", RC.imm)));

    s.top().compile(emptySrc, out);
    long stampB= out.pkgApiStamp("b");

    long afterBuild1= System.currentTimeMillis();
    s.a().put("a", refs("a", afterBuild1+TOUCH));
    s.stub().fixedOutput("a", List.of(literal("A1", RC.mut)));
    s.top().compile(emptySrc, out);

    assertEquals(2, s.stub().calls("a"));
    assertEquals(2, s.stub().calls("b"));
    assertEquals(2, s.stub().calls("c"));
    assertEquals(stampB, out.pkgApiStamp("b"));
    assertEquals(RC.mut, s.stub().lastOtherSeen.get("c").__of(aName).rc());
  }

  @Test void aChangeInTheMiddleLayerMustNotReachTheLayerBelowIt(@TempDir Path tmp){
    long start= System.currentTimeMillis();
    OutputOracle out= ()->tmp;
    var stub= new ScriptedCoordinator();
    stub.fixedOutput("a", List.of(literal("A1", RC.imm)));
    stub.fixedOutput("b", List.of(literal("B1", RC.imm)));
    stub.scripts.put("c", other-> List.of(literal("C1", other.__of(bName).rc())));
    var pkgsA= new LinkedHashMap<String,List<Ref>>();
    pkgsA.put("a", refs("a", start-MARGIN));
    var pkgsB= new LinkedHashMap<String,List<Ref>>();
    pkgsB.put("b", refs("b", start-MARGIN));
    var pkgsC= new LinkedHashMap<String,List<Ref>>();
    pkgsC.put("c", refs("c", start-MARGIN));
    var l1= new MiddleLayer(stub, fixedBase(start-MARGIN), pkgsA);
    var l2= new MiddleLayer(stub, l1, pkgsB);
    var top= new MiddleLayer(stub, l2, pkgsC);

    top.compile(emptySrc, out);
    long stampA= out.pkgApiStamp("a");

    long afterBuild1= System.currentTimeMillis();
    pkgsB.put("b", refs("b", afterBuild1+TOUCH));
    stub.fixedOutput("b", List.of(literal("B1", RC.mut)));
    top.compile(emptySrc, out);

    assertEquals(1, stub.calls("a"));
    assertEquals(2, stub.calls("b"));
    assertEquals(2, stub.calls("c"));
    assertEquals(stampA, out.pkgApiStamp("a"));
    assertEquals(RC.mut, stub.lastOtherSeen.get("c").__of(bName).rc());
  }

  @Test void aFullyCachedRebuildOfAThreeLayerStackRecomputesNothing(@TempDir Path tmp){
    long start= System.currentTimeMillis();
    OutputOracle out= ()->tmp;
    var s= stack(start);
    s.stub().fixedOutput("a", List.of(literal("A1", RC.imm)));
    s.stub().fixedOutput("b", List.of(literal("B1", RC.imm)));
    s.stub().fixedOutput("c", List.of(literal("C1", RC.imm)));

    s.top().compile(emptySrc, out);
    long afterBuild1= System.currentTimeMillis();
    s.a().put("a", refs("a", afterBuild1+TOUCH));
    s.top().compile(emptySrc, out);
    assertEquals(2, s.stub().calls("a"));

    s.top().compile(emptySrc, out);
    assertEquals(2, s.stub().calls("a"));
    assertEquals(1, s.stub().calls("b"));
    assertEquals(1, s.stub().calls("c"));
  }

  private static final class RecordingCoordinator implements Coordinator{
    private final Path stdLib;
    private final Path mods;
    final Map<String,Integer> frontendCalls= new LinkedHashMap<>();
    RecordingCoordinator(Path stdLib, Path mods){ this.stdLib= stdLib; this.mods= mods; }
    @Override public Path rtPath(){ return Path.of("unused"); }
    @Override public Path stLibPath(){ return stdLib; }
    @Override public Path modsPath(){ return mods; }
    @Override public void runAllMains(String pkgName, OutputOracle out){}
    @Override public List<Literal> frontend(String pkgName, List<Ref> files, SourceOracle oracle, OtherPackages other, Map<String,String> vres){
      frontendCalls.merge(pkgName, 1, Integer::sum);
      return List.of();
    }
    @Override public void backend(String pkgName, List<Literal> core, SourceOracle oracle, OtherPackages other, OutputOracle out){}
    int calls(String pkg){ return frontendCalls.getOrDefault(pkg, 0); }
  }
  private static void touch(Path p, long millis){ Fs.ofV(()->Files.setLastModifiedTime(p, FileTime.fromMillis(millis))); }

  @Test void touchingOnePackageMustNotRecompileAnIndependentPackage(@TempDir Path tmp) throws InterruptedException{
    var project= tmp.resolve("project");
    var stdLib= tmp.resolve("stdLib");
    var mods= tmp.resolve("mods");
    var aRank= project.resolve("_a/_rank_core.fear");
    Fs.writeUtf8(aRank, "");
    Fs.writeUtf8(project.resolve("_b/_rank_core.fear"), "");
    Fs.writeUtf8(stdLib.resolve("x.fear"), "");
    Fs.writeUtf8(mods.resolve("dummy.jar"), "");
    var c= new RecordingCoordinator(stdLib, mods);

    c.main(project);
    assertEquals(1, c.calls("a"));
    assertEquals(1, c.calls("b"));

    c.main(project);
    assertEquals(1, c.calls("a"));
    assertEquals(1, c.calls("b"));

    touch(aRank, System.currentTimeMillis()+TOUCH);
    c.main(project);
    assertEquals(2, c.calls("a"));
    assertEquals(1, c.calls("b"));
  }
}
