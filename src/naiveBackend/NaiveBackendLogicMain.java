package naiveBackend;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import tools.Fs;
import tools.JavacTool;

public final class NaiveBackendLogicMain{
  public void of(BackendTools tools, List<Path> extraClasspathDirs){
    var out= tools.rootDir();
    var outPath= out.resolve("_java");
    var fix= new Backend(outPath, tools).produceJavaCode();
    var classes= out.resolve("_classes");
    Fs.cleanDir(classes);
    var pkgPath= classes.resolve("_"+tools.pkgName());
    if (tools.pkgName().equals("base")){
      Fs.copyTreeFlat(tools.rtPath(), outPath);
      Fs.copyTreeFlat(tools.rtPath().resolveSibling("fonts"), pkgPath);
    }
    assert foldDistinct(outPath);
    Runnable post= () -> fix.accept(pkgPath);
    var javacOut= Fs.of(() -> JavacTool.compileTree(outPath, classes, post, out.resolve(tools.pkgName()+".jar"), extraClasspathDirs));
    assert javacOut.isEmpty() : javacOut;
    Fs.rmTree(outPath); Fs.rmTree(classes);//comment out this line to keep the generated .java and .class files for debugging
  }
  private static boolean foldDistinct(Path dir){
    var seen= new HashMap<String,String>();
    return Fs.walk(dir, s -> s.map(p -> p.getFileName().toString()).allMatch(n -> foldFree(seen, n)));
  }
  private static boolean foldFree(HashMap<String,String> seen, String name){
    var prev= seen.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
    assert prev==null || prev.equals(name) :
      "Generated names differ only by case, so they are one file on Windows and macOS: "+prev+" and "+name;
    return true;
  }
}
