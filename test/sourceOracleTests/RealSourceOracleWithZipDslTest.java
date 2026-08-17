package sourceOracleTests;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.AssertionFailedError;

import userMessages.UserError;
import userMessages.Report;
import realSourceOracle.RealSourceOracleWithZip;
import realSourceOracle.SourceOracleWithAutoload;
import testHelperFs.FsDsl;
import static testHelperFs.FsDsl.*;

final class RealSourceOracleWithZipDslTest{

  static{ utils.Err.setUp(AssertionFailedError.class, Assertions::assertEquals, Assertions::assertTrue); }

  private void testOk(Path tmp,String in,String out){
    assertEquals(out, FsDsl.runOk(tmp, in));
  }
  private void autoloadErr(Path tmp, String pkgName, String spec, String expected){
    Path root= tmp.resolve("root").toAbsolutePath().normalize();
    UserError.root= root;
    FsDsl.materialize(root, spec);
    var base= new RealSourceOracleWithZip(root);
    var ex= assertThrows(UserError.class, ()->SourceOracleWithAutoload.of(base, pkgName));
    utils.Err.strCmp(expected, ex.getMessage());
  }
//Java can not create the broken zip
@Disabled @Test void err_zip_duplicate_entry_name(@TempDir Path tmp){ runErrIOE(tmp, """
_pkg/z.zip/a.fear
iii
1
jjj
_pkg/z.zip/a.fear
iii
2
""","""
<put the expected UserError message here>
""");}

@Test void err_zip_duplicate_entry_name2(@TempDir Path tmp){ runErrIOE(tmp, """
_pkg/z.zip/a.fear
iii
1
jjj
_pkg/z.zip/a.fear/
iii
""","""
Root: [###]
Path: "_pkg/z.zip"
Entry: "a.fear"


This zip contains more than one entry called Entry: "a.fear"

Different tools disagree on which one should be used.
Using it may even means that different content is seen in different moments.
(Schizophrenic ZIP file)

We check this so that you[###]
""");}

  @Test void ok_two_disk_files(@TempDir Path tmp){ testOk(tmp, """
_pkg/a.fear
iii
A
jjj
_pkg/b.fear
iii
B
""","""
--- fear:/_pkg/a.fear
A
--- fear:/_pkg/b.fear
B

""");}

  @Test void ok_disk_and_zip_mix(@TempDir Path tmp){ testOk(tmp, """
_pkg/a.fear
iii
A
jjj
_pkg/b.zip/c.fear
iii
C
jjj
_pkg/b.zip/a/b.fear
iii
AB
""","""
--- fear:/_pkg/a.fear
A
--- fear:/_pkg/b/a/b.fear
AB

--- fear:/_pkg/b/c.fear
C
""");}

  @Test void ok_nested_zip(@TempDir Path tmp){ testOk(tmp, """
_pkg/o.zip/p.zip/q.fear
iii
Q
""","""
--- fear:/_pkg/o/p/q.fear
Q

""");}

  @Test void ok_zip_entry_with_slashes(@TempDir Path tmp){ testOk(tmp, """
_pkg/z.zip/a/b/c.fear
iii
C
""","""
--- fear:/_pkg/z/a/b/c.fear
C

""");}

  @Test void ok_two_separate_zips(@TempDir Path tmp){ testOk(tmp, """
_pkg/z1.zip/a.fear
iii
1
jjj
_pkg/z2.zip/a.fear
iii
2
""","""
--- fear:/_pkg/z1/a.fear
1
--- fear:/_pkg/z2/a.fear
2

""");}

  @Test void ok_multiline_content(@TempDir Path tmp){ testOk(tmp, """
_pkg/a.fear
iii
line1
line2
""","""
--- fear:/_pkg/a.fear
line1
line2

""");}

  @Test void ok_invisible_excluded(@TempDir Path tmp){ testOk(tmp, """
_pkg/.d/a.txt
iii
X
jjj
_pkg/v.fear
iii
V
""","""
--- fear:/_pkg/v.fear
V

""");}

