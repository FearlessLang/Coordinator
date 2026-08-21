package userMessages;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import metaParser.Message;
import realSourceOracle.PathEntry;
import realSourceOracle.ZipEntry;
import tools.SourceOracle.Ref;
import tools.SourceOracle.RefParent;
import utils.Bug;
import utils.Join;
import utils.Pop;

import static userMessages.UserError.disp;
import static userMessages.UserError.showRel;
import static userMessages.UserError.showZipRel;

/// Report: the user gave us something we cannot accept, and can fix it by changing
/// what they gave us. Their file and folder names, their zips, their project layout,
/// their Fearless source.
/// The counterpart is Violation, for when we can no longer do our safe job.
public final class Report {
  private Report(){}

  //-- the shape of a project-scan message: what is wrong, how to fix it, then the rules
  private static UserError fail(String rel, String wentWrong, String howToFix){
    String msg= "Invalid path in this project folder.\n\n"
      + rel
      + "\nWhat went wrong\n" + wentWrong
      + "\n\nHow to fix\n" + howToFix+"\n\n" + rulesWall();
    return new UserError(msg);
  }
  private static UserError directFail(String rel, String msg){
    return new UserError(rel+msg+"\n\n" + rulesWall());
  }
  public static final Set<String> allowedNoExtFiles= """
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

  public static final Set<String> allowedMultiDotExts= """
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

  private static String tickList(Set<String> xs){ return Join.of(xs.stream().map(x->"`"+x+"`"),"",", ",""); }

  private static String rulesWall(){ return """
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
- Folders must not be empty.
- Reserved names:
  - a package name of `base` or `rank` is reserved and cannot be used
  - inside a package, only its own rank file may start with `_rank_`;
    no other file may use that prefix

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
  - a total path length of 200 characters
Nothing else about a protected path is checked: it may be empty, it may be a
symbolic link, and a zip inside it is never opened.

Other rules (everywhere)
- Symbolic links are not allowed.
- Only normal files and folders are allowed.
""".formatted(tickList(allowedNoExtFiles), tickList(allowedMultiDotExts));
  }

  //-- the project folder itself
  public static UserError rootNotDirectory(){
    return fail(".",
      "Problem: root is not a directory.",
      "Start Fearless on the Fearless project directory.");
  }
  public static UserError emptyDirectory(Path kid){
    return directFail(showRel(kid),"""
This directory is empty.
Different systems handleds empty directories differently,
and they may not be supported by compression tools (zip)
or version control system (git).
""");}

