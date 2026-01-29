package badZipsTest;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import coordinatorMessages.UserExit;
import realSourceOracle.RealSourceOracleWithZip;
import testHelperFs.FsDsl;
import utils.ResolveResource;

public class BadZipsTest {
  static{ utils.Err.setUp(AssertionFailedError.class, Assertions::assertEquals, Assertions::assertTrue); }
  static Path root= ResolveResource.badZipCorpous;
  public static void runErrIOE(String in, String expected){
    Path input= root.resolve(in);
    UserExit.root= root;
    var ex= assertThrows(UserExit.class, ()->new RealSourceOracleWithZip(input));
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
}