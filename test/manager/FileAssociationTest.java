package manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.LinuxFileAssociations;
import tools.WindowsFileAssociations;

final class FileAssociationTest{
  private static final Path linuxLauncher= Path.of("/home/me/fearlessManaged0_001/bin/fearlessManaged0_001");
  private static final Path winLauncher= Path.of("C:\\fearlessManaged0_001\\fearlessManaged0_001.exe");
  private static final Path proof= Path.of("C:\\fearlessManaged0_001\\proof.ico");
  private static final String name= "fearlessManaged0_001";
  private static Map<String,String> owned(){
    var res= new LinkedHashMap<String,String>();
    res.put(".fearless", name+"-aaa");
    res.put(".fproof", name+"-bbb");
    return res;
  }
  @Test void theLauncherNamesTheProgramAndTheManagerIsNotThePortable(){
    assertEquals(name, FileAssociation.name(linuxLauncher));
    assertEquals(name, FileAssociation.name(Path.of("fearlessManaged0_001.exe")));
    assertNotEquals(FileAssociation.name(linuxLauncher),
      FileAssociation.name(Path.of("/home/me/fearlessBin0_001/bin/fearlessBin0_001")));
  }
  @Test void everyExtensionGetsAKindOfItsOwn(){
    assertEquals("application/x-fearless", LinuxFileAssociations.typeOf(".fearless"));
    assertEquals("application/x-fproof", LinuxFileAssociations.typeOf(".fproof"));
  }
  @Test void theOneDesktopEntryHandsEveryKindToTheRunningLauncher(){
    var types= List.of("application/x-fearless","application/x-fproof");
    var entry= LinuxFileAssociations.desktopEntry(name, linuxLauncher.toString(), types);
    assertTrue(entry.startsWith("[Desktop Entry]\n"), entry);
    assertTrue(entry.contains("Exec=/home/me/fearlessManaged0_001/bin/fearlessManaged0_001 %f"), entry);
    assertTrue(entry.contains("MimeType=application/x-fearless;application/x-fproof;"), entry);
    assertTrue(entry.contains("Icon=fearlessManaged0_001"), entry);
  }
  @Test void everyKindIsDeclaredOutrightAtTheWeightThatSettlesTheFileName(){
    var mine= LinuxFileAssociations.mimePackage(name, owned());
    assertTrue(mine.contains("<mime-type type=\"application/x-fearless\">"), mine);
    assertTrue(mine.contains("<glob pattern=\"*.fearless\" weight=\"100\"/>"), mine);
    assertTrue(mine.contains("<glob pattern=\"*.fproof\" weight=\"100\"/>"), mine);
  }
  @Test void eachKindNamesItsOwnPictureSoOneBatchCanCarryMany(){
    var mine= LinuxFileAssociations.mimePackage(name, owned());
    assertTrue(mine.contains("<icon name=\"fearlessManaged0_001-aaa\"/>"), mine);
    assertTrue(mine.contains("<icon name=\"fearlessManaged0_001-bbb\"/>"), mine);
  }
  @Test void whatWeDeclaredIsWhatWeReadBackOnTheNextRun(){
    var mine= LinuxFileAssociations.mimePackage(name, owned());
    assertEquals(owned(), LinuxFileAssociations.readOwned(mine.lines().toList()));
  }
  @Test void aThousandExtensionsShareOnePictureAndOneDeclarationFile(){
    var many= new LinkedHashMap<String,String>();
    for (var i= 0; i < 1000; i++){ many.put(".fear"+i, name+"-shared"); }
    var mine= LinuxFileAssociations.mimePackage(name, many);
    assertEquals(1000, mine.lines().filter(l->l.contains("<mime-type")).count());
    assertEquals(1000, mine.lines().filter(l->l.contains("name=\"fearlessManaged0_001-shared\"")).count());
    assertEquals(many, LinuxFileAssociations.readOwned(mine.lines().toList()));
  }
  @Test void thePictureIsFiledUnderTheSizeItsOwnHeaderDeclares(){
    assertEquals(48, LinuxFileAssociations.side(png(48)));
    assertEquals(256, LinuxFileAssociations.side(png(256)));
  }
  @Test void thePictureNameFollowsWhatIsInsideItSoTheSameOneIsFiledOnce(){
    assertEquals(LinuxFileAssociations.hash(png(48)), LinuxFileAssociations.hash(png(48)));
    assertNotEquals(LinuxFileAssociations.hash(png(48)), LinuxFileAssociations.hash(png(32)));
  }
  @Test void theRegistryFileGivesEveryExtensionATypeOfItsOwn(){
    var lines= regFile();
    assertTrue(lines.contains("[HKEY_CURRENT_USER\\Software\\Classes\\fearlessManaged0_001.fearless]"), lines.toString());
    assertTrue(lines.contains("[HKEY_CURRENT_USER\\Software\\Classes\\fearlessManaged0_001.fproof]"), lines.toString());
    assertEquals("fearlessManaged0_001.fproof", WindowsFileAssociations.progId(name, ".fproof"));
  }
  @Test void eachTypeCarriesItsOwnPictureAndTheOneSharedCommand(){
    var lines= regFile();
    assertTrue(lines.contains("[HKEY_CURRENT_USER\\Software\\Classes\\fearlessManaged0_001.fproof\\DefaultIcon]"), lines.toString());
    assertTrue(lines.contains("@=\"C:\\\\fearlessManaged0_001\\\\proof.ico,0\""), lines.toString());
    assertEquals(2, lines.stream().filter(l->l.endsWith("\\shell\\open\\command]")).count(), lines.toString());
    assertEquals(2, lines.stream().filter(l->l.equals("@=\"\\\"C:\\\\fearlessManaged0_001\\\\fearlessManaged0_001.exe\\\" \\\"%1\\\"\"")).count(), lines.toString());
  }
  @Test void theRegistryFilePointsEachExtensionAtItsOwnType(){
    var lines= regFile();
    assertTrue(lines.contains("[HKEY_CURRENT_USER\\Software\\Classes\\.fearless]"), lines.toString());
    assertTrue(lines.contains("@=\"fearlessManaged0_001.fearless\""), lines.toString());
    assertTrue(lines.contains("[HKEY_CURRENT_USER\\Software\\Classes\\.fproof\\OpenWithProgids]"), lines.toString());
    assertTrue(lines.contains("\"fearlessManaged0_001.fproof\"=\"\""), lines.toString());
  }
  @Test void theRegistryFileSaysThisFearlessIsAnAppAndWhatItOpens(){
    var lines= regFile();
    assertTrue(lines.contains("[HKEY_CURRENT_USER\\Software\\fearlessManaged0_001\\Capabilities]"), lines.toString());
    assertTrue(lines.contains("\"ApplicationName\"=\"fearlessManaged0_001\""), lines.toString());
    assertTrue(lines.contains("[HKEY_CURRENT_USER\\Software\\fearlessManaged0_001\\Capabilities\\FileAssociations]"), lines.toString());
    assertTrue(lines.contains("\".fproof\"=\"fearlessManaged0_001.fproof\""), lines.toString());
  }
  @Test void theRegistryFileListsThisFearlessAmongTheRegisteredApplications(){
    var lines= regFile();
    assertTrue(lines.contains("[HKEY_CURRENT_USER\\Software\\RegisteredApplications]"), lines.toString());
    assertTrue(lines.contains("\"fearlessManaged0_001\"=\"Software\\\\fearlessManaged0_001\\\\Capabilities\""), lines.toString());
  }
  @Test void theExtensionsWeHoldAreReadFromTheOneKeyThatLfilesThem(){
    assertEquals("HKEY_CURRENT_USER\\Software\\fearlessManaged0_001\\Capabilities\\FileAssociations",
      WindowsFileAssociations.fileAssociations(name));
    assertEquals(".fearless", WindowsFileAssociations.regName("    .fearless    REG_SZ    fearlessManaged0_001.fearless"));
  }
  @Test void theChoiceWindowsRemembersIsLookedForWhereWindowsKeepsIt(){
    assertEquals("HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts\\.fearless\\UserChoice",
      WindowsFileAssociations.userChoiceKey(".fearless"));
  }
  @Test void theSettingsPageOpenedIsTheOneThisFearlessIsRegisteredUnder(){
    var cmd= WindowsFileAssociations.whereToChoose(name);
    assertEquals("explorer.exe", cmd.getFirst());
    assertEquals("ms-settings:defaultapps?registeredAppUser=fearlessManaged0_001", cmd.getLast());
    assertEquals(2, cmd.size(), cmd.toString());
  }
  @Test void theRegistryFileUsesTheLineEndingsWindowsWrites(){
    var reg= WindowsFileAssociations.regFile(name, winLauncher.toString(), winIcons());
    assertEquals(reg.split("\r\n",-1).length, reg.split("\n",-1).length);
  }
  private static Map<String,Path> winIcons(){
    var res= new LinkedHashMap<String,Path>();
    res.put(".fearless", winLauncher);
    res.put(".fproof", proof);
    return res;
  }
  private static List<String> regFile(){
    return WindowsFileAssociations.regFile(name, winLauncher.toString(), winIcons()).lines().toList();
  }
  private static byte[] png(int side){
    var res= new byte[24];
    write(res, 16, side);
    write(res, 20, side);
    return res;
  }
  private static void write(byte[] res, int at, int v){
    res[at]= (byte)(v>>>24); res[at+1]= (byte)(v>>>16); res[at+2]= (byte)(v>>>8); res[at+3]= (byte)v;
  }
}
