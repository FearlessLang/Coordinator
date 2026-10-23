package fileSupport;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.AssertionFailedError;

import tools.Fs;
import userMessages.UserError;
import utils.Box;

final class StringFilesTest{
  static{ utils.Err.setUp(AssertionFailedError.class, Assertions::assertEquals, Assertions::assertTrue); }
  @SuppressWarnings("serial")
  private static final class Marker extends RuntimeException{}
  @Test void invalidUtf8MessageStaysWithinWhitelistAndUsesPlainDots(@TempDir Path tmp) throws Exception{
    var prefix= "x".repeat(120).getBytes(UTF_8);
    var bytes= Arrays.copyOf(prefix, prefix.length+1);
    bytes[prefix.length] = (byte)0xFF;
    var file= tmp.resolve("bad.fear");
    Fs.write(file, bytes);
    var captured= new Box<String>(null);
    assertThrows(Marker.class, () ->
      StringFiles.read(file, (actionTxt,_) -> { captured.set(actionTxt); throw new Marker(); }));
    var msg= captured.get();
    utils.Err.strCmp("[###]...[###]", msg);
    assertTrue(msg.codePoints().allMatch(cp -> cp < 128 && Fs.allowed.indexOf((char)cp) >= 0), msg);
  }
  @Test void aMissingFileIsAFullyExplainedFailure(@TempDir Path tmp){
    var e= assertThrows(UserError.class, () -> StringFiles.read(tmp.resolve("missing.txt"), UserError.onFileError()));
    utils.Err.strCmp("Fearless could not read this file:[###]", e.getMessage());
  }
  @Test void aNewFileInAMissingFolderIsAFullyExplainedFailure(@TempDir Path tmp){
    var e= assertThrows(UserError.class, () -> StringFiles.writeNew(tmp.resolve("missing", "new.txt"), "text", UserError.onFileError()));
    utils.Err.strCmp("Fearless could not write this file:[###]", e.getMessage());
  }
}
