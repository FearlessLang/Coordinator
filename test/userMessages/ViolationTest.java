package userMessages;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

final class ViolationTest{
  static{ utils.Err.setUp(AssertionFailedError.class, Assertions::assertEquals, Assertions::assertTrue); }

  @Test void mustUseLauncher(){
    utils.Err.strCmp("""
Error: Fearless has been started without using its launcher.
  Do not start fearless directly from the Jars.
""", Violation.mustUseLauncher().getMessage());
  }
  @Test void desktopUnsupported(){
    utils.Err.strCmp("""
Error: Program fearlessw was started in headless mode.
  fearlessw requires GUI capabilities.
  Use fearless (without 'w') to work with a console instead.
""", Violation.desktopUnsupported().getMessage());
  }
  @Test void badLaunchArgWithConsole(){
    utils.Err.strCmp("""
Error: The OS provided a broken path for the input file.
  Value: not a real path
""", Violation.badLaunchArg("not a real path", true).getMessage());
  }
  @Test void badLaunchArgWithoutConsole(){
    utils.Err.strCmp("""
Error: fearless received a broken path for the input file.
  Value: not a real path
""", Violation.badLaunchArg("not a real path", false).getMessage());
  }
  @Test void unsupportedOperatingSystem(){
    var osName= System.getProperty("os.name","<not reported>");
    utils.Err.strCmp("""
Fearless does not know this operating system.

Fearless runs on Windows, macOS and Linux.

The operating system reported itself as:
  %s""".formatted(osName), Violation.unsupportedOperatingSystem().getMessage());
  }
  @Test void couldNotForceEnglish(){
    var cause= new SecurityException("access denied");
    utils.Err.strCmp("""
Fearless could not set its own language.

Fearless asks the operating system to report failures in English, so that
it can recognise them and explain them to you. This request failed:
  Locale.setDefault refused: security manager denied setDefault permission

Replace this Fearless folder with a fresh copy.
If this keeps happening, report the problem.
""", Violation.couldNotForceEnglish("Locale.setDefault refused: security manager denied setDefault permission", cause).getMessage());
  }
  @Test void couldNotForceEnglishOneArgOverloadMatchesTwoArgWithNullCause(){
    Assertions.assertEquals(
      Violation.couldNotForceEnglish("mock locale failure", null).getMessage(),
      Violation.couldNotForceEnglish("mock locale failure").getMessage());
  }

  @Test void cacheMissingBaseApiFile(){
    var apiJson= Path.of("C:\\Users\\ada\\Downloads\\fearlessManaged0_007\\app\\stdLib\\baseCache\\base.json");
    utils.Err.strCmp("""
Fearless could not load the API of its own standard library.

This copy of Fearless should already have this file:
  C:\\Users\\ada\\Downloads\\fearlessManaged0_007\\app\\stdLib\\baseCache\\base.json

Replace this Fearless folder with a fresh copy.
If this keeps happening, report the problem.
""", Violation.cacheMissingBaseApiFile(apiJson).getMessage());
  }

  @Test void associationsAmbiguous(){
    var registered= "C:\\Program Files\\FearlessManaged0_006\\fearlessManaged0_006w.exe\nC:\\Users\\ada\\Downloads\\fearlessManaged0_007\\fearlessManaged0_007w.exe";
    utils.Err.strCmp("""
Fearless cannot tell which of your Fearless installs should open Fearless
projects: more than one is registered with your system at once.

Fearless stopped before touching anything: your system is exactly as it
was.

What is registered:
C:\\Program Files\\FearlessManaged0_006\\fearlessManaged0_006w.exe
C:\\Users\\ada\\Downloads\\fearlessManaged0_007\\fearlessManaged0_007w.exe""", Violation.associationsAmbiguous(registered).getMessage());
  }
  @Test void associationUserLocked(){
    var registered= "ProgId: OtherApp.fearless, set via UserChoice, hash-protected";
    utils.Err.strCmp("""
Fearless cannot become the program that opens Fearless projects.

Your system remembers a choice you made by hand for this kind of file,
and no program can change or remove that choice, including this one.
Fearless stopped before touching anything: your system is exactly as it
was.

The only way to clear it is Settings, Apps, Default apps, Reset - which
resets every app default on your machine, not only this one.

What is locked:
ProgId: OtherApp.fearless, set via UserChoice, hash-protected""", Violation.associationUserLocked(registered).getMessage());
  }
  @Test void associationNotOurs(){
    var registered= "registered owner: fearlessBin0_003.exe (an older Fearless install)";
    utils.Err.strCmp("""
Fearless cannot become the program that opens Fearless projects.

Another program already answers for this kind of file. Fearless stopped
before touching anything: your system is exactly as it was.

What stood in the way:
registered owner: fearlessBin0_003.exe (an older Fearless install)""", Violation.associationNotOurs(registered).getMessage());
  }
  @Test void associationNotWritable(){
    var registered= "HKEY_CLASSES_ROOT\\.fearless (write denied: not running as the owning user)";
    utils.Err.strCmp("""
Fearless cannot become the program that opens Fearless projects.

What Fearless would need to write, or remove, is not yours to change.
Fearless stopped before touching anything: your system is exactly as it
was.

What stood in the way:
HKEY_CLASSES_ROOT\\.fearless (write denied: not running as the owning user)""", Violation.associationNotWritable(registered).getMessage());
  }
  @Test void associationLeftHalfDone(){
    var rollback= "removed HKCU\\...\\UserChoice, but restoring the previous ProgId failed";
    utils.Err.strCmp("""
Fearless could not put your system back as it was.

A step failed, and undoing what had already been written failed too, so
your system is left part way. Which program opens Fearless projects may
now be wrong, and you can settle it by hand in your system settings.

Where the undoing stopped:
removed HKCU\\...\\UserChoice, but restoring the previous ProgId failed""", Violation.associationLeftHalfDone(rollback).getMessage());
  }
  @Test void cacheMissingPkgApiFile(){
    var apiJson= Path.of("C:\\Users\\ada\\projects\\myproject\\.fearless_out\\core.json");
    utils.Err.strCmp("""
Build cache is missing a generated package API file.
That metadata should be stored in:
  C:\\Users\\ada\\projects\\myproject\\.fearless_out\\core.json
Delete this project's .fearless_out folder, then recompile.
""", Violation.cacheMissingPkgApiFile(apiJson).getMessage());
  }
  @Test void cacheInvalidFile(){
    var mapJson= Path.of("C:\\Users\\ada\\projects\\myproject\\.fearless_out\\_map.json");
    utils.Err.strCmp("""
Build cache contains an invalid cached file.

Fearless tried to read:
  C:\\Users\\ada\\projects\\myproject\\.fearless_out\\_map.json
but the file content is not valid.

Parse error:
  Unexpected end of input at offset 4096
""", Violation.cacheInvalidFile(mapJson, "Unexpected end of input at offset 4096").getMessage());
  }
  @Test void cacheCouldNotFindZipEntry(){
    var diskZip= Path.of("C:\\Users\\ada\\projects\\myproject\\assets.zip");
    utils.Err.strCmp("""
Cannot find entry in zip (that was found before).
Zip:
  C:\\Users\\ada\\projects\\myproject\\assets.zip
Steps:
  ["textures.zip"]
Entry name:
  "sprite_01.png"
""", Violation.cacheCouldNotFindZipEntry(diskZip, List.of("textures.zip"), "sprite_01.png").getMessage());
  }
}
