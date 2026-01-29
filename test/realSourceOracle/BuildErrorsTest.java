package realSourceOracle;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import coordinatorMessages.UserExit;

public class BuildErrorsTest{
  static{ utils.Err.setUp(AssertionFailedError.class, Assertions::assertEquals, Assertions::assertTrue); }
  enum K{ FILE, DIR, SYMLINK, SPECIAL }
  record E(String rel, K k){}

  @Test void visible_invalidChar(){ fail(List.of(new E("foo-bar.txt", K.FILE)),"""
Invalid path in this project folder.

Root: [###]
Path: "foo-bar.txt"

What went wrong
- A visible folder/file name contains an unsupported character: '-'.
  Visible names may use only lowercase letters (a-z), digits (0-9), and underscore (_).

How to fix
- Rename it to use only lowercase letters, digits, and underscores.
  Examples: "foo_bar2", "src1", "_cache".

We check this so that you do not get surprises later.
Fearless enforces simple, portable names so the same data/repository works on Windows, Linux, and Mac,
and in common tools such as Git/GitHub and file browsers.

While scanning the project, we reject any path that could cause trouble later, including:

- Folder names and file base names must:
  - use only lowercase letters (a-z), digits (0-9), and underscore (_)
  - start with a letter or underscore
  - never contain a double underscore (__)
  - not use Windows reserved device names: `con`, `prn`, `aux`, `nul`, `com1`..`com9`, `lpt1`..`lpt9`
- Files must:
  - have a single extension "name.ext" with exactly one dot
  - use an extension made of lowercase letters and digits, length 1..16 characters
  - stay within a reasonable total path length of 200 characters

Exceptions (explicit allowlists)
- A small set of well-known files are allowed to have no extension:
  `readme`, `license`, `copying`, `notice`, `authors`, `contributors`, `changelog`, `changes`, `news`, `todo`, `thanks`, `security`, `contributing`, `code_of_conduct`
- A small set of well-known multi-part extensions are allowed:
  `tar.gz`, `tar.bz2`, `tar.xz`, `tar.zst`, `tar.lz`, `tar.lz4`, `d.ts`, `d.ts.map`, `js.map`, `css.map`, `min.js`, `min.css`

Protected paths
Many tools treat names starting with '.' (for example `.gitignore`) as private tool data.
Fearless respects this convention and ignores those files and folders.
But we still require them to be safe to check out using a relaxed set of checks:
  - no control characters
  - no Windows-forbidden characters like < > : " / \\ | ? *
  - no name ending with '.' or a space
  - not using Windows reserved device names
  - no two items in the same folder that differ only by case or by Unicode-equivalent spellings

Other rules (everywhere)
- Symbolic links are not allowed.
- Only normal files and folders are allowed.
"""); }
  @Test void visible_mustStart(){ fail(List.of(new E("9foo.txt", K.FILE)),"""
Invalid path in this project folder.

Root: [###]
Path: "9foo.txt"

What went wrong
- A visible folder/file name starts with an invalid character.
  Visible names must start with a lowercase letter (a-z) or underscore (_).

How to fix
- Rename it to start with a-z or _.
  Examples: "foo", "_tmp", "foo1".

We check this so that you do not get surprises later.[###]
"""); }
  @Test void visible_noDoubleUnderscore(){ fail(List.of(new E("foo__bar.txt", K.FILE)),"""
Invalid path in this project folder.

Root: [###]
Path: "foo__bar.txt"

What went wrong
- A visible folder/file name contains a double underscore (__).
  Double underscores are reserved to avoid accidental collisions and confusion.

How to fix
- Rename it to remove '__'.
  Example: change "foo__bar" to "foo_bar".

We check this so that you do not get surprises later.[###]
"""); }
  @Test void visible_windowsReserved(){ fail(List.of(new E("con.txt", K.FILE)),"""
Invalid path in this project folder.

Root: [###]
Path: "con.txt"

What went wrong
- A visible folder/file name is reserved on Windows (device name).
  Even if you add an extension, Windows treats it as the same reserved name.

How to fix
- Rename the folder/file so its base name is not a Windows device name.
  Reserved device name: "con", "prn", "aux", "nul", "com1".."com9", "lpt1".."lpt9"s.

We check this so that you do not get surprises later.[###]
"""); }
  @Test void visible_needsExtension(){ fail(List.of(new E("foo", K.FILE)),"""
Invalid path in this project folder.

Root: [###]
Path: "foo"

What went wrong
- This file has no extension.
  Files normally must be named like "name.ext" (one dot).

How to fix
- Rename it to have a single extension (example: "foo.txt", "source.fear").
- Rename it to a well-known extensionless file (example: "readme").

We check this so that you do not get surprises later.[###]
"""); }
  @Test void visible_missingExtension(){ fail(List.of(new E("bar.", K.FILE)),"""
Invalid path in this project folder.

Root: [###]
Path: "bar."

What went wrong
- The file name ends with a dot.
  That means the extension is missing.

How to fix
- Rename it to "name.ext" with one dot.
  Example: change "foo." to "foo.txt".

We check this so that you do not get surprises later.[###]
"""); }
  @Test void visible_multiDotExtNotAllowed(){ fail(List.of(new E("foo.a.b", K.FILE)),"""
Invalid path in this project folder.

Root: [###]
Path: "foo.a.b"

What went wrong
- This file name has more than one dot in the extension part.
  Most files must use exactly one dot: "name.ext".

How to fix
- Rename it to use a single extension, OR
- Rename it to use a well-known extensionless file (example: \"tar.gz\").

We check this so that you do not get surprises later.[###]
"""); }
  @Test void visible_extLen(){ fail(List.of(new E("foo."+("a".repeat(17)), K.FILE)),"""
Invalid path in this project folder.

Root: [###]
Path: "foo.aaaaaaaaaaaaaaaaa"

What went wrong
- The file extension is too long.
  Extensions must be 1..16 characters.

How to fix
- Use a shorter extension (1..16 characters), using only lowercase letters and digits.

We check this so that you do not get surprises later.[###]
"""); }
  @Test void visible_extInvalidChar(){ fail(List.of(new E("foo.A", K.FILE)),"""
Invalid path in this project folder.

Root: [###]
Path: "foo.A"

What went wrong
- The file extension contains an unsupported character: "A".
  Extensions may use only lowercase letters (a-z) and digits (0-9).

How to fix
- Rename the file to use an extension made only of lowercase letters and digits.
  Examples: ".txt", ".fear", ".md", ".tar.gz"

We check this so that you do not get surprises later.[###]
"""); }
  @Test void visible_symlinkForbidden(){ fail(List.of(new E("ok.txt", K.SYMLINK)),"""
Invalid path in this project folder.

Root: [###]
Path: "ok.txt"

What went wrong
- This path is a symbolic link.
  Symbolic links behave differently across systems and tools, so we forbid them.

How to fix
- Replace the symbolic link with a real file/folder.
- If you need the linked content, copy it into the repository.

We check this so that you do not get surprises later.[###]
"""); }
  @Test void visible_onlyRegularFilesAndDirs(){ fail(List.of(new E("ok.txt", K.SPECIAL)),"""
Invalid path in this project folder.

Root: [###]
Path: "ok.txt"

What went wrong
- This path is not a normal file or folder.

How to fix
- Remove it from the project folder.
- Keep only normal files and folders here.

We check this so that you do not get surprises later.[###]
"""); }

