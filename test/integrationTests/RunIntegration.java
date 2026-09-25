package integrationTests;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Optional;

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
import core.E.Literal;
import core.OtherPackages;
import resources.ResolveResource;
import testBuildBase.BaseCacheBuilder;
import naiveBackend.Backend;
import naiveBackend.BackendTools;
import realSourceOracle.RealSourceOracleWithZip;
import realSourceOracle.SourceOracleWithAutoload;
import testHelperFs.FsDsl;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import fileSupport.JUnitReport;
import tools.Fs;
import tools.JavacTool;
import tools.SourceOracle;
import userMessages.UserError;

public class RunIntegration {
  static{ utils.Err.setUp(AssertionFailedError.class, Assertions::assertEquals, Assertions::assertTrue); }

  static final Path baseCache= ResolveResource.stLibDebugOut.resolve("baseCache");
  static final Path baseTestFile= ResolveResource.stLibDebugOut.resolve("_baseTestOut","base_test.fear");
  static final Path reportsDir= ResolveResource.coordinatorSrc.getParent().resolve(".out","junit_xml");
  static final Path reportsFile= reportsDir.resolve("all_auto_tests.xml");
  static final List<String> suites= new ArrayList<>();
  static final SourceOracle stLib= new RealSourceOracleWithZip(ResolveResource.stLibPath);
  @BeforeAll static void buildBaseOnce(){
    BaseCacheBuilder.buildInto(ResolveResource.coordinatorJars, ResolveResource.stLibDebugOut, Optional.of(baseTestFile));
    Fs.rmTree(reportsDir);
  }
  Coordinator coordinator(Path project){
    System.setProperty(JavacTool.appDirKey,ResolveResource.stLibPath.getParent()
      .resolve("fearlessArtefact","fearless","app").toString());
    return new Coordinator(){
      public Path modsPath(){  return ResolveResource.coordinatorJars; }
      public Optional<Path> baseCachePath(){ return Optional.of(baseCache); }
      public Path stdLibBase(){ return ResolveResource.stLibPath; }
      public BackendTools backendTools(String pkgName, SourceOracle oracle, OtherPackages other, List<Literal> core, CapabilityEnvironment capabilities){
        return BackendTools.of(pkgName, oracle, other, core, project.resolve(Coordinator.outDir), baseCachePath(), ResolveResource.stLibRTPath, capabilities);
      }
    };
  }
  static Path freshIntegrationRoot(String name){
    var root= ResolveResource.integrationTests.resolve(name);
    Fs.rmTree(root.resolve(".fearless_out"));
    return root;
  }
  String run(String name){
    var project= freshIntegrationRoot(name);
    try { return coordinator(project).main(project, stLib);}
    catch (InterruptedException e){ return Assertions.fail(e);}
  }
  void testOk(String name){
    var out= run(name);
    writeJUnitReport(name);
    var fails= out.lines().filter(l->l.startsWith("Test failure ")).toList();
    Assertions.assertTrue(fails.isEmpty(), ()->"Fearless unit tests failed in "+name+":\n"+String.join("\n",fails));
  }
  static void writeJUnitReport(String name){ writeJUnitReport(name, ResolveResource.integrationTests.resolve(name)); }
  static void writeJUnitReport(String name, Path root){
    var one= JUnitReport.suite(name, root);
    if (one.isEmpty()){ return; }
    suites.add(one);
    Fs.writeUtf8(reportsFile, JUnitReport.document(String.join("",suites)));
  }
  @Test void helloWorld(){ testOk("helloWorld");}
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
  @Test void map_a_to_pkc(){ testOk("map_a_to_pkc");}
  // What counts as a main of a package: a top level type implementing base.Main, and
  // also one declared inside a method when it implements base.CaptureFree, since that
  // is the promise that it captures nothing and so the backend gives it an instance.
  // A main declared inside a method WITHOUT that promise may capture the parameters
  // and the "this" of its enclosing method, so there is no instance of it to run, and
  // it is correctly left out: "MkCapturing.mk" is the only way to obtain one.
  @Test void mainInMethod(){
    utils.Err.strCmp("""
capture free main in a method
top level main
""", run("mainInMethod"));
  }
  @Test void helloStackTraces(){ testOk("helloStackTraces");}
  @Test void testingStandardLibrary(){ testOk("testingStandardLibrary");}
  @Test void baseGeneratedExamples(@TempDir Path tmp) throws InterruptedException{
    Path root= tmp.resolve("root");
    UserError.root= root;
    var genDir= root.resolve("_gen");
    Fs.ensureDir(genDir);
    Fs.writeUtf8(genDir.resolve("_rank_app.fear"), Fs.readUtf8(baseTestFile));
    var out= coordinator(root).main(root, stLib);
    writeJUnitReport("baseGeneratedExamples", root);
    var fails= out.lines().filter(l->l.startsWith("Test failure ")).toList();
    Assertions.assertTrue(fails.isEmpty(), ()->"Fearless unit tests failed in base's generated examples:\n"+String.join("\n",fails));
  }
  @Test void testDocs(){ testOk("testDocs");}
  @Test void onlyImmCapture(@TempDir Path tmp){
    var root= tmp.resolve("root");
    UserError.root= root;
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
      public Path modsPath(){ return base.modsPath(); }
      public Optional<Path> baseCachePath(){ return base.baseCachePath(); }
      public Path stdLibBase(){ return base.stdLibBase(); }
      public void backend(String pkgName, List<Literal> core, SourceOracle oracle, OtherPackages other, CapabilityEnvironment capabilities){
        new Backend(genJava, base.backendTools(pkgName, oracle, other, core, capabilities)).produceJavaCode();
      }
    }.compile(root, stLib);
    var got= Fs.walk(genJava, s->s.filter(Files::isRegularFile).sorted().map(p->p.getFileName()+(Fs.readUtf8(p).contains("_base.OnlyImmCapture") ? " only imm" : "")).toList());
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
      """, String.join("\n", got)+"\n");
  }
  @Test void testAssets(){ testOk("testAssets");}
  private Path theOneLogFile(Path dir, String prefix) throws IOException{
    try (var files= Files.list(dir)){
      var found= files.filter(p-> p.getFileName().toString().startsWith(prefix)).toList();
      Assertions.assertEquals(1, found.size(), found.toString());
      return found.get(0);
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
    var pkgContent= Fs.readUtf8(theOneLogFile(logDir.resolve("logging"), "PkgLog$"));
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
zeroF
42
42
zeroMemo
7
7
""", run("testingNorms"));
  }
  //@Test void testGui1(){ testOk("testGui1");}
  void compileOk(String name){ var project= freshIntegrationRoot(name); coordinator(project).compile(project, stLib); }
  @Test void theInteractiveProjectsStillCompile(){
    for (var name: List.of("testGui1","testGui2","testGuiImg","testBasketball","testTicTacToe")){ compileOk(name); }
  }

  // Two assets that generate the same auto-loaded type name (here "foo.txt" and "foo.png",
  // both auto-loading as "Foo") must be rejected end-to-end, before the frontend ever sees
  // the synthetic autoloaded_assets.fear file, with a message naming the two real files
  // instead of blaming a file the user never wrote.
  @Test void assetAutoloadNameCollision(@TempDir Path tmp) throws InterruptedException{
    Path root= tmp.resolve("root");
    UserError.root= root;
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
    var ex= Assertions.assertThrows(UserError.class, ()->coordinator(root).main(root, stLib));
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
    Path root= tmp.resolve("root");
    UserError.root= root;
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
    Assertions.assertEquals("col.Again _col/more.fear\ncol.Hello _col/_rank_app.fear\n", Fs.readUtf8(root.resolve(Coordinator.outDir).resolve("col.mains")));
    Assertions.assertEquals(Map.of("col.Again","_col/more.fear","col.Hello","_col/_rank_app.fear"), coordinator(root).mains(root, stLib).orElseThrow());
  }
  @Test void aCaptureFreeMainDeclaredInAMethodIsListedLikeTheOnesItRuns(@TempDir Path tmp) throws InterruptedException{
    Path root= tmp.resolve("root");
    UserError.root= root;
    FsDsl.materialize(root, """
_col/_rank_app.fear
iii
use base.Main as Main;
use base.CaptureFree as CaptureFree;
Top:Main{s->base.Debug#(`top`)}
MkFree:{.mk:Main->Free:Main,CaptureFree{s->base.Debug#(`free`)}}
""");
    utils.Err.strCmp("free\ntop\n", coordinator(root).main(root, stLib));
    Assertions.assertEquals(Map.of("col.Free","_col/_rank_app.fear","col.Top","_col/_rank_app.fear"), coordinator(root).mains(root, stLib).orElseThrow());
  }
  @Test void aPackageNamedAfterAJavaKeywordRuns(@TempDir Path tmp) throws InterruptedException{
    Path root= tmp.resolve("root");
    UserError.root= root;
    FsDsl.materialize(root, """
_int/_rank_app.fear
iii
use base.Main as Main;
Hello:Main{s->base.Debug#(`hi`)}
""");
    utils.Err.strCmp("hi\n", coordinator(root).main(root, stLib));
  }
  @Test void anAssetWhoseNameStartsWithUnderscoreAutoLoadsAsAPrivateType(@TempDir Path tmp) throws InterruptedException{
    Path root= tmp.resolve("root");
    UserError.root= root;
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
    Path root= tmp.resolve("root");
    UserError.root= root;
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
    Assertions.assertFalse(out.contains("TOP-SECRET-NOT-AN-ASSET"), out);
    Assertions.assertTrue(out.contains("was not recognized by the compiler as auto-imported"), out);
    Assertions.assertTrue(out.contains("REAL ASSET CONTENT"), out);
  }

  @Test void aBaseAssetIsReadFromTheStdLibBase(@TempDir Path tmp) throws InterruptedException{
    Path root= tmp.resolve("root");
    UserError.root= root;
    FsDsl.materialize(root, """
_col/_rank_app.fear
iii
use base.Main as Main;
Hello:Main{s->base.Debug#(base.IconsConflict.path+" "+(base.IconsConflict.readImage(s.assetRead, 1_000_000).width.getDataType.str))}
""");
    utils.Err.strCmp("fear:/_base/icons/conflict.png 256\n", coordinator(root).main(root, stLib));
  }

  @Test void anAssetWhoseNameForgesNoValidTypeIsReportedAgainstTheRealFile(@TempDir Path tmp){
    Path root= tmp.resolve("root");
    UserError.root= root;
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
    var ex= Assertions.assertThrows(UserError.class, ()->coordinator(root).main(root, stLib));
    Assertions.assertFalse(ex.getMessage().contains(SourceOracleWithAutoload.autoloadFileSuffix), ex.getMessage());
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
    Path root= tmp.resolve("root");
    UserError.root= root;
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
    var out= root.resolve(".fearless_out");
    long aBuilt= Fs.lastModified(out.resolve("a.built"));
    long aJson= Fs.lastModified(out.resolve("a.json"));
    long bBuilt= Fs.lastModified(out.resolve("b.built"));
    var aSrc= root.resolve("_a/_rank_core.fear");
    Fs.writeUtf8(aSrc, Fs.readUtf8(aSrc).replace("\"hi\"","\"ho\""));
    Files.setLastModifiedTime(aSrc, FileTime.fromMillis(System.currentTimeMillis()+500));
    c.main(root, stLib);

    Assertions.assertNotEquals(aBuilt, Fs.lastModified(out.resolve("a.built")));
    Assertions.assertEquals(aJson, Fs.lastModified(out.resolve("a.json")));
    Assertions.assertEquals(bBuilt, Fs.lastModified(out.resolve("b.built")));
  }

  // Confirms expected behaviour: only the packages at the highest rank number get their Main run.
  @Test void onlyTheHighestRankPackageMainRuns(@TempDir Path tmp) throws InterruptedException{
    Path root= tmp.resolve("root");
    UserError.root= root;
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
    Assertions.assertTrue(out.contains("from app"), out);
    Assertions.assertFalse(out.contains("from core"), out);
  }

  // Confirms expected behaviour: packages tied for that highest rank number all run.
  @Test void allPackagesAtTheHighestRankRun(@TempDir Path tmp) throws InterruptedException{
    Path root= tmp.resolve("root");
    UserError.root= root;
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
    Assertions.assertTrue(out.contains("from a"), out);
    Assertions.assertTrue(out.contains("from z"), out);
  }

  @Test void flowsAreDeterministic(){ testOk("testFlowDeterminism"); }
  // The sequential flow returns 0 without evaluating any other element, so the parallel one must
  // return as well, whatever the elements past the decision do: here they never return.
  @Test void flowFirstDoesNotWaitForDivergingElements() throws InterruptedException{
    var project= freshIntegrationRoot("testFlowTermination");
    var c= coordinator(project);
    c.compile(project, stLib);
    var out= new StringBuilder();
    var child= Coordinator.startMain(project, ResolveResource.stLibPath, "term.FirstDoesNotWaitForTheRest", c.sharedClasspath(), out::append);
    var waiter= new Thread(()->{ try{ child.await(); } catch(InterruptedException e){ child.kill(); } });
    waiter.start();
    waiter.join(60_000);
    if (waiter.isAlive()){ child.kill(); Assertions.fail("first! did not return within 60s while the later elements diverge:\n"+out); }
    utils.Err.strCmp("0\n", out.toString());
  }
  static boolean recursionCompiled;
  String runRecursionMain(String main) throws InterruptedException{
    var root= ResolveResource.integrationTests.resolve("runRecursion");
    if (!recursionCompiled){ compileOk("runRecursion"); recursionCompiled= true; }
    var out= new StringBuilder();
    Coordinator.startMain(root, ResolveResource.stLibPath, main, coordinator(root).sharedClasspath(), out::append).await();
    return out.toString();
  }
  @Test void aDeepRecursionOverflowsTheStackOfMain() throws InterruptedException{
    utils.Err.strCmp("[###]java.lang.StackOverflowError[###]", runRecursionMain("rec.NaiveSum"));
  }
  @Test void runRecursionOnEveryStepIsDeepAndFast() throws InterruptedException{
    long start= System.nanoTime();
    utils.Err.strCmp("500000500000", runRecursionMain("rec.DeepSum"));
    long millis= (System.nanoTime()-start)/1_000_000;
    Assertions.assertTrue(millis < 20_000, "one million steps took "+millis+"ms");
  }
  @Test void aFamilyNestingTooManyCallsOnOneThreadOverflows() throws InterruptedException{
    utils.Err.strCmp("[###]java.lang.StackOverflowError[###]", runRecursionMain("rec.CrowdedSum"));
  }
  @Test void aFamilyWithTheSameStackAndALowerDepthFits() throws InterruptedException{
    utils.Err.strCmp("500000500000", runRecursionMain("rec.SpreadSum"));
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
  @Test void aStackTraceNamesTheFearlessTypesMethodsAndLines(){
    utils.Err.strCmp("""
AAAAh
imm Bar.bar error line: 8 in file _hello/_rank_app.fear
imm Foo.foo error line: 7 in file _hello/_rank_app.fear
imm Hello.main(_) error line: 6 in file _hello/_rank_app.fear
""", run("helloStackTraces"));
  }
  @Test void aFailingGetShowsNoActionFrames(@TempDir Path tmp) throws InterruptedException{
    Path root= tmp.resolve("root");
    UserError.root= root;
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

  @Test void virtualizationMapMentionsAPackageThatDoesNotExist(@TempDir Path tmp) throws InterruptedException{
    Path root= tmp.resolve("root");
    UserError.root= root;
    FsDsl.materialize(root, """
_pka/_rank_app999.fear
iii
use base.Main as Main;
map a as nonexistentpkg in pkb;
Hello:Main{s->base.Debug#(pkb.B.text)}
jjj
_pkb/_rank_app200.fear
iii
use base.Str as Str;
B:{.text:Str->a.C.text;}
""");
    var ex= Assertions.assertThrows(RuntimeException.class, ()->coordinator(root).main(root, stLib));
    utils.Err.strCmp("""
In file: fear:/_pkb/_rank_app200.fear

002| B:{.text:Str->a.C.text;}
   |               ^^^^^^^^^^

While inspecting a type name
Package "nonexistentpkg" does not exist.
Visible packages: "base", "pkb".
Error 7 WellFormedness
""", ex.getMessage());
  }

  @Test void useOfAHigherRankPackageIsReportedAsUndeclaredNotAsAnOrderingProblem(@TempDir Path tmp) throws InterruptedException{
    Path root= tmp.resolve("root");
    UserError.root= root;
    FsDsl.materialize(root, """
_a/_rank_core.fear
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
    var ex= Assertions.assertThrows(RuntimeException.class, ()->coordinator(root).main(root, stLib));
    utils.Err.strCmp("""
In file: fear:/_a/_rank_core.fear

002| use z.Greeting as Greeting;
   |     ^^^^^^^^^^

While inspecting package header
"use" directive refers to undeclared name: type "Greeting" is not declared in package "z".
Error 7 WellFormedness
""", ex.getMessage());
  }

  @Test void twoTypeNamesDifferingOnlyByCaseMustStillBuildOnASecondRun(@TempDir Path tmp) throws InterruptedException{
    Path root= tmp.resolve("root");
    UserError.root= root;
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

  // --- DownloadCapability -------------------------------------------------
  // Each test starts a local HttpServer (no external network needed) and
  // materializes a fresh Fearless project whose source references its port.

  static HttpServer startServer(HttpHandler handler) throws IOException{
    var server= HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),0);
    server.createContext("/",handler);
    server.start();
    return server;
  }
  static String url(HttpServer server,String path){ return "http://127.0.0.1:"+server.getAddress().getPort()+path; }
  static void reply(HttpExchange ex,int status,byte[] body) throws IOException{
    ex.sendResponseHeaders(status,body.length);
    ex.getResponseBody().write(body);
    ex.close();
  }
  static void replyChunked(HttpExchange ex,byte[] body) throws IOException{
    ex.sendResponseHeaders(200,0);
    ex.getResponseBody().write(body);
    ex.close();
  }
  static void redirect(HttpExchange ex,String location) throws IOException{
    ex.getResponseHeaders().add("Location",location);
    ex.sendResponseHeaders(302,-1);
    ex.close();
  }
  static byte[] onePixelPng() throws IOException{
    var img= new BufferedImage(3,5,BufferedImage.TYPE_INT_ARGB);
    var bout= new ByteArrayOutputStream();
    ImageIO.write(img,"png",bout);
    return bout.toByteArray();
  }
  static String downloadProject(String url,String call){
    return """
_col/_rank_app.fear
iii
use base.Main as Main;
NeverRecovers: base.BadStrUnitRecover { reason, byteOffset, byteLength, rejectedValue -> "should not happen" }
NeverRecoversU: base.BadUStrUnitRecover { reason, byteOffset, byteLength, rejectedValue -> "should not happen".u }
Hello:Main{s->base.Debug#("""+call.replace("$URL",url)+")}\n";
  }

  @Test void downloadStrUtf8Succeeds(@TempDir Path tmp) throws Exception{
    var server= startServer(ex->reply(ex,200,"hello download".getBytes(UTF_8)));
    try{
      Path root= tmp.resolve("root");
      UserError.root= root;
      FsDsl.materialize(root, downloadProject(url(server,"/ok"),
        "s.download.downloadStrUtf8(\"$URL\", 1000, NeverRecovers)"));
      var out= coordinator(root).main(root, stLib);
      Assertions.assertTrue(out.contains("hello download"), out);
    }
    finally{ server.stop(0); }
  }

  @Test void downloadBytesReturnsExactContent(@TempDir Path tmp) throws Exception{
    var server= startServer(ex->reply(ex,200,new byte[]{65,66,67}));
    try{
      Path root= tmp.resolve("root");
      UserError.root= root;
      FsDsl.materialize(root, downloadProject(url(server,"/bytes"),
        "s.download.downloadBytes(\"$URL\", 1000).size"));
      var out= coordinator(root).main(root, stLib);
      Assertions.assertTrue(out.contains("3"), out);
    }
    finally{ server.stop(0); }
  }

  @Test void downloadRejectsNonHttpScheme(@TempDir Path tmp) throws Exception{
    Path root= tmp.resolve("root");
    UserError.root= root;
    FsDsl.materialize(root, downloadProject("ftp://127.0.0.1/x",
      "s.download.downloadStrUtf8(\"$URL\", 1000, NeverRecovers)"));
    var out= coordinator(root).main(root, stLib);
    Assertions.assertTrue(out.contains("Invalid URL descriptor"), out);
    Assertions.assertTrue(out.contains("unsupported scheme"), out);
  }

  @Test void downloadRejectsMalformedUrl(@TempDir Path tmp) throws Exception{
    Path root= tmp.resolve("root");
    UserError.root= root;
    FsDsl.materialize(root, downloadProject("not a url",
      "s.download.downloadStrUtf8(\"$URL\", 1000, NeverRecovers)"));
    var out= coordinator(root).main(root, stLib);
    Assertions.assertTrue(out.contains("Invalid URL descriptor"), out);
  }

  @Test void downloadFailsWhenContentLengthExceedsMaxBytes(@TempDir Path tmp) throws Exception{
    var server= startServer(ex->reply(ex,200,"0123456789".getBytes(UTF_8)));
    try{
      Path root= tmp.resolve("root");
      UserError.root= root;
      FsDsl.materialize(root, downloadProject(url(server,"/big"),
        "s.download.downloadBytes(\"$URL\", 4).size"));
      var out= coordinator(root).main(root, stLib);
      Assertions.assertTrue(out.contains("Download exceeds maxBytes"), out);
      Assertions.assertTrue(out.contains("contentLength"), out);
    }
    finally{ server.stop(0); }
  }

  @Test void downloadFailsWhenStreamedBytesExceedMaxBytesWithoutContentLength(@TempDir Path tmp) throws Exception{
    var server= startServer(ex->replyChunked(ex,"0123456789".getBytes(UTF_8)));
    try{
      Path root= tmp.resolve("root");
      UserError.root= root;
      FsDsl.materialize(root, downloadProject(url(server,"/chunked"),
        "s.download.downloadBytes(\"$URL\", 4).size"));
      var out= coordinator(root).main(root, stLib);
      Assertions.assertTrue(out.contains("Download exceeds maxBytes"), out);
      Assertions.assertTrue(out.contains("bytesRead"), out);
    }
    finally{ server.stop(0); }
  }

  @Test void downloadFollowsRedirectsThenSucceeds(@TempDir Path tmp) throws Exception{
    var server= startServer(ex->{
      switch (ex.getRequestURI().getPath()){
        case "/start" -> redirect(ex,"/next");
        case "/next" -> redirect(ex,"/final");
        case "/final" -> reply(ex,200,"landed".getBytes(UTF_8));
        default -> reply(ex,404,new byte[0]);
      }
    });
    try{
      Path root= tmp.resolve("root");
      UserError.root= root;
      FsDsl.materialize(root, downloadProject(url(server,"/start"),
        "s.download.downloadStrUtf8(\"$URL\", 1000, NeverRecovers)"));
      var out= coordinator(root).main(root, stLib);
      Assertions.assertTrue(out.contains("landed"), out);
    }
    finally{ server.stop(0); }
  }

  @Test void downloadFailsAfterTooManyRedirects(@TempDir Path tmp) throws Exception{
    var server= startServer(ex->redirect(ex,"/loop"));
    try{
      Path root= tmp.resolve("root");
      UserError.root= root;
      FsDsl.materialize(root, downloadProject(url(server,"/loop"),
        "s.download.downloadStrUtf8(\"$URL\", 1000, NeverRecovers)"));
      var out= coordinator(root).main(root, stLib);
      Assertions.assertTrue(out.contains("too many redirects"), out);
    }
    finally{ server.stop(0); }
  }

  @Test void downloadRedirectRevalidatesScheme(@TempDir Path tmp) throws Exception{
    var server= startServer(ex->redirect(ex,"file:///etc/passwd"));
    try{
      Path root= tmp.resolve("root");
      UserError.root= root;
      FsDsl.materialize(root, downloadProject(url(server,"/go"),
        "s.download.downloadStrUtf8(\"$URL\", 1000, NeverRecovers)"));
      var out= coordinator(root).main(root, stLib);
      Assertions.assertTrue(out.contains("Invalid URL descriptor"), out);
      Assertions.assertTrue(out.contains("unsupported scheme"), out);
      Assertions.assertTrue(out.contains("file:///etc/passwd"), out);
    }
    finally{ server.stop(0); }
  }

  @Test void downloadFailsOnNon2xxStatus(@TempDir Path tmp) throws Exception{
    var server= startServer(ex->reply(ex,404,"nope".getBytes(UTF_8)));
    try{
      Path root= tmp.resolve("root");
      UserError.root= root;
      FsDsl.materialize(root, downloadProject(url(server,"/missing"),
        "s.download.downloadStrUtf8(\"$URL\", 1000, NeverRecovers)"));
      var out= coordinator(root).main(root, stLib);
      Assertions.assertTrue(out.contains("HTTP status: 404"), out);
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
    var server= startServer(ex->reply(ex,200,body));
    try{
      Path root= tmp.resolve("root");
      UserError.root= root;
      var u= url(server,"/accent");
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
      Assertions.assertTrue(out.contains("caf?"), out);
      Assertions.assertTrue(out.contains("|4"), out);
    }
    finally{ server.stop(0); }
  }

  @Test void downloadImageSucceeds(@TempDir Path tmp) throws Exception{
    var png= onePixelPng();
    var server= startServer(ex->reply(ex,200,png));
    try{
      Path root= tmp.resolve("root");
      UserError.root= root;
      FsDsl.materialize(root, downloadProject(url(server,"/img.png"),
        "s.download.downloadImage(\"$URL\", 100_000, 1_000_000).width"));
      var out= coordinator(root).main(root, stLib);
      Assertions.assertTrue(out.contains("3"), out);
    }
    finally{ server.stop(0); }
  }

  @Test void downloadTimesOutOnStalledResponse(@TempDir Path tmp) throws Exception{
    var server= startServer(_->{
      try{ Thread.sleep(40_000); } catch(InterruptedException ignored){}
    });
    try{
      Path root= tmp.resolve("root");
      UserError.root= root;
      FsDsl.materialize(root, downloadProject(url(server,"/stall"),
        "s.download.downloadStrUtf8(\"$URL\", 1000, NeverRecovers)"));
      var out= coordinator(root).main(root, stLib);
      Assertions.assertTrue(out.contains("Download timed out"), out);
    }
    finally{ server.stop(0); }
  }
}
