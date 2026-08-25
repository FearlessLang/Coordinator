package manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

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
  @Test void theRegistryFileQuotesTheLauncherItselfAndTheFileHandedToIt(){
    var lines= FileAssociation.regFile(winLauncher,"0_001").lines().toList();
    assertEquals("Windows Registry Editor Version 5.00", lines.getFirst());
    assertTrue(lines.contains("[HKEY_CURRENT_USER\\Software\\Classes\\Fearless.Project.0_001\\shell\\open\\command]"), lines.toString());
    assertTrue(lines.contains("@=\"\\\"C:\\\\fearlessBin0_001\\\\fearlessBin0_001w.exe\\\" \\\"%1\\\"\""), lines.toString());
  }
  @Test void theRegistryFilePointsTheExtensionAtThisVersion(){
    var lines= FileAssociation.regFile(winLauncher,"0_001").lines().toList();
    assertTrue(lines.contains("[HKEY_CURRENT_USER\\Software\\Classes\\.fearless]"), lines.toString());
    assertTrue(lines.contains("@=\"Fearless.Project.0_001\""), lines.toString());
    assertTrue(lines.contains("@=\"C:\\\\fearlessBin0_001\\\\fearlessBin0_001w.exe,0\""), lines.toString());
    assertEquals(8, lines.stream().filter(l->l.startsWith("[HKEY_CURRENT_USER")).count(), lines.toString());
  }
  @Test void theChoiceWindowsRemembersIsLookedForWhereWindowsKeepsIt(){
    assertEquals("HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts\\.fearless\\UserChoice",
      FileAssociation.userChoice);
  }
  @Test void theRegistryFileOffersFearlessByNameInTheOpenWithList(){
    var lines= FileAssociation.regFile(winLauncher,"0_001").lines().toList();
    assertTrue(lines.contains("[HKEY_CURRENT_USER\\Software\\Classes\\.fearless\\OpenWithProgids]"), lines.toString());
    assertTrue(lines.contains("\"Fearless.Project.0_001\"=\"\""), lines.toString());
  }
  @Test void theSettingsPageOpenedIsTheOneThisFearlessIsRegisteredUnder(){
    var cmd= FileAssociation.whereToChooseCommand("0_001");
    assertEquals("explorer.exe", cmd.getFirst());
    assertEquals("ms-settings:defaultapps?registeredAppUser=fearless0_001", cmd.getLast());
    assertEquals(2, cmd.size(), cmd.toString());
  }
  @Test void theRegistryFileSaysThisFearlessIsAnAppAndWhatItOpens(){
    var lines= FileAssociation.regFile(winLauncher,"0_001").lines().toList();
    assertTrue(lines.contains("[HKEY_CURRENT_USER\\Software\\fearless0_001\\Capabilities]"), lines.toString());
    assertTrue(lines.contains("\"ApplicationName\"=\"Fearless 0_001\""), lines.toString());
    assertTrue(lines.contains("[HKEY_CURRENT_USER\\Software\\fearless0_001\\Capabilities\\FileAssociations]"), lines.toString());
    assertTrue(lines.contains("\".fearless\"=\"Fearless.Project.0_001\""), lines.toString());
  }
  @Test void theRegistryFileListsThisFearlessAmongTheRegisteredApplications(){
    var lines= FileAssociation.regFile(winLauncher,"0_001").lines().toList();
    assertTrue(lines.contains("[HKEY_CURRENT_USER\\Software\\RegisteredApplications]"), lines.toString());
    assertTrue(lines.contains("\"fearless0_001\"=\"Software\\\\fearless0_001\\\\Capabilities\""), lines.toString());
  }
  @Test void theRegistryFileUsesTheLineEndingsWindowsWrites(){
    var reg= FileAssociation.regFile(winLauncher,"0_001");
    assertEquals(reg.split("\r\n",-1).length, reg.split("\n",-1).length);
  }
}
