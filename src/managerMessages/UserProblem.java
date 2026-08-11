package managerMessages;

import java.awt.HeadlessException;
import java.lang.reflect.InvocationTargetException;
import java.util.function.BiConsumer;
import java.util.function.Function;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import coordinatorMessages.UserExit;

/// Error notes:
/// Fearless is packaged as an executable via jpackage.
/// Error content: a user-visible sentence is justified only if it changes what
/// the user should do, what the user should avoid doing, or what the user can report.
///
/// This package is to the manager what coordinatorMessages is to the Coordinator: the
/// text of every error lives here, away from the code that discovers it, so that a text
/// can be read and judged as text.
///
/// File reads and writes are delegated to fileSupport
/// (ByteFiles/StringFiles/FailureText): the ..OnFileError handlers below turn a
/// fileSupport explanation into a UserProblem, only choosing which termination
/// sentence is appended. The other classes of this package cover only what fileSupport
/// does not explain: folder creation, file locking, atomic renames, folder listing and
/// watching, the packaged icon, and the GUI itself.
///
/// Classification, one class per kind:
/// - UserActionable: the user can fix the problem by moving Fearless, restoring its
///   folder, or giving write permission where Fearless keeps what it writes.
/// - EnvironmentBusy: a temporary OS/filesystem/tool conflict may be blocking an operation.
/// - EnvironmentBroken: the OS/filesystem/windowing environment does not
///   provide a service Fearless needs.
/// - ExternalInterference: something outside Fearless changed the manager
///   communication files after the manager started.
/// - InternalBug: our own invariants failed.
/// - RuntimeBroken: a low-level packaged runtime failure escaped; keep the
///   Java-provided exception as the trace.
public final class UserProblem extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private UserProblem(String msg){ super(msg); }
  private UserProblem(String msg, Throwable cause){ super(msg, cause); }

  static UserProblem of(String msg){ return new UserProblem(msg); }
  static UserProblem withTrace(String msg, Throwable cause){ return new UserProblem(msg, cause); }

  static UserProblem processExits(String msg){ return of(msg+"\n\nThis Fearless process will now exit."); }
  static UserProblem processExitsWithTrace(String msg, Throwable cause){ return withTrace(msg+"\n\nThis Fearless process will now exit.", cause); }

  static UserProblem managerFatal(String msg){ return of(msg+managerWillTerminate()); }
  static UserProblem managerFatalWithTrace(String msg, Throwable cause){ return withTrace(msg+managerWillTerminate(), cause); }

  //A failure the manager shares with every other Fearless main, so its text is the one
  //Fearless already writes for it (coordinatorMessages.UserExit); the manager only adds
  //its own termination sentence and shows it the way a manager shows things.
  public static UserProblem fromFearless(UserExit cause){ return processExitsWithTrace(cause.getMessage().strip(), cause); }

  //Handlers for StringFiles.read/writeNew. StringFiles hands over
  //(actionTxt, reportTxt); their emptiness encodes the three outcomes below.
  public static BiConsumer<String,String> processExitsOnFileError(){ return onFileError(UserProblem::processExits); }
  public static BiConsumer<String,String> managerFatalOnFileError(){ return onFileError(UserProblem::managerFatal); }
  private static BiConsumer<String,String> onFileError(Function<String,UserProblem> wrap){
    return (actionTxt,reportTxt)->{ throw fileError(actionTxt, reportTxt, wrap); };
  }
  private static UserProblem fileError(String actionTxt, String reportTxt, Function<String,UserProblem> wrap){
    if (reportTxt.isEmpty()){ return wrap.apply(actionTxt); }//fully explained failure, nothing to report
    if (actionTxt.isEmpty()){ return wrap.apply(reportTxt); }//report-required kind: explanation and traces travel together
    //explained failure, but gathering some of the extra details failed too
    return wrap.apply("After a failed file operation, Fearless could not gather all the failure details; the system is likely unstable.\n"+actionTxt+"\n\n"+reportTxt);
  }
  private static String managerWillTerminate(){
    var programs= ErrorText.associatedPrograms();
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
    JOptionPane.showMessageDialog(null, getMessage(), "Fearless", JOptionPane.ERROR_MESSAGE);
  }
  public void displayStderr(Throwable displayFailure){
    printStackTrace(System.err);
    System.err.print("\n\nError dialog could not be shown because of the following:\n");
    displayFailure.printStackTrace(System.err);
  }
}
