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

import fileSupport.NativeLocaleForcer;
import fileSupport.StringFiles;
import mainCoordinator.Main;
import userMessages.Violation;
import userMessages.UserError;
import tools.Fs;
import tools.JavacTool;
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
  //Set early in run(..), and mirrored into UserError so that error messages
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
    catch(UserError e){ exitCode= 1; displayWithFallBack(e); }
    catch(VirtualMachineError|LinkageError e){ exitCode= 2; displayWithFallBack(Violation.vmOrLinkageFailure(e)); }
    catch(Throwable t){ exitCode= 3; displayWithFallBack(UserError.crashed(t)); }
    System.exit(exitCode);//see the resource policy above: this ends workers, window and lock
  }
  private static void displayWithFallBack(UserError e){
    try { e.display(); }
    catch(InterruptedException ie){ e.displayStderr(ie); }
  }
  //Invariant: EVERY launch leaves exactly one message file, then tries to become the owner.
  //Exactly one process holds the lock; the owner drains all message files, including its own.
  private static void run(String message){
    binDir= locateBinDir();
    managerDir= resolveManagerDir();
    UserError.setManagerPaths(msgDir(), lockFile());//from here on, messages can name the real folders
    createManagerFolder(managerDir);
    leaveMessage(msgDir(), message);
    try { managerLock= FileChannel.open(lockFile(), CREATE, WRITE).tryLock(); }
    catch(OverlappingFileLockException e){ throw Bug.unreachable(); }//only possible from a second thread of THIS process; but we control this process
    catch(IOException e){ throw Violation.couldNotUseInstanceLock(e); }
    if (managerLock == null){ return; }//an owner exists; it will drain our message file.
    //Accepted race: if the owner dies right after our failed tryLock, before
    //draining, our message waits in the folder and is shown by the NEXT
    //manager start. Messages are never lost, only delayed.
    UserError.becameManagerOwner();//from here on, a failure ends the manager, not just this process
    Manager.runOwner(msgDir());
  }
  //heuristic to navigate the path upwards
  private static Path locateBinDir(){
    var expectedDirName= JavacTool.appNameFor(Main.reqVersionId())+(Fs.isMac() ? ".app" : "");
    Path startedFrom= Main.reqAppDir().toAbsolutePath().normalize();//TODO: is this absolute needed?
    for(var dir= startedFrom; dir != null; dir= dir.getParent()){
      Path name= dir.getFileName();
      if (name == null){ break; }//a root has no name and cannot be our folder
      if (name.toString().equals(expectedDirName)){ return dir; }
    }
    throw Violation.programFolderNotFound(startedFrom, expectedDirName);
  }
  private static Path resolveManagerDir(){
    var host= InstallLocation.isInstalled(binDir) ? InstallLocation.userDataHome() : codeDir();
    return host.resolve(JavacTool.dataDirNameFor(Main.reqVersionId()));
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
    catch(IOException|UnsupportedOperationException|SecurityException e){ throw Violation.couldNotCreateManagerFolder(dir, e); }
  }
  //Write to a .tmp name, then atomically rename it to .msg: the owner drains
  //only *.msg, so it can never observe a half written file. A crash in between
  //leaves a stale .tmp behind; the owner sweeps those during drains.
  //The write is delegated to fileSupport; the rename cannot be, and keeps its
  //own error below.
  private static void leaveMessage(Path msgDir, String message){
    var name= "%020d-%s".formatted(System.currentTimeMillis(), UUID.randomUUID());
    var tmp= msgDir.resolve(name+".tmp");
    StringFiles.writeNew(tmp, message, UserError.onFileError());
    try { Files.move(tmp, msgDir.resolve(name+".msg"), ATOMIC_MOVE); }
    catch(IOException e){ throw Violation.couldNotLeaveStartMessage(e); }
  }
}