  @Test void err_empty_directory(@TempDir Path tmp){ runErrIOE(tmp, """
_pkg/
iii
""","""
Root: [###]
Path: "_pkg"
This directory is empty.
Different systems handleds empty directories differently,
and they may not be supported by compression tools (zip)
or version control system (git).


We check this so that you[###]
""");}

  @Test void err_empty_expanded_zip_empty_zip(@TempDir Path tmp){ runErrIOE(tmp, """
_pkg/e.zip
iii
""","""
Root: [###]
Path: "_pkg/e.zip"
This zip file contains no entries.
This is most likely a mistake.


We check this so that you[###]
""");}

  @Test void err_empty_expanded_zip_dir_entries_only(@TempDir Path tmp){ runErrIOE(tmp, """
_pkg/e.zip/d/
iii
""","""
Root: [###]
Path: "_pkg/e.zip"
This zip file contains no entries.
This is most likely a mistake.


We check this so that you[###]
""");}

  @Test void err_needs_extension(@TempDir Path tmp){ runErrIOE(tmp, """
_pkg/a
iii
X
""","""
Invalid path in this project folder.

Root: [###]
Path: "_pkg/a"

What went wrong
- This file has no extension.
  Files normally must be named like "name.ext" (one dot).

How to fix
- Rename it to have a single extension (example: "foo.txt", "source.fear").
- Rename it to a well-known extensionless file (example: "readme").

We check this so that[###]
""");}

  @Test void err_visible_must_start_with_letter_or_underscore(@TempDir Path tmp){ runErrIOE(tmp, """
_pkg/1a.fear
iii
X
""","""
Invalid path in this project folder.

Root: [###]
Path: "_pkg/1a.fear"

What went wrong
- A visible folder/file name starts with an invalid character.
  Visible names must start with a lowercase letter (a-z) or underscore (_).

How to fix
- Rename it to start with a-z or _.
  Examples: "foo", "_tmp", "foo1".

We check this so that you[###]
""");}

  @Test void err_visible_invalid_char_uppercase(@TempDir Path tmp){ runErrIOE(tmp, """
_pkg/A.fear
iii
X
""","""
Invalid path in this project folder.

Root: [###]
Path: "_pkg/A.fear"

What went wrong
- A visible folder/file name starts with an invalid character.
  Visible names must start with a lowercase letter (a-z) or underscore (_).

How to fix
- Rename it to start with a-z or _.
  Examples: "foo", "_tmp", "foo1".

We check this so that you[###]
""");}

  @Test void err_visible_invalid_char(@TempDir Path tmp){ runErrIOE(tmp, """
_pkg/fo-o.fear
iii
X
""","""
Invalid path in this project folder.

Root: [###]
Path: "_pkg/fo-o.fear"

What went wrong
- A visible folder/file name contains an unsupported character: '-'.
  Visible names may use only lowercase letters (a-z), digits (0-9), and underscore (_).

How to fix
- Rename it to use only lowercase letters, digits, and underscores.
  Examples: "foo_bar2", "src1", "_cache".

We check this so that you[###]
""");}

  @Test void err_visible_no_double_underscore(@TempDir Path tmp){ runErrIOE(tmp, """
_pkg/a__b.fear
iii
X
""","""
Invalid path in this project folder.

Root: [###]
Path: "_pkg/a__b.fear"

What went wrong
- A visible folder/file name contains a double underscore (__).
  Double underscores are reserved to avoid accidental collisions and confusion.

How to fix
- Rename it to remove '__'.
  Example: change "foo__bar" to "foo_bar".

We check this so that you[###]
""");}

  @Test void err_missing_extension_after_dot_in_zip(@TempDir Path tmp){ runErrIOE(tmp, """
_pkg/z.zip/a.
iii
X
""","""
Invalid path in this project folder.

Root: [###]
Path: "_pkg/z/a."

What went wrong
- The file name ends with a dot.
  That means the extension is missing.

How to fix
- Rename it to "name.ext" with one dot.
  Example: change "foo." to "foo.txt".

We check this so that you[###]
""");}

