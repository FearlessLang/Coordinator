package userMessages;

import java.awt.HeadlessException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import metaParser.Message;
import metaParser.PrettyFileName;
import tools.Fs;
import tools.SourceOracle;
import tools.SourceOracle.RefParent;

/// UserError is the ONLY exception that means "this text is for the user".
/// Everything else that goes wrong is a bug and travels as utils.Bug.
///
/// Fearless is packaged as an executable via jpackage.
/// Error content: a user-visible sentence is justified only if it changes what
/// the user should do, what the user should avoid doing, or what the user can report.
///
/// The text of every error lives away from the code that discovers it, so that a text
/// can be read and judged as text. There are exactly two places to write one, and the
/// choice between them is the only classification we keep:
/// - Report: the user gave us something we cannot accept. Their project, their file
///   names, their zips, their types. They can fix it by changing what they gave us.
/// - Violation: we can no longer do our safe job. Not necessarily a bad actor: an
///   operating system that does not provide what we need, a folder we cannot own, or
///   our own generated files changed under us all land here.
///
/// This file holds only what both of them need: the exception, the paths this run
/// really used, the path rendering helpers, and how the text reaches a human.
@SuppressWarnings("serial")
public final class UserError extends RuntimeException{
  UserError(String msg){ super(msg); }
  UserError(String msg, Throwable cause){ super(msg, cause); }

  private String recoveryLabel;
  private Runnable recovery;
  public UserError withRecovery(String label, Runnable action){
    recoveryLabel= label; recovery= action;
    return this;
  }

  //The project root this run actually used, so that a message always shows the folder
  //that was really involved. Tests set it to whatever fake root they use; a real run
  //keeps the default, since Fearless is launched from inside the project folder.
  public static Path root= Path.of(".").toAbsolutePath();

  //Set by ManagerMain at the single point where this process becomes the manager
  //owner. Read only when a message is displayed: what this process is about to do is
  //known then, and is not a property of what went wrong.
  private static boolean managerOwner= false;
  public static void becameManagerOwner(){ managerOwner= true; }

  private static java.awt.Window owner= null;
  public static void owner(java.awt.Window w){ owner= w; }

  //-- building
  static UserError die(String first, String... more){
    var sb= new StringBuilder();
    sb.append("Error: ").append(first).append('\n');
    for (var s: more){ sb.append("  ").append(s).append('\n'); }
    return new UserError(sb.toString());
  }
  //Handler for StringFiles.read/writeNew. StringFiles hands over (actionTxt, reportTxt);
  //their emptiness encodes the three outcomes below. A file operation we delegated
  //failing is always a Violation: it is our own housekeeping, not the user's input.
  public static BiConsumer<String,String> onFileError(){
    return (actionTxt,reportTxt)->{ throw fileError(actionTxt, reportTxt); };
  }
  private static UserError fileError(String actionTxt, String reportTxt){
    if (reportTxt.isEmpty()){ return new UserError(actionTxt); }//fully explained failure, nothing to report
    if (actionTxt.isEmpty()){ return new UserError(reportTxt); }//report-required kind: explanation and traces travel together
    //explained failure, but gathering some of the extra details failed too
    return new UserError("After a failed file operation, Fearless could not gather all the failure details; the system is likely unstable.\n"+actionTxt+"\n\n"+reportTxt);
  }

  //-- path rendering, shared by both message files
  public static String disp(String s){ return Message.displayString(s); }
  public static String showRel(Path rel){
    return "Root: "+PrettyFileName.displayFileName(root.toUri())+"\nPath: "+disp(rel.toString().replace("\\","/"))+"\n";
  }
  public static String showRel(RefParent rel){
    var s= rel.fearPath();
    assert s.startsWith(SourceOracle.root);
    s= s.substring(SourceOracle.root.length(),s.length());
    return "Root: "+PrettyFileName.displayFileName(root.toUri())+"\nPath: "+disp(s)+"\n";
  }
  public static String showZipRel(Path diskZip, List<String> steps, String entryName){
    assert diskZip.isAbsolute();
    diskZip= root.relativize(diskZip);
    String zipPath= diskZip.toString().replace("\\","/");
    if (!steps.isEmpty()){ zipPath+="/"+String.join("/", steps); }
    return ""
      + "Root: "+PrettyFileName.displayFileName(root.toUri())+"\n"
      + "Path: "+disp(zipPath)+"\n"
      + printEntryName(entryName)+"\n\n";
  }
  static String printEntryName(String entryName){
    if (!isSimpleString(entryName)){
      return "Entry contains non-standard characters.\nShown as: "+disp(entryName); }
    return "Entry: "+disp(entryName)+"\n";
  }
  private static boolean isSimpleString(String s){
    return s.codePoints().allMatch(cp -> cp < 128 && Fs.allowed.indexOf((char)cp) >= 0);
  }
  static String path(String path){ return "  "+path; }

  //-- reaching a human
  public static String crash(Throwable t){
    var sw= new StringWriter();
    t.printStackTrace(new PrintWriter(sw));
    return """
Error: Fearless crashed (this is a bug).
  Please report this error and include the details below.

Details:
""" + sw;
  }
  //A bug still has to reach the user of a windowed program. Wrapping it here is only
  //about how it is shown: what it MEANS is still utils.Bug.
  public static UserError crashed(Throwable t){ return new UserError(crash(t), t); }
  //Only the manager displays errors this way, and only the manager has a termination
  //sentence to add: the coordinator prints getMessage() and ends.
  private String displayText(){ return getMessage()+termination(); }
  private static String termination(){
    if (!managerOwner){ return "\n\nThis Fearless process will now exit."; }
    var programs= Violation.associatedPrograms();
    var res= "\n\nThe Fearless manager process will now terminate.";
    if (programs.isBlank()){ return res; }
    return res+"\nIt will also terminate these Fearless user processes:\n"+programs;
  }
  public void display() throws InterruptedException{
    try { displayGui(); }
    catch(HeadlessException e){ displayStderr(e); }
  }
  private void displayGui() throws InterruptedException{
    if (SwingUtilities.isEventDispatchThread()){ displayNow(); return; }
    try { SwingUtilities.invokeAndWait(this::displayNow); }
    catch(InvocationTargetException e){ displayStderr(e.getCause()); }//Rare, could be a memory overflow or other JVM stuff?
  }
  private void displayNow(){
    if (recovery == null){
      JOptionPane.showMessageDialog(owner, displayText(), "Fearless", JOptionPane.ERROR_MESSAGE);
      return;
    }
    var options= new Object[]{"OK", recoveryLabel};
    var choice= JOptionPane.showOptionDialog(owner, displayText(), "Fearless",
      JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null, options, options[0]);
    if (choice == 1){ recovery.run(); }
  }
  public void displayStderr(Throwable displayFailure){
    System.err.print(displayText()+"\n\n");
    printStackTrace(System.err);//kept: this is the copy a user can report
    System.err.print("\n\nError dialog could not be shown because of the following:\n");
    displayFailure.printStackTrace(System.err);
  }
}
