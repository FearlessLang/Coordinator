package userMessages;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import metaParser.PrettyFileName;
import tools.Fs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class UserErrorTest{
  @Test void pathIsUnchangedForPlainAscii(){
    assertEquals("  C:\\data\\proj", UserError.path("C:\\data\\proj"));
  }
  @Test void pathStaysWithinWhitelistForNonAscii(){
    assertWhitelisted(UserError.path("C:\\Users\\caf\u00e9\\proj"));
  }
  @Test void displayFileNameStaysWithinWhitelist(@TempDir Path tmp){
    Path nonAscii= tmp.resolve("caf\u00e9_\u4e2d\u6587");
    assertWhitelisted(PrettyFileName.displayFileName(nonAscii.toUri()));
  }
  private static void assertWhitelisted(String s){
    assertTrue(s.codePoints().allMatch(cp-> cp < 128 && Fs.allowed.indexOf((char)cp) >= 0), s);
  }
}
