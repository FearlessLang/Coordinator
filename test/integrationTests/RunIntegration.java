package integrationTests;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import coordinator.OutputOracle;
import core.E.Literal;
import core.OtherPackages;
import fileSupport.JUnitReport;
import naiveBackend.Backend;
import naiveBackend.BackendTools;
import realSourceOracle.RealSourceOracleWithZip;
import realSourceOracle.SourceOracleWithAutoload;
import resources.ResolveResource;
import testBuildBase.BaseCacheBuilder;
import testHelperFs.FsDsl;
import tools.Fs;
import tools.JavaTool;
import tools.JavacTool;
import tools.SourceOracle;
import userMessages.UserError;
import utils.Push;

final class RunIntegration{
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
  Coordinator coordinator(Path project){ return coordinator(project, List.of()); }
  Coordinator coordinator(Path project, List<String> jvmArgs){
    System.setProperty(JavacTool.appDirKey,ResolveResource.stLibPath.getParent()
      .resolve("fearlessArtefact","fearless","app").toString());
    return new Coordinator(){
      public Path modsPath(){  return ResolveResource.coordinatorJars; }
      public Optional<Path> baseCachePath(){ return Optional.of(baseCache); }
      public Path stdLibBase(){ return ResolveResource.stLibPath; }
      public BackendTools backendTools(String pkgName, SourceOracle oracle, OtherPackages other, List<Literal> core, CapabilityEnvironment capabilities){
        return BackendTools.of(pkgName, oracle, other, core, project.resolve(Coordinator.outDir), baseCachePath(), ResolveResource.stLibRTPath, capabilities);
      }
      public String runAllMains(String pkgName, OutputOracle out) throws InterruptedException{
        return Coordinator.runMains(Push.of(Coordinator.runData(out.rootDir().getParent(),stdLibBase()),jvmArgs), Push.of(out.rootDir().resolve("gen_java"),sharedClasspath()), "_"+pkgName+".Main");
      }
    };
  }
  static Path freshIntegrationRoot(String name){
    var root= ResolveResource.integrationTests.resolve(name);
    Fs.rmTree(root.resolve(".fearless_out"));
    return root;
  }
  String run(String name){ return main(freshIntegrationRoot(name), List.of()); }
  String main(Path project, List<String> jvmArgs){
    try{ return coordinator(project, jvmArgs).main(project, stLib);}
    catch(InterruptedException e){ return Assertions.fail(e);}
  }
  void testOk(String name){ unitTestsOk(name, freshIntegrationRoot(name)); }
  void unitTestsOk(String name, Path root){
    var marker= "EndOfMain"+new BigInteger(128, new SecureRandom()).toString(36);
    var out= main(root, List.of("-Dfearless.endMarker="+marker));
    writeJUnitReport(name, root);
    var fails= out.lines().filter(l->l.startsWith("Test failure ")).toList();
    Assertions.assertTrue(fails.isEmpty(), ()->"Fearless unit tests failed in "+name+":\n"+String.join("\n",fails));
    var lines= out.lines().toList();
    var mains= coordinator(root).mains(root, stLib).orElseThrow().size();
    var allCompleted= lines.stream().filter(marker::equals).count() == mains && lines.getLast().equals(marker);
    Assertions.assertTrue(allCompleted, ()->"Not all the "+mains+" mains of "+name+" completed: a main that completes prints the line \""+marker+"\", and the output must end with that line. Output:\n"+out);
  }
  static void writeJUnitReport(String name){ writeJUnitReport(name, ResolveResource.integrationTests.resolve(name)); }
  static void writeJUnitReport(String name, Path root){
    var one= JUnitReport.suite(name, root);
    if (one.isEmpty()){ return; }
    suites.add(one);
    Fs.writeUtf8(reportsFile, JUnitReport.document(String.join("",suites)));
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
  @Test void mapAToPkc(){ utils.Err.strCmp("CTEXT\n", run("map_a_to_pkc"));}
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
  @Test void testingStandardLibrary(){ testOk("testingStandardLibrary");}
  @Test void baseGeneratedExamples(@TempDir Path tmp){
    Path root= tmp.resolve("root");
    UserError.root= root;
    var genDir= root.resolve("_gen");
    Fs.ensureDir(genDir);
    Fs.writeUtf8(genDir.resolve("_rank_app.fear"), Fs.readUtf8(baseTestFile));
    unitTestsOk("baseGeneratedExamples", root);
  }
  @Test void testDocs(){
    utils.Err.strCmp("""
Hello world
/// this string must not become documentation
""", run("testDocs"));
  }
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
      var found= files.filter(p->p.getFileName().toString().startsWith(prefix)).toList();
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
    for (var name: List.of("testGui1","testGui2","testGuiImg","testGuiLive","testBasketball","testTicTacToe")){ compileOk(name); }
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
    utils.Err.strCmp("""
{
  "col.Again": ["_col/more.fear", [], []],
  "col.Hello": ["_col/_rank_app.fear", [], []]
}
""", Fs.readUtf8(mainsInfo(root)));
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
    utils.Err.strCmp("[###]was not recognized by the compiler as auto-imported[###]", out);
    utils.Err.strCmp("[###]REAL ASSET CONTENT[###]", out);
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
  static Path claimsProject(Path tmp, String code) throws IOException{
    Path root= tmp.resolve("root");
    UserError.root= root;
    FsDsl.materialize(root, """
_col/_rank_app.fear
iii
use base.Main as Main;
use base.OpenWith as OpenWith;
use base.Shortcut as Shortcut;
"""+code);
    Fs.ensureDir(root.resolve("_col","icons"));
    Files.write(root.resolve("_col","icons","foo.png"), onePixelPng());
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
    var ex= Assertions.assertThrows(UserError.class, ()->coordinator(root).compile(root, stLib));
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
""");
    coordinator(root).compile(root, stLib);
    utils.Err.strCmp("""
{
  "col.Again": ["_col/_rank_app.fear", [["col.IconsFoo", "_col/icons/foo.png", "", "", ""]], [["col.IconsFoo", "_col/icons/foo.png", "", "", "q"]]],
  "col.Exts": ["_col/_rank_app.fear", [["col.IconsFoo", "_col/icons/foo.png", "", "", "fapp123"]], [["col.IconsFoo", "_col/icons/foo.png", "", "", "fear"], ["col.IconsFoo", "_col/icons/foo.png", "", "", "ffile123"], ["col.IconsFoo", "_col/icons/foo.png", "", "", "abcdefghij012345"], ["col.IconsFoo", "_col/icons/foo.png", "", "", "doc"], ["col.IconsFoo", "_col/icons/foo.png", "", "", "htm"]]],
  "col.Foo": ["_col/_rank_app.fear", [["col.IconsFoo", "_col/icons/foo.png", "", "", "fapp001"], ["col.IconsFoo", "_col/icons/foo.png", "", "", ""]], [["col.IconsFoo", "_col/icons/foo.png", "", "", "q"], ["col.IconsFoo", "_col/icons/foo.png", "", "", ""]]],
  "col.Inherited": ["_col/_rank_app.fear", [["col.IconsFoo", "_col/icons/foo.png", "", "", "fapp042"]], []],
  "col.Paths": ["_col/_rank_app.fear", [["col.IconsFoo", "_col/icons/foo.png", "", "", ""]], [["col.IconsFoo", "_col/icons/foo.png", "", "", "q"]]],
  "col.Two": ["_col/_rank_app.fear", [], [["col.IconsFoo", "_col/icons/foo.png", "", "", "q"], ["base.IconsConflict", "icons/conflict.png", "", "", "b"]]]
}
""", Fs.readUtf8(mainsInfo(root)));
  }
  @Test void aClaimWithoutMainIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
Assoc:OpenWith[IconsFoo,"foo"]{}
""", """
In file: fear:/_col/_rank_app.fear

004| Assoc:OpenWith[IconsFoo,"foo"]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "col.Assoc"
Type declaration "col.Assoc" implements "base.OpenWith[_,_]".
Only a main can open files: type declaration "col.Assoc" must also implement "base.Main", directly or through one of its supertypes."""); }
  @Test void aClaimOnANamedObjectLiteralWithoutMainIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
Test:{ #: I -> I: Shortcut[IconsFoo]{} }
""", """
In file: fear:/_col/_rank_app.fear

004| Test:{ #: I -> I: Shortcut[IconsFoo]{} }
   |                ^^^^^^^^^^^^^^^^^^^^^^^

While inspecting object literal "col.I"
Object literal "col.I" implements "base.Shortcut[_]".
Only a main can open files: object literal "col.I" must also implement "base.Main", directly or through one of its supertypes."""); }
  @Test void aClaimOnAnAnonymousObjectLiteralWithoutMainIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
Test:{ #: Shortcut[IconsFoo] -> { .foo: base.Void -> base.Void } }
""", """
In file: fear:/_col/_rank_app.fear

004| Test:{ #: Shortcut[IconsFoo] -> { .foo: base.Void -> base.Void } }
   |                                 ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting object literal instance of "base.Shortcut[_]"
Object literal instance of "base.Shortcut[_]" implements "base.Shortcut[_]".
Only a main can open files: object literal instance of "base.Shortcut[_]" must also implement "base.Main", directly or through one of its supertypes."""); }
  @Test void aClaimWithoutMainBlamesTheDeclarationAddingIt(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
B:A{}
A:OpenWith[IconsFoo,"q"]{}
M:Main,A{}
""", """
In file: fear:/_col/_rank_app.fear

005| A:OpenWith[IconsFoo,"q"]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "col.A"
Type declaration "col.A" implements "base.OpenWith[_,_]".
Only a main can open files: type declaration "col.A" must also implement "base.Main", directly or through one of its supertypes."""); }
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

While inspecting type declaration "lib.Lib"
Type declaration "lib.Lib" implements "base.Shortcut[_]".
Only a main can open files: type declaration "lib.Lib" must also implement "base.Main", directly or through one of its supertypes."""); }
  @Test void aClaimWithATypeVariableIconIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
