package managerTests;

import static managerTests.FolderIconTest.folder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
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
  @Test void aFreeNameIsNeverAskedAboutButStillGetsAMarkerFile(@TempDir Path dir){
    var project= folder(dir,"someproject");
    FolderName.makeUnique(project,Set.of("other"),_->{ throw new AssertionError("must not ask"); });
    assertEquals("someproject", FolderName.compactName(project));
    assertTrue(Files.isRegularFile(project.resolve("someproject.fearless")));
  }
  @Test void makeUniqueIsIdempotentOnAnAlreadyMarkedFolder(@TempDir Path dir){
    var project= folder(dir,"someproject");
    FolderName.makeUnique(project,Set.of("other"),_->{ throw new AssertionError("must not ask"); });
    FolderName.makeUnique(project,Set.of("other"),_->{ throw new AssertionError("must not ask"); });
    assertEquals("someproject", FolderName.compactName(project));
  }
  @Test void anUnsafeFolderNameIsAskedAboutEvenWithNoCollision(@TempDir Path dir){
    var project= folder(dir,"someProject");
    var name= FolderName.makeUnique(project,Set.of("other"),s->s);
    assertEquals("someproject", name);
    assertTrue(Files.isRegularFile(project.resolve("someproject.fearless")));
  }
  @Test void aTakenNameIsAskedAboutAndWrittenIntoANewFearlessFile(@TempDir Path dir){
    var project= folder(dir,"someProject");
    FolderName.makeUnique(project,Set.of("someProject"),_->"my_game");
    assertEquals("my_game", FolderName.compactName(project));
    assertTrue(Files.isRegularFile(project.resolve("my_game.fearless")));
    assertFalse(Fs.readUtf8(project.resolve("my_game.fearless")).isEmpty());
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
    assertTrue(managerInfo.FolderFacts.of(project,managerData.Kind.code).valid());
  }
  @Test void markerProblemIsEmptyWhenTheMarkerMatchesTheAlias(@TempDir Path dir){
    var project= folder(dir,"someProject");
    Fs.writeUtf8(project.resolve("my_game.fearless"),"");
    assertEquals(Optional.empty(), FolderName.markerProblem(project,"my_game"));
  }
  @Test void markerProblemReportsAMissingMarker(@TempDir Path dir){
    var project= folder(dir,"someProject");
    var problem= FolderName.markerProblem(project,"my_game");
    assertTrue(problem.orElseThrow().contains("my_game.fearless"), problem.orElseThrow());
  }
  @Test void defaultTypeNameUppercasesTheFirstLetter(){
    assertEquals("Mydata", FolderName.defaultTypeName("mydata"));
    assertEquals("My_data_2", FolderName.defaultTypeName("my_data_2"));
    assertEquals("_Foo", FolderName.defaultTypeName("_foo"));
    assertTrue(core.TName.isTypeName(FolderName.defaultTypeName("mydata")));
    assertTrue(core.TName.isTypeName(FolderName.defaultTypeName("my_data_2")));
    assertTrue(core.TName.isTypeName(FolderName.defaultTypeName("_foo")));
  }
  @Test void markerProblemReportsMoreThanOneMarker(@TempDir Path dir){
    var project= folder(dir,"someProject");
    Fs.writeUtf8(project.resolve("my_game.fearless"),"");
    Fs.writeUtf8(project.resolve("other.fearless"),"");
    var problem= FolderName.markerProblem(project,"my_game");
    assertTrue(problem.orElseThrow().contains("More than one"), problem.orElseThrow());
  }
}
