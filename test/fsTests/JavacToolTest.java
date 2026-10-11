package fsTests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.Fs;
import tools.JavacTool;

final class JavacToolTest{
  private static void compiles(Path tmp, String folder){
    var src= tmp.resolve(folder).resolve("src");
    Fs.writeUtf8(src.resolve("A.java"), "class A{}\n");
    var classes= tmp.resolve(folder).resolve("classes");
    Fs.ensureDir(classes);
    var jar= tmp.resolve(folder).resolve("out").resolve("A.jar");
    Fs.ensureDir(jar.getParent());
    JavacTool.compileTree(src, classes, ()->{}, jar, List.of());
    assertTrue(Files.isRegularFile(classes.resolve("A.class")));
  }
  @Test void apostropheInProjectFolder(@TempDir Path tmp){ compiles(tmp, "it's"); }
  @Test void backslashInProjectFolder(@TempDir Path tmp){ compiles(tmp, "a\\b"); }
}