A[X:imm]:Main,OpenWith[X,"q"]{}
""", """
In file: fear:/_col/_rank_app.fear

004| A[X:imm]:Main,OpenWith[X,"q"]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "col.A[_]"
Type declaration "col.A[_]" implements `base.OpenWith[X,"q"]`.
The icon "X" is not a concrete type name.
An icon is a type name with no type variables and no generic arguments, like "IconsFoo"."""); }
  @Test void aClaimWithAGenericIconIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
Box[X:imm]:{}
A:Main,Shortcut[Box[IconsFoo]]{}
""", """
In file: fear:/_col/_rank_app.fear

005| A:Main,Shortcut[Box[IconsFoo]]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "col.A"
Type declaration "col.A" implements "base.Shortcut[col.Box[col.IconsFoo]]".
The icon "col.Box[col.IconsFoo]" is not a concrete type name.
An icon is a type name with no type variables and no generic arguments, like "IconsFoo"."""); }
  @Test void aClaimWithATypeVariableExtensionIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
A[X:imm]:Main,OpenWith[IconsFoo,X]{}
""", """
In file: fear:/_col/_rank_app.fear

004| A[X:imm]:Main,OpenWith[IconsFoo,X]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "col.A[_]"
Type declaration "col.A[_]" implements "base.OpenWith[col.IconsFoo,X]".
The extension "X" is not a string literal type.
An extension is written as a string literal type, like `"foo"` or "`foo`"."""); }
  @Test void aClaimWithATypeNameExtensionIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
A:Main,OpenWith[IconsFoo,IconsFoo]{}
""", """
In file: fear:/_col/_rank_app.fear

004| A:Main,OpenWith[IconsFoo,IconsFoo]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "col.A"
Type declaration "col.A" implements "base.OpenWith[col.IconsFoo,col.IconsFoo]".
The extension "col.IconsFoo" is not a string literal type.
An extension is written as a string literal type, like `"foo"` or "`foo`"."""); }
  @Test void aClaimWithANumberExtensionIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
