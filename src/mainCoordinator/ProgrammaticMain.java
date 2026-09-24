package mainCoordinator;

import static java.nio.charset.StandardCharsets.UTF_8;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

import coordinator.CapabilityEnvironment;
import coordinator.Coordinator;
import core.E.Literal;
import core.OtherPackages;
import naiveBackend.BackendTools;
import realSourceOracle.RealSourceOracleWithZip;
import userMessages.UserError;
import tools.SourceOracle;
import tools.Utf8Sink;

public record ProgrammaticMain(StringBuilder out, StringBuilder err,String fName, String code, Path stdLib, Path stdRt, Path dest){
  public void runFearless(){
    var oldOut= System.out;
    var oldErr= System.err;
    try{ _runFearless(); }
    catch(UserError e){ System.err.print(e.getMessage()); }
    catch(Throwable t){ System.err.print(UserError.crash(t)); }
    finally{
      System.setOut(oldOut);
      System.setErr(oldErr);
    }
  }
  private void _runFearless() throws InterruptedException{
    System.setOut(new PrintStream(new Utf8Sink(out::append), true, UTF_8));
    System.setErr(new PrintStream(new Utf8Sink(err::append), true, UTF_8));
    var oracle= SourceOracle.debugBuilder().put(fName,code).build();
    var c= new Coordinator(){
      @Override public SourceOracle sourceOracle(Path path){ return oracle; }
      @Override public Path stdLibBase(){ return stdLib; }
      @Override public BackendTools backendTools(String pkgName, SourceOracle o, OtherPackages other, List<Literal> core, CapabilityEnvironment capabilities){
        return BackendTools.of(pkgName, o, other, core, dest.resolve(Coordinator.outDir), baseCachePath(), stdRt, capabilities);
      }
    };
    c.main(dest, new RealSourceOracleWithZip(stdLib));
  }
}
