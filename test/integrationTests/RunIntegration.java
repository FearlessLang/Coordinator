package integrationTests;

import static java.nio.charset.StandardCharsets.UTF_16LE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntBinaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.AssertionFailedError;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import coordinator.CapabilityEnvironment;
import coordinator.Coordinator;
import coordinator.MainsInfo;
import coordinator.OutputOracle;
import core.E.Literal;
import core.OtherPackages;
import fileSupport.JUnitReport;
import mainCoordinator.ProgrammaticMain;
import naiveBackend.Backend;
import naiveBackend.BackendTools;
import realSourceOracle.RealSourceOracleWithZip;
import realSourceOracle.SourceOracleWithAutoload;
import resources.ResolveResource;
import testBuildBase.BaseCacheBuilder;
import testHelperFs.FsDsl;
import tools.Fs;
import tools.JavacTool;
import tools.PathClassLoader;
import tools.SourceOracle;
import tools.SourceOracle.Ref;
import userMessages.UserError;
import utils.Join;
import utils.OneOr;
import utils.Push;

final class RunIntegration{
  static{ utils.Err.setUp(AssertionFailedError.class, Assertions::assertEquals, Assertions::assertTrue); }
  static final Path baseCache= ResolveResource.stLibDebugOut.resolve("baseCache");
  static final Path reportsDir= ResolveResource.coordinatorSrc.getParent().resolve(".out", "junit_xml");
  static final Path reportsFile= reportsDir.resolve("all_auto_tests.xml");
  static final ArrayList<String> suites= new ArrayList<>();
  static final SourceOracle stLib= new RealSourceOracleWithZip(ResolveResource.stLibPath);
  @BeforeAll static void buildBaseOnce(){
    BaseCacheBuilder.buildInto(ResolveResource.coordinatorJars, ResolveResource.stLibDebugOut, Optional.of(ResolveResource.stLibPath));
    Fs.rmTree(reportsDir);
  }
  Coordinator coordinator(Path project){ return coordinator(project, List.of()); }
  Coordinator coordinator(Path project, List<String> jvmArgs){
    System.setProperty(JavacTool.appDirKey, ResolveResource.stLibPath.getParent()
      .resolve("fearlessArtefact", "fearless", "app").toString());
    return new Coordinator(){
      @Override public Path modsPath(){ return ResolveResource.coordinatorJars; }
      @Override public Optional<Path> baseCachePath(){ return Optional.of(baseCache); }
      @Override public Path stdLibBase(){ return ResolveResource.stLibPath; }
      @Override public BackendTools backendTools(String pkgName, SourceOracle oracle, OtherPackages other, List<Literal> core, CapabilityEnvironment capabilities){
        return BackendTools.of(pkgName, oracle, other, core, project.resolve(Coordinator.outDir), baseCachePath(), ResolveResource.stLibRTPath, capabilities);
      }
      @Override public String runAllMains(String pkgName, OutputOracle out) throws InterruptedException{
        return Coordinator.runMains(Push.of(Coordinator.runData(out.rootDir().getParent(), stdLibBase()), jvmArgs), Push.of(Coordinator.genJava(out.rootDir()), sharedClasspath()), "_"+pkgName+".Main");
      }
    };
  }
  static Path freshIntegrationRoot(String name){
    var root= ResolveResource.integrationTests.resolve(name);
    Fs.rmTree(root.resolve(Coordinator.outDir));
    return root;
  }
  String run(String name){ return main(freshIntegrationRoot(name), List.of()); }
  String main(Path project, List<String> jvmArgs){
    try{ return coordinator(project, jvmArgs).main(project, stLib); }
    catch (InterruptedException e){ return fail(e); }
  }
  void testOk(String name){ unitTestsOk(name, freshIntegrationRoot(name)); }
  void unitTestsOk(String name, Path root){
    var marker= "EndOfMain"+new BigInteger(128, new SecureRandom()).toString(36);
    var out= main(root, List.of("-Dfearless.endMarker="+marker));
    writeJUnitReport(name, root);
    var fails= out.lines().filter(l -> l.startsWith("Test failure ")).toList();
    assertTrue(fails.isEmpty(), () -> Join.of(fails, "Fearless unit tests failed in "+name+":\n", "\n", ""));
    var lines= out.lines().toList();
    var mains= coordinator(root).mains(root, stLib).orElseThrow().mains().size();
    var allCompleted= lines.stream().filter(marker::equals).count() == mains && lines.getLast().equals(marker);
    assertTrue(allCompleted, () -> "Not all the "+mains+" mains of "+name+" completed: a main that completes prints the line \""+marker+"\", and the output must end with that line. Output:\n"+out);
  }
  static void writeJUnitReport(String name){ writeJUnitReport(name, ResolveResource.integrationTests.resolve(name)); }
  static void writeJUnitReport(String name, Path root){
    var one= JUnitReport.suite(name, root);
    if (one.isEmpty()){ return; }
    suites.add(one);
    Fs.writeUtf8(reportsFile, JUnitReport.document(Join.of(suites, "", "", "", "")));
  }
  @Test void helloWorld(){
    utils.Err.strCmp("""
hello world 3
[Hi]
[1, 2, 3, 4]
[11, 12, 13, 14]
AAAAh
imm Bar.bar error line: 16 in file _hello/_rank_app.fear
imm Foo.foo error line: 15 in file _hello/_rank_app.fear
imm Hello6.main(_) error line: 14 in file _hello/_rank_app.fear
""", run("helloWorld"));
  }
  @Test void programmaticMainBuildsBaseWithoutWritingItsGeneratedTests(@TempDir Path tmp){
    var app= tmp.resolve("app");
    Fs.copyTree(ResolveResource.coordinatorJars, app.resolve(JavacTool.deployedModsDirName));
    System.setProperty(JavacTool.appDirKey, app.toString());
    System.setProperty(JavacTool.launcherKey, JavacTool.consoleKey);
    var dest= tmp.resolve("dest");
    var m= new ProgrammaticMain(new StringBuilder(), new StringBuilder(), Map.of("_hi/_rank_app111.fear", "Hi:base.Main{s->base.Debug#(\"hi\")}\n"), ResolveResource.stLibPath, ResolveResource.stLibRTPath, dest);
    try{ m.runFearless(); }
    finally{ System.clearProperty(JavacTool.launcherKey); }
    assertEquals("", m.err().toString());
    assertEquals("hi\n", m.out().toString());
    assertEquals(List.of(), Fs.walk(dest, s -> s.filter(f -> f.getFileName().toString().equals("auto_tests")).toList()));
  }
  //testUnitTests is the project that checks the failure report itself, so it must fail
  @Test void testUnitTests(){
    var out= run("testUnitTests");
    writeJUnitReport("testUnitTests");
    utils.Err.strCmp("""
Test failure MyTests at line: 5 in file: _hello/_rank_app.fear
Assertion failure.
Expected: 3
Actual: 1
imm Assert._fail(_) error line: [###]
imm MyTests# error line: 5 in file _hello/_rank_app.fear
""", out);
  }
  static Path unitTestsProject(Path tmp, String source){
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, "_p/_rank_app.fear\niii\n"+source);
    return root;
  }
  @Test void aFailureTextHoldingTheCdataEndIsEscapedInTheReport(@TempDir Path tmp){
    var root= unitTestsProject(tmp, """
use base.UnitTests as UnitTests;
MyTests: UnitTests {::
  .check{"]]>".assertEq "x"}
  }
""");
    main(root, List.of());
    utils.Err.strCmp("[###]<actual>]]&gt;</actual>\nAssertion failure.\nExpected: x\nActual: ]]&gt;\n[###]", JUnitReport.suite("p", root));
  }
  @Test void anEmptyActualStrIsAnEmptyActualElementInTheReport(@TempDir Path tmp){
    var root= unitTestsProject(tmp, """
use base.UnitTests as UnitTests;
MyTests: UnitTests {::
  .check{"".assertEq "x"}
  }
""");
    main(root, List.of());
    utils.Err.strCmp("[###]<expected>x</expected>\n<actual></actual>\n[###]", JUnitReport.suite("p", root));
  }
  @Test void checkNonDetErrAcceptsAFunctionReturningAMutObject(@TempDir Path tmp){
    var root= unitTestsProject(tmp, """
use base.UnitTests as UnitTests;
use base.Assert as Assert;
use base.EList as EList;
use base.Nat as Nat;
MyTests: UnitTests {::
  .checkNonDetErr({::}, {Assert.msg[mut EList[Nat]] "x"})
  }
""");
    utils.Err.strCmp("", main(root, List.of()));
  }
  @Test void mapAToPkc(){ utils.Err.strCmp("CTEXT\n", run("map_a_to_pkc"));}
  @Test void mapBilling(){
    utils.Err.strCmp("""
log2: # billing
log2: # monthly report
log2: == invoice 42 ==
""", run("map_billing"));
  }
  @Test void mapBillingReportsEachChangeOfThePinItOverrides(@TempDir Path tmp) throws IOException, InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    Fs.copyTree(freshIntegrationRoot("map_billing"), root);
    var app= root.resolve("_billing", "_rank_app.fear");
    editAndTouch(app, " instead of _log_v1", "");
    utils.Err.strCmp("""
The "instead of" list of a map must name exactly the folders chosen by the maps of lower rank that the map overrides:
- "map log as _log_v2 in _invoice;"; written in fear:/_billing/_rank_app.fear
These maps of lower rank choose other folders:
- "map log as _log_v1 in _invoice;"; written in fear:/_invoice/_rank_worker200.fear
Expected: "map log as _log_v2 in _invoice instead of _log_v1;".
Error 7 WellFormedness
""", assertThrows(RuntimeException.class, () -> coordinator(root).main(root, stLib)).getMessage());
    editAndTouch(app, "in _invoice;", "in _invoice instead of _log_v1;");
    editAndTouch(root.resolve("_invoice", "_rank_worker200.fear"), "_log_v1", "_log_v2");
    utils.Err.strCmp("""
The "instead of" list of a map must name exactly the folders chosen by the maps of lower rank that the map overrides:
- "map log as _log_v2 in _invoice instead of _log_v1;"; written in fear:/_billing/_rank_app.fear
No map of lower rank chooses another folder.
Expected: "map log as _log_v2 in _invoice;".
Error 7 WellFormedness
""", assertThrows(RuntimeException.class, () -> coordinator(root).main(root, stLib)).getMessage());
    editAndTouch(app, " instead of _log_v1", "");
    utils.Err.strCmp("""
log2: # billing
log2: # monthly report
log2: == invoice 42 ==
""", coordinator(root).main(root, stLib));
  }
  // What counts as a main of a package: every type implementing base.Main, declared at
  // the top level or inside a method: base.Main implements base.CaptureFree, so a main
  // captures nothing and the backend gives it an instance.
  @Test void mainInMethod(){
    utils.Err.strCmp("""
capture free main in a method
top level main
""", run("mainInMethod"));
  }
  @Test void testingStandardLibrary(){ testOk("testingStandardLibrary");}
  //every main runs under base.NoManager, Child and Failing on their own first; every process Parent starts keeps that attenuator and adds its own
  //Failing run as a new process prints nothing: its Info reaches Parent, which prints it
  @Test void testProcessAttenuation(){
    utils.Err.strCmp("""
child starts
child: download ok
child: manager revoked
child ends
the failing main throws
imm Failing.main(_) error line: 40 in file _proc/_rank_app.fear
parent starts
parent: manager revoked
child starts
child: download ok
child: manager revoked
child ends
parent: child ok
child starts
child: download revoked
child: manager revoked
child ends
parent: child ok
parent: failing failed: the failing main throws
parent ends
spin ends
starter starts
child starts
child: download ok
child: manager revoked
child ends
child awaited: True
child again: CapRunningMain.await was called on a surely terminated CapRunningMain.
twice: [{"line":"a \\"quoted\\" word"},{"line":"a \\"quoted\\" word"}]
twice awaited: Opt[["ab","ab"]]
failing info failed: the failing info main throws
spin info terminated
spin awaited: False
spin terminated
spin again: CapRunningMain.terminate was called on a surely terminated CapRunningMain.
spin ends
failing terminated: the failing main throws
starter ends
""", main(freshIntegrationRoot("testProcessAttenuation"), List.of("-DfearlessAttenuators=base.NoManager")));
  }
  @Test void baseGeneratedExamples(@TempDir Path tmp) throws InterruptedException{
    var out= Coordinator.runMains(Coordinator.runData(tmp, ResolveResource.stLibPath), List.of(ResolveResource.coordinatorJars, baseCache), "_base.Main");
    writeJUnitReport("baseGeneratedExamples", tmp);
    utils.Err.strCmp("", out);
  }
  @Test void testDocs(){
    utils.Err.strCmp("""
Hello world
/// this string must not become documentation
""", run("testDocs"));
  }
  static Path docExamplesProject(Path tmp){
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_p/_rank_app.fear
iii
use base.Main as Main;
use base.Nat as Nat;
use base.Debug as Debug;
Calc:{
  ///Adds one.
  //> .check{Calc.inc(1) .assertEq 2}
  .inc(n: Nat): Nat -> n + 1;
  ///Doubles.
  //> .check{Calc.twice(2) .assertEq 5}
  .twice(n: Nat): Nat -> n * 2;
  }
App:Main{s->Debug#("hi")}
""");
    return root;
  }
  static Path generatedTests(Path root){ return root.resolve("_p", "auto_tests", "p_test.fear"); }
  Coordinator counting(Path root, ArrayList<String> frontends){
    var base= coordinator(root);
    return new Coordinator(){
      @Override public Path modsPath(){ return base.modsPath(); }
      @Override public Optional<Path> baseCachePath(){ return base.baseCachePath(); }
      @Override public Path stdLibBase(){ return base.stdLibBase(); }
      @Override public BackendTools backendTools(String pkgName, SourceOracle oracle, OtherPackages other, List<Literal> core, CapabilityEnvironment capabilities){
        return base.backendTools(pkgName, oracle, other, core, capabilities);
      }
      @Override public List<Literal> frontend(String pkgName, List<Ref> files, SourceOracle oracle, OtherPackages other, Map<String,String> vres, CapabilityEnvironment capabilities){
        frontends.add(pkgName);
        return Coordinator.super.frontend(pkgName, files, oracle, other, vres, capabilities);
      }
    };
  }
  @Test void aProjectWithDocExamplesCompilesOnceAndRunsItsExamples(@TempDir Path tmp) throws IOException, InterruptedException{
    var root= docExamplesProject(tmp);
    var frontends= new ArrayList<String>();
    var c= counting(root, frontends);
    var expected= """
Test failure _Calc_Examples at line: 8 in file: _p/auto_tests/p_test.fear
[###]
hi
""";
    utils.Err.strCmp(expected, c.main(root, stLib));
    utils.Err.strCmp("""
//Autogenerated from the doc examples of package p; any edit is overwritten

_Calc_Examples: base.Test {::
  .test base.Test{::
.check{Calc.inc(1) .assertEq 2}
  }
  .test base.Test{::
.check{Calc.twice(2) .assertEq 5}
  }
  }

AllAutoTests_p: base.UnitTests {::
  .test _Calc_Examples
  }
""", Fs.readUtf8(generatedTests(root)));
    var stamp= Fs.getLastModifiedTime(generatedTests(root));
    assertEquals(Map.of("p", true), Coordinator.pkgsBuilt(root, baseCache));
    utils.Err.strCmp(expected, c.main(root, stLib));
    utils.Err.strCmp(expected, c.main(root, stLib));
    assertEquals(stamp, Fs.getLastModifiedTime(generatedTests(root)));
    assertEquals(Map.of("p", true), Coordinator.pkgsBuilt(root, baseCache));
    assertEquals(List.of("p"), frontends);
  }
  @Test void aStaleGeneratedTestIsRegenerated(@TempDir Path tmp) throws IOException{
    var root= docExamplesProject(tmp);
    var frontends= new ArrayList<String>();
    var c= counting(root, frontends);
    c.compile(root, stLib);
    editAndTouch(root.resolve("_p", "_rank_app.fear"), ".inc", ".plus");
    c.compile(root, stLib);
    utils.Err.strCmp("[###].check{Calc.plus(1) .assertEq 2}[###]", Fs.readUtf8(generatedTests(root)));
    assertEquals(Map.of("p", true), Coordinator.pkgsBuilt(root, baseCache));
    assertEquals(List.of("p", "p"), frontends);
  }
  @Test void aFileWithoutTheMarkAtTheGeneratedLocationIsAnOrdinarySourceFile(@TempDir Path tmp) throws IOException{
    var root= docExamplesProject(tmp);
    var mine= "Mine:Main{s->Debug#(`mine`)}\n";
    Fs.writeUtf8(generatedTests(root), mine);
    utils.Err.strCmp("hi\nmine\n", main(root, List.of()));
    assertEquals(mine, Fs.readUtf8(generatedTests(root)));
  }
  @Test void aBrokenExampleIsReportedInTheGeneratedTestsAtEveryCompile(@TempDir Path tmp) throws IOException{
    var root= docExamplesProject(tmp);
    coordinator(root).compile(root, stLib);
    editAndTouch(root.resolve("_p", "_rank_app.fear"), "Calc.twice(2)", "Calc.thrice(2)");
    for (var _ : List.of(1, 2)){
      var ex= assertThrows(UserError.class, () -> coordinator(root).compile(root, stLib));
      utils.Err.strCmp("""
In file: fear:/_p/auto_tests/p_test.fear
[###]thrice[###]""", ex.getMessage());
    }
  }
  @Test void aUserWrittenAllAutoTestsSuppressesGeneration(@TempDir Path tmp) throws IOException{
    var root= docExamplesProject(tmp);
    coordinator(root).compile(root, stLib);
    assertTrue(Fs.exists(generatedTests(root)));
    editAndTouch(root.resolve("_p", "_rank_app.fear"), "App:Main", "AllAutoTests_p: base.UnitTests{t->t}\nApp:Main");
    utils.Err.strCmp("hi\n", main(root, List.of()));
    assertFalse(Fs.exists(generatedTests(root)));
  }
  @Test void aZippedPackageGetsNoGeneratedTests(@TempDir Path tmp){
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_p.zip/_rank_app.fear
iii
use base.Main as Main;
use base.Nat as Nat;
use base.Debug as Debug;
Calc:{
  //> .check{Calc.inc(1) .assertEq 5}
  .inc(n: Nat): Nat -> n + 1;
  }
App:Main{s->Debug#("hi")}
""");
    utils.Err.strCmp("hi\n", main(root, List.of()));
    utils.Err.strCmp("hi\n", main(root, List.of()));
    assertFalse(Fs.exists(root.resolve("_p")));
  }
  @Test void aDocExampleOfATypeRuns(@TempDir Path tmp){
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_p/_rank_app.fear
iii
use base.Main as Main;
use base.Nat as Nat;
use base.Debug as Debug;
///Counts.
//> .check{Calc.inc(1) .assertEq 5}
Calc:{
  .inc(n: Nat): Nat -> n + 1;
  }
App:Main{s->Debug#("hi")}
""");
    utils.Err.strCmp("""
Test failure _Calc_Examples at line: [###] in file: _p/auto_tests/p_test.fear
[###]
hi
""", main(root, List.of()));
  }
  @Test void docExamplesOfTwoTypesSharingASimpleNameWithDifferentAritiesRun(@TempDir Path tmp){
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_p/_rank_app.fear
iii
use base.Main as Main;
use base.Nat as Nat;
use base.Debug as Debug;
Box:{
  //> .check{Box.one .assertEq 1}
  .one: Nat -> 1;
  }
Box[X:imm]:{
  //> .check{Box.one .assertEq 1}
  .two: Nat -> 2;
  }
App:Main{s->Debug#("hi")}
""");
    utils.Err.strCmp("hi\n", main(root, List.of()));
  }
  @Test void docExamplesOfATypeNameEndingWithAPrimeRun(@TempDir Path tmp){
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_p/_rank_app.fear
iii
use base.Main as Main;
use base.Nat as Nat;
use base.Debug as Debug;
Box':{
  //> .check{Box'.one .assertEq 1}
  .one: Nat -> 1;
  }
App:Main{s->Debug#("hi")}
""");
    utils.Err.strCmp("hi\n", main(root, List.of()));
  }
  @Test void aDeletedLowerPackageIsNoticedByThePackagesBuiltOnIt(@TempDir Path tmp){
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_lib/_rank_core.fear
iii
Lib:{}
jjj
_app/_rank_app.fear
iii
use base.Main as Main;
App:Main, lib.Lib{s->base.Debug#(`app`)}
""");
    utils.Err.strCmp("app\n", main(root, List.of()));
    Fs.rmTree(root.resolve("_lib"));
    assertEquals(Map.of("app", false), Coordinator.pkgsBuilt(root, baseCache));
    var ex= assertThrows(UserError.class, () -> main(root, List.of()));
    utils.Err.strCmp("[###]lib.Lib[###]", ex.getMessage());
    assertFalse(Fs.exists(Coordinator.genJava(root.resolve(Coordinator.outDir)).resolve("lib.jar")));
    assertFalse(Fs.exists(root.resolve(Coordinator.outDir).resolve("lib.built")));
  }
  @Test void onlyImmCapture(@TempDir Path tmp){
    var root= tmp.resolve("root");
    UserError.root = root;
    Fs.ensureDir(root.resolve("_oic"));
    Fs.writeUtf8(root.resolve("_oic").resolve("_rank_app.fear"), """
use base.Nat as Nat;
use base.F as F;
use base.MF as MF;
use base.Var as Var;
use base.Vars as Vars;
use base.Block as Block;
use base.CaptureFree as CaptureFree;
Cases:{
  read .val: Nat -> 5;
  .none: F[Nat] -> NoCapture: F[Nat]{ 5 };
  .free: F[Nat] -> Free: F[Nat], CaptureFree{ 5 };
  .immLocal: F[Nat] -> Block#.let x= {5}.return{ ImmLocal: F[Nat]{ x } };
  .readParam(x: read Var[Nat]): read F[Nat] -> read ReadParam: F[Nat]{ x.get };
  .isoParam(x: iso Var[Nat]): mut MF[Nat] -> mut IsoParam: MF[Nat]{ x.get };
  .mutLocal: mut MF[Nat] -> Block#.let[mut Var[Nat]] x= {Vars#[Nat]5}.return{ mut MutLocal: MF[Nat]{ x.get } };
  imm .immThis: F[Nat] -> ImmThis: F[Nat]{ this.val };
  mut .mutThis: mut MF[Nat] -> mut MutThis: MF[Nat]{ this.val };
  .immX[X:imm](x: X): F[X] -> ImmX[X:imm]: F[X]{ x };
  .anyX[X:*](x: X): mut MF[X] -> mut AnyX[X:*]: MF[X]{ x };
  .lambda(x: Nat): F[Nat] -> { x };
}
""");
    var genJava= tmp.resolve("genJava");
    var base= coordinator(root);
    new Coordinator(){
      @Override public Path modsPath(){ return base.modsPath(); }
      @Override public Optional<Path> baseCachePath(){ return base.baseCachePath(); }
      @Override public Path stdLibBase(){ return base.stdLibBase(); }
      @Override public BackendTools backendTools(String pkgName, SourceOracle oracle, OtherPackages other, List<Literal> core, CapabilityEnvironment capabilities){
        return base.backendTools(pkgName, oracle, other, core, capabilities);
      }
      @Override public void backend(String pkgName, List<Literal> core, SourceOracle oracle, OtherPackages other, CapabilityEnvironment capabilities){
        new Backend(genJava, base.backendTools(pkgName, oracle, other, core, capabilities)).produceJavaCode();
      }
    }.compile(root, stLib);
    var got= Fs.walk(genJava, s -> s.filter(Fs::isRegularFile).sorted().map(p -> p.getFileName()+(Fs.readUtf8(p).contains("_base.OnlyImmCapture") ? " only imm" : "")).toList());
    utils.Err.strCmp("""
AnyX$p$1.java
Cases$1c$0.java
Free$o$0.java only imm
ImmLocal$b4$0.java only imm
ImmThis$5k$0.java only imm
ImmX$p$1.java only imm
IsoParam$b4$0.java
Main.java
MutLocal$b4$0.java
MutThis$5k$0.java
NoCapture$n4$0.java only imm
ReadParam$ls$0.java
_ACase$1k$0.java only imm
_BCase$1k$0.java only imm
_CCase$1k$0.java only imm
_DCase$1k$0.java only imm
_FCase$1k$0.java only imm
_GCase$1k$0.java only imm
""", Join.of(got, "", "\n", "\n"));
  }
  @Test void twoFileLogsWhoseNamesDifferOnlyByCaseWriteToFilesThatDifferNotOnlyByCase(@TempDir Path tmp){
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_p/_rank_app.fear
iii
use base.FileLog as FileLog;
AppLog: FileLog{}
Applog: FileLog{}
""");
    var genJava= tmp.resolve("genJava");
    var base= coordinator(root);
    new Coordinator(){
      @Override public Path modsPath(){ return base.modsPath(); }
      @Override public Optional<Path> baseCachePath(){ return base.baseCachePath(); }
      @Override public Path stdLibBase(){ return base.stdLibBase(); }
      @Override public BackendTools backendTools(String pkgName, SourceOracle oracle, OtherPackages other, List<Literal> core, CapabilityEnvironment capabilities){
        return base.backendTools(pkgName, oracle, other, core, capabilities);
      }
      @Override public void backend(String pkgName, List<Literal> core, SourceOracle oracle, OtherPackages other, CapabilityEnvironment capabilities){
        new Backend(genJava, base.backendTools(pkgName, oracle, other, core, capabilities)).produceJavaCode();
      }
    }.compile(root, stLib);
    var logFiles= Fs.walk(genJava, s -> s.filter(Fs::isRegularFile)
      .flatMap(p -> Fs.readUtf8(p).lines().filter(l -> l.contains("_base.AppLog.open("))).sorted().toList());
    utils.Err.strCmp("""
[###]_base.AppLog.open(java.nio.file.Path.of(".out","logs","_p","app_log.log"), false);
[###]_base.AppLog.open(java.nio.file.Path.of(".out","logs","_p","applog.log"), false);
""", Join.of(logFiles, "", "\n", "\n"));
  }
  @Test void testAssets(){ testOk("testAssets");}
  private Path theOneLogFile(Path dir, String prefix) throws IOException{
    try (var files= Fs.list(dir)){
      var found= files.filter(p -> p.getFileName().toString().startsWith(prefix)).toList();
      assertEquals(1, found.size(), found.toString());
      return found.getFirst();
    }
  }
  @Test void testLogging() throws InterruptedException, IOException{
    var root= ResolveResource.integrationTests.resolve("testLogging");
    Fs.rmTree(root.resolve(".out"));
    testOk("testLogging");
    var logDir= root.resolve(".out").resolve("logs");
    var content= Fs.readUtf8(theOneLogFile(logDir.resolve("_base"), "log$"));
    utils.Err.strCmp("""
[###] starting logging example
[###] processed item 1
[###] processed item 2
[###] processed item 3
[###] finished
""", content);
    var pkgContent= Fs.readUtf8(theOneLogFile(logDir.resolve("_logging"), "pkg_log$"));
    utils.Err.strCmp("[###] package-specific log entry\n", pkgContent);
  }
  // testingNorms holds no fearless unit tests: a cache hit and a recomputation return
  // the very same value, so nothing about caching can be asserted on results alone.
  // Each cached body there prints one Debug line, so the trace below is the assertion:
  // a line appearing once across several calls is what says the cache actually held.
  @Test void testingNorms(){
    utils.Err.strCmp("""
Summing 1 and 2
3
Summing 3 and 4
7
3
3
7
ToInfo HelloStringInfo
ToInfo AnotherInfo
OutInfo "HelloStringInfo"
OutInfo "AnotherInfo"
"HelloStringInfo""AnotherInfo""HelloStringInfo""HelloStringInfo""AnotherInfo""HelloStringInfo"
6
InReprStr10
!10!
InReprStr20
!20!
InReprStr220
!20 - 10!
!20 - 10!
InReprStr230
!30 - 10!
InReprStr30
!30!
memoCat
naming
cat
cat
cat
Bar.baz called
cleaned
Bar.baz called
memoCat
naming
cat
memoCat
naming
cat
naming
cat
listSizeF
3
3
listSizeF
2
summingList
6
6
summingList
7
sizing
71
71
sizing
71
sizingU
2
2
sizingU
3
zeroF
42
42
zeroMemo
7
7
""", run("testingNorms"));
  }
  void compileOk(String name){ var project= freshIntegrationRoot(name); coordinator(project).compile(project, stLib); }
  @Test void theInteractiveProjectsStillCompile(){
    for (var name : List.of("testGui1", "testGui2", "testGuiImg", "testGuiLive", "testGuiInput", "testGuiText", "testGuiTiles", "testBasketball", "testTicTacToe")){ compileOk(name); }
  }
  // Two assets that generate the same auto-loaded type name (here "foo.txt" and "foo.png",
  // both auto-loading as "Foo") must be rejected end-to-end, before the frontend ever sees
  // the synthetic autoloaded_assets.fear file, with a message naming the two real files
  // instead of blaming a file the user never wrote.
  @Test void assetAutoloadNameCollision(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_col/_rank_app.fear
iii

jjj
_col/foo.txt
iii
hello
jjj
_col/foo.png
iii
ignored
""");
    var ex= assertThrows(UserError.class, () -> coordinator(root).main(root, stLib));
    utils.Err.strCmp("""
Invalid path in this project folder.

Root: [###]
Path: "_col/foo.txt"

What went wrong
- Auto-loading both of these files would produce two declarations of the same type name.
  Name 1: "fear:/_col/foo.png"
  Name 2: "fear:/_col/foo.txt"
  Reason: Both would auto-load as the type "Foo".

How to fix
- Rename one of them so they are clearly distinct.
- Avoid two assets whose folder and file name produce the same auto-loaded type name.

We check this so that you[###]
""", ex.getMessage());
  }
  @Test void mainsAreListedWithTheFileDeclaringThem(@TempDir Path tmp){
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_col/_rank_app.fear
iii
use base.Main as Main;
Hello:Main{s->base.Debug#("hi")}
jjj
_col/more.fear
iii
Again:Main{s->base.Debug#("again")}
""");
    coordinator(root).compile(root, stLib);
    utils.Err.strCmp("""
--- col_manifest.info
{
  "mains": {
    "col.Again": ["_col/more.fear", [], [], [["base.IconsFearless", "icons/fearless.png", "", "", ""]]],
    "col.Hello": ["_col/_rank_app.fear", [], [], [["base.IconsFearless", "icons/fearless.png", "", "", ""]]]
  },
  "attenuators": [],
  "dataRead": [],
  "dataEdit": []
}
""", manifests(root));
    assertEquals(Map.of("col.Again", "_col/more.fear", "col.Hello", "_col/_rank_app.fear"), files(coordinator(root).mains(root, stLib).orElseThrow()));
  }
  @Test void theConcreteDataReadAndDataEditTypesAreListed(@TempDir Path tmp){
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_col/_rank_app.fear
iii
use base.DataRead as DataRead;
use base.DataEdit as DataEdit;
Pub: DataRead{}
Archive: Pub{}
Diary: DataEdit{}
Shape: DataRead{ .extra: base.Nat }
""");
    coordinator(root).compile(root, stLib);
    utils.Err.strCmp("""
--- col_manifest.info
{
  "mains": {},
  "attenuators": [],
  "dataRead": ["col.Archive", "col.Pub"],
  "dataEdit": ["col.Diary"]
}
""", manifests(root));
  }
  @Test void aCaptureFreeMainDeclaredInAMethodIsListedLikeTheOnesItRuns(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_col/_rank_app.fear
iii
use base.Main as Main;
use base.CaptureFree as CaptureFree;
Top:Main{s->base.Debug#(`top`)}
MkFree:{.mk:Main->Free:Main,CaptureFree{s->base.Debug#(`free`)}}
""");
    utils.Err.strCmp("free\ntop\n", coordinator(root).main(root, stLib));
    assertEquals(Map.of("col.Free", "_col/_rank_app.fear", "col.Top", "_col/_rank_app.fear"), files(coordinator(root).mains(root, stLib).orElseThrow()));
  }
  @Test void aMainWithGenericParametersIsRefused(@TempDir Path tmp){
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_col/_rank_app.fear
iii
use base.Main as Main;
Hello:Main{s->base.Debug#(`plain`)}
Hello[X]:Main{s->base.Debug#(`generic`)}
""");
    var ex= assertThrows(UserError.class, () -> coordinator(root).compile(root, stLib));
    utils.Err.strCmp("""
In file: fear:/_col/_rank_app.fear

003| Hello[X]:Main{s->base.Debug#(`generic`)}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "Hello[_]"
Type declaration "Hello[_]" implements "Main" and has generic parameters.
A type implementing "Main" must have zero generic parameters.
Error 7 WellFormedness""", ex.getMessage());
  }
  @Test void aSourceFileThatIsNotUtf8IsAUserError(@TempDir Path tmp) throws IOException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_col/_rank_app.fear
iii
use base.Main as Main;
Hello:Main{s->base.Debug#(`hi`)}
""");
    Fs.write(root.resolve("_col", "notes.fear"), new byte[]{'/', '/', 'c', 'a', 'f', (byte)0xE9, '\n'});
    var ex= assertThrows(UserError.class, () -> coordinator(root).compile(root, stLib));
    utils.Err.strCmp("""
[###]Path: "_col/notes.fear"

This file is not valid UTF-8 text.
The first invalid byte sequence is on line 1, at byte offset 5 (counting from zero).
Fearless reads the source files of a project as UTF-8 text.
Save the file again as UTF-8, or remove it from the project folder.
""", ex.getMessage());
  }
  @Test void aPackageNamedAfterAJavaKeywordRuns(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_int/_rank_app.fear
iii
use base.Main as Main;
Hello:Main{s->base.Debug#(`hi`)}
""");
    utils.Err.strCmp("hi\n", coordinator(root).main(root, stLib));
  }
  @Test void aCacheWithNoArgumentsKeepsItsFailure(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_col/_rank_app.fear
iii
use base.Main as Main;
use base.Block as Block;
use base.Debug as Debug;
use base.Str as Str;
use base.Error as Error;
use base.System as System;
use base.Cache as Cache;
use base.CacheF as CacheF;
Answer:CacheF[Str]{ Block#(Debug#("computing"), Error.msg "boom") }
Ask:{ #(s: mut System): Str -> s.try#{Cache#(Answer)}.run{.ok x -> x; .info i -> (i.getMap.get "msg").getMsg} }
Hello:Main{s->Block#
  .do{ Debug#(Ask#(s)) }
  .do{ Debug#(Ask#(s)) }
  .done
  }
""");
    utils.Err.strCmp("""
computing
boom
boom
""", coordinator(root).main(root, stLib));
  }
  @Test void aSingletonRunsAZeroArgumentMethodAtEveryCall(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_col/_rank_app.fear
iii
use base.Main as Main;
use base.Block as Block;
use base.Debug as Debug;
use base.Str as Str;
use base.Nat as Nat;
use base.Cache as Cache;
use base.CacheF as CacheF;
Family:{ .name: Str; .greeting: Str -> Block#(Debug#("greeting "+(this.name)), "hi "+(this.name)) }
FamilyA:Family{ .name -> "a" }
FamilyOf:{ #(n: Str): Family -> {.name -> n} }
OnceF:CacheF[Nat]{ # -> Block#(Debug#("onceF"), 5) }
Hello:Main{s->Block#
  .do{ Debug#(FamilyA.greeting) }
  .do{ Debug#(FamilyA.greeting) }
  .let c= {FamilyOf#"c"}
  .do{ Debug#(c.greeting) }
  .do{ Debug#(c.greeting) }
  .do{ Debug#(Cache.of OnceF) }
  .do{ Cache.flush OnceF }
  .do{ Debug#(Cache.of OnceF) }
  .done
  }
""");
    utils.Err.strCmp("""
greeting a
hi a
greeting a
hi a
greeting c
hi c
greeting c
hi c
onceF
5
onceF
5
""", coordinator(root).main(root, stLib));
  }
  @Test void aFlushReachesAZeroArgumentMethodReturningACachedResult(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_col/_rank_app.fear
iii
use base.Main as Main;
use base.Block as Block;
use base.Debug as Debug;
use base.Nat as Nat;
use base.Cache as Cache;
use base.CacheF as CacheF;
Answer:{ #: Nat -> Cache#(AnswerF) }
AnswerF:CacheF[Nat]{ Block#(Debug#("computing"), 42) }
Hello:Main{s->Block#
  .do{ Debug#(Answer#) }
  .do{ Cache.flush(AnswerF) }
  .do{ Debug#(Answer#) }
  .done
  }
""");
    utils.Err.strCmp("""
computing
42
computing
42
""", coordinator(root).main(root, stLib));
  }
  @Test void aCaptureFreeObjectIsACacheKey(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_col/_rank_app.fear
iii
use base.Main as Main;
use base.Block as Block;
use base.Debug as Debug;
use base.Str as Str;
use base.Cache as Cache;
use base.CacheF as CacheF;
use base.CaptureFree as CaptureFree;
Letter:CaptureFree{ .get: Str }
A:Letter{ .get -> "a" }
B:Letter{ .get -> "b" }
Show:CacheF[Letter,Str]{ x -> Block#(Debug#("computing "+(x.get.get)), x.get.get) }
Hello:Main{s->Block#
  .do{ Debug#(Cache#(Cache.normCaptureFree[Letter](A,{::}), Show)) }
  .do{ Debug#(Cache#(Cache.normCaptureFree[Letter](A,{::}), Show)) }
  .do{ Debug#(Cache#(Cache.normCaptureFree[Letter](B,{::}), Show)) }
  .done
  }
""");
    utils.Err.strCmp("""
computing a
a
a
computing b
b
""", coordinator(root).main(root, stLib));
  }
  @Test void anEnumerationFamilyListsItsValuesOnceForEachCachedMethod(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_col/_rank_app.fear
iii
use base.Main as Main;
use base.Block as Block;
use base.Debug as Debug;
use base.Enum as Enum;
use base.Enums as Enums;
use base.Lists as Lists;
use base.Cache as Cache;
Suit: Enum[Suit]{ read .close->this; .close->::; .norm->Cache.normCaptureFree(this,{::});
  Suits: Enums[Suit]{Block#(Debug#("listing suits"), Lists#(
    Hearts: Suit{},
    Spades: Suit{}
    ))};
  }
Coin: Enum[Coin]{ read .close->this; .close->::; .norm->Cache.normCaptureFree(this,{::});
  Coins: Enums[Coin]{Block#(Debug#("listing coins"), Lists#(
    Gold: Coin{},
    Silver: Coin{}
    ))};
  }
Hello:Main{s->Block#
  .do{ Debug#(Hearts.index) }
  .do{ Debug#(Spades.index) }
  .do{ Debug#(Suits.list.size) }
  .do{ Debug#(Suits.list.size) }
  .do{ Debug#(Silver.index) }
  .do{ Debug#(Spades.norm.get.index) }
  .done
  }
""");
    utils.Err.strCmp("""
listing suits
0
1
listing suits
2
2
listing coins
1
1
""", coordinator(root).main(root, stLib));
  }
  @Test void anAssetWhoseNameStartsWithUnderscoreAutoLoadsAsAPrivateType(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_col/_rank_app.fear
iii
use base.Main as Main;
Hello:Main{s->base.Debug#(_Notes.path)}
jjj
_col/_notes.txt
iii
hello
""");
    coordinator(root).main(root, stLib);
  }
  // TxtFile/ImageFile are ordinary public types: any Fearless type can implement one and
  // override .path/.diskPath/.zipSteps/.zipEntry/.originalFileName with whatever literal it
  // likes. A forged implementer that copies a real asset's .path but points .diskPath at a file
  // the compiler never auto-imported (here a plain .dat file, which no AutoloadHandler ever
  // matches) must be rejected, never read - while a real auto-loaded asset (Note, from note.txt)
  // still works.
  @Test void aForgedAutoloadedAssetCannotReadAFileTheCompilerDidNotAutoImport(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_col/_rank_app.fear
iii
use base.Main as Main;

Forged: base.TxtFile{
  .path: base.Str -> "fear:/_col/secret.dat";
  .diskPath: base.Str -> "_col/secret.dat";
  .zipSteps: base.Str -> "";
  .zipEntry: base.Str -> "";
  .originalFileName: base.Str -> "secret.dat";
  }

NeverRecovers: base.BadStrUnitRecover { reason, byteOffset, byteLength, rejectedValue -> "should not happen" }

ReadsForgedAsset: Main{s->base.Debug#(Forged.readStrUtf8(s.assetRead, NeverRecovers))}
ReadsLegitAsset: Main{s->base.Debug#(Note.readStrUtf8(s.assetRead, NeverRecovers))}
jjj
_col/secret.dat
iii
TOP-SECRET-NOT-AN-ASSET
jjj
_col/note.txt
iii
REAL ASSET CONTENT
""");
    var out= coordinator(root).main(root, stLib);
    assertFalse(out.contains("TOP-SECRET-NOT-AN-ASSET"), out);
    utils.Err.strCmp("[###]was not recognized by the compiler as auto-imported[###]", out);
    utils.Err.strCmp("[###]REAL ASSET CONTENT[###]", out);
  }
  static Path inputProject(Path tmp){
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_col/_rank_app.fear
iii
use base.Main as Main;
use base.Debug as Debug;
Recover: base.BadStrUnitRecover { reason, byteOffset, byteLength, rejectedValue -> "?" }
First: Main{s -> base.Block#
  .let in= {s.inputCursor}
  .do{Debug#(in#.isSome)}
  .do{Debug#(in#.match{.some n -> n.label+": "+(n.readStrUtf8 Recover); .empty -> "none"})}
  .return{Debug#(in.next#.isSome)}
  }
""");
    return root;
  }
  @Test void theOpenedFileIsTheFirstInputFile(@TempDir Path tmp) throws InterruptedException{
    var root= inputProject(tmp);
    var file= tmp.resolve("not"+(Fs.isWindows() ? "é" : "\t")+"s.txt");
    Fs.writeUtf8(file, "caf\u00e9 time");
    utils.Err.strCmp("True\nnot?s.txt: caf? time\nFalse\n", main(root, List.of("-DfearlessInput.file="+PathClassLoader.argOf(file))));
  }
  @Test void aProjectInAFolderWhoseNameIsNotUtf8CompilesAndRuns(@TempDir Path tmp){
    var odd= Fs.isLinux() ? Path.of(URI.create(tmp.toUri()+"caf%E9")) : tmp.resolve("caf\u00e9");
    var root= inputProject(odd);
    var file= odd.resolve("in.txt");
    Fs.writeUtf8(file, "caf\u00e9 time");
    utils.Err.strCmp("True\nin.txt: caf? time\nFalse\n", main(root, List.of("-DfearlessInput.file="+PathClassLoader.argOf(file))));
  }
  @Test void aProjectOfSeveralPackagesInAFolderWhoseNameIsNotUtf8PassesItsTests(@TempDir Path tmp){
    var odd= Fs.isLinux() ? Path.of(URI.create(tmp.toUri()+"caf%E9")) : tmp.resolve("caf\u00e9");
    var root= odd.resolve("testAssets");
    Fs.copyTree(freshIntegrationRoot("testAssets"), root);
    unitTestsOk("testAssetsNotUtf8", root);
  }
  String cutTextRun(Path tmp, String read, byte[] bytes) throws IOException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_col/_rank_app.fear
iii
use base.Main as Main;
use base.Debug as Debug;
Name: base.F[base.BadTextReason,base.Str]{ ::.match{
  .malformedEncoding -> "malformedEncoding";
  .truncatedEncoding -> "truncatedEncoding";
  .notScalar -> "notScalar";
  .notInStr -> "notInStr";
  }}
Show: base.BadStrUnitRecover { reason, byteOffset, byteLength, rejectedValue ->
  "["+(Name#reason)+":"+byteOffset+":"+byteLength+":"+rejectedValue+"]"
  }
Cut: Main{s -> Debug#(s.inputCursor#.match{.some n -> n.READ Show; .empty -> "none"})}
""".replace("READ", read));
    var file= Fs.write(tmp.resolve("cut.bin"), bytes);
    return main(root, List.of("-DfearlessInput.file="+PathClassLoader.argOf(file)));
  }
  @Test void aUtf8SequenceCutByTheEndOfTheBytesIsTruncatedEncoding(@TempDir Path tmp) throws IOException{
    utils.Err.strCmp("a[truncatedEncoding:1:2:226]\n", cutTextRun(tmp, "readStrUtf8", new byte[]{'a', (byte)0xE2, (byte)0x82}));
  }
  @Test void aUtf16SurrogatePairCutByTheEndOfTheBytesIsTruncatedEncoding(@TempDir Path tmp) throws IOException{
    utils.Err.strCmp("a[truncatedEncoding:2:2:55357]\n", cutTextRun(tmp, "readStrUtf16Le", new byte[]{'a', 0, 0x3D, (byte)0xD8}));
  }
  @Test void withNoOpenedFileTheInputFilesStartEmpty(@TempDir Path tmp) throws InterruptedException{
    utils.Err.strCmp("False\nnone\nFalse\n", main(inputProject(tmp), List.of()));
  }
  @Test void anInputFileThatCanNotBeReadIsADeterministicError(@TempDir Path tmp){
    utils.Err.strCmp("The input file \"gone.txt\" could not be read: NoSuchFileException\n", dataRun(tmp, """
Gone: Main{s -> Debug#(St#(base.Try#(s.iso.close, {t -> t.inputCursor#.match{.some n -> n.readStrUtf8 Recover; .empty -> "none"}})))}
""", List.of("-DfearlessInput.file="+PathClassLoader.argOf(tmp.resolve("gone.txt")))));
  }
  static Path dataProject(Path tmp){
    var data= tmp.resolve("data");
    FsDsl.materialize(data, """
data.fearless
iii

jjj
readme
iii
hi
jjj
notes/a.txt
iii
first
jjj
todo/item.txt
iii
milk
jjj
.hidden/secret.txt
iii
secret
jjj
pics.zip/one.txt
iii
inside
jjj
pics.zip/deep/two.txt
iii
deeper
jjj
pics.zip/inner.zip/three.txt
iii
nested
""");
    return data;
  }
  String dataRun(Path tmp, String main, List<String> links){
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_col/_rank_app.fear
iii
use base.Main as Main;
use base.Debug as Debug;
use base.Lists as L;
Recover: base.BadStrUnitRecover { reason, byteOffset, byteLength, rejectedValue -> "?" }
Ls: { #(a: mut base.Action[base.List[base.Str]]): base.Str -> a.run{.ok xs -> xs.str{::}; .info i -> i.msg.match{.some m -> m; .empty -> (i.getMap.get "msg").getMsg}} }
Rd: { #(a: mut base.Action[mut base.CapInputCursorNode]): base.Str -> a.run{.ok n -> n.label+": "+(n.readStrUtf8 Recover); .info i -> i.msg.match{.some m -> m; .empty -> (i.getMap.get "msg").getMsg}} }
Wr: { #(a: mut base.Action[base.Void]): base.Str -> a.run{.ok _ -> "ok"; .info i -> i.msg.match{.some m -> m; .empty -> (i.getMap.get "msg").getMsg}} }
Sz: { #(a: mut base.Action[mut base.CapInputCursorNode]): base.Str -> a.run{.ok n -> n.readBytes.size.str; .info i -> i.msg.match{.some m -> m; .empty -> (i.getMap.get "msg").getMsg}} }
St: { #(a: mut base.Action[base.Str]): base.Str -> a.run{.ok x -> x; .info i -> i.msg.match{.some m -> m; .empty -> (i.getMap.get "msg").getMsg}} }
Q: base.UnencodableRecover{ scalar, index -> L#(63.getByte) }
Pub: base.DataRead{}
Notes: base.DataEdit{}
Nope: base.DataEdit{}
"""+main);
    return main(root, links);
  }
  static List<String> dataLinks(Path pub, Path notes){ return List.of("-DfearlessDataRead.col.Pub="+PathClassLoader.argOf(pub), "-DfearlessDataEdit.col.Notes="+PathClassLoader.argOf(notes)); }
  static List<String> fearPaths(Path data){ return new RealSourceOracleWithZip(data).allFiles().stream().map(Ref::fearPath).toList(); }
  static String fearFile(Path data, String fearPath){ return OneOr.of(fearPath, new RealSourceOracleWithZip(data).allFiles().stream().filter(r -> r.fearPath().equals(fearPath))).loadString(); }
  @Test void aDataProjectIsReadThroughTheRootsOfACapabilityAndAZipIsAFolder(@TempDir Path tmp){
    var data= dataProject(tmp);
    utils.Err.strCmp("""
[notes, pics, readme, todo]
[deep, inner, one.txt]
[three.txt]
The path "readme" of "col.Pub" names a resource, not a folder.
The path "none" of "col.Pub" names no folder.
The path ".hidden" of "col.Pub" is not valid: ".hidden" is not a valid folder name. A folder name uses only lowercase letters, digits and underscores, starts with a letter or an underscore, and is not "con", "prn", "aux", "nul", "com1" to "com9" or "lpt1" to "lpt9".
No data project is mounted on "col.Nope".
col.Notes/readme: hi
No dataEdit project is mounted on "col.Nope".
col.Pub/readme: hi
2
col.Pub/pics/deep/two.txt: deeper
col.Notes/pics/inner/three.txt: nested

col.Notes/notes/a.txt: first
The path "notes/b.txt" of "col.Pub" names no resource.
The path "todo" of "col.Pub" names a folder, not a resource.
The path "" of "col.Pub" names the root folder, not a resource.
The path "data.fearless" of "col.Pub" is not valid: the top of a data project holds no resource with the extension "fearless": that is the place of the marker of the project, which is not part of the data project.
""", dataRun(tmp, """
Reads: Main{s -> base.Block#
  .do{Debug#(Ls#(base.Try#(s.iso.close, {t -> t.dataRead(Pub).paths({})})))}
  .do{Debug#(Ls#(base.Try#(s.iso.close, {t -> t.dataRead(Pub).paths(L#("pics"))})))}
  .do{Debug#(Ls#(base.Try#(s.iso.close, {t -> t.dataEdit(Notes).paths(L#("pics", "inner"))})))}
  .do{Debug#(Ls#(base.Try#(s.iso.close, {t -> t.dataRead(Pub).paths(L#("readme"))})))}
  .do{Debug#(Ls#(base.Try#(s.iso.close, {t -> t.dataRead(Pub).paths(L#("none"))})))}
  .do{Debug#(Ls#(base.Try#(s.iso.close, {t -> t.dataRead(Pub).paths(L#(".hidden"))})))}
  .do{Debug#(Ls#(s.try#(s.iso.close, {t -> t.dataRead(Nope).paths({})})))}
  .do{Debug#(Rd#(base.Try#(s.iso.close, {t -> t.dataRead(Notes).read(L#("readme"))})))}
  .do{Debug#(Rd#(s.try#(s.iso.close, {t -> t.dataEdit(Nope).read(L#("readme"))})))}
  .do{Debug#(Rd#(base.Try#(s.iso.close, {t -> t.dataRead(Pub).read(L#("readme"))})))}
  .do{Debug#(Sz#(base.Try#(s.iso.close, {t -> t.dataRead(Pub).read(L#("readme"))})))}
  .do{Debug#(Rd#(base.Try#(s.iso.close, {t -> t.dataRead(Pub).read(L#("pics", "deep", "two.txt"))})))}
  .do{Debug#(Rd#(base.Try#(s.iso.close, {t -> t.dataEdit(Notes).read(L#("pics", "inner", "three.txt"))})))}
  .do{Debug#(Rd#(base.Try#(s.iso.close, {t -> t.dataEdit(Notes).read(L#("notes", "a.txt"))})))}
  .do{Debug#(Rd#(base.Try#(s.iso.close, {t -> t.dataRead(Pub).read(L#("notes", "b.txt"))})))}
  .do{Debug#(Rd#(base.Try#(s.iso.close, {t -> t.dataRead(Pub).read(L#("todo"))})))}
  .do{Debug#(Rd#(base.Try#(s.iso.close, {t -> t.dataRead(Pub).read({})})))}
  .return{Debug#(Rd#(base.Try#(s.iso.close, {t -> t.dataRead(Pub).read(L#("data.fearless"))})))}
  }
""", dataLinks(data, data)));
  }
  @Test void aWriteKeepsTheDataProjectValidAndRewritesTheZipsItEnters(@TempDir Path tmp){
    var data= dataProject(tmp);
    var resourceName= "is not a valid resource name. A resource name is a valid folder name, a dot and an extension of 1 to 16 lowercase letters or digits, other than \"zip\" (a zip is a folder); a few well known names, like \"readme\", have no extension, and a few well known extensions, like \"tar.gz\", have two parts.";
    utils.Err.strCmp("""
ok
ok
ok
ok
ok
ok
ok
ok
ok
ok
No dataEdit project is mounted on "col.Nope".
The path "Bad.txt" of "col.Notes" is not valid: "Bad.txt" %s
The path "pics/x.zip" of "col.Notes" is not valid: "x.zip" %s
The path "other.fearless" of "col.Notes" is not valid: the top of a data project holds no resource with the extension "fearless": that is the place of the marker of the project, which is not part of the data project.
The path "readme/x.txt" of "col.Notes" is not valid: "readme" is a resource, not a folder.
The path "readme.md" of "col.Notes" is not written: its folder holds "readme", and a resource without extension cannot share its name with a resource with an extension.
The path "todo" of "col.Notes" names a folder, not a resource.
""".formatted(resourceName, resourceName), dataRun(tmp, """
Writes: Main{s -> base.Block#
  .do{Debug#(Wr#(base.Try#(s.iso.close, {t -> t.dataEdit(Notes).writeStrUtf8(L#("notes", "b.txt"), "second")})))}
  .do{Debug#(Wr#(base.Try#(s.iso.close, {t -> t.dataEdit(Notes).writeStrAscii(L#("fresh", "deeper", "c.txt"), "third")})))}
  .do{Debug#(Wr#(base.Try#(s.iso.close, {t -> t.dataEdit(Notes).writeStrLatin1(L#("pics", "deep", "four.txt"), "zipped")})))}
  .do{Debug#(Wr#(base.Try#(s.iso.close, {t -> t.dataEdit(Notes).writeStrUtf8(L#("pics", "inner", "three.txt"), "renested")})))}
  .do{Debug#(Wr#(base.Try#(s.iso.close, {t -> t.dataEdit(Notes).writeStrUtf8(L#("pics", "new", "five.txt"), "in a new folder of a zip")})))}
  .do{Debug#(Wr#(base.Try#(s.iso.close, {t -> t.dataEdit(Notes).writeUStrUtf16Le(L#("u16.txt"), "caf".u("00E9"))})))}
  .do{Debug#(Wr#(base.Try#(s.iso.close, {t -> t.dataEdit(Notes).writeBytes(L#("raw.bin"), L#(72.getByte, 105.getByte))})))}
  .do{Debug#(Wr#(base.Try#(s.iso.close, {t -> t.dataEdit(Notes).writeStrUtf32Be(L#("u32.txt"), "A")})))}
  .do{Debug#(Wr#(base.Try#(s.iso.close, {t -> t.dataEdit(Notes).writeUStrAscii(L#("ascii.txt"), "caf".u("00E9"), Q)})))}
  .do{Debug#(Wr#(base.Try#(s.iso.close, {t -> t.dataEdit(Notes).writeUStrLatin1(L#("latin1.txt"), "caf".u("00E9 1F600"), Q)})))}
  .do{Debug#(Wr#(s.try#(s.iso.close, {t -> t.dataEdit(Nope).writeStrUtf8(L#("x.txt"), "no")})))}
  .do{Debug#(Wr#(base.Try#(s.iso.close, {t -> t.dataEdit(Notes).writeStrUtf8(L#("Bad.txt"), "no")})))}
  .do{Debug#(Wr#(base.Try#(s.iso.close, {t -> t.dataEdit(Notes).writeStrUtf8(L#("pics", "x.zip"), "no")})))}
  .do{Debug#(Wr#(base.Try#(s.iso.close, {t -> t.dataEdit(Notes).writeStrUtf8(L#("other.fearless"), "no")})))}
  .do{Debug#(Wr#(base.Try#(s.iso.close, {t -> t.dataEdit(Notes).writeStrUtf8(L#("readme", "x.txt"), "no")})))}
  .do{Debug#(Wr#(base.Try#(s.iso.close, {t -> t.dataEdit(Notes).writeStrUtf8(L#("readme.md"), "no")})))}
  .return{Debug#(Wr#(base.Try#(s.iso.close, {t -> t.dataEdit(Notes).writeStrUtf8(L#("todo"), "no")})))}
  }
""", dataLinks(data, data)));
    assertEquals(List.of("fear:/ascii.txt", "fear:/data.fearless", "fear:/fresh/deeper/c.txt", "fear:/latin1.txt", "fear:/notes/a.txt", "fear:/notes/b.txt",
      "fear:/pics/deep/four.txt", "fear:/pics/deep/two.txt", "fear:/pics/inner/three.txt", "fear:/pics/new/five.txt", "fear:/pics/one.txt",
      "fear:/raw.bin", "fear:/readme", "fear:/todo/item.txt", "fear:/u16.txt", "fear:/u32.txt"), fearPaths(data));
    assertEquals(List.of("second", "third", "zipped", "renested", "in a new folder of a zip", "deeper", "Hi", "caf?"),
      Stream.of("notes/b.txt", "fresh/deeper/c.txt", "pics/deep/four.txt", "pics/inner/three.txt", "pics/new/five.txt", "pics/deep/two.txt", "raw.bin", "ascii.txt").map(p -> fearFile(data, "fear:/"+p)).toList());
    assertArrayEquals(new byte[]{'c', 0, 'a', 0, 'f', 0, (byte)0xE9, 0}, Fs.of(() -> Fs.readAllBytes(data.resolve("u16.txt"))));
    assertArrayEquals(new byte[]{0, 0, 0, 'A'}, Fs.of(() -> Fs.readAllBytes(data.resolve("u32.txt"))));
    assertArrayEquals(new byte[]{'c', 'a', 'f', (byte)0xE9, '?'}, Fs.of(() -> Fs.readAllBytes(data.resolve("latin1.txt"))));
    assertFalse(Fs.exists(data.resolve("pics")));
  }
  @Test void aDeleteRemovesTheFoldersAndTheZipsItLeavesEmpty(@TempDir Path tmp){
    var data= dataProject(tmp);
    utils.Err.strCmp("""
ok
[deep, one.txt]
ok
ok
ok
The path "notes/a.txt" of "col.Notes" names no resource.
The path "data.fearless" of "col.Notes" is not valid: the top of a data project holds no resource with the extension "fearless": that is the place of the marker of the project, which is not part of the data project.
No dataEdit project is mounted on "col.Nope".
[readme, todo]
""", dataRun(tmp, """
Deletes: Main{s -> base.Block#
  .do{Debug#(Wr#(base.Try#(s.iso.close, {t -> t.dataEdit(Notes).delete(L#("pics", "inner", "three.txt"))})))}
  .do{Debug#(Ls#(base.Try#(s.iso.close, {t -> t.dataEdit(Notes).paths(L#("pics"))})))}
  .do{Debug#(Wr#(base.Try#(s.iso.close, {t -> t.dataEdit(Notes).delete(L#("pics", "deep", "two.txt"))})))}
  .do{Debug#(Wr#(base.Try#(s.iso.close, {t -> t.dataEdit(Notes).delete(L#("pics", "one.txt"))})))}
  .do{Debug#(Wr#(base.Try#(s.iso.close, {t -> t.dataEdit(Notes).delete(L#("notes", "a.txt"))})))}
  .do{Debug#(Wr#(base.Try#(s.iso.close, {t -> t.dataEdit(Notes).delete(L#("notes", "a.txt"))})))}
  .do{Debug#(Wr#(base.Try#(s.iso.close, {t -> t.dataEdit(Notes).delete(L#("data.fearless"))})))}
  .do{Debug#(Wr#(s.try#(s.iso.close, {t -> t.dataEdit(Nope).delete(L#("readme"))})))}
  .return{Debug#(Ls#(base.Try#(s.iso.close, {t -> t.dataEdit(Notes).paths({})})))}
  }
""", dataLinks(data, data)));
    assertEquals(List.of("fear:/data.fearless", "fear:/readme", "fear:/todo/item.txt"), fearPaths(data));
    assertTrue(Fs.isDirectory(data.resolve(".hidden")));
    assertFalse(Fs.exists(data.resolve("pics.zip")));
    assertFalse(Fs.exists(data.resolve("notes")));
  }
  @Test void anImageIsWrittenAsAPng(@TempDir Path tmp) throws IOException{
    var data= dataProject(tmp);
    var img= new BufferedImage(2, 3, BufferedImage.TYPE_INT_ARGB);
    img.setRGB(1, 2, 0xFF336699);
    var bout= new ByteArrayOutputStream();
    ImageIO.write(img, "png", bout);
    Fs.write(data.resolve("pic.png"), bout.toByteArray());
    utils.Err.strCmp("ok\n", dataRun(tmp, """
Images: Main{s -> base.Block#
  .let img= {s.dataRead(Pub).read(L#("pic.png")).readImage(1000)}
  .return{Debug#(Wr#(base.Try#(s.iso.close, {t -> t.dataEdit(Notes).writeImagePng(L#("copy.png"), img)})))}
  }
""", dataLinks(data, data)));
    var copy= ImageIO.read(data.resolve("copy.png").toFile());
    assertEquals(List.of(2, 3, 0xFF336699, 0), List.of(copy.getWidth(), copy.getHeight(), copy.getRGB(1, 2), copy.getRGB(0, 0)));
  }
  @Test void aPaintedImageIsWrittenAsAPng(@TempDir Path tmp) throws IOException{
    var data= dataProject(tmp);
    utils.Err.strCmp("""
Warning: the painter of an image of 4x3 draws past its bottom edge: the part outside the image is clipped
ok
""", dataRun(tmp, """
Painted: Main{s -> base.Block#
  .let img= {base.Images#(base.WidthNat#4, base.HeightNat#3, base.Color.rgb(0, 0, 255))
    .paint{g -> g.color(base.Color.rgb(255, 0, 0)).position(base.XInt#(+1), base.YInt#(+1)).rect(base.WidthNat#2, base.HeightNat#5)}}
  .return{Debug#(Wr#(base.Try#(s.iso.close, {t -> t.dataEdit(Notes).writeImagePng(L#("painted.png"), img)})))}
  }
""", dataLinks(data, data)));
    var png= ImageIO.read(data.resolve("painted.png").toFile());
    assertEquals(List.of(4, 3, 0xFF0000FF, 0xFFFF0000, 0xFFFF0000, 0xFF0000FF, 0xFF0000FF), List.of(png.getWidth(), png.getHeight(), png.getRGB(0, 0), png.getRGB(1, 1), png.getRGB(2, 2), png.getRGB(3, 1), png.getRGB(1, 0)));
  }
  static final String columns= """
Columns: { #(g: mut base.Graphics, x: base.Nat, step: base.Nat, end: base.Nat, h: base.Nat): mut base.Graphics -> (x >= end) ? {
  .then -> g;
  .else -> Columns#(g.position(base.XInt#(x.getInt), base.YInt#(+0)).rect(base.WidthNat#1, base.HeightNat#h), x + step, step, end, h);
  } }
""";
  static String row(BufferedImage png, int y){ return Join.of(IntStream.range(0, png.getWidth()).mapToObj(x -> "%08x".formatted(png.getRGB(x, y))).toList(), "", " ", ""); }
  static List<Integer> reds(BufferedImage png, int y){ return IntStream.range(0, png.getWidth()).mapToObj(x -> png.getRGB(x, y)>>16 & 255).toList(); }
  @Test void anImageScaledToItsOwnSizeKeepsItsPixels(@TempDir Path tmp) throws IOException{
    var data= dataProject(tmp);
    utils.Err.strCmp("ok\n", dataRun(tmp, """
Same: Main{s -> base.Block#
  .let img= {base.Images#(base.WidthNat#5, base.HeightNat#1, base.Color.rgb(0, 0, 0)).paint{g -> g
    .color(base.Color.rgb(255, 255, 255)).position(base.XInt#(+1), base.YInt#(+0)).rect(base.WidthNat#1, base.HeightNat#1)
    .color(base.Color.rgb(255, 0, 0)).position(base.XInt#(+3), base.YInt#(+0)).rect(base.WidthNat#1, base.HeightNat#1)}}
  .return{Debug#(Wr#(base.Try#(s.iso.close, {t -> t.dataEdit(Notes).writeImagePng(L#("same.png"), img.scale(img.width, img.height))})))}
  }
""", dataLinks(data, data)));
    utils.Err.strCmp("ff000000 ffffffff ff000000 ffff0000 ff000000", row(ImageIO.read(data.resolve("same.png").toFile()), 0));
  }
  @Test void anImageShrunkOnOneSideAveragesThePixelsOfThatSide(@TempDir Path tmp) throws IOException{
    var data= dataProject(tmp);
    utils.Err.strCmp("ok\n", dataRun(tmp, columns+"""
Thin: Main{s -> base.Block#
  .let img= {base.Images#(base.WidthNat#1024, base.HeightNat#16, base.Color.rgb(0, 0, 0)).paint{g -> Columns#(g.color(base.Color.rgb(255, 255, 255)), 0, 8, 1024, 16)}}
  .return{Debug#(Wr#(base.Try#(s.iso.close, {t -> t.dataEdit(Notes).writeImagePng(L#("thin.png"), img.scale(base.WidthNat#8, base.HeightNat#16))})))}
  }
""", dataLinks(data, data)));
    var reds= reds(ImageIO.read(data.resolve("thin.png").toFile()), 8);
    assertTrue(reds.stream().allMatch(r -> Math.abs(r - 32) <= 4), () -> "Each pixel covers 128 columns, 16 of them white, so its red is near 32, not "+reds);
  }
  static byte[] jpegWith(int w, int h, IntBinaryOperator rgb, int marker, byte[] segment) throws IOException{
    var img= new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    for (int y= 0; y < h; y += 1){ for (int x= 0; x < w; x += 1){ img.setRGB(x, y, rgb.applyAsInt(x, y)); } }
    var jpeg= new ByteArrayOutputStream();
    ImageIO.write(img, "jpg", jpeg);
    var bs= jpeg.toByteArray();
    var res= new ByteArrayOutputStream();
    res.write(bs, 0, 2);
    res.writeBytes(new byte[]{(byte)0xFF, (byte)marker, (byte)((segment.length + 2)>>8), (byte)(segment.length + 2)});
    res.writeBytes(segment);
    res.write(bs, 2, bs.length - 2);
    return res.toByteArray();
  }
  @Test void aJpegIsReadTurnedByItsExifOrientation(@TempDir Path tmp) throws IOException{
    var data= dataProject(tmp);
    byte[] turnedClockwise= {'E', 'x', 'i', 'f', 0, 0, 'M', 'M', 0, 42, 0, 0, 0, 8, 0, 1, 0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, 6, 0, 0, 0, 0, 0, 0};
    Fs.write(data.resolve("turned.jpg"), jpegWith(32, 16, (x, _) -> x < 16 ? 0xFFFF0000 : 0xFF0000FF, 0xE1, turnedClockwise));
    utils.Err.strCmp("ok\n", dataRun(tmp, """
Turned: Main{s -> base.Block#
  .let img= {s.dataRead(Pub).read(L#("turned.jpg")).readImage(1000)}
  .return{Debug#(Wr#(base.Try#(s.iso.close, {t -> t.dataEdit(Notes).writeImagePng(L#("upright.png"), img)})))}
  }
""", dataLinks(data, data)));
    var png= ImageIO.read(data.resolve("upright.png").toFile());
    var column= Join.of(IntStream.range(0, png.getHeight()).mapToObj(y -> (png.getRGB(8, y)>>16 & 255) > 128 ? "R" : "B"), "", "", "");
    utils.Err.strCmp("16 x 32 "+"R".repeat(16)+"B".repeat(16), png.getWidth()+" x "+png.getHeight()+" "+column);
  }
  @Test void aJpegWithAColorProfileIsReadInSrgb(@TempDir Path tmp) throws IOException{
    var data= dataProject(tmp);
    var profile= new ByteArrayOutputStream();
    profile.writeBytes("ICC_PROFILE".getBytes(UTF_8));
    profile.writeBytes(new byte[]{0, 1, 1});
    profile.writeBytes(ICC_Profile.getInstance(ColorSpace.CS_LINEAR_RGB).getData());
    Fs.write(data.resolve("linear.jpg"), jpegWith(2, 2, (_, _) -> 0xFF808080, 0xE2, profile.toByteArray()));
    utils.Err.strCmp("ok\n", dataRun(tmp, """
Linear: Main{s -> base.Block#
  .let img= {s.dataRead(Pub).read(L#("linear.jpg")).readImage(1000)}
  .return{Debug#(Wr#(base.Try#(s.iso.close, {t -> t.dataEdit(Notes).writeImagePng(L#("srgb.png"), img)})))}
  }
""", dataLinks(data, data)));
    var reds= reds(ImageIO.read(data.resolve("srgb.png").toFile()), 0);
    assertTrue(reds.stream().allMatch(r -> Math.abs(r - 188) <= 6), () -> "A linear gray of 128 is the sRGB gray 188, not "+reds);
  }
  @Test void aResourceDeletedAfterItIsOpenedIsADeterministicError(@TempDir Path tmp){
    var data= dataProject(tmp);
    utils.Err.strCmp("""
ok
The resource "col.Notes/readme" could not be read: NoSuchFileException
""", dataRun(tmp, """
Gone: Main{s -> base.Block#
  .let n= {s.dataEdit(Notes).read(L#("readme"))}
  .do{Debug#(Wr#(base.Try#(s.iso.close, {t -> t.dataEdit(Notes).delete(L#("readme"))})))}
  .return{Debug#(St#(base.Try#(n.iso.close, {m -> m.readStrUtf8 Recover})))}
  }
""", dataLinks(data, data)));
  }
  @Test void aRootFolderThatCannotBeReachedIsANonDeterministicError(@TempDir Path tmp){
    var data= dataProject(tmp);
    utils.Err.strCmp("""
The root folder of the data project mounted on "col.Pub" cannot be reached.
[###]The root folder of the data project mounted on "col.Pub" cannot be reached.[###]""", dataRun(tmp, """
Gone: Main{s -> base.Block#
  .do{Debug#(Ls#(s.try#(s.iso.close, {t -> t.dataRead(Pub).paths({})})))}
  .return{Debug#(Ls#(base.Try#(s.iso.close, {t -> t.dataRead(Pub).paths({})})))}
  }
""", dataLinks(tmp.resolve("gone"), data)));
  }
  @Test void aFolderHoldingANameThatIsNotValidIsANonDeterministicError(@TempDir Path tmp){
    var data= dataProject(tmp);
    Fs.writeUtf8(data.resolve("notes").resolve("Bad.txt"), "bad");
    utils.Err.strCmp("""
The folder at the path "notes" of "col.Pub" holds "Bad.txt", which is not a valid resource or folder name: the data project changed while this program runs.
[###]The folder at the path "notes" of "col.Pub" holds "Bad.txt"[###]""", dataRun(tmp, """
Bad: Main{s -> base.Block#
  .do{Debug#(Ls#(s.try#(s.iso.close, {t -> t.dataRead(Pub).paths(L#("notes"))})))}
  .return{Debug#(Ls#(base.Try#(s.iso.close, {t -> t.dataRead(Pub).paths(L#("notes"))})))}
  }
""", dataLinks(data, data)));
  }
  @Test void aProgramWithNoMountsHasNoDataProject(@TempDir Path tmp){
    utils.Err.strCmp("""
No data project is mounted on "col.Pub".
No dataEdit project is mounted on "col.Notes".
""", dataRun(tmp, """
NoMounts: Main{s -> base.Block#
  .do{Debug#(Ls#(s.try#(s.iso.close, {t -> t.dataRead(Pub).paths({})})))}
  .return{Debug#(Wr#(s.try#(s.iso.close, {t -> t.dataEdit(Notes).writeStrUtf8(L#("a.txt"), "no")})))}
  }
""", List.of()));
  }
  static Map<String,String> files(MainsInfo info){ return info.mains().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().file())); }
  static String pathEncoding(){ return Fs.isWindows() ? "utf16Le" : "utf8"; }
  @Test void aProgramStartedWithoutTheManagerSendsNoMessage(@TempDir Path tmp){
    utils.Err.strCmp("""
The message "Show" is not sent: this program was not started by the Fearless manager.
The message "Register" is not sent: this program was not started by the Fearless manager.
""", dataRun(tmp, """
Caught: { #(a: mut base.Action[base.Void]): base.Str -> a.run{.ok _ -> "ok"; .info i -> i.msg.match{.some m -> m; .empty -> (i.getMap.get "msg").getMsg}} }
NoManager: Main{s -> base.Block#
  .do{Debug#(Caught#(s.try#(s.iso.close, {t -> t.manager.show})))}
  .return{Debug#(Caught#(s.try#(s.iso.close, {t -> t.manager.register("%s".u.%s)})))}
  }
""".formatted(tmp.toAbsolutePath(), pathEncoding()), List.of()));
  }
  @Test void aMessageRunsTheManagerProgramWithTheVerbAndItsArguments(@TempDir Path tmp){
    var log= tmp.resolve("log.txt");
    var manager= tmp.resolve("Manager.java");
    Fs.writeUtf8(manager, """
import java.net.*;
import java.nio.file.*;
import java.util.*;
void main(String[] args) throws Exception{
  var words= List.of(args);
  Files.writeString(Path.of(words.getFirst()), String.join(" ", words.subList(1, words.size()-1))+"\\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
  var out= words.get(1).equals("FearlessCommandForget") ? "Refused\\nThe manager refuses to forget "+words.get(2)+"." : "Applied\\nOutput of "+words.get(1);
  try (var s= new Socket(InetAddress.getLoopbackAddress(), Integer.parseInt(words.getLast().substring("Port:".length())))){ s.getOutputStream().write(out.getBytes()); }
}
""");
    var java= Path.of(System.getProperty("java.home"), "bin", "java");
    var launcher= tmp.resolve(Fs.isWindows() ? "launcher.cmd" : "launcher");
    Fs.writeUtf8(launcher, Fs.isWindows() ? "@\""+java+"\" \""+manager+"\" \""+log+"\" %*\n" : "#!/bin/sh\nexec '"+java+"' '"+manager+"' '"+log+"' \"$@\"\n");
    assertTrue(launcher.toFile().setExecutable(true));
    var path= tmp.toAbsolutePath().toString();
    var base16= "Base16:"+HexFormat.of().withUpperCase().formatHex(path.getBytes(Fs.isWindows() ? UTF_16LE : UTF_8));
    utils.Err.strCmp("""
ok
ok
ok
Output of FearlessCommandRun
Output of FearlessCommandCompile
ok
ok
ok
ok
ok
ok
The manager refuses to forget nope.
The path "relative" of the message "Register" is not an absolute path of this system.
""", dataRun(tmp, """
Caught: { #(a: mut base.Action[base.Void]): base.Str -> a.run{.ok _ -> "ok"; .info i -> i.msg.match{.some m -> m; .empty -> (i.getMap.get "msg").getMsg}} }
Out: { #(a: mut base.Action[base.UStr]): base.Str -> a.run{.ok u -> u.tryGetStr.run{.ok o -> o; .info i -> i.getMsg}; .info i -> i.msg.match{.some m -> m; .empty -> (i.getMap.get "msg").getMsg}} }
Send: Main{s -> base.Block#
  .do{Debug#(Caught#(s.try#(s.iso.close, {t -> t.manager.register("%1$s".u.%2$s)})))}
  .do{Debug#(Caught#(s.try#(s.iso.close, {t -> t.manager.show})))}
  .do{Debug#(Caught#(s.try#(s.iso.close, {t -> t.manager.show "hello"})))}
  .do{Debug#(Out#(s.try#(s.iso.close, {t -> t.manager.run("hello", L#("hello.A", "hello.B"))})))}
  .do{Debug#(Out#(s.try#(s.iso.close, {t -> t.manager.compile "hello"})))}
  .do{Debug#(Caught#(s.try#(s.iso.close, {t -> t.manager.kindDataRead "hello"})))}
  .do{Debug#(Caught#(s.try#(s.iso.close, {t -> t.manager.mount("hello", "hello.Notes", "notes")})))}
  .do{Debug#(Caught#(s.try#(s.iso.close, {t -> t.manager.unmount("hello", "hello.Pub")})))}
  .do{Debug#(Caught#(s.try#(s.iso.close, {t -> t.manager.attenuate("hello", "hello.A", L#("base.NoManagerQuit", "hello.NoGui"))})))}
  .do{Debug#(Caught#(s.try#(s.iso.close, {t -> t.manager.attenuate("hello", "hello.B", {})})))}
  .do{Debug#(Caught#(s.try#(s.iso.close, {t -> t.manager.openWithOrShortcut("%1$s".u.%2$s, "hello", "hello.A")})))}
  .do{Debug#(Caught#(base.Try#(s.iso.close, {t -> t.manager.forget "nope"})))}
  .return{Debug#(Caught#(base.Try#(s.iso.close, {t -> t.manager.register("relative".u.%2$s)})))}
  }
""".formatted(path, pathEncoding()), List.of("-DfearlessManager.launcher="+launcher)));
    utils.Err.strCmp("""
FearlessCommandRegister %1$s
FearlessCommandShow
FearlessCommandShow hello
FearlessCommandRun hello hello.A hello.B
FearlessCommandCompile hello
FearlessCommandKind hello dataRead
FearlessCommandMount hello hello.Notes notes
FearlessCommandMount hello hello.Pub
FearlessCommandAttenuate hello hello.A base.NoManagerQuit hello.NoGui
FearlessCommandAttenuate hello hello.B
FearlessCommandOpenWithOrShortcut %1$s hello hello.A
FearlessCommandForget nope
""".formatted(base16), Fs.readUtf8(log).replace("\r\n", "\n"));
  }
  @Test void aBaseAssetIsReadFromTheStdLibBase(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_col/_rank_app.fear
iii
use base.Main as Main;
Hello:Main{s->base.Debug#(base.IconsConflict.path+" "+(base.IconsConflict.readImage(s.assetRead, 1_000_000).width.getDataType.str))}
""");
    utils.Err.strCmp("fear:/_base/icons/conflict.png 256\n", coordinator(root).main(root, stLib));
  }
  static Path claimsProject(Path tmp, String code) throws IOException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_col/_rank_app.fear
iii
use base.Main as Main;
use base.OpenWith as OpenWith;
use base.Shortcut as Shortcut;
"""+code);
    Fs.ensureDir(root.resolve("_col", "icons"));
    Fs.write(root.resolve("_col", "icons", "foo.png"), onePixelPng());
    return root;
  }
  @Test void mainsClaimingExtensionsRunOnThePortablePath(@TempDir Path tmp) throws Exception{
    var root= claimsProject(tmp, """
Foo:Main, OpenWith[IconsFoo,"foo"], OpenWith[IconsFoo], Shortcut[IconsFoo,`fapp042`], Shortcut[IconsFoo]{s->base.Debug#(`foo`)}
Conflict:lib.Lib, Shortcut[base.IconsConflict]{s->base.Debug#(`conflict`)}
jjj
_lib/_rank_core.fear
iii
Lib:base.Main, base.OpenWith[base.IconsConflict,"fear"]{s->base.Debug#(`lib`)}
""");
    utils.Err.strCmp("conflict\nfoo\n", coordinator(root).main(root, stLib));
  }
  void claimRefused(Path tmp, String code, String expected) throws IOException{
    var root= claimsProject(tmp, code);
    var ex= assertThrows(UserError.class, () -> coordinator(root).compile(root, stLib));
    utils.Err.strCmp(expected, ex.getMessage());
  }
  @Test void wellFormedClaimsAreAccepted(@TempDir Path tmp) throws Exception{
    var root= claimsProject(tmp, """
Foo:Main, OpenWith[IconsFoo,"q"], OpenWith[IconsFoo], Shortcut[IconsFoo,"fapp001"], Shortcut[IconsFoo]{s->base.Debug#(`foo`)}
Two:Main, OpenWith[IconsFoo,"q"], OpenWith[base.IconsConflict,"b"]{s->base.Debug#(`two`)}
P:Main, OpenWith[IconsFoo,"q"], Shortcut[IconsFoo]{}
Q:Main, OpenWith[IconsFoo,"q"], Shortcut[IconsFoo]{}
Paths:P, Q{s->base.Debug#(`paths`)}
Again:P, OpenWith[IconsFoo,"q"]{s->base.Debug#(`again`)}
M:Main{}
Inherited:M, Shortcut[IconsFoo,"fapp042"]{s->base.Debug#(`inherited`)}
Exts:Main, OpenWith[IconsFoo,"fear"], Shortcut[IconsFoo,"fapp123"], OpenWith[IconsFoo,`ffile123`], OpenWith[IconsFoo,"abcdefghij012345"], OpenWith[IconsFoo,"doc"], OpenWith[IconsFoo,`htm`]{s->base.Debug#(`exts`)}
Test:{ #: I -> I: Main, Shortcut[IconsFoo]{s->base.Debug#(`i`)} }
Pre:Main, base.PreLoadIcon[base.IconsConflict], base.PreLoadIcon[IconsFoo]{s->base.Debug#(`pre`)}
""");
    coordinator(root).compile(root, stLib);
    utils.Err.strCmp("""
--- col_manifest.info
{
  "mains": {
    "col.Again": ["_col/_rank_app.fear", [["col.IconsFoo", "_col/icons/foo.png", "", "", ""]], [["col.IconsFoo", "_col/icons/foo.png", "", "", "q"]], [["col.IconsFoo", "_col/icons/foo.png", "", "", ""], ["base.IconsFearless", "icons/fearless.png", "", "", ""]]],
    "col.Exts": ["_col/_rank_app.fear", [["col.IconsFoo", "_col/icons/foo.png", "", "", "fapp123"]], [["col.IconsFoo", "_col/icons/foo.png", "", "", "fear"], ["col.IconsFoo", "_col/icons/foo.png", "", "", "ffile123"], ["col.IconsFoo", "_col/icons/foo.png", "", "", "abcdefghij012345"], ["col.IconsFoo", "_col/icons/foo.png", "", "", "doc"], ["col.IconsFoo", "_col/icons/foo.png", "", "", "htm"]], [["col.IconsFoo", "_col/icons/foo.png", "", "", ""], ["base.IconsFearless", "icons/fearless.png", "", "", ""]]],
    "col.Foo": ["_col/_rank_app.fear", [["col.IconsFoo", "_col/icons/foo.png", "", "", "fapp001"], ["col.IconsFoo", "_col/icons/foo.png", "", "", ""]], [["col.IconsFoo", "_col/icons/foo.png", "", "", "q"], ["col.IconsFoo", "_col/icons/foo.png", "", "", ""]], [["col.IconsFoo", "_col/icons/foo.png", "", "", ""], ["base.IconsFearless", "icons/fearless.png", "", "", ""]]],
    "col.I": ["_col/_rank_app.fear", [["col.IconsFoo", "_col/icons/foo.png", "", "", ""]], [], [["col.IconsFoo", "_col/icons/foo.png", "", "", ""], ["base.IconsFearless", "icons/fearless.png", "", "", ""]]],
    "col.Inherited": ["_col/_rank_app.fear", [["col.IconsFoo", "_col/icons/foo.png", "", "", "fapp042"]], [], [["col.IconsFoo", "_col/icons/foo.png", "", "", ""], ["base.IconsFearless", "icons/fearless.png", "", "", ""]]],
    "col.Paths": ["_col/_rank_app.fear", [["col.IconsFoo", "_col/icons/foo.png", "", "", ""]], [["col.IconsFoo", "_col/icons/foo.png", "", "", "q"]], [["col.IconsFoo", "_col/icons/foo.png", "", "", ""], ["base.IconsFearless", "icons/fearless.png", "", "", ""]]],
    "col.Pre": ["_col/_rank_app.fear", [], [], [["base.IconsFearless", "icons/fearless.png", "", "", ""], ["base.IconsConflict", "icons/conflict.png", "", "", ""], ["col.IconsFoo", "_col/icons/foo.png", "", "", ""]]],
    "col.Two": ["_col/_rank_app.fear", [], [["col.IconsFoo", "_col/icons/foo.png", "", "", "q"], ["base.IconsConflict", "icons/conflict.png", "", "", "b"]], [["base.IconsFearless", "icons/fearless.png", "", "", ""], ["col.IconsFoo", "_col/icons/foo.png", "", "", ""], ["base.IconsConflict", "icons/conflict.png", "", "", ""]]]
  },
  "attenuators": [],
  "dataRead": [],
  "dataEdit": []
}
""", manifests(root));
  }
  @Test void aClaimWithoutMainIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
Assoc:OpenWith[IconsFoo,"foo"]{}
""", """
In file: fear:/_col/_rank_app.fear

004| Assoc:OpenWith[IconsFoo,"foo"]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "Assoc"
Type declaration "Assoc" implements "OpenWith[_,_]".
Only a main can implement "OpenWith[_,_]": type declaration "Assoc" must also implement "Main", directly or through one of its supertypes.
Error 7 WellFormedness"""); }
  @Test void aClaimOnANamedObjectLiteralWithoutMainIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
Test:{ #: I -> I: Shortcut[IconsFoo]{} }
""", """
In file: fear:/_col/_rank_app.fear

004| Test:{ #: I -> I: Shortcut[IconsFoo]{} }
   |                ^^^^^^^^^^^^^^^^^^^^^^^

While inspecting object literal "I"
Object literal "I" implements "Shortcut[_]".
Only a main can implement "Shortcut[_]": object literal "I" must also implement "Main", directly or through one of its supertypes.
Error 7 WellFormedness"""); }
  @Test void aClaimOnAnAnonymousObjectLiteralWithoutMainIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
Test:{ #: Shortcut[IconsFoo] -> { .foo: base.Void -> base.Void } }
""", """
In file: fear:/_col/_rank_app.fear

004| Test:{ #: Shortcut[IconsFoo] -> { .foo: base.Void -> base.Void } }
   |                                 ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting object literal instance of "Shortcut[_]"
Object literal instance of "Shortcut[_]" implements "Shortcut[_]".
Only a main can implement "Shortcut[_]": object literal instance of "Shortcut[_]" must also implement "Main", directly or through one of its supertypes.
Error 7 WellFormedness"""); }
  @Test void aClaimWithoutMainBlamesTheDeclarationAddingIt(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
B:A{}
A:OpenWith[IconsFoo,"q"]{}
M:Main,A{}
""", """
In file: fear:/_col/_rank_app.fear

005| A:OpenWith[IconsFoo,"q"]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "A"
Type declaration "A" implements "OpenWith[_,_]".
Only a main can implement "OpenWith[_,_]": type declaration "A" must also implement "Main", directly or through one of its supertypes.
Error 7 WellFormedness"""); }
  @Test void aClaimWithoutMainInALowerRankPackageIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
Foo:Main{s->base.Debug#(`foo`)}
jjj
_lib/_rank_core.fear
iii
Lib:base.Shortcut[base.IconsConflict]{}
""", """
In file: fear:/_lib/_rank_core.fear

001| Lib:base.Shortcut[base.IconsConflict]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "Lib"
Type declaration "Lib" implements "base.Shortcut[_]".
Only a main can implement "base.Shortcut[_]": type declaration "Lib" must also implement "base.Main", directly or through one of its supertypes.
Error 7 WellFormedness"""); }
  @Test void aClaimWithATypeVariableIconIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
A[X:imm]:Main,OpenWith[X,"q"]{}
""", """
In file: fear:/_col/_rank_app.fear

004| A[X:imm]:Main,OpenWith[X,"q"]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "A[_]"
Type declaration "A[_]" implements `OpenWith[X,"q"]`.
"X" is not a valid icon.
Only concrete types with zero generic arguments are valid icons.
Error 7 WellFormedness"""); }
  @Test void aClaimWithAGenericIconIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
Box[X:imm]:{}
A:Main,Shortcut[Box[IconsFoo]]{}
""", """
In file: fear:/_col/_rank_app.fear

005| A:Main,Shortcut[Box[IconsFoo]]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "A"
Type declaration "A" implements "Shortcut[Box[IconsFoo]]".
"Box[IconsFoo]" is not a valid icon.
Only concrete types with zero generic arguments are valid icons.
Error 7 WellFormedness"""); }
  @Test void aClaimWithATypeVariableExtensionIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
A[X:imm]:Main,OpenWith[IconsFoo,X]{}
""", """
In file: fear:/_col/_rank_app.fear

004| A[X:imm]:Main,OpenWith[IconsFoo,X]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "A[_]"
Type declaration "A[_]" implements "OpenWith[IconsFoo,X]".
The extension "X" is not a string literal type.
An extension is written as a string literal type, like `"ffile347"`, `"xml"` or `"txt"`; or implement "OpenWith[_]" to let the Fearless manager choose one.
Error 7 WellFormedness"""); }
  @Test void aClaimWithATypeNameExtensionIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
A:Main,OpenWith[IconsFoo,IconsFoo]{}
""", """
In file: fear:/_col/_rank_app.fear

004| A:Main,OpenWith[IconsFoo,IconsFoo]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "A"
Type declaration "A" implements "OpenWith[IconsFoo,IconsFoo]".
The extension "IconsFoo" is not a string literal type.
An extension is written as a string literal type, like `"ffile347"`, `"xml"` or `"txt"`; or implement "OpenWith[_]" to let the Fearless manager choose one.
Error 7 WellFormedness"""); }
  @Test void aClaimWithANumberExtensionIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
A:Main,Shortcut[IconsFoo,42]{}
""", """
In file: fear:/_col/_rank_app.fear

004| A:Main,Shortcut[IconsFoo,42]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "A"
Type declaration "A" implements "Shortcut[IconsFoo,42]".
The extension "42" is not a string literal type.
An extension is written as a string literal type, like `"fapp042"`; or implement "Shortcut[_]" to let the Fearless manager choose one.
Error 7 WellFormedness"""); }
  @Test void anUppercaseExtensionIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
A:Main,OpenWith[IconsFoo,"Txt"]{}
""", """
In file: fear:/_col/_rank_app.fear

004| A:Main,OpenWith[IconsFoo,"Txt"]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "A"
Type declaration "A" implements `OpenWith[IconsFoo,"Txt"]`.
"Txt" is not a valid extension.
An extension is 1 to 16 characters, each a lowercase letter "a"-"z" or a digit "0"-"9", with no dot; or implement "OpenWith[_]" to let the Fearless manager choose one.
Error 7 WellFormedness"""); }
  @Test void aMultiDotExtensionIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
A:Main,OpenWith[IconsFoo,"tar.gz"]{}
""", """
In file: fear:/_col/_rank_app.fear

004| A:Main,OpenWith[IconsFoo,"tar.gz"]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "A"
Type declaration "A" implements `OpenWith[IconsFoo,"tar.gz"]`.
"tar.gz" is not a valid extension.
An extension is 1 to 16 characters, each a lowercase letter "a"-"z" or a digit "0"-"9", with no dot; or implement "OpenWith[_]" to let the Fearless manager choose one.
Error 7 WellFormedness"""); }
  @Test void aLeadingDotExtensionIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
A:Main,OpenWith[IconsFoo,".txt"]{}
""", """
In file: fear:/_col/_rank_app.fear

004| A:Main,OpenWith[IconsFoo,".txt"]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "A"
Type declaration "A" implements `OpenWith[IconsFoo,".txt"]`.
".txt" is not a valid extension.
An extension is 1 to 16 characters, each a lowercase letter "a"-"z" or a digit "0"-"9", with no dot; or implement "OpenWith[_]" to let the Fearless manager choose one.
Error 7 WellFormedness"""); }
  @Test void anEmptyExtensionIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
A:Main,OpenWith[IconsFoo,""]{}
""", """
In file: fear:/_col/_rank_app.fear

004| A:Main,OpenWith[IconsFoo,""]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "A"
Type declaration "A" implements `OpenWith[IconsFoo,""]`.
"" is not a valid extension.
An extension is 1 to 16 characters, each a lowercase letter "a"-"z" or a digit "0"-"9", with no dot; or implement "OpenWith[_]" to let the Fearless manager choose one.
Error 7 WellFormedness"""); }
  @Test void aTooLongExtensionIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
A:Main,OpenWith[IconsFoo,"abcdefghij0123456"]{}
""", """
In file: fear:/_col/_rank_app.fear

004| A:Main,OpenWith[IconsFoo,"abcdefghij0123456"]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "A"
Type declaration "A" implements `OpenWith[IconsFoo,"abcd-3456"]`.
"abcdefghij0123456" is not a valid extension.
An extension is 1 to 16 characters, each a lowercase letter "a"-"z" or a digit "0"-"9", with no dot; or implement "OpenWith[_]" to let the Fearless manager choose one.
Error 7 WellFormedness"""); }
  @Test void theFearlessExtensionIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
A:Main,OpenWith[IconsFoo,`fearless`]{}
""", """
In file: fear:/_col/_rank_app.fear

004| A:Main,OpenWith[IconsFoo,`fearless`]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "A"
Type declaration "A" implements "OpenWith[IconsFoo,`fearless`]".
"fearless" is not a valid extension.
The extension "fearless" is reserved; or implement "OpenWith[_]" to let the Fearless manager choose one.
Error 7 WellFormedness"""); }
  @Test void anExtensionClaimedTwiceIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
A:Main,Shortcut[IconsFoo,"fapp042"],Shortcut[base.IconsConflict,"fapp042"]{}
""", """
In file: fear:/_col/_rank_app.fear

004| A:Main,Shortcut[IconsFoo,"fapp042"],Shortcut[base.IconsConflict,"fapp042"]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "A"
Type declaration "A" claims the extension "fapp042" more than once:
both `Shortcut[IconsFoo,"fapp042"]` and `Shortcut[base.IconsConflict,"fapp042"]` claim it.
A main can claim each extension at most once, across all its "OpenWith[_,_]" and "Shortcut[_,_]", since one extension has one icon.
Error 7 WellFormedness"""); }
  @Test void anExtensionClaimedTwiceWithDifferentDelimitersIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
A:Main,OpenWith[IconsFoo,"txt"],OpenWith[IconsFoo,`txt`]{}
""", """
In file: fear:/_col/_rank_app.fear

004| A:Main,OpenWith[IconsFoo,"txt"],OpenWith[IconsFoo,`txt`]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "A"
Type declaration "A" claims the extension "txt" more than once:
both `OpenWith[IconsFoo,"txt"]` and "OpenWith[IconsFoo,`txt`]" claim it.
A main can claim each extension at most once, across all its "OpenWith[_,_]" and "Shortcut[_,_]", since one extension has one icon.
Error 7 WellFormedness"""); }
  @Test void aShortcutOfAnotherExtensionIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
A:Main,Shortcut[IconsFoo,"bar"]{}
""", """
In file: fear:/_col/_rank_app.fear

004| A:Main,Shortcut[IconsFoo,"bar"]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "A"
Type declaration "A" implements `Shortcut[IconsFoo,"bar"]`.
"bar" is not a shortcut extension: a shortcut file only starts its main, so it must not look like a document of another program or a file of the project.
A shortcut extension is "fapp" followed by three digits, like "fapp042"; or implement "Shortcut[_]" to let the Fearless manager choose one.
Error 7 WellFormedness"""); }
  @Test void aShortcutOfAFfileExtensionIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
A:Main,Shortcut[IconsFoo,"ffile042"]{}
""", """
In file: fear:/_col/_rank_app.fear

004| A:Main,Shortcut[IconsFoo,"ffile042"]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "A"
Type declaration "A" implements `Shortcut[IconsFoo,"ffile042"]`.
"ffile042" is not a shortcut extension: a shortcut file only starts its main, so it must not look like a document of another program or a file of the project.
A shortcut extension is "fapp" followed by three digits, like "fapp042"; or implement "Shortcut[_]" to let the Fearless manager choose one.
Error 7 WellFormedness"""); }
  @Test void aShortcutOfAFourDigitFappExtensionIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
A:Main,Shortcut[IconsFoo,"fapp0420"]{}
""", """
In file: fear:/_col/_rank_app.fear

004| A:Main,Shortcut[IconsFoo,"fapp0420"]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "A"
Type declaration "A" implements `Shortcut[IconsFoo,"fapp0420"]`.
"fapp0420" is not a shortcut extension: a shortcut file only starts its main, so it must not look like a document of another program or a file of the project.
A shortcut extension is "fapp" followed by three digits, like "fapp042"; or implement "Shortcut[_]" to let the Fearless manager choose one.
Error 7 WellFormedness"""); }
  @Test void anOpenWithNeverClaimsAFappExtension(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
A:Main,OpenWith[IconsFoo,"fapp042"]{}
""", """
In file: fear:/_col/_rank_app.fear

004| A:Main,OpenWith[IconsFoo,"fapp042"]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "A"
Type declaration "A" implements `OpenWith[IconsFoo,"fapp042"]`.
"fapp042" is a shortcut extension: "fapp" followed by three digits names the shortcut files of the Fearless manager.
Use an extension of the form "ffile" followed by three digits, like "ffile042", or a system extension, like "htm"; or implement "OpenWith[_]" to let the Fearless manager choose one.
Error 7 WellFormedness"""); }
  @Test void aClaimWhoseIconIsNotAnImageFileIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
A:Main,OpenWith[base.Str,"q"]{}
""", """
In file: fear:/_col/_rank_app.fear

004| A:Main,OpenWith[base.Str,"q"]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "col.A"
Type declaration "col.A" implements `base.OpenWith[base.Str,"q"]`.
The icon "base.Str" is not the type generated for an image file.
An icon is the type generated for an image file, like "IconsFoo" for "_pkg/icons/foo.png", or "base.IconsConflict"."""); }
  @Test void aClaimWhoseIconIsImageFileItselfIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
A:Main,Shortcut[base.ImageFile]{}
""", """
In file: fear:/_col/_rank_app.fear

004| A:Main,Shortcut[base.ImageFile]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "col.A"
Type declaration "col.A" implements "base.Shortcut[base.ImageFile]".
The icon "base.ImageFile" is not the type generated for an image file.
An icon is the type generated for an image file, like "IconsFoo" for "_pkg/icons/foo.png", or "base.IconsConflict"."""); }
  @Test void aClaimWhoseIconIsAPlainDeclarationIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
Data:Mid{}
Mid:{}
A:Main,Shortcut[Data]{}
""", """
In file: fear:/_col/_rank_app.fear

006| A:Main,Shortcut[Data]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "col.A"
Type declaration "col.A" implements "base.Shortcut[col.Data]".
The icon "col.Data" is not the type generated for an image file.
An icon is the type generated for an image file, like "IconsFoo" for "_pkg/icons/foo.png", or "base.IconsConflict"."""); }
  @Test void anInheritedClaimWithABadIconBlamesTheDeclarationAddingIt(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
Data:{}
B:A{}
A:Main,Shortcut[Data]{}
""", """
In file: fear:/_col/_rank_app.fear

006| A:Main,Shortcut[Data]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "col.A"
Type declaration "col.A" implements "base.Shortcut[col.Data]".
The icon "col.Data" is not the type generated for an image file.
An icon is the type generated for an image file, like "IconsFoo" for "_pkg/icons/foo.png", or "base.IconsConflict"."""); }
  @Test void aClaimOnAnObjectLiteralMainWithABadIconIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
Data:{}
Test:{ #: I -> I: Main,OpenWith[Data,"q"]{s->base.Debug#(`i`)} }
""", """
In file: fear:/_col/_rank_app.fear

005| Test:{ #: I -> I: Main,OpenWith[Data,"q"]{s->base.Debug#(`i`)} }
   |                ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting object literal "col.I"
Object literal "col.I" implements `base.OpenWith[col.Data,"q"]`.
The icon "col.Data" is not the type generated for an image file.
An icon is the type generated for an image file, like "IconsFoo" for "_pkg/icons/foo.png", or "base.IconsConflict"."""); }
  @Test void aClaimWhoseIconIsAnAbstractImageFileIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
A:Main,OpenWith[Icon,"q"]{}
Icon:Img{}
Img:base.ImageFile{}
""", """
In file: fear:/_col/_rank_app.fear

004| A:Main,OpenWith[Icon,"q"]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "col.A"
Type declaration "col.A" implements `base.OpenWith[col.Icon,"q"]`.
The icon "col.Icon" is not the type generated for an image file.
An icon is the type generated for an image file, like "IconsFoo" for "_pkg/icons/foo.png", or "base.IconsConflict"."""); }
  @Test void aClaimWhoseIconIsATextAssetIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
A:Main,OpenWith[IconsNotes,"q"]{}
jjj
_col/icons/notes.txt
iii
some notes
""", """
In file: fear:/_col/_rank_app.fear

004| A:Main,OpenWith[IconsNotes,"q"]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "col.A"
Type declaration "col.A" implements `base.OpenWith[col.IconsNotes,"q"]`.
The icon "col.IconsNotes" is not the type generated for an image file.
An icon is the type generated for an image file, like "IconsFoo" for "_pkg/icons/foo.png", or "base.IconsConflict"."""); }
  @Test void aHandWrittenImageFileIsNotAnIcon(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
Fake:base.ImageFile{
  .path: base.Str -> "fear:/_col/icons/foo.png";
  .diskPath: base.Str -> "_col/icons/foo.png";
  .zipSteps: base.Str -> "";
  .zipEntry: base.Str -> "";
  .originalFileName: base.Str -> "foo.png";
  }
Foo:Main, OpenWith[Fake,"foo"]{s->base.Debug#(`foo`)}
""", """
In file: fear:/_col/_rank_app.fear

011| Foo:Main, OpenWith[Fake,"foo"]{s->base.Debug#(`foo`)}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "col.Foo"
Type declaration "col.Foo" implements `base.OpenWith[col.Fake,"foo"]`.
The icon "col.Fake" is not the type generated for an image file.
An icon is the type generated for an image file, like "IconsFoo" for "_pkg/icons/foo.png", or "base.IconsConflict"."""); }
  static void editAndTouch(Path file, String from, String to) throws IOException{
    Fs.writeUtf8(file, Fs.readUtf8(file).replace(from, to));
    Fs.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()+500));
  }
  @Test void aClaimReadBackFromTheApiJsonOfALowerRankPackageIsChecked(@TempDir Path tmp) throws Exception{
    var root= claimsProject(tmp, """
Conflict:lib.Lib, Shortcut[base.IconsConflict]{s->base.Debug#(`conflict`)}
jjj
_lib/_rank_core.fear
iii
Lib:base.Main, base.OpenWith[base.IconsConflict,"fear"]{s->base.Debug#(`lib`)}
""");
    utils.Err.strCmp("conflict\n", coordinator(root).main(root, stLib));
    editAndTouch(root.resolve("_col", "_rank_app.fear"), "{s->base.Debug#(`conflict`)}", """
{s->base.Debug#(`conflict`)}
Clash:lib.Lib, OpenWith[IconsFoo,"fear"]{s->base.Debug#(`clash`)}""");
    var ex= assertThrows(UserError.class, () -> coordinator(root).main(root, stLib));
    utils.Err.strCmp("""
In file: fear:/_col/_rank_app.fear

005| Clash:lib.Lib, OpenWith[IconsFoo,"fear"]{s->base.Debug#(`clash`)}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "Clash"
Type declaration "Clash" claims the extension "fear" more than once:
both `OpenWith[IconsFoo,"fear"]` and `OpenWith[base.IconsConflict,"fear"]` claim it.
A main can claim each extension at most once, across all its "OpenWith[_,_]" and "Shortcut[_,_]", since one extension has one icon.
Error 7 WellFormedness""", ex.getMessage());
  }
  static String manifests(Path root){ return MainsInfo.files(root).stream().map(p -> "--- "+p.getFileName()+"\n"+Fs.readUtf8(p)).reduce("", String::concat); }
  @Test void mainsInfoRecordsTheClaimsOfTheTopRankMainsWithTheirIcons(@TempDir Path tmp) throws Exception{
    var root= claimsProject(tmp, """
Foo:Main, OpenWith[IconsFoo,"foo"], OpenWith[lib.IconsLib], Shortcut[base.IconsConflict,`fapp042`], Shortcut[ZInBar]{s->base.Debug#(`foo`)}
jjj
_col/z.zip/in/bar.png
iii
not read by the compiler
jjj
_lib/_rank_core.fear
iii
Lib:base.Main, base.OpenWith[base.IconsConflict,"fear"]{s->base.Debug#(`lib`)}
jjj
_lib/icons/lib.png
iii
not read by the compiler
""");
    coordinator(root).compile(root, stLib);
    utils.Err.strCmp("""
--- col_manifest.info
{
  "mains": {
    "col.Foo": ["_col/_rank_app.fear", [["base.IconsConflict", "icons/conflict.png", "", "", "fapp042"], ["col.ZInBar", "_col/z.zip", "", "in/bar.png", ""]], [["col.IconsFoo", "_col/icons/foo.png", "", "", "foo"], ["lib.IconsLib", "_lib/icons/lib.png", "", "", ""]], [["base.IconsConflict", "icons/conflict.png", "", "", ""], ["col.ZInBar", "_col/z.zip", "", "in/bar.png", ""], ["base.IconsFearless", "icons/fearless.png", "", "", ""], ["col.IconsFoo", "_col/icons/foo.png", "", "", ""], ["lib.IconsLib", "_lib/icons/lib.png", "", "", ""]]]
  },
  "attenuators": [],
  "dataRead": [],
  "dataEdit": []
}
--- lib_manifest.info
{
  "mains": {
    "lib.Lib": ["_lib/_rank_core.fear", [], [["base.IconsConflict", "icons/conflict.png", "", "", "fear"]], [["base.IconsFearless", "icons/fearless.png", "", "", ""], ["base.IconsConflict", "icons/conflict.png", "", "", ""]]]
  },
  "attenuators": [],
  "dataRead": [],
  "dataEdit": []
}
""", manifests(root));
  }
  @Test void aSecondCompileKeepsTheMainsInfoEntriesOfAnUnchangedPackage(@TempDir Path tmp) throws Exception{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_a/_rank_app.fear
iii
use base.Main as Main;
use base.OpenWith as OpenWith;
A:Main, OpenWith[IconsA]{s->base.Debug#(`a`)}
jjj
_a/icons/a.png
iii
not read by the compiler
jjj
_b/_rank_app.fear
iii
use base.Main as Main;
B:Main{s->base.Debug#(`b`)}
""");
    coordinator(root).compile(root, stLib);
    editAndTouch(root.resolve("_b", "_rank_app.fear"), "B:Main", "C:Main{s->base.Debug#(`c`)}\nB:Main");
    coordinator(root).compile(root, stLib);
    utils.Err.strCmp("""
--- a_manifest.info
{
  "mains": {
    "a.A": ["_a/_rank_app.fear", [], [["a.IconsA", "_a/icons/a.png", "", "", ""]], [["base.IconsFearless", "icons/fearless.png", "", "", ""], ["a.IconsA", "_a/icons/a.png", "", "", ""]]]
  },
  "attenuators": [],
  "dataRead": [],
  "dataEdit": []
}
--- b_manifest.info
{
  "mains": {
    "b.B": ["_b/_rank_app.fear", [], [], [["base.IconsFearless", "icons/fearless.png", "", "", ""]]],
    "b.C": ["_b/_rank_app.fear", [], [], [["base.IconsFearless", "icons/fearless.png", "", "", ""]]]
  },
  "attenuators": [],
  "dataRead": [],
  "dataEdit": []
}
""", manifests(root));
  }
  @Test void aDeletedPackageMainsInfoForcesARecompile(@TempDir Path tmp) throws Exception{
    var root= claimsProject(tmp, """
Foo:Main, OpenWith[IconsFoo,"foo"]{s->base.Debug#(`foo`)}
""");
    coordinator(root).compile(root, stLib);
    var text= manifests(root);
    assertEquals(Map.of("col", true), Coordinator.pkgsBuilt(root, baseCache));
    Fs.delete(root.resolve(Coordinator.outDir).resolve("col_manifest.info"));
    assertEquals(Map.of("col", false), Coordinator.pkgsBuilt(root, baseCache));
    assertEquals(Optional.empty(), coordinator(root).mains(root, stLib));
    coordinator(root).compile(root, stLib);
    utils.Err.strCmp(text, manifests(root));
    assertEquals(Map.of("col.Foo", "_col/_rank_app.fear"), files(coordinator(root).mains(root, stLib).orElseThrow()));
  }
  @Test void everyPackageListsItsMainsAndARemovedPackageLosesItsManifest(@TempDir Path tmp) throws Exception{
    var root= claimsProject(tmp, """
Foo:Main{s->base.Debug#(`foo`)}
jjj
_lib/_rank_core.fear
iii
Lib:base.Main, base.OpenWith[base.IconsConflict,"fear"]{s->base.Debug#(`lib`)}
""");
    coordinator(root).compile(root, stLib);
    assertEquals(Map.of("col.Foo", "_col/_rank_app.fear", "lib.Lib", "_lib/_rank_core.fear"), files(coordinator(root).mains(root, stLib).orElseThrow()));
    Fs.rmTree(root.resolve("_col"));
    assertEquals(Map.of("lib", true), Coordinator.pkgsBuilt(root, baseCache));
    utils.Err.strCmp("lib\n", coordinator(root).main(root, stLib));
    utils.Err.strCmp("""
--- lib_manifest.info
{
  "mains": {
    "lib.Lib": ["_lib/_rank_core.fear", [], [["base.IconsConflict", "icons/conflict.png", "", "", "fear"]], [["base.IconsFearless", "icons/fearless.png", "", "", ""], ["base.IconsConflict", "icons/conflict.png", "", "", ""]]]
  },
  "attenuators": [],
  "dataRead": [],
  "dataEdit": []
}
""", manifests(root));
  }
  @Test void deletedManifestsForceARecompileOfEveryPackage(@TempDir Path tmp) throws Exception{
    var root= claimsProject(tmp, """
Foo:Main{s->base.Debug#(`foo`)}
jjj
_more/_rank_app.fear
iii
More:base.Main{s->base.Debug#(`more`)}
""");
    coordinator(root).compile(root, stLib);
    var text= manifests(root);
    for (var p : MainsInfo.files(root)){ Fs.delete(p); }
    assertEquals(Map.of("col", false, "more", false), Coordinator.pkgsBuilt(root, baseCache));
    coordinator(root).compile(root, stLib);
    utils.Err.strCmp(text, manifests(root));
    assertEquals(Map.of("col", true, "more", true), Coordinator.pkgsBuilt(root, baseCache));
  }
  @Test void aBaseApiNewerThanAPackageMakesThatPackageNotBuilt(@TempDir Path tmp) throws Exception{
    var root= claimsProject(tmp, """
Foo:Main{s->base.Debug#(`foo`)}
""");
    coordinator(root).compile(root, stLib);
    assertEquals(Map.of("col", true), Coordinator.pkgsBuilt(root, baseCache));
    var baseApi= baseCache.resolve("base.json");
    var old= Fs.getLastModifiedTime(baseApi);
    try{
      Fs.setLastModifiedTime(baseApi, FileTime.fromMillis(System.currentTimeMillis()+1000));
      assertEquals(Optional.empty(), coordinator(root).mains(root, stLib));
      assertEquals(Map.of("col", false), Coordinator.pkgsBuilt(root, baseCache));
    }
    finally{ Fs.setLastModifiedTime(baseApi, old); }
  }
  void aCorruptedCacheFileIsDeletedWithTheWholeCache(Path tmp, String file, java.util.function.UnaryOperator<byte[]> corrupt, String expected){
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_p/_rank_app999.fear
iii
use base.Main as Main;
A:Main{s->base.Debug#(`hi`)}
""");
    utils.Err.strCmp("hi\n", main(root, List.of()));
    var out= root.resolve(Coordinator.outDir);
    var cached= out.resolve(file);
    Fs.ofV(() -> Fs.write(cached, corrupt.apply(Fs.readAllBytes(cached))));
    var ex= assertThrows(UserError.class, () -> main(root, List.of()));
    utils.Err.strCmp(expected, ex.getMessage());
    assertFalse(Fs.exists(out));
    utils.Err.strCmp("hi\n", main(root, List.of()));
  }
  static String crashedWithCache(String detail){ return "Error: Fearless crashed: either this project's compilation cache was corrupted, or this is a Fearless bug.[###]Fearless deleted this project's .fearless_out folder: compile again to rebuild it.[###]Details:\n"+detail+"[###]"; }
  @Test void aGarbledBuiltFileMakesItsPackageBuildAgain(@TempDir Path tmp){
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_p/_rank_app999.fear
iii
use base.Main as Main;
A:Main{s->base.Debug#(`hi`)}
""");
    utils.Err.strCmp("hi\n", main(root, List.of()));
    var built= root.resolve(Coordinator.outDir).resolve("p.built");
    var good= Fs.of(() -> Fs.readAllBytes(built));
    Fs.ofV(() -> Fs.write(built, new byte[]{(byte)0xff}));
    utils.Err.strCmp("hi\n", main(root, List.of()));
    assertArrayEquals(good, Fs.of(() -> Fs.readAllBytes(built)));
  }
  @Test void aGarbledMainsInfoDeletesTheCache(@TempDir Path tmp){
    aCorruptedCacheFileIsDeletedWithTheWholeCache(tmp, "p_manifest.info", _ -> "[".getBytes(UTF_8), crashedWithCache("utils.Bug"));
  }
  @Test void anApiJsonWithAnUnknownReferenceCapabilityDeletesTheCache(@TempDir Path tmp){
    aCorruptedCacheFileIsDeletedWithTheWholeCache(tmp, "p.json", bs -> new String(bs, UTF_8).replaceFirst("\"imm\"", "\"imx\"").getBytes(UTF_8), crashedWithCache("java.lang.IllegalArgumentException"));
  }
  @Test void anApiJsonWithATypeOfReferenceCapabilityMutHDeletesTheCache(@TempDir Path tmp){
    aCorruptedCacheFileIsDeletedWithTheWholeCache(tmp, "p.json", bs -> new String(bs, UTF_8).replaceFirst("\\[\"p.A\",\"mut\"", "[\"p.A\",\"mutH\"").getBytes(UTF_8), crashedWithCache("java.lang.AssertionError"));
  }
  @Test void anApiJsonWithAnIsoMethodDeletesTheCache(@TempDir Path tmp){
    aCorruptedCacheFileIsDeletedWithTheWholeCache(tmp, "p.json", bs -> new String(bs, UTF_8).replaceFirst("\\[\".main\",\"imm\"", "[\".main\",\"iso\"").getBytes(UTF_8), crashedWithCache("java.lang.AssertionError"));
  }
  @Test void anApiJsonWithARepeatedSupertypeDeletesTheCache(@TempDir Path tmp){
    aCorruptedCacheFileIsDeletedWithTheWholeCache(tmp, "p.json", bs -> new String(bs, UTF_8).replaceFirst("\\[\"base.Main\"\\]", "[\"base.Main\"],[\"base.Main\"]").getBytes(UTF_8), crashedWithCache("java.lang.AssertionError"));
  }
  @Test void aMapDirectiveEditedInAHigherRankPackageMakesItsTargetPackageNotBuilt(@TempDir Path tmp) throws IOException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_pkc/_rank_app100.fear
iii
use base.Str as Str;
C:{.text:Str->"c";}
jjj
_pkd/_rank_app100.fear
iii
use base.Str as Str;
C:{.text:Str->"d";}
jjj
_pkb/_rank_app200.fear
iii
use base.Str as Str;
B:{.text:Str->a.C.text;}
jjj
_pka/_rank_app999.fear
iii
use base.Main as Main;
map a as _pkc in _pkb;
A:Main{s->base.Debug#(pkb.B.text)}
""");
    utils.Err.strCmp("c\n", main(root, List.of()));
    editAndTouch(root.resolve("_pka", "_rank_app999.fear"), "map a as _pkc", "map a as _pkd");
    assertEquals(Map.of("pka", false, "pkb", false, "pkc", false, "pkd", false), Coordinator.pkgsBuilt(root, baseCache));
    utils.Err.strCmp("d\n", main(root, List.of()));
  }
  @Test void aMovedIconOfALowerRankPackageIsFollowed(@TempDir Path tmp) throws Exception{
    var root= claimsProject(tmp, """
Foo:Main, OpenWith[lib.IconsLib]{s->base.Debug#(`foo`)}
jjj
_lib/_rank_core.fear
iii
Lib:{}
jjj
_lib/icons/lib.png
iii
not read by the compiler
""");
    coordinator(root).compile(root, stLib);
    Fs.move(root.resolve("_lib", "icons", "lib.png"), root.resolve("_lib", "icons", "lib.gif"));
    coordinator(root).compile(root, stLib);
    utils.Err.strCmp("""
--- col_manifest.info
{
  "mains": {
    "col.Foo": ["_col/_rank_app.fear", [], [["lib.IconsLib", "_lib/icons/lib.gif", "", "", ""]], [["base.IconsFearless", "icons/fearless.png", "", "", ""], ["lib.IconsLib", "_lib/icons/lib.gif", "", "", ""]]]
  },
  "attenuators": [],
  "dataRead": [],
  "dataEdit": []
}
--- lib_manifest.info
{
  "mains": {},
  "attenuators": [],
  "dataRead": [],
  "dataEdit": []
}
""", manifests(root));
  }
  @Test void aMovedIconIsFollowedInTheManifestOfAPackageBelowTheTopRank(@TempDir Path tmp) throws Exception{
    var root= claimsProject(tmp, """
Foo:Main{s->base.Debug#(`foo`)}
jjj
_mid/_rank_driver.fear
iii
Mid:base.Main, base.OpenWith[lib.IconsLib]{s->base.Debug#(`mid`)}
jjj
_lib/_rank_core.fear
iii
Lib:{}
jjj
_lib/icons/lib.png
iii
not read by the compiler
""");
    coordinator(root).compile(root, stLib);
    Fs.move(root.resolve("_lib", "icons", "lib.png"), root.resolve("_lib", "icons", "lib.gif"));
    coordinator(root).compile(root, stLib);
    utils.Err.strCmp("""
[###]--- mid_manifest.info
{
  "mains": {
    "mid.Mid": ["_mid/_rank_driver.fear", [], [["lib.IconsLib", "_lib/icons/lib.gif", "", "", ""]], [["base.IconsFearless", "icons/fearless.png", "", "", ""], ["lib.IconsLib", "_lib/icons/lib.gif", "", "", ""]]]
  },
  "attenuators": [],
  "dataRead": [],
  "dataEdit": []
}
""", manifests(root));
  }
  @Test void theSelfNameOfAnAnonymousLiteralInADocLinksToTheSectionOfTheLiteral(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_p/_rank_app.fear
iii
use base.Main as Main;
use base.Nat as Nat;
Bar:{ .bar: Nat; .twice: Nat }
Foo:{ .make: Bar -> {'self
  .bar -> 1;
  ///Twice the bar of `self`.
  .twice -> self.bar + (self.bar)
  } }
App:Main{s->base.Debug#(`app`)}
""");
    utils.Err.strCmp("app\n", main(root, List.of()));
    var html= Fs.readUtf8(Coordinator.genJava(root.resolve(Coordinator.outDir)).resolve("p.html"));
    utils.Err.strCmp("[###]id=\"literal-fear_3a__2f__5f_p_2f__5f_rank_5f_app_2e_fear-4-21\"[###]<p>Twice the bar of <code><a href=\"#literal-fear_3a__2f__5f_p_2f__5f_rank_5f_app_2e_fear-4-21\">self</a></code>.</p>[###]", html);
  }
  @Test void theSelfNameOfAnAnonymousLiteralInADocNamesTheLiteral(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_p/_rank_app.fear
iii
use base.Main as Main;
use base.Nat as Nat;
Bar:{ .bar: Nat; .twice: Nat }
Foo:{ .make: Bar -> {'self
  .bar -> 1;
  ///Twice `self.bar`.
  .twice -> self.bar + (self.bar)
  } }
App:Main{s->base.Debug#(`app`)}
""");
    utils.Err.strCmp("app\n", main(root, List.of()));
    var html= Fs.readUtf8(Coordinator.genJava(root.resolve(Coordinator.outDir)).resolve("p.html"));
    utils.Err.strCmp("[###]<p>Twice <code><a href=\"#method-p_2e__5f_AFoo-0-imm-_2e_bar-0\">self.bar</a></code>.</p>[###]", html);
  }
  @Test void aRemovedPackageLosesItsMainsBeforeTheNextCompile(@TempDir Path tmp) throws Exception{
    var root= claimsProject(tmp, """
Foo:Main{s->base.Debug#(`foo`)}
jjj
_lib/_rank_core.fear
iii
Lib:base.Main{s->base.Debug#(`lib`)}
""");
    coordinator(root).compile(root, stLib);
    Fs.rmTree(root.resolve("_col"));
    assertEquals(Map.of("lib.Lib", "_lib/_rank_core.fear"), files(coordinator(root).mains(root, stLib).orElseThrow()));
  }
  @Test void aDeletedPackageJarIsBuiltAgain(@TempDir Path tmp) throws Exception{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_p/_rank_app999.fear
iii
use base.Main as Main;
A:Main{s->base.Debug#(`hi`)}
""");
    utils.Err.strCmp("hi\n", main(root, List.of()));
    Fs.delete(Coordinator.genJava(root.resolve(Coordinator.outDir)).resolve("p.jar"));
    assertEquals(Map.of("p", false), Coordinator.pkgsBuilt(root, baseCache));
    utils.Err.strCmp("hi\n", main(root, List.of()));
  }
  @Test void inheritedClaimsAreListedOnce(@TempDir Path tmp) throws Exception{
    var root= claimsProject(tmp, """
Opener:Main, OpenWith[IconsFoo,"foo"], Shortcut[IconsFoo]{}
Other:Main, Shortcut[IconsFoo], OpenWith[IconsFoo]{}
Foo:Opener, Other, Shortcut[base.IconsConflict,"fapp042"], OpenWith[IconsFoo]{s->base.Debug#(`foo`)}
""");
    coordinator(root).compile(root, stLib);
    utils.Err.strCmp("""
--- col_manifest.info
{
  "mains": {
    "col.Foo": ["_col/_rank_app.fear", [["base.IconsConflict", "icons/conflict.png", "", "", "fapp042"], ["col.IconsFoo", "_col/icons/foo.png", "", "", ""]], [["col.IconsFoo", "_col/icons/foo.png", "", "", ""], ["col.IconsFoo", "_col/icons/foo.png", "", "", "foo"]], [["base.IconsConflict", "icons/conflict.png", "", "", ""], ["col.IconsFoo", "_col/icons/foo.png", "", "", ""], ["base.IconsFearless", "icons/fearless.png", "", "", ""]]]
  },
  "attenuators": [],
  "dataRead": [],
  "dataEdit": []
}
""", manifests(root));
  }
  @Test void literalTypesInSignaturesCompileRunAndAreReadBackFromTheApiJson(@TempDir Path tmp) throws Exception{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_lib/_rank_core.fear
iii
Lit:{ .m(x: "a\\"): base.Str -> x; .n(x: `b"c`): base.Str -> x; .k: 5 -> 5; }
jjj
_col/_rank_app.fear
iii
Hello:base.Main{s->base.Debug#((lib.Lit.m("a\\"))+(lib.Lit.n(`b"c`))+(lib.Lit.k.str))}
""");
    utils.Err.strCmp("a\\b\"c5\n", coordinator(root).main(root, stLib));
    editAndTouch(root.resolve("_col", "_rank_app.fear"), "(lib.Lit.k.str)", "(lib.Lit.k.str)+`!`");
    utils.Err.strCmp("a\\b\"c5!\n", coordinator(root).main(root, stLib));
  }
  @Test void anAssetWhoseNameForgesNoValidTypeIsReportedAgainstTheRealFile(@TempDir Path tmp){
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_col/_rank_app.fear
iii
use base.Main as Main;
Hello:Main{s->base.Debug#("hi")}
jjj
_col/_1.txt
iii
hello
""");
    var ex= assertThrows(UserError.class, () -> coordinator(root).main(root, stLib));
    assertFalse(ex.getMessage().contains(SourceOracleWithAutoload.autoloadFileSuffix), ex.getMessage());
    utils.Err.strCmp("""
Invalid path in this project folder.

Root: [###]
Path: "_col/_1.txt"

What went wrong
- Auto-loading this file would declare a type called "_1".
  That is not a Fearless type name: after any leading underscores, a type name
  must start with an uppercase letter.

How to fix
- Rename the file so that its name starts with a letter.
  Examples: "my_notes.txt" auto-loads as "MyNotes", "_notes.txt" as "_Notes".

We check this so that you[###]
""", ex.getMessage());
  }
  @Test void aRealApiPreservingEditMustNotRebuildTheDependentPackage(@TempDir Path tmp) throws Exception{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_a/_rank_core.fear
iii
use base.Str as Str;
Greeting:{ .hi: Str -> "hi" }
jjj
_b/_rank_app.fear
iii
use base.Main as Main;
use a.Greeting as Greeting;
Hello:Main{s->base.Debug#(Greeting.hi)}
""");
    var c= coordinator(root);
    c.main(root, stLib);
    var out= root.resolve(Coordinator.outDir);
    var aBuilt= Fs.lastModified(out.resolve("a.built"));
    var aJson= Fs.lastModified(out.resolve("a.json"));
    var bBuilt= Fs.lastModified(out.resolve("b.built"));
    editAndTouch(root.resolve("_a/_rank_core.fear"), "\"hi\"", "\"ho\"");
    c.main(root, stLib);
    assertNotEquals(aBuilt, Fs.lastModified(out.resolve("a.built")));
    assertEquals(aJson, Fs.lastModified(out.resolve("a.json")));
    assertEquals(bBuilt, Fs.lastModified(out.resolve("b.built")));
  }
  // Confirms expected behaviour: only the packages at the highest rank number get their Main run.
  @Test void onlyTheHighestRankPackageMainRuns(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_a/_rank_core.fear
iii
use base.Main as Main;
Hello:Main{s->base.Debug#("from core")}
jjj
_z/_rank_app.fear
iii
use base.Main as Main;
Hello:Main{s->base.Debug#("from app")}
""");
    var out= coordinator(root).main(root, stLib);
    utils.Err.strCmp("[###]from app[###]", out);
    assertFalse(out.contains("from core"), out);
  }
  // Confirms expected behaviour: packages tied for that highest rank number all run.
  @Test void allPackagesAtTheHighestRankRun(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_a/_rank_app.fear
iii
use base.Main as Main;
Hello:Main{s->base.Debug#("from a")}
jjj
_z/_rank_app.fear
iii
use base.Main as Main;
Hello:Main{s->base.Debug#("from z")}
""");
    var out= coordinator(root).main(root, stLib);
    utils.Err.strCmp("[###]from a[###]", out);
    utils.Err.strCmp("[###]from z[###]", out);
  }
  @Test void flowsAreDeterministic(){ testOk("testFlowDeterminism"); }
  // The sequential flow returns 0 without evaluating any other element, so the parallel one must
  // return as well, whatever the elements past the decision do: here they never return.
  @Test void flowFirstDoesNotWaitForDivergingElements() throws InterruptedException{
    var project= freshIntegrationRoot("testFlowTermination");
    var c= coordinator(project);
    c.compile(project, stLib);
    var out= new StringBuilder();
    var child= Coordinator.startMain(project, ResolveResource.stLibPath, "term.FirstDoesNotWaitForTheRest", Optional.empty(), Map.of(), Map.of(), List.of(), List.of(), c.sharedClasspath(), out::append);
    var waiter= new Thread(() -> {
      try{ child.await(); }
      catch (InterruptedException _){ child.kill(); }
    });
    waiter.start();
    waiter.join(60_000);
    if (waiter.isAlive()){ child.kill(); fail("first! did not return within 60s while the later elements diverge:\n"+out); }
    utils.Err.strCmp("0\n", out.toString());
  }
  static boolean recursionCompiled;
  String runRecursionMain(String main) throws InterruptedException{
    var root= ResolveResource.integrationTests.resolve("runRecursion");
    if (!recursionCompiled){ compileOk("runRecursion"); recursionCompiled = true; }
    var out= new StringBuilder();
    Coordinator.startMain(root, ResolveResource.stLibPath, main, Optional.empty(), Map.of(), Map.of(), List.of(), List.of(), coordinator(root).sharedClasspath(), out::append).await();
    return out.toString();
  }
  @Test void aDeepRecursionOverflowsTheStackOfMain() throws InterruptedException{
    utils.Err.strCmp("[###]java.lang.StackOverflowError[###]", runRecursionMain("rec.NaiveSum"));
  }
  @Test void runRecursionOnEveryStepIsDeepAndFast() throws InterruptedException{
    var start= System.nanoTime();
    utils.Err.strCmp("500000500000", runRecursionMain("rec.DeepSum"));
    var millis= (System.nanoTime()-start)/1_000_000;
    assertTrue(millis < 20_000, "one million steps took "+millis+"ms");
  }
  @Test void aFamilyNestingTooManyCallsOnOneThreadOverflows() throws InterruptedException{
    utils.Err.strCmp("[###]java.lang.StackOverflowError[###]", runRecursionMain("rec.CrowdedSum"));
  }
  @Test void aFamilyWithTheSameStackAndALowerDepthFits() throws InterruptedException{
    utils.Err.strCmp("5000050000", runRecursionMain("rec.SpreadSum"));
  }
  @Test void aFamilyNestedInAnotherRunsOnItsOwnThreads() throws InterruptedException{
    utils.Err.strCmp("2100000", runRecursionMain("rec.MixedSum"));
  }
  @Test void aDeeplyNestedStructureIsBuiltAndWalked() throws InterruptedException{
    utils.Err.strCmp("1000000", runRecursionMain("rec.NestSize"));
  }
  @Test void errorsReachTheCallerAcrossThreads() throws InterruptedException{
    utils.Err.strCmp("deep boom", runRecursionMain("rec.ErrorsPropagate"));
  }
  @Test void anErrorAcrossThreadsShowsOneContinuousStackTrace() throws InterruptedException{
    utils.Err.strCmp("""
deep boom
mut Fail.then error line: 18 in file _rec/_rank_app.fear
imm True.if(_) error line: 56 in file datatypes/bools.fear
imm Fail#(_) error line: 18 in file _rec/_rank_app.fear
mut Fail# error line: 18 in file _rec/_rank_app.fear
imm _RunRecursion#(_,_) error line: 24 in file run_recursion.fear
imm RunRecursion#(_) error line: 15 in file run_recursion.fear
mut Fail.else error line: 18 in file _rec/_rank_app.fear
imm False.if(_) error line: 67 in file datatypes/bools.fear
imm Fail#(_) error line: 18 in file _rec/_rank_app.fear
mut Fail# error line: 18 in file _rec/_rank_app.fear
imm _RunRecursion#(_,_) error line: 24 in file run_recursion.fear
imm RunRecursion#(_) error line: 15 in file run_recursion.fear
mut Fail.else error line: 18 in file _rec/_rank_app.fear
imm False.if(_) error line: 67 in file datatypes/bools.fear
imm Fail#(_) error line: 18 in file _rec/_rank_app.fear
mut Fail# error line: 18 in file _rec/_rank_app.fear
imm _RunRecursion#(_,_) error line: 24 in file run_recursion.fear
imm RunRecursion#(_) error line: 15 in file run_recursion.fear
mut Fail.else error line: 18 in file _rec/_rank_app.fear
imm False.if(_) error line: 67 in file datatypes/bools.fear
imm Fail#(_) error line: 18 in file _rec/_rank_app.fear
mut Fail# error line: 18 in file _rec/_rank_app.fear
imm _RunRecursion#(_,_) error line: 24 in file run_recursion.fear
imm RunRecursion#(_) error line: 15 in file run_recursion.fear
mut Fail.else error line: 18 in file _rec/_rank_app.fear
imm False.if(_) error line: 67 in file datatypes/bools.fear
imm Fail#(_) error line: 18 in file _rec/_rank_app.fear
imm GluedTrace.main(_) error line: 27 in file _rec/_rank_app.fear
""", runRecursionMain("rec.GluedTrace"));
  }
  @Test void aLiteralInheritsTheSameAbstractMutMethodFromTwoSupertypes(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_p/_rank_app.fear
iii
use base.Main as Main;
use base.Nat as Nat;
use base.Debug as Debug;
A:{ mut .m: Nat; .z: Nat }
B:{ mut .m: Nat }
Mk:{ .mk(k: Nat): A -> Bar:A,B{ .z -> k } }
App:Main{s->Debug#(Mk.mk(1).z)}
""");
    utils.Err.strCmp("1\n", coordinator(root).main(root, stLib));
  }
  @Test void aConcreteMethodImplementsTheAbstractMethodOfAnotherSupertypeInALiteralInsideAMethod(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_p/_rank_app.fear
iii
use base.Main as Main;
use base.Nat as Nat;
use base.Debug as Debug;
A:{ .m: Nat -> 1; .z: Nat }
B:{ .m: Nat }
Mk:{ .mk(k: Nat): A -> Bar:A,B{ .z -> k } }
App:Main{s->Debug#(Mk.mk(2).m + (Mk.mk(2).z))}
""");
    utils.Err.strCmp("3\n", coordinator(root).main(root, stLib));
  }
  @Test void aConcreteMethodImplementsTheAbstractMethodOfAnotherSupertype(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_p/_rank_app.fear
iii
use base.Main as Main;
use base.Nat as Nat;
use base.Debug as Debug;
A:{ .m: Nat -> 1 }
B:{ .m: Nat }
C:A,B{}
App:Main{s->Debug#(C.m)}
""");
    utils.Err.strCmp("1\n", coordinator(root).main(root, stLib));
  }
  @Test void anAbstractTypeInheritsTheNameOfTwoNamedSupertypes(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_p/_rank_app.fear
iii
use base.Main as Main;
use base.Nat as Nat;
use base.Debug as Debug;
A:base.Named{}
B:base.Named{}
C:A,B{ .x: Nat }
D:C{ .x -> 3 }
App:Main{s->Debug#(D.name)}
""");
    utils.Err.strCmp("p.D\n", coordinator(root).main(root, stLib));
  }
  @Test void anAbstractTypeInheritsTheLogOfTwoInMemoryLogSupertypes(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_p/_rank_app.fear
iii
use base.Main as Main;
use base.Nat as Nat;
use base.Debug as Debug;
A:base.InMemoryLog[Nat]{}
B:base.InMemoryLog[Nat]{}
C:A,B{ .x: Nat }
D:C{ .x -> 3 }
App:Main{s->Debug#(D.x)}
""");
    utils.Err.strCmp("3\n", coordinator(root).main(root, stLib));
  }
  @Test void aTypeCannotImplementTwoCaches(@TempDir Path tmp){
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_col/_rank_app.fear
iii
use base.Nat as Nat;
use base.CacheF as CacheF;
Both:CacheF[Nat],CacheF[Nat,Nat]{ # -> 1; #(a) -> a.get + 1 }
""");
    var ex= assertThrows(UserError.class, () -> coordinator(root).compile(root, stLib));
    utils.Err.strCmp("[###]Ambiguous implementation for method \"._uniqueCache\" with 0 parameters.[###]", ex.getMessage());
  }
  @Test void aSubtypeOfACacheHasACacheOfItsOwn(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_col/_rank_app.fear
iii
use base.Main as Main;
use base.Block as Block;
use base.Debug as Debug;
use base.Nat as Nat;
use base.Cache as Cache;
use base.CacheF as CacheF;
A:CacheF[Nat]{ # -> Block#(Debug#("a"), 1) }
B:A,CacheF[Nat]{ # -> Block#(Debug#("b"), 2) }
Hello:Main{s->Block#
  .do{ Debug#(Cache#(A)) }
  .do{ Debug#(Cache#(B)) }
  .do{ Debug#(Cache#(A)) }
  .do{ Debug#(Cache#(B)) }
  .done
  }
""");
    utils.Err.strCmp("""
a
1
b
2
1
2
""", coordinator(root).main(root, stLib));
  }
  @Test void aSelfNameEndingWithAPrimeCompiles(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_p/_rank_app.fear
iii
use base.Main as Main;
use base.Nat as Nat;
use base.Debug as Debug;
B2:{ mut .get: Nat; read .n: Nat }
Mk:{ .m(k: Nat): mut B2 -> mut B2{'me'mut .get -> me'.n + k; read .n -> 5 } }
App:Main{s->Debug#(Mk.m(1).get)}
""");
    utils.Err.strCmp("6\n", coordinator(root).main(root, stLib));
  }
  @Test void aStackTraceNamesTheFearlessTypesMethodsAndLines(){
    utils.Err.strCmp("""
AAAAh
imm Bar.bar error line: 8 in file _hello/_rank_app.fear
imm Foo.foo error line: 7 in file _hello/_rank_app.fear
imm Hello.main(_) error line: 6 in file _hello/_rank_app.fear
""", run("helloStackTraces"));
  }
  @Test void aFailingGetShowsNoActionFrames(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_hello/_rank_app.fear
iii
use base.Main as Main;
use base.Nat as Nat;
use base.Void as Void;
Hello:Main{s->Foo.foo}
Foo:{.foo:Void->Bar.bar(7 .getDiv 0)}
Bar:{.bar(n:Nat):Void->{}}
""");
    utils.Err.strCmp("""
Nat.getDiv: Cannot divide by 0
imm Nat.getDiv(_) error line: [###]
imm Foo.foo error line: 5 in file _hello/_rank_app.fear
imm Hello.main(_) error line: 4 in file _hello/_rank_app.fear
""", coordinator(root).main(root, stLib));
  }
  @Test void anUncaughtStackOverflowShowsFearlessFramesAndNoJavaFrames(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_p/_rank_app.fear
iii
use base.Main as Main;
use base.Nat as Nat;
use base.Debug as Debug;
Rec:{#(n: Nat): Nat -> (n == 0).if{.then -> 0; .else -> n + (this#(n - 1))}}
App:Main{s -> Debug#(Rec#1_000_000)}
""");
    var out= coordinator(root).main(root, stLib);
    utils.Err.strCmp("""
[###]StackOverflowError[###]
imm Rec#(_) error line: 4 in file _p/_rank_app.fear
[###]""", out);
    assertEquals(List.of(), out.lines().filter(l -> l.startsWith("\tat ")).limit(3).toList());
  }
  @Test void aMainEndingWithAnErrorExitsWith1AndTheOtherMainsStillRun(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_col/_rank_app.fear
iii
use base.Main as Main;
A:Main{s->base.Error.msg`boom`}
B:Main{s->base.Debug#(`b`)}
""");
    var c= coordinator(root);
    utils.Err.strCmp("""
boom
imm A.main(_) error line: 2 in file _col/_rank_app.fear
b
""", c.main(root, stLib));
    var cp= Push.of(Coordinator.genJava(root.resolve(Coordinator.outDir)), c.sharedClasspath());
    var out= new StringBuilder();
    assertEquals(1, Coordinator.startProgram(Coordinator.runData(root, c.stdLibBase()), cp, "_col.Main", out::append).await(), out::toString);
    assertEquals(1, Coordinator.startMain(root, c.stdLibBase(), "col.A", Optional.empty(), Map.of(), Map.of(), List.of(), List.of(), c.sharedClasspath(), out::append).await(), out::toString);
    assertEquals(0, Coordinator.startMain(root, c.stdLibBase(), "col.B", Optional.empty(), Map.of(), Map.of(), List.of(), List.of(), c.sharedClasspath(), out::append).await(), out::toString);
  }
  @Test void aMainEndingWithAJavaErrorExitsWith1() throws InterruptedException{
    var root= ResolveResource.integrationTests.resolve("runRecursion");
    if (!recursionCompiled){ compileOk("runRecursion"); recursionCompiled = true; }
    var out= new StringBuilder();
    assertEquals(1, Coordinator.startMain(root, ResolveResource.stLibPath, "rec.NaiveSum", Optional.empty(), Map.of(), Map.of(), List.of(), List.of(), coordinator(root).sharedClasspath(), out::append).await(), out::toString);
    utils.Err.strCmp("[###]java.lang.StackOverflowError[###]", out.toString());
  }
  @Test void aMapMentionsAPackageThatDoesNotExist(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_pka/_rank_app999.fear
iii
use base.Main as Main;
map a as _nonexistentpkg in _pkb;
Hello:Main{s->base.Debug#(pkb.B.text)}
jjj
_pkb/_rank_app200.fear
iii
use base.Str as Str;
B:{.text:Str->a.C.text;}
""");
    var ex= assertThrows(RuntimeException.class, () -> coordinator(root).main(root, stLib));
    utils.Err.strCmp("""
The map refers to the code inside folders named "_nonexistentpkg", but no folder is named "_nonexistentpkg":
- "map a as _nonexistentpkg in _pkb;"; written in fear:/_pka/_rank_app999.fear
Existing folders: "_base", "_pka", "_pkb".
Error 7 WellFormedness
""", ex.getMessage());
  }
  @Test void useOfAHigherRankPackageIsReportedAsARankProblem(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_a/_rank_core345.fear
iii
use base.Main as Main;
use z.Greeting as Greeting;
Hello:Main{s->base.Debug#(Greeting.hi)}
jjj
_z/_rank_app.fear
iii
use base.Str as Str;
Greeting:{ .hi: Str -> "hi" }
""");
    var ex= assertThrows(RuntimeException.class, () -> coordinator(root).main(root, stLib));
    utils.Err.strCmp("""
In file: fear:/_a/_rank_core345.fear

002| use z.Greeting as Greeting;
   |     ^^^^^^^^^^

While inspecting package header
"use" directive refers to the code inside folders named "_z", which has a higher rank than the code inside folders named "_a".
Folders named "_a" have rank "core345" ("_rank_core345.fear") and folders named "_z" have rank "app999" ("_rank_app.fear").
Source code can only refer to code of a lower rank.
Error 7 WellFormedness
""", ex.getMessage());
  }
  @Test void useOfASameRankPackageIsReportedAsARankProblem(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_a/_rank_core.fear
iii
use base.Main as Main;
use z.Greeting as Greeting;
Hello:Main{s->base.Debug#(Greeting.hi)}
jjj
_z/_rank_core999.fear
iii
use base.Str as Str;
Greeting:{ .hi: Str -> "hi" }
""");
    var ex= assertThrows(RuntimeException.class, () -> coordinator(root).main(root, stLib));
    utils.Err.strCmp("""
In file: fear:/_a/_rank_core.fear

002| use z.Greeting as Greeting;
   |     ^^^^^^^^^^

While inspecting package header
"use" directive refers to the code inside folders named "_z", which has the same rank as the code inside folders named "_a".
Folders named "_a" have rank "core999" ("_rank_core.fear") and folders named "_z" have rank "core999" ("_rank_core999.fear").
Source code can only refer to code of a lower rank.
Error 7 WellFormedness
""", ex.getMessage());
  }
  static void versions(Path root){
    FsDsl.materialize(root, """
_webserver_v12/_rank_core100.fear
iii
use base.Str as Str;
Server:{ .version: Str -> "v12" }
jjj
_webserver_v13/_rank_core200.fear
iii
use base.Str as Str;
Server:{ .version: Str -> "v13" }
jjj
_shop/_rank_app100.fear
iii
use base.Str as Str;
map webserver as _webserver_v12 in _shop;
Shop:{ .server: Str -> webserver.Server.version }
jjj
_blog/_rank_app200.fear
iii
use base.Str as Str;
map webserver as _webserver_v13 in _blog;
use webserver.Server as Server;
Blog:{ .server: Str -> Server.version }
jjj
_app/_rank_app999.fear
iii
use base.Main as Main;
Hello:Main{s->base.Debug#("shop "+(shop.Shop.server)+", blog "+(blog.Blog.server))}
""");
  }
  @Test void twoPackagesUseTwoVersionsOfTheSamePackageThroughTheSamePackageName(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    versions(root);
    utils.Err.strCmp("shop v12, blog v13\n", coordinator(root).main(root, stLib));
  }
  @Test void aHigherRankPackageChoosesTheVersionUsedByALowerRankPackage(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_foo_v4/_rank_core100.fear
iii
use base.Str as Str;
Foo:{ .version: Str -> "v4" }
jjj
_foo_v5/_rank_core200.fear
iii
use base.Str as Str;
Foo:{ .version: Str -> "v5" }
jjj
_lib/_rank_driver.fear
iii
use base.Str as Str;
map foo as _foo_v4 in _lib;
Lib:{ .foo: Str -> foo.Foo.version }
jjj
_app/_rank_app.fear
iii
use base.Main as Main;
map foo as _foo_v5 in _lib instead of _foo_v4;
Hello:Main{s->base.Debug#("lib uses "+(lib.Lib.foo))}
""");
    utils.Err.strCmp("lib uses v5\n", coordinator(root).main(root, stLib));
  }
  @Test void aDocReferenceUsesThePackageNameOfTheMap(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_c/_rank_core.fear
iii
use base.Str as Str;
A:{ .name: Str -> "c" }
jjj
_app/_rank_app.fear
iii
use base.Main as Main;
map a as _c in _app;
///Shows the name of `a.A`.
Show:{ .of(x: a.A): base.Str -> x.name }
App:Main{s->base.Debug#(Show.of(a.A))}
""");
    utils.Err.strCmp("c\n", coordinator(root).main(root, stLib));
  }
  @Test void aDocReferenceByTheFolderNameOfAMapIsBroken(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_c/_rank_core.fear
iii
use base.Str as Str;
A:{ .name: Str -> "c" }
jjj
_app/_rank_app.fear
iii
use base.Main as Main;
map a as _c in _app;
///Shows the name of `c.A`.
Show:{ .of(x: a.A): base.Str -> x.name }
App:Main{s->base.Debug#(Show.of(a.A))}
""");
    var ex= assertThrows(RuntimeException.class, () -> coordinator(root).main(root, stLib));
    utils.Err.strCmp("""
[###]Broken reference in a doc comment.[###]
""", ex.getMessage());
  }
  @Test void anOverrideWithoutInsteadOfIsAnError(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_foo_v4/_rank_core100.fear
iii
use base.Str as Str;
Foo:{ .version: Str -> "v4" }
jjj
_foo_v5/_rank_core200.fear
iii
use base.Str as Str;
Foo:{ .version: Str -> "v5" }
jjj
_lib/_rank_driver.fear
iii
use base.Str as Str;
map foo as _foo_v4 in _lib;
Lib:{ .foo: Str -> foo.Foo.version }
jjj
_app/_rank_app.fear
iii
use base.Main as Main;
map foo as _foo_v5 in _lib;
Hello:Main{s->base.Debug#("lib uses "+(lib.Lib.foo))}
""");
    var ex= assertThrows(RuntimeException.class, () -> coordinator(root).main(root, stLib));
    utils.Err.strCmp("""
The "instead of" list of a map must name exactly the folders chosen by the maps of lower rank that the map overrides:
- "map foo as _foo_v5 in _lib;"; written in fear:/_app/_rank_app.fear
These maps of lower rank choose other folders:
- "map foo as _foo_v4 in _lib;"; written in fear:/_lib/_rank_driver.fear
Expected: "map foo as _foo_v5 in _lib instead of _foo_v4;".
Error 7 WellFormedness
""", ex.getMessage());
  }
  @Test void anInsteadOfWithNothingOverriddenIsAnError(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_foo_v5/_rank_core200.fear
iii
use base.Str as Str;
Foo:{ .version: Str -> "v5" }
jjj
_lib/_rank_driver.fear
iii
use base.Str as Str;
Lib:{ .foo: Str -> foo.Foo.version }
jjj
_app/_rank_app.fear
iii
use base.Main as Main;
map foo as _foo_v5 in _lib instead of _foo_v4;
Hello:Main{s->base.Debug#("lib uses "+(lib.Lib.foo))}
""");
    var ex= assertThrows(RuntimeException.class, () -> coordinator(root).main(root, stLib));
    utils.Err.strCmp("""
The "instead of" list of a map must name exactly the folders chosen by the maps of lower rank that the map overrides:
- "map foo as _foo_v5 in _lib instead of _foo_v4;"; written in fear:/_app/_rank_app.fear
No map of lower rank chooses another folder.
Expected: "map foo as _foo_v5 in _lib;".
Error 7 WellFormedness
""", ex.getMessage());
  }
  @Test void anOverrideListsOnlyTheFoldersItRejects(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_foo_v4/_rank_core100.fear
iii
use base.Str as Str;
Foo:{ .version: Str -> "v4" }
jjj
_foo_v5/_rank_core200.fear
iii
use base.Str as Str;
Foo:{ .version: Str -> "v5" }
jjj
_lib/_rank_driver.fear
iii
use base.Str as Str;
map foo as _foo_v4 in _lib;
Lib:{ .foo: Str -> foo.Foo.version }
jjj
_libuser1/_rank_worker100.fear
iii
map foo as _foo_v5 in _lib instead of _foo_v4;
User1:{}
jjj
_libuser2/_rank_worker200.fear
iii
map foo as _foo_v4 in _lib;
User2:{}
jjj
_app/_rank_app.fear
iii
use base.Main as Main;
map foo as _foo_v5 in _lib instead of _foo_v4;
Hello:Main{s->base.Debug#("lib uses "+(lib.Lib.foo))}
""");
    utils.Err.strCmp("lib uses v5\n", coordinator(root).main(root, stLib));
  }
  @Test void withoutAnOverrideALowerRankPackageKeepsItsOwnVersion(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_foo_v4/_rank_core100.fear
iii
use base.Str as Str;
Foo:{ .version: Str -> "v4" }
jjj
_foo_v5/_rank_core200.fear
iii
use base.Str as Str;
Foo:{ .version: Str -> "v5" }
jjj
_lib/_rank_driver.fear
iii
use base.Str as Str;
map foo as _foo_v4 in _lib;
Lib:{ .foo: Str -> foo.Foo.version }
jjj
_app/_rank_app.fear
iii
use base.Main as Main;
map foo as _foo_v5 in _app;
Hello:Main{s->base.Debug#("lib uses "+(lib.Lib.foo)+", app uses "+(foo.Foo.version))}
""");
    utils.Err.strCmp("lib uses v4, app uses v5\n", coordinator(root).main(root, stLib));
  }
  @Test void aVersionFolderNameIsNotAPackageNameWhenAMapNamesIt(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_webserver_v12/_rank_core100.fear
iii
use base.Str as Str;
Server:{ .version: Str -> "v12" }
jjj
_app/_rank_app.fear
iii
use base.Main as Main;
map webserver as _webserver_v12 in _app;
Hello:Main{s->base.Debug#(webserver_v12.Server.version)}
""");
    var ex= assertThrows(RuntimeException.class, () -> coordinator(root).main(root, stLib));
    utils.Err.strCmp("""
In file: fear:/_app/_rank_app.fear

003| Hello:Main{s->base.Debug#(webserver_v12.Server.version)}
   |                           ^^^^^^^^^^^^^^^^^^^^^

While inspecting a type name
Source code inside folders named "_app" can not use package name "webserver_v12".
Did you mean "webserver" ?
Visible package names: "base", "webserver".
Source code inside folders named "_app" uses package name "webserver" to refer to the code inside folders named "_webserver_v12", because of "map webserver as _webserver_v12 in _app", written in fear:/_app/_rank_app.fear.
Error 7 WellFormedness
""", ex.getMessage());
  }
  @Test void aMapOfAnExistingPackage(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_a/_rank_core100.fear
iii
use base.Str as Str;
A:{ .text: Str -> "a" }
jjj
_c/_rank_core200.fear
iii
use base.Str as Str;
A:{ .text: Str -> "c" }
jjj
_b/_rank_app.fear
iii
use base.Main as Main;
map a as _c in _b;
map c as _a in _b;
Hello:Main{s->base.Debug#(a.A.text)}
""");
    var ex= assertThrows(RuntimeException.class, () -> coordinator(root).main(root, stLib));
    utils.Err.strCmp("""
A map can not give package name "a", because folders named "_a" exist:
- "map a as _c in _b;"; written in fear:/_b/_rank_app.fear
With no map, package name "a" refers to the code inside folders named "_a".
Error 7 WellFormedness
""", ex.getMessage());
  }
  @Test void threePackageNamesForOneFolderAreAnError(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_c/_rank_core.fear
iii
use base.Str as Str;
A:{ .text: Str -> "c" }
jjj
_b/_rank_app.fear
iii
use base.Main as Main;
map a as _c in _b;
map d as _c in _b;
map e as _c in _b;
Hello:Main{s->base.Debug#(a.A.text)}
""");
    var ex= assertThrows(RuntimeException.class, () -> coordinator(root).main(root, stLib));
    utils.Err.strCmp("""
Source code inside folders named "_b" can use only one package name to refer to the code inside folders named "_c":
- "map a as _c in _b;"; written in fear:/_b/_rank_app.fear
- "map d as _c in _b;"; written in fear:/_b/_rank_app.fear
- "map e as _c in _b;"; written in fear:/_b/_rank_app.fear
Error 7 WellFormedness
""", ex.getMessage());
  }
  @Test void aMapToTheTargetItself(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_b/_rank_app.fear
iii
use base.Main as Main;
map me as _b in _b;
Hello:Main{s->base.Debug#(`b`)}
""");
    var ex= assertThrows(RuntimeException.class, () -> coordinator(root).main(root, stLib));
    utils.Err.strCmp("""
A map can not refer to the code inside folders named "_b" for source code inside folders named "_b":
- "map me as _b in _b;"; written in fear:/_b/_rank_app.fear
Source code inside folders named "_b" uses the simple names of its own types.
Error 7 WellFormedness
""", ex.getMessage());
  }
  @Test void aMapInAPackageThatDoesNotExist(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_pka/_rank_app999.fear
iii
use base.Main as Main;
map a as _pkc in _pkbb;
Hello:Main{s->base.Debug#(pkb.B.text)}
jjj
_pkb/_rank_app200.fear
iii
use base.Str as Str;
B:{.text:Str->a.C.text;}
jjj
_pkc/_rank_app100.fear
iii
use base.Str as Str;
C:{.text:Str->"c";}
""");
    var ex= assertThrows(RuntimeException.class, () -> coordinator(root).main(root, stLib));
    utils.Err.strCmp("""
The map applies to source code inside folders named "_pkbb", but no folder is named "_pkbb":
- "map a as _pkc in _pkbb;"; written in fear:/_pka/_rank_app999.fear
Did you mean "_pkb" ?
Existing folders: "_pka", "_pkb", "_pkc".
Error 7 WellFormedness
""", ex.getMessage());
  }
  @Test void aMapInBase(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_pka/_rank_app999.fear
iii
use base.Main as Main;
map a as _pka in _base;
Hello:Main{s->base.Debug#(`hi`)}
""");
    var ex= assertThrows(RuntimeException.class, () -> coordinator(root).main(root, stLib));
    utils.Err.strCmp("""
A map can not apply to source code inside folders named "_base":
- "map a as _pka in _base;"; written in fear:/_pka/_rank_app999.fear
Error 7 WellFormedness
""", ex.getMessage());
  }
  @Test void aMapToAHigherRankPackage(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_pka/_rank_app999.fear
iii
use base.Main as Main;
map a as _pka in _pkb;
Hello:Main{s->base.Debug#(pkb.B.text)}
T:{.text:base.Str->"t";}
jjj
_pkb/_rank_app200.fear
iii
use base.Str as Str;
B:{.text:Str->a.T.text;}
""");
    var ex= assertThrows(RuntimeException.class, () -> coordinator(root).main(root, stLib));
    utils.Err.strCmp("""
The map refers to the code inside folders named "_pka", which has a higher rank than the code inside folders named "_pkb":
- "map a as _pka in _pkb;"; written in fear:/_pka/_rank_app999.fear
Folders named "_pkb" have rank "app200" ("_rank_app200.fear") and folders named "_pka" have rank "app999" ("_rank_app999.fear").
Source code can only refer to code of a lower rank.
Error 7 WellFormedness
""", ex.getMessage());
  }
  static final String foos= Stream.of("3", "4", "5", "6").map(v -> """
_foo_vV/_rank_coreV00.fear
iii
use base.Str as Str;
Foo:{ .version: Str -> "vV" }
jjj
""".replace("V", v)).collect(Collectors.joining());
  static String fooLib(String maps){ return "_lib/_rank_driver.fear\niii\nuse base.Str as Str;\n"+maps+"Lib:{ .foo: Str -> foo.Foo.version }\njjj\n"; }
  static String fooUser(String folder, String rank, String maps){ return "_"+folder+"/_rank_"+rank+".fear\niii\n"+maps+"User"+folder+":{}\njjj\n"; }
  static String fooApp(String maps){ return "_app/_rank_app.fear\niii\nuse base.Main as Main;\n"+maps+"Hello:Main{s->base.Debug#(\"lib uses \"+(lib.Lib.foo))}\n"; }
  String mapsRun(Path tmp, String project) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, project);
    return coordinator(root).main(root, stLib);
  }
  String mapsError(Path tmp, String project){
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, project);
    return assertThrows(RuntimeException.class, () -> coordinator(root).main(root, stLib)).getMessage();
  }
  @Test void aMapAgreeingWithALowerRankMapNeedsNoInsteadOf(@TempDir Path tmp) throws InterruptedException{
    utils.Err.strCmp("lib uses v4\n", mapsRun(tmp, foos
      +fooLib("map foo as _foo_v4 in _lib;\n")
      +fooApp("map foo as _foo_v4 in _lib;\n")));
  }
  @Test void aMapListingItsOwnFolderInInsteadOfIsAnError(@TempDir Path tmp){
    utils.Err.strCmp("""
The "instead of" list of a map must name exactly the folders chosen by the maps of lower rank that the map overrides:
- "map foo as _foo_v4 in _lib instead of _foo_v4;"; written in fear:/_app/_rank_app.fear
No map of lower rank chooses another folder.
Expected: "map foo as _foo_v4 in _lib;".
Error 7 WellFormedness
""", mapsError(tmp, foos
      +fooLib("map foo as _foo_v4 in _lib;\n")
      +fooApp("map foo as _foo_v4 in _lib instead of _foo_v4;\n")));
  }
  @Test void anInsteadOfListingAFolderTwiceIsAnError(@TempDir Path tmp){
    utils.Err.strCmp("""
The "instead of" list of a map must name exactly the folders chosen by the maps of lower rank that the map overrides:
- "map foo as _foo_v5 in _lib instead of _foo_v4, _foo_v4;"; written in fear:/_app/_rank_app.fear
These maps of lower rank choose other folders:
- "map foo as _foo_v4 in _lib;"; written in fear:/_lib/_rank_driver.fear
Expected: "map foo as _foo_v5 in _lib instead of _foo_v4;".
Error 7 WellFormedness
""", mapsError(tmp, foos
      +fooLib("map foo as _foo_v4 in _lib;\n")
      +fooApp("map foo as _foo_v5 in _lib instead of _foo_v4, _foo_v4;\n")));
  }
  @Test void anInsteadOfListingAFolderNoMapChoseIsAnError(@TempDir Path tmp){
    utils.Err.strCmp("""
The "instead of" list of a map must name exactly the folders chosen by the maps of lower rank that the map overrides:
- "map foo as _foo_v5 in _lib instead of _foo_v4, _foo_v3;"; written in fear:/_app/_rank_app.fear
These maps of lower rank choose other folders:
- "map foo as _foo_v4 in _lib;"; written in fear:/_lib/_rank_driver.fear
Expected: "map foo as _foo_v5 in _lib instead of _foo_v4;".
Error 7 WellFormedness
""", mapsError(tmp, foos
      +fooLib("map foo as _foo_v4 in _lib;\n")
      +fooApp("map foo as _foo_v5 in _lib instead of _foo_v4, _foo_v3;\n")));
  }
  @Test void aLibraryChangingItsPinBreaksTheOverrideOfTheApplication(@TempDir Path tmp) throws IOException, InterruptedException{
    var root= tmp.resolve("root");
    utils.Err.strCmp("lib uses v5\n", mapsRun(tmp, foos
      +fooLib("map foo as _foo_v4 in _lib;\n")
      +fooApp("map foo as _foo_v5 in _lib instead of _foo_v4;\n")));
    editAndTouch(root.resolve("_lib", "_rank_driver.fear"), "_foo_v4", "_foo_v3");
    var ex= assertThrows(RuntimeException.class, () -> coordinator(root).main(root, stLib));
    utils.Err.strCmp("""
The "instead of" list of a map must name exactly the folders chosen by the maps of lower rank that the map overrides:
- "map foo as _foo_v5 in _lib instead of _foo_v4;"; written in fear:/_app/_rank_app.fear
These maps of lower rank choose other folders:
- "map foo as _foo_v3 in _lib;"; written in fear:/_lib/_rank_driver.fear
Expected: "map foo as _foo_v5 in _lib instead of _foo_v3;".
Error 7 WellFormedness
""", ex.getMessage());
  }
  @Test void removingAnOverrideGivesTheLibraryItsPinBack(@TempDir Path tmp) throws IOException, InterruptedException{
    var root= tmp.resolve("root");
    utils.Err.strCmp("lib uses v5\n", mapsRun(tmp, foos
      +fooLib("map foo as _foo_v4 in _lib;\n")
      +fooApp("map foo as _foo_v5 in _lib instead of _foo_v4;\n")));
    editAndTouch(root.resolve("_app", "_rank_app.fear"), "map foo as _foo_v5 in _lib instead of _foo_v4;\n", "");
    utils.Err.strCmp("lib uses v4\n", coordinator(root).main(root, stLib));
  }
  @Test void twoMapsOfTheSameRankChoosingDifferentFoldersConflict(@TempDir Path tmp){
    utils.Err.strCmp("""
Maps of the same rank give package name "foo" different folders, for source code inside folders named "_lib":
- "map foo as _foo_v5 in _lib instead of _foo_v4;"; written in fear:/_w1/_rank_worker.fear
- "map foo as _foo_v6 in _lib instead of _foo_v4;"; written in fear:/_w2/_rank_worker.fear

How maps work:
- A rank file can have lines like "map name as _folder in _target;".
- With no map, package name "x" refers to the code inside folders named "_x".
- A map in a rank file of higher rank overrides the maps of lower rank, and lists their folders with "instead of".

How to fix it:
- Decide which folder package name "foo" refers to, for source code inside folders named "_lib".
- Add one map in a rank file of higher rank (typically the rank file of your application), with "instead of" listing every folder chosen by the maps of lower rank.
Error 7 WellFormedness
""", mapsError(tmp, foos
      +fooLib("map foo as _foo_v4 in _lib;\n")
      +fooUser("w1", "worker", "map foo as _foo_v5 in _lib instead of _foo_v4;\n")
      +fooUser("w2", "worker", "map foo as _foo_v6 in _lib instead of _foo_v4;\n")
      +fooApp("")));
  }
  @Test void twoMapsOfTheSameRankChoosingTheSameFolderAgree(@TempDir Path tmp) throws InterruptedException{
    utils.Err.strCmp("lib uses v5\n", mapsRun(tmp, foos
      +fooLib("map foo as _foo_v4 in _lib;\n")
      +fooUser("w1", "worker", "map foo as _foo_v5 in _lib instead of _foo_v4;\n")
      +fooUser("w2", "worker", "map foo as _foo_v5 in _lib instead of _foo_v4;\n")
      +fooApp("")));
  }
  @Test void twoMapsOfTheSameRankAgreeingOnTheFolderAreBothChecked(@TempDir Path tmp){
    utils.Err.strCmp("""
The "instead of" list of a map must name exactly the folders chosen by the maps of lower rank that the map overrides:
- "map foo as _foo_v5 in _lib;"; written in fear:/_w2/_rank_worker.fear
These maps of lower rank choose other folders:
- "map foo as _foo_v4 in _lib;"; written in fear:/_lib/_rank_driver.fear
Expected: "map foo as _foo_v5 in _lib instead of _foo_v4;".
Error 7 WellFormedness
""", mapsError(tmp, foos
      +fooLib("map foo as _foo_v4 in _lib;\n")
      +fooUser("w1", "worker", "map foo as _foo_v5 in _lib instead of _foo_v4;\n")
      +fooUser("w2", "worker", "map foo as _foo_v5 in _lib;\n")
      +fooApp("")));
  }
  @Test void aHigherRankMapResolvesAConflictOfTheSameRank(@TempDir Path tmp) throws InterruptedException{
    utils.Err.strCmp("lib uses v6\n", mapsRun(tmp, foos
      +fooLib("map foo as _foo_v4 in _lib;\n")
      +fooUser("w1", "worker", "map foo as _foo_v5 in _lib instead of _foo_v4;\n")
      +fooUser("w2", "worker", "map foo as _foo_v6 in _lib instead of _foo_v4;\n")
      +fooApp("map foo as _foo_v6 in _lib instead of _foo_v4, _foo_v5;\n")));
  }
  @Test void aMapToAFolderOfTheSameRankIsAnError(@TempDir Path tmp){
    utils.Err.strCmp("""
The map refers to the code inside folders named "_foo", which has the same rank as the code inside folders named "_lib":
- "map ff as _foo in _lib;"; written in fear:/_app/_rank_app.fear
Folders named "_lib" have rank "driver999" ("_rank_driver.fear") and folders named "_foo" have rank "driver999" ("_rank_driver999.fear").
Source code can only refer to code of a lower rank.
Error 7 WellFormedness
""", mapsError(tmp, """
_foo/_rank_driver999.fear
iii
Foo:{}
jjj
"""+fooLib("")+fooApp("map ff as _foo in _lib;\n")));
  }
  @Test void aMapCanNotGiveThePackageNameBase(@TempDir Path tmp){
    utils.Err.strCmp("""
A map can not give package name "base", because folders named "_base" exist:
- "map base as _foo_v4 in _lib;"; written in fear:/_lib/_rank_driver.fear
With no map, package name "base" refers to the code inside folders named "_base".
Error 7 WellFormedness
""", mapsError(tmp, foos+fooLib("map base as _foo_v4 in _lib;\nmap foo as _foo_v4 in _lib;\n")+fooApp("")));
  }
  @Test void anOverrideMakingTwoPackageNamesReferToOneFolderIsAnError(@TempDir Path tmp){
    utils.Err.strCmp("""
Source code inside folders named "_lib" can use only one package name to refer to the code inside folders named "_foo_v5":
- "map foo as _foo_v5 in _lib instead of _foo_v4;"; written in fear:/_app/_rank_app.fear
- "map newfoo as _foo_v5 in _lib;"; written in fear:/_lib/_rank_driver.fear
Error 7 WellFormedness
""", mapsError(tmp, foos
      +fooLib("map foo as _foo_v4 in _lib;\nmap newfoo as _foo_v5 in _lib;\n")
      +fooApp("map foo as _foo_v5 in _lib instead of _foo_v4;\n")));
  }
  @Test void anApplicationOverridesOneLibraryAndLeavesAnotherOnItsPin(@TempDir Path tmp) throws InterruptedException{
    utils.Err.strCmp("lib1 uses v5, lib2 uses v4\n", mapsRun(tmp, foos+"""
_lib1/_rank_driver.fear
iii
use base.Str as Str;
map foo as _foo_v4 in _lib1;
Lib:{ .foo: Str -> foo.Foo.version }
jjj
_lib2/_rank_driver.fear
iii
use base.Str as Str;
map foo as _foo_v4 in _lib2;
Lib:{ .foo: Str -> foo.Foo.version }
jjj
_app/_rank_app.fear
iii
use base.Main as Main;
map foo as _foo_v5 in _lib1 instead of _foo_v4;
Hello:Main{s->base.Debug#("lib1 uses "+(lib1.Lib.foo)+", lib2 uses "+(lib2.Lib.foo))}
"""));
  }
  static String describeProject(String appMaps){ return foos+"""
_lib/_rank_driver.fear
iii
use base.Str as Str;
map foo as _foo_v4 in _lib;
Lib:{ .describe(f: foo.Foo): Str -> f.version }
jjj
_app/_rank_app.fear
iii
use base.Main as Main;
"""+appMaps+"""
Hello:Main{s->base.Debug#(lib.Lib.describe(foo.Foo))}
"""; }
  @Test void aLibraryReceivesAFooCreatedByTheApplicationAfterAnOverride(@TempDir Path tmp) throws InterruptedException{
    utils.Err.strCmp("v5\n", mapsRun(tmp, describeProject("map foo as _foo_v5 in _app;\nmap foo as _foo_v5 in _lib instead of _foo_v4;\n")));
  }
  @Test void aLibraryOnItsOwnPinRejectsAFooOfAnotherVersion(@TempDir Path tmp){
    var msg= mapsError(tmp, describeProject("map foo as _foo_v5 in _app;\n"));
    utils.Err.strCmp("""
In file: fear:/_app/_rank_app.fear
[###]foo_v4.Foo[###]
Source code inside folders named "_app" uses package name "foo" to refer to the code inside folders named "_foo_v5", because of "map foo as _foo_v5 in _app", written in fear:/_app/_rank_app.fear.
[###]""", msg);
  }
  @Test void anOverrideBreakingTheLibraryNamesTheRankFileThatChoseTheVersion(@TempDir Path tmp){
    var msg= mapsError(tmp, foos+"""
_foo_v7/_rank_core700.fear
iii
use base.Str as Str;
Foo:{ .version: Str -> "v7"; .legacy: Str -> "legacy" }
jjj
_lib/_rank_driver.fear
iii
use base.Str as Str;
map foo as _foo_v7 in _lib;
Lib:{ .foo: Str -> foo.Foo.legacy }
jjj
"""+fooApp("map foo as _foo_v5 in _lib instead of _foo_v7;\n"));
    utils.Err.strCmp("""
In file: fear:/_lib/_rank_driver.fear
[###]
Source code inside folders named "_lib" uses package name "foo" to refer to the code inside folders named "_foo_v5", because of "map foo as _foo_v5 in _lib", written in fear:/_app/_rank_app.fear.
[###]""", msg);
  }
  @Test void onlyTheWinningMapIsCheckedForInsteadOf(@TempDir Path tmp) throws InterruptedException{
    utils.Err.strCmp("lib uses v6\n", mapsRun(tmp, foos
      +fooLib("map foo as _foo_v4 in _lib;\n")
      +fooUser("mid", "worker", "map foo as _foo_v5 in _lib;\n")
      +fooApp("map foo as _foo_v6 in _lib instead of _foo_v4, _foo_v5;\n")));
  }
  @Test void aMapCanNotGiveTheNameOfAnotherVersionFolder(@TempDir Path tmp){
    utils.Err.strCmp("""
A map can not give package name "foo_v4", because folders named "_foo_v4" exist:
- "map foo_v4 as _foo_v5 in _lib;"; written in fear:/_app/_rank_app.fear
With no map, package name "foo_v4" refers to the code inside folders named "_foo_v4".
Error 7 WellFormedness
""", mapsError(tmp, foos
      +fooLib("map foo as _foo_v4 in _lib;\n")
      +fooApp("map foo_v4 as _foo_v5 in _lib;\n")));
  }
  @Test void aZippedLibraryIsOverriddenByTheApplication(@TempDir Path tmp) throws InterruptedException{
    utils.Err.strCmp("lib uses v5\n", mapsRun(tmp, foos
      +fooLib("map foo as _foo_v4 in _lib;\n").replace("_lib/_rank_driver.fear", "_lib.zip/_rank_driver.fear")
      +fooApp("map foo as _foo_v5 in _lib instead of _foo_v4;\n")));
  }
  @Test void aLibraryLeavesTheVersionToTheApplication(@TempDir Path tmp) throws InterruptedException{
    utils.Err.strCmp("lib uses v5\n", mapsRun(tmp, foos+"""
_lib/_rank_driver.fear
iii
use base.Str as Str;
use foo.Foo as Foo;
Lib:{ .foo: Str -> Foo.version }
jjj
"""+fooApp("map foo as _foo_v5 in _lib;\n")));
  }
  static String insteadOfError(String written, String overridden, String expected){ return """
The "instead of" list of a map must name exactly the folders chosen by the maps of lower rank that the map overrides:
- "%s;"; written in fear:/_app/_rank_app.fear
These maps of lower rank choose other folders:
- "%s;"; written in fear:/_lib/_rank_driver.fear
Expected: "%s;".
Error 7 WellFormedness
""".formatted(written, overridden, expected); }
  @Test void anApplicationFollowsALibraryThroughSeveralReleasesOfItsPin(@TempDir Path tmp) throws IOException, InterruptedException{
    var root= tmp.resolve("root");
    var lib= root.resolve("_lib", "_rank_driver.fear");
    var app= root.resolve("_app", "_rank_app.fear");
    utils.Err.strCmp("lib uses v3\n", mapsRun(tmp, foos+"""
_lib/_rank_driver.fear
iii
use base.Str as Str;
use foo.Foo as Foo;
Lib:{ .foo: Str -> Foo.version }
jjj
"""+fooApp("map foo as _foo_v3 in _lib;\n")));
    editAndTouch(lib, "use foo.Foo as Foo;\n", "map foo as _foo_v4 in _lib;\nuse foo.Foo as Foo;\n");
    utils.Err.strCmp(insteadOfError("map foo as _foo_v3 in _lib", "map foo as _foo_v4 in _lib", "map foo as _foo_v3 in _lib instead of _foo_v4"),
      assertThrows(RuntimeException.class, () -> coordinator(root).main(root, stLib)).getMessage());
    editAndTouch(app, "map foo as _foo_v3 in _lib;", "map foo as _foo_v5 in _lib instead of _foo_v4;");
    utils.Err.strCmp("lib uses v5\n", coordinator(root).main(root, stLib));
    editAndTouch(lib, "_foo_v4", "_foo_v6");
    utils.Err.strCmp(insteadOfError("map foo as _foo_v5 in _lib instead of _foo_v4", "map foo as _foo_v6 in _lib", "map foo as _foo_v5 in _lib instead of _foo_v6"),
      assertThrows(RuntimeException.class, () -> coordinator(root).main(root, stLib)).getMessage());
    editAndTouch(app, "instead of _foo_v4", "instead of _foo_v6");
    utils.Err.strCmp("lib uses v5\n", coordinator(root).main(root, stLib));
    editAndTouch(app, "map foo as _foo_v5 in _lib instead of _foo_v6;\n", "");
    utils.Err.strCmp("lib uses v6\n", coordinator(root).main(root, stLib));
  }
  static String describeLib(String libMaps){ return "_lib/_rank_driver.fear\niii\nuse base.Str as Str;\n"+libMaps+"use foo.Foo as Foo;\nLib:{ .describe(f: Foo): Str -> f.version }\njjj\n"; }
  static final String describeApp= "_app/_rank_app.fear\niii\nuse base.Main as Main;\nmap foo as _foo_v5 in _app;\nmap foo as _foo_v5 in _lib instead of _foo_v4;\nHello:Main{s->base.Debug#(lib.Lib.describe(foo.Foo))}\n";
  @Test void aNewPinOfTheLibraryBreaksTheApplicationUntilItsInsteadOfFollows(@TempDir Path tmp) throws IOException, InterruptedException{
    var root= tmp.resolve("root");
    utils.Err.strCmp("v5\n", mapsRun(tmp, foos+describeLib("map foo as _foo_v4 in _lib;\n")+describeApp));
    editAndTouch(root.resolve("_lib", "_rank_driver.fear"), "_foo_v4", "_foo_v3");
    utils.Err.strCmp(insteadOfError("map foo as _foo_v5 in _lib instead of _foo_v4", "map foo as _foo_v3 in _lib", "map foo as _foo_v5 in _lib instead of _foo_v3"),
      assertThrows(RuntimeException.class, () -> coordinator(root).main(root, stLib)).getMessage());
    editAndTouch(root.resolve("_app", "_rank_app.fear"), "instead of _foo_v4", "instead of _foo_v3");
    utils.Err.strCmp("v5\n", coordinator(root).main(root, stLib));
  }
  @Test void droppingTheOverrideMakesTheLibraryRejectTheFooOfTheApplication(@TempDir Path tmp) throws IOException, InterruptedException{
    var root= tmp.resolve("root");
    utils.Err.strCmp("v5\n", mapsRun(tmp, foos+describeLib("map foo as _foo_v4 in _lib;\n")+describeApp));
    editAndTouch(root.resolve("_app", "_rank_app.fear"), "map foo as _foo_v5 in _lib instead of _foo_v4;\n", "");
    var msg= assertThrows(RuntimeException.class, () -> coordinator(root).main(root, stLib)).getMessage();
    utils.Err.strCmp("""
In file: fear:/_app/_rank_app.fear
[###]
Source code inside folders named "_app" uses package name "foo" to refer to the code inside folders named "_foo_v5", because of "map foo as _foo_v5 in _app", written in fear:/_app/_rank_app.fear.
[###]""", msg);
  }
  @Test void useOfAnOwnTypeInARankFile(@TempDir Path tmp){
    utils.Err.strCmp("""
In file: fear:/_app/_rank_app.fear

002| use app.Helper as H;
   |     ^^^^^^^^^^

While inspecting package header
"use" directive refers to type "Helper" declared inside folders named "_app".
A "use" directive can only refer to types declared in other folders.
Source code inside folders named "_app" uses the simple names of its own types.
Error 7 WellFormedness
""", mapsError(tmp, """
_app/_rank_app.fear
iii
use base.Main as Main;
use app.Helper as H;
Helper:{}
Hello:Main{s->base.Debug#(`hi`)}
"""));
  }
  @Test void useOfAHigherRankPackageThroughAMapName(@TempDir Path tmp){
    utils.Err.strCmp("""
In file: fear:/_lib/_rank_driver.fear

002| use app.Hello as H;
   |     ^^^^^^^^^

While inspecting package header
"use" directive refers to the code inside folders named "_app", which has a higher rank than the code inside folders named "_lib".
Folders named "_lib" have rank "driver999" ("_rank_driver.fear") and folders named "_app" have rank "app999" ("_rank_app.fear").
Source code can only refer to code of a lower rank.
Error 7 WellFormedness
""", mapsError(tmp, foos+"""
_lib/_rank_driver.fear
iii
map foo as _foo_v4 in _lib;
use app.Hello as H;
Lib:{}
jjj
"""+fooApp("")));
  }
  @Test void aMapToAMissingVersionListsTheExistingFolders(@TempDir Path tmp){
    utils.Err.strCmp("""
The map refers to the code inside folders named "_foo_v7", but no folder is named "_foo_v7":
- "map foo as _foo_v7 in _lib instead of _foo_v4;"; written in fear:/_app/_rank_app.fear
Existing folders: "_app", "_base", "_foo_v3", "_foo_v4", "_foo_v5", "_foo_v6", "_lib".
Error 7 WellFormedness
""", mapsError(tmp, foos
      +fooLib("map foo as _foo_v4 in _lib;\n")
      +fooApp("map foo as _foo_v7 in _lib instead of _foo_v4;\n")));
  }
  @Test void aMapInAMissingFolderNamesTheExistingFolders(@TempDir Path tmp){
    utils.Err.strCmp("""
The map applies to source code inside folders named "_libb", but no folder is named "_libb":
- "map foo as _foo_v5 in _libb;"; written in fear:/_app/_rank_app.fear
Did you mean "_lib" ?
Existing folders: "_app", "_foo_v3", "_foo_v4", "_foo_v5", "_foo_v6", "_lib".
Error 7 WellFormedness
""", mapsError(tmp, foos
      +fooLib("map foo as _foo_v4 in _lib;\n")
      +fooApp("map foo as _foo_v5 in _libb;\n")));
  }
  @Test void anErrorInsideAMethodBodyOfAPinnedLibraryNamesThePinAndTheAlias(@TempDir Path tmp){
    utils.Err.strCmp("""
In file: fear:/_lib/_rank_driver.fear

004| Lib:{ .foo(f: Foo): Str -> f.legacy }
   |       ---------------------~^^^^^^^

While inspecting ".foo(_)" line 4
This call to method ".legacy" cannot typecheck.
Method ".legacy" is not declared on type "Foo".

Available methods on type "Foo":
-       .version:Str

Name "Foo" stands for "foo.Foo" because of "use foo.Foo as Foo".
Source code inside folders named "_lib" uses package name "foo" to refer to the code inside folders named "_foo_v4", because of "map foo as _foo_v4 in _lib", written in fear:/_lib/_rank_driver.fear.

Compressed relevant code with inferred types: (compression indicated by `-`)
f.legacy
Error 8 TypeError
""", mapsError(tmp, foos+"""
_lib/_rank_driver.fear
iii
use base.Str as Str;
map foo as _foo_v4 in _lib;
use foo.Foo as Foo;
Lib:{ .foo(f: Foo): Str -> f.legacy }
jjj
"""+fooApp("")));
  }
  static final String fooV7= """
_foo_v7/_rank_core700.fear
iii
use base.Str as Str;
Foo:{ .version: Str -> "v7"; .legacy: Str -> "legacy" }
Legacy:{}
jjj
""";
  static final String fooNote= "Source code inside folders named \"_lib\" uses package name \"foo\" to refer to the code inside folders named \"_foo_v5\", because of \"map foo as _foo_v5 in _lib\", written in fear:/_app/_rank_app.fear.";
  @Test void aMethodErrorInALibraryWithItsOwnPinAndAnOverrideCitesTheApplication(@TempDir Path tmp){
    utils.Err.strCmp("""
In file: fear:/_lib/_rank_driver.fear

004| Lib:{ .foo: Str -> Foo.legacy }
[###]
While inspecting ".foo" line 4
This call to method ".legacy" cannot typecheck.
Method ".legacy" is not declared on type "Foo".

Available methods on type "Foo":
-       .version:Str

Name "Foo" stands for "foo.Foo" because of "use foo.Foo as Foo".
"""+fooNote+"""


Compressed relevant code with inferred types: (compression indicated by `-`)
[###]
Error 8 TypeError
""", mapsError(tmp, foos+fooV7+"""
_lib/_rank_driver.fear
iii
use base.Str as Str;
map foo as _foo_v7 in _lib;
use foo.Foo as Foo;
Lib:{ .foo: Str -> Foo.legacy }
jjj
"""+fooApp("map foo as _foo_v5 in _lib instead of _foo_v7;\n")));
  }
  @Test void anUndeclaredTypeInALibraryWithItsOwnPinAndAnOverrideCitesTheApplication(@TempDir Path tmp){
    utils.Err.strCmp("""
In file: fear:/_lib/_rank_driver.fear

004| Lib:{ .foo: Str -> Foo.version; .old(l: foo.Legacy): Str -> "old" }
[###]
While inspecting a type name
Type "Legacy" is not declared in package "foo".
In scope: "Foo".
[###]
Name "Foo" stands for "foo.Foo" because of "use foo.Foo as Foo".
"""+fooNote+"""

Error 7 WellFormedness
""", mapsError(tmp, foos+fooV7+"""
_lib/_rank_driver.fear
iii
use base.Str as Str;
map foo as _foo_v7 in _lib;
use foo.Foo as Foo;
Lib:{ .foo: Str -> Foo.version; .old(l: foo.Legacy): Str -> "old" }
jjj
"""+fooApp("map foo as _foo_v5 in _lib instead of _foo_v7;\n")));
  }
  static String httpProject(String appMaps){ return Stream.of("1", "2").map(v -> """
_http_vV/_rank_coreV00.fear
iii
use base.Str as Str;
Request:{ .path: Str }
Response:{ .body: Str }
Handler:{ #(r: Request): Response }
Server:{ .serve(h: Handler, path: Str): Str -> "[httpV] "+(h#(Request{ .path -> path }).body) }
jjj
""".replace("V", v)).collect(Collectors.joining())+"""
_blog/_rank_framework100.fear
iii
use base.Str as Str;
map http as _http_v1 in _blog;
use http.Handler as Handler;
use http.Response as Response;
Blog:{ .home: Str -> http.Server.serve(Handler{ r -> Response{ .body -> "blog at "+(r.path) } }, "/blog") }
jjj
_shop/_rank_framework200.fear
iii
use base.Str as Str;
map http as _http_v2 in _shop;
use http.Server as Web;
Shop:{
  .home: Str -> Web.serve(http.Handler{ r -> http.Response{ .body -> "shop at "+(r.path) } }, "/shop");
  .handle(r: http.Request): Str -> "shop handles "+(r.path);
  }
jjj
_app/_rank_app.fear
iii
use base.Main as Main;
map web as _http_v2 in _app;
"""+appMaps+"""
Hello:Main{s->base.Debug#((blog.Blog.home)+", "+(shop.Shop.home)+", "+(shop.Shop.handle(web.Request{ .path -> "/cart" })))}
"""; }
  @Test void twoFrameworksOnTwoVersionsOfAWebLibraryRunTogether(@TempDir Path tmp) throws InterruptedException{
    utils.Err.strCmp("[http1] blog at /blog, [http2] shop at /shop, shop handles /cart\n", mapsRun(tmp, httpProject("")));
  }
  @Test void anApplicationUpgradesTheWebLibraryOfOneFramework(@TempDir Path tmp) throws InterruptedException{
    utils.Err.strCmp("[http2] blog at /blog, [http2] shop at /shop, shop handles /cart\n", mapsRun(tmp, httpProject("map http as _http_v2 in _blog instead of _http_v1;\n")));
  }
  @Test void anApplicationPassingARequestOfTheOtherVersionNamesBothMaps(@TempDir Path tmp){
    var msg= mapsError(tmp, httpProject("").replace("map web as _http_v2 in _app;", "map web as _http_v1 in _app;"));
    utils.Err.strCmp("""
In file: fear:/_app/_rank_app.fear
[###]
Source code inside folders named "_app" uses package name "web" to refer to the code inside folders named "_http_v1", because of "map web as _http_v1 in _app", written in fear:/_app/_rank_app.fear.
[###]""", msg);
  }
  static String jsonLogProject(String appMaps){ return Stream.of("1(:)", "2[:]", "3-:-").map(v -> """
_json_vV/_rank_coreV00.fear
iii
use base.Str as Str;
Json:{ .render(s: Str): Str -> "jVO"+s+"C" }
jjj
_log_vV/_rank_coreV50.fear
iii
use base.Str as Str;
Log:{ .line(s: Str): Str -> "[logV] "+s }
jjj
""".replace("V", v.substring(0, 1)).replace("O", v.substring(1, 2)).replace("C", v.substring(3, 4))).collect(Collectors.joining())+"""
_api/_rank_driver.fear
iii
use base.Str as Str;
map json as _json_v1 in _api;
map log as _log_v2 in _api;
Api:{ .get: Str -> log.Log.line(json.Json.render("api")) }
jjj
_store/_rank_worker.fear
iii
use base.Str as Str;
map json as _json_v2 in _store;
map oldjson as _json_v1 in _store;
map log as _log_v1 in _store;
Store:{ .save: Str -> (log.Log.line(json.Json.render("store")))+" "+(oldjson.Json.render("legacy"))+" "+(api.Api.get) }
jjj
_web/_rank_framework.fear
iii
use base.Str as Str;
map json as _json_v3 in _web;
map logger as _log_v3 in _web;
use logger.Log as Log;
Web:{ .page: Str -> Log.line((json.Json.render("web"))+" "+(store.Store.save)) }
jjj
_app/_rank_app.fear
iii
use base.Main as Main;
map j as _json_v2 in _app;
"""+appMaps+"""
Hello:Main{s->base.Debug#((web.Web.page)+" | "+(j.Json.render("app")))}
"""; }
  @Test void twoLibrariesInThreeVersionsEachThroughThreeLayers(@TempDir Path tmp) throws InterruptedException{
    utils.Err.strCmp("[log3] j3-web- [log1] j2[store] j1(legacy) [log2] j1(api) | j2[app]\n", mapsRun(tmp, jsonLogProject("")));
  }
  @Test void theApplicationUpgradesOneLibraryInOneLayerAndTheOtherLibraryInAnotherLayer(@TempDir Path tmp) throws InterruptedException{
    utils.Err.strCmp("[log3] j3-web- [log1] j3-store- j1(legacy) [log3] j1(api) | j2[app]\n", mapsRun(tmp, jsonLogProject("""
map log as _log_v3 in _api instead of _log_v2;
map json as _json_v3 in _store instead of _json_v2;
""")));
  }
  @Test void theApplicationSwapsTheTwoNamesALayerGivesToTwoVersions(@TempDir Path tmp) throws InterruptedException{
    utils.Err.strCmp("[log3] j3-web- [log1] j1(store) j2[legacy] [log2] j1(api) | j2[app]\n", mapsRun(tmp, jsonLogProject("""
map json as _json_v1 in _store instead of _json_v2;
map oldjson as _json_v2 in _store instead of _json_v1;
""")));
  }
  static String httpTwoNames(String shopExtra, String blogExtra){ return Stream.of("1", "2").map(v -> """
_http_vV/_rank_coreV00.fear
iii
use base.Str as Str;
Request:{ .path: Str }
Response:{ .body: Str }
Handler:{ #(r: Request): Response }
Server:{ .serve(h: Handler, path: Str): Str -> "[httpV] "+(h#(Request{ .path -> path }).body) }
jjj
""".replace("V", v)).collect(Collectors.joining())+"""
_shop/_rank_framework100.fear
iii
use base.Str as Str;
map web as _http_v2 in _shop;
Shop:{
  .home: Str -> web.Server.serve(web.Handler{ r -> web.Response{ .body -> "shop at "+(r.path) } }, "/shop");
"""+shopExtra+"""
  }
jjj
_blog/_rank_framework200.fear
iii
use base.Str as Str;
map http as _http_v2 in _blog;
Blog:{
  .home: Str -> http.Server.serve(http.Handler{ r -> http.Response{ .body -> "blog at "+(r.path) } }, "/blog");
"""+blogExtra+"""
  }
jjj
_app/_rank_app.fear
iii
use base.Main as Main;
Hello:Main{s->base.Debug#((blog.Blog.home)+", "+(shop.Shop.home))}
"""; }
  static final String shopWebNote= "Source code inside folders named \"_shop\" uses package name \"web\" to refer to the code inside folders named \"_http_v2\", because of \"map web as _http_v2 in _shop\", written in fear:/_shop/_rank_framework100.fear.";
  static final String blogHttpNote= "Source code inside folders named \"_blog\" uses package name \"http\" to refer to the code inside folders named \"_http_v2\", because of \"map http as _http_v2 in _blog\", written in fear:/_blog/_rank_framework200.fear.";
  @Test void twoPackagesReachTheSameVersionThroughDifferentNames(@TempDir Path tmp) throws InterruptedException{
    utils.Err.strCmp("[http2] blog at /blog, [http2] shop at /shop\n", mapsRun(tmp, httpTwoNames("", "")));
  }
  @Test void aTypeErrorInsideTheShopUsesTheNameOfTheShop(@TempDir Path tmp){
    utils.Err.strCmp("""
In file: fear:/_shop/_rank_framework100.fear

005|   .bad(r: web.Request): web.Response -> r;
[###]"web.Request"[###]"web.Response"[###]
"""+shopWebNote+"""
[###]Error 8 TypeError
""", mapsError(tmp, httpTwoNames("  .bad(r: web.Request): web.Response -> r;\n", "")));
  }
  @Test void aTypeErrorInsideTheBlogUsesTheNameOfTheBlog(@TempDir Path tmp){
    utils.Err.strCmp("""
In file: fear:/_blog/_rank_framework200.fear

005|   .bad(r: http.Request): http.Response -> r;
[###]"http.Request"[###]"http.Response"[###]
"""+blogHttpNote+"""
[###]Error 8 TypeError
""", mapsError(tmp, httpTwoNames("", "  .bad(r: http.Request): http.Response -> r;\n")));
  }
  @Test void aHiddenVersionInsideTheShopSuggestsTheNameOfTheShop(@TempDir Path tmp){
    utils.Err.strCmp("""
In file: fear:/_shop/_rank_framework100.fear

005|   .old(r: http_v2.Request): Str -> r.path;
   |           ^^^^^^^^^^^^^^^^

While inspecting a type name
Source code inside folders named "_shop" can not use package name "http_v2".
Did you mean "web" ?
Visible package names: "base", "http_v1", "web".
"""+shopWebNote+"""

Error 7 WellFormedness
""", mapsError(tmp, httpTwoNames("  .old(r: http_v2.Request): Str -> r.path;\n", "")));
  }
  @Test void aHiddenVersionInsideTheBlogSuggestsTheNameOfTheBlog(@TempDir Path tmp){
    utils.Err.strCmp("""
In file: fear:/_blog/_rank_framework200.fear

005|   .old(r: http_v2.Request): Str -> r.path;
   |           ^^^^^^^^^^^^^^^^

While inspecting a type name
Source code inside folders named "_blog" can not use package name "http_v2".
Did you mean "http" ?
Visible package names: "base", "http", "http_v1", "shop".
"""+blogHttpNote+"""

Error 7 WellFormedness
""", mapsError(tmp, httpTwoNames("", "  .old(r: http_v2.Request): Str -> r.path;\n")));
  }
  @Test void aZippedVersionIsReachedThroughAMap(@TempDir Path tmp) throws InterruptedException{
    utils.Err.strCmp("lib uses v9\n", mapsRun(tmp, """
_foo_v9.zip/_rank_core100.fear
iii
use base.Str as Str;
Foo:{ .version: Str -> "v9" }
jjj
"""+fooLib("map foo as _foo_v9 in _lib;\n")+fooApp("")));
  }
  @Test void aPackageRenamingBaseReadsItsAutoloadedAsset(@TempDir Path tmp) throws InterruptedException{
    utils.Err.strCmp("REAL NOTE\n", mapsRun(tmp, """
_app/_rank_app.fear
iii
map bb as _base in _app;
NeverRecovers: bb.BadStrUnitRecover { reason, byteOffset, byteLength, rejectedValue -> "should not happen" }
App: bb.Main{s->bb.Debug#(Note.readStrUtf8(s.assetRead, NeverRecovers))}
jjj
_app/note.txt
iii
REAL NOTE
"""));
  }
  @Test void aPackageWithAliasesForBaseTypesReadsItsAutoloadedAssets(@TempDir Path tmp) throws IOException, InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_app/_rank_app.fear
iii
use base.Main as Main;
use base.Str as Str;
use base.TxtFile as TxtFile;
use base.ImageFile as ImageFile;
NeverRecovers: base.BadStrUnitRecover { reason, byteOffset, byteLength, rejectedValue -> "should not happen" }
App: Main{s->base.Debug#(Note.readStrUtf8(s.assetRead, NeverRecovers))}
Picture: {.f:ImageFile -> IconsLogo; .t:TxtFile -> Note; .s:Str -> IconsLogo.path}
jjj
_app/note.txt
iii
REAL NOTE
""");
    Fs.ensureDir(root.resolve("_app", "icons"));
    Fs.write(root.resolve("_app", "icons", "logo.png"), onePixelPng());
    utils.Err.strCmp("REAL NOTE\n", coordinator(root).main(root, stLib));
  }
  @Test void aPackageRenamingBaseWithAnImageAssetCompiles(@TempDir Path tmp) throws IOException, InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_app/_rank_app.fear
iii
map bb as _base in _app;
use bb.Str as Str;
App: bb.Main{s->bb.Debug#(IconsLogo.originalFileName)}
""");
    Fs.ensureDir(root.resolve("_app", "icons"));
    Fs.write(root.resolve("_app", "icons", "logo.png"), onePixelPng());
    utils.Err.strCmp("logo.png\n", coordinator(root).main(root, stLib));
  }
  @Test void aLowerRankPackageRenamingBaseHasAnAutoloadedAsset(@TempDir Path tmp) throws InterruptedException{
    utils.Err.strCmp("greeting.txt\n", mapsRun(tmp, """
_lib/_rank_core.fear
iii
map bb as _base in _lib;
Lib:{ .name: bb.Str -> Greeting.originalFileName }
jjj
_lib/greeting.txt
iii
hello
jjj
_app/_rank_app.fear
iii
use base.Main as Main;
Hello:Main{s->base.Debug#(lib.Lib.name)}
"""));
  }
  @Test void docExamplesInAPackageRenamingBase(@TempDir Path tmp){
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_p/_rank_app.fear
iii
map bb as _base in _p;
Calc:{
  ///Adds one.
  //> .check{Calc.inc(1) .assertEq 2}
  .inc(n: bb.Nat): bb.Nat -> n + 1;
  }
App:bb.Main{s->bb.Debug#("hi")}
""");
    utils.Err.strCmp("hi\n", main(root, List.of()));
    utils.Err.strCmp("""
//Autogenerated from the doc examples of package p; any edit is overwritten

_Calc_Examples: bb.Test {::
  .test bb.Test{::
.check{Calc.inc(1) .assertEq 2}
  }
  }

AllAutoTests_p: bb.UnitTests {::
  .test _Calc_Examples
  }
""", Fs.readUtf8(generatedTests(root)));
  }
  @Test void docExamplesInAPackageWithAliasesForTest(@TempDir Path tmp){
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_p/_rank_app.fear
iii
use base.Main as Main;
use base.Nat as Nat;
use base.Test as Test;
use base.UnitTests as UnitTests;
Calc:{
  ///Adds one.
  //> .check{Calc.inc(1) .assertEq 3}
  .inc(n: Nat): Nat -> n + 1;
  }
App:Main{s->base.Debug#("hi")}
""");
    utils.Err.strCmp("""
Test failure _Calc_Examples at line: [###] in file: _p/auto_tests/p_test.fear
[###]
hi
""", main(root, List.of()));
    utils.Err.strCmp("""
//Autogenerated from the doc examples of package p; any edit is overwritten

_Calc_Examples: Test {::
  .test Test{::
.check{Calc.inc(1) .assertEq 3}
  }
  }

AllAutoTests_p: UnitTests {::
  .test _Calc_Examples
  }
""", Fs.readUtf8(generatedTests(root)));
  }
  @Test void aDestructuringParameterInAPackageRenamingBase(@TempDir Path tmp) throws InterruptedException{
    utils.Err.strCmp("xy\n", mapsRun(tmp, """
_app/_rank_app.fear
iii
map bb as _base in _app;
use bb.Destruct as D;
P:{ .x:bb.Str -> "x"; .y:bb.Str -> "y" }
Q:{ .m({.x, .y}:P):bb.Str -> x+y }
App:bb.Main{s->bb.Debug#(Q.m(P))}
"""));
  }
  @Test void theMainsOfAnUncompiledProjectWithAHeaderErrorAreUnknown(@TempDir Path tmp){
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_app/_rank_app.fear
iii
map bb as _base in _app;
use base.Str as Str;
App:bb.Main{s->bb.Debug#("hi")}
""");
    assertEquals(Optional.empty(), coordinator(root).mains(root, stLib));
    var ex= assertThrows(RuntimeException.class, () -> coordinator(root).main(root, stLib));
    utils.Err.strCmp("""
In file: fear:/_app/_rank_app.fear

002| use base.Str as Str;
   |     ^^^^^^^^

While inspecting package header
Source code inside folders named "_app" can not use package name "base".
Did you mean "bb" ?
Visible package names: "bb".
Source code inside folders named "_app" uses package name "bb" to refer to the code inside folders named "_base", because of "map bb as _base in _app", written in fear:/_app/_rank_app.fear.
Error 7 WellFormedness
""", ex.getMessage());
  }
  @Test void movingAPinFromTheLibraryToTheApplicationKeepsTheVersionAndTheNoteCitesTheApplication(@TempDir Path tmp) throws IOException, InterruptedException{
    var root= tmp.resolve("root");
    utils.Err.strCmp("lib uses v4\n", mapsRun(tmp, foos+fooLib("map foo as _foo_v4 in _lib;\n")+fooApp("")));
    editAndTouch(root.resolve("_lib", "_rank_driver.fear"), "map foo as _foo_v4 in _lib;\n", "");
    editAndTouch(root.resolve("_app", "_rank_app.fear"), "use base.Main as Main;\n", "use base.Main as Main;\nmap foo as _foo_v4 in _lib;\n");
    utils.Err.strCmp("lib uses v4\n", coordinator(root).main(root, stLib));
    editAndTouch(root.resolve("_lib", "_rank_driver.fear"), "foo.Foo.version", "foo.Foo.legacy");
    utils.Err.strCmp("""
In file: fear:/_lib/_rank_driver.fear
[###]
Source code inside folders named "_lib" uses package name "foo" to refer to the code inside folders named "_foo_v4", because of "map foo as _foo_v4 in _lib", written in fear:/_app/_rank_app.fear.
[###]""", assertThrows(RuntimeException.class, () -> coordinator(root).main(root, stLib)).getMessage());
  }
  @Test void movingAnOverrideFromAMiddleLayerToTheApplicationKeepsTheVersionAndTheNoteCitesTheApplication(@TempDir Path tmp) throws IOException, InterruptedException{
    var root= tmp.resolve("root");
    utils.Err.strCmp("lib uses v7\n", mapsRun(tmp, foos+fooV7
      +fooLib("map foo as _foo_v4 in _lib;\n")
      +fooUser("mid", "worker", "map foo as _foo_v7 in _lib instead of _foo_v4;\n")
      +fooApp("")));
    editAndTouch(root.resolve("_mid", "_rank_worker.fear"), "map foo as _foo_v7 in _lib instead of _foo_v4;\n", "");
    editAndTouch(root.resolve("_app", "_rank_app.fear"), "use base.Main as Main;\n", "use base.Main as Main;\nmap foo as _foo_v7 in _lib instead of _foo_v4;\n");
    utils.Err.strCmp("lib uses v7\n", coordinator(root).main(root, stLib));
    editAndTouch(root.resolve("_lib", "_rank_driver.fear"), "foo.Foo.version", "foo.Foo.missing");
    utils.Err.strCmp("""
In file: fear:/_lib/_rank_driver.fear
[###]
Source code inside folders named "_lib" uses package name "foo" to refer to the code inside folders named "_foo_v7", because of "map foo as _foo_v7 in _lib", written in fear:/_app/_rank_app.fear.
[###]""", assertThrows(RuntimeException.class, () -> coordinator(root).main(root, stLib)).getMessage());
  }
  @Test void reorderingTheInsteadOfListKeepsTheOutputAndAWrongListIsAnErrorUntilFixed(@TempDir Path tmp) throws IOException, InterruptedException{
    var root= tmp.resolve("root");
    var app= root.resolve("_app", "_rank_app.fear");
    utils.Err.strCmp("lib uses v6\n", mapsRun(tmp, foos
      +fooLib("map foo as _foo_v4 in _lib;\n")
      +fooUser("mid", "worker", "map foo as _foo_v5 in _lib instead of _foo_v4;\n")
      +fooApp("map foo as _foo_v6 in _lib instead of _foo_v4, _foo_v5;\n")));
    editAndTouch(app, "instead of _foo_v4, _foo_v5", "instead of _foo_v5, _foo_v4");
    utils.Err.strCmp("lib uses v6\n", coordinator(root).main(root, stLib));
    editAndTouch(app, "instead of _foo_v5, _foo_v4", "instead of _foo_v5");
    utils.Err.strCmp("""
The "instead of" list of a map must name exactly the folders chosen by the maps of lower rank that the map overrides:
- "map foo as _foo_v6 in _lib instead of _foo_v5;"; written in fear:/_app/_rank_app.fear
These maps of lower rank choose other folders:
- "map foo as _foo_v4 in _lib;"; written in fear:/_lib/_rank_driver.fear
- "map foo as _foo_v5 in _lib instead of _foo_v4;"; written in fear:/_mid/_rank_worker.fear
Expected: "map foo as _foo_v6 in _lib instead of _foo_v4, _foo_v5;".
Error 7 WellFormedness
""", assertThrows(RuntimeException.class, () -> coordinator(root).main(root, stLib)).getMessage());
    editAndTouch(app, "instead of _foo_v5", "instead of _foo_v4, _foo_v5");
    utils.Err.strCmp("lib uses v6\n", coordinator(root).main(root, stLib));
  }
  @Test void removingAMapOfTheApplicationMakesItsPackageNameUnknown(@TempDir Path tmp) throws IOException, InterruptedException{
    var root= tmp.resolve("root");
    utils.Err.strCmp("v5\n", mapsRun(tmp, foos+"""
_app/_rank_app.fear
iii
use base.Main as Main;
map f as _foo_v5 in _app;
Hello:Main{s->base.Debug#(f.Foo.version)}
"""));
    editAndTouch(root.resolve("_app", "_rank_app.fear"), "map f as _foo_v5 in _app;\n", "");
    utils.Err.strCmp("""
In file: fear:/_app/_rank_app.fear
[###]
Source code inside folders named "_app" can not use package name "f".
[###]""", assertThrows(RuntimeException.class, () -> coordinator(root).main(root, stLib)).getMessage());
  }
  @Test void aNewVersionFolderIsUsedOnceAMapChoosesIt(@TempDir Path tmp) throws IOException, InterruptedException{
    var root= tmp.resolve("root");
    utils.Err.strCmp("lib uses v4\n", mapsRun(tmp, foos+fooLib("map foo as _foo_v4 in _lib;\n")+fooApp("")));
    Fs.ensureDir(root.resolve("_foo_v7"));
    Fs.writeUtf8(root.resolve("_foo_v7", "_rank_core700.fear"), "use base.Str as Str;\nFoo:{ .version: Str -> \"v7\" }\n");
    utils.Err.strCmp("lib uses v4\n", coordinator(root).main(root, stLib));
    editAndTouch(root.resolve("_app", "_rank_app.fear"), "use base.Main as Main;\n", "use base.Main as Main;\nmap foo as _foo_v7 in _lib instead of _foo_v4;\n");
    utils.Err.strCmp("lib uses v7\n", coordinator(root).main(root, stLib));
  }
  @Test void aMapOfTheSameRankAddedOnARebuildConflictsUntilAHigherRankMapDecides(@TempDir Path tmp) throws IOException, InterruptedException{
    var root= tmp.resolve("root");
    utils.Err.strCmp("lib uses v5\n", mapsRun(tmp, foos
      +fooLib("map foo as _foo_v4 in _lib;\n")
      +fooUser("w1", "worker", "map foo as _foo_v5 in _lib instead of _foo_v4;\n")
      +fooUser("w2", "worker", "")
      +fooApp("")));
    editAndTouch(root.resolve("_w2", "_rank_worker.fear"), "Userw2", "map foo as _foo_v6 in _lib instead of _foo_v4;\nUserw2");
    utils.Err.strCmp("""
Maps of the same rank give package name "foo" different folders, for source code inside folders named "_lib":
- "map foo as _foo_v5 in _lib instead of _foo_v4;"; written in fear:/_w1/_rank_worker.fear
- "map foo as _foo_v6 in _lib instead of _foo_v4;"; written in fear:/_w2/_rank_worker.fear

How maps work:
- A rank file can have lines like "map name as _folder in _target;".
- With no map, package name "x" refers to the code inside folders named "_x".
- A map in a rank file of higher rank overrides the maps of lower rank, and lists their folders with "instead of".

How to fix it:
- Decide which folder package name "foo" refers to, for source code inside folders named "_lib".
- Add one map in a rank file of higher rank (typically the rank file of your application), with "instead of" listing every folder chosen by the maps of lower rank.
Error 7 WellFormedness
""", assertThrows(RuntimeException.class, () -> coordinator(root).main(root, stLib)).getMessage());
    editAndTouch(root.resolve("_app", "_rank_app.fear"), "use base.Main as Main;\n", "use base.Main as Main;\nmap foo as _foo_v6 in _lib instead of _foo_v4, _foo_v5;\n");
    utils.Err.strCmp("lib uses v6\n", coordinator(root).main(root, stLib));
  }
  @Test void useOfATypeMissingFromThePinnedVersion(@TempDir Path tmp){
    utils.Err.strCmp("""
In file: fear:/_lib/_rank_driver.fear

002| use foo.Legacy as Legacy;
   |     ^^^^^^^^^^

While inspecting package header
"use" directive refers to type "Legacy", which is not declared in package "foo".

Source code inside folders named "_lib" uses package name "foo" to refer to the code inside folders named "_foo_v4", because of "map foo as _foo_v4 in _lib", written in fear:/_lib/_rank_driver.fear.
Error 7 WellFormedness
""", mapsError(tmp, foos+fooV7+"""
_lib/_rank_driver.fear
iii
map foo as _foo_v4 in _lib;
use foo.Legacy as Legacy;
Lib:{}
jjj
"""+fooApp("")));
  }
  @Test void useOfASimpleNameInARankFileWithAMap(@TempDir Path tmp){
    utils.Err.strCmp("""
In file: fear:/_lib/_rank_driver.fear

002| use Foo as Foo;
   |     ^^^

While inspecting package header
"use" directive refers to "Foo", which is not a full type name.
A "use" directive refers to a type of another package by its full type name, as in "use base.Str as Str;".
Error 7 WellFormedness
""", mapsError(tmp, foos+"""
_lib/_rank_driver.fear
iii
map foo as _foo_v4 in _lib;
use Foo as Foo;
Lib:{}
jjj
"""+fooApp("")));
  }
  @Test void aSimpleNameOfAPinnedVersionSuggestsTheFullTypeName(@TempDir Path tmp){
    utils.Err.strCmp("""
In file: fear:/_lib/_rank_driver.fear

003| Lib:{ .foo: Str -> Foo.version }
   |                    ^^^^

While inspecting a type name
Type "Foo" is not declared in package "lib" and is not made visible via "use".
In scope: "Lib", "Str".
Did you mean "foo.Foo" or "foo_v3.Foo" or "foo_v5.Foo" or "foo_v6.Foo" ?
Add a "use" or write the full type name.

Source code inside folders named "_lib" uses package name "foo" to refer to the code inside folders named "_foo_v4", because of "map foo as _foo_v4 in _lib", written in fear:/_lib/_rank_driver.fear.
Error 7 WellFormedness
""", mapsError(tmp, foos+"""
_lib/_rank_driver.fear
iii
use base.Str as Str;
map foo as _foo_v4 in _lib;
Lib:{ .foo: Str -> Foo.version }
jjj
"""+fooApp("")));
  }
  @Test void aRepeatedMapInARankFileIsAnError(@TempDir Path tmp){
    utils.Err.strCmp("""
In file: fear:/_lib/_rank_driver.fear

001| use base.Str as Str;
002| map foo as _foo_v4 in _lib;
003| map foo as _foo_v5 in _lib;
   | ----------------------^^^^

While inspecting header element > file header > full file
There is already a map of package name "foo" for source code inside folders named "_lib".
Error 2 UnexpectedToken
""", mapsError(tmp, foos
      +fooLib("map foo as _foo_v4 in _lib;\nmap foo as _foo_v5 in _lib;\n")
      +fooApp("")));
  }
  @Test void docExamplesAndAnAssetInAPackageDeclaringItsOwnTestAndStr(@TempDir Path tmp){
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_p/_rank_app.fear
iii
use base.Main as Main;
use base.Nat as Nat;
Test:{}
Str:{}
UnitTests:{}
Calc:{
  ///Adds one.
  //> .check{Calc.inc(1) .assertEq 3}
  .inc(n: Nat): Nat -> n + 1;
  }
App:Main{s->base.Debug#(Note.originalFileName)}
jjj
_p/note.txt
iii
REAL NOTE
""");
    utils.Err.strCmp("""
Test failure _Calc_Examples at line: [###] in file: _p/auto_tests/p_test.fear
[###]
note.txt
""", main(root, List.of()));
    utils.Err.strCmp("""
//Autogenerated from the doc examples of package p; any edit is overwritten

_Calc_Examples: base.Test {::
  .test base.Test{::
.check{Calc.inc(1) .assertEq 3}
  }
  }

AllAutoTests_p: base.UnitTests {::
  .test _Calc_Examples
  }
""", Fs.readUtf8(generatedTests(root)));
  }
  @Test void docExamplesAndAnAssetInAPackageRenamingBaseWithAnAliasForTest(@TempDir Path tmp){
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_p/_rank_app.fear
iii
map bb as _base in _p;
use bb.Test as T;
Calc:{
  ///Adds one.
  //> .check{Calc.inc(1) .assertEq 3}
  .inc(n: bb.Nat): bb.Nat -> n + 1;
  }
App:bb.Main{s->bb.Debug#(Note.originalFileName)}
jjj
_p/note.txt
iii
REAL NOTE
""");
    utils.Err.strCmp("""
Test failure _Calc_Examples at line: [###] in file: _p/auto_tests/p_test.fear
[###]
note.txt
""", main(root, List.of()));
    utils.Err.strCmp("""
//Autogenerated from the doc examples of package p; any edit is overwritten

_Calc_Examples: T {::
  .test T{::
.check{Calc.inc(1) .assertEq 3}
  }
  }

AllAutoTests_p: bb.UnitTests {::
  .test _Calc_Examples
  }
""", Fs.readUtf8(generatedTests(root)));
  }
  @Test void aPackageNamedAsWithDocExamplesReachedThroughAMapNamedIn(@TempDir Path tmp){
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_as/_rank_core.fear
iii
use base.Nat as Nat;
Calc:{
  ///Adds one.
  //> .check{Calc.inc(1) .assertEq 3}
  .inc(n: Nat): Nat -> n + 1;
  }
jjj
_app/_rank_app.fear
iii
use base.Main as Main;
map in as _as in _app;
App:Main{s->base.Debug#(in.Calc.inc(1).str)}
""");
    utils.Err.strCmp("2\n", main(root, List.of()));
  }
  @Test void aPackageNamedLikeAJavaKeywordReachedThroughAMap(@TempDir Path tmp) throws InterruptedException{
    utils.Err.strCmp("new\n", mapsRun(tmp, """
_new/_rank_core.fear
iii
use base.Str as Str;
Class:{ .name: Str -> "new" }
jjj
_app/_rank_app.fear
iii
use base.Main as Main;
map int as _new in _app;
Hello:Main{s->base.Debug#(int.Class.name)}
"""));
  }
  @Test void twoTypeNamesDifferingOnlyByCaseMustStillBuildOnASecondRun(@TempDir Path tmp) throws InterruptedException{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, """
_col/_rank_app.fear
iii
use base.Main as Main;
use base.Void as Void;
Foo:{ .foo:Void->{} }
FOo:{ .fOo:Void->{} }
Hello:Main{s->base.Debug#("hi")}
""");
    var c= coordinator(root);
    c.main(root, stLib);
    c.main(root, stLib);
  }
  // --- CapDownload -------------------------------------------------
  // Each test starts a local HttpServer (no external network needed) and
  // materializes a fresh Fearless project whose source references its port.
  static HttpServer startServer(HttpHandler handler) throws IOException{
    var server= HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/", handler);
    server.start();
    return server;
  }
  static String url(HttpServer server, String path){ return "http://127.0.0.1:"+server.getAddress().getPort()+path; }
  static void reply(HttpExchange ex, int status, byte[] body) throws IOException{
    ex.sendResponseHeaders(status, body.length);
    ex.getResponseBody().write(body);
    ex.close();
  }
  static void replyChunked(HttpExchange ex, byte[] body) throws IOException{
    ex.sendResponseHeaders(200, 0);
    ex.getResponseBody().write(body);
    ex.close();
  }
  static void redirect(HttpExchange ex, String location) throws IOException{
    ex.getResponseHeaders().add("Location", location);
    ex.sendResponseHeaders(302, -1);
    ex.close();
  }
  static byte[] onePixelPng() throws IOException{
    var img= new BufferedImage(3, 5, BufferedImage.TYPE_INT_ARGB);
    var bout= new ByteArrayOutputStream();
    ImageIO.write(img, "png", bout);
    return bout.toByteArray();
  }
  static String downloadProject(String url, String call){
    return """
_col/_rank_app.fear
iii
use base.Main as Main;
NeverRecovers: base.BadStrUnitRecover { reason, byteOffset, byteLength, rejectedValue -> "should not happen" }
NeverRecoversU: base.BadUStrUnitRecover { reason, byteOffset, byteLength, rejectedValue -> "should not happen".u }
Hello:Main{s->base.Debug#(base.Try#(s.iso.close, {t ->"""+" "+call.replace("$URL", url).replace("s.download", "t.download")+"}).run{.ok x -> x.str; .info i -> \"caught: \"+(i.msg.orValue \"\")})}\n";
  }
  @Test void downloadStrUtf8Succeeds(@TempDir Path tmp) throws Exception{
    var server= startServer(ex -> reply(ex, 200, "hello download".getBytes(UTF_8)));
    try{
      var root= tmp.resolve("root");
      UserError.root = root;
      FsDsl.materialize(root, downloadProject(url(server, "/ok"),
        "s.download.downloadStrUtf8(\"$URL\", 1000, NeverRecovers)"));
      var out= coordinator(root).main(root, stLib);
      utils.Err.strCmp("[###]hello download[###]", out);
    }
    finally{ server.stop(0); }
  }
  @Test void downloadBytesReturnsExactContent(@TempDir Path tmp) throws Exception{
    var server= startServer(ex -> reply(ex, 200, new byte[]{65, 66, 67}));
    try{
      var root= tmp.resolve("root");
      UserError.root = root;
      FsDsl.materialize(root, downloadProject(url(server, "/bytes"),
        "s.download.downloadBytes(\"$URL\", 1000).size"));
      var out= coordinator(root).main(root, stLib);
      utils.Err.strCmp("[###]3[###]", out);
    }
    finally{ server.stop(0); }
  }
  @Test void downloadRejectsNonHttpScheme(@TempDir Path tmp) throws Exception{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, downloadProject("ftp://127.0.0.1/x",
      "s.download.downloadStrUtf8(\"$URL\", 1000, NeverRecovers)"));
    var out= coordinator(root).main(root, stLib);
    utils.Err.strCmp("[###]caught: Invalid URL descriptor[###]", out);
    utils.Err.strCmp("[###]unsupported scheme[###]", out);
  }
  @Test void downloadRejectsMalformedUrl(@TempDir Path tmp) throws Exception{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, downloadProject("not a url",
      "s.download.downloadStrUtf8(\"$URL\", 1000, NeverRecovers)"));
    var out= coordinator(root).main(root, stLib);
    utils.Err.strCmp("[###]caught: Invalid URL descriptor[###]", out);
  }
  @Test void downloadFailsWhenContentLengthExceedsMaxBytes(@TempDir Path tmp) throws Exception{
    var server= startServer(ex -> reply(ex, 200, "0123456789".getBytes(UTF_8)));
    try{
      var root= tmp.resolve("root");
      UserError.root = root;
      FsDsl.materialize(root, downloadProject(url(server, "/big"),
        "s.download.downloadBytes(\"$URL\", 4).size"));
      var out= coordinator(root).main(root, stLib);
      utils.Err.strCmp("[###]caught: Download exceeds maxBytes[###]", out);
      utils.Err.strCmp("[###]contentLength[###]", out);
    }
    finally{ server.stop(0); }
  }
  @Test void downloadFailsWhenStreamedBytesExceedMaxBytesWithoutContentLength(@TempDir Path tmp) throws Exception{
    var server= startServer(ex -> replyChunked(ex, "0123456789".getBytes(UTF_8)));
    try{
      var root= tmp.resolve("root");
      UserError.root = root;
      FsDsl.materialize(root, downloadProject(url(server, "/chunked"),
        "s.download.downloadBytes(\"$URL\", 4).size"));
      var out= coordinator(root).main(root, stLib);
      utils.Err.strCmp("[###]caught: Download exceeds maxBytes[###]", out);
      utils.Err.strCmp("[###]bytesRead[###]", out);
    }
    finally{ server.stop(0); }
  }
  @Test void downloadFollowsRedirectsThenSucceeds(@TempDir Path tmp) throws Exception{
    var server= startServer(ex -> {
      switch (ex.getRequestURI().getPath()){
        case "/start" -> redirect(ex, "/next");
        case "/next" -> redirect(ex, "/final");
        case "/final" -> reply(ex, 200, "landed".getBytes(UTF_8));
        default -> reply(ex, 404, new byte[0]);
      }
    });
    try{
      var root= tmp.resolve("root");
      UserError.root = root;
      FsDsl.materialize(root, downloadProject(url(server, "/start"),
        "s.download.downloadStrUtf8(\"$URL\", 1000, NeverRecovers)"));
      var out= coordinator(root).main(root, stLib);
      utils.Err.strCmp("[###]landed[###]", out);
    }
    finally{ server.stop(0); }
  }
  @Test void downloadFailsAfterTooManyRedirects(@TempDir Path tmp) throws Exception{
    var server= startServer(ex -> redirect(ex, "/loop"));
    try{
      var root= tmp.resolve("root");
      UserError.root = root;
      FsDsl.materialize(root, downloadProject(url(server, "/loop"),
        "s.download.downloadStrUtf8(\"$URL\", 1000, NeverRecovers)"));
      var out= coordinator(root).main(root, stLib);
      utils.Err.strCmp("[###]caught: Download failed.[###]too many redirects[###]", out);
    }
    finally{ server.stop(0); }
  }
  @Test void downloadRedirectRevalidatesScheme(@TempDir Path tmp) throws Exception{
    var server= startServer(ex -> redirect(ex, "file:///etc/passwd"));
    try{
      var root= tmp.resolve("root");
      UserError.root = root;
      FsDsl.materialize(root, downloadProject(url(server, "/go"),
        "s.download.downloadStrUtf8(\"$URL\", 1000, NeverRecovers)"));
      var out= coordinator(root).main(root, stLib);
      utils.Err.strCmp("[###]caught: Invalid URL descriptor[###]", out);
      utils.Err.strCmp("[###]unsupported scheme[###]", out);
      utils.Err.strCmp("[###]file:///etc/passwd[###]", out);
    }
    finally{ server.stop(0); }
  }
  @Test void downloadFailsOnNon2xxStatus(@TempDir Path tmp) throws Exception{
    var server= startServer(ex -> reply(ex, 404, "nope".getBytes(UTF_8)));
    try{
      var root= tmp.resolve("root");
      UserError.root = root;
      FsDsl.materialize(root, downloadProject(url(server, "/missing"),
        "s.download.downloadStrUtf8(\"$URL\", 1000, NeverRecovers)"));
      var out= coordinator(root).main(root, stLib);
      utils.Err.strCmp("[###]caught: Download failed.[###]HTTP status: 404[###]", out);
    }
    finally{ server.stop(0); }
  }
  // A precomposed e-acute (U+00E9, via Character.toChars so the source file's
  // own encoding never matters) is valid UTF-8 and a valid Unicode scalar
  // value, but it is outside Str's ASCII-only whitelist: UStr decodes it
  // as-is, Str must route it through the recover callback instead.
  @Test void downloadUStrAcceptsWhatStrMustRecover(@TempDir Path tmp) throws Exception{
    var eAcute= new String(Character.toChars(0xE9));
    var body= ("caf"+eAcute).getBytes(UTF_8);
    var server= startServer(ex -> reply(ex, 200, body));
    try{
      var root= tmp.resolve("root");
      UserError.root = root;
      var u= url(server, "/accent");
      FsDsl.materialize(root, """
_col/_rank_app.fear
iii
use base.Main as Main;
RecoverToMarker: base.BadStrUnitRecover { reason, byteOffset, byteLength, rejectedValue -> "?" }
NeverRecoversU: base.BadUStrUnitRecover { reason, byteOffset, byteLength, rejectedValue -> "should not happen".u }
Hello:Main{s->base.Debug#(
  s.download.downloadStrUtf8(`"""+u+"""
`, 1000, RecoverToMarker)+`|`+(s.download.downloadUStrUtf8(`"""+u+"""
`, 1000, NeverRecoversU).size.str))}
""");
      var out= coordinator(root).main(root, stLib);
      utils.Err.strCmp("[###]caf?[###]", out);
      utils.Err.strCmp("[###]|4[###]", out);
    }
    finally{ server.stop(0); }
  }
  @Test void downloadImageSucceeds(@TempDir Path tmp) throws Exception{
    var png= onePixelPng();
    var server= startServer(ex -> reply(ex, 200, png));
    try{
      var root= tmp.resolve("root");
      UserError.root = root;
      FsDsl.materialize(root, downloadProject(url(server, "/img.png"),
        "s.download.downloadImage(\"$URL\", 100_000, 1_000_000).width"));
      var out= coordinator(root).main(root, stLib);
      utils.Err.strCmp("[###]3[###]", out);
    }
    finally{ server.stop(0); }
  }
  static void answerOnce(ServerSocket server, String response){
    try (var s= server.accept()){
      var in= new BufferedReader(new InputStreamReader(s.getInputStream(), UTF_8));
      while (!in.readLine().isEmpty()){}
      s.getOutputStream().write(response.getBytes(UTF_8));
    }
    catch (IOException e){ throw new UncheckedIOException(e); }
  }
  @Test void downloadRedirectToMalformedLocationIsDeterministic(@TempDir Path tmp) throws Exception{
    var server= startServer(ex -> redirect(ex, "/a b"));
    try{
      var root= tmp.resolve("root");
      UserError.root = root;
      FsDsl.materialize(root, downloadProject(url(server, "/go"),
        "s.download.downloadStrUtf8(\"$URL\", 1000, NeverRecovers)"));
      var out= coordinator(root).main(root, stLib);
      utils.Err.strCmp("[###]caught: [###]/a b[###]", out);
    }
    finally{ server.stop(0); }
  }
  @Test void downloadRejectsOutOfRangePort(@TempDir Path tmp) throws Exception{
    var root= tmp.resolve("root");
    UserError.root = root;
    FsDsl.materialize(root, downloadProject("http://127.0.0.1:99999/x",
      "s.download.downloadStrUtf8(\"$URL\", 1000, NeverRecovers)"));
    var out= coordinator(root).main(root, stLib);
    utils.Err.strCmp("[###]caught: Invalid URL descriptor.[###]99999[###]", out);
  }
  @Test void downloadFailsOnMalformedContentLength(@TempDir Path tmp) throws Exception{
    try (var server= new ServerSocket(0, 1, InetAddress.getLoopbackAddress())){
      Thread.startVirtualThread(() -> answerOnce(server, "HTTP/1.1 200 OK\r\nContent-Length: abc\r\nConnection: close\r\n\r\nhello"));
      var root= tmp.resolve("root");
      UserError.root = root;
      FsDsl.materialize(root, downloadProject("http://127.0.0.1:"+server.getLocalPort()+"/len",
        "s.download.downloadBytes(\"$URL\", 1000).size"));
      var out= coordinator(root).main(root, stLib);
      utils.Err.strCmp("[###]caught: Download failed.[###]", out);
    }
  }
  @Test void downloadFailureAfterARedirectNamesTheUrlThatAnswered(@TempDir Path tmp) throws Exception{
    var server= startServer(ex -> {
      switch (ex.getRequestURI().getPath()){
        case "/start" -> redirect(ex, "/missing");
        default -> reply(ex, 404, new byte[0]);
      }
    });
    try{
      var root= tmp.resolve("root");
      UserError.root = root;
      FsDsl.materialize(root, downloadProject(url(server, "/start"),
        "s.download.downloadStrUtf8(\"$URL\", 1000, NeverRecovers)"));
      var out= coordinator(root).main(root, stLib);
      utils.Err.strCmp("[###]caught: Download failed.\nurl: "+url(server, "/missing")+"\nHTTP status: 404[###]", out);
    }
    finally{ server.stop(0); }
  }
  @Test void downloadErrorTextHoldsOnlyStrCharacters(@TempDir Path tmp) throws Exception{
    var server= startServer(ex -> redirect(ex, "ftp://127.0.0.1/caf"+new String(Character.toChars(0xE9))));
    try{
      var root= tmp.resolve("root");
      UserError.root = root;
      FsDsl.materialize(root, downloadProject(url(server, "/go"),
        "s.download.downloadStrUtf8(\"$URL\", 1000, NeverRecovers)"));
      var out= coordinator(root).main(root, stLib);
      utils.Err.strCmp("[###]caught: Invalid URL descriptor.[###]ftp://127.0.0.1/caf%C3%A9[###]", out);
    }
    finally{ server.stop(0); }
  }
}
