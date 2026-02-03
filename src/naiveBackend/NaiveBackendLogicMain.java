package naiveBackend;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import core.E.Literal;
import tools.Fs;
import tools.JavacTool;

public class NaiveBackendLogicMain {
  public void of(String pkgName, List<Literal> core, Path rootDir, Path rtPath){
    var outPath= rootDir.resolve("gen_java",pkgName);
    var fixers= new Backend(outPath, pkgName, core).produceJavaCode();
    assert Files.exists(rtPath): "Missing extra folder: "+rtPath;
    if (pkgName.equals("base")){ Fs.copyTree(rtPath, outPath); }
    var classes= rootDir.resolve("gen_java","_classes");
    Fs.ensureDir(classes);
    Fs.cleanDirContents(classes);
    var pkgPath= classes.resolve(pkgName);
    Runnable post= ()->fixers.forEach(f->f.accept(pkgPath));
    var javacOut= Fs.of(()->JavacTool.compileTree(outPath, classes,post,rootDir.resolve("gen_java",pkgName+".jar")));
    assert javacOut.isEmpty();
  }
}