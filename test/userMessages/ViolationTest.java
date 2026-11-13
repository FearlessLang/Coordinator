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
    var registered= "fearlessManaged0_006w\nfearlessManaged0_007w";
    utils.Err.strCmp("""
Fearless cannot tell which of your Fearless installs should open Fearless
projects: more than one is registered with your system at once.

Fearless stopped before touching anything: your system is exactly as it
was.

What is registered:
fearlessManaged0_006w
fearlessManaged0_007w""", Violation.associationsAmbiguous(registered).getMessage());
  }
  private static Map<String,String> ordered(String e1, String p1, String e2, String p2){
    var res= new LinkedHashMap<String,String>();
    res.put(e1, p1);
    res.put(e2, p2);
    return res;
  }
  @Test void associationNotOurs(){
    utils.Err.strCmp("""
Fearless needs the desktop to open the files with the extensions listed below with Fearless.
The desktop opens those files with other programs:
  ".fearless" is opened with "Foo\"""", Violation.associationNotOurs(Map.of(".fearless", "Foo"), e -> "").getMessage());
  }
  @Test void associationNotOursSeveralClaimed(){
    utils.Err.strCmp("""
Fearless needs the desktop to open the files with the extensions listed below with Fearless.
The desktop opens those files with other programs:
  ".ffile001" claimed by "hello.Foo" of project "demo" is opened with "org.gnome.TextEditor"
  ".htm" claimed by "hello.Bar" of project "demo" is opened with "Visual Studio Code\"""", Violation.associationNotOurs(ordered(".ffile001", "org.gnome.TextEditor", ".htm", "Visual Studio Code"), ViolationTest::claimedBy).getMessage());
  }
  @Test void associationQuestion(){
    utils.Err.strCmp("""
The desktop opens the files with the extensions listed below with other programs.
Fearless needs the desktop to open those files with Fearless:
  ".ffile001" claimed by "hello.Foo" of project "demo" is opened with "vim"
  ".fapp042" claimed by "hello.Bar" of project "demo" is opened with "OtherApp"
Let the desktop open the files with these extensions with Fearless?
Answering No ends Fearless.""", Violation.associationQuestion(ordered(".ffile001", "vim", ".fapp042", "OtherApp"), ViolationTest::claimedBy, "ends Fearless"));
  }
  @Test void associationNotTaken(){
    var choice= "HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts\\.htm\\UserChoice";
    utils.Err.strCmp("""
Fearless needs the desktop to open the files with the extensions listed below with Fearless.
The desktop does not let Fearless change the program opening those files:
  ".htm" claimed by "hello.Bar" of project "demo" needs a change of
    HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts\\.htm\\UserChoice
    HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts\\.htm\\UserChoice\\Latest""", Violation.associationNotTaken(Map.of(".htm", choice+"\n"+choice+"\\Latest"), ViolationTest::claimedBy).getMessage());
  }
  private static String claimedBy(String ext){ return " claimed by \"hello."+(List.of(".foo", ".ffile001").contains(ext) ? "Foo" : "Bar")+"\" of project \"demo\""; }
  @Test void associationNotWritable(){
    var registered= "RegDeleteTreeW HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts\\.pdf\\UserChoice\nAccess is denied.";
    utils.Err.strCmp("""
Fearless cannot become the program that opens Fearless projects and the
other kinds of file it registers.

What Fearless would need to write, or remove, is not yours to change.

What stood in the way:
RegDeleteTreeW HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts\\.pdf\\UserChoice
Access is denied.""", Violation.associationNotWritable(registered).getMessage());
  }
  @Test void associationLeftHalfDone(){
    var failed= "update-mime-database /home/me/.local/share/mime\nThe program could not be started.";
    utils.Err.strCmp("""
Fearless could not finish changing which programs open the kinds of file
it registers.

A step failed, and the steps before it may have changed your system:
which program opens Fearless projects, and the other kinds of file
Fearless registers, may now be wrong. The next start of Fearless does
these changes again.

What failed:
update-mime-database /home/me/.local/share/mime
The program could not be started.""", Violation.associationLeftHalfDone(failed).getMessage());
  }
}
