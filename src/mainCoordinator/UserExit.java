package mainCoordinator;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;

@SuppressWarnings("serial")
public final class UserExit extends RuntimeException{
  public UserExit(String msg){ super(msg, null, false, false); }

  static UserExit die(String first, String... more){
    var sb= new StringBuilder();
    sb.append("Error: ").append(first).append('\n');
    for (var s: more){ sb.append("  ").append(s).append('\n'); }
    return new UserExit(sb.toString());
  }

  static UserExit launchNeedsProject(){ return die(
    "Start Fearless by opening a \"*.fearless\" file (or pass the project path explicitly).",
    "Nothing was provided to open."
  );}

  static UserExit desktopUnsupported(){ return die(
    "Start Fearless by opening a \"*.fearless\" file (file association).",
    "This system does not support Desktop open-file notifications."
  );}

  static UserExit openFileUnsupported(){ return die(
    "Start Fearless by opening a \"*.fearless\" file (file association).",
    "This system does not support APP_OPEN_FILE notifications."
  );}

  static UserExit interruptedWhileWaitingForProject(){ return die(
    "Interrupted while waiting for the project to open."
  );}

  static UserExit emptyLaunchArg(){ return die(
    "Could not read the project path.",
    "Argument was empty."
  );}

  static UserExit badLaunchArg(String s){ return die(
    "Could not read the project path.",
    "Value: "+s
  );}

  static UserExit nonAbsoluteLaunchArg(String s){ return die(
    "Fearless was started without an absolute project path.",
    "Fix: open a \"*.fearless\" file with Fearless or pass an absolute path.",
    "Value: "+s
  );}

  static UserExit cannotFindProjectFolder(Path launch){ return die(
    "Could not determine the project folder from the launch path.",
    "Value: "+launch
  );}

  static UserExit mustUseLauncherMissingAppDir(){ return die(
    "Start Fearless using its launcher (not by running a jar directly).",
    "Launcher did not provide fearless.appDir."
  );}

  static UserExit launcherProvidedNonAbsoluteAppDir(String s){ return die(
    "Launcher error: fearless.appDir must be an absolute path.",
    "Value: "+s
  );}

  static UserExit from(Throwable t){
    return new UserExit(t.getMessage() == null ? t.toString() : t.getMessage());
  }

  static String crash(Throwable t){
    var sw= new StringWriter();
    t.printStackTrace(new PrintWriter(sw));
    return """
Error: Fearless crashed (this is a bug).
  Please report this error and include the details below.

Details:
""" + sw;
  }
}