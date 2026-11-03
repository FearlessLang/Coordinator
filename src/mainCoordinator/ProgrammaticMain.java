package mainCoordinator;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import coordinator.CapabilityEnvironment;
import coordinator.Coordinator;
import core.E.Literal;
import core.OtherPackages;
import naiveBackend.BackendTools;
import tools.Fs;
import tools.SourceOracle;
import tools.Utf8Sink;
import userMessages.UserError;

public record ProgrammaticMain(StringBuilder out, StringBuilder err, Map<String,String> files, Path stdLib, Path stdRt, Path dest){
  private static final Set<Path> builtBases= ConcurrentHashMap.newKeySet();
  public void runFearless(){
    var oldOut= System.out;
    var oldErr= System.err;
    try{ _runFearless(); }
    catch (UserError e){ System.err.print(e.getMessage()); }
    catch (Throwable e){ System.err.print(UserError.crash(e)); }
    finally{
      System.setOut(oldOut);
      System.setErr(oldErr);
    }
  }
  private void _runFearless() throws InterruptedException{
    System.setOut(new PrintStream(new Utf8Sink(out::append), true, UTF_8));
    System.setErr(new PrintStream(new Utf8Sink(err::append), true, UTF_8));
    var project= dest.resolve("project");
    Fs.rmTree(project);
    files.forEach((path, text) -> Fs.writeUtf8(project.resolve(path), text));
    var baseCache= dest.resolve("baseCache");
    var c= new Coordinator(){
      @Override public Path stdLibBase(){ return stdLib; }
      @Override public Optional<Path> baseCachePath(){ return Optional.of(baseCache); }
      @Override public BackendTools backendTools(String pkgName, SourceOracle o, OtherPackages other, List<Literal> core, CapabilityEnvironment capabilities){
        return BackendTools.of(pkgName, o, other, core, pkgName.equals("base") ? dest.resolve("baseScratch") : project.resolve(Coordinator.outDir), baseCachePath(), !pkgName.equals("base"), stdRt, capabilities);
      }
    };
    if (builtBases.add(baseCache)){ c.buildBaseCache(stdLib, dest.resolve("baseScratch"), baseCache); }
    c.main(project, stdLib);
  }
}