  //-- visible names
  public static UserError pathTooLong(RefParent kid){
    return fail(showRel(kid),
      "- The path is longer than 200 characters.\n"
    + "  Long paths often fail on Windows and in some tools.",
      "- Shorten folder/file names, or move this content closer to the project root."
    );
  }
  public static UserError symlinkForbidden(Path kid){
    return fail(showRel(kid),
      "- This path is a symbolic link.\n"
    + "  Symbolic links behave differently across systems and tools, so we forbid them.",
      "- Replace the symbolic link with a real file/folder.\n"
    + "- If you need the linked content, copy it into the repository."
    );
  }
  public static UserError onlyRegularFilesAndDirs(Path kid){
    return fail(showRel(kid),
      "- This path is not a normal file or folder.",
      "- Remove it from the project folder.\n"
    + "- Keep only normal files and folders here."
    );
  }
  public static UserError needsExtension(RefParent kid){
    return fail(showRel(kid),
      "- This file has no extension.\n"
    + "  Files normally must be named like \"name.ext\" (one dot).",
      "- Rename it to have a single extension (example: \"foo.txt\", \"source.fear\").\n"
    + "- Rename it to a well-known extensionless file (example: \"readme\")."
    );
  }
  public static UserError visibleMustStartWithLetterOrUnderscore(RefParent kid){
    return fail(showRel(kid),
      "- A visible folder/file name starts with an invalid character.\n"
    + "  Visible names must start with a lowercase letter (a-z) or underscore (_).",
      "- Rename it to start with a-z or _.\n"
    + "  Examples: \"foo\", \"_tmp\", \"foo1\"."
    );
  }
  public static UserError visibleInvalidChar(RefParent kid, char c){
    return fail(showRel(kid),
      "- A visible folder/file name contains an unsupported character: '"+c+"'.\n"
    + "  Visible names may use only lowercase letters (a-z), digits (0-9), and underscore (_).",
      "- Rename it to use only lowercase letters, digits, and underscores.\n"
    + "  Examples: \"foo_bar2\", \"src1\", \"_cache\"."
    );
  }
  public static UserError visibleNoDoubleUnderscore(RefParent kid){
    return fail(showRel(kid),
      "- A visible folder/file name contains a double underscore (__).\n"
    + "  Double underscores are reserved to avoid accidental collisions and confusion.",
      "- Rename it to remove '__'.\n"
    + "  Example: change \"foo__bar\" to \"foo_bar\"."
    );
  }
  public static UserError windowsReservedName(RefParent kid){
    return fail(showRel(kid),
      "- A visible folder/file name is reserved on Windows (device name).\n"
    + "  Even if you add an extension, Windows treats it as the same reserved name.",
      "- Rename the folder/file so its base name is not a Windows device name.\n"
    + "  Reserved device name: \"con\", \"prn\", \"aux\", \"nul\", \"com1\"..\"com9\", \"lpt1\"..\"lpt9\"."
    );
  }
  public static UserError missingExtension(RefParent kid){
    return fail(showRel(kid),
      "- The file name ends with a dot.\n"
    + "  That means the extension is missing.",
      "- Rename it to \"name.ext\" with one dot.\n"
    + "  Example: change \"foo.\" to \"foo.txt\"."
    );
  }
  public static UserError multiDotExtNotAllowed(RefParent kid){
    return fail(showRel(kid),
      "- This file name has more than one dot in the extension part.\n"
    + "  Most files must use exactly one dot: \"name.ext\".",
      "- Rename it to use a single extension, OR\n"
    + "- Rename it to use a well-known extensionless file (example: \"tar.gz\")."
    );
  }
  public static UserError extLenMustBe1To16(RefParent kid){
    return fail(showRel(kid),
      "- The file extension is too long.\n"
    + "  Extensions must be 1..16 characters.",
      "- Use a shorter extension (1..16 characters), using only lowercase letters and digits."
    );
  }
  public static UserError extInvalidChar(RefParent kid, char c){
    return fail(showRel(kid),
      "- The file extension contains an unsupported character: "+Message.displayChar(c)+".\n"
    + "  Extensions may use only lowercase letters (a-z) and digits (0-9).",
      "- Rename the file to use an extension made only of lowercase letters and digits.\n"
    + "  Examples: \".txt\", \".fear\", \".md\", \".tar.gz\""
    );
  }
  public static UserError extensionlessMaskExtension(RefParent kid, RefParent noExtKid){
    return fail(showRel(kid),
      "- Both an allowed extensionless file and an extended file share the same base name.\n"
    + "  Extensionless file:\n  "
    + showRel(noExtKid).replace("\nPath:", "\n  Path:")+"\n"
    + "  Extended file:\n  "
    + showRel(kid).replace("\nPath:", "\n  Path:")+"\n"
    + "  This is confusing in file browsers (extensions may be hidden).",
      "- Rename one of them so they are clearly distinct."
    );
  }
  public static UserError zipNameClashesWithFile(RefParent zipKid, RefParent fileKid){
    return fail(showRel(zipKid),
      "- A zip file and a plain file share the same base name.\n"
    + "  Zip file:\n  "
    + showRel(zipKid).replace("\nPath:", "\n  Path:")+"\n"
    + "  Plain file:\n  "
    + showRel(fileKid).replace("\nPath:", "\n  Path:")+"\n"
    + "  Fearless expands each zip file into a folder named after it (without the\n"
    + "  \".zip\"); that folder would have the exact same name as this file.",
      "- Rename one of them, or move/rename the zip file, so this name is no longer\n"
    + "  produced twice."
    );
  }

