package integrationTests;

import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.AssertionFailedError;

import coordinator.Coordinator;
import mainCoordinator.ResolveResource;
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
  void testOk(String name){
    try { coordinator().main(freshIntegrationRoot(name));}
    catch (InterruptedException e){ Assertions.fail(e);}
  }
  @Test void helloWorld(){ testOk("helloWorld");}
  @Test void testUnitTests(){ testOk("testUnitTests");}
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

  @Test void aStackTraceNamesTheFearlessTypesMethodsAndLines() throws InterruptedException{
    var out= new StringBuilder();
    var paths= coordinator();
    new Coordinator(){
      public Path rtPath(){    return paths.rtPath(); }
      public Path stLibPath(){ return paths.stLibPath(); }
      public Path modsPath(){  return paths.modsPath(); }
      @Override public void runAllMains(String pkgName, coordinator.OutputOracle o) throws InterruptedException{
        out.append(JavaTool.runMainFromJars(
          List.of("--enable-native-access=ALL-UNNAMED","-DfearlessUser.dir="+o.rootDir().getParent().getParent()),
          o.rootDir().resolve("gen_java"), pkgName+".Main"));
      }
    }.main(freshIntegrationRoot("helloStackTraces"));
    utils.Err.strCmp("""
AAAAh
imm Bar.bar error line: 8 in file _hello/_rank_app.fear
imm Foo.foo error line: 7 in file _hello/_rank_app.fear
imm Hello.main(_) error line: 6 in file _hello/_rank_app.fear
""", out.toString());
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
