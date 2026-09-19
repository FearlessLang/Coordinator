package naiveBackend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.Fs;
import utils.Pos;

final class BytecodeLineFixTest{
  @Test void aCastOnALineNeverGivenAPosIsBlamedOnItsEnclosingCallsLine(@TempDir Path tmp) throws Exception{
    var pos= Pos.of(URI.create("fear:/_app/greet.fear"), 42, 3);
    var fix= new BytecodeLineFix("Greet", "fear:/_app/greet.fear")
      .a("package generated;\n")
      .a("public interface Greet{\n")
      .a("  default Object run(Object p0){\n")
      .a("    var x$= (String)p0;\n")
      .a("    return self", pos)
      .a("(\n")
      .a("    x$);\n  }\n")
      .a("  default Object self(Object v){ return v; }\n")
      .a("  Greet instance= new Greet(){};\n")
      .a("}\n");
    var castLine= runAndCaptureCastLine(tmp, fix);
    assertEquals(42, castLine);
  }
  @Test void aCastInAFileWithNoRegisteredLineAtAllFallsBackToTheSentinelLine(@TempDir Path tmp) throws Exception{
    var fix= new BytecodeLineFix("Greet", "fear:/_app/greet.fear")
      .a("package generated;\n")
      .a("public interface Greet{\n")
      .a("  default Object run(Object p0){\n")
      .a("    var x$= (String)p0;\n")
      .a("    return x$;\n  }\n")
      .a("  Greet instance= new Greet(){};\n")
      .a("}\n");
    var castLine= runAndCaptureCastLine(tmp, fix);
    assertEquals(1000, castLine);
  }
  private static int runAndCaptureCastLine(Path tmp, BytecodeLineFix fix) throws Exception{
    var srcFile= tmp.resolve("Greet.java");
    var classesDir= tmp.resolve("classes");
    Fs.writeUtf8(srcFile, fix.toString());
    Fs.ensureDir(classesDir);
    Fs.runTool("javac", List.of("-d", classesDir.toString(), srcFile.toString()));
    fix.accept(classesDir);
    try (var loader= new URLClassLoader(new URL[]{classesDir.toUri().toURL()})){
      var cls= Class.forName("generated.Greet", true, loader);
      var instance= cls.getField("instance").get(null);
      var run= cls.getMethod("run", Object.class);
      var thrown= assertThrows(InvocationTargetException.class, ()->run.invoke(instance, 5));
      var cause= (ClassCastException)thrown.getCause();
      return cause.getStackTrace()[0].getLineNumber();
    }
  }
}
