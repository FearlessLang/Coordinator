package managerTests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import managerInfo.JUnitReport;
import tools.Fs;

final class JUnitReportTest{
  private static final String log= """
PLAN|RUN|_hello/_rank_app.fear|MyTests|MyTests at line: 5|5
PLAN|DISABLED|_hello/_rank_app.fear|Later|Later at line: 9|9
<testcase classname="MyTests" name="MyTests at line: 5" file="_hello/_rank_app.fear" line="5"></testcase>
<testcase classname="Other" name="Other at line: 7" file="_hello/_rank_app.fear" line="7">
<failure>
<expected>3</expected>
<actual>1</actual>
Assertion failure.
</failure>
</testcase>
""";
  private static void writeLog(Path project, String stamp, String content){
    Fs.writeUtf8(project.resolve(".out").resolve("logs").resolve("_base").resolve("unit_test_log$"+stamp+".log"), content);
  }
  @Test void aLogBecomesOneSuiteCountingItsTestsAndItsFailures(@TempDir Path project){
    writeLog(project,"20260904_012117_731Z",log);
    var suite= JUnitReport.suite("demo", project);
    assertTrue(suite.startsWith("<testsuite name=\"demo\" tests=\"3\" failures=\"1\" errors=\"0\">"), suite);
  }
  @Test void aDisabledPlanLineBecomesASkippedTestAndTheRunLinesGo(@TempDir Path project){
    writeLog(project,"20260904_012117_731Z",log);
    var suite= JUnitReport.suite("demo", project);
    assertTrue(suite.contains("<testcase classname=\"Later\" name=\"Later at line: 9\" file=\"_hello/_rank_app.fear\" line=\"9\"><skipped/></testcase>"), suite);
    assertFalse(suite.contains("PLAN|"), suite);
  }
  @Test void theNewestLogIsTheOneReported(@TempDir Path project){
    writeLog(project,"20260901_000000_000Z","<testcase classname=\"Old\" name=\"Old\"></testcase>\n");
    writeLog(project,"20260903_000000_000Z","<testcase classname=\"New\" name=\"New\"></testcase>\n");
    var suite= JUnitReport.suite("demo", project);
    assertTrue(suite.contains("\"New\""), suite);
    assertFalse(suite.contains("\"Old\""), suite);
  }
  @Test void writingWrapsTheSuiteInAJUnitDocument(@TempDir Path project){
    writeLog(project,"20260904_012117_731Z",log);
    JUnitReport.write(project,project,"demo",Instant.parse("2026-09-03T00:00:00Z"));
    var xml= Fs.readUtf8(JUnitReport.file(project));
    assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<testsuites>\n"), xml);
    assertTrue(xml.endsWith("</testsuites>\n"), xml);
  }
  @Test void aRunThatLoggedNoUnitTestsReportsNothing(@TempDir Path project){
    assertEquals("", JUnitReport.suite("demo", project));
    JUnitReport.write(project,project,"demo",Instant.parse("2026-09-03T00:00:00Z"));
    assertFalse(Files.exists(JUnitReport.file(project)));
  }
  //A main that runs no unit tests must not republish the log some earlier run left behind
  @Test void aLogOlderThanThisRunIsNotReported(@TempDir Path project){
    writeLog(project,"20260904_012117_731Z",log);
    JUnitReport.write(project,project,"demo",Instant.parse("2026-09-05T00:00:00Z"));
    assertFalse(Files.exists(JUnitReport.file(project)));
  }
}
