package mainCoordinator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import tools.Fs;
import tools.JavacTool;
import offensiveUtils.Require;

public class ModularBuild{
  static final Path out= ResolveResource.coordinatorSrc.getParent().getParent().resolve("out").resolve("modular");
  static final Path mods= out.resolve("mods");

  static void commons(){
    Fs.cleanDir(mods); Fs.ensureDir(mods);
    Fs.copyTreeFlat(ResolveResource.coordinatorJars, mods);
    buildJar("Commons", List.of(ResolveResource.commonsSrc));
  }
  static void frontendMain(){
    buildJar("FearlessFrontend", List.of(ResolveResource.frontendSrc, ResolveResource.frontendSrcModule));
  }
  static void frontendTest(){
    var fe= ResolveResource.frontendSrc.getParent();
    JavacTool.javac(List.of(ResolveResource.frontendSrc, fe.resolve("test"), fe.resolve("testModule")), out.resolve("frontend-test"), mods);
  }
  static void coordinatorTest(){
    var co= ResolveResource.coordinatorSrc.getParent();
    JavacTool.javac(List.of(ResolveResource.coordinatorSrc, co.resolve("test"), co.resolve("testModule")), out.resolve("coordinator-test"), mods);
  }
  static void buildJar(String name, List<Path> srcs){
    var classes= out.resolve(name);
    JavacTool.javac(srcs, classes, mods);
    JavacTool.jar(classes, mods.resolve(name+".jar"));
  }

  static void runJUnit(Path testClasses, String... extraArgs) throws IOException, InterruptedException{
    var javaExe= ProcessHandle.current().info().command().orElseThrow();
    var jars= Fs.walk(mods, s->s.filter(p->p.toString().endsWith(".jar")).toList());
    var console= jars.stream().filter(p->p.getFileName().toString().startsWith("junit-platform-console-standalone")).findFirst()
      .orElseThrow(()->new IllegalStateException("No junit-platform-console-standalone*.jar in "+mods));
    var cp= new StringBuilder(testClasses.toString());
    for (var j: jars){ cp.append(File.pathSeparator).append(j); }
    var args= new ArrayList<String>(List.of(javaExe, "-ea", "-jar", console.toString(), "execute",
      "--class-path", cp.toString(), "--scan-class-path="+testClasses, "--include-classname=.*",
      "--details=summary", "--disable-ansi-colors"));
    args.addAll(List.of(extraArgs));
    var p= new ProcessBuilder(args).inheritIO().start();
    Require.check(p.waitFor() == 0, "JUnit run failed for "+testClasses);
  }
}
