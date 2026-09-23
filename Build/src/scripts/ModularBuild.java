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

public class ModularBuild{
  static final Path out= ResolveResource.coordinatorSrc.getParent().getParent().resolve("out").resolve("modular");
  static final Path mods= out.resolve("mods");
  static final Path resources= ResolveResource.coordinatorSrc.getParent().resolve("Build","src","resources");

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
  static void frontendTest(){ test(ResolveResource.frontendSrc, "frontend-test"); }
  static void coordinatorTest(){ test(ResolveResource.coordinatorSrc, "coordinator-test", resources); }
  static void controllerTest(){ test(ResolveResource.controllerSrc, "controller-test"); }
  static void test(Path src, String name, Path... extra){
    var srcs= new ArrayList<>(List.of(src, src.getParent().resolve("test"), src.getParent().resolve("testModule")));
    srcs.addAll(List.of(extra));
    JavacTool.javac(srcs, out.resolve(name), mods);
  }
  static void buildJar(String name, List<Path> srcs){ buildJar(name, srcs, List.of()); }
  static void buildJar(String name, List<Path> srcs, List<String> extraLintDisables){
    var classes= out.resolve(name);
    JavacTool.javac(srcs, classes, mods, extraLintDisables);
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
    JavaTool.runMain(List.of("-ea"), out.resolve("coordinator-test"), mods, "testBuildBase.BaseCacheBuilder", appRoot.toString());
  }

  static void deploy(Path folderOut, List<List<Path>> srcs, String binName, String mainClass, boolean eclipsePlugin) throws InterruptedException{
    var appRoot= folderOut.resolve(binName);
    new PortableApp(ResolveResource.packaging, folderOut, srcs, ResolveResource.stLibPath, ResolveResource.stLibRTPath,
      ResolveResource.coordinatorJars, binName, ResolveResource.versionId, mainClass).build();
    commons();
    frontendMain();
    coordinatorTest();
    deployBaseCache(appRoot);
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
    Fs.cleanDir(classes); Fs.ensureDir(classes);
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
    Fs.cleanDir(res); Fs.ensureDir(res);
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