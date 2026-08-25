package managerTests;

import static managerTests.FolderIconTest.folder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import managerIcons.FolderName;
import tools.Fs;

final class FolderNameTest{
  @Test void aNameIsOneFearlessAcceptsForItsOwnFile(@TempDir Path dir){
    var project= folder(dir,"someProject");
    assertTrue(FolderName.isName(project,"my_game"));
    assertTrue(FolderName.isName(project,"_x9"));
    assertFalse(FolderName.isName(project,"myGame"));
    assertFalse(FolderName.isName(project,"2fast"));
    assertFalse(FolderName.isName(project,"a__b"));
    assertFalse(FolderName.isName(project,"con"));
    assertFalse(FolderName.isName(project,""));
    assertFalse(FolderName.isName(project,"a/b"));
    assertFalse(FolderName.isName(project,"a.b"));
  }
  @Test void aFolderNameIsTurnedIntoANameFearlessAccepts(@TempDir Path dir){
    var project= folder(dir,"someProject");
    assertEquals("helloworld", FolderName.free(project,"helloWorld",Set.of()));
    assertEquals("map_a_to_pkc", FolderName.free(project,"map_a_to_pkc",Set.of()));
    assertEquals("a_b", FolderName.free(project,"a - b",Set.of()));
    assertEquals("p2fast", FolderName.free(project,"2fast",Set.of()));
    assertEquals("pcon", FolderName.free(project,"con",Set.of()));
  }
  @Test void aTakenNameGetsANumber(@TempDir Path dir){
    var project= folder(dir,"someProject");
    assertEquals("hello2", FolderName.free(project,"hello",Set.of("hello")));
    assertEquals("hello3", FolderName.free(project,"hello",Set.of("hello","hello2")));
  }
  @Test void aFreeNameIsNeverAskedAbout(@TempDir Path dir){
    var project= folder(dir,"someProject");
    FolderName.makeUnique(project,Set.of("other"),_->{ throw new AssertionError("must not ask"); });
    assertEquals("someProject", FolderName.compactName(project));
    assertFalse(Files.exists(project.resolve("someProject.fearless")));
  }
  @Test void aTakenNameIsAskedAboutAndWrittenIntoANewFearlessFile(@TempDir Path dir){
    var project= folder(dir,"someProject");
    FolderName.makeUnique(project,Set.of("someProject"),_->"my_game");
    assertEquals("my_game", FolderName.compactName(project));
    assertTrue(Files.isRegularFile(project.resolve("my_game.fearless")));
  }
  @Test void theSuggestedNameIsTheFolderNameMadeAcceptableAndFree(@TempDir Path dir){
    var project= folder(dir,"helloWorld");
    Fs.writeUtf8(project.resolve("start.fearless"),"");
    var suggested= new String[1];
    FolderName.makeUnique(project,Set.of("start","helloworld"),s->{ suggested[0]= s; return s; });
    assertEquals("helloworld2", suggested[0]);
    assertEquals("helloworld2", FolderName.compactName(project));
  }
  @Test void anExistingFearlessFileIsRenamedNotDuplicated(@TempDir Path dir){
    var project= folder(dir,"someProject");
    Fs.writeUtf8(project.resolve("start.fearless"),"kept\n");
    FolderName.makeUnique(project,Set.of("start"),_->"my_game");
    assertFalse(Files.exists(project.resolve("start.fearless")));
    assertEquals("kept\n", Fs.readUtf8(project.resolve("my_game.fearless")));
  }
  @Test void aNamedFolderStillScansAsAValidFearlessProject(@TempDir Path dir){
    var project= FolderFactsTest.project(dir,"helloWorld");
    FolderName.makeUnique(project,Set.of("helloWorld"),s->s);
    assertEquals("helloworld", FolderName.compactName(project));
    assertTrue(managerInfo.FolderFacts.of(project).valid());
  }
}