  @Test void err_ext_segment_too_long_in_zip(@TempDir Path tmp){ runErrIOE(tmp, """
_pkg/z.zip/a.abcdefghijklmnopq
iii
X
""","""
Invalid path in this project folder.

Root: [###]
Path: "_pkg/z/a.abcdefghijklmnopq"

What went wrong
- The file extension is too long.
  Extensions must be 1..16 characters.

How to fix
- Use a shorter extension (1..16 characters), using only lowercase letters and digits.

We check this so that[###]
""");}

  @Test void err_ext_invalid_char_in_zip(@TempDir Path tmp){ runErrIOE(tmp, """
_pkg/z.zip/a.txT
iii
X
""","""
Invalid path in this project folder.

Root: [###]
Path: "_pkg/z/a.txT"

What went wrong
- The file extension contains an unsupported character: "T".
  Extensions may use only lowercase letters (a-z) and digits (0-9).

How to fix
- Rename the file to use an extension made only of lowercase letters and digits.
  Examples: ".txt", ".fear", ".md", ".tar.gz"

We check this so that you[###]
""");}

  @Test void err_multi_dot_ext_not_allowed_in_zip(@TempDir Path tmp){ runErrIOE(tmp, """
_pkg/z.zip/a.aa.bb.cc
iii
X
""","""
Invalid path in this project folder.

Root: [###]
Path: "_pkg/z/a.aa.bb.cc"

What went wrong
- This file name has more than one dot in the extension part.
  Most files must use exactly one dot: "name.ext".

How to fix
- Rename it to use a single extension, OR
- Rename it to use a well-known extensionless file (example: "tar.gz").

We check this so that you[###]
""");}

  @Test void err_windows_reserved_visible_name_in_zip(@TempDir Path tmp){ runErrIOE(tmp, """
_pkg/z.zip/con.fear
iii
X
""","""
Invalid path in this project folder.

Root: [###]
Path: "_pkg/z/con.fear"

What went wrong
- A visible folder/file name is reserved on Windows (device name).
  Even if you add an extension, Windows treats it as the same reserved name.

How to fix
- Rename the folder/file so its base name is not a Windows device name.
  Reserved device name: "con", "prn", "aux", "nul", "com1".."com9", "lpt1".."lpt9"s.

We check this so that you[###]
""");}

  @Test void err_invisible_trailing_dot_in_zip(@TempDir Path tmp){ runErrIOE(tmp, """
_pkg/z.zip/.d/a.
iii
X
""","""
Invalid path in this project folder.

Root: [###]
Path: "_pkg/z/.d/a."

What went wrong
- A protected name segment ends with a dot or a space.
  Some systems/tools trim these, which causes collisions.
  Bad segment: "a."

How to fix
- Rename the segment so it does not end with '.' or space.

We check this so that you[###]
""");}

  @Test void err_invisible_windows_bad_char_colon_in_zip(@TempDir Path tmp){ runErrIOE(tmp, """
_pkg/z.zip/.d/a:b.txt
iii
X
""","""
Invalid path in this project folder.

Root: [###]
Path: "_pkg/z/.d/a:b.txt"

What went wrong
- A protected name segment contains a character that Windows forbids.
  Bad char: `:`
  Segment: "a:b.txt"

How to fix
- Rename the segment to remove Windows-forbidden characters.
  Forbidden on Windows: < > : " / \\ | ? *

We check this so that you[###]
""");}

  @Test void err_invisible_control_char_in_zip(@TempDir Path tmp){ runErrIOE(tmp, """
_pkg/z.zip/.d/a\u0001b.txt
iii
X
""","""
Invalid path in this project folder.

Root: [###]
Path: "_pkg/z/.d/a" [Start Of Heading 0x01] "b.txt"

What went wrong
- A protected name segment contains a control character.
  Character: [Start Of Heading 0x01]
  Segment: "a" [Start Of Heading 0x01] "b.txt"

How to fix
- Rename the segment to remove the control character.

We check this so that you[###]
""");}

  @Test void err_invisible_windows_reserved_device_name_in_zip(@TempDir Path tmp){ runErrIOE(tmp, """
_pkg/z.zip/.d/con.txt
iii
X
""","""
Invalid path in this project folder.

Root: [###]
Path: "_pkg/z/.d/con.txt"

What went wrong
- A protected name segment uses a Windows reserved device name.
  Bad base: "con" in segment: "con.txt"

How to fix
- Rename it so the base name is not a Windows device name.
  Reserved device name: "con", "prn", "aux", "nul", "com1".."com9", "lpt1".."lpt9".

We check this so that you[###]
""");}

