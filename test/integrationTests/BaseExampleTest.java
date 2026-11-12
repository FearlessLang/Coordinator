package integrationTests;

import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.AssertionFailedError;

import resources.ResolveResource;
import testBuildBase.TestBuildBase;
import tools.Fs;

final class BaseExampleTest{
  static{ utils.Err.setUp(AssertionFailedError.class, Assertions::assertEquals, Assertions::assertTrue); }
  @Test void aFailingExampleOfBaseInAFileOfTheErrorMachineryIsReportedAtItsLine(@TempDir Path tmp){
    var base= tmp.resolve("base");
    Fs.copyTree(ResolveResource.stLibPath, base);
    var action= base.resolve("action.fear");
    var text= Fs.readUtf8(action)+"///A probe.\n//> .check{1 .assertEq 2}\nMachineryProbe:{}\n";
    Fs.writeUtf8(action, text);
    var line= text.lines().toList().indexOf("//> .check{1 .assertEq 2}")+1;
    utils.Err.strCmp("""
Test failure _MachineryProbe_Examples at line: %d in file: action.fear
[###]""".formatted(line), TestBuildBase.run(base, tmp.resolve("dbgOut")));
  }
}