A:Main,Shortcut[IconsFoo,42]{}
""", """
In file: fear:/_col/_rank_app.fear

004| A:Main,Shortcut[IconsFoo,42]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "col.A"
Type declaration "col.A" implements "base.Shortcut[col.IconsFoo,42]".
The extension "42" is not a string literal type.
An extension is written as a string literal type, like `"foo"` or "`foo`"."""); }
  @Test void anUppercaseExtensionIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
A:Main,OpenWith[IconsFoo,"Txt"]{}
""", """
In file: fear:/_col/_rank_app.fear

004| A:Main,OpenWith[IconsFoo,"Txt"]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "col.A"
Type declaration "col.A" implements `base.OpenWith[col.IconsFoo,"Txt"]`.
"Txt" is not a valid extension.
An extension is 1 to 16 characters, each a lowercase letter "a"-"z" or a digit "0"-"9", with no dot; "fearless" is reserved."""); }
  @Test void aMultiDotExtensionIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
A:Main,OpenWith[IconsFoo,"tar.gz"]{}
""", """
In file: fear:/_col/_rank_app.fear

004| A:Main,OpenWith[IconsFoo,"tar.gz"]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "col.A"
Type declaration "col.A" implements `base.OpenWith[col.IconsFoo,"tar.gz"]`.
"tar.gz" is not a valid extension.
An extension is 1 to 16 characters, each a lowercase letter "a"-"z" or a digit "0"-"9", with no dot; "fearless" is reserved."""); }
  @Test void aLeadingDotExtensionIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
A:Main,OpenWith[IconsFoo,".txt"]{}
""", """
In file: fear:/_col/_rank_app.fear

004| A:Main,OpenWith[IconsFoo,".txt"]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "col.A"
Type declaration "col.A" implements `base.OpenWith[col.IconsFoo,".txt"]`.
".txt" is not a valid extension.
An extension is 1 to 16 characters, each a lowercase letter "a"-"z" or a digit "0"-"9", with no dot; "fearless" is reserved."""); }
  @Test void anEmptyExtensionIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
