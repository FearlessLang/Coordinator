package treeErrorTests;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import coordinator.Coordinator;
import tools.SourceOracle;
import userMessages.Report;

/// The project layout errors: no package folder, ambiguous package folder, and the
/// rank file rules. All of them are decided from the file NAMES alone, before anything
/// is read or written, so an in memory SourceOracle is enough to reach every one of
/// them and no temporary folder is needed.
/// mapConflict is the one exception: it needs the rank files to be parsed, so its two
/// files carry real "map .. as .. in ..;" directives.
public class ProjectTreeErrorTest {
  static{ utils.Err.setUp(AssertionFailedError.class, Assertions::assertEquals, Assertions::assertTrue); }

  static final Path fakeRoot= Path.of("unused","project","root");

  /// Runs the coordinator over an in memory project made of the given
  /// path/content pairs, and returns the message of the error it fails with.
  static String errMsg(String... pathThenContent){
    assert pathThenContent.length % 2 == 0;
    var b= SourceOracle.debugBuilder();
    for(int i=0; i<pathThenContent.length; i+=2){
      b.putURI(URI.create(SourceOracle.root+pathThenContent[i]), pathThenContent[i+1]);
    }
    var oracle= b.build();
    var c= new Coordinator(){
      @Override public Path rtPath(){ return Path.of("unused"); }
      @Override public Path stLibPath(){ return Path.of("unused"); }
      @Override public SourceOracle sourceOracle(Path path){ return oracle; }
    };
    try { c.main(fakeRoot); }
    catch(RuntimeException e){ return e.getMessage(); }
    catch(InterruptedException e){ throw new AssertionError(e); }
    return Assertions.fail("Expected an error, but the project was accepted");
  }
  static void runErr(String expected, String... pathThenContent){
    utils.Err.strCmp(expected, errMsg(pathThenContent));
  }

  @Test void emptyProject(){ runErr("""
The fearless project folder contains no *.fear files
Folder:
 "%s"
""".formatted(fakeRoot),
    "readme.txt","hi"); }

  @Test void noPackageSegment(){ runErr("""
This file must be placed inside a package.
File:
  "fear:/src/foo.fear"

Each ".fear" file must be under exactly one folder whose name starts with "_".
That folder name, without the leading "_", defines the package name.
[###]
""",
    "src/foo.fear",""); }

  @Test void ambiguousPackageSegment(){ runErr("""
This path contains more than one folder whose name starts with "_":
  fear:/src/_bla/_beer/bar.fear

Candidates: "_bla", "_beer"

Exactly one "_pkg" folder must define the package.
[###]
Is "bar.fear" in package "bla" or in package "beer"?
[###]
""",
    "src/_bla/_beer/bar.fear",""); }

  //The folder name after the "_" is the package name, and it also becomes a folder and a
  //file name in the generated code. "con" passes every rule of the project scanner, which
  //only rejects a folder literally called "con".
  @Test void windowsReservedPackageName(){ runErr("""
This folder does not name a valid package.
Folder:
  "fear:/src/_con"

Package name: "con"

A folder whose name starts with "_" defines a package: the name after the "_"
is the package name. It must start with a lowercase letter, then use only
lowercase letters (a-z), digits (0-9) and underscore (_), and must not be a
Windows reserved device name: "con", "prn", "aux", "nul", "com1".."com9",
"lpt1".."lpt9".
[###]
""",
    "src/_con/foo.fear",""); }

  @Test void emptyPackageName(){ runErr("""
This folder does not name a valid package.
Folder:
  "fear:/src/_"

Package name: ""
[###]
""",
    "src/_/foo.fear",""); }

  @Test void packageNameStartingWithADigit(){ runErr("""
This folder does not name a valid package.
Folder:
  "fear:/src/_1abc"

Package name: "1abc"
[###]
""",
    "src/_1abc/foo.fear",""); }

