package mainCoordinator;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import coordinator.CapabilityEnvironment;
import coordinator.Coordinator;
import core.E.Literal;
import core.OtherPackages;
import naiveBackend.BackendTools;
import realSourceOracle.RealSourceOracleWithZip;
import tools.SourceOracle;
import tools.Utf8Sink;
import userMessages.UserError;

public record ProgrammaticMain(StringBuilder out, StringBuilder err, Map<String,String> files, Path stdLib, Path stdRt, Path dest){
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
    var b= SourceOracle.debugBuilder();
    files.forEach(b::put);
    var oracle= b.build();
    var c= new Coordinator(){
      @Override public SourceOracle sourceOracle(Path path){ return oracle; }
      @Override public Path stdLibBase(){ return stdLib; }
      @Override public BackendTools backendTools(String pkgName, SourceOracle o, OtherPackages other, List<Literal> core, CapabilityEnvironment capabilities){
        return BackendTools.of(pkgName, o, other, core, dest.resolve(Coordinator.outDir), baseCachePath(), !pkgName.equals("base"), stdRt, capabilities);
      }
    };
    c.main(dest, RealSourceOracleWithZip.stdLib(stdLib));
  }
}
