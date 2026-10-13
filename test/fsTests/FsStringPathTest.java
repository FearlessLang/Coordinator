package fsTests;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import tools.Fs;

public class FsStringPathTest{

  // -------- helpers (each helper does exactly ONE call) --------

  private static void okFileNameWithExt(String in,String out){ assertEquals(out, Fs.fileNameWithExtension(in)); }
  private static void okRemoveFileNameAllowTop(String in,String out){ assertEquals(out, Fs.removeFileNameAllowTop(in)); }
  private static void okFileNameNoExt(String in,String out){ assertEquals(out, Fs.fileNameWithoutExtension(in)); }

  // -------- fileNameWithExtension : success --------

  @Test void fne1(){ okFileNameWithExt("a/b/c.txt","c.txt"); }
  @Test void fne2(){ okFileNameWithExt("a/b/c","c"); }
  @Test void fne3(){ okFileNameWithExt("a.b/c.z","c.z"); }
  @Test void fne4(){ okFileNameWithExt("fear:/a/b/c.fear","c.fear"); }
  @Test void fne5(){ okFileNameWithExt("/a/b/.gitignore",".gitignore"); }
  @Test void fne6(){ okFileNameWithExt("a:/b/c","c"); }

  // -------- fileNameWithExtension : assertion failures --------

  @Test void fneA1(){ assertThrows(AssertionError.class,()->Fs.fileNameWithExtension("abc")); }
  @Test void fneA2(){ assertThrows(AssertionError.class,()->Fs.fileNameWithExtension("a/b/")); }
  @Test void fneA3(){ assertThrows(AssertionError.class,()->Fs.fileNameWithExtension("/")); }
  @Test void fneA4(){ assertThrows(AssertionError.class,()->Fs.fileNameWithExtension("")); }

  // -------- removeFileNameAllowTop : success --------

  @Test void rfa1(){ okRemoveFileNameAllowTop("a/b/c","a/b"); }
  @Test void rfa2(){ okRemoveFileNameAllowTop("a/b","a"); }
  @Test void rfa3(){ okRemoveFileNameAllowTop("a",""); }
  @Test void rfa4(){ okRemoveFileNameAllowTop("",""); }
  @Test void rfa5(){ okRemoveFileNameAllowTop("a:/b/c","a:/b"); }
  @Test void rfa6(){ okRemoveFileNameAllowTop("a:/b","a:/"); }
  @Test void rfa7(){ okRemoveFileNameAllowTop("a:/","a:/"); }

  // -------- fileNameWithoutExtension : success --------

  @Test void fne0(){ okFileNameNoExt("a/b/c.txt","c"); }
  @Test void fne01(){ okFileNameNoExt("a/b/c.tar.gz","c"); }
  @Test void fne02(){ okFileNameNoExt("fear:/a/b/c.tar.gz","c"); }
  @Test void fne03(){ okFileNameNoExt("a.b/c.z","c"); }
  @Test void fne04(){ okFileNameNoExt("a/b/c..d","c"); }

  // -------- fileNameWithoutExtension : assertion failures --------

  @Test void fneA01(){ assertThrows(AssertionError.class,()->Fs.fileNameWithoutExtension("a/b/c")); }
  @Test void fneA02(){ assertThrows(AssertionError.class,()->Fs.fileNameWithoutExtension("a/b/.gitignore")); }
  @Test void fneA03(){ assertThrows(AssertionError.class,()->Fs.fileNameWithoutExtension("a/b/.a.b")); }
  @Test void fneA04(){ assertThrows(AssertionError.class,()->Fs.fileNameWithoutExtension("a/b/c.")); }
  @Test void fneA05(){ assertThrows(AssertionError.class,()->Fs.fileNameWithoutExtension("a/b/")); }
  @Test void fneA06(){ assertThrows(AssertionError.class,()->Fs.fileNameWithoutExtension("abc")); }
}