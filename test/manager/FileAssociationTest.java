package manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

final class FileAssociationTest{
  private static final Path linuxLauncher= Path.of("/home/me/fearlessBin0_001/bin/fearlessBin0_001w");
  private static final Path winLauncher= Path.of("C:\\fearlessBin0_001\\fearlessBin0_001w.exe");
  @Test void eachVersionOfFearlessAnswersUnderItsOwnName(){
    assertEquals("fearless0_001.desktop", FileAssociation.desktopName("0_001"));
    assertEquals("Fearless.Project.0_001", FileAssociation.progId("0_001"));
    assertNotEquals(FileAssociation.desktopName("0_001"), FileAssociation.desktopName("0_002"));
    assertNotEquals(FileAssociation.progId("0_001"), FileAssociation.progId("0_002"));
  }
  @Test void theDesktopEntryHandsTheFileToTheRunningLauncher(){
    var entry= FileAssociation.desktopEntry(linuxLauncher, Path.of("/home/me/fearlessBin0_001/lib/app/icon.png"));
    assertTrue(entry.startsWith("[Desktop Entry]\n"), entry);
    assertTrue(entry.contains("Exec=/home/me/fearlessBin0_001/bin/fearlessBin0_001w %f"), entry);
    assertTrue(entry.contains("MimeType=application/x-fearless;"), entry);
    assertTrue(entry.contains("Icon=/home/me/fearlessBin0_001/lib/app/icon.png"), entry);
  }
  @Test void theRegistryCommandsPointTheExtensionAtTheRunningLauncher(){
    var cmds= FileAssociation.winCommands(winLauncher,"0_001");
    var id= "HKCU\\Software\\Classes\\Fearless.Project.0_001";
    assertEquals(List.of("reg","add",id+"\\shell\\open\\command","/ve","/d",
      "\"C:\\fearlessBin0_001\\fearlessBin0_001w.exe\" \"%1\"","/f"), cmds.get(2));
    assertEquals(List.of("reg","add","HKCU\\Software\\Classes\\.fearless","/ve","/d","Fearless.Project.0_001","/f"), cmds.getLast());
    assertTrue(cmds.stream().allMatch(c->c.getLast().equals("/f")), cmds.toString());
  }
  @Test void theIconOfTheWindowsEntryIsTheLauncherOwnIcon(){
    var cmds= FileAssociation.winCommands(winLauncher,"0_001");
    assertTrue(cmds.get(1).contains("C:\\fearlessBin0_001\\fearlessBin0_001w.exe,0"), cmds.get(1).toString());
  }
}
