package managerTests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import managerData.Kind;
import managerInfo.FolderFacts;
import tools.Fs;

final class FolderFactsTest{
  static Path project(Path dir, String name){
    var res= dir.resolve(name);
    Fs.writeUtf8(res.resolve("_hello").resolve("_rank_app.fear"),"use base.Main as Main;\n");
    Fs.writeUtf8(res.resolve("readme"),"hi\n");
    return res;
  }
  static void cache(Path project, String pkg, long stamp){
    var out= project.resolve(".fearless_out");
    at(out.resolve(pkg+".built"),"fear:/_"+pkg+"/_rank_app.fear\n",stamp);
    at(out.resolve(pkg+".json"),"{}\n",stamp);
  }
  private static void at(Path file, String content, long stamp){
    Fs.writeUtf8(file,content);
    Fs.ofV(()->Files.setLastModifiedTime(file,FileTime.fromMillis(stamp)));
  }
  static long after(Path project){ return FolderFacts.modified(project)+1000; }
  @Test void countsAndSizesTheSourceFiles(@TempDir Path dir){
    var facts= FolderFacts.of(project(dir,"someProject"),Kind.code);
    assertEquals(2, facts.files());
    assertEquals("use base.Main as Main;\n".length()+"hi\n".length(), facts.bytes());
    assertTrue(facts.modified() > 0);
  }
  @Test void theCompiledCacheIsNotCountedAsSource(@TempDir Path dir){
    var project= project(dir,"someProject");
    cache(project,"hello",after(project));
    var facts= FolderFacts.of(project,Kind.code);
    assertEquals(2, facts.files());
    assertTrue(facts.jsonStamp() > facts.modified());
  }
  //A log or an Eclipse report lands after the build stamp, inside the project folder
  @Test void whatFearlessWritesAboutAProjectIsNotCountedAsSource(@TempDir Path dir){
    var project= project(dir,"someProject");
    var stamp= after(project);
    cache(project,"hello",stamp);
    var written= project.resolve(FolderFacts.runDir);
    at(written.resolve("eclipse").resolve("problems.txt"),"",stamp+1000);
    at(written.resolve("logs").resolve("_base").resolve("unit_test_log.log"),"x\n",stamp+1000);
    var facts= FolderFacts.of(project,Kind.code);
    assertEquals(2, facts.files());
    assertTrue(facts.cacheUpToDate());
  }
  @Test void recognisesThePackagesOfTheProject(@TempDir Path dir){
    var project= project(dir,"someProject");
    Fs.writeUtf8(project.resolve("_other").resolve("_rank_app.fear"),"");
    var facts= FolderFacts.of(project,Kind.code);
    assertEquals(List.of("hello","other"), facts.pkgs().stream().sorted().toList());
    assertTrue(facts.valid());
    assertTrue(facts.problem().isEmpty());
  }
  @Test void brokenNamesAreReportedNotHidden(@TempDir Path dir){
    var project= project(dir,"someProject");
    Fs.writeUtf8(project.resolve("_hello").resolve("Bad.fear"),"");
    var facts= FolderFacts.of(project,Kind.code);
    assertFalse(facts.valid());
    assertEquals(List.of(), facts.pkgs());
    assertTrue(facts.problem().orElseThrow().contains("Bad.fear"), facts.problem().orElseThrow());
  }
  @Test void withNoCacheThereIsSomethingToCompileAndNothingToRun(@TempDir Path dir){
    var facts= FolderFacts.of(project(dir,"someProject"),Kind.code);
    assertFalse(facts.hasCache());
    assertFalse(facts.cacheUpToDate());
  }
  @Test void aCacheNewerThanEverySourceIsUpToDate(@TempDir Path dir){
    var project= project(dir,"someProject");
    cache(project,"hello",after(project));
    var facts= FolderFacts.of(project,Kind.code);
    assertTrue(facts.hasCache());
    assertTrue(facts.cacheUpToDate());
  }
  @Test void aCacheOlderThanOneSourceIsStale(@TempDir Path dir){
    var project= project(dir,"someProject");
    cache(project,"hello",after(project));
    cache(project,"other",1000);
    var facts= FolderFacts.of(project,Kind.code);
    assertTrue(facts.hasCache());
    assertFalse(facts.cacheUpToDate());
  }
  @Test void aDataFolderNeedsNoPackageStructure(@TempDir Path dir){
    var project= dir.resolve("publicFiles");
    Fs.writeUtf8(project.resolve("readme.txt"),"hi\n");
    var facts= FolderFacts.of(project,Kind.dataReadOnly);
    assertTrue(facts.valid());
    assertEquals(List.of(), facts.pkgs());
  }
  @Test void aDataFolderStillRejectsUnsafeNames(@TempDir Path dir){
    var project= dir.resolve("publicFiles");
    Fs.writeUtf8(project.resolve("Bad Name.txt"),"hi\n");
    var facts= FolderFacts.of(project,Kind.dataReadOnly);
    assertFalse(facts.valid());
  }
}