  @Test void err_hidden_sibling_case_collision_in_zip(@TempDir Path tmp){ runErrIOE(tmp, """
_pkg/z.zip/.d/a.txt
iii
1
jjj
_pkg/z.zip/.d/A.txt
iii
2
""","""
Invalid path in this project folder.

Root: [###]
Path: "_pkg/z/.d/A.txt"

What went wrong
- Two protected names in the same folder collide.
  Name 1: "a.txt"
  Name 2: "A.txt"
  Reason: Names differ only by case.

How to fix
- Rename one of them so they are clearly distinct.
- Avoid differences that are only case changes or Unicode-equivalent spellings.

We check this so that you[###]
""");}

  @Test void err_hidden_sibling_nfc_collision_in_zip(@TempDir Path tmp){ runErrIOE(tmp, """
_pkg/z.zip/.d/\u00e9.txt
iii
1
jjj
_pkg/z.zip/.d/e\u0301.txt
iii
2
""","""
Invalid path in this project folder.

Root: [###]
Path: "_pkg/z/.d/e" [U+0301] ".txt"

What went wrong
- Two protected names in the same folder collide.
  Name 1: [U+00E9] ".txt"
  Name 2: "e" [U+0301] ".txt"
  Reason: Names differ only by Unicode normalization (NFC).

How to fix
- Rename one of them so they are clearly distinct.
- Avoid differences that are only case changes or Unicode-equivalent spellings.

We check this so that you[###]
""");}

  @Test void err_zip_bad_entry_absolute(@TempDir Path tmp){ runErrIOE(tmp, """
_pkg/z.zip//a.fear
iii
X
""","""
Root: [###]
Path: "_pkg/z.zip"
Entry: "/a.fear"


There is a zip entry named "/a.fear"
This entry name cannot be handled safely and consistently across systems and tools.

Invalid entry names[###]
""");}

  @Test void err_zip_bad_entry_dot_segment(@TempDir Path tmp){ runErrIOE(tmp, """
_pkg/z.zip/./a.fear
iii
X
""","""
Root: [###]
Path: "_pkg/z.zip"
Entry: "./a.fear"


There is a zip entry named "./a.fear"
This entry name cannot be handled safely and consistently across systems and tools.

Invalid entry names (based on the exact text of the entry name):[###]
""");}

@Test void err_zip_bad_entry_dotdot_segment(@TempDir Path tmp){ runErrIOE(tmp, """
_pkg/z.zip/../a.fear
iii
X
""","""
Root: [###]
Path: "_pkg/z.zip"
Entry: "../a.fear"


There is a zip entry named "../a.fear"
This entry name cannot be handled safely and consistently across systems and tools.

Invalid entry names[###]
""");}

@Test void err_zip_bad_entry_ends_with_slash_dot(@TempDir Path tmp){ runErrIOE(tmp, """
_pkg/z.zip/a/.
iii
X
""","""
Root: [###]
Path: "_pkg/z.zip"
Entry: "a/."


There is a zip entry named "a/."
This entry name cannot be handled safely and consistently across systems and tools.

Invalid entry[###]
""");}

@Test void err_zip_bad_entry_ends_with_slash_dotdot(@TempDir Path tmp){ runErrIOE(tmp, """
_pkg/z.zip/a/..
iii
X
""","""
Root: [###]
Path: "_pkg/z.zip"
Entry: "a/.."


There is a zip entry named "a/.."
This entry name cannot be handled safely and consistently across systems and tools.

Invalid entry names[###]
""");}

