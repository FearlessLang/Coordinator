package scripts;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import resources.ResolveResource;
import tools.Fs;
import tools.JavacTool;
import tools.JavaTool;
import tools.PortableApp;
import utils.OneOr;
import utils.Push;

public class ModularBuild{
  static final Path root= ResolveResource.coordinatorSrc.getParent().getParent();
  static final Path out= root.resolve("out").resolve("modular");
  static final Path mods= out.resolve("mods");
  static final Path resources= ResolveResource.coordinatorSrc.getParent().resolve("Build","src","resources");

  static void mainJars(){ commons(); frontendMain(); coordinatorMain(); }
  static void commons(){
    Fs.cleanDir(mods);
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
  static Path frontendTest(){ return test(ResolveResource.frontendSrc, "frontend-test"); }
  static Path coordinatorTest(){ return test(ResolveResource.coordinatorSrc, "coordinator-test", resources); }
  static Path controllerTest(){ return test(ResolveResource.controllerSrc, "controller-test", resources); }
  static Path test(Path src, String name, Path... extra){
    JavacTool.javac(Push.<Path>of(List.of(src, src.getParent().resolve("test"), src.getParent().resolve("testModule")), List.of(extra)), out.resolve(name), mods);
    return out.resolve(name);
  }
  static void buildJar(String name, List<Path> srcs){ buildJar(name, srcs, List.of()); }
  static void buildJar(String name, List<Path> srcs, List<String> extraLintDisables){
    var classes= out.resolve(name);
    JavacTool.javac(srcs, classes, mods, extraLintDisables);
    JavacTool.jar(classes, mods.resolve(name+".jar"));
  }

  static void fearlessTour(){
    var tour= root.resolve("FearlessTour");
    Fs.copyTreeFlat(tour.resolve("externalJars"), mods);
    //an automatic module (flexmark has no module-info) pulls every other automatic
    //module into the graph, so this shaded jar's bundled junit classes collide
    //with the real org.junit.jupiter.api module also sitting in mods
    var jars= Fs.walk(mods, s->s.filter(p->p.getFileName().toString().startsWith("junit-platform-console-standalone")).toList());
    Fs.ofV(()->Files.delete(OneOr.of("Expected exactly one junit console jar in "+mods, jars.stream())));
    buildJar("FearlessTour", List.of(tour.resolve("src"), resources), List.of("-requires-automatic"));
  }

  static void runJUnit(Path testClasses, String... extraArgs) throws InterruptedException{
    var args= Push.<String>of(List.of("execute",
      "--class-path", testClasses.toString(), "--scan-class-path="+testClasses,
      "--include-classname=.*", "--details=summary", "--disable-ansi-colors"), List.of(extraArgs));
    JavaTool.runMain(List.of("-ea"), testClasses, mods, "org.junit.platform.console.ConsoleLauncher", args.toArray(String[]::new));
  }

  static void deploy(Path folderOut, List<List<Path>> srcs, String binName, String mainClass, boolean eclipsePlugin) throws InterruptedException{
    var appRoot= folderOut.resolve(binName);
    new PortableApp(ResolveResource.packaging, folderOut, srcs, ResolveResource.stLibPath, ResolveResource.stLibRTPath,
      ResolveResource.coordinatorJars, binName, ResolveResource.versionId, mainClass).build();
    commons();
    frontendMain();
    JavaTool.runMain(List.of("-ea"), coordinatorTest(), mods, "testBuildBase.BaseCacheBuilder", appRoot.toString());
    if (eclipsePlugin){ deployEclipsePlugin(appRoot); }
  }

  static void deployEclipsePlugin(Path appRoot){
    var found= Fs.walk(appRoot, s->s.filter(Files::isDirectory).filter(p->p.getFileName().toString().equals("mods")).toList());
    var appDir= OneOr.of("Expected exactly one 'mods' dir under "+appRoot, found.stream()).getParent();
    Fs.copyFresh(buildEclipsePlugin(), appDir.resolve("eclipsePlugin"));
  }
  //the plugin is loaded by the java the eclipse it drops into runs, which is older than the one
  //everything else here is built with, and it sees the suggest package as plain sources, not as
  //the Controller module: it is the one thing built to a class path and to an older release.
  //The path lint is off because the bundles carry Class-Path entries for jars they ship without.
  static Path buildEclipsePlugin(){
    var plugin= ResolveResource.controllerPluginSrc;
    var classes= out.resolve("eclipsePluginClasses");
    Fs.cleanDir(classes);
    var plugins= ResolveResource.eclipsePlugins;
    var bundles= Fs.walk(plugins, s->s.filter(p->p.getParent().equals(plugins) && p.toString().endsWith(".jar")).map(Path::toString).sorted().toList());
    var args= new ArrayList<>(JavacTool.javacArgs);
    args.addAll(List.of("--release", ResolveResource.eclipseJavaVersion, "-Xlint:-path",
      "-d", classes.toString(),
      "-cp", String.join(File.pathSeparator, bundles)));
    List.of(plugin.resolve("src"), ResolveResource.controllerSrc.resolve("suggest")).forEach(src->
      Fs.walkV(src, s->s.filter(p->p.toString().endsWith(".java")).forEach(p->args.add(p.toString()))));
    Fs.runTool("javac", args);
    var res= out.resolve("eclipsePlugin");
    Fs.cleanDir(res);
    Fs.runTool("jar", List.of("--create",
      "--file", res.resolve(bundleFileName(plugin)).toString(),
      "--manifest", plugin.resolve("META-INF","MANIFEST.MF").toString(),
      "-C", classes.toString(), ".",
      "-C", plugin.toString(), "plugin.xml",
      "-C", plugin.toString(), "icons",
      "-C", plugin.toString(), "syntaxes",
      "-C", plugin.toString(), "themes"));
    return res;
  }
  static String bundleFileName(Path plugin){
    var lines= Fs.readUtf8(plugin.resolve("META-INF","MANIFEST.MF")).lines().toList();
    return manifestValue(lines, "Bundle-SymbolicName").split(";")[0]+"_"+manifestValue(lines, "Bundle-Version")+".jar";
  }
  static String manifestValue(List<String> lines, String key){
    var found= lines.stream().filter(l->l.startsWith(key+": "));
    return OneOr.of("Expected exactly one "+key+" in the plugin manifest", found).substring(key.length()+2).strip();
  }
}