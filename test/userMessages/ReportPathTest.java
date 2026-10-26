package userMessages;

import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

final class ReportPathTest{
  static{ utils.Err.setUp(AssertionFailedError.class, Assertions::assertEquals, Assertions::assertTrue); }
  static final String names= System.getProperty("sun.jnu.encoding");
  @Test void launchPathNotText(){
    utils.Err.strCmp("""
Fearless was given a path that this system could not read as text.

Path, as this system read it:
  /home/me/caf{U+FFFD}/hello

A name on this path holds bytes that are not valid in %s, the encoding
this system uses for file names. The system replaced them with U+FFFD, so the
path no longer names the folder it named.

Give Fearless the path as a file URI, where every byte is written as an escape
(for example file:///home/me/caf%%E9), or rename the folders on the path.
""".formatted(names), Report.launchPathNotText("/home/me/caf\uFFFD/hello").getMessage());
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
