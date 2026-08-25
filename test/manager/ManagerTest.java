package manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import managerTests.MockManagerData;
import tools.Fs;

final class ManagerTest{
  private static Path folder(Path dir, String name){
    var res= dir.resolve(name);
    Fs.ensureDir(res);
    return res;
  }
  @Test void theFilePickedOnIsTheOneNamingARegisteredFolder(@TempDir Path dir){
    var data= new MockManagerData();
    var folder= folder(dir,"myProject");
    Fs.writeUtf8(folder.resolve("hello.fearless"),"anything");
    data.addRegisteredFolder(folder);
    assertEquals(folder.resolve("hello.fearless").toAbsolutePath(), Manager.projectFile(data).orElseThrow());
  }
  @Test void aRegisteredFolderWithNoProjectFileOffersNothingToPickOn(@TempDir Path dir){
    var data= new MockManagerData();
    data.addRegisteredFolder(folder(dir,"myProject"));
    assertTrue(Manager.projectFile(data).isEmpty(), Manager.projectFile(data).toString());
  }
  @Test void nothingRegisteredOffersNothingToPickOn(){
    assertTrue(Manager.projectFile(new MockManagerData()).isEmpty());
  }
}
