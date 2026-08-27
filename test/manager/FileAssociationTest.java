package manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.LinuxFileAssociations;
import tools.WindowsFileAssociations;

final class FileAssociationTest{
  private static final Path linuxLauncher= Path.of("/home/me/fearlessManaged0_001/bin/fearlessManaged0_001");
  private static final Path winLauncher= Path.of("C:\\fearlessManaged0_001\\fearlessManaged0_001.exe");
  private static final Path icon= Path.of("/home/me/fearlessManaged0_001/lib/app/icon.png");
  private static final List<String> types= List.of("application/x-fearless");
  private static final String name= "fearlessManaged0_001";
  @Test void theLauncherNamesTheProgramAndTheManagerIsNotThePortable(){
    assertEquals(name, FileAssociation.name(linuxLauncher));
    assertEquals(name, FileAssociation.name(Path.of("fearlessManaged0_001.exe")));
    assertNotEquals(FileAssociation.name(linuxLauncher),
      FileAssociation.name(Path.of("/home/me/fearlessBin0_001/bin/fearlessBin0_001")));
  }
  @Test void anExtensionNobodyClaimsGetsAKindOfItsOwn(){
    assertEquals("application/x-fearless", LinuxFileAssociations.typeOf(".fearless"));
  }
  @Test void theDesktopEntryHandsTheFileToTheRunningLauncher(){
    var entry= LinuxFileAssociations.desktopEntry(name, icon, linuxLauncher.toString(), types);
    assertTrue(entry.startsWith("[Desktop Entry]\n"), entry);
    assertTrue(entry.contains("Exec=/home/me/fearlessManaged0_001/bin/fearlessManaged0_001 %f"), entry);
    assertTrue(entry.contains("MimeType=application/x-fearless;"), entry);
    assertTrue(entry.contains("Icon=/home/me/fearlessManaged0_001/lib/app/icon.png"), entry);
  }
  @Test void onlyTheKindsNobodyDeclaredAreDeclared(){
    var mine= LinuxFileAssociations.mimePackage(name, FileAssociation.extensions, types);
    assertTrue(mine.contains("<mime-type type=\"application/x-fearless\">"), mine);
    assertTrue(mine.contains("<glob pattern=\"*.fearless\"/>"), mine);
    var known= LinuxFileAssociations.mimePackage("x", List.of(".txt"), List.of("text/plain"));
    assertTrue(!known.contains("<mime-type"), known);
  }
  @Test void theRegistryFileQuotesTheLauncherItselfAndTheFileHandedToIt(){
    var lines= regFile();
    assertEquals("Windows Registry Editor Version 5.00", lines.getFirst());
    assertTrue(lines.contains("[HKEY_CURRENT_USER\\Software\\Classes\\fearlessManaged0_001\\shell\\open\\command]"), lines.toString());
    assertTrue(lines.contains("@=\"\\\"C:\\\\fearlessManaged0_001\\\\fearlessManaged0_001.exe\\\" \\\"%1\\\"\""), lines.toString());
  }
  @Test void theRegistryFilePointsTheExtensionAtThisFearless(){
    var lines= regFile();
    assertTrue(lines.contains("[HKEY_CURRENT_USER\\Software\\Classes\\.fearless]"), lines.toString());
    assertTrue(lines.contains("@=\"fearlessManaged0_001\""), lines.toString());
    assertTrue(lines.contains("@=\"C:\\\\fearlessManaged0_001\\\\fearlessManaged0_001.exe,0\""), lines.toString());
    assertEquals(8, lines.stream().filter(l->l.startsWith("[HKEY_CURRENT_USER")).count(), lines.toString());
  }
  @Test void theChoiceWindowsRemembersIsLookedForWhereWindowsKeepsIt(){
    assertEquals("HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts\\.fearless\\UserChoice",
      WindowsFileAssociations.userChoiceKey(".fearless"));
  }
  @Test void theRegistryFileOffersFearlessByNameInTheOpenWithList(){
    var lines= regFile();
    assertTrue(lines.contains("[HKEY_CURRENT_USER\\Software\\Classes\\.fearless\\OpenWithProgids]"), lines.toString());
    assertTrue(lines.contains("\"fearlessManaged0_001\"=\"\""), lines.toString());
  }
  @Test void theSettingsPageOpenedIsTheOneThisFearlessIsRegisteredUnder(){
    var cmd= WindowsFileAssociations.whereToChoose(name);
    assertEquals("explorer.exe", cmd.getFirst());
    assertEquals("ms-settings:defaultapps?registeredAppUser=fearlessManaged0_001", cmd.getLast());
    assertEquals(2, cmd.size(), cmd.toString());
  }
  @Test void theRegistryFileSaysThisFearlessIsAnAppAndWhatItOpens(){
    var lines= regFile();
    assertTrue(lines.contains("[HKEY_CURRENT_USER\\Software\\fearlessManaged0_001\\Capabilities]"), lines.toString());
    assertTrue(lines.contains("\"ApplicationName\"=\"fearlessManaged0_001\""), lines.toString());
    assertTrue(lines.contains("[HKEY_CURRENT_USER\\Software\\fearlessManaged0_001\\Capabilities\\FileAssociations]"), lines.toString());
    assertTrue(lines.contains("\".fearless\"=\"fearlessManaged0_001\""), lines.toString());
  }
  @Test void theRegistryFileListsThisFearlessAmongTheRegisteredApplications(){
    var lines= regFile();
    assertTrue(lines.contains("[HKEY_CURRENT_USER\\Software\\RegisteredApplications]"), lines.toString());
    assertTrue(lines.contains("\"fearlessManaged0_001\"=\"Software\\\\fearlessManaged0_001\\\\Capabilities\""), lines.toString());
  }
  @Test void theRegistryFileUsesTheLineEndingsWindowsWrites(){
    var reg= WindowsFileAssociations.regFile(name, FileAssociation.extensions, winLauncher, winLauncher.toString());
    assertEquals(reg.split("\r\n",-1).length, reg.split("\n",-1).length);
  }
  private static List<String> regFile(){
    return WindowsFileAssociations.regFile(name, FileAssociation.extensions, winLauncher, winLauncher.toString())
      .lines().toList();
  }
}
