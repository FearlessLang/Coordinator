package integrationTests;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.AssertionFailedError;

import coordinator.Coordinator;
import mainCoordinator.ResolveResource;
import realSourceOracle.SourceOracleWithAutoload;
import testHelperFs.FsDsl;
import java.util.List;
import tools.Fs;
import tools.JavaTool;
import tools.JavacTool;
import userMessages.UserError;

public class RunIntegration {
  static{ utils.Err.setUp(AssertionFailedError.class, Assertions::assertEquals, Assertions::assertTrue); }

  Coordinator coordinator(){
    System.setProperty(JavacTool.appDirKey,ResolveResource.stLibPath.getParent()
      .resolve("fearlessArtefact","fearless","app").toString());
    return new Coordinator(){
      public Path rtPath(){    return ResolveResource.stLibRTPath; }
      public Path stLibPath(){ return ResolveResource.stLibPath; }
      public Path modsPath(){  return ResolveResource.coordinatorJars; }
    };
  }
  static Path freshIntegrationRoot(String name){
    var root= ResolveResource.integrationTests.resolve(name);
    Fs.rmTree(root.resolve(".fearless_out"));
    return root;
  }
  String run(String name){
    try { return coordinator().main(freshIntegrationRoot(name));}
    catch (InterruptedException e){ return Assertions.fail(e);}
  }
  void testOk(String name){
    var out= run(name);
    var fails= out.lines().filter(l->l.startsWith("Test failure ")).toList();
    Assertions.assertTrue(fails.isEmpty(), ()->"Fearless unit tests failed in "+name+":\n"+String.join("\n",fails));
  }
  @Test void helloWorld(){ testOk("helloWorld");}
  //testUnitTests is the project that checks the failure report itself, so it must fail
  @Test void testUnitTests(){
    var fails= run("testUnitTests").lines().filter(l->l.startsWith("Test failure ")).toList();
    utils.Err.strCmp("Test failure MyTests at line: 5 in file: _hello/_rank_app.fear [###]", String.join("\n",fails));
  }
  @Test void map_a_to_pkc(){ testOk("map_a_to_pkc");}
  @Test void helloStackTraces(){ testOk("helloStackTraces");}
  @Test void testingStandardLibrary(){ testOk("testingStandardLibrary");}
  @Test void testDocs(){ testOk("testDocs");}
  @Test void testAssets(){ testOk("testAssets");}
  @Test void testingNorms(){ testOk("testingNorms");}
  //@Test void testGui1(){ testOk("testGui1");}

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
    var ex= Assertions.assertThrows(UserError.class, ()->coordinator().main(root));
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
    coordinator().main(root);
  }

  @Test void anAssetWhoseNameForgesNoValidTypeIsReportedAgainstTheRealFile(@TempDir Path tmp){
    Path root= tmp.resolve("root");
    UserError.root= root;
    FsDsl.materialize(root, """
_col/_rank_app.fear
iii
use base.Main as Main;
Hello:Main{s->base.Debug#(`hi`)}
jjj
_col/_1.txt
iii
hello
""");
    var ex= Assertions.assertThrows(UserError.class, ()->coordinator().main(root));
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
  Examples: "notes.txt" auto-loads as "Notes", "_notes.txt" as "_Notes".

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
Greeting:{ .hi: Str -> `hi` }
jjj
_b/_rank_app.fear
iii
use base.Main as Main;
use a.Greeting as Greeting;
Hello:Main{s->base.Debug#(Greeting.hi)}
""");
    var c= coordinator();
    c.main(root);
    var out= root.resolve(".fearless_out");
    long aBuilt= Fs.lastModified(out.resolve("a.built"));
    long aJson= Fs.lastModified(out.resolve("a.json"));
    long bBuilt= Fs.lastModified(out.resolve("b.built"));
    var aSrc= root.resolve("_a/_rank_core.fear");
    Fs.writeUtf8(aSrc, Fs.readUtf8(aSrc).replace("`hi`","`ho`"));
    Files.setLastModifiedTime(aSrc, FileTime.fromMillis(System.currentTimeMillis()+500));
    c.main(root);

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
Hello:Main{s->base.Debug#(`from core`)}
jjj
_z/_rank_app.fear
iii
use base.Main as Main;
Hello:Main{s->base.Debug#(`from app`)}
""");
    var out= coordinator().main(root);
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
Hello:Main{s->base.Debug#(`from a`)}
jjj
_z/_rank_app.fear
iii
use base.Main as Main;
Hello:Main{s->base.Debug#(`from z`)}
""");
    var out= coordinator().main(root);
    Assertions.assertTrue(out.contains("from a"), out);
    Assertions.assertTrue(out.contains("from z"), out);
  }

  @Test void aStackTraceNamesTheFearlessTypesMethodsAndLines(){
    utils.Err.strCmp("""
AAAAh
imm Bar.bar error line: 8 in file _hello/_rank_app.fear
imm Foo.foo error line: 7 in file _hello/_rank_app.fear
imm Hello.main(_) error line: 6 in file _hello/_rank_app.fear
""", run("helloStackTraces"));
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
    var ex= Assertions.assertThrows(RuntimeException.class, ()->coordinator().main(root));
    utils.Err.strCmp("""
In file: fear:/_pkb/_rank_app200.fear

002| B:{.text:Str->a.C.text;}
   |               ^^^^^^^^^^

While inspecting a type name
Package "nonexistentpkg" does not exist.
Visible packages: "base".
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
Greeting:{ .hi: Str -> `hi` }
""");
    var ex= Assertions.assertThrows(RuntimeException.class, ()->coordinator().main(root));
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
Hello:Main{s->base.Debug#(`hi`)}
""");
    var c= coordinator();
    c.main(root);
    c.main(root);
  }
}
