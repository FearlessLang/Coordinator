package coordinator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.AssertionFailedError;

import realSourceOracle.Since;
import tools.Fs;
import tools.SourceOracle;
import userMessages.UserError;

final class CrashTest{
  static{ utils.Err.setUp(AssertionFailedError.class, Assertions::assertEquals, Assertions::assertTrue); }
  static final String crashed= """
Fearless crashed: either this project's compilation cache was corrupted, or this is a Fearless bug.

Details:
java.lang.IllegalStateException: crash
[###]""";
  static final Coordinator crashing= new Coordinator(){
    @Override public SourceOracle sourceOracle(Path path, Since since){ throw new IllegalStateException("crash"); }
  };
  @Test void aCrashOfACheckIsCacheCorruptedAlsoWithNoCompilationCache(@TempDir Path tmp){
    assertFalse(Fs.exists(tmp.resolve(Coordinator.outDir)));
    utils.Err.strCmp(crashed, assertThrows(UserError.class, () -> crashing.check(tmp)).getMessage());
  }
  @Test void aCrashOfACompileIsCacheCorruptedAlsoWithNoCompilationCache(@TempDir Path tmp){
    assertFalse(Fs.exists(tmp.resolve(Coordinator.outDir)));
    var e= assertThrows(UserError.class, () -> crashing.compile(tmp, tmp, "p", _ -> Optional.empty()));
    utils.Err.strCmp(crashed, e.getMessage());
  }
  @Test void aCrashOfFinishAndOfCachedMainsIsCacheCorrupted(@TempDir Path tmp){
    Fs.writeUtf8(tmp.resolve(Coordinator.outDir).resolve("p.json"), "{");
    var corrupted= "Fearless crashed: either this project's compilation cache was corrupted, or this is a Fearless bug.\n\nDetails:\n[###]";
    utils.Err.strCmp(corrupted, assertThrows(UserError.class, () -> Coordinator.cachedMains(tmp)).getMessage());
    utils.Err.strCmp(corrupted, assertThrows(UserError.class, () -> Coordinator.finish(tmp)).getMessage());
  }
}
