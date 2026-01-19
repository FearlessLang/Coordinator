package coordinatorMessages;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import tools.SourceOracle.Ref;

import utils.Bug;
import utils.Join;

@SuppressWarnings("serial")
public final class UserExit extends RuntimeException{
  public UserExit(String msg){ super(msg); }

  static UserExit die(String first, String... more){
    var sb= new StringBuilder();
    sb.append("Error: ").append(first).append('\n');
    for (var s: more){ sb.append("  ").append(s).append('\n'); }
    return new UserExit(sb.toString());
  }

  static UserExit launchNeedsProject(){ return die(
    "Start Fearless by opening a \"*.fearless\" file (or pass the project path explicitly).",
    "Nothing was provided to open."
  );}

  public static UserExit desktopUnsupported(){ return die(
    "Start Fearless by opening a \"*.fearless\" file (file association).",
    "This system does not support Desktop open-file notifications."
  );}

  public static UserExit openFileUnsupported(){ return die(
    "Start Fearless by opening a \"*.fearless\" file (file association).",
    "This system does not support APP_OPEN_FILE notifications."
  );}

  public static UserExit interruptedWhileWaitingForProject(){ return die(
    "Interrupted while waiting for the project to open."
  );}

  public static UserExit emptyLaunchArg(){ return die(
    "Could not read the project path.",
    "Argument was empty."
  );}

  public static UserExit badLaunchArg(String s){ return die(
    "Could not read the project path.",
    "Value: "+s
  );}

  public static UserExit nonAbsoluteLaunchArg(String s){ return die(
    "Fearless was started without an absolute project path.",
    "Fix: open a \"*.fearless\" file with Fearless or pass an absolute path.",
    "Value: "+s
  );}

  public static UserExit cannotFindProjectFolder(Path launch){ return die(
    "Could not determine the project folder from the launch path.",
    "Value: "+launch
  );}

  public static UserExit mustUseLauncherMissingAppDir(){ return die(
    "Start Fearless using its launcher (not by running a jar directly).",
    "Launcher did not provide fearless.appDir."
  );}

  public static UserExit launcherProvidedNonAbsoluteAppDir(String s){ return die(
    "Launcher error: fearless.appDir must be an absolute path.",
    "Value: "+s
  );}

  static UserExit from(Throwable t){
    return new UserExit(t.getMessage() == null ? t.toString() : t.getMessage());
  }

  public static String crash(Throwable t){
    var sw= new StringWriter();
    t.printStackTrace(new PrintWriter(sw));
    return """
Error: Fearless crashed (this is a bug).
  Please report this error and include the details below.

Details:
""" + sw;
  }
  public static String showRel(Path rel){
    var s= rel.toString();
    return s.indexOf('\\') < 0 ? s : s.replace('\\','/');
  }
  private static UncheckedIOException fail(Path rel, String wentWrong, String howToFix){
    Objects.requireNonNull(rel);
    Objects.requireNonNull(wentWrong);
    Objects.requireNonNull(howToFix);
    String msg= "Invalid path in this project folder.\n\n"
      + "Path: `"+showRel(rel)
      + "`\n\nWhat went wrong\n" + wentWrong
      + "\n\nHow to fix\n" + howToFix+"\n\n" + rulesWallS();
    return new UncheckedIOException(msg, new IOException(msg));
  }
  public static final Set<String> allowedNoExtFilesS= """
readme
license
copying
notice
authors
contributors
changelog
changes
news
todo
thanks
security
contributing
code_of_conduct
""".lines().collect(Collectors.toCollection(LinkedHashSet::new));

  public static final Set<String> allowedMultiDotExtsS= """
tar.gz
tar.bz2
tar.xz
tar.zst
tar.lz
tar.lz4
d.ts
d.ts.map
js.map
css.map
min.js
min.css
""".lines().collect(Collectors.toCollection(LinkedHashSet::new));

  static String tickList(Set<String> xs){ return Join.of(xs.stream().map(x->"`"+x+"`"),"",", ",""); }