  @Test void visible_extensionlessMasksExtension(){ fail(List.of(new E("readme", K.FILE), new E("readme.md", K.FILE)),"""
Invalid path in this project folder.

Root: [###]
Path: "readme.md"

What went wrong
- Both an allowed extensionless file and an extended file share the same base name.
  Extensionless file:
  Root: [###]
  Path: "readme"
  
  Extended file:
  Root: [###]
  Path: "readme.md"

  This is confusing in file browsers (extensions may be hidden).

How to fix
- Rename one of them so they are clearly distinct.

We check this so that you do not get surprises later.[###]
"""); }

  @Test void protected_windowsBadChar(){ fail(List.of(new E(".d/a:b", K.FILE)),"""
Invalid path in this project folder.

Root: [###]
Path: ".d/a:b"

What went wrong
- A protected name segment contains a character that Windows forbids.
  Bad char: `:`
  Segment: "a:b"

How to fix
- Rename the segment to remove Windows-forbidden characters.
  Forbidden on Windows: < > : " / \\ | ? *

We check this so that you do not get surprises later.[###]
"""); }
  @Test void protected_controlChar(){ fail(List.of(new E(".a\u0001b", K.FILE)),"""
Invalid path in this project folder.

Root: [###]
Path: ".a\\u0001b"

What went wrong
- A protected name segment contains a control character.
  Character: [Start Of Heading 0x01]
  Segment: ".a\\u0001b"

How to fix
- Rename the segment to remove the control character.

We check this so that you do not get surprises later.[###]
"""); }
  @Test void protected_trailingDot(){  fail(List.of(new E(".x.", K.FILE)),"""
Invalid path in this project folder.

Root: [###]
Path: ".x."

What went wrong
- A protected name segment ends with a dot or a space.
  Some systems/tools trim these, which causes collisions.
  Bad segment: ".x."

How to fix
- Rename the segment so it does not end with '.' or space.

We check this so that you do not get surprises later.[###]
"""); }
  @Test void protected_trailingSpace(){ fail(List.of(new E(".x ", K.FILE)),"""
Invalid path in this project folder.

Root: [###]
Path: ".x "

What went wrong
- A protected name segment ends with a dot or a space.
  Some systems/tools trim these, which causes collisions.
  Bad segment: ".x "

How to fix
- Rename the segment so it does not end with '.' or space.

We check this so that you do not get surprises later.[###]
"""); }
  @Test void protected_windowsReservedDevice(){ fail(List.of(new E(".d/con.txt", K.FILE)),"""
Invalid path in this project folder.

Root: [###]
Path: ".d/con.txt"

What went wrong
- A protected name segment uses a Windows reserved device name.
  Bad base: "con" in segment: "con.txt"

How to fix
- Rename it so the base name is not a Windows device name.
  Reserved device name: "con", "prn", "aux", "nul", "com1".."com9", "lpt1".."lpt9".

We check this so that you do not get surprises later.[###]
"""); }

