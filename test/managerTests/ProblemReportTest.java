package managerTests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import managerInfo.ProblemReport;
import tools.Fs;

final class ProblemReportTest{
  private static final String sourceError= """
In file: fear:/_pkb/_rank_app200.fear

002| B:{.text:Str->a.C.text;}
   |               ^^^^^^^^^^

While inspecting a type name
Package "nonexistentpkg" does not exist.
Visible packages: "base".
Error 7 WellFormedness
""";
  @Test void aSourceErrorBecomesItsPathItsLineThenTheWholeMessage(@TempDir Path project){
    ProblemReport.write(project, sourceError);
    assertEquals("_pkb/_rank_app200.fear\n002\n"+sourceError, Fs.readUtf8(ProblemReport.file(project)));
  }
  @Test void aCompileThatSucceedsClearsTheProblem(@TempDir Path project){
    ProblemReport.write(project, sourceError);
    ProblemReport.write(project, "");
    assertEquals("", Fs.readUtf8(ProblemReport.file(project)));
  }
  @Test void anErrorWithNoSourcePositionMarksNothing(@TempDir Path project){
    ProblemReport.write(project, "The fearless project folder contains no *.fear files\n");
    assertEquals("", Fs.readUtf8(ProblemReport.file(project)));
  }
  @Test void aPositionQuotedInsideALongerErrorIsStillFound(@TempDir Path project){
    var wrapped= "Broken reference in a doc comment.\n\n"+sourceError;
    ProblemReport.write(project, wrapped);
    assertEquals("_pkb/_rank_app200.fear\n002\n"+wrapped, Fs.readUtf8(ProblemReport.file(project)));
  }
}