  @Test void err_zip_bad_entry_nul(@TempDir Path tmp){ runErrIOE(tmp, """
_pkg/z.zip/a\u0000b.fear
iii
X
""","""
Root: [###]
Path: "_pkg/z.zip"
Entry contains non-standard characters.
Shown as: "a" [Null 0x00] "b.fear"

There is a zip entry named "a" [Null 0x00] "b.fear"
This entry name cannot be handled safely and consistently across systems and tools.

Invalid entry names (based on the exact text of the entry name):
- empty name: some tools ignore it, others reject the archive
- name starts with "/" (example: "/a.fear"): dangerous, some tools may unpack it outside the destination (zip slip)
- name contains "//" (example: "a//b.txt"): confusing, there is nothing between the two "/" characters, and tools disagree on the real location
- name is "." or "..", or starts with "./" or "../", or contains "/./" or "/../", or ends with "/." or "/.."
  (examples: "../a", "a/../b", "./a", "a/./b", "a/..", "a/."):
  dangerous, can escape the zip when unpacked (zip slip)
- name contains special or invisible characters: may be changed, hidden, or lost


We check this so that you[###]
""");}

  @Test void err_zip_file_dir_prefix_conflict(@TempDir Path tmp){ runErrIOE(tmp, """
_pkg/z.zip/a
iii
X
jjj
_pkg/z.zip/a/b.txt
iii
Y
""","""
Invalid path in this project folder.

Root: [###]
Path: "_pkg/z/a"

What went wrong
- This file has no extension.
  Files normally must be named like "name.ext" (one dot).

How to fix
- Rename it to have a single extension (example: "foo.txt", "source.fear").
- Rename it to a well-known extensionless file (example: "readme").

We check this so that you[###]
""");}

  @Test void err_real_folder_collides_with_zip_expanded_folder(@TempDir Path tmp){ runErrIOE(tmp, """
_pkg/z/foo.fear
iii
X
jjj
_pkg/z.zip/foo.fear
iii
Y
""","""
Invalid path in this project folder.

Root: [###]
Path: "_pkg/z/foo.fear"

What went wrong
- Expanding zip files into folders makes this path exist twice, from two
  different places in the project:
  - "the entry foo.fear inside the zip _pkg/z.zip"
  - "a real file/folder at _pkg/z/foo.fear"

How to fix
- Rename one of them, or move/rename the zip file involved, so this path is no
  longer produced twice.

We check this so that you[###]
""");}

  @Test void err_visible_symlink_forbidden(@TempDir Path tmp) throws Exception{
    Path root= tmp.resolve("root").toAbsolutePath().normalize();
    UserError.root= root;
    Files.createDirectories(root.resolve("_pkg"));
    Files.createSymbolicLink(root.resolve("_pkg/link.fear"), root.resolve("_pkg/missing.fear"));
    var ex= assertThrows(UserError.class, ()->new RealSourceOracleWithZip(root));
    utils.Err.strCmp("""
Invalid path in this project folder.

Root: [###]
Path: "<root>/_pkg/link.fear"

What went wrong
- This path is a symbolic link.
  Symbolic links behave differently across systems and tools, so we forbid them.

How to fix
- Replace the symbolic link with a real file/folder.
- If you need the linked content, copy it into the repository.

We check this so that you[###]
""", FsDsl.dumpErr(root, ex));
  }

  @Test void err_invisible_symlink_forbidden(@TempDir Path tmp) throws Exception{
    Path root= tmp.resolve("root").toAbsolutePath().normalize();
    UserError.root= root;
    Files.createDirectories(root.resolve("_pkg/.d"));
    Files.createSymbolicLink(root.resolve("_pkg/.d/link"), root.resolve("_pkg/.d/missing"));
    var ex= assertThrows(UserError.class, ()->new RealSourceOracleWithZip(root));
    utils.Err.strCmp("""
Invalid path in this project folder.

Root: [###]
Path: "<root>/_pkg/.d/link"

What went wrong
- This protected path is a symbolic link.
  Even though protected paths are ignored, symbolic links can cause surprises across systems/tools.

How to fix
- Replace the symbolic link with a real file/folder, or remove it.

We check this so that you[###]
""", FsDsl.dumpErr(root, ex));
  }

  @Test void err_extensionless_masks_extension(@TempDir Path tmp){ runErrIOE(tmp, """
_pkg/readme
iii
X
jjj
_pkg/readme.md
iii
Y
""","""
Invalid path in this project folder.

Root: [###]
Path: "_pkg/readme.md"

What went wrong
- Both an allowed extensionless file and an extended file share the same base name.
  Extensionless file:
  Root: [###]
  Path: "_pkg/readme"

  Extended file:
  Root: [###]
  Path: "_pkg/readme.md"

  This is confusing in file browsers (extensions may be hidden).

How to fix
- Rename one of them so they are clearly distinct.

We check this so that you[###]
""");}