  //-- protected ("dot") names: ignored by Fearless, but still checked out by other tools
  public static UserError invisibleOnlyRegularFilesAndDirs(Path kid){
    return fail(showRel(kid),
      "- This protected path is not a normal file or folder.",
      "- Remove it, and keep only normal files and folders."
    );
  }
  public static UserError invisibleNoTrailingDotOrSpace(RefParent kid, String name){
    return fail(showRel(kid),
      "- A protected name segment ends with a dot or a space.\n"
    + "  Some systems/tools trim these, which causes collisions.\n"
    + "  Bad segment: "+disp(name)+"",
      "- Rename the segment so it does not end with '.' or space."
    );
  }
  public static UserError invisibleInvalidSurrogate(RefParent kid, String name){
    return fail(showRel(kid),
      "- A protected name segment contains invalid Unicode.\n"
    + "  Bad segment: "+disp(name),
      "- Rename the segment to remove the invalid characters."
    );
  }
  public static UserError invisibleNoControlChars(RefParent kid, int cp, String name){
    return fail(showRel(kid),
      "- A protected name segment contains a control character.\n"
     + "  Character: " + Message.displayChar(cp)+"\n"
     + "  Segment: " + disp(name) + "",
      "- Rename the segment to remove the control character."
    );}
  public static UserError invisibleNoWindowsBadChars(RefParent kid, char bad, String name){
    return fail(showRel(kid),
      "- A protected name segment contains a character that Windows forbids.\n"
    + "  Bad char: `"+bad+"`\n"
    + "  Segment: "+disp(name),
      "- Rename the segment to remove Windows-forbidden characters.\n"
    + "  Forbidden on Windows: < > : \" / \\ | ? *"
    );
  }
  public static UserError invisibleWindowsReservedDeviceName(RefParent kid, String base, String name){
    return fail(showRel(kid),
      "- A protected name segment uses a Windows reserved device name.\n"
    + "  Bad base: "+disp(base)+" in segment: "+disp(name)+"",
      "- Rename it so the base name is not a Windows device name.\n"
    + "  Reserved device name: \"con\", \"prn\", \"aux\", \"nul\", \"com1\"..\"com9\", \"lpt1\"..\"lpt9\"."
    );
  }
  public static UserError hiddenSiblingNamesCollide(RefParent kid, String prev, String name, boolean caseOnly, boolean nfcOnly){
    String reason=
      caseOnly ? "Names differ only by case."
      : nfcOnly  ? "Names differ only by Unicode normalization (NFC)."
      :            "Names collide after Unicode NFC normalization and case-folding.";
    return fail(showRel(kid),
      "- Two protected names in the same folder collide.\n"
    + "  Name 1: "+disp(prev)+"\n"
    + "  Name 2: "+disp(name)+"\n"
    + "  Reason: "+reason,
      "- Rename one of them so they are clearly distinct.\n"
    + "- Avoid differences that are only case changes or Unicode-equivalent spellings."
    );
  }

  //-- auto-loaded assets
  public static UserError autoloadedNamesCollide(RefParent kid, String prev, String name, String typeName){
    return fail(showRel(kid),
      "- Auto-loading both of these files would produce two declarations of the same type name.\n"
    + "  Name 1: "+disp(prev)+"\n"
    + "  Name 2: "+disp(name)+"\n"
    + "  Reason: Both would auto-load as the type "+disp(typeName)+".",
      "- Rename one of them so they are clearly distinct.\n"
    + "- Avoid two assets whose folder and file name produce the same auto-loaded type name."
    );
  }

  public static UserError autoloadedNameNotAType(RefParent kid, String typeName){
    return fail(showRel(kid),
      "- Auto-loading this file would declare a type called "+disp(typeName)+".\n"
    + "  That is not a Fearless type name: after any leading underscores, a type name\n"
    + "  must start with an uppercase letter.",
      "- Rename the file so that its name starts with a letter.\n"
    + "  Examples: \"notes.txt\" auto-loads as \"Notes\", \"_notes.txt\" as \"_Notes\"."
    );
  }

