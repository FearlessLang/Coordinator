package userMessages;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

/// One test per Violation.xxx factory, each showing the message in its full intended
/// form: mock inputs, no real Fearless run needed. Long shared paragraphs
/// (freshCopyThenReport/managerFolderIntro/blockingPrograms) are elided with a hole
/// once their own exact text is pinned by the three dedicated tests below; everything
/// specific to one message is spelled out in full.
final class ViolationTest{
  static{ utils.Err.setUp(AssertionFailedError.class, Assertions::assertEquals, Assertions::assertTrue); }

  @Test void freshCopyThenReportText(){
    utils.Err.strCmp("""
Replace this Fearless folder with a fresh copy.
If this keeps happening, report the problem.
""", Violation.freshCopyThenReport());
  }
  @Test void managerFolderIntroText(){
    utils.Err.strCmp("""
Fearless uses one process (called the manager process)
to keep track of other Fearless processes (the user processes).
The manager folder is the folder used by the manager process to store
coordination information into files.
""", Violation.managerFolderIntro());
  }
  @Test void blockingProgramsText(){
    utils.Err.strCmp("""
Programs that may use or block this folder include security software
(antivirus, ransomware protection, endpoint protection), backup tools,
sync tools, and file preview tools.
""", Violation.blockingPrograms());
  }

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
    var startedFrom= Path.of("C:\\Users\\marco\\fearlessManaged0_001\\app");
    utils.Err.strCmp("""
This copy of Fearless appears to have been moved, renamed, or damaged:
no folder named "fearlessManaged0_001" exists above
  C:\\Users\\marco\\fearlessManaged0_001\\app

[###]
""", Violation.programFolderNotFound(startedFrom, "fearlessManaged0_001").getMessage());
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
    utils.Err.strCmp("""
Fearless could not open its window.

Fearless needs to show a window, but opening the window failed.

Reported reason:
mock headless failure""", Violation.couldNotStartGui(new RuntimeException("mock headless failure")).getMessage());
  }
  @Test void couldNotForceEnglish(){
    utils.Err.strCmp("""
Fearless could not set its own language.

Fearless asks the operating system to report failures in English, so that
it can recognise them and explain them to you. This request failed:
  mock locale failure

[###]
""", Violation.couldNotForceEnglish("mock locale failure", new RuntimeException("mock cause")).getMessage());
  }
  @Test void couldNotForceEnglishOneArgOverloadMatchesTwoArgWithNullCause(){
    Assertions.assertEquals(
      Violation.couldNotForceEnglish("mock locale failure", null).getMessage(),
      Violation.couldNotForceEnglish("mock locale failure").getMessage());
  }

  @Test void vmOrLinkageFailure(){
    utils.Err.strCmp("""
Fearless crashed because of a low-level JVM failure.

Fearless includes its own packaged JVM. The packaged JVM failed, or the
packaged Fearless code could not be linked correctly.

[###]
""", Violation.vmOrLinkageFailure(new LinkageError("mock linkage failure")).getMessage());
  }
  @Test void couldNotLoadIcon(){
    var icon= Path.of("C:\\fearlessManaged0_001\\app\\icon.png");
    utils.Err.strCmp("""
Fearless could not load its own icon.

This copy of Fearless should already have this file:
  C:\\fearlessManaged0_001\\app\\icon.png

Reported reason:
mock io failure

[###]
""", Violation.couldNotLoadIcon(icon, new IOException("mock io failure")).getMessage());
  }
  @Test void couldNotDecodeIcon(){
    var icon= Path.of("C:\\fearlessManaged0_001\\app\\icon.png");
    utils.Err.strCmp("""
Fearless could not load its own icon.

This copy of Fearless should already have this file:
  C:\\fearlessManaged0_001\\app\\icon.png
The file is there and could be read, but it does not hold an image this
Java runtime can decode.

[###]
""", Violation.couldNotDecodeIcon(icon).getMessage());
  }
  @Test void cacheMissingBaseApiFile(){
    var apiJson= Path.of("C:\\fearlessManaged0_001\\app\\stdLib\\baseCache\\base.json");
    utils.Err.strCmp("""
This copy of Fearless should already have this file:
  C:\\fearlessManaged0_001\\app\\stdLib\\baseCache\\base.json

Replace this Fearless folder with a fresh copy.
If this keeps happening, report the problem.
""", Violation.cacheMissingBaseApiFile(apiJson).getMessage());
  }
  @Test void multipleIcons(){
    var dir= Path.of("C:\\proj\\.icons");
    var found= List.of(Path.of("C:\\proj\\.icons\\a.png"), Path.of("C:\\proj\\.icons\\b.png"));
    utils.Err.strCmp("""
More than one .png file was found for this project's icon.

Looked in:
  C:\\proj\\.icons

Found:
  a.png
  b.png

Keep exactly one .png file there.""", Violation.multipleIcons(dir, found).getMessage());
  }

  @Test void couldNotCreateManagerFolder(){
    var dir= Path.of("C:\\Users\\marco\\AppData\\Local\\Fearless\\manager");
    utils.Err.strCmp("""
Fearless could not create its manager folder.

Fearless tried to create this folder:
  C:\\Users\\marco\\AppData\\Local\\Fearless\\manager

[###]
The manager folder needs write permission.

Reported reason:
mock io failure

Move or unpack Fearless into a folder with write permission, then start
Fearless again.""", Violation.couldNotCreateManagerFolder(dir, new IOException("mock io failure")).getMessage());
  }
  @Test void couldNotUseInstanceLock(){
    var lockFile= Path.of("C:\\Users\\marco\\AppData\\Local\\Fearless\\manager\\lock");
    utils.Err.strCmp("""
Fearless could not use the file that marks the manager process.

[###]
While a manager process runs, it keeps this file reserved, so that any
new Fearless process can tell that a manager already exists. This
feature is called file locking. Fearless tried to open and reserve:
  C:\\Users\\marco\\AppData\\Local\\Fearless\\manager\\lock

Reported reason:
mock io failure

Common fixes are to use a manager folder with write permission, or to
close another program that is using or blocking this folder.
[###]
""", Violation.couldNotUseInstanceLock(lockFile, new IOException("mock io failure")).getMessage());
  }
  @Test void couldNotLeaveStartMessage(){
    var msgDir= Path.of("C:\\Users\\marco\\AppData\\Local\\Fearless\\manager\\messages");
    utils.Err.strCmp("""
Fearless could not leave its start message.

[###]
Every starting Fearless process writes one small message file into this
folder:
  C:\\Users\\marco\\AppData\\Local\\Fearless\\manager\\messages
The manager process (an already running one, or the process that is
starting right now) then reads and removes those files. The message
file was written, but renaming it to its final name failed.

Reported reason:
mock io failure

A manager process may still be running: do not delete the manager
folder. If the problem repeats, close programs that may be using or
blocking the folder.
[###]
""", Violation.couldNotLeaveStartMessage(msgDir, new IOException("mock io failure")).getMessage());
  }
  @Test void couldNotWatchMessageFolder(){
    var msgDir= Path.of("C:\\Users\\marco\\AppData\\Local\\Fearless\\manager\\messages");
    utils.Err.strCmp("""
Fearless could not watch its manager folder.

[###]
Fearless asked the operating system to tell it when files appear in this
folder:
  C:\\Users\\marco\\AppData\\Local\\Fearless\\manager\\messages
This feature is called file-change notifications, and the request failed.

Reported reason:
mock io failure""", Violation.couldNotWatchMessageFolder(msgDir, new IOException("mock io failure")).getMessage());
  }
  @Test void messageFolderNotWatchable(){
    var msgDir= Path.of("C:\\Users\\marco\\AppData\\Local\\Fearless\\manager\\messages");
    utils.Err.strCmp("""
Fearless can no longer watch its manager folder.

[###]
Fearless was watching this folder:
  C:\\Users\\marco\\AppData\\Local\\Fearless\\manager\\messages
Fearless could watch it when the manager process started, but cannot
anymore. Watching a folder means asking the operating system to tell
Fearless when files appear in it (file-change notifications).

The folder may have been removed, replaced, or disconnected, or the
operating system may have stopped sending file-change notifications
for it.""", Violation.messageFolderNotWatchable(msgDir).getMessage());
  }
  @Test void couldNotDrainMessageFolder(){
    var msgDir= Path.of("C:\\Users\\marco\\AppData\\Local\\Fearless\\manager");
    utils.Err.strCmp("""
Fearless could not list its manager folder, or could not remove a
message file from it.

The manager folder is:
  C:\\Users\\marco\\AppData\\Local\\Fearless\\manager
A file may have been deleted, locked, or changed while Fearless was
using it, or the folder itself may be blocked.

Reported reason:
mock io failure""", Violation.couldNotDrainMessageFolder(msgDir, new IOException("mock io failure")).getMessage());
  }
  @Test void couldNotSaveRegisteredFolders(){
    var managerDir= Path.of("C:\\Users\\marco\\AppData\\Local\\Fearless\\manager");
    utils.Err.strCmp("""
Fearless could not save what it remembers about your project folders.

[###]
The manager folder is:
  C:\\Users\\marco\\AppData\\Local\\Fearless\\manager
The change was not recorded, so Fearless will not remember it.

Reported reason:
mock io failure
[###]
""", Violation.couldNotSaveRegisteredFolders(managerDir, new IOException("mock io failure")).getMessage());
  }

  @Test void associationsAmbiguous(){
    utils.Err.strCmp("""
Fearless cannot tell which of your Fearless installs should open Fearless
projects: more than one is registered with your system at once.

Fearless stopped before touching anything: your system is exactly as it
was.

What is registered:
mock registered list""", Violation.associationsAmbiguous("mock registered list").getMessage());
  }
  @Test void associationUserLocked(){
    utils.Err.strCmp("""
Fearless cannot become the program that opens Fearless projects.

Your system remembers a choice you made by hand for this kind of file,
and no program can change or remove that choice, including this one.
Fearless stopped before touching anything: your system is exactly as it
was.

The only way to clear it is Settings, Apps, Default apps, Reset - which
resets every app default on your machine, not only this one.

What is locked:
mock lock owner""", Violation.associationUserLocked("mock lock owner").getMessage());
  }
  @Test void associationNotOurs(){
    utils.Err.strCmp("""
Fearless cannot become the program that opens Fearless projects.

Another program already answers for this kind of file. Fearless stopped
before touching anything: your system is exactly as it was.

What stood in the way:
mock other owner""", Violation.associationNotOurs("mock other owner").getMessage());
  }
  @Test void associationNotWritable(){
    utils.Err.strCmp("""
Fearless cannot become the program that opens Fearless projects.

What Fearless would need to write, or remove, is not yours to change.
Fearless stopped before touching anything: your system is exactly as it
was.

What stood in the way:
mock unwritable reason""", Violation.associationNotWritable("mock unwritable reason").getMessage());
  }
  @Test void associationLeftHalfDone(){
    utils.Err.strCmp("""
Fearless could not put your system back as it was.

A step failed, and undoing what had already been written failed too, so
your system is left part way. Which program opens Fearless projects may
now be wrong, and you can settle it by hand in your system settings.

Where the undoing stopped:
mock rollback trace""", Violation.associationLeftHalfDone("mock rollback trace").getMessage());
  }

  @Test void cacheMissingPkgApiFile(){
    var apiJson= Path.of("C:\\proj\\.fearless_out\\basketball.json");
    utils.Err.strCmp("""
Build cache is missing a generated package API file.
That metadata should be stored in:
  C:\\proj\\.fearless_out\\basketball.json
Delete this project's .fearless_out folder, then recompile.
""", Violation.cacheMissingPkgApiFile(apiJson).getMessage());
  }
  @Test void cacheInvalidFile(){
    var mapJson= Path.of("C:\\proj\\.fearless_out\\_map.json");
    utils.Err.strCmp("""
Build cache contains an invalid cached file.

Fearless tried to read:
  C:\\proj\\.fearless_out\\_map.json
but the file content is not valid.

Parse error:
  mock parse error at offset 12
""", Violation.cacheInvalidFile(mapJson, "mock parse error at offset 12").getMessage());
  }
  @Test void cacheCanNotFindZipEntry(){
    var diskZip= Path.of("C:\\proj\\assets.zip");
    utils.Err.strCmp("""
Cannot find entry in zip (that was found before).
Zip:
  C:\\proj\\assets.zip
Steps:
  ["inner.zip"]
Entry name:
  "entry.txt"
""", Violation.cacheCanNotFindZipEntry(diskZip, List.of("inner.zip"), "entry.txt").getMessage());
  }
}