A:Main,OpenWith[IconsFoo,""]{}
""", """
In file: fear:/_col/_rank_app.fear

004| A:Main,OpenWith[IconsFoo,""]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "col.A"
Type declaration "col.A" implements `base.OpenWith[col.IconsFoo,""]`.
"" is not a valid extension.
An extension is 1 to 16 characters, each a lowercase letter "a"-"z" or a digit "0"-"9", with no dot; "fearless" is reserved."""); }
  @Test void aTooLongExtensionIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
A:Main,OpenWith[IconsFoo,"abcdefghij0123456"]{}
""", """
In file: fear:/_col/_rank_app.fear

004| A:Main,OpenWith[IconsFoo,"abcdefghij0123456"]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "col.A"
Type declaration "col.A" implements `base.OpenWith[col.IconsFoo,"abcdefghij0123456"]`.
"abcdefghij0123456" is not a valid extension.
An extension is 1 to 16 characters, each a lowercase letter "a"-"z" or a digit "0"-"9", with no dot; "fearless" is reserved."""); }
  @Test void theFearlessExtensionIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
A:Main,OpenWith[IconsFoo,`fearless`]{}
""", """
In file: fear:/_col/_rank_app.fear

004| A:Main,OpenWith[IconsFoo,`fearless`]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "col.A"
Type declaration "col.A" implements "base.OpenWith[col.IconsFoo,`fearless`]".
"fearless" is not a valid extension.
An extension is 1 to 16 characters, each a lowercase letter "a"-"z" or a digit "0"-"9", with no dot; "fearless" is reserved."""); }
  @Test void anExtensionClaimedTwiceIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
A:Main,Shortcut[IconsFoo,"fapp042"],Shortcut[base.IconsConflict,"fapp042"]{}
""", """
In file: fear:/_col/_rank_app.fear

004| A:Main,Shortcut[IconsFoo,"fapp042"],Shortcut[base.IconsConflict,"fapp042"]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "col.A"
Type declaration "col.A" claims the extension "fapp042" more than once:
both `base.Shortcut[col.IconsFoo,"fapp042"]` and `base.Shortcut[base.IconsConflict,"fapp042"]` claim it.
A main can claim each extension at most once, across all its "base.OpenWith[_,_]" and "base.Shortcut[_,_]", since one extension has one icon."""); }
  @Test void anExtensionClaimedTwiceWithDifferentDelimitersIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
