package manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.Fs;

final class ManagerTest{
  private static Path folder(Path dir, String name){
    var res= dir.resolve(name);
    Fs.ensureDir(res);
    return res;
  }
  @Test void aStartedFolderIsTheProjectItself(@TempDir Path dir){
    var project= folder(dir,"myProject");
    assertEquals(project, Manager.projectFolder(project.toString(), folder(dir,"manager")).orElseThrow());
  }
  @Test void aStartedFileIsTheFolderAround(@TempDir Path dir){
    var project= folder(dir,"myProject");
    var file= project.resolve("hello.fearless");
    Fs.writeUtf8(file,"anything");
    assertEquals(project, Manager.projectFolder(file.toString(), folder(dir,"manager")).orElseThrow());
  }
  @Test void theManagerFolderIsNotAProject(@TempDir Path dir){
    var managerDir= folder(dir,"manager");
    assertTrue(Manager.projectFolder(managerDir.toString(), managerDir).isEmpty());
  }
  @Test void theFileTheDesktopWindowOpensOnIsNotAProjectEither(@TempDir Path dir){
    var managerDir= folder(dir,"manager");
    var file= FileAssociation.pickFile(managerDir);
    assertTrue(Manager.projectFolder(file.toString(), managerDir).isEmpty());
  }
  @Test void thatFileIsMadeOnceAndKeptAsItIs(@TempDir Path dir){
    var managerDir= folder(dir,"manager");
    var file= FileAssociation.pickFile(managerDir);
    assertEquals(managerDir.resolve("example.fearless"), file);
    assertEquals(FileAssociation.pickFileContent, Fs.readUtf8(file));
    Fs.writeUtf8(file,"changed by someone");
    assertEquals("changed by someone", Fs.readUtf8(FileAssociation.pickFile(managerDir)));
  }
}