  static String rulesWallS(){ return """
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
  %s
- A small set of well-known multi-part extensions are allowed:
  %s

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
""".formatted(tickList(allowedNoExtFilesS), tickList(allowedMultiDotExtsS));
  }
  public static UncheckedIOException pathTooLong(Ref kid){ throw Bug.todo(); }
  public static UncheckedIOException pathTooLong(Path kid){
    return UserExit.fail(kid,
      "- The path is longer than 200 characters.\n"
    + "  Long paths often fail on Windows and in some tools.",
      "- Shorten folder/file names, or move this content closer to the project root."
    );
  }
  public static UncheckedIOException symlinkForbidden(Ref kid){ throw Bug.todo(); }
  public static UncheckedIOException symlinkForbidden(Path kid){
    return UserExit.fail(kid,
      "- This path is a symbolic link.\n"
    + "  Symbolic links behave differently across systems and tools, so we forbid them.",
      "- Replace the symbolic link with a real file/folder.\n"
    + "- If you need the linked content, copy it into the repository."
    );
  }
  public static UncheckedIOException onlyRegularFilesAndDirs(Path kid){
    return UserExit.fail(kid,
      "- This path is not a normal file or folder.",
      "- Remove it from the project folder.\n"
    + "- Keep only normal files and folders here."
    );
  }
  public static UncheckedIOException needsExtension(Path kid){
    return UserExit.fail(kid,
      "- This file has no extension.\n"
    + "  Files normally must be named like `name.ext` (one dot).",
      "- Rename it to have a single extension (example: `foo.txt`, `source.fear`).\n"
    + "- Rename it to a well-known extensionless file (example: `readme`)."
    );
  }
  public static UncheckedIOException emptyName(Path kid){
    return UserExit.fail(kid,
      "- A name segment is empty.\n"
    + "  This usually happens when there is an extra dot or an invalid name.",
      "- Rename the path so each folder/file name is non-empty."
    );
  }
  public static UncheckedIOException visibleMustStartWithLetterOrUnderscore(Path kid){
    return UserExit.fail(kid,
      "- A visible folder/file name starts with an invalid character.\n"
    + "  Visible names must start with a lowercase letter (a-z) or underscore (_).",
      "- Rename it to start with a-z or _.\n"
    + "  Examples: `foo`, `_tmp`, `foo1`."
    );
  }
  public static UncheckedIOException visibleInvalidChar(Path kid, char c){
    return UserExit.fail(kid,
      "- A visible folder/file name contains an unsupported character: '"+c+"'.\n"
    + "  Visible names may use only lowercase letters (a-z), digits (0-9), and underscore (_).",
      "- Rename it to use only lowercase letters, digits, and underscores.\n"
    + "  Examples: `foo_bar2`, `src1`, `_cache`."
    );
  }
  public static UncheckedIOException visibleNoDoubleUnderscore(Path kid){
    return UserExit.fail(kid,
      "- A visible folder/file name contains a double underscore (__).\n"
    + "  Double underscores are reserved to avoid accidental collisions and confusion.",
      "- Rename it to remove '__'.\n"
    + "  Example: change `foo__bar` to `foo_bar`."
    );
  }
  public static UncheckedIOException windowsReservedName(Path kid){
    return UserExit.fail(kid,
      "- A visible folder/file name is reserved on Windows (device name).\n"
    + "  Even if you add an extension, Windows treats it as the same reserved name.",
      "- Rename the folder/file so its base name is not a Windows device name.\n"
    + "  Reserved device name: `con`, `prn`, `aux`, `nul`, `com1`..`com9`, `lpt1`..`lpt9`."
    );
  }
  public static UncheckedIOException missingExtension(Path kid){
    return UserExit.fail(kid,
      "- The file name ends with a dot.\n"
    + "  That means the extension is missing.",
      "- Rename it to `name.ext` with one dot.\n"
    + "  Example: change `foo.` to `foo.txt`."
    );
  }
  public static UncheckedIOException multiDotExtNotAllowed(Path kid){
    return UserExit.fail(kid,
      "- This file name has more than one dot in the extension part.\n"
    + "  Most files must use exactly one dot: `name.ext`.",
      "- Rename it to use a single extension, OR\n"
    + "- Rename it to use a well-known extensionless file (example: `tar.gz`)."
    );
  }
  public static UncheckedIOException extLenMustBe1To16(Path kid){
    return UserExit.fail(kid,
      "- The file extension is too long.\n"
    + "  Extensions must be 1..16 characters.",
      "- Use a shorter extension (1..16 characters), using only lowercase letters and digits."
    );
  }
  public static UncheckedIOException extInvalidChar(Path kid, char c){
    return UserExit.fail(kid,
      "- The file extension contains an unsupported character: '"+c+"'.\n"
    + "  Extensions may use only lowercase letters (a-z) and digits (0-9).",
      "- Rename the file to use an extension made only of lowercase letters and digits.\n"
    + "  Examples: `.txt`, `.fear`, `.md`, `.tar.gz` (if allowed)."
    );
  }
  public static UncheckedIOException invisibleSymlinkForbidden(Path kid){
    return UserExit.fail(kid,
      "- This protected path is a symbolic link.\n"
    + "  Even though protected paths are ignored, symbolic links can cause surprises across systems/tools.",
      "- Replace the symbolic link with a real file/folder, or remove it."
    );
  }
  public static UncheckedIOException invisibleOnlyRegularFilesAndDirs(Path kid){
    return UserExit.fail(kid,
      "- This protected path is not a normal file or folder.",
      "- Remove it, and keep only normal files and folders."
    );
  }
  public static UncheckedIOException invisibleEmptySegment(Path kid){
    return UserExit.fail(kid,
      "- A protected name segment is empty.",
      "- Rename it so each folder/file name is non-empty."
    );
  }
  public static UncheckedIOException invisibleNoTrailingDotOrSpace(Path kid, String name){
    return UserExit.fail(kid,
      "- A protected name segment ends with a dot or a space.\n"
    + "  Some systems/tools trim these, which causes collisions.\n"
    + "  Bad segment: `"+name+"`",
      "- Rename the segment so it does not end with '.' or space."
    );
  }
  public static UncheckedIOException invisibleInvalidSurrogate(Path kid, String name){
    return UserExit.fail(kid,
      "- A protected name segment contains invalid Unicode.\n"
    + "  Bad segment: `"+name+"`",
      "- Rename the segment to remove the invalid characters."
    );
  }
  public static UncheckedIOException invisibleNoControlChars(Path kid, int cp, String name){
    return UserExit.fail(kid,
      "- A protected name segment contains a control character.\n"
    + "  Bad code: "+cp+"\n"
    + "  Segment: `"+name+"`",
      "- Rename the segment to remove the control character."
    );
  }
  public static UncheckedIOException invisibleNoWindowsBadChars(Path kid, char bad, String name){
    return UserExit.fail(kid,
      "- A protected name segment contains a character that Windows forbids.\n"
    + "  Bad char: '"+bad+"'\n"
    + "  Segment: `"+name+"`",
      "- Rename the segment to remove Windows-forbidden characters.\n"
    + "  Forbidden on Windows: < > : \" / \\ | ? *"
    );
  }
  public static UncheckedIOException invisibleWindowsReservedDeviceName(Path kid, String base, String name){
    return UserExit.fail(kid,
      "- A protected name segment uses a Windows reserved device name.\n"
    + "  Bad base: `"+base+"` in segment: `"+name+"`",
      "- Rename it so the base name is not a Windows device name.\n"
    + "  Reserved device name: `con`, `prn`, `aux`, `nul`, `com1`..`com9`, `lpt1`..`lpt9`."
    );
  }
  public static UncheckedIOException hiddenSiblingNamesCollide(Path kid, String prev, String name, String reason){
    return UserExit.fail(kid,
      "- Two protected names in the same folder collide.\n"
    + "  Name 1: `"+prev+"`\n"
    + "  Name 2: `"+name+"`\n"
    + "  Reason: "+reason,
      "- Rename one of them so they are clearly distinct.\n"
    + "- Avoid differences that are only case changes or Unicode-equivalent spellings."
    );
  }
  public static UncheckedIOException extensionlessMaskExtension(Path kid,Path noExtKid){
    return UserExit.fail(kid,
      "- Both an allowed extensionless file and an extended file share the same base name.\n"
    + "  Extensionless file: `"+UserExit.showRel(noExtKid)+"`\n"
    + "  Extended file:      `"+UserExit.showRel(kid)+"`\n"
    + "  This is confusing in file browsers (extensions may be hidden).",
      "- Rename one of them so they are clearly distinct."
    );
  }
  public static UncheckedIOException rootNotDirectory(){
    return UserExit.fail(Path.of("."),
    "Problem: root is not a directory.",
    "Start Fearless on the Fearless project directory."); 
  }
  // "zip file is unreadable/corrupt/not a zip" (top or nested)
public static RuntimeException zipCanNotRead(Path diskZip, List<String> steps, RuntimeException cause){ throw Bug.todo(); }

// "entry name violates zip rules"
public static RuntimeException zipBadEntryName(Path diskZip, List<String> steps, String entryName){ throw Bug.todo(); }

// "entry name length too large"
public static RuntimeException zipEntryNameTooLong(Path diskZip, List<String> steps, String entryName, int maxLen){ throw Bug.todo(); }

// "same name appears multiple times in one zip"
public static RuntimeException zipDuplicateEntryName(Path diskZip, List<String> steps, String entryName){ throw Bug.todo(); }

// "nested zips too deep"
public static RuntimeException zipNestingTooDeep(Path diskZip, List<String> steps, int depth, int maxDepth){ throw Bug.todo(); }

public static RuntimeException zipFileDirPrefixConflict(Path diskZip, List<String> steps, String a, String b){ throw Bug.todo(); }

public static RuntimeException emptyDirectory(Path d){ throw Bug.todo(); }

public static RuntimeException emptyExpandedZip(Path rel){ throw Bug.todo(); }
}