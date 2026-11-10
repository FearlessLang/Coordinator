package sourceOracleTests;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import realSourceOracle.Fix;
import realSourceOracle.RealSourceOracleWithZip;
import testHelperFs.FsDsl;
import tools.Fs;
import userMessages.Report;
import userMessages.UserError;

final class FixTest{
  private static Path root(Path tmp){
    var root= tmp.resolve("root").toAbsolutePath().normalize();
    UserError.root = root;
    Fs.ensureDir(root);
    return root;
  }
  private static Path root(Path tmp, String spec){
    var root= root(tmp);
    FsDsl.materialize(root, spec);
    return root;
  }
  private static String fix(Path root){
    var sb= new StringBuilder();
    Fix.of(root, line -> sb.append(line).append("\n"));
    return sb.toString();
  }
  private static String dump(Path root){
    var sb= new StringBuilder();
    for (var p : Fs.walk(root, s -> s.filter(p -> !p.equals(root)).sorted(Comparator.comparing(Path::toString)).toList())){
      var name= root.toUri().relativize(p.toUri()).getPath();
      sb.append(Fs.isDirectory(p) ? name : show(name, Fs.of(() -> Fs.readAllBytes(p)))).append("\n");
    }
    return sb.toString();
  }
  private static String show(String name, byte[] bytes){
    if (name.endsWith("/")){ return name; }
    if (!name.endsWith(".zip")){ return name+"="+text(bytes); }
    var sb= new StringBuilder(name+"[");
    stored(bytes).forEach(e -> sb.append(show(e.getName(), e.getExtra())).append(";"));
    return sb+"]";
  }
  private static String text(byte[] bytes){
    return new String(bytes, ISO_8859_1).replace("\u00EF\u00BB\u00BF", "<mark>").replace("\r", "<cr>").replace("\n", "<lf>");
  }
  private static List<ZipEntry> stored(byte[] bytes){
    var res= new ArrayList<ZipEntry>();
    Fs.ofV(() -> {
      try (var zin= new ZipInputStream(new ByteArrayInputStream(bytes), UTF_8)){
        for (var e= zin.getNextEntry(); e != null; e = zin.getNextEntry()){
          var copy= new ZipEntry(e.getName());
          copy.setTime(e.getTime());
          copy.setExtra(zin.readAllBytes());
          res.add(copy);
        }
      }
    });
    return res;
  }
  private static void put(ZipOutputStream zip, String name, long time, String content) throws IOException{
    var e= new ZipEntry(name);
    e.setTime(time);
    zip.putNextEntry(e);
    zip.write(content.getBytes(UTF_8));
  }
  private static void fixes(Path tmp, String spec, String lines, String after){
    var root= root(tmp, spec);
    assertEquals(lines, fix(root));
    assertEquals(after, dump(root));
    assertEquals("", fix(root));
  }
  @Test void deletesTheDeskFiles(@TempDir Path tmp){ fixes(tmp, """
.DS_Store
iii
x
jjj
._a.fear
iii
x
jjj
__MACOSX/lib/._b.fear
iii
x
jjj
lib/Thumbs.db
iii
x
jjj
lib/sub/DESKTOP.INI
iii
x
jjj
lib/.directory
iii
x
jjj
lib/a.fear~
iii
x
jjj
lib/.Trash-1000/files/a.fear
iii
x
jjj
.git/.DS_Store
iii
x
jjj
.git/config
iii
x
jjj
lib/a.fear
iii
A:{}
jjj
lib/sub/b.txt
iii
b""", """
Deleted .DS_Store
Deleted ._a.fear
Deleted .git/.DS_Store
Deleted __MACOSX
Deleted lib/.Trash-1000
Deleted lib/.directory
Deleted lib/Thumbs.db
Deleted lib/a.fear~
Deleted lib/sub/DESKTOP.INI
""", """
.git/
.git/config=x
lib/
lib/a.fear=A:{}
lib/sub/
lib/sub/b.txt=b
""");}
  @Test void deletesTheIconFileOfAFolder(@TempDir Path tmp){ fixes(tmp, """
lib/Icon\r
iii
jjj
lib/a.txt
iii
a""", """
Deleted lib/Icon\r
""", """
lib/
lib/a.txt=a
""");}
  @Test void keepsAFolderNamedAsADeskFileAndAFileNamedAsADeskFolder(@TempDir Path tmp){ fixes(tmp, """
Thumbs.db/a.txt
iii
a
jjj
__MACOSX
iii
b""", "", """
Thumbs.db/
Thumbs.db/a.txt=a
__MACOSX=b
""");}
  @Test void removesTheByteOrderMarkAndTheCarriageReturnsOfTheVisibleFearFiles(@TempDir Path tmp){ fixes(tmp, """
_app/main.fear
iii
\uFEFFA:{}\r
B:{}\r
jjj
_app/mark.fear
iii
\uFEFFA:{}
jjj
_app/lone.fear
iii
A:{}\rB:{}
jjj
_app/notes.txt
iii
\uFEFFa\r
b
jjj
.old/main.fear
iii
\uFEFFA:{}\r
B:{}""", """
Removed the byte order mark of _app/main.fear
Fixed the line endings of _app/main.fear
Removed the byte order mark of _app/mark.fear
""", """
.old/
.old/main.fear=<mark>A:{}<cr><lf>B:{}
_app/
_app/lone.fear=A:{}<cr>B:{}
_app/main.fear=A:{}<lf>B:{}<cr>
_app/mark.fear=A:{}
_app/notes.txt=<mark>a<cr><lf>b
""");}
  @Test void resetsTheTimesThatAreUnsetOrInTheFuture(@TempDir Path tmp){
    var root= root(tmp, """
img/unset.png
iii
x
jjj
img/future.png
iii
x
jjj
img/fine.png
iii
x
jjj
.git/unset
iii
x""");
    var before= System.currentTimeMillis();
    var fine= before-86_400_000;
    Fs.ofV(() -> Fs.setLastModifiedTime(root.resolve("img/unset.png"), FileTime.fromMillis(0)));
    Fs.ofV(() -> Fs.setLastModifiedTime(root.resolve(".git/unset"), FileTime.fromMillis(0)));
    Fs.ofV(() -> Fs.setLastModifiedTime(root.resolve("img/future.png"), FileTime.fromMillis(before+3_600_000)));
    Fs.ofV(() -> Fs.setLastModifiedTime(root.resolve("img/fine.png"), FileTime.fromMillis(fine)));
    assertEquals("""
Reset the time of .git/unset
Reset the time of img/future.png
Reset the time of img/unset.png
""", fix(root));
    for (var f : List.of("img/unset.png", "img/future.png", ".git/unset")){
      var t= Fs.lastModified(root.resolve(f));
      assertTrue(t>=before-2_000 && t<=System.currentTimeMillis()+2_000, f+" "+t);
    }
    assertEquals(fine/2_000, Fs.lastModified(root.resolve("img/fine.png"))/2_000);
    assertEquals("", fix(root));
  }
  @Test void deletesTheVisibleFoldersLeftWithNoEntry(@TempDir Path tmp){ fixes(tmp, """
old/sub/.DS_Store
iii
x
jjj
empty/
iii
jjj
keep/.hidden/
iii
jjj
.cache/empty/
iii
jjj
a.txt
iii
a""", """
Deleted the empty folder empty
Deleted old/sub/.DS_Store
Deleted the empty folder old/sub
Deleted the empty folder old
""", """
.cache/
.cache/empty/
a.txt=a
keep/
keep/.hidden/
""");}
  @Test void keepsTheProjectFolderLeftWithNoEntry(@TempDir Path tmp){ fixes(tmp, """
.DS_Store
iii
x""", """
Deleted .DS_Store
""", "");}
  @Test void fixesTheEntriesOfAZip(@TempDir Path tmp){ fixes(tmp, """
lib/x.zip/a.fear
iii
A:{}\r
B:{}
jjj
lib/x.zip/.DS_Store
iii
x
jjj
lib/x.zip/__MACOSX/._a.fear
iii
x
jjj
lib/x.zip/__MACOSX/sub/._b.fear
iii
x
jjj
lib/x.zip/old/
iii
jjj
lib/x.zip/gone/sub/Thumbs.db
iii
x
jjj
lib/x.zip/.git/refs/
iii
jjj
lib/x.zip/.git/a.fear
iii
A:{}\r
B:{}
jjj
lib/x.zip/img/
iii
jjj
lib/x.zip/img/logo.png
iii
x""", """
Fixed the line endings of lib/x/a.fear
Deleted lib/x/.DS_Store
Deleted lib/x/__MACOSX
Deleted lib/x/gone/sub/Thumbs.db
Deleted the empty folder lib/x/old
Deleted the empty folder lib/x/gone/sub
Deleted the empty folder lib/x/gone
Deleted the empty folder lib/x/.git/refs
""", """
lib/
lib/x.zip[a.fear=A:{}<lf>B:{};.git/a.fear=A:{}<cr><lf>B:{};img/;img/logo.png=x;]
""");}
  @Test void fixesAZipInsideAZipAndDeletesTheZipsLeftWithNoFile(@TempDir Path tmp){ fixes(tmp, """
lib/x.zip/in/y.zip/a.fear
iii
A:{}\r
B:{}
jjj
lib/x.zip/in/y.zip/._a.fear
iii
x
jjj
lib/x.zip/none.zip/sub/.DS_Store
iii
x
jjj
lib/x.zip/.hidden/z.zip/.DS_Store
iii
x
jjj
gone/sub/w.zip/a/._b
iii
x
jjj
empty/e.zip
iii
jjj
.inv/v.zip/.DS_Store
iii
x
jjj
a.txt
iii
a""", """
Deleted the empty zip empty/e.zip
Deleted the empty folder empty
Deleted gone/sub/w/a/._b
Deleted the empty folder gone/sub/w/a
Deleted the empty zip gone/sub/w.zip
Deleted the empty folder gone/sub
Deleted the empty folder gone
Fixed the line endings of lib/x/in/y/a.fear
Deleted lib/x/in/y/._a.fear
Deleted lib/x/none/sub/.DS_Store
Deleted the empty folder lib/x/none/sub
Deleted the empty zip lib/x/none.zip
""", """
.inv/
.inv/v.zip[.DS_Store=x;]
a.txt=a
lib/
lib/x.zip[in/y.zip[a.fear=A:{}<lf>B:{};];.hidden/z.zip[.DS_Store=x;];]
""");}
  @Test void aRewrittenZipKeepsTheOrderAndTheTimesOfItsFiles(@TempDir Path tmp) throws IOException{
    var root= root(tmp);
    var before= System.currentTimeMillis();
    var fine= 978_307_200_000L;
    var bytes= new ByteArrayOutputStream();
    try (var zip= new ZipOutputStream(bytes, UTF_8)){
      put(zip, "z.txt", fine, "z");
      put(zip, "unset.txt", 0, "u");
      put(zip, ".git/future", before+3_600_000, "f");
      put(zip, "a.txt", fine+2_000, "a");
    }
    Fs.write(root.resolve("x.zip"), bytes.toByteArray());
    assertEquals("""
Reset the time of x/unset.txt
Reset the time of x/.git/future
""", fix(root));
    var after= stored(Fs.readAllBytes(root.resolve("x.zip")));
    assertEquals(List.of("z.txt", "unset.txt", ".git/future", "a.txt"), after.stream().map(ZipEntry::getName).toList());
    assertEquals(fine, after.get(0).getTime());
    assertEquals(fine+2_000, after.get(3).getTime());
    for (var i : List.of(1, 2)){
      var t= after.get(i).getTime();
      assertTrue(t>=before-2_000 && t<=System.currentTimeMillis(), after.get(i).getName()+" "+t);
    }
    assertEquals("", fix(root));
  }
  @Test void leavesAsItIsAZipWithNothingToFix(@TempDir Path tmp) throws IOException{
    var root= root(tmp, """
x.zip/sub/
iii
jjj
x.zip/sub/a.fear
iii
A:{}""");
    var old= Fs.readAllBytes(root.resolve("x.zip"));
    var time= Fs.lastModified(root.resolve("x.zip"));
    assertEquals("", fix(root));
    assertArrayEquals(old, Fs.readAllBytes(root.resolve("x.zip")));
    assertEquals(time, Fs.lastModified(root.resolve("x.zip")));
  }
  @Test void leavesAsItIsAFileThatIsNotAReadableZip(@TempDir Path tmp) throws IOException{
    var root= root(tmp);
    Fs.write(root.resolve("x.zip"), "not a zip".getBytes(UTF_8));
    Fs.write(root.resolve("y.zip"), new byte[0]);
    assertEquals("", fix(root));
    assertEquals("not a zip", Fs.readUtf8(root.resolve("x.zip")));
    assertEquals("", Fs.readUtf8(root.resolve("y.zip")));
  }
  @Test void leavesAsItIsAZipStoringANameTwice(@TempDir Path tmp) throws IOException{
    var root= root(tmp);
    var bytes= new ByteArrayOutputStream();
    try (var zip= new ZipOutputStream(bytes, UTF_8)){
      put(zip, "a/.DS_Store", 0, "x");
      put(zip, "a/b.txt", 0, "b");
      put(zip, "a/b.txt/", 0, "");
    }
    Fs.write(root.resolve("x.zip"), bytes.toByteArray());
    assertEquals("", fix(root));
    assertArrayEquals(bytes.toByteArray(), Fs.readAllBytes(root.resolve("x.zip")));
  }
  @Test void leavesAsItIsAZipStoringAFileUnderAFile(@TempDir Path tmp) throws IOException{
    var root= root(tmp);
    var bytes= new ByteArrayOutputStream();
    try (var zip= new ZipOutputStream(bytes, UTF_8)){
      put(zip, "a", 0, "x");
      put(zip, "a/.DS_Store", 0, "b");
    }
    Fs.write(root.resolve("x.zip"), bytes.toByteArray());
    assertEquals("", fix(root));
    assertArrayEquals(bytes.toByteArray(), Fs.readAllBytes(root.resolve("x.zip")));
  }
  @Test void leavesAsItIsAZipStoringABrokenName(@TempDir Path tmp) throws IOException{
    var root= root(tmp);
    var bytes= new ByteArrayOutputStream();
    try (var zip= new ZipOutputStream(bytes, UTF_8)){
      put(zip, "a/../.DS_Store", 0, "x");
      put(zip, ".DS_Store", 0, "x");
    }
    Fs.write(root.resolve("x.zip"), bytes.toByteArray());
    assertEquals("", fix(root));
    assertArrayEquals(bytes.toByteArray(), Fs.readAllBytes(root.resolve("x.zip")));
  }
  @Test void theProjectFolderIsWellShapedAfterFix(@TempDir Path tmp){
    var root= root(tmp, """
_pkg/a.fear
iii
A:{}\r
B:{}
jjj
_pkg/.DS_Store
iii
x
jjj
_pkg/z.zip/b.fear
iii
\uFEFFB:{}
jjj
_pkg/z.zip/__MACOSX/._b.fear
iii
x
jjj
_pkg/z.zip/empty/
iii
jjj
_pkg/old/
iii""");
    assertThrows(UserError.class, () -> new RealSourceOracleWithZip(root));
    assertEquals("""
Deleted _pkg/.DS_Store
Fixed the line endings of _pkg/a.fear
Deleted the empty folder _pkg/old
Removed the byte order mark of _pkg/z/b.fear
Deleted _pkg/z/__MACOSX
Deleted the empty folder _pkg/z/empty
""", fix(root));
    assertEquals("""
--- fear:/_pkg/a.fear
A:{}
B:{}
--- fear:/_pkg/z/b.fear
B:{}
""", FsDsl.dump(new RealSourceOracleWithZip(root)));
  }
  @Test void endsAtASymbolicLinkAfterTheChangesBeforeThatLink(@TempDir Path tmp) throws IOException{
    var root= root(tmp, """
a/.DS_Store
iii
x
jjj
a/a.txt
iii
a
jjj
c/.DS_Store
iii
x
jjj
c/c.txt
iii
c""");
    try{ Files.createSymbolicLink(root.resolve("c/b"), root.resolve("a")); }
    catch (AccessDeniedException e){
      if (!Fs.isWindows()){ throw e; }
      return;
    }
    Fs.onSymbolicLink = Report::symlinkForbidden;
    var lines= new ArrayList<String>();
    assertThrows(UserError.class, () -> Fix.of(root, lines::add));
    assertEquals(List.of("Deleted a/.DS_Store"), lines);
    assertTrue(Fs.exists(root.resolve("c/.DS_Store")));
  }
}