  @Test void err_path_too_long(@TempDir Path tmp){
    String longName= "a".repeat(200);
    runErrIOE(tmp, ("""
_pkg/%s.fear
iii
X
""").formatted(longName), ("""
Invalid path in this project folder.

Root: [###]
Path: "_pkg/%s.fear"

What went wrong
- The path is longer than 200 characters.
  Long paths often fail on Windows and in some tools.

How to fix
- Shorten folder/file names, or move this content closer to the project root.

We check this so that you[###]
""").formatted(longName));
  }

  // Real filesystem paths can't carry a raw UTF-16 surrogate through UTF-8 encoding,
  // so this exercises the message factory directly rather than through a real scan.
  @Test void err_invisible_invalid_surrogate_message(@TempDir Path tmp) throws Exception{
    Path root= tmp.resolve("root").toAbsolutePath().normalize();
    UserError.root= root;
    Files.createDirectories(root.resolve("_pkg"));
    Files.writeString(root.resolve("_pkg/a.fear"), "X");
    var oracle= new RealSourceOracleWithZip(root);
    var ref= oracle.allFiles().get(0);
    var ex= Report.invisibleInvalidSurrogate(ref, ".x\uD800y");
    utils.Err.strCmp("""
Invalid path in this project folder.

Root: [###]
Path: "_pkg/a.fear"

What went wrong
- A protected name segment contains invalid Unicode.
  Bad segment: ".x" [HIGH SURROGATE U+D800] "y"

How to fix
- Rename the segment to remove the invalid characters.

We check this so that you[###]
""", ex.getMessage());
  }

  // Two assets that generate the same auto-loaded type name must be rejected, naming both
  // real files, instead of silently declaring that type twice in the synthetic
  // autoloaded_assets.fear file (see realSourceOracle.SourceOracleWithAutoload.generate).
  @Test void err_asset_autoload_name_collision(@TempDir Path tmp){ autoloadErr(tmp, "_assets", """
_assets/foo.txt
iii
hello
jjj
_assets/foo.png
iii
ignored
""","""
Invalid path in this project folder.

Root: [###]
Path: "_assets/foo.txt"

What went wrong
- Auto-loading both of these files would produce two declarations of the same type name.
  Name 1: "fear:/_assets/foo.png"
  Name 2: "fear:/_assets/foo.txt"
  Reason: Both would auto-load as the type "Foo".

How to fix
- Rename one of them so they are clearly distinct.
- Avoid two assets whose folder and file name produce the same auto-loaded type name.

We check this so that you[###]
"""); }

  @Test void err_asset_autoload_name_collision_same_handler(@TempDir Path tmp){ autoloadErr(tmp, "_assets", """
_assets/foo.png
iii
1
jjj
_assets/foo.jpg
iii
2
""","""
Invalid path in this project folder.

Root: [###]
Path: "_assets/foo.png"

What went wrong
- Auto-loading both of these files would produce two declarations of the same type name.
  Name 1: "fear:/_assets/foo.jpg"
  Name 2: "fear:/_assets/foo.png"
  Reason: Both would auto-load as the type "Foo".

How to fix
- Rename one of them so they are clearly distinct.
- Avoid two assets whose folder and file name produce the same auto-loaded type name.

We check this so that you[###]
"""); }

  @Test void ok_asset_autoload_no_collision_for_distinct_names(@TempDir Path tmp){
    Path root= tmp.resolve("root");
    UserError.root= root;
    FsDsl.materialize(root, """
_assets/foo.txt
iii
hello
jjj
_assets/bar.txt
iii
world
""");
    var base= new RealSourceOracleWithZip(root);
    var res= SourceOracleWithAutoload.of(base, "_assets");
    var generated= res.newRefs().getFirst().loadString();
    assertTrue(generated.contains("Foo: base.TxtFile{"), generated);
    assertTrue(generated.contains("Bar: base.TxtFile{"), generated);
  }
}