  //-- zips, which we read as if they were folders
  public static UserError zipBadEntryName(Path diskZip, List<String> steps, String entryName){
    return directFail(showZipRel(diskZip, steps, entryName),
"There is a zip entry named "+disp(entryName)+"""
\nThis entry name cannot be handled safely and consistently across systems and tools.

Invalid entry names (based on the exact text of the entry name):
- empty name: some tools ignore it, others reject the archive
- name starts with "/" (example: "/a.fear"): dangerous, some tools may unpack it outside the destination (zip slip)
- name contains "//" (example: "a//b.txt"): confusing, there is nothing between the two "/" characters, and tools disagree on the real location
- name is "." or "..", or starts with "./" or "../", or contains "/./" or "/../", or ends with "/." or "/.."
  (examples: "../a", "a/../b", "./a", "a/./b", "a/..", "a/."):
  dangerous, can escape the zip when unpacked (zip slip)
- name contains special or invisible characters: may be changed, hidden, or lost
""");
  }
  public static UserError zipDuplicateEntryName(Path diskZip, List<String> steps, String entryName){
    return directFail(showZipRel(diskZip, steps, entryName),
      "This zip contains more than one entry called "+UserError.printEntryName(entryName)
     +"\nDifferent tools disagree on which one should be used.\n"
     +"Using it may even means that different content is seen in different moments.\n(Schizophrenic ZIP file)");
  }
  public static UserError zipFileUsedAsDirectory(Path diskZip, List<String> steps, String entryName, String nestedEntryName){
    return directFail(showZipRel(diskZip, steps, entryName),
      "This zip contains an entry called "+disp(entryName)+",\n"
     +"and also this other entry nested under it, as if it were a folder:\n"
     +"  "+disp(nestedEntryName)+"\n"
     +"\n"
     +"An entry cannot be both a file and a folder in the same zip.\n"
     +"Different tools disagree on which one should be used: some show the file and\n"
     +"hide what is nested under it, others expand it as a folder and hide the file.");
  }
  public static UserError zipNestingTooDeep(Path diskZip, List<String> steps, int depth, int maxDepth){
    assert !steps.isEmpty();
    var all= Pop.right(steps);
    return directFail(showZipRel(diskZip,all,steps.getLast()),"""
Too many layers of nested zips.
We explored %s layers and there was still more.
Different systems handleds very nested zips differenty; overall if
recursivelly unzipped, it would clearly go over the OS path lenght limit.
""".formatted(depth));}
  //Reached when reading THIS ONE entry exhausted the memory of the whole program,
  //so the size we could report is exactly the size we could not measure.
  public static UserError zipEntryTooBig(Path diskZip, List<String> steps, String entryName){
    return directFail(showZipRel(diskZip, steps, entryName),"""
This zip entry is too big to read.
Fearless ran out of memory while unpacking this single entry.
A zip entry can be much bigger unpacked than it looks inside the zip,
sometimes thousands of times bigger, and we can only check content we can hold.
Remove this entry from the zip, or store its content as normal files instead.
""");}
  public static UserError emptyExpandedZip(Path kid){
    return directFail(showRel(kid),"""
This zip file contains no entries.
This is most likely a mistake.
""");}
  public static UserError zipExpandedPathCollides(RefParent kid, RefParent first, RefParent second){
    return fail(showRel(kid),
      "- Expanding zip files into folders makes this path exist twice, from two\n"
    + "  different places in the project:\n"
    + originBlock(first)+"\n\n"
    + originBlock(second),
      "- Rename one of them, or move/rename the zip file involved, so this path is no\n"
    + "  longer produced twice."
    );
  }
  private static String originBlock(RefParent r){
    if (r instanceof PathEntry p){ return "  Real file/folder:\n  "+showRel(p.local()).strip().replace("\nPath:","\n  Path:"); }
    if (r instanceof ZipEntry z){
      return "  Entry inside a zip, expanded as a folder:\n  "
        + showZipRel(z.root().resolve(z.local()), z.zips(), z.lastZips()).strip().replace("\nPath:","\n  Path:").replace("\nEntry:","\n  Entry:");
    }
    throw Bug.unreachable();
  }

  //-- project layout: which folder defines a package, and the rank file of each package
  public static UserError projectEmpty(Path root){ return new UserError("""
The fearless project folder contains no *.fear files
Folder:
 "%s"
""".formatted(root));
  }

