package userMessages;

import java.nio.file.Path;
import java.util.List;

import metaParser.Message;
import metaParser.PrettyFileName;
import utils.Join;

import static userMessages.UserError.die;
import static userMessages.UserError.path;

/// Violation: Fearless can no longer do its safe job.
/// Not necessarily a bad actor. An operating system that does not offer a service we
/// need, a folder we cannot own, a packaged runtime that fails, and our own generated
/// files changed under us are all the same thing from here: we cannot keep the
/// promise, so we stop rather than continue on an unknown footing.
/// The counterpart is Report, for what the user gave us and can change.
public final class Violation {
  private Violation(){}

  //-- the pieces of text that more than one message needs, written once
  static String reported(Throwable cause){ return "Reported reason:\n"+cause.getMessage(); }
  static String crashed(){ return "Fearless crashed."; }
  static String reportProblem(){ return "Please report this problem."; }
  static String associatedPrograms(){ return "  <Will return the set of active and monitored Fearless programs>"; }//TODO: later
  static String freshCopyThenReport(){ return """
    Replace this Fearless folder with a fresh copy.
    If this keeps happening, report the problem.
    """;
  }
  static String managerFolderIntro(){ return """
    Fearless uses one process (called the manager process)
    to keep track of other Fearless processes (the user processes).
    The manager folder is the folder used by the manager process to store
    coordination information into files.
    """;
  }
  static String blockingPrograms(){ return """
    Programs that may use or block this folder include security software
    (antivirus, ransomware protection, endpoint protection), backup tools,
    sync tools, and file preview tools.
    """;
  }

  //-- how this copy of Fearless was started
  public static UserError mustUseLauncher(){ return die(
    "Fearless has been started without using its launcher.",
    "Do not start fearless directly from the Jars."
  );}
  public static UserError desktopUnsupported(){ return die(
    "Program fearlessw was started in headless mode.",
    "fearlessw requires GUI capabilities.",
    "Use fearless (without 'w') to work with a console instead."
  );}
  public static UserError badLaunchArg(String s, boolean hasConsole){
    if (hasConsole){ return die(
      "The OS provided a broken path for the input file.",
      "Value: "+s
      );}
    return die(
      "fearless received a broken path for the input file.",
      "Value: "+s
    );
  }
  public static UserError programFolderNotFound(Path startedFrom, String expectedDirName){
    return new UserError("""
      Fearless could not find its own program folder.

      This copy of Fearless appears to have been moved, renamed, or damaged:
      no folder named "%s" exists above
      %s

      %s""".formatted(
        expectedDirName,
        path(startedFrom.toString()),
        freshCopyThenReport()
      ));
  }

  //-- the operating system does not provide a service we need
  //Reached only where Fearless must name the folder this operating system reserves for
  //one program's per-user data: there is no honest guess to make for a system whose
  //conventions Fearless does not know.
  public static UserError unsupportedOperatingSystem(){
    return new UserError("""
      Fearless does not know this operating system.

      Fearless runs on Windows, macOS and Linux. It needs to know which one it
      is running on to find the folder this system keeps for what one program
      writes for one user.

      The operating system reported itself as:
      %s""".formatted(path(System.getProperty("os.name","<not reported>"))));
  }
  public static UserError couldNotStartGui(Throwable cause){
    return new UserError("""
      Fearless could not open its window.

      Fearless needs to show a window, but opening the window failed.

      %s""".formatted(reported(cause)), cause);
  }
  //Fearless asks the operating system for English so that the failures it reports back
  //to us are text we can recognise. Without it we cannot tell one failure from another.
  public static UserError couldNotForceEnglish(String what, Throwable cause){
    return new UserError("""
      Fearless could not set its own language.

      Fearless asks the operating system to report failures in English, so that
      it can recognise them and explain them to you. This request failed:
      %s

      %s""".formatted(path(what), freshCopyThenReport()), cause);
  }
  public static UserError couldNotForceEnglish(String what){ return couldNotForceEnglish(what, null); }

