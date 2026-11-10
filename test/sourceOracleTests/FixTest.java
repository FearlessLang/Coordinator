package sourceOracleTests;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_16BE;
import static java.nio.charset.StandardCharsets.UTF_16LE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ProcessBuilder.Redirect;
import java.net.URI;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
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
import utils.Join;

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
  private static final long fine= 978_307_200_000L;
  private static void put(ZipOutputStream zip, String name, long time, String content) throws IOException{ put(zip, name, time, content.getBytes(UTF_8)); }
  private static void put(ZipOutputStream zip, String name, long time, byte[] content) throws IOException{
    var e= new ZipEntry(name);
    e.setTime(time);
    zip.putNextEntry(e);
    zip.write(content);
  }
  private static byte[] zipOf(String name, byte[] content) throws IOException{
    var bytes= new ByteArrayOutputStream();
    try (var zip= new ZipOutputStream(bytes, UTF_8)){ put(zip, name, fine, content); }
    return bytes.toByteArray();
  }
  private static List<String> names(Path dir){
    return Fs.of(() -> { try (var s= Fs.list(dir)){ return s.map(p -> p.getFileName().toString()).sorted().toList(); } });
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
.AppleDouble/a.fear
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
lib/ehthumbs.db
iii
x
jjj
lib/EhThumbs_Vista.db
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
lib/~$doc.docx
iii
x
jjj
lib/.~lock.a.ods#
iii
x
jjj
lib/#a.fear#
iii
x
jjj
lib/.a.fear.swp
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
Deleted .AppleDouble
Deleted .DS_Store
Deleted ._a.fear
Deleted .git/.DS_Store
Deleted __MACOSX
Deleted lib/#a.fear#
Deleted lib/.Trash-1000
Deleted lib/.a.fear.swp
Deleted lib/.directory
Deleted lib/.~lock.a.ods#
Deleted lib/EhThumbs_Vista.db
Deleted lib/Thumbs.db
Deleted lib/a.fear~
Deleted lib/ehthumbs.db
Deleted lib/sub/DESKTOP.INI
Deleted lib/~$doc.docx
""", """
.git/
.git/config=x
lib/
lib/a.fear=A:{}
lib/sub/
lib/sub/b.txt=b
""");}
  @Test void deletesTheDeskFilesWithANameThatNotEveryDeskHolds(@TempDir Path tmp){
    for (var name : List.of("Icon\r", "a.txt:Zone.Identifier")){
      var spec= "@/"+name+"\niii\nx\njjj\n@/a.txt\niii\na";
      var lines= "Deleted lib/"+name+"\n";
      if (wrote(() -> tmp.resolve(name), "")){ fixes(tmp.resolve("disk"+name.length()), spec.replace("@", "lib"), lines, "lib/\nlib/a.txt=a\n"); }
      fixes(tmp.resolve("zip"+name.length()), spec.replace("@", "lib.zip"), lines, "lib.zip[a.txt=a;]\n");
    }
  }
  @Test void renamesTheNamesOfTheDiskThatAreNotValidUnicode(@TempDir Path tmp){
    var root= root(tmp);
    var rawByte= wrote(() -> Path.of(URI.create(root.toUri()+"caf%E9.txt")), "raw");
    var loneSurrogate= wrote(() -> root.resolve("caf\uD800.txt"), "lone");
    if (!rawByte || !loneSurrogate){ Fs.writeUtf8(root.resolve("caf\uFFFD.txt"), "real"); }
    var old= names(root);
    var texts= texts(root);
    var fixed= Stream.of("caf_.txt", "caf_2.txt", "caf_3.txt").limit(old.size()).toList();
    assertEquals(Join.of(IntStream.range(0, old.size()).mapToObj(i -> "Renamed "+old.get(i)+" to "+fixed.get(i)+"\n"), "", "", ""), fix(root));
    assertEquals(fixed, names(root));
    assertEquals(texts, texts(root));
    assertEquals("", fix(root));
  }
  private static boolean wrote(Supplier<Path> file, String text){
    try{ Files.writeString(file.get(), text); return true; }
    catch (IOException | InvalidPathException _){ return false; }
  }
  private static List<String> texts(Path dir){ return Fs.walk(dir, s -> s.filter(Fs::isRegularFile).map(Fs::readUtf8).sorted().toList()); }
  @Test void renamesTheInvisibleNamesThatAreNotValidUnicode(@TempDir Path tmp){ fixes(tmp, """
.git/caf\uFFFD
iii
1
jjj
.git/cafx
iii
2
jjj
.caf\uFFFD/My File
iii
3
jjj
z.zip/.git/e\u0301x
iii
4
jjj
z.zip/.git/\u00E9\uFFFD
iii
5
jjj
z.zip/.git/\uFFFD\uFFFD.b
iii
6""", """
Renamed .caf\uFFFD to .cafx
Renamed .git/caf\uFFFD to cafx2
Renamed z/.git/\u00E9\uFFFD to \u00E9x2
Renamed z/.git/\uFFFD\uFFFD.b to xx.b
""", """
.cafx/
.cafx/My File=3
.git/
.git/cafx=2
.git/cafx2=1
z.zip[.git/e\u0301x=4;.git/\u00E9x2=5;.git/xx.b=6;]
""");}
  @Test void neverRenamesToTheNameOfADeskFile(@TempDir Path tmp){ fixes(tmp, """
.Trash\uFFFD1/a
iii
1
jjj
.git/ehthumbs\uFFFDvista.db
iii
2
jjj
.\uFFFDfoo
iii
3
jjj
lib/ehthumbs vista.db
iii
4""", """
Renamed .Trash\uFFFD1 to .Trashx1
Renamed .git/ehthumbs\uFFFDvista.db to ehthumbsxvista.db
Renamed .\uFFFDfoo to .xfoo
Renamed lib/ehthumbs vista.db to ehthumbs_vista2.db
""", """
.Trashx1/
.Trashx1/a=1
.git/
.git/ehthumbsxvista.db=2
.xfoo=3
lib/
lib/ehthumbs_vista2.db=4
""");}
  @Test void neverRenamesToADeviceName(@TempDir Path tmp){ fixes(tmp, """
.git/au�
iii
1
jjj
Com/a.txt
iii
2
jjj
com.zip/b.txt
iii
3""", """
Renamed .git/au� to aux2
Renamed Com to com10
""", """
.git/
.git/aux2=1
com.zip[b.txt=3;]
com10/
com10/a.txt=2
""");}
  @Test void theKelvinSignIsNotTheLetterKOfADeskFile(@TempDir Path tmp){ fixes(tmp, """
lib/DESKTOP.INI
iii
x""", "Renamed lib/DESKTOP.INI to desktop2.ini\n", "lib/\nlib/desktop2.ini=x\n");}
  @Test void aRenameCanBringAPathOverTheLimit(@TempDir Path tmp){
    var name= "a".repeat(196);
    var root= root(tmp, "x.zip/"+name+"\niii\na");
    assertEquals("Renamed x/"+name+" to "+name+".txt\n", fix(root));
    assertEquals("", fix(root));
    utils.Err.strCmp("[###]- The path is longer than 200 characters.[###]", FsDsl.dumpErr(root, assertThrows(UserError.class, () -> new RealSourceOracleWithZip(root))));
  }
  @Test void aZipWrittenAgainLosesItsComment(@TempDir Path tmp) throws IOException{
    var root= root(tmp);
    var bytes= new ByteArrayOutputStream();
    try (var zip= new ZipOutputStream(bytes, UTF_8)){
      zip.setComment("note");
      put(zip, ".DS_Store", fine, "x");
      put(zip, "a.txt", fine, "a");
    }
    Files.write(root.resolve("x.zip"), bytes.toByteArray());
    assertEquals("Deleted x/.DS_Store\n", fix(root));
    assertEquals("x.zip[a.txt=a;]\n", dump(root));
    try (var zip= new ZipFile(root.resolve("x.zip").toFile())){ assertNull(zip.getComment()); }
  }
  @Test void keepsAFolderNamedAsADeskFileAndAFileNamedAsADeskFolder(@TempDir Path tmp){ fixes(tmp, """
.git/Thumbs.db/a.txt
iii
a
jjj
.git/__MACOSX
iii
b""", "", """
.git/
.git/Thumbs.db/
.git/Thumbs.db/a.txt=a
.git/__MACOSX=b
""");}
  @Test void renamesTheVisibleEntriesToTheFixedNames(@TempDir Path tmp){ fixes(tmp, """
My Lib/Main File.FEAR
iii
A:{}
jjj
Lib/A.TXT
iii
a
jjj
img/My Logo.PNG
iii
x
jjj
img/archive.TAR.GZ
iii
x
jjj
img/a.b.c
iii
x
jjj
img/9lives.txt
iii
x
jjj
img/ok_name.txt
iii
x
jjj
.git/Keep Me.TXT
iii
x""", """
Renamed Lib to lib
Renamed lib/A.TXT to a.txt
Renamed My Lib to my_lib
Renamed my_lib/Main File.FEAR to main_file.fear
Renamed img/9lives.txt to p9lives.txt
Renamed img/My Logo.PNG to my_logo.png
Renamed img/a.b.c to a_b.c
Renamed img/archive.TAR.GZ to archive.tar.gz
""", """
.git/
.git/Keep Me.TXT=x
img/
img/a_b.c=x
img/archive.tar.gz=x
img/my_logo.png=x
img/ok_name.txt=x
img/p9lives.txt=x
lib/
lib/a.txt=a
my_lib/
my_lib/main_file.fear=A:{}
""");}
  @Test void addsANumberToAFixedNameThatClashes(@TempDir Path tmp){ fixes(tmp, """
a/my_file.txt
iii
1
jjj
a/my file.txt
iii
2
jjj
a/my-file.txt
iii
3
jjj
Old Lib/x.txt
iii
x
jjj
old_lib.zip/y.txt
iii
y""", """
Renamed Old Lib to old_lib2
Renamed a/my file.txt to my_file2.txt
Renamed a/my-file.txt to my_file3.txt
""", """
a/
a/my_file.txt=1
a/my_file2.txt=2
a/my_file3.txt=3
old_lib.zip[y.txt=y;]
old_lib2/
old_lib2/x.txt=x
""");}
  @Test void guessesTheExtensionOfAFileWithNoExtension(@TempDir Path tmp) throws IOException{
    var root= root(tmp);
    Fs.ensureDir(root.resolve("img"));
    Fs.write(root.resolve("img/Logo"), "\u0089PNG\r\n\u001A\nrest".getBytes(ISO_8859_1));
    Fs.write(root.resolve("img/photo"), "\u00FF\u00D8\u00FF\u00E0rest".getBytes(ISO_8859_1));
    Fs.write(root.resolve("img/anim"), "GIF89arest".getBytes(ISO_8859_1));
    Fs.write(root.resolve("img/old anim"), "GIF87arest".getBytes(ISO_8859_1));
    Fs.write(root.resolve("Makefile"), "all:\n\tjavac\r\n".getBytes(UTF_8));
    Fs.write(root.resolve("empty file"), new byte[0]);
    Fs.write(root.resolve("data"), new byte[]{'a', 0, 'b'});
    Fs.write(root.resolve("latin"), "caf\u00E9".getBytes(ISO_8859_1));
    Fs.write(root.resolve("utf"), "caf\u00E9".getBytes(UTF_8));
    Fs.write(root.resolve("report.final version"), "x".getBytes(UTF_8));
    Fs.write(root.resolve("long.abcdefghijklmnopq"), "x".getBytes(UTF_8));
    Fs.write(root.resolve("short.abcdefghijklmnop"), new byte[]{0});
    assertEquals("""
Renamed Makefile to makefile.txt
Renamed data to data.bin
Renamed empty file to empty_file.txt
Renamed img/Logo to logo.png
Renamed img/anim to anim.gif
Renamed img/old anim to old_anim.gif
Renamed img/photo to photo.jpg
Renamed latin to latin.bin
Renamed long.abcdefghijklmnopq to long_abcdefghijklmnopq.txt
Renamed report.final version to report_final_version.txt
Renamed utf to utf.txt
""", fix(root));
    assertEquals(List.of("data.bin", "empty_file.txt", "img", "latin.bin", "long_abcdefghijklmnopq.txt", "makefile.txt", "report_final_version.txt", "short.abcdefghijklmnop", "utf.txt"), names(root));
    assertEquals(List.of("anim.gif", "logo.png", "old_anim.gif", "photo.jpg"), names(root.resolve("img")));
    assertEquals("", fix(root));
  }
  @Test void cleansTheLineEndingsOfTheVisibleFearFiles(@TempDir Path tmp){ fixes(tmp, """
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
Fixed the line endings of _app/lone.fear
Removed the byte order mark of _app/main.fear
Fixed the line endings of _app/main.fear
Removed the byte order mark of _app/mark.fear
""", """
.old/
.old/main.fear=<mark>A:{}<cr><lf>B:{}
_app/
_app/lone.fear=A:{}<lf>B:{}
_app/main.fear=A:{}<lf>B:{}<lf>
_app/mark.fear=A:{}
_app/notes.txt=<mark>a<cr><lf>b
""");}
  @Test void cleansTheTextOfTheVisibleFearFiles(@TempDir Path tmp) throws IOException{
    var root= root(tmp);
    Fs.ensureDir(root.resolve("_app"));
    Fs.ensureDir(root.resolve(".old"));
    var quotes= "\u201Ca\u201D \u2018b\u2019 \u2013\u2014\u2212\tc\u00E9\u00A0d\u2028e\uFEFFf";
    Fs.write(root.resolve("_app/quotes.fear"), quotes.getBytes(UTF_8));
    Fs.write(root.resolve(".old/quotes.fear"), quotes.getBytes(UTF_8));
    Fs.write(root.resolve("_app/one.fear"), "A:{}\t".getBytes(UTF_8));
    Fs.write(root.resolve("_app/lines.fear"), "a\u000Bb\fc\u0085d\r\uFEFF\ne".getBytes(UTF_8));
    Fs.write(root.resolve("_app/utf16le.fear"), "\uFEFFA:{}\r\n".getBytes(UTF_16LE));
    Fs.write(root.resolve("_app/utf16be.fear"), "\uFEFFB:{}".getBytes(UTF_16BE));
    Fs.write(root.resolve("_app/win.fear"), "A:{ \u0093x\u0094 \u00E9 }".getBytes(ISO_8859_1));
    Fs.write(root.resolve("_app/mark.fear"), "\u00EF\u00BB\u00BFA:{}\u0093".getBytes(ISO_8859_1));
    assertEquals("""
Fixed the line endings of _app/lines.fear
Removed the byte order mark of _app/mark.fear
Cleaned 1 character of _app/mark.fear
Cleaned 1 character of _app/one.fear
Fixed the line endings of _app/quotes.fear
Cleaned 11 characters of _app/quotes.fear
Removed the byte order mark of _app/utf16be.fear
Removed the byte order mark of _app/utf16le.fear
Fixed the line endings of _app/utf16le.fear
Cleaned 3 characters of _app/win.fear
""", fix(root));
    assertEquals("a\nb\nc\nd\ne", Fs.readUtf8(root.resolve("_app/lines.fear")));
    assertEquals("A:{}\"", Fs.readUtf8(root.resolve("_app/mark.fear")));
    assertEquals("A:{} ", Fs.readUtf8(root.resolve("_app/one.fear")));
    assertEquals("\"a\" 'b' --- ce d\nef", Fs.readUtf8(root.resolve("_app/quotes.fear")));
    assertEquals("B:{}", Fs.readUtf8(root.resolve("_app/utf16be.fear")));
    assertEquals("A:{}\n", Fs.readUtf8(root.resolve("_app/utf16le.fear")));
    assertEquals("A:{ \"x\" e }", Fs.readUtf8(root.resolve("_app/win.fear")));
    assertEquals(quotes, Fs.readUtf8(root.resolve(".old/quotes.fear")));
    assertEquals("", fix(root));
  }
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
  @Test void deletesTheFoldersLeftWithNoEntryOutsideTheInvisibleFolders(@TempDir Path tmp){ fixes(tmp, """
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
.gone/
iii
jjj
a.txt
iii
a""", """
Deleted the empty folder .gone
Deleted the empty folder empty
Deleted the empty folder keep/.hidden
Deleted the empty folder keep
Deleted old/sub/.DS_Store
Deleted the empty folder old/sub
Deleted the empty folder old
""", """
.cache/
.cache/empty/
a.txt=a
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
lib/x.zip/.gone/
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
Deleted lib/x/.DS_Store
Deleted the empty folder lib/x/.gone
Deleted lib/x/__MACOSX
Fixed the line endings of lib/x/a.fear
Deleted lib/x/gone/sub/Thumbs.db
Deleted the empty folder lib/x/gone/sub
Deleted the empty folder lib/x/gone
Deleted the empty folder lib/x/old
""", """
lib/
lib/x.zip[a.fear=A:{}<lf>B:{};.git/refs/;.git/a.fear=A:{}<cr><lf>B:{};img/;img/logo.png=x;]
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
Deleted lib/x/in/y/._a.fear
Fixed the line endings of lib/x/in/y/a.fear
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
  @Test void fixesTheStoredNamesOfAZip(@TempDir Path tmp) throws IOException{
    var root= root(tmp);
    var bytes= new ByteArrayOutputStream();
    try (var zip= new ZipOutputStream(bytes, UTF_8)){
      put(zip, "dir\\a.txt", fine, "a");
      put(zip, "/b.txt", fine, "b");
      put(zip, "c//d.txt", fine, "d");
      put(zip, "./e.txt", fine, "e");
      put(zip, "/", fine, "");
      put(zip, "f\\.\\g/", fine, "");
      put(zip, "f/h.txt", fine, "h");
    }
    Fs.write(root.resolve("x.zip"), bytes.toByteArray());
    assertEquals("""
Fixed the stored names of x.zip
Deleted the empty folder x/f/g
""", fix(root));
    assertEquals("""
x.zip[dir/a.txt=a;b.txt=b;c/d.txt=d;e.txt=e;f/h.txt=h;]
""", dump(root));
    assertEquals("", fix(root));
  }
  @Test void renamesTheEntriesOfAZip(@TempDir Path tmp) throws IOException{
    var root= root(tmp);
    var bytes= new ByteArrayOutputStream();
    try (var zip= new ZipOutputStream(bytes, UTF_8)){
      put(zip, "CON.txt", fine, "c");
      put(zip, "Aux/a.txt", fine, "a");
      put(zip, "A.txt", fine, "1");
      put(zip, "a.txt", fine, "2");
      put(zip, "trailing.", fine, "t");
      put(zip, "a.txt:Zone.Identifier", fine, "z");
      put(zip, "In/Y.ZIP", fine, zipOf("B File.FEAR", "B:{}\r\n".getBytes(UTF_8)));
      put(zip, "In/y/k.txt", fine, "k");
    }
    Fs.write(root.resolve("x.zip"), bytes.toByteArray());
    assertEquals("""
Renamed x/A.txt to a2.txt
Renamed x/Aux to paux
Renamed x/CON.txt to pcon.txt
Renamed x/In to in
Renamed x/in/Y.ZIP to y2.zip
Renamed x/in/y2/B File.FEAR to b_file.fear
Fixed the line endings of x/in/y2/b_file.fear
Deleted x/a.txt:Zone.Identifier
Renamed x/trailing. to trailing_.txt
""", fix(root));
    assertEquals("""
x.zip[pcon.txt=c;paux/a.txt=a;a2.txt=1;a.txt=2;trailing_.txt=t;in/y2.zip[b_file.fear=B:{}<lf>;];in/y/k.txt=k;]
""", dump(root));
    assertEquals("", fix(root));
  }
  private static final String mixed= """
@/My Dir/A File.FEAR
iii
\uFEFFA:{}\r
B:{\t}
jjj
@/My Dir/.DS_Store
iii
x
jjj
@/gone/sub/Thumbs.db
iii
x
jjj
@/empty/
iii
jjj
@/.git/refs/
iii
jjj
@/.git/Keep Me
iii
x
jjj
@/.gone/
iii
jjj
@/Notes
iii
n
jjj
@/my file.txt
iii
1
jjj
@/my_file.txt
iii
2""";
  @Test void fixesTheEntriesOfAZipAsTheEntriesOfAFolder(@TempDir Path tmp){
    var disk= root(tmp.resolve("disk"), mixed.replace("@", "d"));
    var zip= root(tmp.resolve("zip"), mixed.replace("@", "d.zip"));
    var lines= """
Deleted the empty folder d/.gone
Renamed d/My Dir to my_dir
Deleted d/my_dir/.DS_Store
Renamed d/my_dir/A File.FEAR to a_file.fear
Removed the byte order mark of d/my_dir/a_file.fear
Fixed the line endings of d/my_dir/a_file.fear
Cleaned 1 character of d/my_dir/a_file.fear
Renamed d/Notes to notes.txt
Deleted the empty folder d/empty
Deleted d/gone/sub/Thumbs.db
Deleted the empty folder d/gone/sub
Deleted the empty folder d/gone
Renamed d/my file.txt to my_file2.txt
""";
    assertEquals(lines, fix(disk));
    assertEquals(lines, fix(zip));
    assertEquals("""
d/
d/.git/
d/.git/Keep Me=x
d/.git/refs/
d/my_dir/
d/my_dir/a_file.fear=A:{}<lf>B:{ }
d/my_file.txt=2
d/my_file2.txt=1
d/notes.txt=n
""", dump(disk));
    assertEquals("""
d.zip[my_dir/a_file.fear=A:{}<lf>B:{ };.git/refs/;.git/Keep Me=x;notes.txt=n;my_file2.txt=1;my_file.txt=2;]
""", dump(zip));
    var files= FsDsl.dump(new RealSourceOracleWithZip(disk));
    assertEquals(files, FsDsl.dump(new RealSourceOracleWithZip(zip)));
    assertEquals("", fix(disk));
    assertEquals("", fix(zip));
  }
  @Test void leavesAsItIsAZipDeeperThan64Levels(@TempDir Path tmp) throws IOException{
    var root= root(tmp);
    var inner= new ByteArrayOutputStream();
    try (var zip= new ZipOutputStream(inner, UTF_8)){
      put(zip, ".DS_Store", fine, "x");
      put(zip, "a.txt", fine, "a");
    }
    var deep= inner.toByteArray();
    for (var i= 0; i<64; i += 1){ deep = zipOf("in.zip", deep); }
    var tooDeep= zipOf("in.zip", deep);
    Fs.write(root.resolve("ok.zip"), deep);
    Fs.write(root.resolve("x.zip"), tooDeep);
    assertEquals("Deleted ok/"+"in/".repeat(64)+".DS_Store\n", fix(root));
    assertArrayEquals(tooDeep, Fs.readAllBytes(root.resolve("x.zip")));
    assertEquals("", fix(root));
  }
  @Test void aRewrittenZipKeepsTheOrderAndTheTimesOfItsFiles(@TempDir Path tmp) throws IOException{
    var root= root(tmp);
    var before= System.currentTimeMillis();
    var bytes= new ByteArrayOutputStream();
    try (var zip= new ZipOutputStream(bytes, UTF_8)){
      put(zip, "z.txt", fine, "z");
      put(zip, "unset.txt", 0, "u");
      put(zip, ".git/future", before+3_600_000, "f");
      put(zip, "a.txt", fine+2_000, "a");
    }
    Fs.write(root.resolve("x.zip"), bytes.toByteArray());
    assertEquals("""
Reset the time of x/.git/future
Reset the time of x/unset.txt
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
  @Test void renamesTheNamesOfAZipThatAreNotUtf8(@TempDir Path tmp) throws IOException{
    var root= root(tmp);
    var bytes= new ByteArrayOutputStream();
    try (var zip= new ZipOutputStream(bytes, ISO_8859_1)){
      put(zip, ".DS_Store", fine, "x");
      put(zip, "caf\u00E9.txt", fine, "b");
      put(zip, ".git/caf\u00E9", fine, "c");
    }
    Fs.write(root.resolve("x.zip"), bytes.toByteArray());
    assertEquals("""
Deleted x/.DS_Store
Renamed x/.git/caf\uFFFD to cafx
Renamed x/caf\uFFFD.txt to caf_.txt
""", fix(root));
    assertEquals("""
x.zip[caf_.txt=b;.git/cafx=c;]
""", dump(root));
    assertEquals("", fix(root));
  }
  @Test void leavesAsItIsAZipSettingTheUtf8FlagForANameThatIsNotUtf8(@TempDir Path tmp) throws IOException{
    var root= root(tmp);
    var bytes= new ByteArrayOutputStream();
    try (var zip= new ZipOutputStream(bytes, UTF_8)){
      put(zip, ".DS_Store", fine, "x");
      put(zip, "caf\u00E9.txt", fine, "b");
    }
    var broken= new String(bytes.toByteArray(), ISO_8859_1).replace("caf\u00C3\u00A9", "caf\u00C3(").getBytes(ISO_8859_1);
    Fs.write(root.resolve("x.zip"), broken);
    assertEquals("", fix(root));
    assertArrayEquals(broken, Fs.readAllBytes(root.resolve("x.zip")));
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
_pkg/Other File.FEAR
iii
C:{}
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
Renamed _pkg/Other File.FEAR to other_file.fear
Fixed the line endings of _pkg/a.fear
Deleted the empty folder _pkg/old
Deleted _pkg/z/__MACOSX
Removed the byte order mark of _pkg/z/b.fear
Deleted the empty folder _pkg/z/empty
""", fix(root));
    assertEquals("""
--- fear:/_pkg/a.fear
A:{}
B:{}
--- fear:/_pkg/other_file.fear
C:{}
--- fear:/_pkg/z/b.fear
B:{}
""", FsDsl.dump(new RealSourceOracleWithZip(root)));
  }
  @Test void aRenameNeverReplacesAnotherFile(@TempDir Path tmp){
    var root= root(tmp);
    Fs.writeUtf8(root.resolve(".a/.DS_Store"), "");
    Fs.writeUtf8(root.resolve(".a/b"), "zero");
    Fs.writeUtf8(root.resolve("\u017F_.txt"), "one");
    var oneName= Fs.exists(root.resolve("s_.txt"));
    Fs.writeUtf8(root.resolve("S!.txt"), "two");
    var lines= new ArrayList<String>();
    if (oneName){ assertEquals(new FileAlreadyExistsException(root.resolve("s_.txt").toString()).toString(), assertThrows(UncheckedIOException.class, () -> Fix.of(root, lines::add)).getCause().toString()); }
    if (!oneName){ Fix.of(root, lines::add); }
    assertEquals(oneName ? List.of("Deleted .a/.DS_Store") : List.of("Deleted .a/.DS_Store", "Renamed S!.txt to s_.txt", "Renamed \u017F_.txt to __.txt"), lines);
    assertEquals(List.of("one", "two", "zero"), texts(root));
  }
  @Test void endsAtASymbolicLinkOrJunctionAfterTheChangesBeforeThatLink(@TempDir Path tmp) throws Exception{
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
    folderLink(root.resolve("c/b"), root.resolve("a"));
    var before= Fs.onSymbolicLink;
    Fs.onSymbolicLink = Report::symlinkForbidden;
    var lines= new ArrayList<String>();
    try{ utils.Err.strCmp("""
Invalid path in this project folder.

Root: [###]
Path: "<root>/c/b"

What went wrong
- This path is a symbolic link/junction.
[###]""", FsDsl.dumpErr(root, assertThrows(UserError.class, () -> Fix.of(root, lines::add)))); }
    finally{ Fs.onSymbolicLink = before; }
    assertEquals(List.of("Deleted a/.DS_Store"), lines);
    assertTrue(Fs.exists(root.resolve("c/.DS_Store")));
  }
  private static void folderLink(Path link, Path target) throws Exception{
    if (!Fs.isWindows()){ Files.createSymbolicLink(link, target); return; }
    assertEquals(0, new ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString(), target.toString()).redirectErrorStream(true).redirectOutput(Redirect.DISCARD).start().waitFor());
  }
}
