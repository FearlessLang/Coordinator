package userMessages;

import static userMessages.UserError.die;
import static userMessages.UserError.path;

import java.nio.file.FileSystemException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

import utils.Join;

/// Violation: Fearless can no longer do its safe job.
/// Not necessarily a bad actor. An operating system that does not offer a service we
/// need, a folder we cannot own, a packaged runtime that fails, and our own generated
/// files changed under us are all the same thing from here: we cannot keep the
/// promise, so we stop rather than continue on an unknown footing.
/// The counterpart is Report, for what the user gave us and can change.
public final class Violation{
  //-- the pieces of text that more than one message needs, written once
  public static String reported(Throwable cause){ return "Reported reason:\n"+(cause instanceof FileSystemException f ? lines(f) : cause.getMessage()); }
  public static String lines(FileSystemException f){ return Join.of(Stream.of(f.getFile(), f.getOtherFile(), f.getReason()).filter(Objects::nonNull), "", "\n", ""); }
  private static Supplier<List<String>> running= List::of;
  public static void running(Supplier<List<String>> live){ running = live; }
  static String associatedPrograms(){ return Join.of(running.get().stream().map(s -> "  "+s), "", "\n", "", ""); }
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
%s""".formatted(path(System.getProperty("os.name", "<not reported>"))));
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
  public static String associationsAmbiguous(String reported){
    return """
Fearless cannot tell which of your Fearless installs should open Fearless
projects: more than one is registered with your system at once.

Fearless stopped before touching anything: your system is exactly as it
was.

What is registered:
%s""".formatted(reported);
  }
  public static final String removeAllButOne= "Remove all of those Fearless installs but one, then close this dialog.";
  public static String associationNotOurs(Map<String,String> held){
    return """
Fearless needs the desktop to open the files with the extensions listed below with Fearless.
The desktop opens those files with other programs:
%s""".formatted(Join.of(held.entrySet().stream().map(e -> "  "+UserError.disp(e.getKey())+" is opened with "+UserError.disp(e.getValue())), "", "\n", ""));
  }
  public static final String tickToHold= """
Fearless makes the desktop open the files of each ticked extension with Fearless.
Fearless gives up each extension left unticked; Cancel gives up every extension.""";
  public static String associationNotTaken(Map<String,String> blocked){
    return """
Fearless needs the desktop to open the files with the extensions listed below with Fearless.
The desktop does not let Fearless change the program opening those files:
%s""".formatted(Join.of(blocked.entrySet().stream().map(e -> "  "+UserError.disp(e.getKey())+" needs a change of\n    "+e.getValue().replace("\n", "\n    ")), "", "\n", ""));
  }
  public static final String tickToTake= """
Change the desktop so that Fearless can change the program opening the files of each ticked extension, then press OK.
Fearless gives up each extension left unticked; Cancel gives up every extension.""";
  public static String associationNotWritable(String reported){
    return """
Fearless cannot become the program that opens Fearless projects and the
other kinds of file it registers.

What Fearless would need to write, or remove, is not yours to change.

What stood in the way:
%s""".formatted(reported);
  }
  public static final String givePermission= "Give Fearless the permission to change each of those files, folders and settings, then close this dialog.";
  public static UserError associationLeftHalfDone(String reported){
    return new UserError("""
Fearless could not finish changing which programs open the kinds of file
it registers.

A step failed, and the steps before it may have changed your system:
which program opens Fearless projects, and the other kinds of file
Fearless registers, may now be wrong. The next start of Fearless does
these changes again.

What failed:
%s""".formatted(reported));
  }
}