A:Main,OpenWith[IconsFoo,"txt"],OpenWith[IconsFoo,`txt`]{}
""", """
In file: fear:/_col/_rank_app.fear

004| A:Main,OpenWith[IconsFoo,"txt"],OpenWith[IconsFoo,`txt`]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "col.A"
Type declaration "col.A" claims the extension "txt" more than once:
both `base.OpenWith[col.IconsFoo,"txt"]` and "base.OpenWith[col.IconsFoo,`txt`]" claim it.
A main can claim each extension at most once, across all its "base.OpenWith[_,_]" and "base.Shortcut[_,_]", since one extension has one icon."""); }
  @Test void aShortcutOfAnotherExtensionIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
A:Main,Shortcut[IconsFoo,"bar"]{}
""", """
In file: fear:/_col/_rank_app.fear

004| A:Main,Shortcut[IconsFoo,"bar"]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "col.A"
Type declaration "col.A" implements `base.Shortcut[col.IconsFoo,"bar"]`.
"bar" is not a shortcut extension: a shortcut file only starts its main, so it must not look like a document of another program or a file of the project.
A shortcut extension is "fapp" followed by three digits, like "fapp042"; or implement "base.Shortcut[_]" to let the Fearless manager choose one."""); }
  @Test void aShortcutOfAFfileExtensionIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
A:Main,Shortcut[IconsFoo,"ffile042"]{}
""", """
In file: fear:/_col/_rank_app.fear

004| A:Main,Shortcut[IconsFoo,"ffile042"]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "col.A"
Type declaration "col.A" implements `base.Shortcut[col.IconsFoo,"ffile042"]`.
"ffile042" is not a shortcut extension: a shortcut file only starts its main, so it must not look like a document of another program or a file of the project.
A shortcut extension is "fapp" followed by three digits, like "fapp042"; or implement "base.Shortcut[_]" to let the Fearless manager choose one."""); }
  @Test void aShortcutOfAFourDigitFappExtensionIsRefused(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
A:Main,Shortcut[IconsFoo,"fapp0420"]{}
""", """
In file: fear:/_col/_rank_app.fear

004| A:Main,Shortcut[IconsFoo,"fapp0420"]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "col.A"
Type declaration "col.A" implements `base.Shortcut[col.IconsFoo,"fapp0420"]`.
"fapp0420" is not a shortcut extension: a shortcut file only starts its main, so it must not look like a document of another program or a file of the project.
A shortcut extension is "fapp" followed by three digits, like "fapp042"; or implement "base.Shortcut[_]" to let the Fearless manager choose one."""); }
  @Test void anOpenWithNeverClaimsAFappExtension(@TempDir Path tmp) throws Exception{ claimRefused(tmp, """
A:Main,OpenWith[IconsFoo,"fapp042"]{}
""", """
In file: fear:/_col/_rank_app.fear

004| A:Main,OpenWith[IconsFoo,"fapp042"]{}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "col.A"
Type declaration "col.A" implements `base.OpenWith[col.IconsFoo,"fapp042"]`.
"fapp042" is a shortcut extension: "fapp" followed by three digits names the shortcut files of the Fearless manager.
Use an extension of the form "ffile" followed by three digits, like "ffile042", or a system extension, like "htm"; or implement "base.OpenWith[_]" to let the Fearless manager choose one."""); }
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
    Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()+500));
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
    editAndTouch(root.resolve("_col","_rank_app.fear"), "{s->base.Debug#(`conflict`)}", """
{s->base.Debug#(`conflict`)}
Clash:lib.Lib, OpenWith[IconsFoo,"fear"]{s->base.Debug#(`clash`)}""");
    var ex= Assertions.assertThrows(UserError.class, ()->coordinator(root).main(root, stLib));
    utils.Err.strCmp("""
In file: fear:/_col/_rank_app.fear

005| Clash:lib.Lib, OpenWith[IconsFoo,"fear"]{s->base.Debug#(`clash`)}
   | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

While inspecting type declaration "col.Clash"
Type declaration "col.Clash" claims the extension "fear" more than once:
both `base.OpenWith[col.IconsFoo,"fear"]` and `base.OpenWith[base.IconsConflict,"fear"]` claim it.
A main can claim each extension at most once, across all its "base.OpenWith[_,_]" and "base.Shortcut[_,_]", since one extension has one icon.""", ex.getMessage());
  }
  static Path mainsInfo(Path root){ return root.resolve(Coordinator.outDir).resolve("mains.info"); }
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
{
  "col.Foo": ["_col/_rank_app.fear", [["base.IconsConflict", "icons/conflict.png", "", "", "fapp042"], ["col.ZInBar", "_col/z.zip", "", "in/bar.png", ""]], [["col.IconsFoo", "_col/icons/foo.png", "", "", "foo"], ["lib.IconsLib", "_lib/icons/lib.png", "", "", ""]]]
}
""", Fs.readUtf8(mainsInfo(root)));
  }
  @Test void aSecondCompileKeepsTheMainsInfoEntriesOfAnUnchangedPackage(@TempDir Path tmp) throws Exception{
    Path root= tmp.resolve("root");
    UserError.root= root;
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
    editAndTouch(root.resolve("_b","_rank_app.fear"), "B:Main", "C:Main{s->base.Debug#(`c`)}\nB:Main");
    coordinator(root).compile(root, stLib);
    utils.Err.strCmp("""
{
  "a.A": ["_a/_rank_app.fear", [], [["a.IconsA", "_a/icons/a.png", "", "", ""]]],
  "b.B": ["_b/_rank_app.fear", [], []],
  "b.C": ["_b/_rank_app.fear", [], []]
}
""", Fs.readUtf8(mainsInfo(root)));
  }
  @Test void aDeletedPackageMainsInfoForcesARecompile(@TempDir Path tmp) throws Exception{
    var root= claimsProject(tmp, """
