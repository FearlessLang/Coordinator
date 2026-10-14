package fsTests;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import tools.Fs;

final class FsExtSegTest{
  private static void ok(String s){ assertTrue(Fs.isExtSeg(s)); }
  private static void ko(String s){ assertFalse(Fs.isExtSeg(s)); }
  @Test void oneChar(){ ok("a"); }
  @Test void fear(){ ok("fear"); }
  @Test void digits(){ ok("fapp042"); }
  @Test void max(){ ok("abcdefgh01234567"); }
  @Test void empty(){ ko(""); }
  @Test void tooLong(){ ko("abcdefgh012345678"); }
  @Test void upper(){ ko("Txt"); }
  @Test void multiDot(){ ko("tar.gz"); }
  @Test void leadingDot(){ ko(".txt"); }
  @Test void dash(){ ko("a-b"); }
}