  public static UserError projectNoPackageSegment(Ref file){ return new UserError("""
This file must be placed inside a package.
File:
  %s

Each ".fear" file must be under exactly one folder whose name starts with "_".
That folder name, without the leading "_", defines the package name.

Valid examples (folders may appear before and after the package folder):
  projectRoot/
      +-- src/
      |   +-- _bla/
      |       +-- baz/
      |           +-- foo.fear
      +-- test/
          +-- _bla/
              +-- bar.fear
Here both "foo.fear" and "bar.fear" are in package "bla".
""".formatted(disp(file.fearPath())));
  }
  public static UserError projectBadPackageName(Ref file, String segment){ return new UserError("""
This folder does not name a valid package.
Folder:
  %s

Package name: %s

A folder whose name starts with "_" defines a package: the name after the "_"
is the package name. It must start with a lowercase letter, then use only
lowercase letters (a-z), digits (0-9) and underscore (_), and must not be a
Windows reserved device name: "con", "prn", "aux", "nul", "com1".."com9",
"lpt1".."lpt9".

The package name also becomes a folder name and a file name in the generated
code, so a name Windows reserves would not survive there.

Valid examples:
  "_bla" is the package "bla"
  "_my_pkg2" is the package "my_pkg2"
  "_console" is the package "console"
""".formatted(disp(pkgFolder(file, segment)), disp(segment.substring(1))));
  }
  private static String pkgFolder(Ref file, String segment){
    var s= file.fearPath();
    return s.substring(0, s.indexOf("/"+segment+"/")+segment.length()+1);
  }
  public static UserError projectReservedPackageName(Ref file, String segment){ return new UserError("""
This folder names a reserved package.
Folder:
  %s

Package name: %s

%s

Rename the folder to use a different package name.
""".formatted(disp(pkgFolder(file, segment)), disp(segment.substring(1)), reservedPackageReason(segment.substring(1))));
  }
  private static String reservedPackageReason(String pkg){ return switch(pkg){
    case "base" -> "\"base\" is reserved: it is the name of the Fearless standard library package.";
    case "rank" -> ""
      + "\"rank\" is reserved: it would create a package folder \"_rank\", easily\n"
      + "confused with the \"_rank_<rankName>.fear\" rank-file naming convention used\n"
      + "inside every package (and with the autoloaded names generated from a\n"
      + "package's own folder structure).";
    default -> throw Bug.unreachable();
  };}
  public static UserError projectAmbiguousPackageSegment(Ref file, List<String> candidates){ return new UserError("""
This path contains more than one folder whose name starts with "_":
  %s

Candidates: %s

Exactly one "_pkg" folder must define the package.

Example showing this ambiguity:

  projectRoot/
  +-- src/
      +-- _bla/
          +-- _beer/
              +-- bar.fear

Is "bar.fear" in package "bla" or in package "beer"?

Valid alternatives:

  projectRoot/
  +-- src/
      +-- _bla/
          +-- beer/
              +-- bar.fear

  projectRoot/
  +-- src/
      +-- bla/
          +-- _beer/
              +-- bar.fear
      """.formatted(file, Join.of(candidates.stream().map(c->disp(c)), "",", ","")));
  }
  public static UserError projectMissingRankFile(String pkg, Path pkgRoot){ return new UserError("""
Missing rank file for a package.

Package:
  %s

Each package folder must contain exactly one file whose name follows this pattern:
  "_rank_<rankName>.fear"
or
  "_rank_<rankName><NNN>.fear"

<rankName> is one of:
  base, core, driver, worker, framework, accumulator, tool, app
<NNN> are digits

Examples:
  _rank_app.fear
  _rank_core043.fear
  _rank_worker999.fear

Example of a valid package folder:
  projectRoot/
  +-- src/
      +-- _%s/
          +-- _rank_app.fear
          +-- foo.fear
          +-- sub/
              +-- bar.fear
""".formatted(disp(pkg), pkg));
  }
  public static UserError projectReservedRankPrefix(String pkg, List<Ref> files){ return new UserError("""
"_rank_" is a reserved file name prefix.
Package:
  %s

These names start with "_rank_" but are not the package's rank file:
  %s

"_rank_" is reserved for the package's own single rank file, named:
  "_rank_<rankName>.fear" or "_rank_<rankName><NNN>.fear"

Rename them so they do not start with "_rank_".
""".formatted(disp(pkg), Join.of(files.stream().map(f->disp(f.toString())), "",", ","")));
  }
  public static UserError projectMultipleRankFiles(String pkg, List<Ref> rankFiles){ return new UserError("""
Multiple rank files for the same package.
Package:
  %s

Rank files:
  %s

Each package must contain exactly one rank file.
""".formatted(disp(pkg), Join.of(rankFiles.stream().map(f->disp(f.toString())), "",", ","")));
  }
  public static UserError projectMalformedRankFileName(Ref rankFile){ return new UserError("""
Malformed rank file name.
File:
  %s

Each package folder must contain exactly one file whose name follows this pattern:
  "_rank_<rankName>.fear"
or
  "_rank_<rankName><NNN>.fear"

<rankName> is one of:
  base, core, driver, worker, framework, accumulator, tool, app
<NNN> are digits

Examples:
  _rank_app.fear
  _rank_core043.fear
  _rank_worker999.fear

""".formatted(disp(rankFile.toString())));
  }

  //-- the user's Fearless source. The frontend explains these itself, in the language of
  //the language: parse errors, well formedness, types. Here they only become terminal.
  public static UserError sourceError(String rendered){ return new UserError(rendered); }
}
//TODO: test cases where virtualization map mentions not existing stuff
//TODO: test cases where dependency order is violated. Both cases are likely giving bad unreadable errors.
