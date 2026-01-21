package sourceOracleTests;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import testHelperFs.FsDsl;

final class RealSourceOracleWithZipDslTest{

  private void testOk(Path tmp,String in,String out){
    assertEquals(out, FsDsl.runOk(tmp, in));
  }

  private void testErr(Path tmp,String in,String err){
    assertEquals(err, FsDsl.runErr(tmp, in));//At a later stage this could be changed with any other kind of string result comparision, including ways to be less flaky
  }

  @Test void err_zip_duplicate_entry_name(@TempDir Path tmp){ testErr(tmp, """
_pkg/z.zip/a.fear
iii
1
jjj
_pkg/z.zip/a.fear
iii
2
""","""
<put the expected UserExit message here>
""");}

// Add these tests to RealSourceOracleWithZipDslTest

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

  @Test void err_empty_directory(@TempDir Path tmp){ testErr(tmp, """
_pkg/
iii
""","""
<expected UserExit.emptyDirectory message, with <root> placeholders if present>
""");}

  @Test void err_empty_expanded_zip_empty_zip(@TempDir Path tmp){ testErr(tmp, """
_pkg/e.zip
iii
""","""
<expected UserExit.emptyExpandedZip message>
""");}

  @Test void err_empty_expanded_zip_dir_entries_only(@TempDir Path tmp){ testErr(tmp, """
_pkg/e.zip/d/
iii
""","""
<expected UserExit.emptyExpandedZip message>
""");}

  @Test void err_needs_extension(@TempDir Path tmp){ testErr(tmp, """
_pkg/a
iii
X
""","""
<expected UserExit.needsExtension message>
""");}

  @Test void err_visible_must_start_with_letter_or_underscore(@TempDir Path tmp){ testErr(tmp, """
_pkg/1a.fear
iii
X
""","""
<expected UserExit.visibleMustStartWithLetterOrUnderscore message>
""");}

  @Test void err_visible_invalid_char_uppercase(@TempDir Path tmp){ testErr(tmp, """
_pkg/A.fear
iii
X
""","""
<expected UserExit.visibleInvalidChar message>
""");}

  @Test void err_visible_no_double_underscore(@TempDir Path tmp){ testErr(tmp, """
_pkg/a__b.fear
iii
X
""","""
<expected UserExit.visibleNoDoubleUnderscore message>
""");}

  @Test void err_missing_extension_after_dot_in_zip(@TempDir Path tmp){ testErr(tmp, """
_pkg/z.zip/a.
iii
X
""","""
<expected UserExit.missingExtension message>
""");}

  @Test void err_ext_segment_too_long_in_zip(@TempDir Path tmp){ testErr(tmp, """
_pkg/z.zip/a.abcdefghijklmnopq
iii
X
""","""
<expected UserExit.extLenMustBe1To16 message>
""");}

  @Test void err_ext_invalid_char_in_zip(@TempDir Path tmp){ testErr(tmp, """
_pkg/z.zip/a.txT
iii
X
""","""
<expected UserExit.extInvalidChar message>
""");}

  @Test void err_multi_dot_ext_not_allowed_in_zip(@TempDir Path tmp){ testErr(tmp, """
_pkg/z.zip/a.aa.bb.cc
iii
X
""","""
<expected UserExit.multiDotExtNotAllowed message>
""");}

  @Test void err_windows_reserved_visible_name_in_zip(@TempDir Path tmp){ testErr(tmp, """
_pkg/z.zip/con.fear
iii
X
""","""
<expected UserExit.windowsReservedName message>
""");}

  @Test void err_invisible_trailing_dot_in_zip(@TempDir Path tmp){ testErr(tmp, """
_pkg/z.zip/.d/a.
iii
X
""","""
<expected UserExit.invisibleNoTrailingDotOrSpace message>
""");}

  @Test void err_invisible_windows_bad_char_colon_in_zip(@TempDir Path tmp){ testErr(tmp, """
_pkg/z.zip/.d/a:b.txt
iii
X
""","""
<expected UserExit.invisibleNoWindowsBadChars message>
""");}

  @Test void err_invisible_control_char_in_zip(@TempDir Path tmp){ testErr(tmp, """
_pkg/z.zip/.d/a\u0001b.txt
iii
X
""","""
<expected UserExit.invisibleNoControlChars message>
""");}

  @Test void err_invisible_windows_reserved_device_name_in_zip(@TempDir Path tmp){ testErr(tmp, """
_pkg/z.zip/.d/con.txt
iii
X
""","""
<expected UserExit.invisibleWindowsReservedDeviceName message>
""");}

  @Test void err_hidden_sibling_case_collision_in_zip(@TempDir Path tmp){ testErr(tmp, """
_pkg/z.zip/.d/a.txt
iii
1
jjj
_pkg/z.zip/.d/A.txt
iii
2
""","""
<expected UserExit.hiddenSiblingNamesCollide message>
""");}

  @Test void err_hidden_sibling_nfc_collision_in_zip(@TempDir Path tmp){ testErr(tmp, """
_pkg/z.zip/.d/\u00e9.txt
iii
1
jjj
_pkg/z.zip/.d/e\u0301.txt
iii
2
""","""
<expected UserExit.hiddenSiblingNamesCollide message>
""");}

  @Test void err_zip_bad_entry_absolute(@TempDir Path tmp){ testErr(tmp, """
_pkg/z.zip//a.fear
iii
X
""","""
<expected UserExit.zipBadEntryName message>
""");}

  @Test void err_zip_bad_entry_dot_segment(@TempDir Path tmp){ testErr(tmp, """
_pkg/z.zip/./a.fear
iii
X
""","""
<expected UserExit.zipBadEntryName message>
""");}

  @Test void err_zip_bad_entry_dotdot_segment(@TempDir Path tmp){ testErr(tmp, """
_pkg/z.zip/../a.fear
iii
X
""","""
<expected UserExit.zipBadEntryName message>
""");}

  @Test void err_zip_bad_entry_nul(@TempDir Path tmp){ testErr(tmp, """
_pkg/z.zip/a\u0000b.fear
iii
X
""","""
<expected UserExit.zipBadEntryName message>
""");}

  @Test void err_zip_file_dir_prefix_conflict(@TempDir Path tmp){ testErr(tmp, """
_pkg/z.zip/a
iii
X
jjj
_pkg/z.zip/a/b.txt
iii
Y
""","""
<expected UserExit.zipFileDirPrefixConflict message>
""");}
}