  //-- the packaged runtime, or this copy of Fearless, is damaged
  public static UserError vmOrLinkageFailure(Throwable cause){
    return new UserError("""
      Fearless crashed because of a low-level JVM failure.

      Fearless includes its own packaged JVM. The packaged JVM failed, or the
      packaged Fearless code could not be linked correctly.

      %s""".formatted(freshCopyThenReport()), cause);
  }
  //The icon travels inside the program folder: Fearless put it there when it was built.
  //Failing to load it is not a fact about the user's files, it is a fact about this copy
  //of Fearless, so it is reported like the other damaged-copy failures.
  public static UserError couldNotLoadIcon(Path icon, Throwable cause){
    return new UserError("""
      Fearless could not load its own icon.

      The icon is part of Fearless and travels inside its program folder:
      %s

      %s

      %s""".formatted(
        path(icon.toString()),
        reported(cause),
        freshCopyThenReport()
      ), cause);
  }
  public static UserError couldNotDecodeIcon(Path icon){
    return new UserError("""
      Fearless could not load its own icon.

      The icon is part of Fearless and travels inside its program folder:
      %s
      The file is there and could be read, but it does not hold an image this
      Java runtime can decode.

      %s""".formatted(
        path(icon.toString()),
        freshCopyThenReport()
      ));
  }

  //-- the manager folder: the files Fearless processes use to find each other
  public static UserError couldNotCreateManagerFolder(Path dir, Throwable cause){
    return new UserError("""
      Fearless could not create its manager folder.

      Fearless tried to create this folder:
      %s

      %s
      The manager folder needs write permission.

      %s

      Move or unpack Fearless into a folder with write permission, then start
      Fearless again.""".formatted(
        path(dir.toString()),
        managerFolderIntro(),
        reported(cause)
      ), cause);
  }
  public static UserError couldNotUseInstanceLock(Path lockFile, Throwable cause){
    return new UserError("""
      Fearless could not use the file that marks the manager process.

      %s
      While a manager process runs, it keeps this file reserved, so that any
      new Fearless process can tell that a manager already exists. This
      feature is called file locking. Fearless tried to open and reserve:
      %s

      %s

      Common fixes are to use a manager folder with write permission, or to
      close another program that is using or blocking this folder.
      %s
      """.formatted(
        managerFolderIntro(),
        path(lockFile.toString()),
        reported(cause),
        blockingPrograms()
      ), cause);
  }
  //Written by EVERY starting process, before we know whether a manager
  //already exists; the text must not claim that one does.
  //Only the final atomic rename is reported here: writing the message file
  //itself is delegated to fileSupport, which explains its own failures.
  public static UserError couldNotLeaveStartMessage(Path msgDir, Throwable cause){
    return new UserError("""
      Fearless could not leave its start message.

      %s
      Every starting Fearless process writes one small message file into this
      folder:
      %s
      The manager process (an already running one, or the process that is
      starting right now) then reads and removes those files. The message
      file was written, but renaming it to its final name failed.

      %s

      A manager process may still be running: do not delete the manager
      folder. If the problem repeats, close programs that may be using or
      blocking the folder.
      %s""".formatted(
        managerFolderIntro(),
        path(msgDir.toString()),
        reported(cause),
        blockingPrograms()
      ), cause);
  }
  public static UserError couldNotWatchMessageFolder(Path msgDir, Throwable cause){
    return new UserError("""
      Fearless could not watch its manager folder.

      %s
      Fearless asked the operating system to tell it when files appear in this
      folder:
      %s
      This feature is called file-change notifications, and the request failed.

      %s""".formatted(
        managerFolderIntro(),
        path(msgDir.toString()),
        reported(cause)
      ), cause);
  }
  public static UserError messageFolderNotWatchable(Path msgDir){
    return new UserError("""
      Fearless can no longer watch its manager folder.

      %s
      Fearless was watching this folder:
      %s
      Fearless could watch it when the manager process started, but cannot
      anymore. Watching a folder means asking the operating system to tell
      Fearless when files appear in it (file-change notifications).

      The folder may have been removed, replaced, or disconnected, or the
      operating system may have stopped sending file-change notifications
      for it.""".formatted(
        managerFolderIntro(),
        path(msgDir.toString())
      ));
  }
  //Reading a message file is delegated to fileSupport; this covers the
  //remaining folder operations of a drain: listing, and removing files.
  public static UserError couldNotDrainMessageFolder(Path msgDir, Throwable cause){
    return new UserError("""
      Fearless could not list its manager folder, or could not remove a
      message file from it.

      The manager folder is:
      %s
      A file may have been deleted, locked, or changed while Fearless was
      using it, or the folder itself may be blocked.

      %s""".formatted(
        path(msgDir.toString()),
        reported(cause)
      ), cause);
  }

