package manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import fileAssociations.Icon;
import fileAssociations.LinuxAssociations;
import fileAssociations.WindowsAssociations;

final class FileAssociationTest{
  private static final Path linuxLauncher= Path.of("/home/me/fearlessManaged0_001/bin/fearlessManaged0_001");
  private static final Path winLauncher= Path.of("C:\\fearlessManaged0_001\\fearlessManaged0_001.exe");
  private static final Path proof= Path.of("C:\\fearlessManaged0_001\\proof.ico");
  private static final Path programIco= Path.of("C:\\fearlessManaged0_001\\app.ico");
  private static final String identity= "fearlessManaged0_001";
  private static Map<String,String> owned(){
    var res= new LinkedHashMap<String,String>();
    res.put(".fearless", identity+"-aaa");
    res.put(".fproof", identity+"-bbb");
    return res;
  }
  @Test void theLauncherNamesTheProgramAndTheManagerIsNotThePortable(){
    assertEquals(identity, FileAssociation.identity(linuxLauncher));
    assertEquals(identity, FileAssociation.identity(Path.of("fearlessManaged0_001.exe")));
    assertNotEquals(FileAssociation.identity(linuxLauncher),
      FileAssociation.identity(Path.of("/home/me/fearlessBin0_001/bin/fearlessBin0_001")));
  }
  @Test void everyLauncherOfTheFamilyNamesItInItsOwnFile(){
    assertTrue(FileAssociation.belongsToFamily.test("fearlessManaged0_001"));
    assertTrue(FileAssociation.belongsToFamily.test("FearlessApp_001"));
    assertFalse(FileAssociation.belongsToFamily.test("SomeOtherEditor"));
  }
  @Test void everyExtensionGetsAKindOfItsOwn(){
    assertEquals("application/x-fearless", LinuxAssociations.typeOf(".fearless"));
    assertEquals("application/x-fproof", LinuxAssociations.typeOf(".fproof"));
  }
  @Test void theOneDesktopEntryHandsEveryKindToTheRunningLauncher(){
    var types= List.of("application/x-fearless","application/x-fproof");
    var entry= LinuxAssociations.desktopEntry(identity, linuxLauncher.toString(), types);
    assertTrue(entry.startsWith("[Desktop Entry]\n"), entry);
    assertTrue(entry.contains("Exec=/home/me/fearlessManaged0_001/bin/fearlessManaged0_001 %f"), entry);
    assertTrue(entry.contains("MimeType=application/x-fearless;application/x-fproof;"), entry);
    assertTrue(entry.contains("Icon=fearlessManaged0_001"), entry);
  }
  @Test void everyKindIsDeclaredOutrightAtTheWeightThatSettlesTheFileName(){
    var mine= LinuxAssociations.mimePackage(identity, owned());
    assertTrue(mine.contains("<mime-type type=\"application/x-fearless\">"), mine);
    assertTrue(mine.contains("<glob pattern=\"*.fearless\" weight=\"100\"/>"), mine);
    assertTrue(mine.contains("<glob pattern=\"*.fproof\" weight=\"100\"/>"), mine);
  }
  @Test void eachKindNamesItsOwnPictureSoOneBatchCanCarryMany(){
    var mine= LinuxAssociations.mimePackage(identity, owned());
    assertTrue(mine.contains("<icon name=\"fearlessManaged0_001-aaa\"/>"), mine);
    assertTrue(mine.contains("<icon name=\"fearlessManaged0_001-bbb\"/>"), mine);
  }
  @Test void whatWeDeclaredIsWhatWeReadBackOnTheNextRun(){
    var mine= LinuxAssociations.mimePackage(identity, owned());
    assertEquals(owned(), LinuxAssociations.readOwned(mine.lines().toList()));
  }
  @Test void aThousandExtensionsShareOnePictureAndOneDeclarationFile(){
    var many= new LinkedHashMap<String,String>();
    for (var i= 0; i < 1000; i++){ many.put(".fear"+i, identity+"-shared"); }
    var mine= LinuxAssociations.mimePackage(identity, many);
    assertEquals(1000, mine.lines().filter(l->l.contains("<mime-type")).count());
    assertEquals(1000, mine.lines().filter(l->l.contains("name=\"fearlessManaged0_001-shared\"")).count());
    assertEquals(many, LinuxAssociations.readOwned(mine.lines().toList()));
  }
  @Test void thePictureIsFiledUnderTheSizeItsOwnHeaderDeclares(){
    assertEquals(48, LinuxAssociations.side(png(48)));
    assertEquals(256, LinuxAssociations.side(png(256)));
  }
  @Test void thePictureNameFollowsWhatIsInsideItSoTheSameOneIsFiledOnce(){
    assertEquals(LinuxAssociations.hash(png(48)), LinuxAssociations.hash(png(48)));
    assertNotEquals(LinuxAssociations.hash(png(48)), LinuxAssociations.hash(png(32)));
  }
  @Test void theRegistryFileGivesEveryExtensionATypeOfItsOwn(){
    var lines= regFile();
    assertTrue(lines.contains("[HKEY_CURRENT_USER\\Software\\Classes\\fearlessManaged0_001.fearless]"), lines.toString());
    assertTrue(lines.contains("[HKEY_CURRENT_USER\\Software\\Classes\\fearlessManaged0_001.fproof]"), lines.toString());
    assertEquals("fearlessManaged0_001.fproof", WindowsAssociations.progId(identity, ".fproof"));
  }
  @Test void eachTypeCarriesItsOwnPictureAndTheOneSharedCommand(){
    var lines= regFile();
    assertTrue(lines.contains("[HKEY_CURRENT_USER\\Software\\Classes\\fearlessManaged0_001.fproof\\DefaultIcon]"), lines.toString());
    assertTrue(lines.contains("@=\"C:\\\\fearlessManaged0_001\\\\proof.ico,0\""), lines.toString());
    assertEquals(2, lines.stream().filter(l->l.endsWith("\\shell\\open\\command]")).count(), lines.toString());
    assertEquals(2, lines.stream().filter(l->l.equals("@=\"\\\"C:\\\\fearlessManaged0_001\\\\fearlessManaged0_001.exe\\\" \\\"%1\\\"\"")).count(), lines.toString());
  }
  @Test void theRegistryFileCarriesTheApplicationsOwnPictureToo(){
    var lines= regFile();
    assertTrue(lines.contains("[HKEY_CURRENT_USER\\Software\\fearlessManaged0_001\\Capabilities]"), lines.toString());
    assertTrue(lines.contains("\"ApplicationIcon\"=\"C:\\\\fearlessManaged0_001\\\\app.ico,0\""), lines.toString());
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
  @Test void theExtensionsWeHoldAreReadFromTheOneKeyThatFilesThem(){
    assertEquals("HKEY_CURRENT_USER\\Software\\fearlessManaged0_001\\Capabilities\\FileAssociations",
      WindowsAssociations.fileAssociations(identity));
    assertEquals(".fearless", WindowsAssociations.regName("    .fearless    REG_SZ    fearlessManaged0_001.fearless"));
  }
  @Test void theChoiceWindowsRemembersIsLookedForWhereWindowsKeepsIt(){
    assertEquals("HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts\\.fearless\\UserChoice",
      WindowsAssociations.userChoiceKey(".fearless"));
  }
  @Test void theRegistryFileUsesTheLineEndingsWindowsWrites(){
    var reg= WindowsAssociations.regFile(identity, winLauncher, winIcons(), programIco);
    assertEquals(reg.split("\r\n",-1).length, reg.split("\n",-1).length);
  }
  private static List<Icon> winIcons(){
    return List.of(new Icon(".fearless", winLauncher, winLauncher), new Icon(".fproof", proof, proof));
  }
  private static List<String> regFile(){
    return WindowsAssociations.regFile(identity, winLauncher, winIcons(), programIco).lines().toList();
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
