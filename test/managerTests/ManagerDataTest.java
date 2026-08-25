package managerTests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import managerData.DiskData;
import managerData.ManagerData;
import tools.Fs;
import userMessages.UserError;

final class ManagerDataTest{
  private static DiskData data(Path dir){ return new DiskData(dir); }
  private static Path folder(Path dir, String name){
    var res= dir.resolve(name);
    Fs.ensureDir(res);
    return res;
  }
  @Test void noFileYetIsNoRegisteredFolder(@TempDir Path dir){
    assertEquals(List.of(), data(dir).registered());
  }
  @Test void addedFolderIsStoredAbsoluteWithNoTimes(@TempDir Path dir){
    var project= folder(dir,"someProject");
    data(dir).addRegisteredFolder(project);
    assertEquals(List.of(new ManagerData.Entry(project.toAbsolutePath().normalize(),-1,-1)), data(dir).registered());
  }
  @Test void addingTheSameFolderTwiceRegistersItOnce(@TempDir Path dir){
    var project= folder(dir,"someProject");
    data(dir).addRegisteredFolder(project);
    data(dir).addRegisteredFolder(project.resolve("..").resolve("someProject"));
    assertEquals(1, data(dir).registered().size());
  }
  @Test void compileAndRunTimesSurviveAReRead(@TempDir Path dir){
    var project= folder(dir,"someProject");
    var d= data(dir);
    d.addRegisteredFolder(project);
    d.setCompiled(project,111);
    d.setRun(project,222);
    var reread= data(dir).registered().getFirst();
    assertEquals(111, reread.compiled());
    assertEquals(222, reread.run());
  }
  @Test void forgettingAFolderRemovesOnlyThatOne(@TempDir Path dir){
    var kept= folder(dir,"kept");
    var gone= folder(dir,"gone");
    var d= data(dir);
    d.addRegisteredFolder(kept);
    d.addRegisteredFolder(gone);
    d.removeRegisteredFolder(gone);
    assertEquals(List.of(kept.toAbsolutePath().normalize()), d.registered().stream().map(ManagerData.Entry::folder).toList());
    assertTrue(d.isRegistered(kept));
    assertFalse(d.isRegistered(gone));
  }
  @Test void awkwardFolderNamesSurviveAReRead(@TempDir Path dir){
    var project= folder(dir,"a name with spaces");
    var d= data(dir);
    d.addRegisteredFolder(project);
    assertEquals(project.toAbsolutePath().normalize(), data(dir).registered().getFirst().folder());
    assertEquals(1, Fs.readUtf8(dir.resolve("registered.txt")).lines().count());
  }
  @Test void aChangedFileIsReportedNotGuessed(@TempDir Path dir){
    Fs.writeUtf8(dir.resolve("registered.txt"), "this is not a registered folder\n");
    var e= assertThrows(UserError.class, ()->data(dir).registered());
    assertTrue(e.getMessage().contains("cannot read back what it remembers"), e.getMessage());
  }
}
