package coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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
    return new Literal(rc, new TName(name, 0, Pos.unknown), List.of(), List.of(), "this", List.of(), Src.synthetic, false);
  }
  private static Layer fixedBase(long stamp){
    return new Layer(){
      @Override public OtherPackages compile(SourceOracle src, OutputOracle out){ return OtherPackages.start(Map.of(), List.<Literal>of(), stamp); }
      @Override public SourceOracle stLib(){ return emptySrc; }
    };
  }
  private static final class ScriptedCoordinator implements Coordinator{
    final Path root;
    ScriptedCoordinator(Path root){ this.root= root; }
    final LinkedHashMap<String,Function<OtherPackages,List<Literal>>> scripts= new LinkedHashMap<>();
    final LinkedHashMap<String,Integer> frontendCalls= new LinkedHashMap<>();
    final LinkedHashMap<String,OtherPackages> lastOtherSeen= new LinkedHashMap<>();
    void fixedOutput(String pkg, List<Literal> core){ scripts.put(pkg, _ -> core); }
    @Override public List<Literal> frontend(String pkgName, List<Ref> files, SourceOracle oracle, OtherPackages other, Map<String,String> vres, CapabilityEnvironment capabilities){
      frontendCalls.merge(pkgName, 1, Integer::sum);
      lastOtherSeen.put(pkgName, other);
      return scripts.get(pkgName).apply(other);
    }
    @Override public void backend(String pkgName, List<Literal> core, SourceOracle oracle, OtherPackages other, CapabilityEnvironment capabilities){ Fs.writeUtf8(Coordinator.genJava(root).resolve(pkgName+".jar"), ""); }
    int calls(String pkg){ return frontendCalls.getOrDefault(pkg, 0); }
  }
  private static final long margin= 30_000;
  private static final long touchGap= 500;
  @Test void fullyCachedRebuildRecomputesNothingAndTouchesNoFiles(@TempDir Path tmp){
    var start= System.currentTimeMillis();
    OutputOracle out= () -> tmp;
    var stub= new ScriptedCoordinator(tmp);
    stub.fixedOutput("a", List.of(literal("A1", RC.imm)));
    stub.fixedOutput("b", List.of(literal("B1", RC.imm)));
    var pkgs= new LinkedHashMap<String,List<Ref>>();
    pkgs.put("a", refs("a", start-margin));
    pkgs.put("b", refs("b", start-margin));
    var layer= new MiddleLayer(stub, fixedBase(start-margin), pkgs);
    layer.compile(emptySrc, out);
    assertEquals(1, stub.calls("a"));
    assertEquals(1, stub.calls("b"));
    var stampA= out.pkgApiStamp("a");
    var stampB= out.pkgApiStamp("b");
    layer.compile(emptySrc, out);
    assertEquals(1, stub.calls("a"));
    assertEquals(1, stub.calls("b"));
    assertEquals(stampA, out.pkgApiStamp("a"));
    assertEquals(stampB, out.pkgApiStamp("b"));
  }
  @Test void sourceTouchedWithUnchangedApiRecomputesButDoesNotRewriteAndLeavesSiblingAlone(@TempDir Path tmp){
    var start= System.currentTimeMillis();
    OutputOracle out= () -> tmp;
    var stub= new ScriptedCoordinator(tmp);
    stub.fixedOutput("a", List.of(literal("A1", RC.imm), literal("_Hidden", RC.imm)));
    stub.fixedOutput("b", List.of(literal("B1", RC.imm)));
    var pkgs= new LinkedHashMap<String,List<Ref>>();
    pkgs.put("a", refs("a", start-margin));
    pkgs.put("b", refs("b", start-margin));
    var layer= new MiddleLayer(stub, fixedBase(start-margin), pkgs);
    layer.compile(emptySrc, out);
    var stampA= out.pkgApiStamp("a");
    var stampB= out.pkgApiStamp("b");
    var afterBuild1= System.currentTimeMillis();
    pkgs.put("a", refs("a", afterBuild1+touchGap));
    layer.compile(emptySrc, out);
    assertEquals(2, stub.calls("a"));
    assertEquals(1, stub.calls("b"));
    assertEquals(stampA, out.pkgApiStamp("a"));
    assertEquals(stampB, out.pkgApiStamp("b"));
  }
  @Test void apiChangeRewritesAndCascadesToDependentLayerWithUpdatedData(@TempDir Path tmp){
    var start= System.currentTimeMillis();
    OutputOracle out= () -> tmp;
    var stub= new ScriptedCoordinator(tmp);
    var aName= new TName("A1", 0, Pos.unknown);
    stub.fixedOutput("a", List.of(literal("A1", RC.imm)));
    stub.fixedOutput("b", List.of(literal("B1", RC.imm)));
    stub.scripts.put("c", other -> List.of(literal("C1", other.__of(aName).rc())));
    var pkgs1= new LinkedHashMap<String,List<Ref>>();
    pkgs1.put("a", refs("a", start-margin));
    pkgs1.put("b", refs("b", start-margin));
    var layer1= new MiddleLayer(stub, fixedBase(start-margin), pkgs1);
    var pkgs2= new LinkedHashMap<String,List<Ref>>();
    pkgs2.put("c", refs("c", start-margin));
    var layer2= new MiddleLayer(stub, layer1, pkgs2);
    layer2.compile(emptySrc, out);
    assertEquals(1, stub.calls("a"));
    assertEquals(1, stub.calls("c"));
    var stampA= out.pkgApiStamp("a");
    var stampC= out.pkgApiStamp("c");
    assertEquals(RC.imm, stub.lastOtherSeen.get("c").__of(aName).rc());
    var afterBuild1= System.currentTimeMillis();
    pkgs1.put("a", refs("a", afterBuild1+touchGap));
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
    var start= System.currentTimeMillis();
    OutputOracle out= () -> tmp;
    var stub= new ScriptedCoordinator(tmp);
    stub.fixedOutput("a", List.of(literal("A1", RC.imm)));
    stub.fixedOutput("c", List.of(literal("C1", RC.imm)));
    var pkgs1= new LinkedHashMap<String,List<Ref>>();
    pkgs1.put("a", refs("a", start-margin));
    var layer1= new MiddleLayer(stub, fixedBase(start-margin), pkgs1);
    var pkgs2= new LinkedHashMap<String,List<Ref>>();
    pkgs2.put("c", refs("c", start-margin));
    var layer2= new MiddleLayer(stub, layer1, pkgs2);
    layer2.compile(emptySrc, out);
    assertEquals(1, stub.calls("a"));
    assertEquals(1, stub.calls("c"));
    var afterBuild1= System.currentTimeMillis();
    pkgs1.put("a", refs("a", afterBuild1+touchGap));
    layer2.compile(emptySrc, out);
    assertEquals(2, stub.calls("a"));
    assertEquals(1, stub.calls("c"));
  }
  @Test void rebuildAfterAnApiPreservingEditMustNotRecompileAgain(@TempDir Path tmp){
    var start= System.currentTimeMillis();
    OutputOracle out= () -> tmp;
    var stub= new ScriptedCoordinator(tmp);
    stub.fixedOutput("a", List.of(literal("A1", RC.imm)));
    var pkgs= new LinkedHashMap<String,List<Ref>>();
    pkgs.put("a", refs("a", start-margin));
    var layer= new MiddleLayer(stub, fixedBase(start-margin), pkgs);
    layer.compile(emptySrc, out);
    assertEquals(1, stub.calls("a"));
    var afterBuild1= System.currentTimeMillis();
    pkgs.put("a", refs("a", afterBuild1+touchGap));
    layer.compile(emptySrc, out);
    assertEquals(2, stub.calls("a"));
    layer.compile(emptySrc, out);
    assertEquals(2, stub.calls("a"));
  }
  @Test void layerStampMustTrackTheFreshestSiblingNotJustTheLastOneMerged(@TempDir Path tmp){
    var start= System.currentTimeMillis();
    OutputOracle out= () -> tmp;
    var stub= new ScriptedCoordinator(tmp);
    stub.fixedOutput("a", List.of(literal("A1", RC.imm)));
    stub.fixedOutput("b", List.of(literal("B1", RC.imm)));
    var pkgs= new LinkedHashMap<String,List<Ref>>();
    pkgs.put("a", refs("a", start-margin));
    pkgs.put("b", refs("b", start-margin));
    var layer= new MiddleLayer(stub, fixedBase(start-margin), pkgs);
    layer.compile(emptySrc, out);
    var stampB= out.pkgApiStamp("b");
    var afterBuild1= System.currentTimeMillis();
    pkgs.put("a", refs("a", afterBuild1+touchGap));
    pkgs.put("b", refs("b", afterBuild1+touchGap/2));
    stub.fixedOutput("a", List.of(literal("A1", RC.mut)));
    var result= layer.compile(emptySrc, out);
    assertEquals(2, stub.calls("a"));
    assertEquals(2, stub.calls("b"));
    assertEquals(stampB, out.pkgApiStamp("b"));
    assertEquals(out.pkgApiStamp("a"), result.stamp());
    assertNotEquals(stampB, result.stamp());
  }
  private static List<Ref> twoRefs(long mtime){
    return List.of(
      new FakeRef(SourceOracle.root+"_a/a.fear", mtime),
      new FakeRef(SourceOracle.root+"_a/b.fear", mtime));
  }
  @Test void deletingASourceFileMustRecompileThePackage(@TempDir Path tmp){
    var start= System.currentTimeMillis();
    OutputOracle out= () -> tmp;
    var stub= new ScriptedCoordinator(tmp);
    stub.fixedOutput("a", List.of(literal("A1", RC.imm)));
    var pkgs= new LinkedHashMap<String,List<Ref>>();
    pkgs.put("a", twoRefs(start-margin));
    var layer= new MiddleLayer(stub, fixedBase(start-margin), pkgs);
    layer.compile(emptySrc, out);
    assertEquals(1, stub.calls("a"));
    layer.compile(emptySrc, out);
    assertEquals(1, stub.calls("a"));
    pkgs.put("a", refs("a", start-margin));
    layer.compile(emptySrc, out);
    assertEquals(2, stub.calls("a"));
  }
  @Test void addingASourceFileWithAnOldMtimeMustRecompileThePackage(@TempDir Path tmp){
    var start= System.currentTimeMillis();
    OutputOracle out= () -> tmp;
    var stub= new ScriptedCoordinator(tmp);
    stub.fixedOutput("a", List.of(literal("A1", RC.imm)));
    var pkgs= new LinkedHashMap<String,List<Ref>>();
    pkgs.put("a", refs("a", start-margin));
    var layer= new MiddleLayer(stub, fixedBase(start-margin), pkgs);
    layer.compile(emptySrc, out);
    assertEquals(1, stub.calls("a"));
    pkgs.put("a", twoRefs(start-margin));
    layer.compile(emptySrc, out);
    assertEquals(2, stub.calls("a"));
  }
  @Test void deletingALowerPackageMustRecompileThePackageBuiltAgainstIt(@TempDir Path tmp){
    var start= System.currentTimeMillis();
    OutputOracle out= () -> tmp;
    var stub= new ScriptedCoordinator(tmp);
    stub.fixedOutput("a", List.of(literal("A1", RC.imm)));
    stub.fixedOutput("b", List.of(literal("B1", RC.imm)));
    var pkgsA= new LinkedHashMap<String,List<Ref>>();
    pkgsA.put("a", refs("a", start-margin));
    var pkgsB= new LinkedHashMap<String,List<Ref>>();
    pkgsB.put("b", refs("b", start-margin));
    var base= fixedBase(start-margin);
    new MiddleLayer(stub, new MiddleLayer(stub, base, pkgsA), pkgsB).compile(emptySrc, out);
    assertEquals(1, stub.calls("a"));
    assertEquals(1, stub.calls("b"));
    new MiddleLayer(stub, base, pkgsB).compile(emptySrc, out);
    assertEquals(2, stub.calls("b"));
  }
  @Test void aLowerPackageMovingIntoTheSameRankMustRecompileThePackageBuiltAgainstIt(@TempDir Path tmp){
    var start= System.currentTimeMillis();
    OutputOracle out= () -> tmp;
    var stub= new ScriptedCoordinator(tmp);
    stub.fixedOutput("a", List.of(literal("A1", RC.imm)));
    stub.fixedOutput("b", List.of(literal("B1", RC.imm)));
    var pkgsA= new LinkedHashMap<String,List<Ref>>();
    pkgsA.put("a", refs("a", start-margin));
    var pkgsB= new LinkedHashMap<String,List<Ref>>();
    pkgsB.put("b", refs("b", start-margin));
    var base= fixedBase(start-margin);
    new MiddleLayer(stub, new MiddleLayer(stub, base, pkgsA), pkgsB).compile(emptySrc, out);
    var both= new LinkedHashMap<String,List<Ref>>();
    both.put("a", refs("a", start-margin));
    both.put("b", refs("b", start-margin));
    new MiddleLayer(stub, base, both).compile(emptySrc, out);
    assertEquals(1, stub.calls("a"));
    assertEquals(2, stub.calls("b"));
  }
  private static final class EditableRef implements Ref{
    final String fearPath;
    volatile long lastModified;
    EditableRef(String fearPath, long lastModified){ this.fearPath= fearPath; this.lastModified= lastModified; }
    @Override public String fearPath(){ return fearPath; }
    @Override public long lastModified(){ return lastModified; }
    @Override public byte[] loadBytes(){ return new byte[0]; }
    @Override public String toString(){ return fearPath; }
  }
  @Test void aSourceSavedAfterThePackageWasReadMustBeCompiledAgainByTheNextBuild(@TempDir Path tmp){
    var start= System.currentTimeMillis();
    OutputOracle out= () -> tmp;
    var stub= new ScriptedCoordinator(tmp);
    var edited= new EditableRef(SourceOracle.root+"_a/a.fear", start-margin);
    stub.scripts.put("a", _ -> {
      edited.lastModified = start-margin/2;
      return List.of(literal("A1", RC.imm));
    });
    var pkgs= new LinkedHashMap<String,List<Ref>>();
    pkgs.put("a", List.of(edited));
    var layer= new MiddleLayer(stub, fixedBase(start-margin), pkgs);
    layer.compile(emptySrc, out);
    assertEquals(1, stub.calls("a"));
    layer.compile(emptySrc, out);
    assertEquals(2, stub.calls("a"));
    layer.compile(emptySrc, out);
    assertEquals(2, stub.calls("a"));
  }
  private static final TName aName= new TName("A1", 0, Pos.unknown);
  private static final TName bName= new TName("B1", 0, Pos.unknown);
  private record Stack(ScriptedCoordinator stub, LinkedHashMap<String,List<Ref>> a, Layer top){}
  private static Stack stack(Path tmp, long start){
    var stub= new ScriptedCoordinator(tmp);
    var pkgsA= new LinkedHashMap<String,List<Ref>>();
    pkgsA.put("a", refs("a", start-margin));
    var pkgsB= new LinkedHashMap<String,List<Ref>>();
    pkgsB.put("b", refs("b", start-margin));
    var pkgsC= new LinkedHashMap<String,List<Ref>>();
    pkgsC.put("c", refs("c", start-margin));
    var l1= new MiddleLayer(stub, fixedBase(start-margin), pkgsA);
    var l2= new MiddleLayer(stub, l1, pkgsB);
    return new Stack(stub, pkgsA, new MiddleLayer(stub, l2, pkgsC));
  }
  @Test void apiChangeInTheBottomLayerCascadesThroughEveryLayerAbove(@TempDir Path tmp){
    var start= System.currentTimeMillis();
    OutputOracle out= () -> tmp;
    var s= stack(tmp, start);
    s.stub().fixedOutput("a", List.of(literal("A1", RC.imm)));
    s.stub().scripts.put("b", other -> List.of(literal("B1", other.__of(aName).rc())));
    s.stub().scripts.put("c", other -> List.of(literal("C1", other.__of(bName).rc())));
    s.top().compile(emptySrc, out);
    assertEquals(1, s.stub().calls("a"));
    assertEquals(1, s.stub().calls("b"));
    assertEquals(1, s.stub().calls("c"));
    var afterBuild1= System.currentTimeMillis();
    s.a().put("a", refs("a", afterBuild1+touchGap));
    s.stub().fixedOutput("a", List.of(literal("A1", RC.mut)));
    s.top().compile(emptySrc, out);
    assertEquals(2, s.stub().calls("a"));
    assertEquals(2, s.stub().calls("b"));
    assertEquals(2, s.stub().calls("c"));
    assertEquals(RC.mut, s.stub().lastOtherSeen.get("c").__of(bName).rc());
  }
  @Test void apiPreservingEditInTheBottomLayerMustNotReachAnyLayerAbove(@TempDir Path tmp){
    var start= System.currentTimeMillis();
    OutputOracle out= () -> tmp;
    var s= stack(tmp, start);
    s.stub().fixedOutput("a", List.of(literal("A1", RC.imm)));
    s.stub().fixedOutput("b", List.of(literal("B1", RC.imm)));
    s.stub().fixedOutput("c", List.of(literal("C1", RC.imm)));
    s.top().compile(emptySrc, out);
    var stampC= out.pkgApiStamp("c");
    var afterBuild1= System.currentTimeMillis();
    s.a().put("a", refs("a", afterBuild1+touchGap));
    s.top().compile(emptySrc, out);
    assertEquals(2, s.stub().calls("a"));
    assertEquals(1, s.stub().calls("b"));
    assertEquals(1, s.stub().calls("c"));
    assertEquals(stampC, out.pkgApiStamp("c"));
  }
  @Test void aBottomLayerApiChangeReachesTheTopEvenWhenTheMiddleApiIsUnchanged(@TempDir Path tmp){
    var start= System.currentTimeMillis();
    OutputOracle out= () -> tmp;
    var s= stack(tmp, start);
    s.stub().fixedOutput("a", List.of(literal("A1", RC.imm)));
    s.stub().fixedOutput("b", List.of(literal("B1", RC.imm)));
    s.stub().fixedOutput("c", List.of(literal("C1", RC.imm)));
    s.top().compile(emptySrc, out);
    var stampB= out.pkgApiStamp("b");
    var afterBuild1= System.currentTimeMillis();
    s.a().put("a", refs("a", afterBuild1+touchGap));
    s.stub().fixedOutput("a", List.of(literal("A1", RC.mut)));
    s.top().compile(emptySrc, out);
    assertEquals(2, s.stub().calls("a"));
    assertEquals(2, s.stub().calls("b"));
    assertEquals(2, s.stub().calls("c"));
    assertEquals(stampB, out.pkgApiStamp("b"));
    assertEquals(RC.mut, s.stub().lastOtherSeen.get("c").__of(aName).rc());
  }
  @Test void aChangeInTheMiddleLayerMustNotReachTheLayerBelowIt(@TempDir Path tmp){
    var start= System.currentTimeMillis();
    OutputOracle out= () -> tmp;
    var stub= new ScriptedCoordinator(tmp);
    stub.fixedOutput("a", List.of(literal("A1", RC.imm)));
    stub.fixedOutput("b", List.of(literal("B1", RC.imm)));
    stub.scripts.put("c", other -> List.of(literal("C1", other.__of(bName).rc())));
    var pkgsA= new LinkedHashMap<String,List<Ref>>();
    pkgsA.put("a", refs("a", start-margin));
    var pkgsB= new LinkedHashMap<String,List<Ref>>();
    pkgsB.put("b", refs("b", start-margin));
    var pkgsC= new LinkedHashMap<String,List<Ref>>();
    pkgsC.put("c", refs("c", start-margin));
    var l1= new MiddleLayer(stub, fixedBase(start-margin), pkgsA);
    var l2= new MiddleLayer(stub, l1, pkgsB);
    var top= new MiddleLayer(stub, l2, pkgsC);
    top.compile(emptySrc, out);
    var stampA= out.pkgApiStamp("a");
    var afterBuild1= System.currentTimeMillis();
    pkgsB.put("b", refs("b", afterBuild1+touchGap));
    stub.fixedOutput("b", List.of(literal("B1", RC.mut)));
    top.compile(emptySrc, out);
    assertEquals(1, stub.calls("a"));
    assertEquals(2, stub.calls("b"));
    assertEquals(2, stub.calls("c"));
    assertEquals(stampA, out.pkgApiStamp("a"));
    assertEquals(RC.mut, stub.lastOtherSeen.get("c").__of(bName).rc());
  }
  @Test void aFullyCachedRebuildOfAThreeLayerStackRecomputesNothing(@TempDir Path tmp){
    var start= System.currentTimeMillis();
    OutputOracle out= () -> tmp;
    var s= stack(tmp, start);
    s.stub().fixedOutput("a", List.of(literal("A1", RC.imm)));
    s.stub().fixedOutput("b", List.of(literal("B1", RC.imm)));
    s.stub().fixedOutput("c", List.of(literal("C1", RC.imm)));
    s.top().compile(emptySrc, out);
    var afterBuild1= System.currentTimeMillis();
    s.a().put("a", refs("a", afterBuild1+touchGap));
    s.top().compile(emptySrc, out);
    assertEquals(2, s.stub().calls("a"));
    s.top().compile(emptySrc, out);
    assertEquals(2, s.stub().calls("a"));
    assertEquals(1, s.stub().calls("b"));
    assertEquals(1, s.stub().calls("c"));
  }
  private static final class RecordingCoordinator implements Coordinator{
    private final Path mods;
    private final Path project;
    final LinkedHashMap<String,Integer> frontendCalls= new LinkedHashMap<>();
    RecordingCoordinator(Path project, Path mods){ this.project= project; this.mods= mods; }
    @Override public Path modsPath(){ return mods; }
    @Override public String runAllMains(String pkgName, OutputOracle out){ return ""; }
    @Override public List<Literal> frontend(String pkgName, List<Ref> files, SourceOracle oracle, OtherPackages other, Map<String,String> vres, CapabilityEnvironment capabilities){
      frontendCalls.merge(pkgName, 1, Integer::sum);
      return List.of();
    }
    @Override public void backend(String pkgName, List<Literal> core, SourceOracle oracle, OtherPackages other, CapabilityEnvironment capabilities){ Fs.writeUtf8(Coordinator.genJava(project.resolve(Coordinator.outDir)).resolve(pkgName+".jar"), ""); }
    int calls(String pkg){ return frontendCalls.getOrDefault(pkg, 0); }
  }
  private static void touch(Path p, long millis){ Fs.ofV(() -> Fs.setLastModifiedTime(p, FileTime.fromMillis(millis))); }
  @Test void touchingOnePackageMustNotRecompileAnIndependentPackage(@TempDir Path tmp) throws InterruptedException{
    var project= tmp.resolve("project");
    var stdLib= tmp.resolve("stdLib");
    var mods= tmp.resolve("mods");
    var aRank= project.resolve("_a/_rank_core.fear");
    Fs.writeUtf8(aRank, "");
    Fs.writeUtf8(project.resolve("_b/_rank_core.fear"), "");
    Fs.writeUtf8(stdLib.resolve("x.fear"), "");
    Fs.writeUtf8(mods.resolve("dummy.jar"), "");
    var c= new RecordingCoordinator(project, mods);
    c.main(project, c.sourceOracle(stdLib));
    assertEquals(1, c.calls("a"));
    assertEquals(1, c.calls("b"));
    c.main(project, c.sourceOracle(stdLib));
    assertEquals(1, c.calls("a"));
    assertEquals(1, c.calls("b"));
    touch(aRank, System.currentTimeMillis()+touchGap);
    c.main(project, c.sourceOracle(stdLib));
    assertEquals(2, c.calls("a"));
    assertEquals(1, c.calls("b"));
  }
  @Test void deletingAFileFromOnePackageMustRecompileOnlyThatPackage(@TempDir Path tmp) throws InterruptedException{
    var project= tmp.resolve("project");
    var stdLib= tmp.resolve("stdLib");
    var mods= tmp.resolve("mods");
    var extra= project.resolve("_a/extra.fear");
    Fs.writeUtf8(project.resolve("_a/_rank_core.fear"), "");
    Fs.writeUtf8(extra, "");
    Fs.writeUtf8(project.resolve("_b/_rank_core.fear"), "");
    Fs.writeUtf8(stdLib.resolve("x.fear"), "");
    Fs.writeUtf8(mods.resolve("dummy.jar"), "");
    var c= new RecordingCoordinator(project, mods);
    c.main(project, c.sourceOracle(stdLib));
    assertEquals(1, c.calls("a"));
    assertEquals(1, c.calls("b"));
    Fs.ofV(() -> Fs.delete(extra));
    c.main(project, c.sourceOracle(stdLib));
    assertEquals(2, c.calls("a"));
    assertEquals(1, c.calls("b"));
  }
}
