package mainCoordinator;

import static java.nio.charset.StandardCharsets.UTF_8;
import java.io.PrintStream;
import java.nio.file.Path;

import coordinator.Coordinator;
import coordinatorMessages.CacheCorruptionError;
import coordinatorMessages.UserExit;
import coordinatorMessages.UserTreeError;
import tools.SourceOracle;

public record ProgrammaticMain(StringBuilder out, StringBuilder err,String fName, String code, Path stdLib, Path stdRt, Path dest){
  static public void runFearless(Path projectPath, Path base, Path rt) throws Throwable {
    try{ Main.run(projectPath, base,rt); }
    catch(UserExit e){ System.err.print(e.getMessage()); }
    catch(UserTreeError e){ System.err.print(e.getMessage()); }
    catch(CacheCorruptionError e){ System.err.print(e.getMessage()); }
    catch (InterruptedException e){ throw e; }
    //catch (InvocationTargetException e){ throw e.getCause(); }
    //catch (ExecutionException e){ throw e.getCause(); }
    catch(Throwable t){ 
      System.err.println(t.getClass().getCanonicalName());
      System.err.print(UserExit.crash(t));
    }
  }
  public void runFearless(){
    var oldOut= System.out;
    var oldErr= System.err;
    try{ _runFearless(); }
    catch(UserExit e){ System.err.print(e.getMessage()); }
    catch(UserTreeError e){ System.err.print(e.getMessage()); }
    catch(CacheCorruptionError e){ System.err.print(e.getMessage()); }
    catch(Throwable t){ System.err.print(UserExit.crash(t)); }
    finally{
      System.setOut(oldOut);
      System.setErr(oldErr);
    }
  }
  private void _runFearless() throws InterruptedException{
    System.setOut(new PrintStream(new Utf8Sink(out::append), true, UTF_8));
    System.setErr(new PrintStream(new Utf8Sink(err::append), true, UTF_8));
    var oracle= SourceOracle.debugBuilder().put(fName,code).build();
    new Coordinator(){
      @Override public Path stLibPath(){ return stdLib; }
      @Override public Path rtPath(){ return stdRt; }
      @Override public SourceOracle sourceOracle(Path path){
        if (path.equals(stdLib)){ return Coordinator.super.sourceOracle(path); }
        return oracle;
      }
    }.main(dest);
  }
}
