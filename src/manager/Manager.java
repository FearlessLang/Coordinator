package manager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.nio.file.FileSystems;
import java.nio.file.WatchService;

import fileSupport.StringFiles;
import managerData.ManagerData;
import tools.JavacTool;
import userMessages.Report;
import userMessages.UserError;
import userMessages.Violation;
import userMessages.UserError;
import utils.Bug;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;

public class Manager {
  static void runOwner(Path msgDir, ManagerData data){
    //Resource policy (see ManagerMain): the watch service, the executor,
    //the workers and the window live for the whole process life and are ended
    //by process exit; every path out of this method leads to System.exit.
    var executor= Executors.newVirtualThreadPerTaskExecutor();
    var watcher= watcher(msgDir);//registered BEFORE the first drain: messages arriving in between queue up, they are not lost
    var stop= new Stop();
    ManagerGui gui= ManagerGui.create(stop::quit, data);
    ManagerTray.install(gui, stop::quit);
    gui.showManager();
    executor.submit(stop.worker(() -> watchMessages(watcher,gui,data,msgDir)));
    executor.submit(stop.worker(gui::tickLoop));//example long lived worker; more will come
    offerAssociation(gui);
    try { stop.await(); }
    finally { executor.shutdownNow(); }
    //shutdownNow is not resource cleanup: it stops the workers, so that a
    //manager on its way out (quit, or showing its error dialog) cannot keep
    //consuming message files that are meant for the next manager.
  }
  private static void offerAssociation(ManagerGui gui){
    var versionId= JavacTool.reqVersionId(Violation::mustUseLauncher);
    if (!FileAssociation.canBecomeDefault(versionId)){ return; }
    if (!gui.askBecomeDefault()){ return; }
    try { FileAssociation.makeDefault(ManagerMain.binDir, versionId, FearlessIcon.file()); }
    catch(UserError e){ gui.explain(e); }
  }
  private static WatchService watcher(Path msgDir){
    try {
      var watcher= FileSystems.getDefault().newWatchService();
      msgDir.register(watcher, ENTRY_CREATE);//Can take up to 2 seconds in Mac; accepted
      return watcher;
    }
    catch(IOException|UnsupportedOperationException|SecurityException e){ throw Violation.couldNotWatchMessageFolder(msgDir, e); }
  }
  private static List<Path> list(Path dir, String glob) throws IOException {
    var files= new ArrayList<Path>();
    try(var stream= Files.newDirectoryStream(dir, glob)){ stream.forEach(files::add); }
    //TODO: add handling for DirectoryIteratorException
    return files;
  }
  private static void watchMessages(WatchService watcher, ManagerGui gui, ManagerData data, Path msgDir){
    drainToGui(gui,data,msgDir);//first drain: renders our own launch message, and anything left over from a dead manager
    while(true){
      try { tryWatch(watcher,gui,data,msgDir); }
      catch(InterruptedException e){ return; }
    }
  }
  private static void tryWatch(WatchService watcher, ManagerGui gui, ManagerData data, Path msgDir) throws InterruptedException {
    var key= watcher.take();
    key.pollEvents();//events must be consumed before reset, or the key requeues immediately
    drainToGui(gui,data,msgDir);
    if (!key.reset()){ throw Violation.messageFolderNotWatchable(msgDir); }
  }
  private static void drainToGui(ManagerGui gui, ManagerData data, Path msgDir){
    //Called only from the single watcher worker (including once at its start):
    //never concurrently, so no synchronization is needed. If a second caller
    //is ever added, make this synchronized.
    //Reading a message file is delegated to fileSupport (it throws its own
    //UserError); the IOException below covers listing and removing files.
    try {
      var messages= drainMessageFiles(msgDir);
      if (messages.isEmpty()){ return; }
      messages.forEach(message -> register(gui,data,message));
      gui.foldersChanged();
      gui.addMessages(messages);
      gui.showManager();
    }
    catch(IOException e){ throw Violation.couldNotDrainMessageFolder(msgDir, e); }
  }
  private static void register(ManagerGui gui, ManagerData data, String message){
    var folder= projectFolder(message);
    if (folder.isEmpty() || data.isRegistered(folder.get())){ return; }
    var nested= data.nestedWith(folder.get());
    if (nested.isPresent()){ gui.explain(Report.folderNestedWithRegistered(folder.get(),nested.get())); return; }
    gui.nameFolder(data,folder.get());
    data.addRegisteredFolder(folder.get());
  }
  private static Optional<Path> projectFolder(String message){
    Path path;
    try { path= Path.of(message); }
    catch(InvalidPathException e){ return Optional.empty(); }
    if (Files.isDirectory(path)){ return Optional.of(path); }
    if (!Files.isRegularFile(path)){ return Optional.empty(); }
    return Optional.ofNullable(path.toAbsolutePath().getParent());
  }
  private static List<String> drainMessageFiles(Path msgDir) throws IOException {
    var files= list(msgDir, "*.msg");
    files.sort(Comparator.comparing(file -> file.getFileName().toString()));//names start with zero padded millis: oldest first
    var messages= new ArrayList<String>();
    for(var file: files){
      var message= StringFiles.read(file, UserError.onFileError());
      messages.add(message.isBlank() ? "(started with no argument)" : message);
      Files.deleteIfExists(file);
    }
    deleteStaleTmp(msgDir);
    return messages;
  }
  //A sender that crashes between writing name.tmp and renaming it to name.msg
  //leaves the .tmp behind forever. A live sender holds a .tmp only for
  //milliseconds, so any .tmp older than a minute is garbage from a crash.
  //Sweeping here, on every drain, keeps the folder self cleaning; sweeping on
  //manager exit instead would race with senders that are starting right then.
  private static void deleteStaleTmp(Path msgDir) throws IOException {
    var old= Instant.now().minusSeconds(60);
    for(var file: list(msgDir, "*.tmp")){
      try { if (Files.getLastModifiedTime(file).toInstant().isBefore(old)){ Files.deleteIfExists(file); } }
      catch(NoSuchFileException e){}//just renamed to .msg by its live sender: not stale
    }
  }
}
//Note: A worker stopped by shutdownNow records a problem that nobody ever reads; this is harmless.
class Stop {
  private final CountDownLatch latch= new CountDownLatch(1);
  private final AtomicReference<RuntimeException> failure= new AtomicReference<>();
  void quit(){ latch.countDown(); }
  Runnable worker(Runnable task){ return () -> runWorker(task); }
  private void runWorker(Runnable task){
    try { task.run(); fail(Bug.of("A manager worker stopped, but it must run for the whole life of the manager process")); }
    catch(UserError problem){ fail(problem); }
    catch(Throwable t){ fail(Bug.of(t)); }
  }
  private void fail(RuntimeException problem){ failure.compareAndSet(null, problem); latch.countDown(); }
  void await(){
    try { latch.await(); }
    catch(InterruptedException e){ throw Bug.of(e); }
    var problem= failure.get();
    if (problem != null){ throw problem; }
  }
}