  public static UserError couldNotSaveRegisteredFolders(Path managerDir, Throwable cause){
    return new UserError("""
      Fearless could not save what it remembers about your project folders.

      %s
      The manager folder is:
      %s
      The change was not recorded, so Fearless will not remember it.

      %s
      %s""".formatted(
        managerFolderIntro(),
        path(managerDir.toString()),
        reported(cause),
        blockingPrograms()
      ), cause);
  }
  public static UserError registeredFoldersUnreadable(Path managerDir){
    return new UserError("""
      Fearless cannot read back what it remembers about your project folders.

      %s
      The manager folder is:
      %s
      Its content is not what Fearless left there: another program changed it,
      or an earlier Fearless was stopped while saving. Fearless stops here
      rather than guess what it used to remember.

      While no Fearless program is running, you can delete the manager folder.
      Fearless then starts again remembering nothing, and starting Fearless on
      a project folder registers that folder again.
      """.formatted(
        managerFolderIntro(),
        path(managerDir.toString())
      ));
  }

  public static UserError associationsAmbiguous(String reported){
    return new UserError("""
      Fearless cannot tell which of your Fearless installs should open Fearless
      projects: more than one is registered with your system at once.

      Fearless stopped before touching anything: your system is exactly as it
      was. Fearless keeps running: only the double-click shortcut is missing.

      What is registered:
      %s""".formatted(reported));
  }
  public static UserError associationUserLocked(String reported){
    return new UserError("""
      Fearless cannot become the program that opens Fearless projects.

      Your system remembers a choice you made by hand for this kind of file,
      and no program can change or remove that choice, including this one.
      Fearless stopped before touching anything: your system is exactly as it
      was.

      The only way to clear it is Settings, Apps, Default apps, Reset - which
      resets every app default on your machine, not only this one.

      Fearless keeps running: only the double-click shortcut is missing.

      What is locked:
      %s""".formatted(reported));
  }
  public static UserError associationNotOurs(String reported){
    return new UserError("""
      Fearless cannot become the program that opens Fearless projects.

      Another program already answers for this kind of file. Fearless stopped
      before touching anything: your system is exactly as it was.

      Fearless keeps running: only the double-click shortcut is missing.

      What stood in the way:
      %s""".formatted(reported));
  }
  public static UserError associationNotWritable(String reported){
    return new UserError("""
      Fearless cannot become the program that opens Fearless projects.

      What Fearless would need to write, or remove, is not yours to change.
      Fearless stopped before touching anything: your system is exactly as it
      was.

      What stood in the way:
      %s""".formatted(reported));
  }
  public static UserError associationLeftHalfDone(String reported){
    return new UserError("""
      Fearless could not put your system back as it was.

      A step failed, and undoing what had already been written failed too, so
      your system is left part way. Which program opens Fearless projects may
      now be wrong, and you can settle it by hand in your system settings.

      Where the undoing stopped:
      %s""".formatted(reported));
  }

  //-- our own generated files, changed under us
  //Either in generated outputs under ".fearless_out", or in the other files of the
  //project after the initial verification checks. Reaching one of these means another
  //actor touched what only Fearless should write, a previous run stopped mid-build, or
  //the filesystem returned partial writes or stale directory state.
  public static UserError cacheMissingPkgApiFile(Path apiJson){
    return new UserError("""
Build cache is missing a generated package API file.
That metadata should be stored in:
  %s
""".formatted(PrettyFileName.displayFileName(apiJson.toUri())));
  }
  public static UserError cacheInvalidFile(Path mapJson, String parseErr){
    return new UserError("""
Build cache contains an invalid cached file.

Fearless tried to read:
  %s
but the file content is not valid.

Parse error:
  %s
      """.formatted(PrettyFileName.displayFileName(mapJson.toUri()), parseErr));
  }
  public static UserError cacheCanNotFindZipEntry(Path diskZip, List<String> steps, String entryName){
    return new UserError("""
Can not find entry in zip (that was found before)
Zip:
  %s
Steps:
  %s
Entry name:
  %s
""".formatted(PrettyFileName.displayFileName(diskZip.toUri()),
      Join.of(steps.stream().map(Message::displayString),"[",", ","]", "<no steps>"),
      Message.displayString(entryName)));
  }
}