  @Test void protected_siblingCollision_case(){ fail(List.of(new E(".A", K.FILE), new E(".a", K.FILE)),"""
Invalid path in this project folder.

Root: [###]
Path: ".a"

What went wrong
- Two protected names in the same folder collide.
  Name 1: ".A"
  Name 2: ".a"
  Reason: Names differ only by case.

How to fix
- Rename one of them so they are clearly distinct.
- Avoid differences that are only case changes or Unicode-equivalent spellings.

We check this so that you do not get surprises later.[###]
"""); }
  @Test void protected_siblingCollision_nfc(){ fail(List.of(new E(".e\u0301", K.FILE), new E(".\u00e9", K.FILE)),"""
Invalid path in this project folder.

Root: [###]
Path: ".\\u00E9"

What went wrong
- Two protected names in the same folder collide.
  Name 1: ".e\\u0301"
  Name 2: ".\\u00E9"
  Reason: Names differ only by Unicode normalization (NFC).

How to fix
- Rename one of them so they are clearly distinct.
- Avoid differences that are only case changes or Unicode-equivalent spellings.

We check this so that you do not get surprises later.[###]
"""); }
  @Test void protected_invalidSurrogate(){ fail(List.of(new E(".x\uD800y", K.FILE)),"""
Invalid path in this project folder.

Root: [###]
Path: ".x\\uD800y"

What went wrong
- A protected name segment contains invalid Unicode.
  Bad segment: ".x\\uD800y"

How to fix
- Rename the segment to remove the invalid characters.

We check this so that you do not get surprises later.[###]
"""); }

  // If you still want to cover these "unrealistic but guarded" branches:
  @Test void visible_emptyNameAtom_unreachableInRealFs(){
    // Forces name ".": visible check isn't used; leaving this as a placeholder if you later add a direct Err test.
    fail(List.of(new E("_", K.FILE)),"""
Invalid path in this project folder.

Root: [###]
Path: "_"

What went wrong
- This file has no extension.
  Files normally must be named like "name.ext" (one dot).

How to fix
- Rename it to have a single extension (example: "foo.txt", "source.fear").
- Rename it to a well-known extensionless file (example: "readme").

We check this so that you[###]
"""); }

  private static void fail(List<E> es, String expected){
    var root= Path.of("buildErrorsMockRoot").toAbsolutePath().normalize();
    var b= new MockBuild(root, es);
    var ex= assertThrows(UserExit.class, b::build);
    utils.Err.strCmp(expected, ex.getMessage());
  }

  static final class MockBuild extends Build{
    private final Path rootAbs;
    private final FileSystem fs;

    private final List<Path> absList= new ArrayList<>();
    private final Map<Path,FakePath> absToRel= new HashMap<>();
    private final Map<Path,Path> relToAbs= new HashMap<>();
    private final Map<Path,K> kindByAbs= new HashMap<>();

