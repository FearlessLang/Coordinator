package sourceOracleTests;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_16BE;
import static java.nio.charset.StandardCharsets.UTF_16LE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ProcessBuilder.Redirect;
import java.net.URI;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import realSourceOracle.Fix;
import realSourceOracle.LongPath;
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
  @Test void neverRenamesToTheNameOfAJunkFile(@TempDir Path tmp){ fixes(tmp, """
ehthumbs vista.db
iii
x
jjj""", """
Renamed ehthumbs vista.db to ehthumbs_vista2.db
""", """
ehthumbs_vista2.db=x
""");}
  @Test void neverRenamesToADeviceName(@TempDir Path tmp){ fixes(tmp, """
Com/b.txt
iii
b
jjj
com.zip/y.txt
iii
y""", """
Renamed Com to com10
""", """
com.zip[y.txt=y;]
com10/
com10/b.txt=b
""");}
  @Test void theFixedSegmentLowersOnlyTheLettersAToZ(@TempDir Path tmp){ fixes(tmp, """
\u212Aey.txt
iii
k
jjj""", """
Renamed \u212Aey.txt to _ey.txt
""", """
_ey.txt=k
""");}
  @Test void deletesTheJunkFilesWithANameThatNotEveryDeskHolds(@TempDir Path tmp){
    for (var name : List.of("Icon\r", "a.txt:Zone.Identifier")){
      var spec= "@/"+name+"\niii\nx\njjj\n@/a.txt\niii\na";
      var lines= "Deleted lib/"+name+"\n";
      if (!Fs.isWindows()){ fixes(tmp.resolve("disk"+name.length()), spec.replace("@", "lib"), lines, "lib/\nlib/a.txt=a\n"); }
      fixes(tmp.resolve("zip"+name.length()), spec.replace("@", "lib.zip"), lines, "lib.zip[a.txt=a;]\n");
    }
  }
  @Test void aNameInDecomposedFormIsFixedFromItsBytes(@TempDir Path tmp){
    var spec= "@/cafe\u0301.txt\niii\nx";
    for (var lib : List.of("lib", "lib.zip")){ assertEquals("Renamed lib/cafe\u0301.txt to cafe_.txt\n", fix(root(tmp.resolve(lib), spec.replace("@", lib)))); }
  }
  @Test void theKelvinSignIsNotTheLetterKOfAJunkFile(@TempDir Path tmp){ fixes(tmp, """
lib/DES\u212ATOP.INI
iii
x""", "Renamed lib/DES\u212ATOP.INI to des_top.ini\n", "lib/\nlib/des_top.ini=x\n");}
  @Test void renamesTheVisibleNamesThatAreNotValidUnicode(@TempDir Path tmp){
    var root= root(tmp);
    if (Fs.isLinux()){ Fs.writeUtf8(Path.of(URI.create(root.toUri()+"caf%E9.txt")), "raw"); }
    if (Fs.isWindows()){ Fs.writeUtf8(root.resolve("caf\uD800.txt"), "lone"); }
    Fs.writeUtf8(root.resolve("caf\uFFFD.txt"), "real");
    var old= names(root);
    var texts= texts(root);
    var fixed= Stream.of("caf_.txt", "caf_2.txt", "caf_3.txt").limit(old.size()).toList();
    assertEquals(Join.of(IntStream.range(0, old.size()).mapToObj(i -> "Renamed "+old.get(i)+" to "+fixed.get(i)+"\n"), "", "", ""), fix(root));
    assertEquals(fixed, names(root));
    assertEquals(texts, texts(root));
    assertEquals("", fix(root));
  }
  @Test void movesTheHiddenNamesThatAreNotValidUnicodeToTheFixFolder(@TempDir Path tmp){
    var root= root(tmp);
    Fs.writeUtf8(root.resolve(".git/caf\uFFFD"), "real");
    if (Fs.isLinux()){ Fs.writeUtf8(Path.of(URI.create(root.toUri()+".git/raw%E9")), "raw"); }
    if (Fs.isWindows()){ Fs.writeUtf8(root.resolve(".git/lone\uD800"), "lone"); }
    Fs.writeUtf8(root.resolve(".git/ok"), "ok");
    var gone= names(root.resolve(".git")).stream().filter(n -> !n.equals("ok")).toList();
    assertEquals(Fs.isMac() ? 1 : 2, gone.size());
    var into= tmp.resolve("root_fix").resolve(".git");
    var lines= gone.stream().map(n -> "Moved .git/"+n+" to "+into.resolve(n)+"\n").collect(java.util.stream.Collectors.joining());
    assertEquals(lines, fix(root));
    assertEquals(List.of("ok"), names(root.resolve(".git")));
    assertEquals(gone, names(into));
    assertEquals("", fix(root));
  }
  @Test void movesTheLaterOfTwoHiddenNamesEqualAfterCaseFoldingToTheFixFolder(@TempDir Path tmp){
    var root= root(tmp);
    Fs.writeUtf8(root.resolve(".a/x"), "lower");
    Fs.writeUtf8(root.resolve(".a/\u00E9"), "nfc");
    if (Fs.isLinux()){ Fs.writeUtf8(root.resolve(".a/X"), "upper"); }
    if (!Fs.isMac()){ Fs.writeUtf8(root.resolve(".a/e\u0301"), "nfd"); }
    var into= tmp.resolve("root_fix").resolve(".a");
    var lines= new StringBuilder();
    if (Fs.isLinux()){ lines.append("Moved .a/x to "+into.resolve("x")+"\n"); }
    if (!Fs.isMac()){ lines.append("Moved .a/\u00E9 to "+into.resolve("\u00E9")+"\n"); }
    assertEquals(lines.toString(), fix(root));
    assertEquals("", fix(root));
  }
  @Test void movesAnEntryWhosePathIsTooLongToTheFixFolder(@TempDir Path tmp){
    var visible= "a".repeat(100)+"/"+"b".repeat(100)+".txt";
    var hidden= ".h/"+"c".repeat(120)+"/"+"d".repeat(130);
    var short1= "a".repeat(100)+"/ok.txt";
    var root= root(tmp, visible+"\niii\nv\njjj\n"+hidden+"\niii\nh\njjj\n"+short1+"\niii\no");
    var fixed= tmp.resolve("root_fix");
    assertEquals("Moved "+hidden+" to "+fixed.resolve(hidden)+"\nMoved "+visible+" to "+fixed.resolve(visible)+"\n", fix(root));
    assertEquals("v", Fs.readUtf8(fixed.resolve(visible)));
    assertEquals("h", Fs.readUtf8(fixed.resolve(hidden)));
    assertEquals("", fix(root));
  }
  @Test void renamesAZipNextToAFolderWithItsName(@TempDir Path tmp){ fixes(tmp, """
x/a.txt
iii
a
jjj
x.zip/b.txt
iii
b""", """
Renamed x.zip to x2.zip
""", """
x/
x/a.txt=a
x2.zip[b.txt=b;]
""");}
  @Test void movesAHiddenNameLongerThan255BytesToTheFixFolderOrEndsWhereTheDeskCanNotHoldIt(@TempDir Path tmp) throws IOException{
    var name= ".h"+"\u00E9".repeat(127);
    var root= root(tmp);
    var holds= !Fs.isLinux();
    var zip= stored(List.of(name, "a.txt"), UTF_8);
    Fs.write(root.resolve("x.zip"), zip);
    var to= tmp.resolve("root_fix").resolve("x").resolve(name);
    if (holds){
      assertEquals("Moved x/"+name+" to "+to+"\n", fix(root));
      assertEquals("x.zip[a.txt=x;]\n", dump(root));
      return;
    }
    var lines= new ArrayList<String>();
    assertThrows(UncheckedIOException.class, () -> Fix.of(root, lines::add));
    assertEquals(List.of(), lines);
    assertArrayEquals(zip, Fs.readAllBytes(root.resolve("x.zip")));
  }
  @Test void aRenameCanBringAPathOverTheLimitAndTheEntryMovesToTheFixFolder(@TempDir Path tmp){
    var name= "a".repeat(197);
    var root= root(tmp, name+"\niii\nx");
    assertEquals("Renamed "+name+" to "+name+".txt\nMoved "+name+".txt to "+tmp.resolve("root_fix").resolve(name+".txt")+"\n", fix(root));
    assertEquals(List.of(), names(root));
    assertEquals("", fix(root));
  }
  @Test void aDeletedEntryDoesNotMoveALaterEntryRenamedToItsName(@TempDir Path tmp){ fixes(tmp, """
a_b/
iii
jjj
a\u00E9b/x.txt
iii
x""", """
Deleted the empty folder a_b
Renamed a\u00E9b to a_b
""", """
a_b/
a_b/x.txt=x
""");}
  @Test void theNamesOfAFolderAreHandledInTheOrderOfTheDesk(@TempDir Path tmp){
    var root= root(tmp, "a\uFF01.txt\niii\n1\njjj\na\uD83D\uDE00.txt\niii\n2");
    var first= Fs.isWindows() ? "a\uD83D\uDE00.txt" : "a\uFF01.txt";
    var second= Fs.isWindows() ? "a\uFF01.txt" : "a\uD83D\uDE00.txt";
    assertEquals("Renamed "+first+" to a_.txt\nRenamed "+second+" to a_2.txt\n", fix(root));
  }
  @Test void theDottedAndTheDotlessIAreNotEqualAfterSimpleCaseFolding(@TempDir Path tmp){
    var root= root(tmp, ".h/i\niii\n1\njjj\n.h/\u0131\niii\n2");
    assertEquals(2, names(root.resolve(".h")).size());
    assertEquals("", fix(root));
  }
  @Test void aFixEndingInsideAZipWritesTheZipWithTheChangesDone(@TempDir Path tmp) throws IOException{
    var root= root(tmp);
    var tooLong= ".a/"+"c".repeat(250);
    var longName= ".h"+"\u00E9".repeat(127);
    var holds= !Fs.isLinux();
    Fs.write(root.resolve("x.zip"), stored(List.of(".DS_Store", tooLong, longName), UTF_8));
    var q= tmp.resolve("root_fix").resolve("x");
    var done= List.of("Deleted x/.DS_Store", "Moved x/"+tooLong+" to "+q.resolve(tooLong), "Deleted the empty folder x/.a");
    var lines= new ArrayList<String>();
    if (holds){
      Fix.of(root, lines::add);
      assertEquals(Stream.concat(done.stream(), Stream.of("Moved x/"+longName+" to "+q.resolve(longName), "Deleted the empty zip x.zip")).toList(), lines);
      return;
    }
    assertThrows(UncheckedIOException.class, () -> Fix.of(root, lines::add));
    assertEquals(done, lines);
    assertEquals(List.of(longName), stored(Fs.readAllBytes(root.resolve("x.zip"))).stream().map(ZipEntry::getName).toList());
    assertEquals("x", Fs.readUtf8(q.resolve(tooLong)));
  }
  @Test void aRefusedMoveOutOfAZipLeavesNoPartialCopy(@TempDir Path tmp) throws IOException{
    var root= root(tmp);
    var longName= "\u00E9".repeat(150);
    var holds= !Fs.isLinux();
    var zip= stored(List.of(".H/a.txt", ".h/ok.txt", ".h/"+longName), UTF_8);
    Fs.write(root.resolve("a.zip"), zip);
    var moved= tmp.resolve("root_fix").resolve("a").resolve(".h");
    var lines= new ArrayList<String>();
    if (holds){
      Fix.of(root, lines::add);
      assertEquals(List.of("Moved a/.h to "+moved), lines);
      assertEquals("x", Fs.readUtf8(moved.resolve("ok.txt")));
      return;
    }
    assertThrows(UncheckedIOException.class, () -> Fix.of(root, lines::add));
    assertEquals(List.of(), lines);
    assertTrue(!Files.exists(moved, java.nio.file.LinkOption.NOFOLLOW_LINKS));
    assertArrayEquals(zip, Fs.readAllBytes(root.resolve("a.zip")));
  }
  @Test void aMoveEndsWhenAnEarlierMoveLeftALinkOnItsWay(@TempDir Path tmp) throws Exception{
    var root= root(tmp, "a/a.txt\niii\na\njjj\nx.zip/.h/con\niii\nc");
    folderLink(root.resolve("x"), root.resolve("a"));
    var q= tmp.resolve("root_fix").resolve("x");
    var lines= new ArrayList<String>();
    var e= assertThrows(UncheckedIOException.class, () -> Fix.of(root, lines::add));
    assertEquals(new FileAlreadyExistsException(q.toString()).toString(), e.getCause().toString());
    assertEquals(List.of("Moved x to "+q), lines);
  }
  @Test void aCleanedFileInsideAZipGetsTheCurrentTime(@TempDir Path tmp) throws IOException{
    var root= root(tmp);
    var bytes= new ByteArrayOutputStream();
    try (var zip= new ZipOutputStream(bytes, UTF_8)){ put(zip, "a.fear", fine, "A:{}\r\n"); }
    Fs.write(root.resolve("x.zip"), bytes.toByteArray());
    var before= System.currentTimeMillis();
    assertEquals("Fixed the line endings of x/a.fear\n", fix(root));
    assertTrue(stored(Fs.readAllBytes(root.resolve("x.zip"))).getFirst().getTime() >= before-2_000);
  }
  @Test void endsAtAWriteOfAFileWithMoreThanOneName(@TempDir Path tmp) throws IOException{
    var root= root(tmp);
    Fs.writeUtf8(root.resolve("a.fear"), "A:{}\r\n");
    Files.createLink(tmp.resolve("other.fear"), root.resolve("a.fear"));
    var lines= new ArrayList<String>();
    var e= assertThrows(UncheckedIOException.class, () -> Fix.of(root, lines::add));
    utils.Err.strCmp("[###]this file has more than one name, so writing it changes those other names too", e.getCause().getMessage());
    assertEquals(List.of(), lines);
    assertEquals("A:{}\r\n", Fs.readUtf8(tmp.resolve("other.fear")));
  }
  @Test void fixChangesTheReadOnlyFilesAndFoldersOfTheProject(@TempDir Path tmp){
    var root= root(tmp, "lib/a.fear\niii\nA:{}\njjj\nlib/Bad Name.txt\niii\nb\njjj\n__MACOSX/x/._a\niii\nj");
    Fs.writeUtf8(root.resolve("lib/a.fear"), "A:{}\r\n");
    Stream.of("lib/a.fear", "lib/Bad Name.txt", "lib", "__MACOSX/x/._a", "__MACOSX/x", "__MACOSX").forEach(n -> readOnly(root.resolve(n)));
    assertEquals("Deleted __MACOSX\nRenamed lib/Bad Name.txt to bad_name.txt\nFixed the line endings of lib/a.fear\n", fix(root));
    assertEquals("lib/\nlib/a.fear=A:{}<lf>\nlib/bad_name.txt=b\n", dump(root));
  }
  private static void readOnly(Path p){
    if (Fs.isWindows()){ Fs.ofV(() -> Files.setAttribute(p, "dos:readonly", true)); return; }
    Fs.ofV(() -> Files.setPosixFilePermissions(p, PosixFilePermissions.fromString(Files.isDirectory(p) ? "r-xr-xr-x" : "r--r--r--")));
  }
  @Test void movesTheHiddenNamesOfAZipEndingWithADotOrASpaceOrReservedForADevice(@TempDir Path tmp) throws IOException{
    var root= root(tmp);
    Fs.write(root.resolve("x.zip"), stored(List.of(".h/a.", ".h/b ", ".h/con.txt", ".h/d./nul", "a.txt"), UTF_8));
    var q= tmp.resolve("root_fix")+File.separator+"x"+File.separator+".h"+File.separator;
    assertEquals("Moved x/.h/a. to "+q+"a.\nMoved x/.h/b  to "+q+"b \nMoved x/.h/con.txt to "+q+"con.txt\nMoved x/.h/d. to "+q+"d.\nDeleted the empty folder x/.h\n", fix(root));
    assertEquals(List.of("a.", "b ", "con.txt", "d."), names(tmp.resolve("root_fix").resolve("x").resolve(".h")));
    assertEquals("x.zip[a.txt=x;]\n", dump(root));
  }
  @Test void movesAHiddenNameOfAZipHoldingAColonOrEndsOnWindows(@TempDir Path tmp) throws IOException{
    var root= root(tmp);
    var zip= stored(List.of(".h/a:b", "a.txt"), UTF_8);
    Fs.write(root.resolve("x.zip"), zip);
    var lines= new ArrayList<String>();
    if (Fs.isWindows()){
      assertThrows(UncheckedIOException.class, () -> Fix.of(root, lines::add));
      assertEquals(List.of(), lines);
      assertArrayEquals(zip, Fs.readAllBytes(root.resolve("x.zip")));
      assertEquals(List.of(), texts(tmp.resolve("root_fix")));
      return;
    }
    var q= tmp.resolve("root_fix").resolve("x").resolve(".h");
    Fix.of(root, lines::add);
    assertEquals(List.of("Moved x/.h/a:b to "+q.resolve("a:b"), "Deleted the empty folder x/.h"), lines);
  }
  @Test void fixRenamesAndMovesTheNamesEndingWithADotOrASpaceOrReservedForADevice(@TempDir Path tmp){
    var root= root(tmp, "a.txt\niii\na");
    made(root, ".d./x", "x");
    made(root, ".h ", "h");
    made(root, "b.", "b");
    made(root, "con", "c");
    made(root, "con.txt~", "j");
    var q= tmp.resolve("root_fix")+File.separator;
    assertEquals("Moved .d. to "+q+".d.\nMoved .h  to "+q+".h \nRenamed b. to b_.txt\nRenamed con to pcon.txt\nDeleted con.txt~\n", fix(root));
    assertEquals("a.txt=a\nb_.txt=b\npcon.txt=c\n", dump(root));
    assertEquals(List.of(".d.", ".h "), names(tmp.resolve("root_fix")));
    assertEquals("", fix(root));
  }
  private static void made(Path root, String name, String text){
    if (!Fs.isWindows()){ Fs.writeUtf8(root.resolve(name), text); return; }
    var top= name.split("/")[0];
    var mid= root.resolveSibling("made");
    Fs.writeUtf8(mid.resolve(name.substring(top.length()).replaceFirst("^/", "")), text);
    Fs.ofV(() -> LongPath.move(LongPath.of(mid), LongPath.of(root, top)));
  }
  @Test void aWindowsPlaceholderIsAFileAndALinkOfWslIsAnOther(){
    if (!Fs.isWindows()){ return; }
    fail("""
      Agent on Windows: FolderShapesDesign takes a OneDrive placeholder, a compressed file and a deduplicated file as files,
      and a symbolic link of WSL, an app execution alias, and a socket, a fifo or a device of WSL as others.
      Fix keeps a reparse point that is not a link as a file (Fix.Disk.kind), but the check takes only Fs.isRegularFile as a file (BuildWithZip.collectFile), so the check likely still refuses a placeholder after Fix.
      Make a cloud placeholder (CfRegisterSyncRoot and CfCreatePlaceholders, no administrator rights) and a link of WSL (wsl ln -s),
      then make this test check that the check accepts the placeholder, that Fix leaves the placeholder, and that Fix moves the link of WSL to the fix folder.""");
  }
  @Test void fixWritesAFileOnAVolumeThatHoldsNoHardLinks(){
    if (!Fs.isWindows()){ return; }
    fail("""
      Agent on Windows: Fix counts the names of a file with "fsutil hardlink list" (Fix.Disk.names), and takes a FAT volume as holding no hard links.
      FolderShapesDesign counts a file as having one name when fsutil fails because the volume holds no hard links.
      Investigate fsutil hardlink list on a shared folder of the network, on a ReFS volume and on a FAT volume (a VHD, or a USB stick),
      then make this test check that Fix cleans a .fear file on each of those volumes.""");
  }
  @Test void aFolderWhereAFileSystemIsMountedMakesTheProjectInvalidAndEndsFix(@TempDir Path tmp) throws Exception{
    var root= root(tmp, "a.txt\niii\na");
    var at= root.resolve("sub");
    Fs.ensureDir(at);
    try (var _= fileSystemAt(tmp, at)){
      Fs.writeUtf8(at.resolve("c.txt"), "c");
      var e= assertThrows(UserError.class, () -> new RealSourceOracleWithZip(root));
      utils.Err.strCmp("[###]A file system is mounted on this folder[###]", e.getMessage());
      var lines= new ArrayList<String>();
      var end= assertThrows(UncheckedIOException.class, () -> Fix.of(root, lines::add));
      utils.Err.strCmp("[###]a file system is mounted on this entry or inside it", end.getCause().getMessage());
      assertEquals(List.of(), lines);
      assertEquals("c", Fs.readUtf8(at.resolve("c.txt")));
    }
  }
  @Test void fixDoesNotDeleteAFolderThatIsAlsoMountedAtAnotherPlace(@TempDir Path tmp) throws Exception{
    if (Fs.isMac()){ fail("""
      Agent on macOS: investigate whether macOS can show a folder at a second place (bindfs, mount_nullfs, a share of this machine mounted on this machine),
      and how Fix can tell that an entry holds a folder shown at another place.
      Fix must refuse to delete or move such an entry (FolderShapesDesign: the desk refuses delete(D, p s) when a mount point m has inside(D, m, p s)).
      Then make this test show that folder at a second place, as it does on Linux."""); }
    if (Fs.isWindows()){ fail("""
      Agent on Windows: investigate how Fix can tell that an entry holds a folder shown at another place:
      a drive made by "subst X: <folder>" (no administrator rights needed), a shared folder of this machine mapped as a drive.
      Fix must refuse to delete or move such an entry (FolderShapesDesign: the desk refuses delete(D, p s) when a mount point m has inside(D, m, p s)).
      Then make this test show that folder at a second place, as it does on Linux."""); }
    var root= root(tmp, "a.txt\niii\na\njjj\n__MACOSX/b/c.txt\niii\nc");
    var elsewhere= tmp.resolve("elsewhere");
    Fs.ensureDir(elsewhere);
    try (var _= bound(root.resolve("__MACOSX/b"), elsewhere)){
      var lines= new ArrayList<String>();
      var end= assertThrows(UncheckedIOException.class, () -> Fix.of(root, lines::add));
      utils.Err.strCmp("[###]this entry, or an entry inside it, is also mounted at another place", end.getCause().getMessage());
      assertEquals(List.of(), lines);
      assertEquals("c", Fs.readUtf8(elsewhere.resolve("c.txt")));
    }
  }
  private static final String needsRoot= "This test mounts a file system with \"sudo -n mount\": it needs a user that runs sudo with no password on this desk.";
  private static AutoCloseable bound(Path folder, Path at){
    run(needsRoot, "sudo", "-n", "mount", "--bind", folder.toString(), at.toString());
    return () -> run(needsRoot, "sudo", "-n", "umount", at.toString());
  }
  private static AutoCloseable fileSystemAt(Path tmp, Path at){
    if (Fs.isWindows()){ fail("""
      Agent on Windows: investigate how this test can mount a file system on a folder of the project (a VHD with Mount-DiskImage and Add-PartitionAccessPath, or mountvol: both need administrator rights).
      The check must then give the error Report.mountPoint, and Fix must end at that folder with "a file system is mounted on this entry or inside it" (FolderShapesDesign: okEntry, and the desk refuses move(D, p s, _) when a mount point m has same(D, m, p s)).
      Fix reads the mount points from the volumes of the system (LongPath.mountPoints), but the check walks the folders with Fs.walk, which may take such a folder for a junction (Fs.isLink) and then gives the error of a link."""); }
    if (Fs.isLinux()){
      var other= tmp.resolve("other");
      Fs.ensureDir(other);
      return bound(other, at);
    }
    var image= tmp.resolve("disk.dmg").toString();
    var why= "This test mounts a disk image with hdiutil.";
    run(why, "hdiutil", "create", "-size", "1m", "-fs", "HFS+", "-volname", "fearless_test", image);
    run(why, "hdiutil", "attach", "-nobrowse", "-mountpoint", at.toString(), image);
    return () -> run(why, "hdiutil", "detach", at.toString());
  }
  private static void run(String why, String... cmd){
    var p= Fs.of(() -> new ProcessBuilder(cmd).redirectErrorStream(true).start());
    var out= new String(Fs.of(() -> p.getInputStream().readAllBytes()));
    assertEquals(0, p.onExit().join().exitValue(), why+"\n"+String.join(" ", cmd)+"\n"+out);
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
    try (var zip= new java.util.zip.ZipFile(root.resolve("x.zip").toFile())){ assertEquals(null, zip.getComment()); }
  }
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
  @Test void movesAZipDeeperThan64LevelsToTheFixFolder(@TempDir Path tmp) throws IOException{
    var root= root(tmp);
    var inner= new ByteArrayOutputStream();
    try (var zip= new ZipOutputStream(inner, UTF_8)){
      put(zip, ".DS_Store", fine, "x");
      put(zip, "a.txt", fine, "a");
    }
    var deep= inner.toByteArray();
    for (var i= 0; i<64; i += 1){ deep = zipOf("in.zip", deep); }
    Fs.write(root.resolve("ok.zip"), deep);
    Fs.write(root.resolve("x.zip"), zipOf("in.zip", deep));
    var x= "x/"+"in/".repeat(64);
    var moved= tmp.resolve("root_fix").resolve(x+"in.zip");
    var lines= new StringBuilder("Deleted ok/"+"in/".repeat(64)+".DS_Store\nMoved "+x+"in.zip to "+moved+"\n");
    for (var k= 63; k>=0; k -= 1){ lines.append("Deleted the empty zip x/"+"in/".repeat(k)+"in.zip\n"); }
    lines.append("Deleted the empty zip x.zip\n");
    assertEquals(lines.toString(), fix(root));
    assertArrayEquals(inner.toByteArray(), Fs.readAllBytes(moved));
    assertEquals(List.of("ok.zip"), names(root));
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
  @Test void movesAFileThatIsNotAZipFileToTheFixFolder(@TempDir Path tmp) throws IOException{
    var root= root(tmp);
    Fs.write(root.resolve("x.zip"), "not a zip".getBytes(UTF_8));
    Fs.write(root.resolve("y.zip"), new byte[0]);
    var fixed= tmp.resolve("root_fix");
    assertEquals("Moved x.zip to "+fixed.resolve("x.zip")+"\nMoved y.zip to "+fixed.resolve("y.zip")+"\n", fix(root));
    assertEquals("x.zip=not a zip\ny.zip=\n", dumpFiles(fixed));
    assertEquals("", fix(root));
  }
  @Test void movesAZipWhoseStoredNamesAreNotGoodToTheFixFolder(@TempDir Path tmp) throws IOException{
    var twice= stored(List.of("a/.DS_Store", "a/b.txt", "a/b.txt/"), UTF_8);
    var underAFile= stored(List.of("a", "a/.DS_Store"), UTF_8);
    var broken= stored(List.of("a/../.DS_Store", ".DS_Store"), UTF_8);
    var notUtf8= stored(List.of(".DS_Store", "caf\u00E9.txt"), ISO_8859_1);
    var cases= List.of(twice, underAFile, broken, notUtf8);
    for (var i= 0; i<cases.size(); i += 1){
      var root= root(tmp.resolve("case"+i));
      Fs.write(root.resolve("x.zip"), cases.get(i));
      assertEquals("Moved x.zip to "+tmp.resolve("case"+i).resolve("root_fix").resolve("x.zip")+"\n", fix(root));
      assertArrayEquals(cases.get(i), Fs.readAllBytes(tmp.resolve("case"+i).resolve("root_fix").resolve("x.zip")));
      assertEquals(List.of(), names(root));
    }
  }
  private static byte[] stored(List<String> names, java.nio.charset.Charset cs) throws IOException{
    var bytes= new ByteArrayOutputStream();
    try (var zip= new ZipOutputStream(bytes, cs)){ for (var n : names){ put(zip, n, fine, n.endsWith("/") ? "" : "x"); } }
    return bytes.toByteArray();
  }
  private static String dumpFiles(Path dir){
    var sb= new StringBuilder();
    for (var p : Fs.walk(dir, s -> s.filter(Fs::isRegularFile).sorted(Comparator.comparing(Path::toString)).toList())){
      sb.append(dir.toUri().relativize(p.toUri()).getPath()).append("=").append(Fs.readUtf8(p)).append("\n");
    }
    return sb.toString();
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
  @Test void aRenameNeverGivesANameThatClashesAfterCaseFolding(@TempDir Path tmp){
    var root= root(tmp);
    Fs.writeUtf8(root.resolve("\u017F_.txt"), "one");
    Fs.writeUtf8(root.resolve("S!.txt"), "two");
    assertEquals("Renamed S!.txt to s_2.txt\nRenamed \u017F_.txt to __.txt\n", fix(root));
    assertEquals(List.of("one", "two"), texts(root));
    assertEquals("", fix(root));
  }
  @Test void movesASymbolicLinkOrJunctionToTheFixFolder(@TempDir Path tmp) throws Exception{
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
    var moved= tmp.resolve("root_fix").resolve("c").resolve("b");
    assertEquals("Deleted a/.DS_Store\nDeleted c/.DS_Store\nMoved c/b to "+moved+"\n", fix(root));
    assertTrue(Fs.isLink(moved));
    assertEquals("a/\na/a.txt=a\nc/\nc/c.txt=c\n", dump(root));
    assertEquals("", fix(root));
  }
  @Test void movesADanglingLinkToTheFixFolder(@TempDir Path tmp) throws Exception{
    var root= root(tmp, "a.txt\niii\na");
    var gone= tmp.resolve("gone");
    Fs.ensureDir(gone);
    folderLink(root.resolve("x"), gone);
    Files.delete(gone);
    var moved= tmp.resolve("root_fix").resolve("x");
    assertEquals("Moved x to "+moved+"\n", fix(root));
    assertTrue(Fs.isLink(moved));
    assertEquals("a.txt=a\n", dump(root));
  }
  private static void folderLink(Path link, Path target) throws Exception{
    if (!Fs.isWindows()){ Files.createSymbolicLink(link, target); return; }
    assertEquals(0, new ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString(), target.toString()).redirectErrorStream(true).redirectOutput(Redirect.DISCARD).start().waitFor());
  }
  private static List<String> texts(Path dir){ return Fs.walk(dir, s -> s.filter(Fs::isRegularFile).map(Fs::readUtf8).sorted().toList()); }
}
