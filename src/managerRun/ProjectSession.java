package managerRun;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

import coordinator.Coordinator;
import tools.ChildJvm;
import tools.JavacTool;
import userMessages.UserError;
import userMessages.Violation;
import utils.Bug;

public final class ProjectSession{
  private final Path folder;
  private final Executor worker;
  private final Consumer<String> out;
  private final Runnable changed;
  private ChildJvm child;
  private String current= "";
  private Instant since= Instant.now();
  private Optional<List<String>> mains= Optional.empty();
  public ProjectSession(Path folder, Executor worker, Consumer<String> out, Runnable changed){
    this.folder= folder;
    this.worker= worker;
    this.out= out;
    this.changed= changed;
  }
  public Path folder(){ return folder; }
  public synchronized boolean busy(){ return !current.isEmpty(); }
  public synchronized String current(){ return current; }
  public synchronized Duration elapsed(){ return Duration.between(since, Instant.now()); }
  public synchronized Optional<List<String>> mains(){ return mains; }
  public synchronized Optional<String> running(){ return child == null ? Optional.empty() : Optional.of(current); }
  public void refresh(){ submit("reading", this::readMains); }
  public void compile(){ submit("compiling", this::doCompile); }
  public void run(List<String> selected){ submit("starting", ()->doRun(selected)); }
  public synchronized void terminate(){
    if (child == null){ return; }
    out.accept("--- terminating "+current+" ---\n");
    child.kill();
  }
  private synchronized void submit(String what, Runnable job){
    if (busy()){ return; }
    starting(what);
    worker.execute(()->guard(job));
  }
  private void guard(Runnable job){
    try{ job.run(); }
    catch(UserError e){ out.accept(e.getMessage()); }
    catch(Throwable t){ out.accept(UserError.crash(t)); }
    finally{ done(); }
  }
  private synchronized void done(){
    current= "";
    changed.run();
  }
  private synchronized void starting(String what){
    current= what;
    since= Instant.now();
    changed.run();
  }
  private void readMains(){
    Optional<List<String>> res;
    try{ res= new Coordinator(){
      @Override public Path stLibPath(){ return stdLib("base"); }
      @Override public Path rtPath(){ return stdLib("rt"); }
    }.mains(folder); }
    catch(UserError _){ res= Optional.empty(); }
    synchronized(this){ mains= res; }
  }
  private void doCompile(){
    out.accept("--- compiling "+folder.getFileName()+" ---\n");
    var ec= await(()->ChildJvm.start(compileArgs(), out));
    out.accept("--- compile "+(ec == 0 ? "done" : "failed with "+ec)+" ---\n");
    readMains();
  }
  private void doRun(List<String> selected){
    readMains();
    var known= mains();
    if (known.isEmpty()){ out.accept("--- this project needs compiling ---\n"); return; }
    selected.stream().filter(known.get()::contains).forEach(this::runOne);
  }
  private void runOne(String main){
    starting(main);
    out.accept("--- running "+main+" ---\n");
    var ec= await(()->Coordinator.startMain(folder, main, out));
    out.accept("--- "+main+" exited with "+ec+" after "+elapsed().toSeconds()+"s ---\n");
  }
  private int await(Supplier<ChildJvm> start){
    ChildJvm started;
    synchronized(this){ started= start.get(); child= started; }
    try{ return started.await(); }
    catch(InterruptedException e){ throw Bug.of(e.toString()); }
    finally{ synchronized(this){ child= null; } }
  }
  private static Path stdLib(String name){
    return JavacTool.reqAppDir(Violation::mustUseLauncher).resolve("stdLib").resolve(name);
  }
  private List<String> compileArgs(){
    var appDir= JavacTool.reqAppDir(Violation::mustUseLauncher);
    return List.of(
      "-Djava.awt.headless=true",
      "-D"+JavacTool.appDirKey+"="+appDir,
      "-D"+JavacTool.launcherKey+"="+JavacTool.consoleKey,
      "-D"+JavacTool.versionIdKey+"="+JavacTool.reqVersionId(Violation::mustUseLauncher),
      "--enable-native-access=Commons,Coordinator",
      "-p", appDir.resolve(JavacTool.deployedModsDirName).toString(),
      "-m", "Coordinator/managerRun.ChildMain",
      folder.toString());
  }
}