  @Test void missingRankFile(){ runErr("""
Missing rank file for a package.

Package:
  "bla"

Each package folder must contain exactly one file whose name follows this pattern:
  "_rank_<rankName>.fear"
[###]
""",
    "src/_bla/foo.fear",""); }

  @Test void multipleRankFiles(){ runErr("""
Multiple rank files for the same package.
Package:
  "bla"

Rank files:
  "fear:/src/_bla/_rank_app.fear", "fear:/src/_bla/_rank_core.fear"

Each package must contain exactly one rank file.
""",
    "src/_bla/_rank_app.fear","",
    "src/_bla/_rank_core.fear",""); }

  @Test void malformedRankFileName(){ runErr("""
Malformed rank file name.
File:
  "fear:/src/_bla/_rank_bogus.fear"

Each package folder must contain exactly one file whose name follows this pattern:
  "_rank_<rankName>.fear"
[###]
""",
    "src/_bla/_rank_bogus.fear",""); }

  //Two rank files of the SAME rank map the same virtual name to different real
  //packages, so neither can override the other. Reported as a well formedness error.
  @Test void mapConflict(){ runErr("""
For package "pkb", the virtual package name "a" is mapped to different real packages:
 - fear:/_pka/_rank_app999.fear
   "map  a  as  pkc  in  pkb;"
 - fear:/_pkd/_rank_app999.fear
   "map  a  as  pkb  in  pkb;"

These mappings come from rank files with the same priority (same rank),
so there is no higher one to override the other.
[###]
How to fix it:
- Decide which real package should represent "a" inside of "pkb".
[###]
""",
    "_pka/_rank_app999.fear","map a as pkc in pkb;",
    "_pkd/_rank_app999.fear","map a as pkb in pkb;"); }

  @Test void aPlainResourceFileNamedLikeARankFileIsRejectedWithADedicatedMessage(){
    runErr("""
"_rank_" is a reserved file name prefix.
Package:
  "bla"

These names start with "_rank_" but are not the package's rank file:
  "fear:/src/_bla/_rank_notes.txt"

"_rank_" is reserved for the package's own single rank file, named:
  "_rank_<rankName>.fear" or "_rank_<rankName><NNN>.fear"

Rename them so they do not start with "_rank_".
""",
      "src/_bla/_rank_app.fear","",
      "src/_bla/_rank_notes.txt","hello"); }

  @Test void basePackageNameIsReserved(){ runErr("""
This folder names a reserved package.
Folder:
  "fear:/src/_base"

Package name: "base"

"base" is reserved: it is the name of the Fearless standard library package.

Rename the folder to use a different package name.
""",
    "src/_base/foo.fear",""); }

  @Test void rankPackageNameIsReserved(){ runErr("""
This folder names a reserved package.
Folder:
  "fear:/src/_rank"

Package name: "rank"

"rank" is reserved: it would create a package folder "_rank", easily
confused with the "_rank_<rankName>.fear" rank-file naming convention used
inside every package (and with the autoloaded names generated from a
package's own folder structure).

Rename the folder to use a different package name.
""",
    "src/_rank/foo.fear",""); }

  @Test void ambiguousPackageSegmentMessageMustNameTheActualCandidates(){
    record FakeRef(String fearPath) implements SourceOracle.Ref{
      @Override public byte[] loadBytes(){ return new byte[0]; }
      @Override public long lastModified(){ return 0; }
      @Override public String toString(){ return fearPath; }
    }
    SourceOracle.Ref file= new FakeRef("fear:/src/x/y/bar.fear"); // deliberately unrelated to the candidates below
    var candidates= List.of("_zzcandidateone","_zzcandidatetwo");
    var msg= Report.projectAmbiguousPackageSegment(file, candidates).getMessage();
    Assertions.assertTrue(msg.contains("_zzcandidateone"), msg);
    Assertions.assertTrue(msg.contains("_zzcandidatetwo"), msg);
  }
}
