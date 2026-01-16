package naiveBackend;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import core.E.Literal;
import tools.Fs;
import tools.JavacTool;
import utils.IoErr;

public class NaiveBackendLogicMain {
  public void of(String pkgName, List<Literal> core, Path rootDir, Path rtPath){
    var outPath= rootDir.resolve("gen_java",pkgName);
    new Backend(outPath, pkgName, core).produceJavaCode();
    assert Files.exists(rtPath): "Missing extra folder: "+rtPath;
    if (pkgName.equals("base")){ Fs.copyTree(rtPath, outPath); }
    var classes= rootDir.resolve("gen_java","_classes");
    Fs.ensureDir(classes);
    Fs.cleanDirContents(classes);
    var javacOut= IoErr.of(()->JavacTool.compileTree(outPath, classes,rootDir.resolve("gen_java",pkgName+".jar")));
    assert javacOut.isEmpty();
    }
}