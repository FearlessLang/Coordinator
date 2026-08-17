package badZipsTest;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import userMessages.UserError;
import mainCoordinator.ResolveResource;
import realSourceOracle.RealSourceOracleWithZip;
import testHelperFs.FsDsl;

public class BadZipsTest {
  static{ utils.Err.setUp(AssertionFailedError.class, Assertions::assertEquals, Assertions::assertTrue); }
  static Path root= ResolveResource.badZipCorpous;
  public static void runErrIOE(String in, String expected){
    Path input= root.resolve(in);
    UserError.root= root;
    var ex= assertThrows(UserError.class, ()->new RealSourceOracleWithZip(input));
    String res= FsDsl.dumpErr(input,ex);
    utils.Err.strCmp(expected, res);
  }
@Test void zip1(){ runErrIOE("zip1","""
Root: [###]
Path: "unzip_bad_lzma_1.zip"
This zip file contains no entries.
This is most likely a mistake.


We check this so that you[###]
"""); }
@Test void zip2(){ runErrIOE("zip2","""
Root: [###]
Path: "unzip_bad_lzma_2.zip"
This zip file contains no entries.
This is most likely a mistake.


We check this so that you[###]
"""); }
@Test void zip3(){ runErrIOE("zip3","""
Root: [###]
Path: "zip3/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip/r/r.zip"
Entry: "r/r.zip"


Too many layers of nested zips.
We explored 65 layers and there was still more.
Different systems handleds very nested zips differenty; overall if
recursivelly unzipped, it would clearly go over the OS path lenght limit.


We check this so that you[###]
"""); }
@Test void zip4(){ runErrIOE("zip4","""
Root: [###]
Path: "zip4/file_used_as_folder.zip"
Entry: "readme"


This zip contains an entry called "readme",
and also this other entry nested under it, as if it were a folder:
  "readme/notes.txt"

An entry cannot be both a file and a folder in the same zip.
Different tools disagree on which one should be used: some show the file and
hide what is nested under it, others expand it as a folder and hide the file.

We check this so that you[###]
"""); }
}