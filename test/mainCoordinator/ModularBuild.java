package mainCoordinator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import tools.Fs;
import tools.JavacTool;
import tools.JavaTool;
import utils.OneOr;

public class ModularBuild{
  static final Path out= ResolveResource.coordinatorSrc.getParent().getParent().resolve("out").resolve("modular");
  static final Path mods= out.resolve("mods");

  static void commons(){
    Fs.cleanDir(mods); Fs.ensureDir(mods);
    Fs.copyTreeFlat(ResolveResource.coordinatorJars, mods);
    Fs.copyTreeFlat(ResolveResource.coordinatorTestJars, mods);
    buildJar("Commons", List.of(ResolveResource.commonsSrc));
  }
  static void frontendMain(){
    buildJar("FearlessFrontend", List.of(ResolveResource.frontendSrc, ResolveResource.frontendSrcModule));
  }
  static void coordinatorMain(){
    buildJar("Coordinator", List.of(ResolveResource.coordinatorSrc, ResolveResource.coordinatorSrcModule));
  }
  static void frontendTest(){
    var fe= ResolveResource.frontendSrc.getParent();
    JavacTool.javac(List.of(ResolveResource.frontendSrc, fe.resolve("test"), fe.resolve("testModule")), out.resolve("frontend-test"), mods);
  }
  static void coordinatorTest(){
    var co= ResolveResource.coordinatorSrc.getParent();
    JavacTool.javac(List.of(ResolveResource.coordinatorSrc, co.resolve("test"), co.resolve("testModule")), out.resolve("coordinator-test"), mods);
  }
  static void controllerTest(){
    var co= ResolveResource.controllerSrc.getParent();
    JavacTool.javac(List.of(ResolveResource.controllerSrc, co.resolve("test"), co.resolve("testModule")), out.resolve("controller-test"), mods);
  }
  static void buildJar(String name, List<Path> srcs){
    var classes= out.resolve(name);
    JavacTool.javac(srcs, classes, mods);
    JavacTool.jar(classes, mods.resolve(name+".jar"));
  }

  static void runJUnit(Path testClasses, String... extraArgs) throws InterruptedException{
    var args= new ArrayList<String>(List.of("execute",
      "--class-path", testClasses.toString(), "--scan-class-path="+testClasses,
      "--include-classname=.*", "--details=summary", "--disable-ansi-colors"));
    args.addAll(List.of(extraArgs));
    JavaTool.runMain(List.of("-ea"), testClasses, mods, "org.junit.platform.console.ConsoleLauncher", args.toArray(String[]::new));
  }

  static void deployBaseCache(Path appRoot) throws InterruptedException{
    JavaTool.runMain(List.of("-ea"), out.resolve("coordinator-test"), mods, "mainCoordinator.BaseCacheBuilder", appRoot.toString());
  }

  static void deployEclipsePlugin(Path appRoot){
    var found= Fs.walk(appRoot, s->s.filter(Files::isDirectory).filter(p->p.getFileName().toString().equals("mods")).toList());
    var appDir= OneOr.of("Expected exactly one 'mods' dir under "+appRoot, found.stream()).getParent();
    Fs.copyFresh(ResolveResource.controllerPluginJars, appDir.resolve("eclipsePlugin"));
  }
}