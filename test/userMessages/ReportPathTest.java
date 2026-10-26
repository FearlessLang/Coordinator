package userMessages;

import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

final class ReportPathTest{
  static{ utils.Err.setUp(AssertionFailedError.class, Assertions::assertEquals, Assertions::assertTrue); }
  static final String names= System.getProperty("sun.jnu.encoding");
  @Test void fileNamesNotUtf8(){
    utils.Err.strCmp("""
Fearless was started with file names read as ANSI_X3.4-1968.

Fearless reads file names as UTF-8. On Linux the locale of a program decides how
it reads them, and the locale of this start is not a UTF-8 one (for example
LANG=C). The launchers Fearless installs set it; from a terminal, start Fearless
with a UTF-8 locale, for example:
  LC_ALL=C.UTF-8 <the command you used>
""", Report.fileNamesNotUtf8("ANSI_X3.4-1968").getMessage());
  }
  @Test void installationNotUtf8(){
    utils.Err.strCmp("""
Fearless cannot start a program from this installation.

Folder:
  [###]fearless

A Fearless program reads file names as UTF-8, and it finds the Java runtime
and the code of Fearless through the name of this folder. This system writes
file names in %s, and the bytes naming this folder are not valid UTF-8,
so the program would look for a folder that does not exist.

Move Fearless to a folder whose path uses only ASCII characters.
""".formatted(names), Report.installationNotUtf8(Path.of("/opt/fearless")).getMessage());
  }
}
