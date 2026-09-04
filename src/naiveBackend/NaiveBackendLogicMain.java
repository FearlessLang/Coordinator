package naiveBackend;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import coordinator.CapabilityEnvironment;
import core.OtherPackages;
import docBuilder.HtmlDocBuilder;
import core.E.Literal;
import tools.Fs;
import tools.JavacTool;
import tools.SourceOracle;

public class NaiveBackendLogicMain {
  public void of(String pkgName, SourceOracle oracle, OtherPackages other, List<Literal> core, Path rootDir, Path rtPath, List<Path> extraClasspathDirs, Optional<Path> baseDocLocation, CapabilityEnvironment capabilities){
    var outPath= rootDir.resolve("gen_java",pkgName);
    var docs= new HtmlDocBuilder(oracle,other,core,baseDocLocation);
    var fixers= new Backend(outPath, pkgName, core, docs, capabilities).produceJavaCode();
    assert Files.exists(rtPath): "Missing extra folder: "+rtPath;
    if (pkgName.equals("base")){
    	Fs.copyTreeFlat(rtPath, outPath);
    }
    assert foldDistinct(outPath);
    var classes= rootDir.resolve("gen_java","_classes");
    Fs.ensureDir(classes);
    Fs.cleanDirContents(classes);
    var pkgPath= classes.resolve(pkgName);
    Runnable post= ()->fixers.forEach(f->f.accept(pkgPath));
    var javacOut= Fs.of(()->JavacTool.compileTree(outPath, classes,post,rootDir.resolve("gen_java",pkgName+".jar"),extraClasspathDirs));
    assert javacOut.isEmpty(): javacOut;
    docs.complete();
    Fs.rmTree(outPath); Fs.rmTree(classes);//comment out this line to keep the generated .java and .class files for debugging
  }
  private static boolean foldDistinct(Path dir){
    var seen= new HashMap<String,String>();
    return Fs.walk(dir, s->s.map(p->p.getFileName().toString()).allMatch(n->foldFree(seen,n)));
  }
  private static boolean foldFree(HashMap<String,String> seen, String name){
    var prev= seen.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
    assert prev == null || prev.equals(name):
      "Generated names differ only by case, so they are one file on Windows and macOS: "+prev+" and "+name;
    return true;
  }
}