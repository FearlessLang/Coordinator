package userMessages;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

import metaParser.Message;
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
  public static String reported(Throwable cause){ return "Reported reason:\n"+cause.getMessage(); }
  private static Supplier<List<String>> running= List::of;
  public static void running(Supplier<List<String>> live){ running= live; }
  static String associatedPrograms(){ return Join.of(running.get().stream().map(s->"  "+s),"","\n","",""); }
  public static String freshCopyThenReport(){ return """
    Replace this Fearless folder with a fresh copy.
    If this keeps happening, report the problem.
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
  public static UserError badLaunchArg(String s, boolean hasConsole){ return die(
    (hasConsole ? "The OS provided" : "fearless received")+" a broken path for the input file.",
    "Value: "+s
  );}

  //-- the operating system does not provide a service we need
  public static UserError unsupportedOperatingSystem(){
    return new UserError("""
      Fearless does not know this operating system.

      Fearless runs on Windows, macOS and Linux.

      The operating system reported itself as:
      %s""".formatted(path(System.getProperty("os.name","<not reported>"))));
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
  public static UserError cacheMissingBaseApiFile(Path apiJson){
    return new UserError("""
      Fearless could not load the API of its own standard library.

      This copy of Fearless should already have this file:
      %s

      %s""".formatted(
        path(apiJson.toString()),
        freshCopyThenReport()
      ));
  }

  public static UserError associationsAmbiguous(String reported){
    return new UserError("""
      Fearless cannot tell which of your Fearless installs should open Fearless
      projects: more than one is registered with your system at once.

      Fearless stopped before touching anything: your system is exactly as it
      was.

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

      What is locked:
      %s""".formatted(reported));
  }
  public static UserError associationNotOurs(String reported){
    return new UserError("""
      Fearless cannot become the program that opens Fearless projects.

      Another program already answers for this kind of file. Fearless stopped
      before touching anything: your system is exactly as it was.

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
      Delete this project's .fearless_out folder, then recompile.
      """.formatted(path(apiJson.toString())));
  }
  public static UserError cacheInvalidFile(Path file, String parseErr){
    return new UserError("""
      Build cache contains an invalid cached file.

      Fearless tried to read:
      %s
      but the file content is not valid.

      Parse error:
        %s
      """.formatted(path(file.toString()), parseErr));
  }
  public static UserError cacheCouldNotFindZipEntry(Path diskZip, List<String> steps, String entryName){
    return new UserError("""
      Cannot find entry in zip (that was found before).
      Zip:
      %s
      Steps:
        %s
      Entry name:
        %s
      """.formatted(
        path(diskZip.toString()),
        Join.of(steps.stream().map(Message::displayString),"[",", ","]", "<no steps>"),
        Message.displayString(entryName)
      ));
  }
}
