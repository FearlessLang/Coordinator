package manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class FileAssociationTest{
  private static final Path binDir= Path.of("/home/me/fearlessBin0_001");
  @Test void theDesktopFileIsNamedAfterThisVersionOfFearless(){
    assertEquals("fearless0_001.desktop", FileAssociation.desktopName("0_001"));
    assertEquals("fearless0_002.desktop", FileAssociation.desktopName("0_002"));
  }
  @Test void theWindowedLauncherOfThisVersionIsTheOneOffered(){
    assertEquals(binDir.resolve("bin").resolve("fearlessBin0_001w"), FileAssociation.launcher(binDir,"0_001"));
  }
  @Test void theDesktopEntryHandsTheFileToThatLauncher(){
    var entry= FileAssociation.desktopEntry(FileAssociation.launcher(binDir,"0_001"), binDir.resolve("icon.png"));
    assertTrue(entry.contains("Exec=/home/me/fearlessBin0_001/bin/fearlessBin0_001w %f"), entry);
    assertTrue(entry.contains("MimeType=application/x-fearless;"), entry);
    assertTrue(entry.contains("Icon=/home/me/fearlessBin0_001/icon.png"), entry);
    assertTrue(entry.startsWith("[Desktop Entry]\n"), entry);
  }
  @Test void versionsDoNotFightOverTheSameDesktopFile(){
    assertTrue(!FileAssociation.desktopName("0_001").equals(FileAssociation.desktopName("0_002")));
  }
}
