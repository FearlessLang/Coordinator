// java --module-path ../../../Commons/Commons.jar --add-modules Commons scripts/RunBenchmarks.java [name=StandardLibraryRoot ...] [benchmarkRegex]
// With no name=root pair the benchmarks run on the StandardLibrary of this working copy, as "wc".
package scripts;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import resources.ResolveResource;
import tools.Fs;
import tools.JavaTool;

public class RunBenchmarks{
  public static void main(String[] args) throws InterruptedException{
    ModularBuild.commons();
    ModularBuild.frontendMain();
    ModularBuild.coordinatorMain();
    var stlibs= new ArrayList<String>();
    var include= new ArrayList<String>();
    for (var a: args){ (a.contains("=") ? stlibs : include).add(a); }
    var bench= ResolveResource.controllerSrc.getParent().resolve("benchmarks","src");
    var classes= ModularBuild.out.resolve("benchmarks");
    Fs.cleanDir(classes); Fs.ensureDir(classes);
    var jars= Fs.walk(ModularBuild.mods, s->s.filter(p->p.toString().endsWith(".jar")).map(Path::toString).sorted().collect(Collectors.joining(File.pathSeparator)));
    var javac= new ArrayList<>(List.of("-encoding","UTF-8","-proc:full","-d",classes.toString(),"-s",classes.toString(),"-cp",jars));
    List.of(bench, ModularBuild.resources).forEach(src->Fs.walkV(src, s->s.filter(p->p.toString().endsWith(".java")).forEach(p->javac.add(p.toString()))));
    Fs.runTool("javac", javac);
    var jvmArgs= new ArrayList<>(List.of("-ea"));
    if (!stlibs.isEmpty()){ jvmArgs.add("-D"+"fearless.benchmarks.stlibs="+String.join(";",stlibs)); }
    JavaTool.runMain(jvmArgs, classes, ModularBuild.mods, "benchmarks.Main", include.toArray(String[]::new));
  }
}
