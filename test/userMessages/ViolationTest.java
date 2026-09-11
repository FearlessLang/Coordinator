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
  @Test void programFolderNotFound(){
    var startedFrom= Path.of("C:\\Users\\ada\\Desktop\\app");
    utils.Err.strCmp("""
This copy of Fearless appears to have been moved, renamed, or damaged:
no folder named "fearlessManaged0_007" exists above
  C:\\Users\\ada\\Desktop\\app

Replace this Fearless folder with a fresh copy.
If this keeps happening, report the problem.
""", Violation.programFolderNotFound(startedFrom, "fearlessManaged0_007").getMessage());
  }

  @Test void unsupportedOperatingSystem(){
    var osName= System.getProperty("os.name","<not reported>");
    utils.Err.strCmp("""
Fearless does not know this operating system.

Fearless runs on Windows, macOS and Linux. It needs to know which one it
is running on to find the folder this system keeps for what one program
writes for one user.

The operating system reported itself as:
  %s""".formatted(osName), Violation.unsupportedOperatingSystem().getMessage());
  }
  @Test void couldNotStartGui(){
    var cause= new RuntimeException("No X11 DISPLAY variable was set, but this program performed an operation which requires it.");
    utils.Err.strCmp("""
Fearless could not open its window.

Fearless needs to show a window, but opening the window failed.

Reported reason:
No X11 DISPLAY variable was set, but this program performed an operation which requires it.""", Violation.couldNotStartGui(cause).getMessage());
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

  @Test void vmOrLinkageFailure(){
    var cause= new LinkageError("loader constraint violation: loader previously initiated loading for coordinator.Coordinator");
    utils.Err.strCmp("""
Fearless crashed because of a low-level JVM failure.

Fearless includes its own packaged JVM. The packaged JVM failed, or the
packaged Fearless code could not be linked correctly.

Replace this Fearless folder with a fresh copy.
If this keeps happening, report the problem.
""", Violation.vmOrLinkageFailure(cause).getMessage());
  }
  @Test void couldNotLoadIcon(){
    var icon= Path.of("C:\\Program Files\\FearlessManaged0_007\\app\\icon.png");
    var cause= new IOException("The process cannot access the file because it is being used by another process");
    utils.Err.strCmp("""
Fearless could not load its own icon.

This copy of Fearless should already have this file:
  C:\\Program Files\\FearlessManaged0_007\\app\\icon.png

Reported reason:
The process cannot access the file because it is being used by another process

Replace this Fearless folder with a fresh copy.
If this keeps happening, report the problem.
""", Violation.couldNotLoadIcon(icon, cause).getMessage());
  }
  @Test void couldNotDecodeIcon(){
    var icon= Path.of("C:\\Users\\ada\\Downloads\\fearlessManaged0_007\\app\\icon.png");
    utils.Err.strCmp("""
Fearless could not load its own icon.

This copy of Fearless should already have this file:
  C:\\Users\\ada\\Downloads\\fearlessManaged0_007\\app\\icon.png
The file is there and could be read, but it does not hold an image this
Java runtime can decode.

Replace this Fearless folder with a fresh copy.
If this keeps happening, report the problem.
""", Violation.couldNotDecodeIcon(icon).getMessage());
  }
  @Test void cacheMissingBaseApiFile(){
    var apiJson= Path.of("C:\\Users\\ada\\Downloads\\fearlessManaged0_007\\app\\stdLib\\baseCache\\base.json");
    utils.Err.strCmp("""
This copy of Fearless should already have this file:
  C:\\Users\\ada\\Downloads\\fearlessManaged0_007\\app\\stdLib\\baseCache\\base.json

Replace this Fearless folder with a fresh copy.
If this keeps happening, report the problem.
""", Violation.cacheMissingBaseApiFile(apiJson).getMessage());
  }
  @Test void multipleIcons(){
    var dir= Path.of("C:","Users","ada","projects","myproject",".icons");
    var found= List.of(dir.resolve("logo.png"), dir.resolve("logo_v2.png"));
    utils.Err.strCmp("""
More than one .png file was found for this project's icon.

Looked in:
  %s

Found:
  logo.png
  logo_v2.png

Keep exactly one .png file there.""".formatted(dir), Violation.multipleIcons(dir, found).getMessage());
  }

  @Test void couldNotCreateManagerFolder(){
    var dir= Path.of("C:\\Users\\ada\\AppData\\Local\\Fearless\\manager");
    var cause= new IOException("Access is denied");
    utils.Err.strCmp("""
Fearless could not create its manager folder.

Fearless tried to create this folder:
  C:\\Users\\ada\\AppData\\Local\\Fearless\\manager

Fearless uses one process (called the manager process)
to keep track of other Fearless processes (the user processes).
The manager folder is the folder used by the manager process to store
coordination information into files.

The manager folder needs write permission.

Reported reason:
Access is denied

Move or unpack Fearless into a folder with write permission, then start
Fearless again.""", Violation.couldNotCreateManagerFolder(dir, cause).getMessage());
  }
  @Test void couldNotUseInstanceLockSanitizesNonAsciiUserName(){
    var lockFile= Path.of("C:\\Users\\caf\u00e9\\AppData\\Local\\Fearless\\manager\\lock");
    var cause= new IOException("The process cannot access the file because it is being used by another process");
    utils.Err.strCmp("""
Fearless could not use the file that marks the manager process.

Fearless uses one process (called the manager process)
to keep track of other Fearless processes (the user processes).
The manager folder is the folder used by the manager process to store
coordination information into files.

While a manager process runs, it keeps this file reserved, so that any
new Fearless process can tell that a manager already exists. This
feature is called file locking. Fearless tried to open and reserve:
  C:\\Users\\caf?\\AppData\\Local\\Fearless\\manager\\lock

Reported reason:
The process cannot access the file because it is being used by another process

Common fixes are to use a manager folder with write permission, or to
close another program that is using or blocking this folder.
Programs that may use or block this folder include security software
(antivirus, ransomware protection, endpoint protection), backup tools,
sync tools, and file preview tools.

""", Violation.couldNotUseInstanceLock(lockFile, cause).getMessage());
  }
  @Test void couldNotLeaveStartMessage(){
    var msgDir= Path.of("C:\\Users\\ada\\AppData\\Local\\Fearless\\manager\\messages");
    var cause= new IOException("Cannot create a file when that file already exists");
    utils.Err.strCmp("""
Fearless could not leave its start message.

Fearless uses one process (called the manager process)
to keep track of other Fearless processes (the user processes).
The manager folder is the folder used by the manager process to store
coordination information into files.

Every starting Fearless process writes one small message file into this
folder:
  C:\\Users\\ada\\AppData\\Local\\Fearless\\manager\\messages
The manager process (an already running one, or the process that is
starting right now) then reads and removes those files. The message
file was written, but renaming it to its final name failed.

Reported reason:
Cannot create a file when that file already exists

A manager process may still be running: do not delete the manager
folder. If the problem repeats, close programs that may be using or
blocking the folder.
Programs that may use or block this folder include security software
(antivirus, ransomware protection, endpoint protection), backup tools,
sync tools, and file preview tools.
""", Violation.couldNotLeaveStartMessage(msgDir, cause).getMessage());
  }
  @Test void couldNotWatchMessageFolder(){
    var msgDir= Path.of("C:\\Users\\ada\\AppData\\Local\\Fearless\\manager\\messages");
    var cause= new IOException("The I/O operation has been aborted because of either a thread exit or an application request");
    utils.Err.strCmp("""
Fearless could not watch its manager folder.

Fearless uses one process (called the manager process)
to keep track of other Fearless processes (the user processes).
The manager folder is the folder used by the manager process to store
coordination information into files.

Fearless asked the operating system to tell it when files appear in this
folder:
  C:\\Users\\ada\\AppData\\Local\\Fearless\\manager\\messages
This feature is called file-change notifications, and the request failed.

Reported reason:
The I/O operation has been aborted because of either a thread exit or an application request""", Violation.couldNotWatchMessageFolder(msgDir, cause).getMessage());
  }
  @Test void messageFolderNotWatchable(){
    var msgDir= Path.of("C:\\Users\\ada\\AppData\\Local\\Fearless\\manager\\messages");
    utils.Err.strCmp("""
Fearless can no longer watch its manager folder.

Fearless uses one process (called the manager process)
to keep track of other Fearless processes (the user processes).
The manager folder is the folder used by the manager process to store
coordination information into files.

Fearless was watching this folder:
  C:\\Users\\ada\\AppData\\Local\\Fearless\\manager\\messages
Fearless could watch it when the manager process started, but cannot
anymore. Watching a folder means asking the operating system to tell
Fearless when files appear in it (file-change notifications).

The folder may have been removed, replaced, or disconnected, or the
operating system may have stopped sending file-change notifications
for it.""", Violation.messageFolderNotWatchable(msgDir).getMessage());
  }
  @Test void couldNotDrainMessageFolder(){
    var msgDir= Path.of("C:\\Users\\ada\\AppData\\Local\\Fearless\\manager");
    var cause= new IOException("The system cannot find the file specified");
    utils.Err.strCmp("""
Fearless could not list its manager folder, or could not remove a
message file from it.

The manager folder is:
  C:\\Users\\ada\\AppData\\Local\\Fearless\\manager
A file may have been deleted, locked, or changed while Fearless was
using it, or the folder itself may be blocked.

Reported reason:
The system cannot find the file specified""", Violation.couldNotDrainMessageFolder(msgDir, cause).getMessage());
  }
  @Test void couldNotSaveRegisteredFolders(){
    var managerDir= Path.of("C:\\Users\\ada\\AppData\\Local\\Fearless\\manager");
    var cause= new IOException("There is not enough space on the disk");
    utils.Err.strCmp("""
Fearless could not save what it remembers about your project folders.

Fearless uses one process (called the manager process)
to keep track of other Fearless processes (the user processes).
The manager folder is the folder used by the manager process to store
coordination information into files.

The manager folder is:
  C:\\Users\\ada\\AppData\\Local\\Fearless\\manager
The change was not recorded, so Fearless will not remember it.

Reported reason:
There is not enough space on the disk
Programs that may use or block this folder include security software
(antivirus, ransomware protection, endpoint protection), backup tools,
sync tools, and file preview tools.
""", Violation.couldNotSaveRegisteredFolders(managerDir, cause).getMessage());
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
  @Test void cacheCanNotFindZipEntry(){
    var diskZip= Path.of("C:\\Users\\ada\\projects\\myproject\\assets.zip");
    utils.Err.strCmp("""
Cannot find entry in zip (that was found before).
Zip:
  C:\\Users\\ada\\projects\\myproject\\assets.zip
Steps:
  ["textures.zip"]
Entry name:
  "sprite_01.png"
""", Violation.cacheCanNotFindZipEntry(diskZip, List.of("textures.zip"), "sprite_01.png").getMessage());
  }
}
