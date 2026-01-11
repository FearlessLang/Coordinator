package realSourceOracle;

import java.io.UncheckedIOException;
import java.nio.file.Path;

class Err{
  static UncheckedIOException pathTooLong(Path kid){
    return Policy.fail(kid,
      "- The path is longer than 200 characters.\n"
    + "  Long paths often fail on Windows and in some tools.",
      "- Shorten folder/file names, or move this content closer to the project root."
    );
  }
  static UncheckedIOException symlinkForbidden(Path kid){
    return Policy.fail(kid,
      "- This path is a symbolic link.\n"
    + "  Symbolic links behave differently across systems and tools, so we forbid them.",
      "- Replace the symbolic link with a real file/folder.\n"
    + "- If you need the linked content, copy it into the repository."
    );
  }
  static UncheckedIOException onlyRegularFilesAndDirs(Path kid){
    return Policy.fail(kid,
      "- This path is not a normal file or folder.",
      "- Remove it from the project folder.\n"
    + "- Keep only normal files and folders here."
    );
  }
  static UncheckedIOException needsExtension(Path kid){
    return Policy.fail(kid,
      "- This file has no extension.\n"
    + "  Files normally must be named like `name.ext` (one dot).",
      "- Rename it to have a single extension (example: `foo.txt`, `source.fear`).\n"
    + "- If it is a well-known extensionless file (example: `readme`), add it to the allowlist."
    );
  }
  static UncheckedIOException emptyName(Path kid){
    return Policy.fail(kid,
      "- A name segment is empty.\n"
    + "  This usually happens when there is an extra dot or an invalid name.",
      "- Rename the path so each folder/file name is non-empty."
    );
  }
  static UncheckedIOException visibleMustStartWithLetterOrUnderscore(Path kid){
    return Policy.fail(kid,
      "- A visible folder/file name starts with an invalid character.\n"
    + "  Visible names must start with a lowercase letter (a-z) or underscore (_).",
      "- Rename it to start with a-z or _.\n"
    + "  Examples: `foo`, `_tmp`, `foo1`."
    );
  }
  static UncheckedIOException visibleInvalidChar(Path kid, char c){
    return Policy.fail(kid,
      "- A visible folder/file name contains an unsupported character: '"+c+"'.\n"
    + "  Visible names may use only lowercase letters (a-z), digits (0-9), and underscore (_).",
      "- Rename it to use only lowercase letters, digits, and underscores.\n"
    + "  Examples: `foo_bar2`, `src1`, `_cache`."
    );
  }
  static UncheckedIOException visibleNoDoubleUnderscore(Path kid){
    return Policy.fail(kid,
      "- A visible folder/file name contains a double underscore (__).\n"
    + "  Double underscores are reserved to avoid accidental collisions and confusion.",
      "- Rename it to remove '__'.\n"
    + "  Example: change `foo__bar` to `foo_bar`."
    );
  }
  static UncheckedIOException windowsReservedName(Path kid){
    return Policy.fail(kid,
      "- A visible folder/file name is reserved on Windows (device name).\n"
    + "  Even if you add an extension, Windows treats it as the same reserved name.",
      "- Rename the folder/file so its base name is not a Windows device name.\n"
    + "  Examples to avoid: `con`, `prn`, `aux`, `nul`, `com1`..`com9`, `lpt1`..`lpt9`."
    );
  }
  static UncheckedIOException missingExtension(Path kid){
    return Policy.fail(kid,
      "- The file name ends with a dot.\n"
    + "  That means the extension is missing.",
      "- Rename it to `name.ext` with one dot.\n"
    + "  Example: change `foo.` to `foo.txt`."
    );
  }
  static UncheckedIOException multiDotExtNotAllowed(Path kid){
    return Policy.fail(kid,
      "- This file name has more than one dot in the extension part.\n"
    + "  Most files must use exactly one dot: `name.ext`.",
      "- Rename it to use a single extension, OR\n"
    + "- If it is a well-known multi-part extension (example: `tar.gz`), add it to the allowlist."
    );
  }
  static UncheckedIOException extLenMustBe1To16(Path kid){
    return Policy.fail(kid,
      "- The file extension is too long.\n"
    + "  Extensions must be 1..16 characters.",
      "- Use a shorter extension (1..16 characters), using only lowercase letters and digits."
    );
  }
  static UncheckedIOException extInvalidChar(Path kid, char c){
    return Policy.fail(kid,
      "- The file extension contains an unsupported character: '"+c+"'.\n"
    + "  Extensions may use only lowercase letters (a-z) and digits (0-9).",
      "- Rename the file to use an extension made only of lowercase letters and digits.\n"
    + "  Examples: `.txt`, `.fear`, `.md`, `.tar.gz` (if allowed)."
    );
  }

