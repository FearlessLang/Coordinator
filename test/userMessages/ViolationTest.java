package userMessages;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    var osName= System.getProperty("os.name", "<not reported>");
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
    assertEquals(
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
  private static final String chosenByHand= """
Fearless cannot become the program that opens Fearless projects and the
other kinds of file it registers.

Your system remembers the choices listed below. Each one is a choice
made by hand of the program that opens a kind of file, for example with
"Open with" and "Always". Fearless does not remove these choices on
its own, and stopped before touching anything: your system is exactly
as it was.

Removing these choices clears the way. Each choice is listed under the
place holding the choice: a file, or a kind of file standing for the
registry key
HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts\\<kind of file>\\UserChoice
and the key UserChoiceLatest next to it.

The choices:
""";
  @Test void associationChosenByHandForKindsOfFile(){
    utils.Err.strCmp(chosenByHand+"""
.foo claimed by "hello.Foo" of project "demo"
  SomeApp.foo
.fapp042 claimed by "hello.Bar" of project "demo"
  SomeApp.fapp042""", Violation.associationChosenByHand(ordered(".foo", "SomeApp.foo", ".fapp042", "SomeApp.fapp042"), ViolationTest::claimedBy).getMessage());
  }
  @Test void associationChosenByHandInFiles(){
    var choices= new LinkedHashMap<String,List<String>>();
    choices.put("/home/me/.config/mimeapps.list", List.of("application/x-fearless-htm=firefox.desktop", "application/x-fearless=editor.desktop;"));
    choices.put("/etc/xdg/mimeapps.list", List.of("text/plain=fearlessBin0_001.desktop"));
    utils.Err.strCmp(chosenByHand+"""
/home/me/.config/mimeapps.list
  application/x-fearless-htm=firefox.desktop
  application/x-fearless=editor.desktop;
/etc/xdg/mimeapps.list
  text/plain=fearlessBin0_001.desktop""", Violation.associationChosenByHand(choices, e -> "").getMessage());
  }
  private static Map<String,List<String>> ordered(String e1, String p1, String e2, String p2){
    var res= new java.util.LinkedHashMap<String,List<String>>();
    res.put(e1, List.of(p1));
    res.put(e2, List.of(p2));
    return res;
  }
  @Test void associationNotOurs(){
    utils.Err.strCmp("""
Fearless cannot become the program that opens the kinds of file listed
below.

Another program already answers for these kinds of file. Fearless
stopped before touching anything: your system is exactly as it was.

What stood in the way:
.fearless -> fearlessBin0_003""", Violation.associationNotOurs(Map.of(".fearless", List.of("fearlessBin0_003")), e -> "").getMessage());
  }
  @Test void associationNotOursSeveral(){
    var held= new LinkedHashMap<String,List<String>>();
    held.put(".ffile042", List.of("org.gnome.TextEditor", "text/plain"));
    held.put(".fearless", List.of("OtherApp.fearless"));
    utils.Err.strCmp("""
Fearless cannot become the program that opens the kinds of file listed
below.

Another program already answers for these kinds of file. Fearless
stopped before touching anything: your system is exactly as it was.

What stood in the way:
.ffile042 -> org.gnome.TextEditor
.ffile042 -> text/plain
.fearless -> OtherApp.fearless""", Violation.associationNotOurs(held, e -> "").getMessage());
  }
  @Test void associationNotOursSeveralClaimed(){
    var held= new LinkedHashMap<String,List<String>>();
    held.put(".ffile001", List.of("org.gnome.TextEditor", "vim"));
    held.put(".fapp042", List.of("OtherApp.fapp042"));
    utils.Err.strCmp("""
Fearless cannot become the program that opens the kinds of file listed
below.

Another program already answers for these kinds of file. Fearless
stopped before touching anything: your system is exactly as it was.

What stood in the way:
.ffile001 claimed by "hello.Foo" of project "demo" -> org.gnome.TextEditor
.ffile001 claimed by "hello.Foo" of project "demo" -> vim
.fapp042 claimed by "hello.Bar" of project "demo" -> OtherApp.fapp042""", Violation.associationNotOurs(held, ViolationTest::claimedBy).getMessage());
  }
  private static String claimedBy(String ext){ return " claimed by \"hello."+(List.of(".foo", ".ffile001").contains(ext) ? "Foo" : "Bar")+"\" of project \"demo\""; }
  @Test void associationNotWritable(){
    var registered= "HKEY_CLASSES_ROOT\\.fearless (write denied: not running as the owning user)";
    utils.Err.strCmp("""
Fearless cannot become the program that opens Fearless projects and the
other kinds of file it registers.

What Fearless would need to write, or remove, is not yours to change.
Fearless stopped before touching anything: your system is exactly as it
was.

What stood in the way:
HKEY_CLASSES_ROOT\\.fearless (write denied: not running as the owning user)""", Violation.associationNotWritable(registered).getMessage());
  }
  @Test void associationLeftHalfDone(){
    var failed= "update-mime-database /home/me/.local/share/mime\nThe program could not be started.";
    utils.Err.strCmp("""
Fearless could not finish changing which programs open the kinds of file
it registers.

A step failed after the steps before it had changed your system, so your
system is left part way: which program opens Fearless projects, and the
other kinds of file Fearless registers, may now be wrong. The next start
of Fearless does these changes again.

What failed:
update-mime-database /home/me/.local/share/mime
The program could not be started.""", Violation.associationLeftHalfDone(failed).getMessage());
  }
}
