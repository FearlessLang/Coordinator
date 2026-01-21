package fsTests;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import tools.Fs;

public class FsStringPathTest{

  // -------- helpers (each helper does exactly ONE call) --------

  private static void okFileNameWithExt(String in,String out){ assertEquals(out, Fs.fileNameWithExtension(in)); }
  private static void okRemoveFileName(String in,String out){ assertEquals(out, Fs.removeFileName(in)); }
  private static void okRemoveFileNameAllowTop(String in,String out){ assertEquals(out, Fs.removeFileNameAllowTop(in)); }
  private static void okFileNameNoExt(String in,String out){ assertEquals(out, Fs.fileNameWithoutExtension(in)); }
  private static void okExtWithDot(String in,String out){ assertEquals(out, Fs.extensionWithDot(in)); }

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

  // -------- removeFileName : success --------

  @Test void rfn1(){ okRemoveFileName("a/b/c","a/b"); }
  @Test void rfn2(){ okRemoveFileName("a/b/c.txt","a/b"); }
  @Test void rfn3(){ okRemoveFileName("fear:/a/b/c.fear","fear:/a/b"); }
  @Test void rfn4(){ okRemoveFileName("a.b/c.z","a.b"); }
  @Test void rfn5(){ okRemoveFileName("a:/b/c","a:/b"); }

  // -------- removeFileName : assertion failures --------

  @Test void rfnA1(){ assertThrows(AssertionError.class,()->Fs.removeFileName("a")); }
  @Test void rfnA2(){ assertThrows(AssertionError.class,()->Fs.removeFileName("abc")); }
  @Test void rfnA3(){ assertThrows(AssertionError.class,()->Fs.removeFileName("a/")); }
  @Test void rfnA4(){ assertThrows(AssertionError.class,()->Fs.removeFileName("/")); }

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

  // -------- extensionWithDot : success --------

  @Test void ext1(){ okExtWithDot("a/b/c.txt",".txt"); }
  @Test void ext2(){ okExtWithDot("a/b/c.tar.gz",".tar.gz"); }
  @Test void ext3(){ okExtWithDot("a.b/c.z",".z"); }
  @Test void ext4(){ okExtWithDot("a/b/c..d","..d"); }
  @Test void ext5(){ okExtWithDot("a/b/.gitignore",".gitignore"); }
  @Test void ext6(){ okExtWithDot("fear:/a/b/c.fear",".fear"); }

  // -------- extensionWithDot : assertion failures --------

  @Test void extA1(){ assertThrows(AssertionError.class,()->Fs.extensionWithDot("a/b/c")); }
  @Test void extA2(){ assertThrows(AssertionError.class,()->Fs.extensionWithDot("a/b/c.")); }
  @Test void extA3(){ assertThrows(AssertionError.class,()->Fs.extensionWithDot("a/b/")); }
  @Test void extA4(){ assertThrows(AssertionError.class,()->Fs.extensionWithDot("abc")); }
}