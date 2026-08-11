package manager;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import coordinatorMessages.UserExit;
import fileSupport.NativeLocaleForcer;
import fileSupport.StringFiles;
import mainCoordinator.LauncherProps;
import managerMessages.EnvironmentBusy;
import managerMessages.ErrorContext;
import managerMessages.InternalBug;
import managerMessages.RuntimeBroken;
import managerMessages.UserActionable;
import managerMessages.UserProblem;
import tools.FearlessId;
import utils.Bug;
//Resource policy: the manager is a long living process. Resources opened for
//its WHOLE life (the instance lock, the watch service, the worker threads,
//the window) are deliberately never closed by us: every path out of main
//leads to System.exit, and the operating system reclaims all of them on
//process death - including after a crash. Closing them on the way out would only add new failure opportunities to the exit path.
//Short lived resources used DURING the manager life (message files, directory streams) are still closed promptly at their use site.
public class ManagerMain {
  //The lock must stay strongly reachable for the whole process life: if the
  //channel object became garbage, the JVM could close it and release the lock
  //while the manager is still running.
  private static FileLock managerLock;
  //Set early in run(..), and mirrored into ErrorContext so that error messages
  //always show the paths this run actually used.
  static Path binDir;
  static Path managerDir;
  static Path msgDir(){ return managerDir.resolve("messages"); }
  static Path lockFile(){ return managerDir.resolve("instance.lock"); }
  public static void main(String[] args){
    NativeLocaleForcer.forceEnglish();
    var exitCode= 0;
    //TODO: throw user problem if called with more then one arg
    try { run(args.length==0 ? "" : args[0]); }
    catch(UserProblem e){ exitCode= 1; displayWithFallBack(e); }
    catch(UserExit e){ exitCode= 1; displayWithFallBack(UserProblem.fromFearless(e)); }
    catch(VirtualMachineError|LinkageError e){ exitCode= 2; displayWithFallBack(RuntimeBroken.vmOrLinkageFailure(e)); }
    catch(Throwable t){ exitCode= 3; displayWithFallBack(InternalBug.unhandledThrowable(t)); }
    System.exit(exitCode);//see the resource policy above: this ends workers, window and lock
  }
  private static void displayWithFallBack(UserProblem e){
    try { e.display(); }
    catch(InterruptedException ie){ e.displayStderr(ie); }
  }
  //Invariant: EVERY launch leaves exactly one message file, then tries to become the owner.
  //Exactly one process holds the lock; the owner drains all message files, including its own.
  private static void run(String message){
    binDir= locateBinDir();
    managerDir= resolveManagerDir();
    ErrorContext.set(msgDir(), lockFile());//from here on, messages can name the real folders
    createManagerFolder(managerDir);
    leaveMessage(msgDir(), message);
    try { managerLock= FileChannel.open(lockFile(), CREATE, WRITE).tryLock(); }
    catch(OverlappingFileLockException e){ throw Bug.unreachable(); }//only possible from a second thread of THIS process; but we control this process
    catch(IOException e){ throw UserActionable.couldNotUseInstanceLock(e); }
    if (managerLock == null){ return; }//an owner exists; it will drain our message file.
    //Accepted race: if the owner dies right after our failed tryLock, before
    //draining, our message waits in the folder and is shown by the NEXT
    //manager start. Messages are never lost, only delayed.
    Manager.runOwner(msgDir());
  }
  //Where is "the application", as the user sees it?
  //The launcher hands over $APPDIR (LauncherProps): a folder inside the app image, at a
  //place jpackage picks differently on each operating system. The folder holding the code
  //is the one named FearlessId.binFolder on the way up from there.
  //We match OUR OWN name, we do not count folder levels: the nesting is jpackage's
  //business and differs per OS (and on mac the app image is a bundle, folder name
  //<binFolder>.app, whose inside must not be written to as it would break the code
  //signature), while the name is the one thing this deploy chose and this code knows.
  private static Path locateBinDir(){
    var startedFrom= LauncherProps.reqAppDir().toAbsolutePath().normalize();
    for(var dir= startedFrom; dir != null; dir= dir.getParent()){
      var name= dir.getFileName();
      if (name == null){ break; }//a root has no name and cannot be our folder
      if (isBinName(name.toString())){ return dir; }
    }
    throw UserActionable.programFolderNotFound(startedFrom);
  }
  private static boolean isBinName(String name){
    return name.equals(FearlessId.binFolder) || name.equals(FearlessId.binFolder+".app");
  }
  //Manager folder resolution: read where the code sits, then follow the convention of
  //that place. No probing of what is writable: a writability probe answers about this
  //instant, and the answer moves the user's data the first time an antivirus, a backup
  //tool or a permission change makes the probe say otherwise. A location is stable.
  //No setting either: the folder is fearless+versionId, and only this rule places it.
  private static Path resolveManagerDir(){
    var host= InstallLocation.isInstalled(binDir) ? InstallLocation.userDataHome() : codeDir();
    return host.resolve(FearlessId.dataFolder);
  }
  //The folder holding the program folder. Reached only when the program folder is NOT in
  //an installed-programs location, so Fearless was unpacked here and owns this folder:
  //it holds the program folder, and now also the manager folder.
  private static Path codeDir(){
    var res= binDir.getParent();
    if (res == null){ throw Bug.unreachable(); }//binDir has a name, so it is not a root
    return res;
  }
  //Creating the folders IS the writability probe for the folder we already chose: no
  //separate check could be more truthful than attempting the exact operation we need.
  private static void createManagerFolder(Path dir){
    try { Files.createDirectories(dir.resolve("messages")); }
    catch(IOException|UnsupportedOperationException|SecurityException e){ throw UserActionable.couldNotCreateManagerFolder(dir, e); }
  }
  //Write to a .tmp name, then atomically rename it to .msg: the owner drains
  //only *.msg, so it can never observe a half written file. A crash in between
  //leaves a stale .tmp behind; the owner sweeps those during drains.
  //The write is delegated to fileSupport; the rename cannot be, and keeps its
  //own error below.
  private static void leaveMessage(Path msgDir, String message){
    var name= "%020d-%s".formatted(System.currentTimeMillis(), UUID.randomUUID());
    var tmp= msgDir.resolve(name+".tmp");
    StringFiles.writeNew(tmp, message, UserProblem.processExitsOnFileError());
    try { Files.move(tmp, msgDir.resolve(name+".msg"), ATOMIC_MOVE); }
    catch(IOException e){ throw EnvironmentBusy.couldNotLeaveStartMessage(e); }
  }
}