  static UncheckedIOException invisibleSymlinkForbidden(Path kid){
    return Policy.fail(kid,
      "- This protected path is a symbolic link.\n"
    + "  Even though protected paths are ignored, symbolic links can cause surprises across systems/tools.",
      "- Replace the symbolic link with a real file/folder, or remove it."
    );
  }
  static UncheckedIOException invisibleOnlyRegularFilesAndDirs(Path kid){
    return Policy.fail(kid,
      "- This protected path is not a normal file or folder.",
      "- Remove it, and keep only normal files and folders."
    );
  }
  static UncheckedIOException invisibleEmptySegment(Path kid){
    return Policy.fail(kid,
      "- A protected name segment is empty.",
      "- Rename it so each folder/file name is non-empty."
    );
  }
  static UncheckedIOException invisibleNoTrailingDotOrSpace(Path kid, String name){
    return Policy.fail(kid,
      "- A protected name segment ends with a dot or a space.\n"
    + "  Some systems/tools trim these, which causes collisions.\n"
    + "  Bad segment: `"+name+"`",
      "- Rename the segment so it does not end with '.' or space."
    );
  }
  static UncheckedIOException invisibleInvalidSurrogate(Path kid, String name){
    return Policy.fail(kid,
      "- A protected name segment contains invalid Unicode.\n"
    + "  Bad segment: `"+name+"`",
      "- Rename the segment to remove the invalid characters."
    );
  }
  static UncheckedIOException invisibleNoControlChars(Path kid, int cp, String name){
    return Policy.fail(kid,
      "- A protected name segment contains a control character.\n"
    + "  Bad code: "+cp+"\n"
    + "  Segment: `"+name+"`",
      "- Rename the segment to remove the control character."
    );
  }
  static UncheckedIOException invisibleNoWindowsBadChars(Path kid, char bad, String name){
    return Policy.fail(kid,
      "- A protected name segment contains a character that Windows forbids.\n"
    + "  Bad char: '"+bad+"'\n"
    + "  Segment: `"+name+"`",
      "- Rename the segment to remove Windows-forbidden characters.\n"
    + "  Forbidden on Windows: < > : \" / \\ | ? *"
    );
  }
  static UncheckedIOException invisibleWindowsReservedDeviceName(Path kid, String base, String name){
    return Policy.fail(kid,
      "- A protected name segment uses a Windows reserved device name.\n"
    + "  Bad base: `"+base+"` in segment: `"+name+"`",
      "- Rename it so the base name is not a Windows device name.\n"
    + "  Examples to avoid: `con`, `prn`, `aux`, `nul`, `com1`..`com9`, `lpt1`..`lpt9`."
    );
  }

  static UncheckedIOException hiddenSiblingNamesCollide(Path kid, String prev, String name, String reason){
    return Policy.fail(kid,
      "- Two protected names in the same folder collide.\n"
    + "  Name 1: `"+prev+"`\n"
    + "  Name 2: `"+name+"`\n"
    + "  Reason: "+reason,
      "- Rename one of them so they are clearly distinct.\n"
    + "- Avoid differences that are only case changes or Unicode-equivalent spellings."
    );
  }

  static UncheckedIOException extensionlessMaskExtension(Path kid,Path noExtKid){
    return Policy.fail(kid,
      "- Both an allowed extensionless file and an extended file share the same base name.\n"
    + "  Extensionless file: `"+Policy.showRel(noExtKid)+"`\n"
    + "  Extended file:      `"+Policy.showRel(kid)+"`\n"
    + "  This is confusing in file browsers (extensions may be hidden).",
      "- Rename one of them, or remove the extensionless one if it is not needed here."
    );
  }
}