    MockBuild(Path root, List<E> es){
      super(root);
      this.rootAbs= root.toAbsolutePath().normalize();
      this.fs= rootAbs.getFileSystem();

      for (int i= 0; i < es.size(); i++){
        var abs= rootAbs.resolve("__abs"+i);
        var rel= new FakePath(es.get(i).rel, fs);

        absList.add(abs);
        absToRel.put(abs, rel);
        relToAbs.put(rel, abs);
        kindByAbs.put(abs, es.get(i).k);
      }
    }

    @Override protected void walkRoot(java.util.function.Consumer<Path> f){
      absList.forEach(f);
    }

    @Override protected Path relativize(Path abs){
      if (abs.equals(rootAbs)) return new FakePath("", fs);
      var rel= absToRel.get(abs);
      assert rel != null : abs;
      return rel;
    }

    @Override protected Path resolve(Path rel){
      var abs= relToAbs.get(rel);
      assert abs != null : rel;
      return abs;
    }

    @Override protected URI uriOf(Path abs){
      var rel= absToRel.get(abs);
      return URI.create("file:/"+(rel==null? "__root__" : rel.toString()));
    }

    @Override protected boolean isSymbolicLink(Path abs){ return kindByAbs.get(abs) == K.SYMLINK; }
    @Override protected boolean isDirectory(Path abs){ return kindByAbs.get(abs) == K.DIR; }
    @Override protected boolean isRegularFile(Path abs){ return kindByAbs.get(abs) == K.FILE; }
  }

  // Minimal Path impl for Build's usage.
  static final class FakePath implements Path{
    private final String s;
    private final String[] segs;
    private final FileSystem fs;

    FakePath(String rel, FileSystem fs){
      this.fs= fs;
      this.s= rel.replace('\\','/');
      this.segs= s.isEmpty()? new String[0] : s.split("/", -1);
    }

    @Override public String toString(){ return s; }
    @Override public int hashCode(){ return s.hashCode(); }
    @Override public boolean equals(Object o){ return o instanceof Path p && s.equals(p.toString()); }

    @Override public FileSystem getFileSystem(){ return fs; }
    @Override public int getNameCount(){ return segs.length; }
    @Override public Path getName(int index){ return new FakePath(segs[index], fs); }

    @Override public Path getFileName(){
      return segs.length==0 ? null : new FakePath(segs[segs.length-1], fs);
    }

    @Override public Path getParent(){
      if (segs.length<=1) return null;
      var sb= new StringBuilder();
      for (int i= 0; i < segs.length-1; i++){
        if (i>0) sb.append('/');
        sb.append(segs[i]);
      }
      return new FakePath(sb.toString(), fs);
    }

    @Override public int compareTo(Path other){ return toString().compareTo(other.toString()); }

    // Unused by Build: offensive
    @Override public boolean isAbsolute(){ throw uoe(); }
    @Override public Path getRoot(){ throw uoe(); }
    @Override public Path normalize(){ return this; }
    @Override public Path resolve(Path other){ throw uoe(); }
    @Override public Path resolve(String other){ throw uoe(); }
    @Override public Path resolveSibling(Path other){ throw uoe(); }
    @Override public Path resolveSibling(String other){ throw uoe(); }
    @Override public Path relativize(Path other){ throw uoe(); }
    @Override public URI toUri(){ throw uoe(); }
    @Override public Path toAbsolutePath(){ throw uoe(); }
    @Override public Path toRealPath(java.nio.file.LinkOption... options){ throw uoe(); }
    @Override public java.io.File toFile(){ throw uoe(); }
    @Override public java.nio.file.WatchKey register(java.nio.file.WatchService watcher, java.nio.file.WatchEvent.Kind<?>[] events, java.nio.file.WatchEvent.Modifier... modifiers){ throw uoe(); }
    @Override public java.nio.file.WatchKey register(java.nio.file.WatchService watcher, java.nio.file.WatchEvent.Kind<?>... events){ throw uoe(); }
    @Override public java.util.Iterator<Path> iterator(){ throw uoe(); }
    @Override public boolean startsWith(Path other){ throw uoe(); }
    @Override public boolean startsWith(String other){ throw uoe(); }
    @Override public boolean endsWith(Path other){ throw uoe(); }
    @Override public boolean endsWith(String other){ throw uoe(); }
    @Override public Path subpath(int beginIndex, int endIndex){ throw uoe(); }

    private static UnsupportedOperationException uoe(){ return new UnsupportedOperationException(); }
  }
}