Foo:Main, OpenWith[IconsFoo,"foo"]{s->base.Debug#(`foo`)}
""");
    coordinator(root).compile(root, stLib);
    var text= Fs.readUtf8(mainsInfo(root));
    Assertions.assertEquals(Map.of("col",true), Coordinator.pkgsBuilt(root));
    Files.delete(root.resolve(Coordinator.outDir).resolve("col.mains.info"));
    Assertions.assertEquals(Map.of("col",false), Coordinator.pkgsBuilt(root));
    Assertions.assertEquals(Optional.empty(), coordinator(root).mains(root, stLib));
    coordinator(root).compile(root, stLib);
    utils.Err.strCmp(text, Fs.readUtf8(mainsInfo(root)));
    Assertions.assertEquals(Map.of("col.Foo","_col/_rank_app.fear"), coordinator(root).mains(root, stLib).orElseThrow());
  }
  @Test void aPackageReachingTheTopRankListsItsMains(@TempDir Path tmp) throws Exception{
    var root= claimsProject(tmp, """
Foo:Main{s->base.Debug#(`foo`)}
jjj
_lib/_rank_core.fear
iii
Lib:base.Main, base.OpenWith[base.IconsConflict,"fear"]{s->base.Debug#(`lib`)}
""");
    coordinator(root).compile(root, stLib);
    Assertions.assertEquals(Map.of("col.Foo","_col/_rank_app.fear"), coordinator(root).mains(root, stLib).orElseThrow());
    Fs.rmTree(root.resolve("_col"));
    Assertions.assertEquals(Map.of("lib",true), Coordinator.pkgsBuilt(root));
    utils.Err.strCmp("lib\n", coordinator(root).main(root, stLib));
    utils.Err.strCmp("""
{
  "lib.Lib": ["_lib/_rank_core.fear", [], [["base.IconsConflict", "icons/conflict.png", "", "", "fear"]]]
}
""", Fs.readUtf8(mainsInfo(root)));
  }
  @Test void aDeletedMainsInfoListsTheMainsOfEveryTopRankPackage(@TempDir Path tmp) throws Exception{
    var root= claimsProject(tmp, """
Foo:Main{s->base.Debug#(`foo`)}
jjj
_more/_rank_app.fear
iii
More:base.Main{s->base.Debug#(`more`)}
""");
    coordinator(root).compile(root, stLib);
    var text= Fs.readUtf8(mainsInfo(root));
    Files.delete(mainsInfo(root));
    Assertions.assertEquals(Map.of("col",false,"more",false), Coordinator.pkgsBuilt(root));
    coordinator(root).compile(root, stLib);
    utils.Err.strCmp(text, Fs.readUtf8(mainsInfo(root)));
    Assertions.assertEquals(Map.of("col",true,"more",true), Coordinator.pkgsBuilt(root));
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
    Files.move(root.resolve("_lib","icons","lib.png"), root.resolve("_lib","icons","lib.gif"));
    coordinator(root).compile(root, stLib);
    utils.Err.strCmp("""
{
  "col.Foo": ["_col/_rank_app.fear", [], [["lib.IconsLib", "_lib/icons/lib.gif", "", "", ""]]]
}
""", Fs.readUtf8(mainsInfo(root)));
  }
  @Test void inheritedClaimsAreListedOnce(@TempDir Path tmp) throws Exception{
    var root= claimsProject(tmp, """
Opener:Main, OpenWith[IconsFoo,"foo"], Shortcut[IconsFoo]{}
Other:Main, Shortcut[IconsFoo], OpenWith[IconsFoo]{}
Foo:Opener, Other, Shortcut[base.IconsConflict,"fapp042"], OpenWith[IconsFoo]{s->base.Debug#(`foo`)}
""");
    coordinator(root).compile(root, stLib);
    utils.Err.strCmp("""
{
  "col.Foo": ["_col/_rank_app.fear", [["base.IconsConflict", "icons/conflict.png", "", "", "fapp042"], ["col.IconsFoo", "_col/icons/foo.png", "", "", ""]], [["col.IconsFoo", "_col/icons/foo.png", "", "", ""], ["col.IconsFoo", "_col/icons/foo.png", "", "", "foo"]]]
}
""", Fs.readUtf8(mainsInfo(root)));
  }
  @Test void literalTypesInSignaturesCompileRunAndAreReadBackFromTheApiJson(@TempDir Path tmp) throws Exception{
    Path root= tmp.resolve("root");
    UserError.root= root;
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
    editAndTouch(root.resolve("_col","_rank_app.fear"), "(lib.Lit.k.str)", "(lib.Lit.k.str)+`!`");
    utils.Err.strCmp("a\\b\"c5!\n", coordinator(root).main(root, stLib));
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
    editAndTouch(root.resolve("_a/_rank_core.fear"), "\"hi\"", "\"ho\"");
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
    utils.Err.strCmp("[###]from app[###]", out);
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
    var child= Coordinator.startMain(project, ResolveResource.stLibPath, "term.FirstDoesNotWaitForTheRest", c.sharedClasspath(), out::append);
    var waiter= new Thread(()->{ try{ child.await(); } catch(InterruptedException _){ child.kill(); } });
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
  @Test void aMainEndingWithAnErrorExitsWith1AndTheOtherMainsStillRun(@TempDir Path tmp) throws InterruptedException{
    Path root= tmp.resolve("root");
    UserError.root= root;
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
    var cp= Push.of(Coordinator.genJava(root),c.sharedClasspath());
    var out= new StringBuilder();
    Assertions.assertEquals(1, JavaTool.startMainFromJars(Coordinator.runData(root,c.stdLibBase()), cp, "_col.Main", out::append).await(), out::toString);
    Assertions.assertEquals(1, Coordinator.startMain(root, c.stdLibBase(), "col.A", c.sharedClasspath(), out::append).await(), out::toString);
    Assertions.assertEquals(0, Coordinator.startMain(root, c.stdLibBase(), "col.B", c.sharedClasspath(), out::append).await(), out::toString);
  }
  @Test void aMainEndingWithAJavaErrorExitsWith1() throws InterruptedException{
    var root= ResolveResource.integrationTests.resolve("runRecursion");
    if (!recursionCompiled){ compileOk("runRecursion"); recursionCompiled= true; }
    var out= new StringBuilder();
    Assertions.assertEquals(1, Coordinator.startMain(root, ResolveResource.stLibPath, "rec.NaiveSum", coordinator(root).sharedClasspath(), out::append).await(), out::toString);
    utils.Err.strCmp("[###]java.lang.StackOverflowError[###]", out.toString());
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
For package "pkb", the virtual package name "a" is mapped to "nonexistentpkg",
but package "nonexistentpkg" does not exist:
 - fear:/_pka/_rank_app999.fear
   "map  a  as  nonexistentpkg  in  pkb;"
Existing packages: "base", "pka", "pkb".
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
      utils.Err.strCmp("[###]hello download[###]", out);
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
      utils.Err.strCmp("[###]3[###]", out);
    }
    finally{ server.stop(0); }
  }

  @Test void downloadRejectsNonHttpScheme(@TempDir Path tmp) throws Exception{
    Path root= tmp.resolve("root");
    UserError.root= root;
    FsDsl.materialize(root, downloadProject("ftp://127.0.0.1/x",
      "s.download.downloadStrUtf8(\"$URL\", 1000, NeverRecovers)"));
    var out= coordinator(root).main(root, stLib);
    utils.Err.strCmp("[###]Invalid URL descriptor[###]", out);
    utils.Err.strCmp("[###]unsupported scheme[###]", out);
  }

  @Test void downloadRejectsMalformedUrl(@TempDir Path tmp) throws Exception{
    Path root= tmp.resolve("root");
    UserError.root= root;
    FsDsl.materialize(root, downloadProject("not a url",
      "s.download.downloadStrUtf8(\"$URL\", 1000, NeverRecovers)"));
    var out= coordinator(root).main(root, stLib);
    utils.Err.strCmp("[###]Invalid URL descriptor[###]", out);
  }

  @Test void downloadFailsWhenContentLengthExceedsMaxBytes(@TempDir Path tmp) throws Exception{
    var server= startServer(ex->reply(ex,200,"0123456789".getBytes(UTF_8)));
    try{
      Path root= tmp.resolve("root");
      UserError.root= root;
      FsDsl.materialize(root, downloadProject(url(server,"/big"),
        "s.download.downloadBytes(\"$URL\", 4).size"));
      var out= coordinator(root).main(root, stLib);
      utils.Err.strCmp("[###]Download exceeds maxBytes[###]", out);
      utils.Err.strCmp("[###]contentLength[###]", out);
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
      utils.Err.strCmp("[###]Download exceeds maxBytes[###]", out);
      utils.Err.strCmp("[###]bytesRead[###]", out);
    }
    finally{ server.stop(0); }
  }

  @Test void downloadFollowsRedirectsThenSucceeds(@TempDir Path tmp) throws Exception{
    var server= startServer(ex->{
      switch(ex.getRequestURI().getPath()){
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
      utils.Err.strCmp("[###]landed[###]", out);
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
      utils.Err.strCmp("[###]too many redirects[###]", out);
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
      utils.Err.strCmp("[###]Invalid URL descriptor[###]", out);
      utils.Err.strCmp("[###]unsupported scheme[###]", out);
      utils.Err.strCmp("[###]file:///etc/passwd[###]", out);
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
      utils.Err.strCmp("[###]HTTP status: 404[###]", out);
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
      utils.Err.strCmp("[###]caf?[###]", out);
      utils.Err.strCmp("[###]|4[###]", out);
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
      utils.Err.strCmp("[###]3[###]", out);
    }
    finally{ server.stop(0); }
  }

  @Test void downloadTimesOutOnStalledResponse(@TempDir Path tmp) throws Exception{
    var server= startServer(_->{
      try{ Thread.sleep(40_000); } catch(InterruptedException _){}
    });
    try{
      Path root= tmp.resolve("root");
      UserError.root= root;
      FsDsl.materialize(root, downloadProject(url(server,"/stall"),
        "s.download.downloadStrUtf8(\"$URL\", 1000, NeverRecovers)"));
      var out= coordinator(root).main(root, stLib);
      utils.Err.strCmp("[###]Download timed out[###]", out);
    }
    finally{ server.stop(0); }
  }
}
